package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Works the queue, one step at a time, inside the mode's own circle: where the helper lives is still the mode's business,
 * and what it does there is the queue's. Fighting and collecting its own harvest both come first.
 */
public final class HelperWorkGoal extends Goal {
    /** How far past its own circle a helper looks for something to work on. */
    private static final int SEARCH_MARGIN = 8;
    /** A chest for the harvest may stand this far off. */
    private static final int STORE_RADIUS = 64;
    private static final double SPEED = 1.0D;

    /** Felling a tree in order: the trunk, then the crown, then down off the steps, then a new sapling in. */
    private enum Phase { FELL, CROWN, RECOVER, PLANT }

    private final NpcHelperEntity helper;
    private @Nullable BlockPos site;
    private @Nullable List<BlockPos> trunk;
    private @Nullable List<BlockPos> crown;
    private @Nullable BlockPos foot;
    private Phase phase = Phase.FELL;

    public HelperWorkGoal(final NpcHelperEntity helper) {
        this.helper = helper;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return helper.getTarget() == null && !helper.queue().isEmpty() && helper.centre() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        helper.breaking().reset();
        helper.getNavigation().stop();
        site = null;
    }

    @Override
    public void tick() {
        HelperStep step = helper.queue().current();
        if (step == null || !helper.queue().tick()) return;
        if (!(helper.level() instanceof ServerLevel level)) return;

        switch (step.type()) {
            case HelperStep.GATHER_WOOD -> gatherWood(level, step);
            case HelperStep.GATHER_DIRT -> gatherDirt(level, step);
            case HelperStep.PLANT -> plant(level, step);
            case HelperStep.STORE -> store(level, step);
            case HelperStep.FETCH -> fetch(level, step);
            case HelperStep.CRAFT -> craft(level, step);
            case HelperStep.EQUIP -> equip(step);
            case HelperStep.TAKE_BENCH -> takeBench(level);
            default -> helper.queue().advance();
        }
    }

    // ---- felling ----

    private void gatherWood(final ServerLevel level, final HelperStep step) {
        if (trunk == null) {
            BlockPos first = findLog(level);
            if (first == null) {
                helper.queue().clear();
                return;
            }
            trunk = HelperTree.trunk(level, first);
            crown = HelperTree.crown(level, trunk);
            foot = HelperTree.foot(trunk);
            phase = Phase.FELL;
        }

        switch (phase) {
            case FELL -> fellTrunk(level, step);
            case CROWN -> clearCrown(level);
            case RECOVER -> recoverSteps(level);
            case PLANT -> {
                plantAtFoot(level);
                trunk = null;
                crown = null;
                foot = null;
                phase = Phase.FELL;
                if (step.finished()) helper.queue().advance();
            }
        }
    }

    private void fellTrunk(final ServerLevel level, final HelperStep step) {
        BlockPos log = standing(level, trunk, BlockTags.LOGS);
        if (log == null) {
            phase = Phase.CROWN;
            return;
        }
        // Too high to reach: a step of its own dirt goes down first.
        if (HelperSupports.outOfReach(helper, log) && !helper.breaking().inReach(log)) {
            if (!approach(log) && !helper.supports().step(level, log)) phase = Phase.CROWN;
            return;
        }
        if (!helper.breaking().inReach(log)) {
            approach(log);
            return;
        }
        helper.getNavigation().stop();
        if (!helper.breaking().tick(log)) return;

        helper.queue().noteProgress();
        step.progress();
    }

    /** The crown is taken down for its saplings; nothing is built to reach it. */
    private void clearCrown(final ServerLevel level) {
        BlockPos leaf = reachable(level, crown);
        if (leaf == null) {
            phase = Phase.RECOVER;
            return;
        }
        helper.getNavigation().stop();
        if (helper.breaking().tick(leaf)) helper.queue().noteProgress();
    }

    /** Down off its own steps, taking the dirt back as it goes. */
    private void recoverSteps(final ServerLevel level) {
        BlockPos support = helper.supports().topmost(level);
        if (support == null) {
            phase = Phase.PLANT;
            return;
        }
        if (!helper.breaking().inReach(support)) {
            approach(support);
            return;
        }
        if (!helper.breaking().tick(support)) return;
        helper.supports().forget(support);
        helper.queue().noteProgress();
    }

    private void plantAtFoot(final ServerLevel level) {
        if (foot != null) plantAt(level, foot);
    }

    // ---- the other steps ----

    private void gatherDirt(final ServerLevel level, final HelperStep step) {
        if (site == null || !isSoil(level, site)) site = findSoil(level);
        if (site == null) {
            helper.queue().advance();
            return;
        }
        if (!helper.breaking().inReach(site)) {
            approach(site);
            return;
        }
        helper.getNavigation().stop();
        if (!helper.breaking().tick(site)) return;
        site = null;
        helper.queue().noteProgress();
        step.progress();
        if (step.finished()) helper.queue().advance();
    }

    private void plant(final ServerLevel level, final HelperStep step) {
        plantAt(level, helper.blockPosition());
        helper.queue().advance();
    }

    /** Carries the harvest to a chest or barrel with room in it. */
    private void store(final ServerLevel level, final HelperStep step) {
        if (site == null || HelperContainers.containerAt(level, site) == null) {
            site = HelperContainers.findWithRoom(level, helper.blockPosition(), STORE_RADIUS);
        }
        if (site == null) {
            helper.queue().advance();
            return;
        }
        if (!helper.breaking().inReach(site)) {
            approach(site);
            return;
        }
        var container = HelperContainers.containerAt(level, site);
        if (container != null) {
            var bag = helper.getBag();
            for (int slot = 0; slot < bag.getContainerSize(); slot++) {
                ItemStack stack = bag.getItem(slot);
                if (stack.isEmpty() || !keepsOut(stack)) continue;
                ItemStack left = HelperContainers.put(container, stack);
                bag.setItem(slot, left);
            }
        }
        site = null;
        helper.queue().noteProgress();
        helper.queue().advance();
    }

