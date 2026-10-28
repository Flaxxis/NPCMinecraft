package dev.npchelper.work;

import dev.npchelper.entity.HelperNavigation;
import dev.npchelper.entity.NpcHelperEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** One real stack per trip, kept in the visible, saved cargo slot until a destination accepts it. */
public final class HelperKeeper {
    public static final int WAREHOUSE_RADIUS = 16;
    /** Something on the ground is left this long before it counts as lying about rather than just dropped. */
    private static final int SETTLE_AGE = 20 * 10;
    /** Another helper's harvest that has lain this long has plainly been given up on. */
    private static final int ABANDONED_AGE = 20 * 120;
    /** An item it could not get to is left alone this long before it is tried again. */
    private static final int LOOSE_RETRY_TICKS = 20 * 30;

    private final NpcHelperEntity helper;
    private @Nullable ItemEntity looseTarget;
    private @Nullable ItemEntity chasedLoose;
    private double bestLooseDistance;
    private long looseProgress;
    private final Map<UUID, Long> looseRetryAfter = new HashMap<>();
    private @Nullable BlockPos warehouse;
    private @Nullable BlockPos walkTarget;
    private final Map<BlockPos, Long> retryAfter = new HashMap<>();
    private long lastProgress;
    private double bestDistance;
    private String problem = "";
    private boolean navigating;

    public HelperKeeper(final NpcHelperEntity helper) { this.helper = helper; }
    public String problem() { return problem; }
    public void reset() {
        warehouse = null;
        walkTarget = null;
        if (navigating) helper.getNavigation().setRequiredPathLength(0);
        navigating = false;
        problem = "";
        retryAfter.clear();
        looseTarget = null;
        chasedLoose = null;
        looseRetryAfter.clear();
    }

    private void enableNavigation() {
        if (!navigating) {
            helper.getNavigation().setRequiredPathLength(Math.max(96, helper.workRadius() * 2 + 16));
            navigating = true;
        }
    }

    private boolean arrived(final BlockPos pos) { return pos.distToCenterSqr(helper.position()) <= 6.25D; }
    private boolean reachable(final ServerLevel level, final BlockPos pos) {
        if (retryAfter.getOrDefault(pos, 0L) > level.getGameTime()) return false;
        if (arrived(pos)) return true;
        var path = HelperNavigation.probe(helper, pos, 1);
        return path != null && path.canReach();
    }

