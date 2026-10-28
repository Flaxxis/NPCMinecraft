package dev.npchelper.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Food restores nutrition points as health, preserving vanilla consumption effects and containers. */
public final class HelperEating {
    private static final int EAT_INTERVAL = 100;
    private final NpcHelperEntity helper;
    private long nextMeal;

    public HelperEating(final NpcHelperEntity helper) { this.helper = helper; }

    private boolean canEat(final ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        return helper.isAlive() && helper.getHealth() < helper.getMaxHealth() && !stack.isEmpty()
                && food != null && food.nutrition() > 0 && stack.has(DataComponents.CONSUMABLE);
    }

    public boolean feed(final Player player, final InteractionHand hand) {
        ItemStack offered = player.getItemInHand(hand);
        if (!canEat(offered) || !(helper.level() instanceof ServerLevel level)) return false;
        ItemStack meal = player.hasInfiniteMaterials() ? offered.copyWithCount(1) : offered.split(1);
        ItemStack remainder = consume(meal);
        if (!remainder.isEmpty() && !player.getInventory().add(remainder)) helper.spawnAtLocation(level, remainder);
        return true;
    }

    public void tick() {
        if (!(helper.level() instanceof ServerLevel level) || level.getGameTime() < nextMeal) return;
        for (int slot = 0; slot < helper.getBag().getContainerSize(); slot++) {
            ItemStack stack = helper.getBag().getItem(slot);
            if (!canEat(stack)) continue;
            ItemStack meal = helper.getBag().removeItem(slot, 1);
            ItemStack remainder = consume(meal);
            if (!remainder.isEmpty()) {
                ItemStack left = helper.getBag().addItem(remainder);
                if (!left.isEmpty()) helper.spawnAtLocation(level, left);
            }
            helper.getBag().setChanged();
            return;
        }
    }

    private ItemStack consume(final ItemStack meal) {
        var food = meal.get(DataComponents.FOOD);
        int nutrition = food == null ? 0 : food.nutrition();
        ItemStack remainder = meal.finishUsingItem(helper.level(), helper);
        helper.heal(nutrition);
        nextMeal = helper.level().getGameTime() + EAT_INTERVAL;
        return remainder;
    }
}
