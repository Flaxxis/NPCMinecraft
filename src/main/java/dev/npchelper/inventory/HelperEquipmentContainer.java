package dev.npchelper.inventory;

import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Views the helper's real equipment, so the menu, the model, the fighting and the save data all share the same stacks. */
public final class HelperEquipmentContainer implements Container {
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final NpcHelperEntity helper;

    public HelperEquipmentContainer(final NpcHelperEntity helper) {
        this.helper = helper;
    }

    @Override
    public int getContainerSize() {
        return SLOTS.length;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot slot : SLOTS) {
            if (!helper.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(final int slot) {
        return helper.getItemBySlot(SLOTS[slot]);
    }

    @Override
    public ItemStack removeItem(final int slot, final int count) {
        ItemStack remaining = getItem(slot).copy();
        ItemStack removed = remaining.split(count);
        setItem(slot, remaining);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        ItemStack held = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return held;
    }

    @Override
    public void setItem(final int slot, final ItemStack stack) {
        helper.equipFromPlayer(SLOTS[slot], stack);
    }

    @Override
    public void setChanged() { }

    @Override
    public boolean stillValid(final Player player) {
        return helper.isAlive() && helper.distanceTo(player) < 8.0F;
    }

    @Override
    public void clearContent() {
        for (EquipmentSlot slot : SLOTS) helper.setItemSlot(slot, ItemStack.EMPTY);
    }
}
