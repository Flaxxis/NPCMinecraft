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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** One real stack per trip, kept in the visible, saved cargo slot until a destination accepts it. */
public final class HelperKeeper {
    public static final int WAREHOUSE_RADIUS = 16;
    /** Something on the ground is left this long before it counts as lying about rather than just dropped. */
    private static final int SETTLE_AGE = 20 * 10;
    /** Free bag slots below which a round of gathering stops for a walk to the warehouse. */
    private static final int BAG_MARGIN = 2;
    /** How long each turn lasts before the other gets its go: gathering off the ground, then tending the chests. */
    private static final int GATHER_TURN = 20 * 30, CHEST_TURN = 20 * 30;
    /** An item it could not get to is left alone this long before it is tried again. */
    private static final int LOOSE_RETRY_TICKS = 20 * 30;

    private final NpcHelperEntity helper;
    private @Nullable ItemEntity looseTarget;
    private @Nullable ItemEntity chasedLoose;
    private double bestLooseDistance;
    private long looseProgress;
    private final Map<UUID, Long> looseRetryAfter = new HashMap<>();
    /** Whose turn it is, and when it is up; -1 until the first turn starts. */
    private boolean gathering = true;
    private long turnEnds = -1;
    /** The last gathering round found nothing lying about. */
    private boolean groundClear;
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
        gathering = true;
        turnEnds = -1;
        groundClear = false;
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

    /**
     * Where a thing is best put, lowest first: topping up a part stack of the same → a chest that already holds it → an
     * empty chest → and, failing all those, any warehouse chest with a free slot, so a warehouse never shuts for want of a
     * tidy place while there is still room in it.
     */
    private int rank(final Container container, final ItemStack stack) {
        if (HelperContainers.matchingRoom(container, stack) > 0) return 0;
        if (HelperContainers.contains(container, stack)) return 1;
        return container.isEmpty() ? 2 : 3;
    }

