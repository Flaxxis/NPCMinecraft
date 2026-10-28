package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Resource order is always bag, marked warehouses, then the movement area. */
public final class HelperResources {
    private HelperResources() { }

    public static int soilCount(final NpcHelperEntity helper) {
        int count = 0;
        for (ItemStack stack : helper.getBag().getItems()) if (stack.is(net.minecraft.tags.ItemTags.DIRT)) count += stack.getCount();
        return count;
    }

    public static boolean ready(final NpcHelperEntity helper) {
        return soilCount(helper) >= HelperSupports.CARRY && (helper.getBag().countItem(net.minecraft.world.item.Items.CRAFTING_TABLE) > 0
                || helper.placedBench() != null);
    }

    public static List<BlockPos> storages(final NpcHelperEntity helper) {
        if (!(helper.level() instanceof ServerLevel level) || helper.centre() == null) return List.of();
        BlockPos centre = BlockPos.containing(helper.centre());
        var ordered = new LinkedHashSet<BlockPos>();
        for (BlockPos mark : HelperSigns.around(level, centre, 64, HelperSigns.Mark.STORE)) {
            ordered.addAll(HelperContainers.around(level, mark, 16));
        }
        ordered.addAll(HelperContainers.around(level, centre, helper.workRadius()));
        return ordered.stream().filter(pos -> {
            var path = dev.npchelper.entity.HelperNavigation.probe(helper, pos, 1);
            return path != null && path.canReach();
        }).toList();
    }

    public static List<ItemStack> carried(final NpcHelperEntity helper) {
        return helper.getBag().getItems().stream().map(ItemStack::copy).toList();
    }

    public static HelperObtain.Plan plan(final NpcHelperEntity helper, final Item wanted, final int count) {
        if (!(helper.level() instanceof ServerLevel level)) return new HelperObtain.Plan(List.of(), "Немає серверного світу");
        boolean bench = HelperCrafting.findBench(helper) != null;
        var local = HelperObtain.plan(level, carried(helper), bench, wanted, count);
        if (!local.refused() && local.steps().stream().noneMatch(step -> step.type().equals(HelperStep.GATHER_WOOD)
                || step.type().equals(HelperStep.FETCH))) return local;
        List<ItemStack> stock = new ArrayList<>();
        for (BlockPos pos : storages(helper)) {
            var container = HelperContainers.containerAt(level, pos);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).isEmpty()) stock.add(container.getItem(slot).copy());
            }
        }
        return HelperObtain.plan(level, carried(helper), bench, wanted, count, stock);
    }
}
