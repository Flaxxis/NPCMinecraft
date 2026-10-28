package dev.npchelper.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jspecify.annotations.Nullable;

/** Chests and barrels around a spot: where the helper looks before it makes anything of its own. */
public final class HelperContainers {
    /** Scanned in slabs this tall around the centre, so a cellar below is found as well as a shed above. */
    private static final int HEIGHT = 8;

    private HelperContainers() { }

    /** Every chest and barrel within the radius, nearest first. */
    public static List<BlockPos> around(final Level level, final BlockPos centre, final int radius) {
        List<BlockPos> found = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return found;
        for (int x = (centre.getX() - radius) >> 4; x <= (centre.getX() + radius) >> 4; x++) {
            for (int z = (centre.getZ() - radius) >> 4; z <= (centre.getZ() + radius) >> 4; z++) {
                var chunk = server.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (!(entry.getValue() instanceof ChestBlockEntity) && !(entry.getValue() instanceof BarrelBlockEntity)) continue;
                    long dx = pos.getX() - centre.getX(), dz = pos.getZ() - centre.getZ();
                    if (Math.abs(pos.getY() - centre.getY()) <= HEIGHT && dx * dx + dz * dz <= (long) radius * radius) {
                        BlockPos canonical = canonical(level, pos);
                        if (seen.add(canonical)) found.add(canonical);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(centre)));
        return found;
    }

    public static boolean isStorage(final Level level, final BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity;
    }

    public static @Nullable Container containerAt(final Level level, final BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest) {
            return net.minecraft.world.level.block.ChestBlock.getContainer(chest, state, level, pos, false);
        }
        return level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel ? barrel : null;
    }

    /**
     * A furnace or blast furnace as something to put into and take out of. Kept apart from {@link #containerAt}, which is
     * storage only, so a furnace is never mistaken for a chest and filled with whatever needs a home.
     */
    public static @Nullable Container furnaceAt(final Level level, final BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return entity instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity
                || entity instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity ? (Container) entity : null;
    }

    public static BlockPos canonical(final Level level, final BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos other = pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
            if (level.hasChunkAt(other) && level.getBlockState(other).getBlock() == state.getBlock()) {
                return pos.asLong() < other.asLong() ? pos.immutable() : other.immutable();
            }
        }
        return pos.immutable();
    }

    public static int matchingRoom(final Container container, final ItemStack stack) {
        int room = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, stack) && container.canPlaceItem(slot, stack)) {
                room += Math.max(0, Math.min(inSlot.getMaxStackSize(), container.getMaxStackSize()) - inSlot.getCount());
            }
        }
        return room;
    }

    public static int room(final Container container, final ItemStack stack) {
        int room = matchingRoom(container, stack);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, stack)) room += Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
        }
        return room;
    }

    public static boolean contains(final Container container, final ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (ItemStack.isSameItemSameComponents(container.getItem(slot), stack)) return true;
        }
        return false;
    }

    public static boolean needsCompaction(final Container container) {
        for (int target = 0; target < container.getContainerSize(); target++) {
            ItemStack first = container.getItem(target);
            if (first.isEmpty() || first.getCount() >= Math.min(first.getMaxStackSize(), container.getMaxStackSize())) continue;
            for (int source = target + 1; source < container.getContainerSize(); source++) {
                if (ItemStack.isSameItemSameComponents(first, container.getItem(source))) return true;
            }
        }
        return false;
    }

    public static boolean compact(final Container container) {
        boolean changed = false;
        for (int target = 0; target < container.getContainerSize(); target++) {
            ItemStack first = container.getItem(target);
            if (first.isEmpty()) continue;
            for (int source = target + 1; source < container.getContainerSize(); source++) {
                ItemStack later = container.getItem(source);
                if (!ItemStack.isSameItemSameComponents(first, later)) continue;
                int moved = Math.min(later.getCount(), Math.max(0, Math.min(first.getMaxStackSize(), container.getMaxStackSize()) - first.getCount()));
                if (moved <= 0) continue;
                first.grow(moved);
                later.shrink(moved);
                if (later.isEmpty()) container.setItem(source, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) container.setChanged();
        return changed;
    }

    /** The nearest chest or barrel holding something the helper is after. */
    public static @Nullable BlockPos findHolding(final Level level, final BlockPos centre, final int radius,
                                                 final Predicate<ItemStack> wanted) {
        for (BlockPos pos : around(level, centre, radius)) {
            Container container = containerAt(level, pos);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (wanted.test(container.getItem(slot))) return pos;
            }
        }
        return null;
    }

    /** The nearest chest or barrel with room to spare. */
    public static @Nullable BlockPos findWithRoom(final Level level, final BlockPos centre, final int radius) {
        for (BlockPos pos : around(level, centre, radius)) {
            Container container = containerAt(level, pos);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).isEmpty()) return pos;
            }
        }
        return null;
    }

    /** Takes the first matching stack out; empty when there was none. */
    public static ItemStack take(final Container container, final Predicate<ItemStack> wanted) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (wanted.test(container.getItem(slot))) {
                ItemStack taken = container.removeItemNoUpdate(slot);
                container.setChanged();
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Puts what fits in and hands back what did not. */
    public static ItemStack put(final Container container, final ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, remaining) || !container.canPlaceItem(slot, remaining)) continue;
            int room = Math.max(0, Math.min(inSlot.getMaxStackSize(), container.getMaxStackSize()) - inSlot.getCount());
            int moved = Math.min(room, remaining.getCount());
            if (moved > 0) { inSlot.grow(moved); remaining.shrink(moved); }
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remaining)) continue;
            int moved = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), container.getMaxStackSize()));
            container.setItem(slot, remaining.split(moved));
        }
        container.setChanged();
        return remaining;
    }
}
