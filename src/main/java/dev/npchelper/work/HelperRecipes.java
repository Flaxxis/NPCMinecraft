package dev.npchelper.work;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Reading what a recipe makes without having laid anything out yet.
 *
 * Only the two plain kinds answer that question safely. A shaped or shapeless recipe carries its result ready-made, but a
 * transmuting one — dyeing a shulker box, say — builds its result out of the item put in, and asking it with nothing in hand
 * throws. Everything else is passed over: a helper has no business making it anyway.
 */
public final class HelperRecipes {
    private HelperRecipes() { }

    /** What the recipe makes, or an empty stack when that cannot be told without inputs. */
    public static ItemStack resultOf(final Recipe<?> recipe) {
        if (recipe.isSpecial()) return ItemStack.EMPTY;
        if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) return ItemStack.EMPTY;
        try {
            return ((Recipe<CraftingInput>) recipe).assemble(CraftingInput.EMPTY);
        } catch (RuntimeException ignored) {
            // A recipe that will not say what it makes is simply not one the helper can plan with.
            return ItemStack.EMPTY;
        }
    }
}
