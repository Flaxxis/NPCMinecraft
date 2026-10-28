package dev.npchelper.inventory;

import dev.npchelper.NpcHelperMod;
import dev.npchelper.entity.HelperMode;
import dev.npchelper.entity.HelperProfession;
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
    public static final int BUTTON_ROAM = 0, BUTTON_STAY = 1, BUTTON_FOLLOW = 2;
    public static final int BUTTON_RADIUS = 10, BUTTON_PROFESSION = 20, BUTTON_EXPEDITION = 30;

    public static final int EQUIPMENT_COUNT = HelperEquipmentContainer.SLOTS.length;
    public static final int BAG_SIZE = NpcHelperEntity.BAG_SIZE;
    private static final int HELPER_SLOTS = EQUIPMENT_COUNT + BAG_SIZE;

    /** Laid out by the screen, which draws a frame around each one. */
    public static final int LEFT = 12, EQUIPMENT_Y = 146, BAG_Y = 178, PLAYER_Y = 248, HOTBAR_Y = 306;

    private final @Nullable NpcHelperEntity helper;
    private final Container bag;
    private final Player viewer;
    private boolean actionsTab;
    private dev.npchelper.net.HelperActions lastActions;
    private java.util.List<dev.npchelper.work.HelperStep.View> actions = java.util.List.of();
    private String actionStatus = "Отримую список дій…";

    public void setActionsTab(final boolean visible) { actionsTab = visible; }
    public java.util.List<dev.npchelper.work.HelperStep.View> actions() { return actions; }
    public String actionStatus() { return actionStatus; }

    public void receiveActions(final dev.npchelper.net.HelperActions message) {
        if (helper == null || message.containerId() != containerId || message.entityId() != helper.getId()) return;
        actions = message.steps();
        actionStatus = message.status();
    }

    /** Slots stay synchronized while the queue page hides their rendering and mouse targets. */
    private Slot inventorySlot(final Container container, final int slot, final int x, final int y) {
        return new Slot(container, slot, x, y) {
            @Override public boolean isActive() { return !actionsTab; }
        };
    }

    public dev.npchelper.net.HelperActions actionSnapshot() {
        if (helper == null) return new dev.npchelper.net.HelperActions(containerId, -1, "NPC недоступний", java.util.List.of());
        String status;
        if (helper.isRetreating()) status = "Відступає до власника";
        else if (helper.getTarget() != null) status = "Бій — роботу призупинено";
        else if (helper.mustReturnToOwner()) status = "Повертається до власника — роботу призупинено";
        else if (helper.nightGoal().active()) status = helper.nightGoal().hasDestination()
                ? "Ніч — іде додому або відпочиває в укритті" : "Ніч — шукає дім або вхід до укриття";
        else if (helper.pickup().isChasing()) status = "Підбирає доступну здобич";
        else if (helper.pickup().waitingForSpace()) status = "Сумка повна — підбір призупинено";
        else if (helper.pickup().hasDeferredDrops()) status = "Недоступна здобич — підбір відкладено";
        else if (helper.queue().isEmpty()) status = helper.getProfession() == HelperProfession.NONE
                ? "Немає запланованої роботи" : "Відпочиває / очікує нового циклу";
        else if (!helper.workProblem().isEmpty()) status = helper.workProblem();
        else status = "Виконує ланцюжок дій";
        var steps = new java.util.ArrayList<>(helper.queue().snapshot());
        if (!steps.isEmpty() && steps.getFirst().type().equals(dev.npchelper.work.HelperStep.GATHER_DIRT)) {
            int soil = 0;
            for (var stack : helper.getBag().getItems()) if (stack.is(net.minecraft.tags.ItemTags.DIRT)) soil += stack.getCount();
            var first = steps.getFirst();
            steps.set(0, new dev.npchelper.work.HelperStep.View(first.type(), first.item(), first.count(), soil));
        }
        for (int index = 0; index < steps.size(); index++) {
            var step = steps.get(index);
            if (step.type().equals(dev.npchelper.work.HelperStep.GATHER_WOOD)
                    || step.type().equals(dev.npchelper.work.HelperStep.BOOTSTRAP_WOOD)) {
                steps.set(index, new dev.npchelper.work.HelperStep.View(step.type(), step.item(), step.count(), helper.carriedLogs()));
            }
        }
        return new dev.npchelper.net.HelperActions(containerId, helper.getId(), status, steps);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(viewer instanceof net.minecraft.server.level.ServerPlayer player)) return;
        var snapshot = actionSnapshot();
        if (!snapshot.equals(lastActions)) {
            dev.npchelper.net.HelperNetwork.CHANNEL.send(snapshot, net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
            lastActions = snapshot;
        }
    }

    /** Built on the client from the entity id the server wrote when the menu was opened. */
    public static HelperMenu fromNetwork(final int containerId, final Inventory inventory, final FriendlyByteBuf data) {
        var entity = inventory.player.level().getEntity(data.readVarInt());
        return new HelperMenu(containerId, inventory, entity instanceof NpcHelperEntity found ? found : null);
    }

    public HelperMenu(final int containerId, final Inventory inventory, final @Nullable NpcHelperEntity helper) {
        super(NpcHelperMod.HELPER_MENU.get(), containerId);
        this.helper = helper;
        this.viewer = inventory.player;
        this.bag = helper == null ? new SimpleContainer(BAG_SIZE) : helper.getBag();
        Container equipment = helper == null ? new SimpleContainer(EQUIPMENT_COUNT) : new HelperEquipmentContainer(helper);
        bag.startOpen(inventory.player);

        for (int index = 0; index < EQUIPMENT_COUNT; index++) {
            final EquipmentSlot type = HelperEquipmentContainer.SLOTS[index];
            // Armour on the left, the two hands set apart on the right.
            int x = index < 4 ? LEFT + index * 18 : LEFT + 108 + (index - 4) * 36;
            addSlot(new Slot(equipment, index, x, EQUIPMENT_Y) {
                @Override public boolean isActive() { return !actionsTab; }

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
                addSlot(inventorySlot(bag, column + row * 9, LEFT + column * 18, BAG_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(inventorySlot(inventory, 9 + column + row * 9, LEFT + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(inventorySlot(inventory, column, LEFT + column * 18, HOTBAR_Y));
    }

    public @Nullable NpcHelperEntity helper() {
        return helper;
    }

    @Override
    public boolean clickMenuButton(final Player player, final int buttonId) {
        if (helper == null || !helper.isOwnedBy(player)) return false;
        if (buttonId == BUTTON_EXPEDITION) {
            helper.setExpedition(!helper.isExpedition());
            return true;
        }
        int radius = buttonId - BUTTON_RADIUS;
        if (radius >= 0 && radius < NpcHelperEntity.ROAM_RADII.length) {
            helper.setRoamRadius(NpcHelperEntity.ROAM_RADII[radius]);
            return true;
        }
        int profession = buttonId - BUTTON_PROFESSION;
        if (profession >= 0 && profession < HelperProfession.values().length) {
            helper.setProfession(HelperProfession.values()[profession]);
            return true;
        }
        switch (buttonId) {
            case BUTTON_ROAM -> helper.setMode(HelperMode.ROAM);
            case BUTTON_STAY -> helper.setMode(HelperMode.STAY);
            case BUTTON_FOLLOW -> helper.setMode(HelperMode.FOLLOW);
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
