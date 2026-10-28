package dev.npchelper.client;

import dev.npchelper.entity.HelperMode;
import dev.npchelper.entity.NpcHelperEntity;
import dev.npchelper.inventory.HelperMenu;
import dev.npchelper.net.HelperNetwork;
import dev.npchelper.net.RenameHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The helper's interface: its bag, the mode buttons and the name box. Drawn rather than blitted, so it needs no texture. */
public final class HelperScreen extends AbstractContainerScreen<HelperMenu> {
    private static final int WIDTH = 176, HEIGHT = 166;
    private static final int PANEL = 0xFFC6C6C6, PANEL_LIGHT = 0xFFFFFFFF, PANEL_DARK = 0xFF555555, SLOT = 0xFF8B8B8B;
    private static final int ACTIVE_LABEL = 0xFF55FF55;

    private EditBox nameBox;
    private Button roamButton, stayButton, followButton, radiusButton;

    public HelperScreen(final HelperMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelY = HEIGHT - 94;
    }

    @Override
    protected void init() {
        super.init();
        NpcHelperEntity helper = menu.helper();

        nameBox = new EditBox(font, leftPos + 8, topPos + 16, 160, 14, Component.literal("Ім'я"));
        nameBox.setMaxLength(RenameHelper.MAX_LENGTH);
        nameBox.setValue(helper != null && helper.hasCustomName() ? helper.getCustomName().getString() : "");
        nameBox.setResponder(this::sendName);
        addRenderableWidget(nameBox);

        int top = topPos + 58;
        roamButton = addRenderableWidget(Button.builder(Component.literal("Вільно"), b -> press(HelperMenu.BUTTON_ROAM))
                .bounds(leftPos + 8, top, 52, 18).build());
        stayButton = addRenderableWidget(Button.builder(Component.literal("Стій тут"), b -> press(HelperMenu.BUTTON_STAY))
                .bounds(leftPos + 62, top, 52, 18).build());
        followButton = addRenderableWidget(Button.builder(Component.literal("За мною"), b -> press(HelperMenu.BUTTON_FOLLOW))
                .bounds(leftPos + 116, top, 52, 18).build());
        radiusButton = addRenderableWidget(Button.builder(radiusLabel(), b -> press(HelperMenu.BUTTON_RADIUS))
                .bounds(leftPos + 8, top + 20, 160, 18).build());
    }

    private void press(final int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void sendName(final String name) {
        NpcHelperEntity helper = menu.helper();
        if (helper != null) HelperNetwork.CHANNEL.send(new RenameHelper(helper.getId(), name), net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    private Component radiusLabel() {
        NpcHelperEntity helper = menu.helper();
        int radius = helper == null ? 0 : helper.getRoamRadius();
        return Component.literal("Радіус вільного ходу: " + radius);
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        graphics.fill(x, y, x + WIDTH, y + 1, PANEL_LIGHT);
        graphics.fill(x, y, x + 1, y + HEIGHT, PANEL_LIGHT);
        graphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, PANEL_DARK);
        graphics.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, PANEL_DARK);
        for (var slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT);
        }
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        NpcHelperEntity helper = menu.helper();
        if (helper == null) return;
        graphics.text(font, Component.literal("Зараз: " + modeName(helper.getMode())), 8, 46, ACTIVE_LABEL, false);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        radiusButton.setMessage(radiusLabel());
        NpcHelperEntity helper = menu.helper();
        if (helper == null) return;
        roamButton.active = helper.getMode() != HelperMode.ROAM;
        stayButton.active = helper.getMode() != HelperMode.STAY;
        followButton.active = helper.getMode() != HelperMode.FOLLOW;
    }

    private static String modeName(final HelperMode mode) {
        return switch (mode) {
            case ROAM -> "ходить вільно";
            case STAY -> "стоїть тут";
            case FOLLOW -> "іде за тобою";
        };
    }
}
