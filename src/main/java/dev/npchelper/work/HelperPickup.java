package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the helper breaks falls on the ground as it would for anyone, and is walked to and picked up. Each drop is marked as
 * the helper's harvest, so it takes its own work and leaves what the player threw down alone.
 */
public final class HelperPickup {
    private static final String HARVESTER = "npchelper:harvester";
    /** A drop has this long to come to rest before «there is no way to it» is believed. */
    private static final int SETTLE = 40;
    /** Unreachable loot is retried after a pause rather than after every small wander. */
    private static final int RETRY_DELAY = 20 * 30;
    private static final int GIVE_UP_AFTER = 3;
    /** Vanilla can merge our drop into an older stack with another id, so the ground is swept now and then. */
    private static final int RESCAN_RADIUS = 24;

    private final NpcHelperEntity helper;
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, Long> born = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Map<UUID, Long> retryAfter = new HashMap<>();

    private @Nullable ItemEntity chasing;
    private double bestDistance = Double.MAX_VALUE;
    private long lastProgress;

    public HelperPickup(final NpcHelperEntity helper) {
        this.helper = helper;
    }

    /** Drops what a broken block gave, as real items, and remembers them as this helper's. */
    public void drop(final BlockPos pos, final List<ItemStack> drops) {
        if (!(helper.level() instanceof ServerLevel level)) return;
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.35D, pos.getZ() + 0.5D, stack.copy());
            var random = helper.getRandom();
            item.setDeltaMovement((random.nextDouble() - 0.5D) * 0.08D, 0.12D, (random.nextDouble() - 0.5D) * 0.08D);
            item.setPickUpDelay(10);
            item.getPersistentData().store(HARVESTER, UUIDUtil.CODEC, helper.getUUID());
            if (level.addFreshEntity(item)) {
                pending.add(item.getUUID());
                born.put(item.getUUID(), level.getGameTime());
            }
        }
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Picks up whatever is already within arm's length, and sweeps for drops that vanilla merged elsewhere. */
    public void tick() {
        if (!(helper.level() instanceof ServerLevel level)) return;
        if (helper.tickCount % 20 == 0) rediscover(level);
        for (UUID id : Set.copyOf(pending)) if (gone(level, id)) forget(id);

        for (UUID id : Set.copyOf(pending)) {
            if (!(level.getEntity(id) instanceof ItemEntity item) || item.hasPickUpDelay()) continue;
            if (!touches(helper.position(), item)) continue;

            ItemStack offered = item.getItem().copy();
            ItemStack left = helper.getBag().addItem(offered);
            if (left.getCount() == offered.getCount()) continue;

            level.playSound(null, helper.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.2F, 1.1F);
            if (left.isEmpty()) {
                item.discard();
                forget(id);
            } else {
                item.setItem(left);
            }
        }
    }

    /** True while there is a drop worth walking to. */
    public boolean canChase() {
        return !(helper.level() instanceof ServerLevel level) ? false : nextTarget(level) != null;
    }

    /** One tick of walking to the nearest drop of its own. */
    public void tickChase() {
        if (!(helper.level() instanceof ServerLevel level)) return;

        if (chasing == null || !chasing.isAlive() || !pending.contains(chasing.getUUID())) {
            chasing = nextTarget(level);
            bestDistance = Double.MAX_VALUE;
            lastProgress = level.getGameTime();
        }
        if (chasing == null) return;
        if (!helper.getBag().canAddItem(chasing.getItem())) {
            stopChase();
            helper.getNavigation().stop();
            return;
        }

        helper.getLookControl().setLookAt(chasing.getX(), chasing.getY(), chasing.getZ());
        if (touches(helper.position(), chasing)) {
            helper.getNavigation().stop();
            return;
        }

        double distance = helper.distanceToSqr(chasing);
        if (distance + 0.05D < bestDistance) {
            bestDistance = distance;
            lastProgress = level.getGameTime();
        }
        // Standing still for too long: this drop is set aside rather than chased forever.
        if (level.getGameTime() - lastProgress > 60L) {
            UUID id = chasing.getUUID();
            if (attempts.merge(id, 1, Integer::sum) >= GIVE_UP_AFTER) retryAfter.put(id, level.getGameTime() + RETRY_DELAY);
            chasing = null;
            helper.getNavigation().stop();
            return;
        }
        if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
            helper.getNavigation().moveTo(chasing.getX(), chasing.getY(), chasing.getZ(), 1.0D);
        }
    }

    public boolean isChasing() { return chasing != null && chasing.isAlive(); }

    public boolean waitingForSpace() {
        if (!(helper.level() instanceof ServerLevel level)) return false;
        return pending.stream().anyMatch(id -> level.getEntity(id) instanceof ItemEntity item
                && item.isAlive() && !helper.getBag().canAddItem(item.getItem()));
    }

    public boolean hasDeferredDrops() {
        return helper.level() instanceof ServerLevel level
                && retryAfter.values().stream().anyMatch(time -> time > level.getGameTime());
    }

    public void stopChase() { chasing = null; }

    public void cancel() {
        chasing = null;
        pending.clear();
        born.clear();
        attempts.clear();
        retryAfter.clear();
    }

    private @Nullable ItemEntity nextTarget(final ServerLevel level) {
        List<ItemEntity> candidates = new ArrayList<>();
        for (UUID id : pending) {
            if (!(level.getEntity(id) instanceof ItemEntity item) || !item.isAlive() || item.hasPickUpDelay()) continue;
            if (!helper.getBag().canAddItem(item.getItem()) || helper.distanceToSqr(item) > RESCAN_RADIUS * RESCAN_RADIUS) continue;
            if (retryAfter.getOrDefault(id, 0L) > level.getGameTime()) continue;
            if (!touches(helper.position(), item)) {
                var path = helper.getNavigation().createPath(item.blockPosition(), 0);
                if (path == null || !path.canReach()) {
                    if (level.getGameTime() - born.getOrDefault(id, 0L) >= SETTLE) {
                        retryAfter.put(id, level.getGameTime() + RETRY_DELAY);
                    }
                    continue;
                }
            }
            candidates.add(item);
        }
        return candidates.stream().min(Comparator.comparingDouble(helper::distanceToSqr)).orElse(null);
    }

    /**
     * Reach as a player has it: the body widened by a block sideways and half a block up and down. A plain distance check
     * misses a drop lying on a ledge at head height or in a dip at the feet.
     */
    private boolean touches(final Vec3 at, final ItemEntity item) {
        return helper.getBoundingBox().move(at.subtract(helper.position())).inflate(1.0D, 0.5D, 1.0D)
                .intersects(item.getBoundingBox());
    }

    /** Finds this helper's harvest again after vanilla merged it into another stack. */
    private void rediscover(final ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, helper.getBoundingBox().inflate(RESCAN_RADIUS))) {
            if (!item.isAlive() || pending.contains(item.getUUID())) continue;
            if (!item.getPersistentData().read(HARVESTER, UUIDUtil.CODEC).filter(helper.getUUID()::equals).isPresent()) continue;
            pending.add(item.getUUID());
            born.putIfAbsent(item.getUUID(), level.getGameTime());
        }
    }

    private boolean gone(final ServerLevel level, final UUID id) {
        var item = level.getEntity(id);
        // A moment's grace: a drop that merged into another stack is found again by the sweep.
        return item == null ? level.getGameTime() - born.getOrDefault(id, 0L) > SETTLE : !item.isAlive();
    }

    private void forget(final UUID id) {
        pending.remove(id);
        born.remove(id);
        attempts.remove(id);
        retryAfter.remove(id);
    }
}
