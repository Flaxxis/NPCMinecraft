package dev.npchelper.work;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

/**
 * «Get me one of those», worked out backwards from the game's own recipes rather than from a table written by hand: what is
 * in the bag already, what a recipe needs, and what the helper knows how to gather. The answer is a plain list of steps.
 *
 * Wood is one family — any log, any plank — because the recipes take them all; planks and sticks are made by whichever craft
 * needs them. Anything none of the skills can reach is said plainly instead of being attempted.
 */
public final class HelperObtain {
    /** The result: steps to run, or why there are none. */
    public record Plan(List<HelperStep> steps, @Nullable String refusal) {
        public boolean refused() {
            return refusal != null;
        }
    }

    private static final int MAX_STEPS = 16, MAX_DEPTH = 6;
    private static final String LOGS = "#logs", PLANKS = "#planks";
    private static final String STICK = "minecraft:stick";

    private final ServerLevel level;
    /** A bench within reach means a 3x3 recipe needs no new one. */
    private boolean bench;
    private final Map<String, Integer> bag = new HashMap<>();
    private final List<HelperStep> steps = new ArrayList<>();
    private final Set<String> active = new HashSet<>();
    private @Nullable String why;

    private HelperObtain(final ServerLevel level, final List<ItemStack> carried, final boolean bench) {
        this.level = level;
        this.bench = bench;
        for (ItemStack stack : carried) {
            if (!stack.isEmpty()) bag.merge(key(stack.getItem()), stack.getCount(), Integer::sum);
        }
    }

    public static Plan plan(final ServerLevel level, final List<ItemStack> carried, final boolean benchNearby,
                            final Item wanted, final int count) {
        boolean hasBench = benchNearby || carried.stream().anyMatch(stack -> stack.is(Items.CRAFTING_TABLE));
        HelperObtain planner = new HelperObtain(level, carried, hasBench);
        if (!planner.need(key(wanted), count, 0)) {
            return new Plan(List.of(), planner.why == null ? "Не знаю, де взяти " + name(wanted) + "." : planner.why);
        }
        if (planner.steps.size() > MAX_STEPS) {
            return new Plan(List.of(), "Для " + name(wanted) + " задовгий ланцюжок.");
        }
        return new Plan(List.copyOf(planner.steps), null);
    }

    /** Takes the count out of the bag, getting whatever is missing first. */
    private boolean need(final String key, final int count, final int depth) {
        int have = bag.getOrDefault(key, 0);
        if (have >= count) {
            bag.put(key, have - count);
            return true;
        }
        bag.put(key, 0);
        int missing = count - have;
        if (depth > MAX_DEPTH || !active.add(key)) return refuse("Ланцюжок для " + key + " заплутався.");
        try {
            return produce(key, missing, depth);
        } finally {
            active.remove(key);
        }
    }

    private boolean produce(final String key, final int count, final int depth) {
        switch (key) {
            case LOGS -> {
                return add(new HelperStep(HelperStep.GATHER_WOOD, "", count));
            }
            // Planks and sticks are made by the craft that needs them, so only their own material is asked for here.
            case PLANKS -> {
                return need(LOGS, ceil(count, 4), depth + 1);
            }
            case STICK -> {
                return need(PLANKS, ceil(count, 4) * 2, depth + 1);
            }
            default -> { }
        }
        Item item = BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(key)).orElse(null);
        if (item == null) return refuse("Невідомий предмет " + key + ".");
        if (craft(item, count, depth)) return true;
        return refuse("Не знаю, де взяти " + name(item) + ": копати й плавити я ще не вмію.");
    }

    /** A recipe makes it: its ingredients first, then the craft itself. */
    private boolean craft(final Item item, final int count, final int depth) {
        String before = why;
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack result = HelperRecipes.resultOf(recipe);
            if (result.isEmpty() || !result.is(item)) continue;

            var placement = recipe.placementInfo();
            if (placement.isImpossibleToPlace()) continue;
            var ingredients = placement.ingredients();
            if (ingredients.isEmpty()) continue;

            int crafts = ceil(count, result.getCount());
            // One slot takes one item, so the slot map is what says how many of each ingredient a craft eats.
            Map<String, Integer> needed = new LinkedHashMap<>();
            boolean known = true;
            int slots = 0;
            for (int index : placement.slotsToIngredientIndex()) {
                if (index < 0 || index >= ingredients.size()) continue;
                slots++;
                String option = cheapest(ingredients.get(index));
                if (option == null) {
                    known = false;
                    break;
                }
                needed.merge(option, crafts, Integer::sum);
            }
            if (!known || needed.isEmpty()) continue;

            // More than four slots means a 3x3 grid, which wants a bench to work at.
            boolean large = slots > 4;
            if (large && !bench) needed.merge(LOGS, 1, Integer::sum);

            var saved = Map.copyOf(bag);
            int stepsBefore = steps.size();
            boolean all = true;
            for (var entry : needed.entrySet()) {
                if (!need(entry.getKey(), entry.getValue(), depth + 1)) {
                    all = false;
                    break;
                }
            }
            if (all && add(new HelperStep(HelperStep.CRAFT, key(item), count))) {
                why = before;
                if (large) bench = true;
                return true;
            }
            bag.clear();
            bag.putAll(saved);
            while (steps.size() > stepsBefore) steps.removeLast();
        }
        return false;
    }

    /** Of the items an ingredient accepts, the one already carried, else the first the helper can reach. */
    private @Nullable String cheapest(final Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return null;
        List<String> options = new ArrayList<>();
        ingredient.items().forEach(holder -> {
            String key = key(holder.value());
            if (!options.contains(key)) options.add(key);
        });
        if (options.isEmpty()) return null;
        for (String option : options) {
            if (bag.getOrDefault(option, 0) > 0) return option;
        }
        // Wood is one family: a recipe that takes every plank is served by whichever the helper fells.
        for (String option : options) {
            if (option.equals(PLANKS) || option.equals(LOGS) || option.equals(STICK)) return option;
        }
        return options.getFirst();
    }

    private boolean add(final HelperStep step) {
        steps.add(step);
        return steps.size() <= MAX_STEPS;
    }

    private boolean refuse(final String reason) {
        if (why == null) why = reason;
        return false;
    }

    private static int ceil(final int amount, final int per) {
        return (amount + per - 1) / per;
    }

    /** Families first: any log and any plank count as one thing, because the recipes treat them so. */
    static String key(final Item item) {
        ItemStack stack = new ItemStack(item);
        if (stack.is(ItemTags.LOGS)) return LOGS;
        if (stack.is(ItemTags.PLANKS)) return PLANKS;
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    static String name(final Item item) {
        return new ItemStack(item).getHoverName().getString();
    }
}
