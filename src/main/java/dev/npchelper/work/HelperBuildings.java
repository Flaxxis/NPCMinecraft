package dev.npchelper.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Telling what a player built from what grew by itself, so a log cabin is never mistaken for a stand of trees.
 *
 * A log is a tree when a living crown stands beside it: leaves that grew there, which vanilla marks by NOT being persistent.
 * Leaves a player placed are persistent, so a decorated log is left alone. A log with no crown that touches anything crafted —
 * planks, a door, glass, a torch, a chest — is somebody's wall. A lone log with neither is still a tree.
 */
public final class HelperBuildings {
    private HelperBuildings() { }

    /** Blocks nobody finds in the wild beside a log: made at a bench or set by hand. */
    public static boolean crafted(final BlockState state) {
        return state.is(BlockTags.PLANKS) || state.is(BlockTags.SLABS) || state.is(BlockTags.STAIRS)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCES)
                || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WALLS) || state.is(BlockTags.BEDS)
                || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.IMPERMEABLE) || state.is(BlockTags.CANDLES)
                || state.is(Blocks.BRICKS) || state.is(Blocks.LADDER) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.LANTERN) || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.CHEST)
                || state.is(Blocks.BARREL) || state.is(Blocks.FURNACE) || state.is(Blocks.SMOOTH_STONE)
                || state.is(Blocks.GLASS_PANE);
    }

    /** Anything crafted, or anything with a block entity — a chest, a furnace, a sign — right next to this block. */
    public static boolean nearCrafted(final ServerLevel level, final BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos near = pos.relative(side);
            if (!level.hasChunkAt(near)) continue;
            if (crafted(level.getBlockState(near)) || level.getBlockEntity(near) != null) return true;
        }
        return false;
    }

    /** Leaves that grew here, as against leaves a player placed. */
    public static boolean naturalLeaves(final BlockState state) {
        return state.getBlock() instanceof LeavesBlock
                && state.hasProperty(LeavesBlock.PERSISTENT)
                && !state.getValue(LeavesBlock.PERSISTENT);
    }

    /** A living crown beside the log: what makes it a tree rather than a beam. */
    public static boolean crownBeside(final ServerLevel level, final BlockPos log) {
        for (BlockPos pos : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (naturalLeaves(state)) return true;
            if (state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.WARPED_WART_BLOCK) || state.is(Blocks.SHROOMLIGHT)) return true;
        }
        return false;
    }

    /** Leaves a player placed beside the log: a planting or an ornament, not timber. */
    public static boolean decorated(final ServerLevel level, final BlockPos log) {
        for (BlockPos pos : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT)
                    && state.getValue(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    /** Whether this log may be felled: a crown settles it, otherwise anything built or placed beside it says no. */
    public static boolean fellable(final ServerLevel level, final BlockPos log) {
        if (crownBeside(level, log)) return true;
        return !nearCrafted(level, log) && !decorated(level, log);
    }
}
