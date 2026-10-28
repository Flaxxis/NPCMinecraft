package dev.npchelper.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Signs are where a helper keeps what it must not forget: the mouth of a mine, the edge of a field, the grove it is to cut.
 * Written into the world rather than into the helper, so the knowledge outlives it, is shared by every helper who walks past,
 * and is plain to the player — who can set a sign of their own and be obeyed, without any interface at all.
 */
public final class HelperSigns {
    /** What a sign's first line says, and what it tells a helper to do there. */
    public enum Mark {
        MINE("шахта"),
        FIELD("поле"),
        GROVE("гай"),
        /** Where the furnaces stand: the smith works here and nowhere else. */
        FORGE("кузня"),
        STORE("склад"),
        HOME("дім");

        private final String written;

        Mark(final String written) {
            this.written = written;
        }

        public String written() {
            return written;
        }
    }

    /** How far apart the signs of one chain may stand and still lead on to each other. */
    public static final int CHAIN_STEP = 24;
    private static final int BELOW = 24, ABOVE = 24;

    private HelperSigns() { }

    /** The mark written on the sign at this spot, or null when it is not one of ours. */
    public static @Nullable Mark markAt(final ServerLevel level, final BlockPos pos) {
        if (!level.getBlockState(pos).is(BlockTags.ALL_SIGNS)) return null;
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return null;

        var messages = sign.getText(SignTextSlot.FRONT).getMessages(false);
        if (messages.isEmpty()) return null;
        String first = messages.getFirst().getString().strip().toLowerCase(Locale.ROOT);
        for (Mark mark : Mark.values()) {
            if (mark.written().equals(first)) return mark;
        }
        return null;
    }

    /** Every sign of that mark around the spot, nearest first. Only loaded sections are looked at. */
    public static List<BlockPos> around(final ServerLevel level, final BlockPos origin, final int radius, final Mark mark) {
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
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(BlockTags.ALL_SIGNS))) continue;

                    int bottom = level.getSectionYFromSectionIndex(index) << 4;
                    for (int y = Math.max(bottom, minY); y <= Math.min(bottom + 15, maxY); y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                if (!section.getBlockState(x, y & 15, z).is(BlockTags.ALL_SIGNS)) continue;
                                BlockPos pos = new BlockPos((chunkX << 4) + x, y, (chunkZ << 4) + z);
                                long dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ();
                                if (dx * dx + dz * dz > (long) radius * radius) continue;
                                if (markAt(level, pos) == mark) found.add(pos);
                            }
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        return found;
    }

    /** The nearest sign of that mark, or null when there is none. */
    public static @Nullable BlockPos nearest(final ServerLevel level, final BlockPos origin, final int radius, final Mark mark) {
        var found = around(level, origin, radius, mark);
        return found.isEmpty() ? null : found.getFirst();
    }

    /**
     * Follows a line of signs away from the first one, taking a sign at random where several lead on, up to the given number
     * of hops. The far end is where the work has got to; the wandering is what keeps a mine from being one straight tunnel.
     */
    public static @Nullable BlockPos followChain(final ServerLevel level, final BlockPos start, final Mark mark,
                                                 final int hops, final RandomSource random) {
        BlockPos here = start;
        Set<BlockPos> visited = new HashSet<>();
        visited.add(start);

        for (int hop = 0; hop < hops; hop++) {
            List<BlockPos> onward = new ArrayList<>();
            for (BlockPos pos : around(level, here, CHAIN_STEP, mark)) {
                if (visited.add(pos)) onward.add(pos);
            }
            if (onward.isEmpty()) return here;
            here = onward.get(random.nextInt(onward.size()));
        }
        return here;
    }

    /** Standing signs can replace vegetation, but never fluids or an existing solid block. */
    public static boolean canPlace(final ServerLevel level, final BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState current = level.getBlockState(pos);
        return (current.isAir() || current.canBeReplaced()) && current.getFluidState().isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    /** Sets a sign down at the spot with that mark on it, taking one out of the bag. True when it went up. */
    public static boolean place(final ServerLevel level, final Container bag, final BlockPos pos, final Mark mark) {
        if (!canPlace(level, pos)) return false;

        int slot = findSign(bag);
        if (slot < 0) return false;

        ItemStack carried = bag.getItem(slot);
        if (!(carried.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return false;
        BlockState sign = blockItem.getBlock().defaultBlockState();
        if (!level.setBlockAndUpdate(pos, sign)) return false;
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity entity)) {
            level.removeBlock(pos, false);
            return false;
        }

        List<net.minecraft.network.chat.Component> lines = List.of(
                net.minecraft.network.chat.Component.literal(mark.written()),
                net.minecraft.network.chat.Component.empty(),
                net.minecraft.network.chat.Component.empty(),
                net.minecraft.network.chat.Component.empty());
        entity.setText(new SignText(lines, lines, net.minecraft.world.item.DyeColor.BLACK, false), SignTextSlot.FRONT);
        entity.setChanged();
        level.sendBlockUpdated(pos, sign, sign, 3);

        bag.removeItem(slot, 1);
        bag.setChanged();
        return true;
    }

    public static boolean hasSign(final Container bag) {
        return findSign(bag) >= 0;
    }

    private static int findSign(final Container bag) {
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (!stack.isEmpty() && stack.is(ItemTags.SIGNS)) return slot;
        }
        return -1;
    }
}
