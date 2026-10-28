package dev.npchelper.work;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Making things out of what is in the bag, by the game's own recipes. A three-by-three recipe wants a bench to work at. */
public final class HelperCrafting {
    /** How far a bench may stand and still be worked at. */
    public static final double BENCH_REACH = 4.0D;

    public enum Outcome { DONE, NO_MATERIALS, NEEDS_BENCH, NO_RECIPE }

    private HelperCrafting() { }

    public static @Nullable Item item(final String key) {
        var id = Identifier.tryParse(key);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /** One craft of the item into the bag. */
    public static Outcome craft(final ServerLevel level, final Container bag, final Item wanted, final boolean benchNearby) {
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe) || recipe.isSpecial()) continue;

            ItemStack result = recipe.assemble(CraftingInput.EMPTY);
            if (result.isEmpty() || !result.is(wanted)) continue;

            var placement = recipe.placementInfo();
            if (placement.isImpossibleToPlace()) continue;
            var ingredients = placement.ingredients();

            List<Ingredient> perSlot = new ArrayList<>();
            for (int index : placement.slotsToIngredientIndex()) {
                if (index >= 0 && index < ingredients.size()) perSlot.add(ingredients.get(index));
            }
            if (perSlot.isEmpty()) continue;
            if (perSlot.size() > 4 && !benchNearby) return Outcome.NEEDS_BENCH;

            List<Integer> slotsToTake = match(bag, perSlot);
            if (slotsToTake == null) continue;

            for (int slot : slotsToTake) bag.removeItem(slot, 1);
            ItemStack left = addTo(bag, result.copy());
            bag.setChanged();
            return left.isEmpty() ? Outcome.DONE : Outcome.DONE;
        }
        return Outcome.NO_RECIPE;
    }

    /** True when the bag holds everything some recipe for this item needs. */
    public static boolean canCraft(final ServerLevel level, final Container bag, final Item wanted) {
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe) || recipe.isSpecial()) continue;
            ItemStack result = recipe.assemble(CraftingInput.EMPTY);
            if (result.isEmpty() || !result.is(wanted)) continue;

            var placement = recipe.placementInfo();
            var ingredients = placement.ingredients();
            List<Ingredient> perSlot = new ArrayList<>();
            for (int index : placement.slotsToIngredientIndex()) {
                if (index >= 0 && index < ingredients.size()) perSlot.add(ingredients.get(index));
            }
            if (!perSlot.isEmpty() && match(bag, perSlot) != null) return true;
        }
        return false;
    }

    /** Which bag slot pays for which recipe slot; null when something is missing. */
    private static @Nullable List<Integer> match(final Container bag, final List<Ingredient> perSlot) {
        int[] spent = new int[bag.getContainerSize()];
        List<Integer> chosen = new ArrayList<>(perSlot.size());
        for (Ingredient ingredient : perSlot) {
            int found = -1;
            for (int slot = 0; slot < bag.getContainerSize(); slot++) {
                ItemStack stack = bag.getItem(slot);
                if (stack.isEmpty() || stack.getCount() - spent[slot] <= 0) continue;
                if (!ingredient.test(stack)) continue;
                found = slot;
                break;
            }
            if (found < 0) return null;
            spent[found]++;
            chosen.add(found);
        }
        return chosen;
    }

    private static ItemStack addTo(final Container bag, final ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < bag.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack inSlot = bag.getItem(slot);
            if (inSlot.isEmpty()) {
                bag.setItem(slot, remaining.copyAndClear());
            } else if (ItemStack.isSameItemSameComponents(inSlot, remaining)) {
                int room = inSlot.getMaxStackSize() - inSlot.getCount();
                int moved = Math.min(room, remaining.getCount());
                if (moved > 0) {
                    inSlot.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
        return remaining;
    }

    /** A bench the helper can work at without setting one down. */
    public static boolean benchNearby(final ServerLevel level, final BlockPos around) {
        int reach = (int) Math.ceil(BENCH_REACH);
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-reach, -2, -reach), around.offset(reach, 2, reach))) {
            if (level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) return true;
        }
        return false;
    }

    /** Sets a bench down beside the helper; the spot it took, or null when there was nowhere for it. */
    public static @Nullable BlockPos placeBench(final ServerLevel level, final BlockPos feet) {
        for (BlockPos pos : List.of(feet.north(), feet.south(), feet.east(), feet.west())) {
            if (!level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.below()).isSolid()) continue;
            level.setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
            return pos.immutable();
        }
        return null;
    }
}
