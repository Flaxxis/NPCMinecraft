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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Works the queue, one step at a time, inside the mode's own circle: where the helper lives is still the mode's business,
 * and what it does there is the queue's. Fighting and collecting its own harvest both come first.
 */
public final class HelperWorkGoal extends Goal {
    /** A chest for the harvest may stand this far off. */
    private static final int STORE_RADIUS = 32;
    private static final int STORE_SIGN_RADIUS = 64;
    private static final double SPEED = 1.0D;

    /** Felling a tree in order: the trunk, then down off its own steps, then a sapling in where it stood. */
    private enum Phase { FELL, RECOVER, PLANT }

    /** How far a sapling or stick on the ground is worth noticing while working. */
    private static final int GLEAN_RADIUS = 8;

    private final NpcHelperEntity helper;
    private final HelperKeeper keeper;
    private @Nullable HelperStep activeStep;
    private @Nullable BlockPos site;
    private @Nullable List<BlockPos> trunk;
    private @Nullable BlockPos foot;
    private Phase phase = Phase.FELL;
    private @Nullable BlockPos soilExit;
    private final java.util.Set<BlockPos> soilProtected = new java.util.HashSet<>();
    private String problem = "";
    private final java.util.Map<BlockPos, Long> markerRetryAfter = new java.util.HashMap<>();
    private long nextMarkerSearch;
    private long markerLastProgress;
    private double markerDistance = Double.MAX_VALUE;

    public String problem() { return problem.isEmpty() ? keeper.problem() : problem; }
    public HelperKeeper keeper() { return keeper; }

    public HelperWorkGoal(final NpcHelperEntity helper) {
        this.helper = helper;
        this.keeper = new HelperKeeper(helper);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return helper.getTarget() == null && !helper.mustReturnToOwner()
                && !helper.queue().isEmpty() && helper.centre() != null;
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
        keeper.reset();
    }

    @Override
    public void tick() {
        HelperStep step = helper.queue().current();
        if (step != activeStep && step != null && helper.getProfession() != dev.npchelper.entity.HelperProfession.NONE
                && !helper.supports().any() && needsKit(step) && !kitReady()
                && helper.queue().snapshot().stream().noneMatch(view -> view.type().equals(HelperStep.PREPARE_KIT))) {
            helper.queue().prepend(List.of(new HelperStep(HelperStep.PREPARE_KIT, "", 1)));
            step = helper.queue().current();
        }
        if (step != activeStep) {
            resetTask();
            activeStep = step;
        }
        if (step == null || !helper.queue().tick()) return;
        if (!(helper.level() instanceof ServerLevel level)) return;

        switch (step.type()) {
            case HelperStep.GATHER_WOOD, HelperStep.BOOTSTRAP_WOOD -> gatherWood(level, step);
            case HelperStep.PREPARE_KIT -> prepareKit(level, step);
            case HelperStep.PREPARE_TOOLS -> {
                var plan = HelperTrades.prepareTools(helper);
                if (plan.isEmpty()) {
                    if (HelperToolkit.missing(helper, helper.getProfession()) == null) helper.queue().advance();
                    else problem = "Бракує ресурсів для інструментів професії";
                } else helper.queue().prepend(plan);
            }
            case HelperStep.PREPARE_STORE -> {
                if (keeper.prepare(level)) helper.queue().advance();
            }
            case HelperStep.KEEPER_DELIVER -> {
                if (keeper.deliver(level)) helper.queue().advance();
            }
            case HelperStep.KEEPER_VISIT -> {
                if (keeper.visit(level)) helper.queue().advance();
            }
            case HelperStep.KEEPER_WORK -> {
                if (!kitReady()) helper.queue().prepend(List.of(new HelperStep(HelperStep.PREPARE_KIT, "", 1)));
                else if (keeper.tick(level)) helper.queue().advance();
            }
            case HelperStep.PREPARE_GROVE -> prepareGrove(level);
            case HelperStep.PREPARE_AXE -> prepareAxe();
            case HelperStep.MARK_GROVE -> markGrove(level);
            case HelperStep.GATHER_DIRT -> gatherDirt(level, step);
            case HelperStep.PLANT -> plant(level, step);
            case HelperStep.STORE -> store(level, step);
            case HelperStep.FETCH -> fetch(level, step);
            case HelperStep.CRAFT -> craft(level, step);
            case HelperStep.EQUIP -> equip(step);
            case HelperStep.TAKE_BENCH -> takeBench(level);
            case HelperStep.RECOVER_SUPPORTS -> {
                if (!helper.supports().any()) helper.queue().advance();
                else recoverSteps(level);
            }
            default -> helper.queue().advance();
        }
    }

