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
    private static final int WIDTH = 300, HEIGHT = 330;
    /** Each control keeps its own band, so nothing is drawn over anything else. */
    private static final int NAME_Y = 22, TABS_Y = 44, MODES_Y = 70, RANGE_Y = 94, STATE_Y = 122;

    private static final int BORDER = 0xFF555F70;
    private static final int PANEL = 0xFF161C28;
    private static final int SLOT_FRAME = 0xFF555F70;
    private static final int SLOT_FACE = 0xFF2B3340;
    private static final int LABEL = 0xFFB8C3D1;
    private static final int ACTIVE = 0xFF7BE87B;

    private EditBox nameBox;
    private Button expeditionButton;
    private Button roamButton, stayButton, followButton, movementTab, professionTab, actionsTab, previousPage, nextPage;
    private final java.util.List<Button> radiusButtons = new java.util.ArrayList<>();
    private final java.util.List<Button> professionButtons = new java.util.ArrayList<>();
    /** The trade behind each button, in the same order: the buttons are no longer one per trade. */
    private final java.util.List<HelperProfession> shownProfessions = new java.util.ArrayList<>();
    private enum Tab { MOVEMENT, PROFESSIONS, ACTIONS }
    private Tab selectedTab = Tab.MOVEMENT;
    private static final int PAGE_SIZE = 6;
    private int actionPage;

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

        nameBox = new EditBox(font, leftPos + HelperMenu.LEFT, topPos + NAME_Y, 276, 16, Component.literal("Ім'я"));
        nameBox.setMaxLength(RenameHelper.MAX_LENGTH);
        nameBox.setValue(helper != null && helper.hasCustomName() ? helper.getCustomName().getString() : "");
        nameBox.setResponder(this::sendName);
        addRenderableWidget(nameBox);

        radiusButtons.clear();
        professionButtons.clear();
        movementTab = addRenderableWidget(Button.builder(Component.literal("Переміщення"), b -> selectTab(Tab.MOVEMENT))
                .bounds(leftPos + HelperMenu.LEFT, topPos + TABS_Y, 90, 20).build());
        professionTab = addRenderableWidget(Button.builder(Component.literal("Професії"), b -> selectTab(Tab.PROFESSIONS))
                .bounds(leftPos + HelperMenu.LEFT + 93, topPos + TABS_Y, 90, 20).build());
        actionsTab = addRenderableWidget(Button.builder(Component.literal("Дії"), b -> selectTab(Tab.ACTIONS))
                .bounds(leftPos + HelperMenu.LEFT + 186, topPos + TABS_Y, 90, 20).build());
        previousPage = addRenderableWidget(Button.builder(Component.literal("Назад"), b -> changePage(-1))
                .bounds(leftPos + HelperMenu.LEFT, topPos + 300, 76, 20).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal("Далі"), b -> changePage(1))
                .bounds(leftPos + HelperMenu.LEFT + 200, topPos + 300, 76, 20).build());
        int modesY = topPos + MODES_Y;
        roamButton = addRenderableWidget(Button.builder(Component.literal("Вільно"), b -> press(HelperMenu.BUTTON_ROAM))
                .bounds(leftPos + HelperMenu.LEFT, modesY, 90, 20).build());
        stayButton = addRenderableWidget(Button.builder(Component.literal("Стій тут"), b -> press(HelperMenu.BUTTON_STAY))
                .bounds(leftPos + HelperMenu.LEFT + 93, modesY, 90, 20).build());
        followButton = addRenderableWidget(Button.builder(Component.literal("За мною"), b -> press(HelperMenu.BUTTON_FOLLOW))
                .bounds(leftPos + HelperMenu.LEFT + 186, modesY, 90, 20).build());

        for (int index = 0; index < NpcHelperEntity.ROAM_RADII.length; index++) {
            final int choice = index;
            radiusButtons.add(addRenderableWidget(Button.builder(Component.literal("" + NpcHelperEntity.ROAM_RADII[index]),
                    b -> press(HelperMenu.BUTTON_RADIUS + choice))
                    .bounds(leftPos + HelperMenu.LEFT + 80 + index * 40, topPos + RANGE_Y, 36, 20).build()));
        }
        // Only the trades that work are offered; each button still sends its trade's own number.
        shownProfessions.clear();
        for (HelperProfession profession : HelperProfession.values()) {
            if (!profession.available()) continue;
            int index = shownProfessions.size();
            shownProfessions.add(profession);
            professionButtons.add(addRenderableWidget(Button.builder(Component.literal(trade(profession)),
                    b -> press(HelperMenu.BUTTON_PROFESSION + profession.ordinal()))
                    .bounds(leftPos + HelperMenu.LEFT + index % 4 * 70, topPos + MODES_Y + index / 4 * 24, 66, 20).build()));
        }
        expeditionButton = addRenderableWidget(Button.builder(Component.literal("Звичайний"), b -> press(HelperMenu.BUTTON_EXPEDITION))
                .bounds(leftPos + HelperMenu.LEFT, topPos + 116, 100, 16).build());
        expeditionButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Звичайний: уночі шукає дім. Похід: не йде додому через ніч.")));
        selectTab(selectedTab);
        updateButtons();
    }

    private void selectTab(final Tab tab) {
        selectedTab = tab;
        menu.setActionsTab(tab == Tab.ACTIONS);
        movementTab.active = tab != Tab.MOVEMENT;
        professionTab.active = tab != Tab.PROFESSIONS;
        actionsTab.active = tab != Tab.ACTIONS;
        expeditionButton.visible = tab == Tab.MOVEMENT;
        roamButton.visible = stayButton.visible = followButton.visible = tab == Tab.MOVEMENT;
        for (Button button : radiusButtons) button.visible = tab == Tab.MOVEMENT;
        for (Button button : professionButtons) button.visible = tab == Tab.PROFESSIONS;
        previousPage.visible = nextPage.visible = tab == Tab.ACTIONS;
        updatePages();
    }

    private int pageCount() { return Math.max(1, (menu.actions().size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    private void changePage(final int delta) {
        actionPage += delta;
        updatePages();
    }

    private void updatePages() {
        actionPage = Math.clamp(actionPage, 0, pageCount() - 1);
        previousPage.active = actionPage > 0;
        nextPage.active = actionPage + 1 < pageCount();
    }

    @Override
    public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
        if (selectedTab == Tab.ACTIONS && x >= leftPos && x < leftPos + WIDTH
                && y >= topPos + MODES_Y && y < topPos + HEIGHT && scrollY != 0) {
            changePage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
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
            if (!slot.isActive()) continue;
            int sx = x + slot.x, sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_FRAME);
            graphics.fill(sx, sy, sx + 16, sy + 16, SLOT_FACE);
        }
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        NpcHelperEntity helper = menu.helper();
        String health = helper == null ? "" : String.format(java.util.Locale.ROOT, "Життя: %.1f / %.1f", helper.getHealth(), helper.getMaxHealth());
        int healthX = WIDTH - HelperMenu.LEFT - font.width(health);
        graphics.text(font, font.plainSubstrByWidth(title.getString(), Math.max(0, healthX - titleLabelX - 8)), titleLabelX, titleLabelY, LABEL, false);
        if (helper != null) graphics.text(font, health, healthX, titleLabelY, helper.getHealth() < helper.getMaxHealth() ? 0xFFFF9999 : ACTIVE, false);
        if (selectedTab == Tab.ACTIONS) {
            extractActions(graphics);
            return;
        }
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
        graphics.text(font, Component.literal("Спорядження"), HelperMenu.LEFT, HelperMenu.EQUIPMENT_Y - 12, LABEL, false);
        graphics.text(font, Component.literal("Вантаж"), 236, HelperMenu.EQUIPMENT_Y - 12, LABEL, false);
        graphics.text(font, Component.literal("Сумка"), HelperMenu.LEFT, HelperMenu.BAG_Y - 12, LABEL, false);

        if (selectedTab == Tab.MOVEMENT) graphics.text(font, Component.literal("Радіус:"), HelperMenu.LEFT, RANGE_Y + 6, LABEL, false);
        if (helper == null) return;
        String status = selectedTab == Tab.PROFESSIONS ? "Ремесло: " + trade(helper.getProfession()) : "Зараз: " + state(helper);
        graphics.text(font, Component.literal(status), selectedTab == Tab.MOVEMENT ? 120 : HelperMenu.LEFT, STATE_Y, ACTIVE, false);
    }

    private void extractActions(final GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.literal("Ланцюжок дій: " + menu.actions().size()), HelperMenu.LEFT, 72, LABEL, false);
        graphics.text(font, font.plainSubstrByWidth(menu.actionStatus(), 276), HelperMenu.LEFT, 88, ACTIVE, false);
        var steps = menu.actions();
        if (steps.isEmpty()) {
            graphics.text(font, Component.literal("Черга порожня"), HelperMenu.LEFT, 114, LABEL, false);
        }
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = actionPage * PAGE_SIZE + row;
            if (index >= steps.size()) break;
            var step = steps.get(index);
            int y = 110 + row * 30;
            int color = index == 0 ? ACTIVE : LABEL;
            String label = (index + 1) + ". " + actionName(step);
            graphics.text(font, font.plainSubstrByWidth(label, 276), HelperMenu.LEFT, y, color, false);
            String progress = index == 0 ? "Зараз" : "Далі";
            if (step.count() > 1 || step.type().equals(dev.npchelper.work.HelperStep.CRAFT)) {
                progress += " · " + step.done() + " / " + step.count();
                if (step.type().equals(dev.npchelper.work.HelperStep.CRAFT)) progress += " крафтів";
            }
            graphics.text(font, Component.literal(progress), HelperMenu.LEFT + 12, y + 12, color, false);
        }
        graphics.text(font, Component.literal((actionPage + 1) + " / " + pageCount()), 136, 306, LABEL, false);
    }

    private static String actionName(final dev.npchelper.work.HelperStep.View step) {
        return switch (step.type()) {
            case "prepare_kit" -> "Перевірити 10 землі та власний верстак";
            case "prepare_tools" -> "Підготувати інструменти професії";
            case "prepare_store" -> "Знайти скриню та позначити склад";
            case "keeper_work" -> "Переносити ресурси й об'єднувати стаки";
            case "keeper_deliver" -> "Доставити збережений вантаж";
            case "prepare_grove" -> "Знайти або підготувати табличку «гай»";
            case "prepare_axe" -> "Знайти або виготовити сокиру";
            case "mark_grove" -> "Знайти дерево й поставити табличку";
            case "bootstrap_wood" -> "Добути колоди для підготовки";
            case "gather_wood" -> step.item().equals("#materials") ? "Добути деревину для крафту" : "Заготовити деревину";
            case "gather_dirt" -> "Зібрати землю для сходинок";
            case "craft" -> "Виготовити: " + itemName(step.item());
            case "fetch" -> "Забрати зі сховища: " + itemName(step.item());
            case "equip" -> "Взяти в руку: " + itemName(step.item());
            case "store" -> "Віднести здобич у сховище";
            case "take_bench" -> "Прибрати власний верстак";
            case "recover_supports" -> "Прибрати власні земляні сходинки";
            case "plant" -> "Посадити саджанець";
            default -> "Інша дія: " + step.type();
        };
    }

    private static String itemName(final String key) {
        if (key.equals("#planks")) return "дошки";
        if (key.equals("#signs")) return "таблички";
        var item = dev.npchelper.work.HelperCrafting.item(key);
        return item == null ? key : new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateButtons();
        updatePages();
    }

    private void updateButtons() {
        NpcHelperEntity helper = menu.helper();
        if (helper == null) return;
        expeditionButton.setMessage(Component.literal(helper.isExpedition() ? "Похід" : "Звичайний"));
        roamButton.active = helper.getMode() != HelperMode.ROAM;
        stayButton.active = helper.getMode() != HelperMode.STAY;
        followButton.active = helper.getMode() != HelperMode.FOLLOW;
        for (int index = 0; index < radiusButtons.size(); index++) {
            radiusButtons.get(index).active = helper.getRoamRadius() != NpcHelperEntity.ROAM_RADII[index];
        }
        for (int index = 0; index < professionButtons.size(); index++) {
            professionButtons.get(index).active = helper.getProfession() != shownProfessions.get(index);
        }
    }

    private static String state(final NpcHelperEntity helper) {
        return switch (helper.getMode()) {
            case ROAM -> "ходить вільно";
            case STAY -> "стоїть тут";
            case FOLLOW -> "іде за тобою";
        };
    }
}
