package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The dirt a helper carries to reach what it cannot: a step at a time, up towards the work, and taken back down again
 * afterwards. Every block it sets is remembered, so nothing of its own is left standing in the world.
 */
public final class HelperSupports {
    /** No higher than this: a helper builds a step, not a tower. */
    public static final int MAX_STEPS = 6;
    /** Dirt a trade carries for the purpose. */
    public static final int CARRY = 16;

    private final NpcHelperEntity helper;
    private final List<BlockPos> placed = new ArrayList<>();

    public HelperSupports(final NpcHelperEntity helper) {
        this.helper = helper;
    }

    public boolean any() {
        return !placed.isEmpty();
    }

    public int count() {
        return placed.size();
    }

    /** True when the spot is too high above the helper to be worked on from here. */
    public static boolean outOfReach(final NpcHelperEntity helper, final BlockPos target) {
        return target.getY() - helper.blockPosition().getY() > 2;
    }

    /**
     * Sets one block down to stand on next, on the side the work is. True when a step went in; false when there is no dirt,
     * nowhere to put it, or the helper is high enough already.
     */
    public boolean step(final ServerLevel level, final BlockPos target) {
        if (placed.size() >= MAX_STEPS) return false;

        BlockPos feet = helper.blockPosition();
        Direction towards = Direction.getApproximateNearest(
                target.getX() - feet.getX(), 0.0D, target.getZ() - feet.getZ());
        // The step goes beside and level with the feet, so walking on to it is an ordinary stride up.
        BlockPos spot = feet.relative(towards);
        if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) {
            spot = feet;
            if (!level.getBlockState(spot).isAir()) return false;
        }

        int slot = findSoil();
        if (slot < 0) return false;
        ItemStack soil = helper.getBag().getItem(slot);
        if (!(soil.getItem() instanceof BlockItem block)) return false;

        if (!level.setBlockAndUpdate(spot, block.getBlock().defaultBlockState())) return false;
        helper.getBag().removeItem(slot, 1);
        placed.add(spot.immutable());
        return true;
    }

    /** The topmost block it set that is still standing, to be broken on the way down. */
    public @Nullable BlockPos topmost(final ServerLevel level) {
        for (int index = placed.size() - 1; index >= 0; index--) {
            BlockPos pos = placed.get(index);
            if (!level.getBlockState(pos).isAir()) return pos;
            placed.remove(index);
        }
        return null;
    }

    public void forget(final BlockPos pos) {
        placed.remove(pos);
    }

    public void clear() {
        placed.clear();
    }

    private int findSoil() {
        var bag = helper.getBag();
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(Blocks.DIRT.asItem()) || stack.is(Blocks.COARSE_DIRT.asItem())
                    || stack.is(Blocks.GRASS_BLOCK.asItem()) || stack.is(ItemTags.DIRT)) return slot;
        }
        return -1;
    }

    public void store(final ValueOutput output) {
        if (placed.isEmpty()) return;
        var list = output.childrenList("Placed");
        for (BlockPos pos : placed) {
            var child = list.addChild();
            child.putInt("X", pos.getX());
            child.putInt("Y", pos.getY());
            child.putInt("Z", pos.getZ());
        }
    }

    public void load(final ValueInput input) {
        placed.clear();
        for (ValueInput child : input.childrenListOrEmpty("Placed")) {
            placed.add(new BlockPos(child.getIntOr("X", 0), child.getIntOr("Y", 0), child.getIntOr("Z", 0)));
        }
    }
}