    /** Tools and the dirt it carries stay with the helper; the harvest goes in. */
    private boolean keepsOut(final ItemStack stack) {
        for (HelperToolkit.Tool tool : HelperToolkit.Tool.values()) {
            if (tool.is(stack)) return false;
        }
        return !stack.is(ItemTags.DIRT) && !stack.is(Blocks.CRAFTING_TABLE.asItem()) && !stack.is(ItemTags.SIGNS);
    }

    private void fetch(final ServerLevel level, final HelperStep step) {
        var wanted = HelperCrafting.item(step.item());
        if (wanted == null) {
            helper.queue().advance();
            return;
        }
        if (site == null || HelperContainers.containerAt(level, site) == null) {
            site = HelperContainers.findHolding(level, helper.blockPosition(), HelperToolkit.FETCH_RADIUS,
                    stack -> stack.is(wanted));
        }
        if (site == null) {
            helper.queue().advance();
            return;
        }
        if (!helper.breaking().inReach(site)) {
            approach(site);
            return;
        }
        var container = HelperContainers.containerAt(level, site);
        if (container != null) {
            var taken = HelperContainers.take(container, stack -> stack.is(wanted));
            if (!taken.isEmpty()) helper.getBag().addItem(taken);
        }
        site = null;
        helper.queue().noteProgress();
        helper.queue().advance();
    }

    private void craft(final ServerLevel level, final HelperStep step) {
        var wanted = HelperCrafting.item(step.item());
        if (wanted == null) {
            helper.queue().advance();
            return;
        }
        boolean bench = HelperCrafting.benchNearby(level, helper.blockPosition());
        switch (HelperCrafting.craft(level, helper.getBag(), wanted, bench)) {
            case DONE -> {
                helper.queue().noteProgress();
                step.progress();
                if (step.finished()) helper.queue().advance();
            }
            case NEEDS_BENCH -> {
                var placed = HelperCrafting.placeBench(level, helper.blockPosition());
                if (placed == null) helper.queue().clear();
                else helper.setPlacedBench(placed);
            }
            case NO_MATERIALS, NO_RECIPE -> helper.queue().clear();
        }
    }

    private void equip(final HelperStep step) {
        var wanted = HelperCrafting.item(step.item());
        if (wanted != null) helper.takeInHand(wanted);
        helper.queue().advance();
    }

    private void takeBench(final ServerLevel level) {
        var bench = helper.placedBench();
        if (bench == null || !level.getBlockState(bench).is(Blocks.CRAFTING_TABLE)) {
            helper.setPlacedBench(null);
            helper.queue().advance();
            return;
        }
        if (!helper.breaking().inReach(bench)) {
            approach(bench);
            return;
        }
        if (!helper.breaking().tick(bench)) return;
        helper.setPlacedBench(null);
        helper.queue().noteProgress();
        helper.queue().advance();
    }

    // ---- small helpers ----

    /** Walks on towards the spot. False when the navigation has nothing to offer. */
    private boolean approach(final BlockPos pos) {
        if (!helper.getNavigation().isDone() && !helper.getNavigation().isStuck()) return true;
        return helper.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, SPEED);
    }

    private @Nullable BlockPos standing(final ServerLevel level, final @Nullable List<BlockPos> blocks,
                                        final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) {
        if (blocks == null) return null;
        for (BlockPos pos : blocks) {
            if (level.getBlockState(pos).is(tag)) return pos;
        }
        return null;
    }

    private @Nullable BlockPos reachable(final ServerLevel level, final @Nullable List<BlockPos> blocks) {
        if (blocks == null) return null;
        for (BlockPos pos : blocks) {
            if (HelperBuildings.naturalLeaves(level.getBlockState(pos)) && helper.breaking().inReach(pos)) return pos;
        }
        return null;
    }

    /** Puts a sapling out of the bag into the ground at the spot, if one is carried and the ground will take it. */
    private void plantAt(final ServerLevel level, final BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).is(BlockTags.DIRT)) return;

        var bag = helper.getBag();
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (stack.isEmpty() || !stack.is(ItemTags.SAPLINGS)) continue;
            if (!(stack.getItem() instanceof BlockItem sapling)) continue;
            if (!level.setBlockAndUpdate(pos, sapling.getBlock().defaultBlockState())) return;
            bag.removeItem(slot, 1);
            return;
        }
    }

    private boolean isSoil(final ServerLevel level, final BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.DIRT);
    }

    /** Bare soil near the work, never from under what is standing on it. */
    private @Nullable BlockPos findSoil(final ServerLevel level) {
        Vec3 centre = helper.workCentre();
        if (centre == null) return null;
        BlockPos middle = BlockPos.containing(centre);
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-12, -4, -12), middle.offset(12, 4, 12))) {
            if (!isSoil(level, pos) || !level.getBlockState(pos.above()).isAir()) continue;
            if (HelperBuildings.nearCrafted(level, pos)) continue;
            candidates.add(pos.immutable());
        }
        return candidates.stream().min(java.util.Comparator.comparingDouble(pos -> pos.distToCenterSqr(helper.position())))
                .orElse(null);
    }

    private @Nullable BlockPos findLog(final ServerLevel level) {
        Vec3 centre = helper.workCentre();
        if (centre == null) return null;
        var logs = HelperTreeSearch.find(level, BlockPos.containing(centre), helper.workRadius() + SEARCH_MARGIN);
        return logs.isEmpty() ? null : logs.getFirst();
    }
}
