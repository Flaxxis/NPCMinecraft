package dev.npchelper.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

/** A whole tree rather than a single log: the trunk and the crown that belongs to it. */
public final class HelperTree {
    /** Enough for a jungle giant, and a stop against following a whole forest of touching crowns. */
    private static final int MAX_LOGS = 256, MAX_LEAVES = 512;

    private HelperTree() { }

    /** Every log joined to this one, corners included, because that is how the game grows a trunk. */
    public static List<BlockPos> trunk(final ServerLevel level, final BlockPos start) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        found.add(start.immutable());

        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            BlockPos here = queue.poll();
            for (BlockPos near : BlockPos.betweenClosed(here.offset(-1, -1, -1), here.offset(1, 1, 1))) {
                BlockPos pos = near.immutable();
                if (found.contains(pos) || !level.hasChunkAt(pos)) continue;
                if (!level.getBlockState(pos).is(BlockTags.LOGS)) continue;
                if (!HelperBuildings.fellable(level, pos)) continue;
                found.add(pos);
                queue.add(pos);
            }
        }
        List<BlockPos> logs = new ArrayList<>(found);
        // Felled from the foot up, as anyone would.
        logs.sort(Comparator.comparingInt(BlockPos::getY));
        return logs;
    }

    /** The living crown over those logs: what a sapling and an apple fall out of. */
    public static List<BlockPos> crown(final ServerLevel level, final List<BlockPos> logs) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos log : logs) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
                BlockPos pos = near.immutable();
                if (!found.contains(pos) && level.hasChunkAt(pos) && HelperBuildings.naturalLeaves(level.getBlockState(pos))) {
                    found.add(pos);
                    queue.add(pos);
                }
            }
        }
        while (!queue.isEmpty() && found.size() < MAX_LEAVES) {
            BlockPos here = queue.poll();
            for (BlockPos near : BlockPos.betweenClosed(here.offset(-1, -1, -1), here.offset(1, 1, 1))) {
                BlockPos pos = near.immutable();
                if (found.contains(pos) || !level.hasChunkAt(pos)) continue;
                if (!HelperBuildings.naturalLeaves(level.getBlockState(pos))) continue;
                found.add(pos);
                queue.add(pos);
            }
        }
        List<BlockPos> leaves = new ArrayList<>(found);
        leaves.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        return leaves;
    }

    /** Where the trunk stood: the lowest log, which is where a new one is put in. */
    public static BlockPos foot(final List<BlockPos> logs) {
        return logs.stream().min(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
    }
}
