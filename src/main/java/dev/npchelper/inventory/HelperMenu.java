package dev.npchelper.inventory;

import dev.npchelper.NpcHelperMod;
import dev.npchelper.entity.HelperMode;
import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The helper's bag, plus the mode buttons. Buttons travel on the vanilla menu-button packet, so the mod needs none of its own. */
public final class HelperMenu extends AbstractContainerMenu {
    public static final int BUTTON_ROAM = 0, BUTTON_STAY = 1, BUTTON_FOLLOW = 2, BUTTON_RADIUS = 3;
    private static final int BAG_SIZE = NpcHelperEntity.BAG_SIZE;

    private final @Nullable NpcHelperEntity helper;
    private final Container bag;

    /** Built on the client from the entity id the server wrote when the menu was opened. */
    public static HelperMenu fromNetwork(final int containerId, final Inventory inventory, final FriendlyByteBuf data) {
        var entity = inventory.player.level().getEntity(data.readVarInt());
        return new HelperMenu(containerId, inventory, entity instanceof NpcHelperEntity found ? found : null);
    }

    public HelperMenu(final int containerId, final Inventory inventory, final @Nullable NpcHelperEntity helper) {
        super(NpcHelperMod.HELPER_MENU.get(), containerId);
        this.helper = helper;
        this.bag = helper == null ? new SimpleContainer(BAG_SIZE) : helper.getBag();
        bag.startOpen(inventory.player);

        for (int column = 0; column < BAG_SIZE; column++) addSlot(new Slot(bag, column, 8 + column * 18, 36));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
    }

    public @Nullable NpcHelperEntity helper() {
        return helper;
    }

    @Override
    public boolean clickMenuButton(final Player player, final int buttonId) {
        if (helper == null || !helper.isOwnedBy(player)) return false;
        switch (buttonId) {
            case BUTTON_ROAM -> helper.setMode(HelperMode.ROAM);
            case BUTTON_STAY -> helper.setMode(HelperMode.STAY);
            case BUTTON_FOLLOW -> helper.setMode(HelperMode.FOLLOW);
            case BUTTON_RADIUS -> helper.cycleRoamRadius();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean fromBag = slotIndex < BAG_SIZE;
        if (fromBag ? !moveItemStackTo(stack, BAG_SIZE, slots.size(), true)
                : !moveItemStackTo(stack, 0, BAG_SIZE, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public void removed(final Player player) {
        super.removed(player);
        bag.stopOpen(player);
    }

    @Override
    public boolean stillValid(final Player player) {
        return helper != null && helper.isAlive() && helper.distanceTo(player) < 8.0F;
    }
}