    private @Nullable BlockPos destination(final ServerLevel level, final List<BlockPos> stores, final ItemStack stack) {
        BlockPos best = null;
        // Every rank is acceptable; the order only says which is preferred.
        int score = 4;
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

        // A nearly full bag goes to the warehouse first; the round resumes afterwards.
        if (freeSlots() < BAG_MARGIN) return false;
        if (looseTarget != null && (!looseTarget.isAlive() || !worthTaking(level, looseTarget, stores))) looseTarget = null;
        if (looseTarget == null) looseTarget = nearestLoose(level, centre, stores);
        if (looseTarget == null) return false;

        ItemEntity item = looseTarget;
        if (touches(item)) {
            helper.getNavigation().stop();
            // Into the bag, so one round picks up a good many things before the walk to the warehouse.
            ItemStack lying = item.getItem().copy();
            ItemStack left = helper.getBag().addItem(lying);
            if (left.getCount() < item.getItem().getCount()) {
                if (left.isEmpty()) item.discard();
                else item.setItem(left);
                level.playSound(null, helper.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.2F, 1.1F);
                helper.queue().noteProgress();
            }
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

    /**
     * Anything that has lain ten seconds is fair game — another helper's harvest included, since it ends up in the warehouse
     * either way. Only what is burning or in lava is let go: it is gone already, and walking in after it is how a helper dies.
     * Whether the warehouse has room does not matter: in the bag nothing despawns, on the ground everything does.
     */
    private boolean worthTaking(final ServerLevel level, final ItemEntity item, final List<BlockPos> stores) {
        if (!item.isAlive() || item.hasPickUpDelay() || item.getAge() < SETTLE_AGE) return false;
        if (looseRetryAfter.containsKey(item.getUUID())) return false;
        return !item.isInLava() && !item.isOnFire();
    }

    /**
     * Carries what it has gathered into the warehouse, one chest at a time. Its own kit — the dirt for a pillar and one
     * bench — stays in the bag; everything over that goes in. True while there is still something to take there.
     */
    private boolean unloadBag(final ServerLevel level, final List<BlockPos> stores) {
        var bag = helper.getBag();
        int dirtToKeep = HelperSupports.CARRY;
        int benchesToKeep = 1;
        boolean nowhere = false;

        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (stack.isEmpty()) continue;

            int keep = 0;
            if (stack.is(net.minecraft.tags.ItemTags.DIRT)) {
                keep = Math.min(stack.getCount(), dirtToKeep);
                dirtToKeep -= keep;
            } else if (stack.is(Items.CRAFTING_TABLE)) {
                keep = Math.min(stack.getCount(), benchesToKeep);
                benchesToKeep -= keep;
            }
            int spare = stack.getCount() - keep;
            if (spare <= 0) continue;

            ItemStack load = stack.copyWithCount(spare);
            BlockPos target = destination(level, stores, load);
            if (target == null) {
                nowhere = true;
                continue;
            }
            if (!walk(level, target)) return true;

            Container container = HelperContainers.containerAt(level, target);
            if (container == null) return true;
            ItemStack left = HelperContainers.put(container, load);
            int moved = spare - left.getCount();
            if (moved > 0) {
                stack.shrink(moved);
                bag.setChanged();
                helper.queue().noteProgress();
            }
            return true;
        }
        // Kept, not dropped: in the bag it lasts, and the warehouse may have room again later.
        if (nowhere) problem = "Склад повний: тримаю зібране в сумці";
        return false;
    }

    // ---- the round of the warehouse, every three minutes ----

    /** The stages of a round, in order: what was gathered goes in, the furnaces are emptied, then loaded again. */
    private enum Visit { UNLOAD, COLLECT, SMELT, DONE }

    /** Furnace slots, as the game numbers them. */
    private static final int FURNACE_INPUT = 0, FURNACE_FUEL = 1, FURNACE_RESULT = 2;
    /** One coal smelts eight items, in a furnace and in a blast furnace alike. */
    private static final int PER_COAL = 8;

    private Visit visit = Visit.DONE;
    private @Nullable List<BlockPos> visitFurnaces;
    private final java.util.Set<BlockPos> loadedThisVisit = new java.util.HashSet<>();
    private @Nullable BlockPos loadFurnace;
    private @Nullable Item loadOre;
    private int loadOreCount, loadCoalCount;

    /** Starts a round from the beginning. */
    public void beginVisit() {
        visit = Visit.UNLOAD;
        visitFurnaces = null;
        loadedThisVisit.clear();
        loadFurnace = null;
        loadOre = null;
    }

    /**
     * One step of the round. True when it is over. With no furnace about, the round is just the unloading: smelting is
     * only ever done where there is something to smelt in.
     */
    public boolean visit(final ServerLevel level) {
        enableNavigation();
        if (!helper.getCargo().isEmpty()) { deliver(level); return false; }
        if (findWarehouse(level) == null) {
            problem = "Немає складу: потрібна табличка «склад» біля скрині";
            return true;
        }
        List<BlockPos> stores = warehouseChests(level);
        switch (visit) {
            case UNLOAD -> {
                if (!unloadBag(level, stores)) visit = Visit.COLLECT;
                return false;
            }
            case COLLECT -> {
                if (collectFurnaces(level, stores)) visit = Visit.SMELT;
                return false;
            }
            case SMELT -> {
                if (loadFurnaces(level, stores)) visit = Visit.DONE;
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    /** Furnaces and blast furnaces about the warehouse, blast furnaces first: looked for once a round. */
    private List<BlockPos> furnaces(final ServerLevel level) {
        if (visitFurnaces == null) visitFurnaces = findFurnaces(level);
        return visitFurnaces;
    }

    private List<BlockPos> findFurnaces(final ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(warehouse.offset(-WAREHOUSE_RADIUS, -4, -WAREHOUSE_RADIUS),
                warehouse.offset(WAREHOUSE_RADIUS, 4, WAREHOUSE_RADIUS))) {
            var entity = level.getBlockEntity(pos);
            if (entity instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity
                    || entity instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity) found.add(pos.immutable());
        }
        found.sort(Comparator.comparingInt((BlockPos pos) ->
                level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity ? 0 : 1)
                .thenComparingDouble(pos -> pos.distSqr(warehouse)));
        return found;
    }

    /** What has finished smelting comes out and goes into the chests. True once every furnace is empty of it. */
    private boolean collectFurnaces(final ServerLevel level, final List<BlockPos> stores) {
        for (BlockPos pos : furnaces(level)) {
            Container furnace = HelperContainers.furnaceAt(level, pos);
            if (furnace == null || furnace.getItem(FURNACE_RESULT).isEmpty() || !reachable(level, pos)) continue;
            if (freeSlots() == 0) break;
            if (!walk(level, pos)) return false;
            ItemStack ready = furnace.getItem(FURNACE_RESULT);
            ItemStack left = helper.getBag().addItem(ready.copy());
            furnace.setItem(FURNACE_RESULT, left);
            furnace.setChanged();
            helper.queue().noteProgress();
            return false;
        }
        return !unloadBag(level, stores);
    }

    /** Raw metal and metal ore: what a blast furnace is for. Nothing else is put to the fire. */
    private static boolean ore(final ItemStack stack) {
        return stack.is(Items.RAW_IRON) || stack.is(Items.RAW_GOLD) || stack.is(Items.RAW_COPPER)
                || stack.is(net.minecraft.tags.ItemTags.IRON_ORES) || stack.is(net.minecraft.tags.ItemTags.GOLD_ORES)
                || stack.is(net.minecraft.tags.ItemTags.COPPER_ORES);
    }

    private static boolean coal(final ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.COALS);
    }

    /**
     * Loads ore and coal into the furnaces, a furnace at a time, the ore shared out between them so they all work at once.
     * Coal goes in only as much as the ore needs, on top of what is in the furnace already. True once there is no ore, no
     * coal, or no furnace that will take any.
     */
    private boolean loadFurnaces(final ServerLevel level, final List<BlockPos> stores) {
        List<BlockPos> furnaces = furnaces(level);
        if (furnaces.isEmpty()) return true;

        if (loadFurnace == null && !planLoad(level, stores, furnaces)) return true;
        BlockPos target = loadFurnace;

        // First fetch what the load needs out of the chests, then carry it over.
        if (helper.getBag().countItem(loadOre) < loadOreCount) return fetch(level, stores, stack -> stack.is(loadOre), loadOreCount);
        if (countCoal() < loadCoalCount) return fetch(level, stores, HelperKeeper::coal, loadCoalCount);

        if (!walk(level, target)) return false;
        Container furnace = HelperContainers.furnaceAt(level, target);
        if (furnace != null) {
            if (loadCoalCount > 0) {
                ItemStack fuel = takeFromBag(HelperKeeper::coal, loadCoalCount);
                ItemStack inFuel = furnace.getItem(FURNACE_FUEL);
                if (inFuel.isEmpty()) furnace.setItem(FURNACE_FUEL, fuel);
                else if (ItemStack.isSameItemSameComponents(inFuel, fuel)) inFuel.grow(fuel.getCount());
                else helper.getBag().addItem(fuel);
            }
            ItemStack load = takeFromBag(stack -> stack.is(loadOre), loadOreCount);
            ItemStack inInput = furnace.getItem(FURNACE_INPUT);
            if (inInput.isEmpty()) furnace.setItem(FURNACE_INPUT, load);
            else if (ItemStack.isSameItemSameComponents(inInput, load)) inInput.grow(load.getCount());
            else helper.getBag().addItem(load);
            furnace.setChanged();
            helper.queue().noteProgress();
        }
        loadedThisVisit.add(target);
        loadFurnace = null;
        return false;
    }

    /** Chooses the next furnace and how much ore and coal it gets. False when nothing more can be smelted this round. */
    private boolean planLoad(final ServerLevel level, final List<BlockPos> stores, final List<BlockPos> furnaces) {
        java.util.Map<Item, Integer> ores = new java.util.LinkedHashMap<>();
        int coalStored = 0;
        for (BlockPos pos : stores) {
            Container container = HelperContainers.containerAt(level, pos);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (ore(stack)) ores.merge(stack.getItem(), stack.getCount(), Integer::sum);
                else if (coal(stack)) coalStored += stack.getCount();
            }
        }
        if (ores.isEmpty()) return false;

        List<BlockPos> open = new ArrayList<>();
        for (BlockPos pos : furnaces) {
            if (loadedThisVisit.contains(pos) || !reachable(level, pos)) continue;
            Container furnace = HelperContainers.furnaceAt(level, pos);
            if (furnace == null) continue;
            ItemStack fuel = furnace.getItem(FURNACE_FUEL);
            // A furnace burning something else is somebody's: it is left as it is.
            if (!fuel.isEmpty() && !coal(fuel)) continue;
            open.add(pos);
        }
        if (open.isEmpty()) return false;

        for (BlockPos pos : open) {
            Container furnace = HelperContainers.furnaceAt(level, pos);
            ItemStack input = furnace.getItem(FURNACE_INPUT);
            ItemStack fuel = furnace.getItem(FURNACE_FUEL);
            // The ore already in it decides what more goes in; an empty one takes whatever there is most of.
            Item kind = input.isEmpty()
                    ? ores.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey()
                    : input.getItem();
            if (!input.isEmpty() && (!ore(input) || !ores.containsKey(kind))) continue;

            int available = ores.getOrDefault(kind, 0);
            // Shared out over the furnaces still open, so they all work at once.
            int share = (available + open.size() - 1) / open.size();
            // Room is measured by the ore's own stack size: an empty slot would report one, the size of nothing.
            int room = new ItemStack(kind).getMaxStackSize() - input.getCount();
            int amount = Math.min(Math.min(share, available), room);
            int inFurnace = input.getCount() + amount;
            int coalNeeded = Math.max(0, (inFurnace + PER_COAL - 1) / PER_COAL - fuel.getCount());
            if (coalNeeded > coalStored) {
                coalNeeded = coalStored;
                amount = Math.min(amount, (fuel.getCount() + coalNeeded) * PER_COAL - input.getCount());
            }
            if (amount <= 0) continue;

            loadFurnace = pos;
            loadOre = kind;
            loadOreCount = amount;
            loadCoalCount = coalNeeded;
            return true;
        }
        return false;
    }

    /** Goes to the nearest warehouse chest holding what is wanted and takes up to the count the load needs. */
    private boolean fetch(final ServerLevel level, final List<BlockPos> stores,
                          final java.util.function.Predicate<ItemStack> wanted, final int target) {
        for (BlockPos pos : stores) {
            Container container = HelperContainers.containerAt(level, pos);
            if (container == null || !reachable(level, pos)) continue;
            boolean holds = false;
            for (int slot = 0; slot < container.getContainerSize() && !holds; slot++) holds = wanted.test(container.getItem(slot));
            if (!holds) continue;
            if (!walk(level, pos)) return false;
            int missing = target - countInBag(wanted);
            for (int slot = 0; slot < container.getContainerSize() && missing > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!wanted.test(stack)) continue;
                ItemStack taken = container.removeItem(slot, Math.min(missing, stack.getCount()));
                ItemStack left = helper.getBag().addItem(taken);
                missing -= taken.getCount() - left.getCount();
                if (!left.isEmpty()) container.setItem(slot, left);
            }
            container.setChanged();
            helper.queue().noteProgress();
            return false;
        }
        // The chests ran short while it walked: what it has is all the furnace gets.
        loadOreCount = Math.min(loadOreCount, helper.getBag().countItem(loadOre));
        loadCoalCount = Math.min(loadCoalCount, countCoal());
        if (loadOreCount <= 0) loadFurnace = null;
        return false;
    }

