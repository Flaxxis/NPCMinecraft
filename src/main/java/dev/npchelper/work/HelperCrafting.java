package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Crafts against a copy of the bag, committing ingredients, output and remainders together. */
public final class HelperCrafting {
    public static final double BENCH_REACH = 4.0D;
    public static final int BENCH_SEARCH = 32;

    public enum Outcome { DONE, NO_MATERIALS, NEEDS_BENCH, NO_RECIPE, NO_ROOM }

    private HelperCrafting() { }

    public static @Nullable Item item(final String key) {
        var id = Identifier.tryParse(key);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static boolean wanted(final ItemStack result, final String key) {
        return key.equals("#signs") ? result.is(ItemTags.SIGNS)
                : key.equals("#planks") ? result.is(ItemTags.PLANKS)
                : !result.isEmpty() && result.is(item(key));
    }

    public static boolean needsBench(final net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) return shaped.getWidth() > 2 || shaped.getHeight() > 2;
        int slots = 0;
        for (int index : recipe.placementInfo().slotsToIngredientIndex()) if (index >= 0) slots++;
        return slots > 4;
    }

    /** One actual recipe execution. A full bag never consumes ingredients or discards output. */
    public static Outcome craft(final ServerLevel level, final Container bag, final String key, final boolean benchNearby) {
        boolean known = false, materials = false;
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            var recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe crafting) || !wanted(HelperRecipes.resultOf(recipe), key)) continue;
            known = true;
            var placement = recipe.placementInfo();
            if (placement.isImpossibleToPlace()) continue;
            List<Ingredient> perSlot = new ArrayList<>();
            for (int index : placement.slotsToIngredientIndex()) {
                if (index >= 0) perSlot.add(placement.ingredients().get(index));
            }
            List<Integer> sources = match(bag, perSlot);
            if (sources == null || sources.isEmpty()) continue;
            materials = true;
            if (needsBench(recipe) && !benchNearby) continue;

            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : Math.min(3, sources.size());
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : (sources.size() + 2) / 3;
            List<ItemStack> inputItems = new ArrayList<>();
            int source = 0;
            for (int index : placement.slotsToIngredientIndex()) {
                inputItems.add(index < 0 ? ItemStack.EMPTY : bag.getItem(sources.get(source++)).copyWithCount(1));
            }
            while (inputItems.size() < width * height) inputItems.add(ItemStack.EMPTY);
            CraftingInput input = CraftingInput.of(width, height, inputItems);
            if (!crafting.matches(input, level)) continue;
            ItemStack output = crafting.assemble(input);
            if (output.isEmpty()) continue;
            SimpleContainer trial = new SimpleContainer(bag.getContainerSize());
            for (int slot = 0; slot < bag.getContainerSize(); slot++) trial.setItem(slot, bag.getItem(slot).copy());
            for (int slot : sources) trial.removeItem(slot, 1);
            if (!trial.addItem(output.copy()).isEmpty()) return Outcome.NO_ROOM;
            for (ItemStack remainder : crafting.getRemainingItems(input)) {
                if (!trial.addItem(remainder.copy()).isEmpty()) return Outcome.NO_ROOM;
            }
            for (int slot = 0; slot < bag.getContainerSize(); slot++) bag.setItem(slot, trial.getItem(slot));
            bag.setChanged();
            return Outcome.DONE;
        }
        return materials && !benchNearby ? Outcome.NEEDS_BENCH : known ? Outcome.NO_MATERIALS : Outcome.NO_RECIPE;
    }

    /** Which bag slot pays for which recipe slot; null when something is missing. */
    private static @Nullable List<Integer> match(final Container bag, final List<Ingredient> perSlot) {
        int[] spent = new int[bag.getContainerSize()];
        List<Integer> chosen = new ArrayList<>();
        for (Ingredient ingredient : perSlot) {
            int found = -1;
            for (int slot = 0; slot < bag.getContainerSize(); slot++) {
                ItemStack stack = bag.getItem(slot);
                if (stack.getCount() - spent[slot] > 0 && ingredient.test(stack)) { found = slot; break; }
            }
            if (found < 0) return null;
            spent[found]++;
            chosen.add(found);
        }
        return chosen;
    }

    /** Search loaded sections with tables in their palette; skip unreachable workstations. */
    public static @Nullable BlockPos findBench(final NpcHelperEntity helper) {
        if (!(helper.level() instanceof ServerLevel level)) return null;
        BlockPos feet = helper.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = (feet.getX() - BENCH_SEARCH) >> 4; x <= (feet.getX() + BENCH_SEARCH) >> 4; x++) {
            for (int z = (feet.getZ() - BENCH_SEARCH) >> 4; z <= (feet.getZ() + BENCH_SEARCH) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                // Crafting tables have no block entity: scan only palettes that contain one.
                for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
                    var section = chunk.getSection(sectionIndex);
                    if (!section.maybeHas(state -> state.is(Blocks.CRAFTING_TABLE))) continue;
                    int bottom = level.getSectionYFromSectionIndex(sectionIndex) << 4;
                    for (int dy = 0; dy < 16; dy++) for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                        if (!section.getBlockState(dx, dy, dz).is(Blocks.CRAFTING_TABLE)) continue;
                        BlockPos pos = new BlockPos((x << 4) + dx, bottom + dy, (z << 4) + dz);
                        if (pos.distSqr(feet) <= BENCH_SEARCH * BENCH_SEARCH) candidates.add(pos);
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(feet)));
        for (BlockPos pos : candidates) {
            if (withinBenchReach(helper, pos)) return pos;
            var path = helper.getNavigation().createPath(pos, 2);
            if (path != null && path.canReach()) return pos;
        }
        return null;
    }

    public static boolean withinBenchReach(final NpcHelperEntity helper, final BlockPos pos) {
        return pos.distToCenterSqr(helper.position()) <= BENCH_REACH * BENCH_REACH;
    }

    /** Place exactly one carried table, and consume it only after the world accepted the block. */
    public static @Nullable BlockPos placeBench(final ServerLevel level, final Container bag, final BlockPos feet) {
        int slot = -1;
        for (int index = 0; index < bag.getContainerSize(); index++) {
            if (bag.getItem(index).is(Blocks.CRAFTING_TABLE.asItem())) { slot = index; break; }
        }
        if (slot < 0) return null;
        for (BlockPos pos : List.of(feet.north(), feet.south(), feet.east(), feet.west())) {
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolid()) continue;
            if (!level.setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState())) continue;
            bag.removeItem(slot, 1);
            bag.setChanged();
            return pos.immutable();
        }
        return null;
    }
}
