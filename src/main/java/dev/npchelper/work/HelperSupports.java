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
    /** No higher than this: a helper builds a pillar to reach its work, not a tower. */
    public static final int MAX_HEIGHT = 10;
    /** Dirt a trade carries: one full pillar, and every block of it comes back, so no more is ever needed. */
    public static final int CARRY = MAX_HEIGHT;

    private final NpcHelperEntity helper;
    private final List<BlockPos> placed = new ArrayList<>();
    /** Where the next block of the pillar goes; null until a climb starts. */
    private @Nullable BlockPos next;

    public HelperSupports(final NpcHelperEntity helper) {
        this.helper = helper;
    }

    public boolean any() {
        return !placed.isEmpty();
    }

    public int count() {
        return placed.size();
    }

    /** Whether the helper set this block itself, and so may take it away again. */
    public boolean owns(final BlockPos pos) {
        return placed.contains(pos);
    }

    /** True when the spot is too high above the helper to be worked on from here. */
    public static boolean outOfReach(final NpcHelperEntity helper, final BlockPos target) {
        return target.getY() - helper.blockPosition().getY() > 2;
    }

    /**
     * Raises the helper a block, the way anyone does it: a hop, and a block dropped into the space just left. The pillar
     * goes straight up from where it started, so it never wanders off over the work. True while the climb is going on.
     */
    public boolean step(final ServerLevel level, final BlockPos target) {
        if (placed.size() >= MAX_HEIGHT) return false;
        if (findSoil() < 0) return false;

        helper.getNavigation().stop();
        helper.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);

        // Standing — on the ground or on top of its own pillar — the next block goes exactly where the feet are now.
        // It is decided afresh on every landing, never carried over from an earlier climb somewhere else.
        if (helper.onGround()) {
            BlockPos feet = helper.blockPosition();
            // Room for the head and for the hop itself before anything is spent.
            if (!passable(level, feet.above()) || !passable(level, feet.above(2))) return false;
            next = feet.immutable();
            helper.getJumpControl().jump();
            return true;
        }
        // In the air without a climb begun: wait to come down.
        if (next == null) return true;
        // Clear of the slot now: a block goes in and the helper comes down on top of it.
        if (helper.getY() < next.getY() + 0.6D) return true;
        // Grass, flowers or snow where the feet were give way to the block, as they would for a player.
        if (!level.getBlockState(next).canBeReplaced()) return true;

        int slot = findSoil();
        ItemStack soil = helper.getBag().getItem(slot);
        if (!(soil.getItem() instanceof BlockItem block)) return false;
        if (!level.setBlockAndUpdate(next, block.getBlock().defaultBlockState())) return true;

        helper.getBag().removeItem(slot, 1);
        placed.add(next.immutable());
        next = null;
        return true;
    }

    /** Nothing to bump the head on: air, or something the body passes through. */
    private static boolean passable(final ServerLevel level, final BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
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
        next = null;
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
