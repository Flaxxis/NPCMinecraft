package dev.npchelper.work;

import dev.npchelper.entity.HelperProfession;
import dev.npchelper.entity.NpcHelperEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** What a trade needs in hand, and which of the ones lying about is the best of them. */
public final class HelperToolkit {
    /** How far the helper will walk to a chest for a tool. */
    public static final int FETCH_RADIUS = 32;

    /** A kind of tool, worst material first, so the last match found is the best one. */
    public enum Tool {
        AXE(Items.WOODEN_AXE, Items.GOLDEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE),
        PICKAXE(Items.WOODEN_PICKAXE, Items.GOLDEN_PICKAXE, Items.STONE_PICKAXE, Items.COPPER_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE),
        SHOVEL(Items.WOODEN_SHOVEL, Items.GOLDEN_SHOVEL, Items.STONE_SHOVEL, Items.COPPER_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL),
        SWORD(Items.WOODEN_SWORD, Items.GOLDEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);

        private final List<Item> byQuality;

        Tool(final Item... byQuality) {
            this.byQuality = List.of(byQuality);
        }

        /** The simplest one, which is what gets made when none can be found. */
        public Item plainest() {
            return byQuality.getFirst();
        }

        public boolean is(final ItemStack stack) {
            return !stack.isEmpty() && byQuality.contains(stack.getItem());
        }

        /** Higher is better; -1 for anything that is not this tool. */
        public int quality(final ItemStack stack) {
            return stack.isEmpty() ? -1 : byQuality.indexOf(stack.getItem());
        }
    }

    private HelperToolkit() { }

    /** The tools a trade cannot work without, in the order they are seen to. */
    public static List<Tool> needs(final HelperProfession profession) {
        return switch (profession) {
            case LUMBERJACK -> List.of(Tool.AXE);
            case MINER -> List.of(Tool.PICKAXE, Tool.SHOVEL);
            case GUARD -> List.of(Tool.SWORD);
            default -> List.of();
        };
    }

    /** The best one of that tool already in the bag or in hand; empty when there is none. */
    public static ItemStack best(final NpcHelperEntity helper, final Tool tool) {
        ItemStack best = ItemStack.EMPTY;
        List<ItemStack> carried = new ArrayList<>();
        carried.add(helper.getMainHandItem());
        carried.add(helper.getOffhandItem());
        for (int slot = 0; slot < helper.getBag().getContainerSize(); slot++) carried.add(helper.getBag().getItem(slot));
        for (ItemStack stack : carried) {
            if (tool.quality(stack) > tool.quality(best)) best = stack;
        }
        return best;
    }

    /** The first tool of the lot that the helper has not got. */
    public static @Nullable Tool missing(final NpcHelperEntity helper, final HelperProfession profession) {
        for (Tool tool : needs(profession)) {
            if (best(helper, tool).isEmpty()) return tool;
        }
        return null;
    }
}
