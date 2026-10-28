package dev.npchelper.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

/**
 * Finding timber without walking the whole box block by block: only chunk sections already loaded, and of those only the ones
 * whose palette says they hold a log at all. Nothing is loaded or generated to look for wood.
 */
public final class HelperTreeSearch {
    private static final int BELOW = 16, ABOVE = 32;

    private HelperTreeSearch() { }

    /** Logs around the spot that may be felled, nearest and lowest first. */
    public static List<BlockPos> find(final ServerLevel level, final BlockPos origin, final int radius) {
        List<BlockPos> found = new ArrayList<>();
        int minY = Math.max(level.getMinY(), origin.getY() - BELOW);
        int maxY = Math.min(level.getMaxY(), origin.getY() + ABOVE);

        for (int chunkX = (origin.getX() - radius) >> 4; chunkX <= (origin.getX() + radius) >> 4; chunkX++) {
            for (int chunkZ = (origin.getZ() - radius) >> 4; chunkZ <= (origin.getZ() + radius) >> 4; chunkZ++) {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;

                for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) {
                    if (index < 0 || index >= chunk.getSections().length) continue;
                    var section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(BlockTags.LOGS))) continue;

                    int bottom = level.getSectionYFromSectionIndex(index) << 4;
                    for (int y = Math.max(bottom, minY); y <= Math.min(bottom + 15, maxY); y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int worldX = (chunkX << 4) + x, worldZ = (chunkZ << 4) + z;
                                long dx = worldX - origin.getX(), dz = worldZ - origin.getZ();
                                if (dx * dx + dz * dz > (long) radius * radius) continue;
                                if (!section.getBlockState(x, y & 15, z).is(BlockTags.LOGS)) continue;

                                BlockPos log = new BlockPos(worldX, y, worldZ);
                                if (HelperBuildings.fellable(level, log)) found.add(log);
                            }
                        }
                    }
                }
            }
        }
        // Near first, and low before high: a tree is felled from the foot.
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin) + Math.max(0, pos.getY() - origin.getY()) * 4.0D));
        return found;
    }
}