    private int countCoal() {
        return countInBag(HelperKeeper::coal);
    }

    private int countInBag(final java.util.function.Predicate<ItemStack> wanted) {
        int count = 0;
        var bag = helper.getBag();
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            if (wanted.test(bag.getItem(slot))) count += bag.getItem(slot).getCount();
        }
        return count;
    }

    /** Takes up to the count of what is wanted out of the bag, as one stack. */
    private ItemStack takeFromBag(final java.util.function.Predicate<ItemStack> wanted, final int count) {
        ItemStack taken = ItemStack.EMPTY;
        var bag = helper.getBag();
        for (int slot = 0; slot < bag.getContainerSize() && (taken.isEmpty() || taken.getCount() < count); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (!wanted.test(stack) || (!taken.isEmpty() && !ItemStack.isSameItemSameComponents(taken, stack))) continue;
            int amount = Math.min(stack.getCount(), count - (taken.isEmpty() ? 0 : taken.getCount()));
            if (taken.isEmpty()) taken = stack.copyWithCount(amount);
            else taken.grow(amount);
            stack.shrink(amount);
        }
        bag.setChanged();
        return taken;
    }

    private int freeSlots() {
        int free = 0;
        var bag = helper.getBag();
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            if (bag.getItem(slot).isEmpty()) free++;
        }
        return free;
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

    /**
     * True means there is nothing left to do: no chest work and nothing lying about.
     *
     * The keeper takes turns: a round of gathering off the ground and carrying it in, then a turn at the chests, then the
     * ground again. In a lived-in settlement something always lies about, so gathering first and always would mean the
     * chests were never tended; tending the chests first and always would leave the ground to despawn. A walk or a chase
     * under way is finished before the turn changes, so a long trip is never turned back halfway.
     */
    public boolean tick(final ServerLevel level) {
        enableNavigation();
        problem = "";
        // Something in hand is always delivered first, whichever turn it is.
        if (!helper.getCargo().isEmpty()) { deliver(level); return false; }
        if (findWarehouse(level) == null) { prepare(level); return false; }
        List<BlockPos> stores = warehouseChests(level);
        long now = level.getGameTime();
        if (turnEnds < 0) turnEnds = now + GATHER_TURN;

        if (gathering) {
            // At night the keeper keeps to the warehouse: the chests and furnaces, not the dark beyond them.
            boolean busy = !dev.npchelper.entity.HelperNightGoal.night(helper) && sweepGround(level, stores);
            if (busy && (now < turnEnds || looseTarget != null)) return false;
            // What was gathered goes in, all in one walk rather than a walk per item.
            if (unloadBag(level, stores)) return false;
            groundClear = !busy;
            gathering = false;
            turnEnds = now + CHEST_TURN;
        }

        boolean chestIdle = tendChests(level, stores);
        if (!chestIdle && (now < turnEnds || walkTarget != null)) return false;
        gathering = true;
        turnEnds = now + GATHER_TURN;
        return chestIdle && groundClear;
    }

    /** One step of the work at the chests. True means none is left. */
    private boolean tendChests(final ServerLevel level, final List<BlockPos> stores) {
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
