package dev.npchelper.inventory;

import dev.npchelper.NpcHelperMod;
import dev.npchelper.entity.HelperMode;
import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The helper's gear and bag, plus the mode buttons. Buttons travel on the vanilla menu-button packet, so the mod needs no
 * channel of its own for them.
 */
public final class HelperMenu extends AbstractContainerMenu {
    public static final int BUTTON_ROAM = 0, BUTTON_STAY = 1, BUTTON_FOLLOW = 2, BUTTON_RADIUS = 3, BUTTON_PROFESSION = 4;

    public static final int EQUIPMENT_COUNT = HelperEquipmentContainer.SLOTS.length;
    public static final int BAG_SIZE = NpcHelperEntity.BAG_SIZE;
    private static final int HELPER_SLOTS = EQUIPMENT_COUNT + BAG_SIZE;

    /** Laid out by the screen, which draws a frame around each one. */
    public static final int LEFT = 12, EQUIPMENT_Y = 88, BAG_Y = 116, PLAYER_Y = 180, HOTBAR_Y = 238;

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
        Container equipment = helper == null ? new SimpleContainer(EQUIPMENT_COUNT) : new HelperEquipmentContainer(helper);
        bag.startOpen(inventory.player);

        for (int index = 0; index < EQUIPMENT_COUNT; index++) {
            final EquipmentSlot type = HelperEquipmentContainer.SLOTS[index];
            // Armour on the left, the two hands set apart on the right.
            int x = index < 4 ? LEFT + index * 18 : LEFT + 108 + (index - 4) * 36;
            addSlot(new Slot(equipment, index, x, EQUIPMENT_Y) {
                @Override
                public int getMaxStackSize() {
                    return type.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? 1 : 64;
                }

                @Override
                public boolean mayPlace(final ItemStack stack) {
                    return type.getType() != EquipmentSlot.Type.HUMANOID_ARMOR
                            || helper == null || stack.canEquip(type, helper);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(bag, column + row * 9, LEFT + column * 18, BAG_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + column + row * 9, LEFT + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, LEFT + column * 18, HOTBAR_Y));
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
            case BUTTON_PROFESSION -> helper.cycleProfession();
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
        boolean fromHelper = slotIndex < HELPER_SLOTS;
        if (fromHelper) {
            if (!moveItemStackTo(stack, HELPER_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Armour and weapons find their own slot first, everything else goes to the bag.
            if (!moveItemStackTo(stack, 0, EQUIPMENT_COUNT, false)
                    && !moveItemStackTo(stack, EQUIPMENT_COUNT, HELPER_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
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
