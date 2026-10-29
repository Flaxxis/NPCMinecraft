package dev.npchelper.work;

import dev.npchelper.entity.HelperProfession;
import dev.npchelper.entity.NpcHelperEntity;
import dev.npchelper.work.HelperSigns.Mark;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What taking up a trade comes to: see to the tools first — what is carried, then what lies in a chest nearby, and only
 * then what has to be made — and get on with the work afterwards.
 */
public final class HelperTrades {
    /** Logs a lumberjack fells before it goes looking for somewhere to put them. */
    public static final int WOOD_LOAD = 64;

    private HelperTrades() { }

    /** The sign a trade reads to know where its work is; null for trades that work wherever they stand. */
    public static @Nullable Mark markFor(final HelperProfession profession) {
        return switch (profession) {
            case LUMBERJACK -> Mark.GROVE;
            case MINER -> Mark.MINE;
            case SMELTER -> Mark.FORGE;
            case KEEPER -> Mark.STORE;
            default -> null;
        };
    }

    public static List<HelperStep> planFor(final NpcHelperEntity helper, final HelperProfession profession) {
        List<HelperStep> plan = new ArrayList<>();
        if (!(helper.level() instanceof ServerLevel level)) return plan;

        if (helper.supports().any()) plan.add(new HelperStep(HelperStep.RECOVER_SUPPORTS, "", 1));
        if (helper.placedBench() != null) plan.add(new HelperStep(HelperStep.TAKE_BENCH, "", 1));
        if (!helper.getCargo().isEmpty()) plan.add(new HelperStep(HelperStep.KEEPER_DELIVER, "", 1));
        if (profession != HelperProfession.NONE) plan.add(new HelperStep(HelperStep.PREPARE_KIT, "", 1));
        if (profession == HelperProfession.LUMBERJACK) {
            // Resolve each preparation stage from the real inventory left by the previous one.
            plan.add(new HelperStep(HelperStep.PREPARE_GROVE, "", 1));
            plan.add(new HelperStep(HelperStep.PREPARE_AXE, "", 1));
            plan.add(new HelperStep(HelperStep.MARK_GROVE, "", 1));
            plan.add(new HelperStep(HelperStep.GATHER_DIRT, "", HelperSupports.CARRY));
            plan.add(new HelperStep(HelperStep.GATHER_WOOD, "", WOOD_LOAD));
            plan.add(new HelperStep(HelperStep.STORE, "", 1));
        } else if (profession == HelperProfession.GUARD) {
            // The sword is a must and is seen to like any tool; the shield is looked for once, never chased.
            plan.add(new HelperStep(HelperStep.PREPARE_TOOLS, "", 1));
            plan.add(new HelperStep(HelperStep.PREPARE_SHIELD, "", 1));
            plan.add(new HelperStep(HelperStep.TAKE_BENCH, "", 1));
            plan.add(new HelperStep(HelperStep.GUARD_PATROL, "", 1));
        } else if (profession == HelperProfession.KEEPER) {
            plan.add(new HelperStep(HelperStep.PREPARE_STORE, "", 1));
            plan.add(new HelperStep(HelperStep.TAKE_BENCH, "", 1));
            plan.add(new HelperStep(HelperStep.KEEPER_WORK, "", 1));
        } else if (profession != HelperProfession.NONE) {
            plan.add(new HelperStep(HelperStep.PREPARE_TOOLS, "", 1));
        }
        if (!plan.isEmpty()) plan.add(new HelperStep(HelperStep.TAKE_BENCH, "", 1));
        return plan;
    }

    public static List<HelperStep> prepareAxe(final NpcHelperEntity helper) {
        List<HelperStep> plan = new ArrayList<>();
        if (helper.level() instanceof ServerLevel level) addTool(level, helper, HelperToolkit.Tool.AXE, plan);
        return plan;
    }