    /** A different order invalidates tree context; temporary pickup/combat interruptions do not. */
    public void resetTask() {
        helper.breaking().reset();
        helper.getNavigation().stop();
        activeStep = null;
        keeper.reset();
        site = null;
        trunk = null;
        foot = null;
        phase = Phase.FELL;
        soilExit = null;
        soilProtected.clear();
        problem = "";
        markerRetryAfter.clear();
        nextMarkerSearch = 0;
        markerLastProgress = 0;
        markerDistance = Double.MAX_VALUE;
    }

    public void store(final net.minecraft.world.level.storage.ValueOutput output) {
        if (activeStep != helper.queue().current()) return;
        if (soilExit != null) {
            output.store("SoilExit", BlockPos.CODEC, soilExit);
            var supports = output.childrenList("SoilProtected");
            for (BlockPos pos : soilProtected) supports.addChild().store("Pos", BlockPos.CODEC, pos);
        }
        if (trunk == null || foot == null) return;
        output.putString("Phase", phase.name());
        output.store("Foot", BlockPos.CODEC, foot);
        var logs = output.childrenList("Trunk");
        for (BlockPos pos : trunk) logs.addChild().store("Pos", BlockPos.CODEC, pos);
    }

    public void load(final net.minecraft.world.level.storage.ValueInput input) {
        resetTask();
        HelperStep step = helper.queue().current();
        if (step != null && step.type().equals(HelperStep.GATHER_DIRT)) {
            soilExit = input.read("SoilExit", BlockPos.CODEC).orElse(helper.blockPosition());
            for (var child : input.childrenListOrEmpty("SoilProtected")) child.read("Pos", BlockPos.CODEC).ifPresent(soilProtected::add);
            activeStep = step;
            return;
        }
        if (step == null || !(step.type().equals(HelperStep.GATHER_WOOD) || step.type().equals(HelperStep.BOOTSTRAP_WOOD))) return;
        foot = input.read("Foot", BlockPos.CODEC).orElse(null);
        if (foot == null) return;
        trunk = new ArrayList<>();
        for (var child : input.childrenListOrEmpty("Trunk")) child.read("Pos", BlockPos.CODEC).ifPresent(trunk::add);
        try { phase = Phase.valueOf(input.getStringOr("Phase", "FELL")); }
        catch (IllegalArgumentException ignored) { phase = Phase.FELL; }
        activeStep = step;
    }

    private void useTool(final HelperToolkit.Tool tool) {
        ItemStack best = HelperToolkit.best(helper, tool);
        if (!best.isEmpty()) helper.takeInHand(best.getItem());
    }

    private int soilCount() { return HelperResources.soilCount(helper); }
    private boolean kitReady() { return HelperResources.ready(helper); }

    private boolean needsKit(final HelperStep step) {
        return switch (step.type()) {
            case HelperStep.PREPARE_GROVE, HelperStep.PREPARE_AXE, HelperStep.PREPARE_TOOLS,
                    HelperStep.MARK_GROVE, HelperStep.PREPARE_STORE, HelperStep.KEEPER_WORK,
                    HelperStep.STORE, HelperStep.GATHER_WOOD -> true;
            default -> false;
        };
    }

