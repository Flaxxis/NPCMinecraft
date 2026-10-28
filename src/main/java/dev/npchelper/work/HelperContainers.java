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
        BlockPos.betweenClosedStream(
                centre.offset(-radius, -HEIGHT, -radius),
                centre.offset(radius, HEIGHT, radius)
        ).forEach(pos -> {
            if (isStorage(level, pos)) found.add(pos.immutable());
        });
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(centre)));
        return found;
    }

    public static boolean isStorage(final Level level, final BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity;
    }

    public static @Nullable Container containerAt(final Level level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
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
            if (wanted.test(container.getItem(slot))) return container.removeItemNoUpdate(slot);
        }
        return ItemStack.EMPTY;
    }

    /** Puts what fits in and hands back what did not. */
    public static ItemStack put(final Container container, final ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (inSlot.isEmpty()) {
                container.setItem(slot, remaining.copyAndClear());
            } else if (ItemStack.isSameItemSameComponents(inSlot, remaining)) {
                int room = Math.min(inSlot.getMaxStackSize(), container.getMaxStackSize()) - inSlot.getCount();
                int moved = Math.min(room, remaining.getCount());
                if (moved > 0) {
                    inSlot.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
        container.setChanged();
        return remaining;
    }
}