    public static List<HelperStep> prepareTools(final NpcHelperEntity helper) {
        List<HelperStep> plan = new ArrayList<>();
        if (helper.level() instanceof ServerLevel level) {
            for (HelperToolkit.Tool tool : HelperToolkit.needs(helper.getProfession())) {
                if (HelperToolkit.best(helper, tool).isEmpty()) { addTool(level, helper, tool, plan); break; }
            }
        }
        return plan;
    }

    /**
     * A shield for the other hand: one carried, else one from a chest nearby, else one made — but only when the iron for
     * it is already in the bag or a chest. Iron is never dug or smelted for it, and with no shield to be had the plan is
     * empty and the guard goes on without.
     */
    public static List<HelperStep> prepareShield(final NpcHelperEntity helper) {
        List<HelperStep> plan = new ArrayList<>();
        if (!(helper.level() instanceof ServerLevel level)) return plan;
        String shield = HelperObtain.key(Items.SHIELD);
        if (helper.getOffhandItem().is(Items.SHIELD)) return plan;
        if (helper.getMainHandItem().is(Items.SHIELD) || helper.getBag().countItem(Items.SHIELD) > 0) {
            plan.add(new HelperStep(HelperStep.EQUIP_OFFHAND, shield, 1));
            return plan;
        }
        boolean stored = false, iron = helper.getBag().countItem(Items.IRON_INGOT) > 0;
        for (var pos : HelperResources.storages(helper)) {
            var container = HelperContainers.containerAt(level, pos);
            if (container == null) continue;
            stored |= container.countItem(Items.SHIELD) > 0;
            iron |= container.countItem(Items.IRON_INGOT) > 0;
        }
        if (stored) {
            plan.add(new HelperStep(HelperStep.FETCH, shield, 1));
        } else if (iron) {
            HelperObtain.Plan obtained = HelperResources.plan(helper, Items.SHIELD, 1);
            if (obtained.refused()) return plan;
            plan.addAll(obtained.steps());
        } else {
            return plan;
        }
        plan.add(new HelperStep(HelperStep.EQUIP_OFFHAND, shield, 1));
        return plan;
    }

    private static void addTool(final ServerLevel level, final NpcHelperEntity helper,
                                final HelperToolkit.Tool tool, final List<HelperStep> plan) {
        // Carried already: nothing to do but take it in hand when the time comes.
        ItemStack carried = HelperToolkit.best(helper, tool);
        if (!carried.isEmpty()) {
            plan.add(new HelperStep(HelperStep.EQUIP, HelperObtain.key(carried.getItem()), 1));
            return;
        }

        // In a chest or a barrel within reach: the best one there is worth fetching.
        var stored = bestStored(level, helper, tool);
        if (stored != null) {
            plan.add(new HelperStep(HelperStep.FETCH, HelperObtain.key(stored), 1));
            plan.add(new HelperStep(HelperStep.EQUIP, HelperObtain.key(stored), 1));
            return;
        }

        // Nothing anywhere: make the plainest one, from the ground up if need be.
        var plain = tool.plainest();
        HelperObtain.Plan obtained = HelperResources.plan(helper, plain, 1);
        if (obtained.refused()) return;
        plan.addAll(obtained.steps());
        plan.add(new HelperStep(HelperStep.EQUIP, HelperObtain.key(plain), 1));
    }

    /** The best tool of that kind lying in a chest or barrel around the helper. */
    private static @Nullable Item bestStored(final ServerLevel level, final NpcHelperEntity helper,
                                             final HelperToolkit.Tool tool) {
        Item best = null;
        int bestQuality = -1;
        for (var pos : HelperResources.storages(helper)) {
            best = null;
            bestQuality = -1;
            var container = HelperContainers.containerAt(level, pos);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                int quality = tool.quality(stack);
                if (quality > bestQuality) {
                    bestQuality = quality;
                    best = stack.getItem();
                }
            }
            if (best != null) return best;
        }
        return null;
    }
}
