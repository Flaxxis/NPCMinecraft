package dev.npchelper.client;

import dev.npchelper.entity.HelperMode;
import dev.npchelper.entity.HelperProfession;
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
import net.minecraftforge.network.PacketDistributor;

/** The helper's interface: gear, bag, modes and a name box. Drawn rather than blitted, so it needs no texture of its own. */
public final class HelperScreen extends AbstractContainerScreen<HelperMenu> {
    private static final int WIDTH = 300, HEIGHT = 262;

    private static final int BORDER = 0xFF555F70;
    private static final int PANEL = 0xFF161C28;
    private static final int SLOT_FRAME = 0xFF555F70;
    private static final int SLOT_FACE = 0xFF2B3340;
    private static final int LABEL = 0xFFB8C3D1;
    private static final int ACTIVE = 0xFF7BE87B;

    private EditBox nameBox;
    private Button roamButton, stayButton, followButton, radiusButton, professionButton;

    public HelperScreen(final HelperMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        titleLabelX = HelperMenu.LEFT;
        titleLabelY = 8;
        inventoryLabelX = HelperMenu.LEFT;
        inventoryLabelY = HelperMenu.PLAYER_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        NpcHelperEntity helper = menu.helper();

        nameBox = new EditBox(font, leftPos + HelperMenu.LEFT, topPos + 22, 276, 16, Component.literal("Ім'я"));
        nameBox.setMaxLength(RenameHelper.MAX_LENGTH);
        nameBox.setValue(helper != null && helper.hasCustomName() ? helper.getCustomName().getString() : "");
        nameBox.setResponder(this::sendName);
        addRenderableWidget(nameBox);

        int buttonsY = topPos + 44;
        roamButton = addRenderableWidget(Button.builder(Component.literal("Вільно"), b -> press(HelperMenu.BUTTON_ROAM))
                .bounds(leftPos + HelperMenu.LEFT, buttonsY, 90, 20).build());
        stayButton = addRenderableWidget(Button.builder(Component.literal("Стій тут"), b -> press(HelperMenu.BUTTON_STAY))
                .bounds(leftPos + HelperMenu.LEFT + 93, buttonsY, 90, 20).build());
        followButton = addRenderableWidget(Button.builder(Component.literal("За мною"), b -> press(HelperMenu.BUTTON_FOLLOW))
                .bounds(leftPos + HelperMenu.LEFT + 186, buttonsY, 90, 20).build());
        radiusButton = addRenderableWidget(Button.builder(radiusLabel(), b -> press(HelperMenu.BUTTON_RADIUS))
                .bounds(leftPos + HelperMenu.LEFT + 150, topPos + HelperMenu.EQUIPMENT_Y - 22, 126, 18).build());
        professionButton = addRenderableWidget(Button.builder(professionLabel(), b -> press(HelperMenu.BUTTON_PROFESSION))
                .bounds(leftPos + HelperMenu.LEFT, buttonsY + 22, 276, 20).build());
    }

    private void press(final int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void sendName(final String name) {
        NpcHelperEntity helper = menu.helper();
        if (helper != null) {
            HelperNetwork.CHANNEL.send(new RenameHelper(helper.getId(), name), PacketDistributor.SERVER.noArg());
        }
    }

    private Component radiusLabel() {
        NpcHelperEntity helper = menu.helper();
        return Component.literal("Радіус: " + (helper == null ? 0 : helper.getRoamRadius()) + " блоків");
    }

    private Component professionLabel() {
        NpcHelperEntity helper = menu.helper();
        return Component.literal("Ремесло: " + trade(helper == null ? HelperProfession.NONE : helper.getProfession()));
    }

    private static String trade(final HelperProfession profession) {
        return switch (profession) {
            case NONE -> "без діла";
            case GUARD -> "сторож";
            case LUMBERJACK -> "лісоруб";
            case MINER -> "шахтар";
            case SMELTER -> "кузнець";
            case HUNTER -> "мисливець";
            case BUILDER -> "будівельник";
            case KEEPER -> "комірник";
        };
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        for (var slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_FRAME);
            graphics.fill(sx, sy, sx + 16, sy + 16, SLOT_FACE);
        }
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, LABEL, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
        graphics.text(font, Component.literal("Спорядження"), HelperMenu.LEFT, HelperMenu.EQUIPMENT_Y - 12, LABEL, false);
        graphics.text(font, Component.literal("Сумка"), HelperMenu.LEFT, HelperMenu.BAG_Y - 12, LABEL, false);

        NpcHelperEntity helper = menu.helper();
        if (helper == null) return;
        graphics.text(font, Component.literal("Зараз: " + state(helper)), HelperMenu.LEFT, 68, ACTIVE, false);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        radiusButton.setMessage(radiusLabel());
        professionButton.setMessage(professionLabel());
        NpcHelperEntity helper = menu.helper();
        if (helper == null) return;
        roamButton.active = helper.getMode() != HelperMode.ROAM;
        stayButton.active = helper.getMode() != HelperMode.STAY;
        followButton.active = helper.getMode() != HelperMode.FOLLOW;
        radiusButton.active = helper.getMode() == HelperMode.ROAM;
    }

    private static String state(final NpcHelperEntity helper) {
        return switch (helper.getMode()) {
            case ROAM -> "ходить вільно";
            case STAY -> "стоїть тут";
            case FOLLOW -> "іде за тобою";
        };
    }
}