    private boolean walk(final ServerLevel level, final BlockPos pos) {
        enableNavigation();
        if (arrived(pos)) { helper.getNavigation().stop(); walkTarget = null; return true; }
        if (!pos.equals(walkTarget)) {
            helper.getNavigation().stop();
            walkTarget = pos;
            lastProgress = level.getGameTime();
            bestDistance = Double.MAX_VALUE;
        }
        double distance = pos.distToCenterSqr(helper.position());
        if (distance + 0.1D < bestDistance) {
            bestDistance = distance;
            lastProgress = level.getGameTime();
            helper.queue().noteProgress();
        }
        if (level.getGameTime() - lastProgress > 100) {
            retryAfter.put(pos, level.getGameTime() + 100);
            walkTarget = null;
            helper.getNavigation().stop();
            problem = "Шлях до сховища заблокований";
            return false;
        }
        if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
            var path = HelperNavigation.probe(helper, pos, 1);
            if (path == null || !path.canReach()) {
                retryAfter.put(pos, level.getGameTime() + 100);
                problem = "Не можу дійти до сховища";
            } else helper.getNavigation().moveTo(path, 1.0D);
        }
        return false;
    }

    private @Nullable BlockPos findWarehouse(final ServerLevel level) {
        if (warehouse != null && HelperSigns.markAt(level, warehouse) == HelperSigns.Mark.STORE
                && !HelperContainers.around(level, warehouse, WAREHOUSE_RADIUS).isEmpty()) return warehouse;
        warehouse = null;
        if (helper.centre() == null) return null;
        for (BlockPos sign : HelperSigns.around(level, BlockPos.containing(helper.centre()), 64, HelperSigns.Mark.STORE)) {
            if (!HelperContainers.around(level, sign, WAREHOUSE_RADIUS).isEmpty()) { warehouse = sign; break; }
        }
        return warehouse;
    }

    /** Returns true only once a real marker exists beside a real storage container. */
    public boolean prepare(final ServerLevel level) {
        enableNavigation();
        problem = "";
        if (findWarehouse(level) != null) return true;
        if (helper.centre() == null) { problem = "Не визначено область переміщення"; return false; }
        var chests = HelperContainers.around(level, BlockPos.containing(helper.centre()), helper.workRadius());
        if (chests.isEmpty()) { problem = "У радіусі переміщення немає скрині або діжки"; return false; }
        if (!HelperSigns.hasSign(helper.getBag())) {
            var plan = HelperResources.plan(helper, Items.OAK_SIGN, 1);
            if (plan.refused() || plan.steps().isEmpty()) problem = "Бракує матеріалів для таблички «склад»";
            else helper.queue().prepend(plan.steps());
            return false;
        }
        for (BlockPos chest : chests) {
            if (HelperContainers.containerAt(level, chest) == null) continue;
            List<BlockPos> candidates = new ArrayList<>();
            for (BlockPos cursor : BlockPos.betweenClosed(chest.offset(-2, -2, -2), chest.offset(2, 2, 2))) {
                if (HelperSigns.canPlace(level, cursor)) candidates.add(cursor.immutable());
            }
            candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(helper.position())));
            for (BlockPos pos : candidates) {
                if (!reachable(level, pos)) continue;
                if (!walk(level, pos)) return false;
                if (HelperSigns.place(level, helper.getBag(), pos, HelperSigns.Mark.STORE)) {
                    warehouse = pos;
                    helper.queue().noteProgress();
                    return true;
                }
            }
        }
        problem = "Немає доступного місця для таблички біля скринь";
        return false;
    }

    private List<BlockPos> warehouseChests(final ServerLevel level) {
        if (findWarehouse(level) == null) return List.of();
        List<BlockPos> result = new ArrayList<>(HelperContainers.around(level, warehouse, WAREHOUSE_RADIUS));
        result.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(warehouse)).thenComparingLong(BlockPos::asLong));
        return result;
    }

    private int rank(final Container container, final ItemStack stack) {
        if (HelperContainers.matchingRoom(container, stack) > 0) return 0;
        if (HelperContainers.contains(container, stack)) return 1;
        return container.isEmpty() ? 2 : 3;
    }

    private @Nullable BlockPos destination(final ServerLevel level, final List<BlockPos> stores, final ItemStack stack) {
        BlockPos best = null;
        int score = 3;
        for (BlockPos pos : stores) {
            Container container = HelperContainers.containerAt(level, pos);
            if (container == null || HelperContainers.room(container, stack) <= 0 || !reachable(level, pos)) continue;
            int candidate = rank(container, stack);
            if (candidate < score) { score = candidate; best = pos; }
        }
        return best;
    }

    public boolean deliver(final ServerLevel level) {
        enableNavigation();
        ItemStack cargo = helper.getCargo().getItem(0);
        if (cargo.isEmpty()) return true;
        if (findWarehouse(level) == null) {
            if (!prepare(level)) return false;
        }
        BlockPos target = destination(level, warehouseChests(level), cargo);
        if (target == null) { problem = "Немає місця: потрібна скриня з таким ресурсом або порожня"; return false; }
        problem = "";
        if (!walk(level, target)) return false;
        Container container = HelperContainers.containerAt(level, target);
        if (container == null) return false;
        ItemStack left = HelperContainers.put(container, cargo);
        if (left.getCount() < cargo.getCount()) {
            helper.getCargo().setItem(0, left);
            helper.queue().noteProgress();
        }
        return helper.getCargo().isEmpty();
    }

    /**
     * Picks up what lies about within its range and has a place in the warehouse. Something just thrown down is let lie a
     * moment, so the player is not robbed of what they dropped a second ago, and another helper's harvest is left for that
     * helper — unless it has plainly been abandoned. True while a pick-up is under way.
     */
    private boolean sweepGround(final ServerLevel level, final List<BlockPos> stores) {
        var centre = helper.centre();
        if (centre == null) return false;

        if (looseTarget != null && (!looseTarget.isAlive() || !worthTaking(level, looseTarget, stores))) looseTarget = null;
        if (looseTarget == null) looseTarget = nearestLoose(level, centre, stores);
        if (looseTarget == null) return false;

        ItemEntity item = looseTarget;
        if (touches(item)) {
            helper.getNavigation().stop();
            ItemStack lying = item.getItem();
            int amount = Math.min(lying.getCount(), lying.getMaxStackSize());
            helper.getCargo().setItem(0, lying.copyWithCount(amount));
            if (amount >= lying.getCount()) item.discard();
            else item.setItem(lying.copyWithCount(lying.getCount() - amount));
            level.playSound(null, helper.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.2F, 1.1F);
            helper.queue().noteProgress();
            looseTarget = null;
            return true;
        }

        enableNavigation();
        double distance = helper.distanceToSqr(item);
        if (item != chasedLoose) {
            chasedLoose = item;
            bestLooseDistance = Double.MAX_VALUE;
            looseProgress = level.getGameTime();
        }
        if (distance + 0.1D < bestLooseDistance) {
            bestLooseDistance = distance;
            looseProgress = level.getGameTime();
            helper.queue().noteProgress();
        }
        // No closer in five seconds: set it aside for a while instead of circling it forever.
        if (level.getGameTime() - looseProgress > 100) {
            looseRetryAfter.put(item.getUUID(), level.getGameTime() + LOOSE_RETRY_TICKS);
            looseTarget = null;
            chasedLoose = null;
            helper.getNavigation().stop();
            return false;
        }
        if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
            helper.getNavigation().moveTo(item, 1.0D);
        }
        return true;
    }

    private @Nullable ItemEntity nearestLoose(final ServerLevel level, final net.minecraft.world.phys.Vec3 centre,
                                              final List<BlockPos> stores) {
        double radius = helper.workRadius();
        var box = new net.minecraft.world.phys.AABB(centre, centre).inflate(radius, Math.min(radius, 16.0D), radius);
        looseRetryAfter.values().removeIf(until -> until < level.getGameTime());
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (item.position().distanceTo(centre) > radius || !worthTaking(level, item, stores)) continue;
            double distance = helper.distanceToSqr(item);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    private boolean worthTaking(final ServerLevel level, final ItemEntity item, final List<BlockPos> stores) {
        if (!item.isAlive() || item.hasPickUpDelay() || item.getAge() < SETTLE_AGE) return false;
        if (looseRetryAfter.containsKey(item.getUUID())) return false;
        // Burning or in lava: it is going anyway, and walking in after it is how a helper dies.
        if (item.isInLava() || item.isOnFire()) return false;
        // Another helper's harvest is that helper's, until it has lain long enough to be plainly forgotten.
        if (HelperPickup.claimedByAnother(item, helper) && item.getAge() < ABANDONED_AGE) return false;
        return destination(level, stores, item.getItem()) != null;
    }

    /** The player's own reach: the body widened by a block sideways and half a block up and down. */
    private boolean touches(final ItemEntity item) {
        return helper.getBoundingBox().inflate(1.0D, 0.5D, 1.0D).intersects(item.getBoundingBox());
    }

    private void withdraw(final Container source, final int slot, final int count) {
        if (!helper.getCargo().isEmpty() || count <= 0) return;
        ItemStack moved = source.removeItem(slot, count);
        if (!moved.isEmpty()) {
            helper.getCargo().setItem(0, moved);
            source.setChanged();
            helper.queue().noteProgress();
            helper.getNavigation().stop();
            walkTarget = null;
        }
    }

    /** True means this sweep has no remaining reachable transfer or compaction. */
    public boolean tick(final ServerLevel level) {
        enableNavigation();
        problem = "";
        if (!helper.getCargo().isEmpty()) { deliver(level); return false; }
        if (findWarehouse(level) == null) { prepare(level); return false; }
        List<BlockPos> stores = warehouseChests(level);
        // Things lying on the ground come first: left there, they are gone in five minutes.
        if (sweepGround(level, stores)) return false;
        var scanned = new LinkedHashSet<>(stores);
        if (helper.centre() != null) scanned.addAll(HelperContainers.around(level, BlockPos.containing(helper.centre()), helper.workRadius()));
        // Merge duplicate slots within every inspected chest, while actually standing beside it.
        for (BlockPos pos : scanned) {
            Container container = HelperContainers.containerAt(level, pos);
            if (container == null || !HelperContainers.needsCompaction(container) || !reachable(level, pos)) continue;
            if (!walk(level, pos)) return false;
            if (HelperContainers.compact(container)) helper.queue().noteProgress();
            return false;
        }
        // Consolidate partial stacks between warehouse chests in a stable direction; never shuffle them back.
        for (int donor = 1; donor < stores.size(); donor++) {
            BlockPos from = stores.get(donor);
            Container source = HelperContainers.containerAt(level, from);
            if (source == null || !reachable(level, from)) continue;
            for (int slot = 0; slot < source.getContainerSize(); slot++) {
                ItemStack stack = source.getItem(slot);
                if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) continue;
                for (int receiver = 0; receiver < donor; receiver++) {
                    BlockPos to = stores.get(receiver);
                    Container target = HelperContainers.containerAt(level, to);
                    if (target == null || !reachable(level, to)) continue;
                    int room = HelperContainers.matchingRoom(target, stack);
                    if (room <= 0) continue;
                    if (!walk(level, from)) return false;
                    withdraw(source, slot, Math.min(stack.getCount(), room));
                    return false;
                }
            }
        }
        // Only the outer annulus supplies the warehouse: radius is measured from the STORE sign.
        if (helper.centre() == null) return true;
        for (BlockPos from : HelperContainers.around(level, BlockPos.containing(helper.centre()), helper.workRadius())) {
            long dx = from.getX() - warehouse.getX(), dz = from.getZ() - warehouse.getZ();
            if (dx * dx + dz * dz <= WAREHOUSE_RADIUS * WAREHOUSE_RADIUS || stores.contains(from) || !reachable(level, from)) continue;
            Container source = HelperContainers.containerAt(level, from);
            if (source == null) continue;
            for (int slot = 0; slot < source.getContainerSize(); slot++) {
                ItemStack stack = source.getItem(slot);
                if (stack.isEmpty()) continue;
                BlockPos to = destination(level, stores, stack);
                if (to == null) continue;
                Container target = HelperContainers.containerAt(level, to);
                if (target == null) continue;
                int amount = Math.min(stack.getCount(), HelperContainers.room(target, stack));
                if (amount <= 0) continue;
                if (!walk(level, from)) return false;
                withdraw(source, slot, amount);
                return false;
            }
        }
        return true;
    }
}
