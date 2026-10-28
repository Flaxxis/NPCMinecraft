package dev.npchelper.entity;

import dev.npchelper.work.HelperSigns;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/** A bounded shelter heuristic: standing space, a non-leaf roof and walls in at least three directions. */
public final class HelperShelter {
    private HelperShelter() { }

    public static boolean standable(final ServerLevel level, final BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isSolid()) return false;
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos at = pos.above(dy);
            var state = level.getBlockState(at);
            if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, at).isEmpty()) return false;
        }
        var ground = level.getBlockState(pos.below());
        return !ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && !ground.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                && !ground.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE);
    }

    public static boolean indoors(final ServerLevel level, final BlockPos pos) {
        if (!standable(level, pos)) return false;
        // A resting point must leave enough room to get the whole body through and close the entrance.
        if (level.getBlockState(pos).getBlock() instanceof DoorBlock) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(side)).getBlock() instanceof DoorBlock) return false;
        }
        boolean roof = false;
        for (int up = 2; up <= 10; up++) {
            BlockPos at = pos.above(up);
            var state = level.getBlockState(at);
            if (state.isSolid() && !state.is(BlockTags.LEAVES)) { roof = true; break; }
        }
        if (!roof) return false;
        int walls = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 8; distance++) {
                BlockPos at = pos.above().relative(side, distance);
                if (!level.hasChunkAt(at)) break;
                var state = level.getBlockState(at);
                if (state.isSolid() || state.getBlock() instanceof DoorBlock) { walls++; break; }
            }
        }
        return walls >= 3;
    }

    private static @Nullable BlockPos reachable(final NpcHelperEntity helper, final List<BlockPos> candidates) {
        candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(helper.position())));
        for (BlockPos pos : candidates) {
            if (pos.distToCenterSqr(helper.position()) < 1) return pos;
            var path = helper.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) return pos;
        }
        return null;
    }

    public static @Nullable BlockPos find(final NpcHelperEntity helper) {
        if (!(helper.level() instanceof ServerLevel level)) return null;
        var homes = HelperSigns.around(level, helper.blockPosition(), 64, HelperSigns.Mark.HOME);
        for (BlockPos home : homes) {
            List<BlockPos> inside = new ArrayList<>(), nearby = new ArrayList<>();
            for (BlockPos cursor : BlockPos.betweenClosed(home.offset(-8, -2, -8), home.offset(8, 2, 8))) {
                BlockPos pos = cursor.immutable();
                if (indoors(level, pos)) inside.add(pos);
                else if (pos.distSqr(home) <= 9 && standable(level, pos)) nearby.add(pos);
            }
            BlockPos found = reachable(helper, inside);
            if (found != null) return found;
            found = reachable(helper, nearby);
            if (found != null) return found; // An explicit home marker remains useful even without a valid room.
        }
        if (indoors(level, helper.blockPosition())) return helper.blockPosition();
        BlockPos origin = helper.blockPosition();
        List<BlockPos> doors = new ArrayList<>();
        int radius = 32;
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (int index = 0; index < chunk.getSections().length; index++) {
                    var section = chunk.getSection(index);
                    if (!section.maybeHas(DoorBlock::isWoodenDoor)) continue;
                    int bottom = level.getSectionYFromSectionIndex(index) << 4;
                    for (int dy = 0; dy < 16; dy++) for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                        var state = section.getBlockState(dx, dy, dz);
                        if (!DoorBlock.isWoodenDoor(state) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                        BlockPos pos = new BlockPos((x << 4) + dx, bottom + dy, (z << 4) + dz);
                        if (Math.abs(pos.getY() - origin.getY()) <= 8 && pos.distSqr(origin) <= radius * radius) doors.add(pos);
                    }
                }
            }
        }
        doors.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        for (BlockPos door : doors) {
            List<BlockPos> inside = new ArrayList<>();
            for (BlockPos cursor : BlockPos.betweenClosed(door.offset(-3, -1, -3), door.offset(3, 1, 3))) {
                if (indoors(level, cursor)) inside.add(cursor.immutable());
            }
            BlockPos found = reachable(helper, inside);
            if (found != null) return found;
        }
        return null;
    }
}