    private void stowKitItems() {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack held = helper.getItemInHand(hand);
            if (!held.is(net.minecraft.world.item.Items.CRAFTING_TABLE) && !held.is(ItemTags.DIRT)) continue;
            ItemStack left = helper.getBag().addItem(held.copy());
            helper.setItemInHand(hand, left);
        }
    }

    private void prepareKit(final ServerLevel level, final HelperStep step) {
        stowKitItems();
        if (helper.getBag().countItem(net.minecraft.world.item.Items.CRAFTING_TABLE) == 0) {
            if (helper.placedBench() != null) {
                helper.queue().prepend(List.of(new HelperStep(HelperStep.TAKE_BENCH, "", 1)));
                return;
            }
            if (helper.pickup().hasPendingItem(net.minecraft.world.item.Items.CRAFTING_TABLE)) return;
            var plan = HelperResources.plan(helper, net.minecraft.world.item.Items.CRAFTING_TABLE, 1);
            if (plan.refused() || plan.steps().isEmpty()) { problem = "Бракує ресурсів для власного верстака"; return; }
            helper.queue().prepend(plan.steps());
            return;
        }
        int soil = soilCount();
        if (soil < HelperSupports.CARRY) {
            for (BlockPos pos : HelperResources.storages(helper)) {
                var container = HelperContainers.containerAt(level, pos);
                if (container == null) continue;
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (stack.is(ItemTags.DIRT)) {
                        helper.queue().prepend(List.of(new HelperStep(HelperStep.FETCH,
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), HelperSupports.CARRY - soil)));
                        return;
                    }
                }
            }
            if (step.done() == 0) {
                step.reconcile(1);
                helper.queue().prepend(List.of(new HelperStep(HelperStep.GATHER_DIRT, "", HelperSupports.CARRY)));
            } else problem = "Потрібно 10 землі — немає безпечного місця для добування";
            return;
        }
        problem = "";
        helper.queue().advance();
    }

    // ---- lumberjack preparation ----

    private @Nullable BlockPos grove(final ServerLevel level) {
        return HelperSigns.nearest(level, helper.blockPosition(), helper.workRadius() + 32, HelperSigns.Mark.GROVE);
    }

    private void prepareGrove(final ServerLevel level) {
        if (grove(level) != null || HelperSigns.hasSign(helper.getBag())) {
            helper.queue().advance();
            return;
        }
        boolean bench = HelperCrafting.findBench(helper) != null;
        boolean carriedBench = helper.getBag().countItem(net.minecraft.world.item.Items.CRAFTING_TABLE) > 0;
        int logs = 0;
        List<ItemStack> carried = new ArrayList<>();
        for (int slot = 0; slot < helper.getBag().getContainerSize(); slot++) {
            ItemStack stack = helper.getBag().getItem(slot);
            carried.add(stack);
            if (stack.is(ItemTags.LOGS)) logs += stack.getCount();
        }
        var plan = HelperResources.plan(helper, net.minecraft.world.item.Items.OAK_SIGN, 1);
        boolean needsWood = plan.steps().stream().anyMatch(step -> step.type().equals(HelperStep.GATHER_WOOD));
        if (!bench && !carriedBench && logs < 5 && needsWood) {
            helper.queue().prepend(List.of(new HelperStep(HelperStep.BOOTSTRAP_WOOD, "", 5)));
            return;
        }
        if (plan.refused() || plan.steps().isEmpty()) helper.queue().clear();
        else helper.queue().prepend(plan.steps());
    }

    private void prepareAxe() {
        var plan = HelperTrades.prepareAxe(helper);
        if (plan.isEmpty()) helper.queue().clear();
        else {
            helper.queue().advance();
            helper.queue().prepend(plan);
        }
    }

    private void markGrove(final ServerLevel level) {
        if (grove(level) != null) { helper.queue().advance(); return; }
        if (!HelperSigns.hasSign(helper.getBag())) {
            helper.queue().prepend(List.of(new HelperStep(HelperStep.PREPARE_GROVE, "", 1)));
            return;
        }
        long now = level.getGameTime();
        if (site != null && !HelperSigns.canPlace(level, site)) rejectMarker(now);
        if (site == null) {
            if (now < nextMarkerSearch) return;
            nextMarkerSearch = now + 40;
            site = findMarkerSite(level);
            markerDistance = Double.MAX_VALUE;
            markerLastProgress = now;
            if (site == null) {
                problem = "Немає дерева з доступним місцем для таблички";
                return;
            }
        }
        problem = "";
        if (helper.breaking().inReach(site)) {
            helper.getNavigation().stop();
            if (HelperSigns.place(level, helper.getBag(), site, HelperSigns.Mark.GROVE)) {
                helper.queue().noteProgress();
                helper.queue().advance();
            } else rejectMarker(now);
            return;
        }
        double distance = site.distToCenterSqr(helper.position());
        if (distance + 0.1D < markerDistance) {
            markerDistance = distance;
            markerLastProgress = now;
            helper.queue().noteProgress();
        }
        if (now - markerLastProgress > 60) {
            rejectMarker(now);
            problem = "Не можу дійти — шукаю інше місце для таблички";
            return;
        }
        if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
            if (!helper.getNavigation().moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, 0, SPEED)) rejectMarker(now);
        }
    }

    private void rejectMarker(final long now) {
        if (site != null) markerRetryAfter.put(site, now + 100);
        site = null;
        nextMarkerSearch = 0;
        helper.getNavigation().stop();
    }

    private @Nullable BlockPos findMarkerSite(final ServerLevel level) {
        Vec3 centre = helper.workCentre();
        if (centre == null) return null;
        var logs = HelperTreeSearch.find(level, BlockPos.containing(centre), helper.workRadius());
        var columns = new java.util.HashSet<BlockPos>();
        for (BlockPos log : logs) {
            if (!columns.add(new BlockPos(log.getX(), 0, log.getZ()))) continue;
            if (columns.size() > 16) break;
            List<BlockPos> candidates = new ArrayList<>();
            int low = Math.min(log.getY(), helper.getBlockY()) - 3;
            int high = Math.max(log.getY(), helper.getBlockY()) + 3;
            for (int radius = 2; radius <= 4; radius++) {
                for (int y = low; y <= high; y++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos pos = new BlockPos(log.getX() + dx, y, log.getZ() + dz);
                    if (markerRetryAfter.getOrDefault(pos, 0L) > level.getGameTime()) continue;
                    if (!HelperSigns.canPlace(level, pos) || level.getBlockState(pos.below()).is(BlockTags.LOGS)) continue;
                    candidates.add(pos);
                }
            }
            candidates.sort(java.util.Comparator.comparingDouble(pos -> pos.distToCenterSqr(helper.position())));
            for (BlockPos pos : candidates) {
                if (helper.breaking().inReach(pos)) return pos;
                var path = helper.getNavigation().createPath(pos, 0);
                if (path != null && path.canReach()) return pos;
            }
        }
        return null;
    }

    // ---- felling ----

    private void gatherWood(final ServerLevel level, final HelperStep step) {
        useTool(HelperToolkit.Tool.AXE);
        // The crown is never cut: it withers once the trunk is gone. What it sheds on the way down is simply noticed.
        if (helper.tickCount % 20 == 0) {
            helper.pickup().claimLoose(level, helper.blockPosition(), GLEAN_RADIUS, HelperWorkGoal::shedByTree);
        }
        step.reconcile(helper.carriedLogs());
        if (step.finished() && (trunk == null || step.type().equals(HelperStep.BOOTSTRAP_WOOD))) {
            helper.queue().advance();
            resetTask();
            return;
        }
        if (trunk == null && step.type().equals(HelperStep.GATHER_WOOD) && step.item().isEmpty()
                && helper.getProfession() == dev.npchelper.entity.HelperProfession.LUMBERJACK) {
            if (!kitReady()) { helper.queue().prepend(List.of(new HelperStep(HelperStep.PREPARE_KIT, "", 1))); return; }
            if (HelperToolkit.best(helper, HelperToolkit.Tool.AXE).isEmpty()) {
                helper.queue().prepend(List.of(new HelperStep(HelperStep.PREPARE_AXE, "", 1)));
                return;
            }
        }
        if (trunk == null) {
            BlockPos first = findLog(level);
            if (first == null) {
                if (step.type().equals(HelperStep.BOOTSTRAP_WOOD)) helper.queue().clear();
                else helper.queue().advance();
                return;
            }
            trunk = HelperTree.trunk(level, first);
            foot = HelperTree.foot(trunk);
            phase = Phase.FELL;
        }

        switch (phase) {
            case FELL -> {
                fellTrunk(level, step);
                if (step.type().equals(HelperStep.BOOTSTRAP_WOOD) && step.finished()) {
                    // Bootstrap needs five logs, not a whole tree before an axe and marker exist.
                    helper.queue().advance();
                    resetTask();
                }
            }
            case RECOVER -> recoverSteps(level);
            case PLANT -> {
                plantAtFoot(level);
                trunk = null;
                foot = null;
                phase = Phase.FELL;
                if (step.finished()) helper.queue().advance();
            }
        }
    }

    private void fellTrunk(final ServerLevel level, final HelperStep step) {
        BlockPos log = standing(level, trunk, BlockTags.LOGS);
        if (log == null) {
            phase = Phase.RECOVER;
            return;
        }
        // Too high to reach: a step of its own dirt goes down first.
        if (HelperSupports.outOfReach(helper, log) && !helper.breaking().inReach(log)) {
            double dx = helper.getX() - (log.getX() + 0.5D), dz = helper.getZ() - (log.getZ() + 0.5D);
            if (dx * dx + dz * dz > 6.25D) {
                approach(new BlockPos(log.getX(), helper.blockPosition().getY(), log.getZ()));
                return;
            }
            if (!helper.getNavigation().isDone() && !helper.getNavigation().isStuck()) return;
            if (helper.supports().step(level, log)) helper.queue().noteProgress();
            else phase = Phase.RECOVER;
            return;
        }
        if (!helper.breaking().inReach(log)) {
            approach(log);
            return;
        }
        helper.getNavigation().stop();
        if (!helper.breaking().tick(log)) return;

        helper.queue().noteProgress();
    }

    /** What a dying crown sheds, and nothing else: the helper is not scavenging the forest floor. */
    private static boolean shedByTree(final ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) || stack.is(Items.STICK) || stack.is(Items.APPLE);
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
        if (soilExit == null) soilExit = helper.blockPosition();
        soilProtected.add(soilExit.below());
        var escape = dev.npchelper.entity.HelperNavigation.probe(helper, soilExit, 0);
        if (escape == null || !escape.canReach()) {
            problem = "Немає безпечного шляху з виїмки — копання зупинено";
            helper.breaking().reset();
            return;
        }
        protectSoilRoute(escape);
        int soil = 0;
        for (int slot = 0; slot < helper.getBag().getContainerSize(); slot++) {
            ItemStack stack = helper.getBag().getItem(slot);
            if (stack.is(ItemTags.DIRT)) soil += stack.getCount();
        }
        if (soil >= step.count()) { finishSoil(level); return; }
        useTool(HelperToolkit.Tool.SHOVEL);
        // Pickup can move the NPC after selecting a block: recheck safety at the actual swing, too.
        if (site == null || !safeSoil(level, site) || reachableSoilTread(level, site) == null) {
            helper.breaking().reset();
            site = findSoil(level);
        }
        if (site == null) { finishSoil(level); return; }
        if (!helper.breaking().inReach(site)) {
            BlockPos tread = reachableSoilTread(level, site);
            if (tread != null) approach(tread);
            return;
        }
        BlockPos tread = reachableSoilTread(level, site);
        if (tread == null) { site = null; return; }
        var treadPath = dev.npchelper.entity.HelperNavigation.probe(helper, tread, 0);
        if (treadPath != null) protectSoilRoute(treadPath);
        helper.getNavigation().stop();
        if (!helper.breaking().tick(site)) return;
        site = null;
        helper.queue().noteProgress();
        step.progress();
    }

    private void finishSoil(final ServerLevel level) {
        helper.breaking().reset();
        site = null;
        if (soilExit != null && (helper.getBlockY() < soilExit.getY() || soilExit.distToCenterSqr(helper.position()) > 2.25D)) {
            var path = dev.npchelper.entity.HelperNavigation.probe(helper, soilExit, 0);
            if (path != null && path.canReach()) {
                problem = "Повертається східцями з виїмки";
                if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) helper.getNavigation().moveTo(path, SPEED);
                return;
            }
            problem = "Вихід із виїмки заблокований";
            return;
        }
        problem = "";
        helper.queue().advance();
    }

    /** Every dug cell has a floor and an adjacent, reachable tread exactly one block higher. */
    private boolean safeSoil(final ServerLevel level, final BlockPos pos) {
        if (!level.hasChunkAt(pos) || !isSoil(level, pos)) return false;
        if (soilProtected.contains(pos) || pos.equals(helper.blockPosition().below()) || (soilExit != null && pos.equals(soilExit.below()))) return false;
        if (soilExit != null && pos.getY() < soilExit.getY() - 5) return false;
        var above = level.getBlockState(pos.above());
        if (!(above.isAir() || above.canBeReplaced()) || !above.getFluidState().isEmpty()) return false;
        if (!level.getBlockState(pos.below()).isSolid() || HelperBuildings.nearCrafted(level, pos)) return false;
        if (!clearHeadspace(level, pos.above(2))) return false;
        for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for (int rise = 0; rise <= 1; rise++) {
                if (dev.npchelper.entity.HelperShelter.standable(level, pos.relative(side).above(rise))) return true;
            }
        }
        return false;
    }

    private void protectSoilRoute(final net.minecraft.world.level.pathfinder.Path path) {
        for (int index = 0; index < path.getNodeCount(); index++) {
            var node = path.getNode(index);
            soilProtected.add(new BlockPos(node.x, node.y - 1, node.z));
        }
    }

    private @Nullable BlockPos reachableSoilTread(final ServerLevel level, final BlockPos pos) {
        for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for (int rise = 0; rise <= 1; rise++) {
                BlockPos tread = pos.relative(side).above(rise);
                if (!dev.npchelper.entity.HelperShelter.standable(level, tread)) continue;
                var path = dev.npchelper.entity.HelperNavigation.probe(helper, tread, 0);
                if (path == null || !path.canReach()) continue;
                boolean supported = true;
                for (int index = 0; index < path.getNodeCount(); index++) {
                    var node = path.getNode(index);
                    if (pos.equals(new BlockPos(node.x, node.y - 1, node.z))) { supported = false; break; }
                }
                if (supported) return tread;
            }
        }
        return null;
    }

    private boolean clearHeadspace(final ServerLevel level, final BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced());
    }

    private void plant(final ServerLevel level, final HelperStep step) {
        plantAt(level, helper.blockPosition());
        helper.queue().advance();
    }

    /** Carries the harvest to a chest or barrel with room in it. */
    private void store(final ServerLevel level, final HelperStep step) {
        if (site == null || HelperContainers.containerAt(level, site) == null) {
            site = null;
            for (BlockPos sign : HelperSigns.around(level, helper.blockPosition(), STORE_SIGN_RADIUS, HelperSigns.Mark.STORE)) {
                site = HelperContainers.findWithRoom(level, sign, 8);
                if (site != null) break;
            }
            if (site == null) site = HelperContainers.findWithRoom(level, helper.blockPosition(), STORE_RADIUS);
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
        if (wanted == null || step.finished()) { helper.queue().advance(); return; }
        helper.getNavigation().setRequiredPathLength(Math.max(96, helper.workRadius() * 2 + 16));
        if (site != null) {
            var container = HelperContainers.containerAt(level, site);
            boolean holds = false;
            if (container != null) for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(wanted)) { holds = true; break; }
            if (!holds) site = null;
        }
        if (site == null) {
            for (BlockPos pos : HelperResources.storages(helper)) {
                var container = HelperContainers.containerAt(level, pos);
                if (container == null) continue;
                boolean holds = false;
                for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(wanted)) { holds = true; break; }
                if (!holds) continue;
                var path = dev.npchelper.entity.HelperNavigation.probe(helper, pos, 1);
                if (helper.breaking().inReach(pos) || (path != null && path.canReach())) { site = pos; break; }
            }
        }
        if (site == null) { helper.queue().advance(); return; }
        if (!helper.breaking().inReach(site)) {
            if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
                var path = dev.npchelper.entity.HelperNavigation.probe(helper, site, 1);
                if (path != null && path.canReach()) helper.getNavigation().moveTo(path, SPEED);
            }
            return;
        }
        var container = HelperContainers.containerAt(level, site);
        if (container == null) { site = null; return; }
        for (int slot = 0; slot < container.getContainerSize() && !step.finished(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(wanted)) continue;
            int amount = Math.min(step.count() - step.done(), stack.getCount());
            ItemStack taken = container.removeItem(slot, amount);
            ItemStack left = helper.getBag().addItem(taken);
            int moved = taken.getCount() - left.getCount();
            if (!left.isEmpty()) {
                ItemStack overflow = HelperContainers.put(container, left);
                if (!overflow.isEmpty()) helper.pickup().drop(site, List.of(overflow));
            }
            container.setChanged();
            if (moved > 0) { step.reconcile(step.done() + moved); helper.queue().noteProgress(); }
            else { problem = "Немає місця в сумці для матеріалів"; return; }
        }
        site = null;
        if (step.finished()) helper.queue().advance();
    }

    private void craft(final ServerLevel level, final HelperStep step) {
        if (site != null && !level.getBlockState(site).is(Blocks.CRAFTING_TABLE)) site = null;
        if (site == null) site = HelperCrafting.findBench(helper);
        if (site != null && !HelperCrafting.withinBenchReach(helper, site)) {
            approach(site);
            return;
        }
        switch (HelperCrafting.craft(level, helper.getBag(), step.item(), site != null)) {
            case DONE -> {
                helper.queue().noteProgress();
                step.progress();
                if (step.finished()) helper.queue().advance();
            }
            case NEEDS_BENCH -> {
                // The planner supplies a real table; placing it spends that item.
                var placed = HelperCrafting.placeBench(level, helper.getBag(), helper.blockPosition());
                if (placed == null) helper.queue().clear();
                else {
                    helper.setPlacedBench(placed);
                    site = placed;
                    helper.queue().noteProgress();
                }
            }
            case NO_MATERIALS, NO_RECIPE -> helper.queue().clear();
            case NO_ROOM -> { /* Wait without consuming ingredients; the player can free bag space. */ }
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
        var state = level.getBlockState(pos);
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    /** Bare soil near the work, never from under what is standing on it. */
    private @Nullable BlockPos findSoil(final ServerLevel level) {
        Vec3 centre = helper.workCentre();
        if (centre == null) return null;
        BlockPos middle = BlockPos.containing(centre);
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-12, -4, -12), middle.offset(12, 4, 12))) {
            if (safeSoil(level, pos)) candidates.add(pos.immutable());
        }
        candidates.sort(java.util.Comparator.comparingDouble(pos -> pos.distToCenterSqr(helper.position())));
        for (BlockPos pos : candidates) {
            if (reachableSoilTread(level, pos) != null) return pos;
        }
        return null;
    }

    private @Nullable BlockPos findLog(final ServerLevel level) {
        Vec3 centre = helper.workCentre();
        if (centre == null) return null;
        var logs = HelperTreeSearch.find(level, BlockPos.containing(centre), helper.workRadius());
        return logs.isEmpty() ? null : logs.getFirst();
    }
}
