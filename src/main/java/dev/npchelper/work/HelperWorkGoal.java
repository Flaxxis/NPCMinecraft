package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Works the queue, one step at a time, inside the mode's own circle: where the helper lives is still the mode's business,
 * and what it does there is the queue's. Fighting and collecting its own harvest both come first.
 */
public final class HelperWorkGoal extends Goal {
    /** How far from the centre a helper looks for something to work on, on top of its mode's radius. */
    private static final int SEARCH_MARGIN = 8;
    private static final double SPEED = 1.0D;

    private final NpcHelperEntity helper;
    private @Nullable BlockPos site;

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
            case HelperStep.FETCH -> fetch(level, step);
            case HelperStep.CRAFT -> craft(level, step);
            case HelperStep.EQUIP -> equip(step);
            case HelperStep.TAKE_BENCH -> takeBench(level);
            // An unknown step is no reason to stand still forever.
            default -> helper.queue().advance();
        }
    }

    /** Walks to the nearest chest or barrel holding the item and takes one out. */
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
            if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
                helper.getNavigation().moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, SPEED);
            }
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

    /** Makes the item, setting a bench down first when the recipe wants one and none stands nearby. */
    private void craft(final ServerLevel level, final HelperStep step) {
        var wanted = HelperCrafting.item(step.item());
        if (wanted == null) {
            helper.queue().advance();
            return;
        }
        boolean bench = HelperCrafting.benchNearby(level, helper.blockPosition());
        var outcome = HelperCrafting.craft(level, helper.getBag(), wanted, bench);
        switch (outcome) {
            case DONE -> {
                helper.queue().noteProgress();
                step.progress();
                if (step.finished()) helper.queue().advance();
            }
            case NEEDS_BENCH -> {
                // Its own bench goes down right here and is taken back when the making is done.
                var placed = HelperCrafting.placeBench(level, helper.blockPosition());
                if (placed == null) helper.queue().clear();
                else helper.setPlacedBench(placed);
            }
            // Nothing to make it from, or nothing that makes it: the plan was wrong, so it is dropped.
            case NO_MATERIALS, NO_RECIPE -> helper.queue().clear();
        }
    }

    private void equip(final HelperStep step) {
        var wanted = HelperCrafting.item(step.item());
        if (wanted != null) helper.takeInHand(wanted);
        helper.queue().advance();
    }

    /** Breaks the bench it set down and lets the pickup bring it back. */
    private void takeBench(final ServerLevel level) {
        var bench = helper.placedBench();
        if (bench == null || !level.getBlockState(bench).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
            helper.setPlacedBench(null);
            helper.queue().advance();
            return;
        }
        if (!helper.breaking().inReach(bench)) {
            if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
                helper.getNavigation().moveTo(bench.getX() + 0.5D, bench.getY(), bench.getZ() + 0.5D, SPEED);
            }
            return;
        }
        if (!helper.breaking().tick(bench)) return;
        helper.setPlacedBench(null);
        helper.queue().noteProgress();
        helper.queue().advance();
    }

    private void gatherWood(final ServerLevel level, final HelperStep step) {
        if (site == null || !level.getBlockState(site).is(BlockTags.LOGS)
                || !HelperBuildings.fellable(level, site)) site = findLog(level);
        if (site == null) {
            // Nothing left to fell within reach of the centre: the step cannot be finished here.
            helper.queue().clear();
            return;
        }

        if (!helper.breaking().inReach(site)) {
            if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
                helper.getNavigation().moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, SPEED);
            }
            return;
        }

        helper.getNavigation().stop();
        if (!helper.breaking().tick(site)) return;

        helper.queue().noteProgress();
        step.progress();
        site = null;
        if (step.finished()) helper.queue().advance();
    }

    /** The nearest fellable log around the helper's own centre, so work stays where the player put it. */
    private @Nullable BlockPos findLog(final ServerLevel level) {
        Vec3 centre = helper.centre();
        if (centre == null) return null;
        var logs = HelperTreeSearch.find(level, BlockPos.containing(centre), helper.workRadius() + SEARCH_MARGIN);
        return logs.isEmpty() ? null : logs.getFirst();
    }
}
