package dev.npchelper.gametest;

import dev.npchelper.NpcHelperMod;
import dev.npchelper.entity.HelperMode;
import dev.npchelper.entity.HelperProfession;
import dev.npchelper.entity.NpcHelperEntity;
import dev.npchelper.inventory.HelperMenu;
import dev.npchelper.work.*;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;

/** Server regressions use real recipes, inventories, navigation and entity save data. */
public final class HelperRegressionTests {
    private HelperRegressionTests() { }

    public static void register(final BusGroup bus) {
        DeferredRegister<Consumer<GameTestHelper>> tests = DeferredRegister.create(Registries.TEST_FUNCTION, NpcHelperMod.MODID);
        tests.register("craft_full_bag", () -> HelperRegressionTests::craftFullBag);
        tests.register("craft_remainders", () -> HelperRegressionTests::craftRemainders);
        tests.register("tool_from_logs", () -> HelperRegressionTests::toolFromLogs);
        tests.register("bench_spends_item", () -> HelperRegressionTests::benchSpendsItem);
        tests.register("nearby_bench", () -> HelperRegressionTests::nearbyBench);
        tests.register("wood_runs_out", () -> HelperRegressionTests::woodRunsOut);
        tests.register("eight_logs_to_storage", () -> HelperRegressionTests::eightLogsToStorage);
        tests.register("marked_storage", () -> HelperRegressionTests::markedStorage);
        tests.register("tree_restart", () -> HelperRegressionTests::treeRestart);
        tests.register("command_cancels_tree", () -> HelperRegressionTests::commandCancelsTree);
        tests.register("movement_and_menu", () -> HelperRegressionTests::movementAndMenu);
        tests.register("lumberjack_bootstrap", () -> HelperRegressionTests::lumberjackBootstrap);
        tests.register("existing_grove", () -> HelperRegressionTests::existingGrove);
        tests.register("sign_from_materials", () -> HelperRegressionTests::signFromMaterials);
        tests.register("soil_under_grass", () -> HelperRegressionTests::soilUnderGrass);
        tests.register("tall_tree_steps", () -> HelperRegressionTests::tallTreeSteps);
        tests.register("actions_menu_sync", () -> HelperRegressionTests::actionsMenuSync);
        tests.register("profession_cancels_running_work", () -> HelperRegressionTests::professionCancelsRunningWork);
        tests.register("pickup_full_bag", () -> HelperRegressionTests::pickupFullBag);
        tests.register("pickup_unreachable", () -> HelperRegressionTests::pickupUnreachable);
        tests.register("closed_door", () -> h -> passage(h, false, false));
        tests.register("open_door", () -> h -> passage(h, false, true));
        tests.register("closed_gate", () -> h -> passage(h, true, false));
        tests.register("open_gate", () -> h -> passage(h, true, true));
        tests.register("iron_door", () -> HelperRegressionTests::ironDoor);
        tests.register("night_home", () -> h -> nightHome(h, true));
        tests.register("night_unmarked_house", () -> h -> nightHome(h, false));
        tests.register("expedition_at_night", () -> HelperRegressionTests::expeditionAtNight);
        tests.register("night_distant_home", () -> HelperRegressionTests::nightDistantHome);
        tests.register("shelter_classification", () -> HelperRegressionTests::shelterClassification);
        tests.register("night_scaffold_cleanup", () -> HelperRegressionTests::nightScaffoldCleanup);
        tests.register("night_wander_inside_4", () -> h -> nightWanderInside(h, 4));
        tests.register("night_wander_inside_16", () -> h -> nightWanderInside(h, 16));
        tests.register("night_wander_without_home", () -> HelperRegressionTests::nightWanderWithoutHome);
        tests.register("marker_in_grass", () -> HelperRegressionTests::markerInGrass);
        tests.register("soil_staircase_exit", () -> HelperRegressionTests::soilStaircaseExit);
        tests.register("food_healing", () -> HelperRegressionTests::foodHealing);
        tests.register("player_feeding", () -> HelperRegressionTests::playerFeeding);
        tests.register("inventory_harvest_count", () -> HelperRegressionTests::inventoryHarvestCount);
        tests.register("planned_inventory_target", () -> HelperRegressionTests::plannedInventoryTarget);
        tests.register(bus);
    }

    private static NpcHelperEntity helper(final GameTestHelper h) {
        NpcHelperEntity helper = h.spawn(NpcHelperMod.HELPER.get(), new BlockPos(20, 1, 20));
        helper.setMode(HelperMode.STAY);
        helper.setNoAi(true);
        helper.setOnGround(true);
        return helper;
    }

    private static void craftFullBag(final GameTestHelper h) {
        SimpleContainer bag = new SimpleContainer(2);
        bag.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        bag.setItem(1, new ItemStack(Items.COAL, 64));
        var result = HelperCrafting.craft(h.getLevel(), bag, "#planks", false);
        h.assertTrue(result == HelperCrafting.Outcome.NO_ROOM, "A full bag must reject the craft");
        h.assertTrue(bag.getItem(0).getCount() == 64 && bag.getItem(1).getCount() == 64, "Failed craft consumed items");
        bag.setItem(1, ItemStack.EMPTY);
        h.assertTrue(HelperCrafting.craft(h.getLevel(), bag, "#planks", false) == HelperCrafting.Outcome.DONE, "Craft after freeing a slot failed");
        h.assertTrue(bag.countItem(Items.OAK_LOG) == 63 && bag.countItem(Items.OAK_PLANKS) == 4, "Wrong recipe accounting");
        h.succeed();
    }

    private static void craftRemainders(final GameTestHelper h) {
        SimpleContainer bag = new SimpleContainer(6);
        for (int i = 0; i < 3; i++) bag.setItem(i, new ItemStack(Items.MILK_BUCKET));
        bag.setItem(3, new ItemStack(Items.SUGAR, 2));
        bag.setItem(4, new ItemStack(Items.EGG));
        bag.setItem(5, new ItemStack(Items.WHEAT, 3));
        h.assertTrue(HelperCrafting.craft(h.getLevel(), bag, "minecraft:cake", true) == HelperCrafting.Outcome.DONE, "Cake craft failed");
        h.assertTrue(bag.countItem(Items.CAKE) == 1 && bag.countItem(Items.BUCKET) == 3, "Crafting remainders were lost");
        h.succeed();
    }

    private static void toolFromLogs(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        var plan = HelperObtain.plan(h.getLevel(), List.of(), false, Items.WOODEN_AXE, 1);
        h.assertTrue(!plan.refused(), "Cannot plan an axe from scratch");
        boolean bench = false;
        for (HelperStep step : plan.steps()) {
            switch (step.type()) {
                case HelperStep.GATHER_WOOD -> helper.getBag().addItem(new ItemStack(Items.OAK_LOG, step.count()));
                case HelperStep.CRAFT -> {
                    for (int i = 0; i < step.count(); i++) {
                        var result = HelperCrafting.craft(h.getLevel(), helper.getBag(), step.item(), bench);
                        if (result == HelperCrafting.Outcome.NEEDS_BENCH) {
                            h.assertTrue(HelperCrafting.placeBench(h.getLevel(), helper.getBag(), helper.blockPosition()) != null, "No real bench to place");
                            bench = true;
                            result = HelperCrafting.craft(h.getLevel(), helper.getBag(), step.item(), true);
                        }
                        h.assertTrue(result == HelperCrafting.Outcome.DONE, "Planned craft failed: " + step + " => " + result);
                    }
                }
                default -> throw new IllegalStateException("Unexpected step: " + step);
            }
        }
        h.assertTrue(helper.getBag().countItem(Items.WOODEN_AXE) == 1, "No axe made");
        h.assertTrue(helper.getBag().countItem(Items.CRAFTING_TABLE) == 0, "Placed table remained in bag");
        helper.discard();
        h.succeed();
    }

    private static void benchSpendsItem(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        h.assertTrue(HelperCrafting.placeBench(h.getLevel(), helper.getBag(), helper.blockPosition()) == null, "Created a free bench");
        helper.getBag().addItem(new ItemStack(Items.CRAFTING_TABLE));
        BlockPos bench = HelperCrafting.placeBench(h.getLevel(), helper.getBag(), helper.blockPosition());
        h.assertTrue(bench != null && helper.getBag().countItem(Items.CRAFTING_TABLE) == 0, "Table not consumed");
        helper.setPlacedBench(bench);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        helper.saveWithoutId(output);
        NpcHelperEntity restored = NpcHelperMod.HELPER.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(bench.equals(restored.placedBench()), "Own bench was forgotten on reload");
        helper.discard();
        h.succeed();
    }

    private static void nearbyBench(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        BlockPos bench = h.absolutePos(new BlockPos(32, 1, 20));
        h.getLevel().setBlockAndUpdate(bench, Blocks.CRAFTING_TABLE.defaultBlockState());
        h.assertTrue(bench.equals(HelperCrafting.findBench(helper)), "Bench twelve blocks away was not found");
        helper.getBag().addItem(new ItemStack(Items.OAK_PLANKS, 3));
        helper.getBag().addItem(new ItemStack(Items.STICK, 2));
        helper.queue().set(List.of(new HelperStep(HelperStep.CRAFT, "minecraft:wooden_axe", 1)));
        HelperWorkGoal goal = new HelperWorkGoal(helper);
        goal.tick();
        h.assertTrue(!helper.getNavigation().isDone(), "Did not walk to existing bench");
        h.assertTrue(helper.placedBench() == null, "Placed a new bench instead of walking");
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getBag().countItem(Items.WOODEN_AXE) == 1, "Did not reach and use existing bench");
            h.assertTrue(helper.placedBench() == null && h.getLevel().getBlockState(bench).is(Blocks.CRAFTING_TABLE), "Borrowed table was removed or duplicated");
            helper.discard();
        });
    }

    private static void woodRunsOut(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64), new HelperStep(HelperStep.STORE, "", 1)));
        new HelperWorkGoal(helper).tick();
        h.assertTrue(helper.queue().current() != null && helper.queue().current().type().equals(HelperStep.STORE), "No trees cleared storage step");
        helper.discard();
        h.succeed();
    }

    private static void eightLogsToStorage(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.IRON_AXE));
        for (int x = 22; x <= 23; x++) for (int y = 1; y <= 2; y++) for (int z = 20; z <= 21; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        }
        BlockPos chest = h.absolutePos(new BlockPos(32, 1, 20));
        h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64), new HelperStep(HelperStep.STORE, "", 1)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), chest).countItem(Items.OAK_LOG) == 8,
                    "Eight logs were not physically harvested, picked up and delivered after trees ran out");
            h.assertTrue(helper.getBag().countItem(Items.OAK_LOG) == 0, "Delivered logs remained in bag");
            helper.discard();
        });
    }

    private static void markedStorage(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.OAK_LOG, 8));
        BlockPos start = h.absolutePos(new BlockPos(4, 1, 20));
        helper.teleportTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        helper.setMode(HelperMode.STAY);
        BlockPos near = h.absolutePos(new BlockPos(5, 1, 20));
        BlockPos marked = h.absolutePos(new BlockPos(44, 1, 20));
        h.getLevel().setBlockAndUpdate(near, Blocks.CHEST.defaultBlockState());
        h.getLevel().setBlockAndUpdate(marked, Blocks.CHEST.defaultBlockState());
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), marked.north(), HelperSigns.Mark.STORE), "Cannot place storage marker");
        helper.queue().set(List.of(new HelperStep(HelperStep.STORE, "", 1)));
        HelperWorkGoal goal = new HelperWorkGoal(helper);
        goal.tick();
        h.assertTrue(HelperContainers.containerAt(h.getLevel(), near).isEmpty(), "Chose unmarked nearby chest");
        helper.teleportTo(marked.getX() - 1, marked.getY(), marked.getZ());
        helper.getNavigation().stop();
        goal.tick();
        h.assertTrue(HelperContainers.containerAt(h.getLevel(), marked).countItem(Items.OAK_LOG) == 8, "Harvest did not reach marked storage");
        helper.discard();
        h.succeed();
    }

    private static void treeRestart(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        BlockPos log = helper.blockPosition().east(2);
        h.getLevel().setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64)));
        HelperWorkGoal goal = new HelperWorkGoal(helper);
        goal.tick();
        var saved = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        goal.store(saved);
        h.assertTrue(saved.buildResult().contains("Foot"), "Tree state was not saved");
        HelperWorkGoal restored = new HelperWorkGoal(helper);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved.buildResult()));
        var after = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        restored.store(after);
        h.assertTrue(saved.buildResult().equals(after.buildResult()), "Tree phase changed during reload");
        restored.resetTask();
        var cleared = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        restored.store(cleared);
        h.assertTrue(cleared.isEmpty(), "Cancelled tree remained active");
        helper.discard();
        h.succeed();
    }

    private static void commandCancelsTree(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        h.getLevel().setBlockAndUpdate(helper.blockPosition().east(2), Blocks.OAK_LOG.defaultBlockState());
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64)));
        helper.setNoAi(false);
        h.runAfterDelay(4, () -> {
            helper.setMode(HelperMode.FOLLOW);
            h.assertTrue(helper.queue().isEmpty(), "Movement command retained work queue");
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
            helper.saveWithoutId(saved);
            h.assertTrue(saved.buildResult().getCompoundOrEmpty("TreeWork").isEmpty(), "Movement command retained tree context");
            helper.setProfession(HelperProfession.NONE);
            h.assertTrue(helper.queue().isEmpty(), "Old profession's work survived cancellation");
            helper.discard();
            h.succeed();
        });
    }

    private static String state(final GameTestHelper h, final NpcHelperEntity helper) {
        var saved = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        helper.supports().store(saved);
        return "pos=" + h.relativePos(helper.blockPosition()) + ",queue=" + helper.queue()
                + ",bag=" + helper.getBag().getItems() + ",hand=" + helper.getMainHandItem()
                + ",supports=" + helper.supports().count() + ",breaking=" + helper.breaking().target()
                + ",pathDone=" + helper.getNavigation().isDone() + ",onGround=" + helper.onGround()
                + ",steps=" + saved.buildResult();
    }

    private static void lumberjackBootstrap(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        for (int y = 1; y <= 7; y++) h.setBlock(new BlockPos(24, y, 20), Blocks.BIRCH_LOG);
        for (int x = 14; x <= 18; x++) for (int z = 16; z <= 20; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
        helper.setProfession(HelperProfession.LUMBERJACK);
        h.assertTrue(helper.queue().current().type().equals(HelperStep.PREPARE_GROVE), "Sign preparation must come first");
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            BlockPos sign = HelperSigns.nearest(h.getLevel(), helper.blockPosition(), 32, HelperSigns.Mark.GROVE);
            h.assertTrue(sign != null, "Bare lumberjack never crafted and placed a grove marker: " + state(h, helper));
            h.assertTrue(h.getLevel().getBlockState(sign).is(Blocks.BIRCH_SIGN), "Marker did not use the harvested wood species");
            h.assertTrue(!HelperToolkit.best(helper, HelperToolkit.Tool.AXE).isEmpty(), "Marker was placed before axe preparation");
            h.assertTrue(helper.getBag().countItem(Items.DIRT) >= HelperSupports.CARRY, "Bootstrap never reached physical soil collection");
            helper.discard();
        });
    }

    private static void existingGrove(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        BlockPos mark = helper.blockPosition().north(3);
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), mark, HelperSigns.Mark.GROVE), "Cannot prepare existing marker");
        helper.setProfession(HelperProfession.LUMBERJACK);
        HelperWorkGoal goal = new HelperWorkGoal(helper);
        goal.tick();
        h.assertTrue(helper.queue().current().type().equals(HelperStep.PREPARE_AXE), "Existing grove did not skip sign and bench preparation");
        h.assertTrue(helper.getBag().countItem(Items.CRAFTING_TABLE) == 0, "Existing grove created a table");
        helper.discard();
        h.succeed();
    }

    private static void signFromMaterials(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.BIRCH_PLANKS, 16));
        helper.getBag().addItem(new ItemStack(Items.IRON_AXE));
        helper.setProfession(HelperProfession.LUMBERJACK);
        new HelperWorkGoal(helper).tick();
        h.assertTrue(!helper.queue().current().type().equals(HelperStep.BOOTSTRAP_WOOD), "Ignored carried materials and tried to gather five logs");
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getBag().countItem(Items.BIRCH_SIGN) == 3, "Did not craft signs from carried birch planks");
            h.assertTrue(helper.placedBench() != null && h.getLevel().getBlockState(helper.placedBench()).is(Blocks.CRAFTING_TABLE), "No real table made for the sign recipe");
            helper.discard();
        });
    }

    private static void soilUnderGrass(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        for (int x = 21; x <= 24; x++) {
            h.setBlock(new BlockPos(x, 1, 22), Blocks.GRASS_BLOCK);
            h.setBlock(new BlockPos(x, 2, 22), Blocks.SHORT_GRASS);
        }
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_DIRT, "", 4)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getBag().countItem(Items.DIRT) == 4, "Did not dig and collect soil from under replaceable grass without a shovel: " + state(h, helper));
            h.assertTrue(helper.queue().isEmpty(), "Soil collection did not finish");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(20, 0, 20))).is(Blocks.STONE), "Dug the ground under own feet");
            helper.discard();
        });
    }

    private static void tallTreeSteps(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.IRON_AXE));
        helper.getBag().addItem(new ItemStack(Items.DIRT, HelperSupports.CARRY));
        for (int y = 1; y <= 9; y++) h.setBlock(new BlockPos(23, y, 20), Blocks.OAK_LOG);
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(23, 9, 20))).isAir(), "Did not climb soil steps to fell the top log: " + state(h, helper));
            h.assertTrue(!helper.supports().any(), "Own steps were not recovered");
            h.assertTrue(helper.getBag().countItem(Items.OAK_LOG) == 9, "Tall tree harvest was not physically collected");
            h.assertTrue(helper.getBag().countItem(Items.DIRT) == HelperSupports.CARRY, "Own soil was lost during the climb");
            helper.discard();
        });
    }

    private static void actionsMenuSync(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        var viewer = h.makeMockServerPlayerInLevel();
        HelperMenu menu = new HelperMenu(7, viewer.getInventory(), helper);
        HelperStep work = new HelperStep(HelperStep.GATHER_WOOD, "", 64);
        helper.queue().set(List.of(work, new HelperStep(HelperStep.STORE, "", 1)));
        var initial = menu.actionSnapshot();
        work.progress();
        helper.getBag().addItem(new ItemStack(Items.OAK_LOG));
        h.assertTrue(initial.steps().getFirst().done() == 0, "Already sent snapshot mutated with live work");
        var update = menu.actionSnapshot();
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            dev.npchelper.net.HelperActions.encode(update, buffer);
            var decoded = dev.npchelper.net.HelperActions.decode(buffer);
            h.assertTrue(decoded.equals(update), "Queue packet lost step order or progress");
            menu.receiveActions(decoded);
        } finally { buffer.release(); }
        h.assertTrue(menu.actions().getFirst().done() == 1 && menu.actions().size() == 2, "Menu did not receive updated progress");
        menu.receiveActions(new dev.npchelper.net.HelperActions(8, helper.getId(), "stale", List.of()));
        menu.receiveActions(new dev.npchelper.net.HelperActions(7, helper.getId() + 1, "other NPC", List.of()));
        h.assertTrue(menu.actions().size() == 2, "An unrelated menu packet replaced this NPC's queue");
        helper.queue().prepend(List.of(new HelperStep(HelperStep.CRAFT, "minecraft:stick", 1)));
        menu.receiveActions(menu.actionSnapshot());
        h.assertTrue(menu.actions().getFirst().type().equals(HelperStep.CRAFT) && menu.actions().size() == 3, "Preparation inserted at runtime was not shown");
        menu.setActionsTab(true);
        h.assertTrue(menu.slots.stream().noneMatch(slot -> slot.isActive()), "Inventory slots remain clickable under the actions page");
        menu.setActionsTab(false);
        h.assertTrue(menu.slots.stream().allMatch(slot -> slot.isActive()), "Inventory did not return after leaving actions page");
        helper.setProfession(HelperProfession.NONE);
        menu.receiveActions(menu.actionSnapshot());
        h.assertTrue(menu.actions().isEmpty(), "Menu retained cancelled work");
        viewer.discard();
        helper.discard();
        h.succeed();
    }

    private static void professionCancelsRunningWork(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        var owner = h.makeMockServerPlayerInLevel();
        helper.setOwner(owner);
        HelperMenu menu = new HelperMenu(8, owner.getInventory(), helper);
        BlockPos log = helper.blockPosition().east(2);
        h.getLevel().setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64), new HelperStep(HelperStep.STORE, "", 1)));
        helper.setNoAi(false);
        h.runAfterDelay(8, () -> {
            h.assertTrue(helper.breaking().target() != null, "Test did not reach active chopping");
            h.assertTrue(menu.clickMenuButton(owner, HelperMenu.BUTTON_PROFESSION + HelperProfession.GUARD.ordinal()), "Profession button was rejected");
            h.assertTrue(helper.queue().isEmpty() && helper.breaking().target() == null, "Switching profession retained chopping");
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
            helper.saveWithoutId(saved);
            h.assertTrue(saved.buildResult().getCompoundOrEmpty("TreeWork").isEmpty(), "Old tree context survived profession switch");
            h.runAfterDelay(30, () -> {
                h.assertTrue(h.getLevel().getBlockState(log).is(Blocks.OAK_LOG), "Old chopping resumed after profession switch");
                helper.setProfession(HelperProfession.LUMBERJACK);
                var actions = menu.actionSnapshot().steps();
                h.assertTrue(actions.getFirst().type().equals(HelperStep.PREPARE_GROVE), "New lumberjack chain did not start from preparation");
                h.assertTrue(actions.stream().filter(step -> step.type().equals(HelperStep.GATHER_WOOD)).count() == 1, "Old harvesting step leaked into the new chain");
                helper.discard();
                owner.discard();
                h.succeed();
            });
        });
    }

    private static void pickupFullBag(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        for (int slot = 0; slot < helper.getBag().getContainerSize(); slot++) helper.getBag().setItem(slot, new ItemStack(Items.COAL, 64));
        helper.pickup().drop(helper.blockPosition().east(3), List.of(new ItemStack(Items.OAK_LOG)));
        h.runAfterDelay(20, () -> {
            h.assertTrue(!helper.pickup().canChase() && helper.pickup().waitingForSpace(), "Full bag still chases items forever");
            helper.getBag().setItem(0, ItemStack.EMPTY);
            h.assertTrue(helper.pickup().canChase(), "Freeing bag space did not restore collection");
            helper.discard();
            h.succeed();
        });
    }

    private static void pickupUnreachable(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        BlockPos perch = helper.blockPosition().east(5).above(7);
        h.getLevel().setBlockAndUpdate(perch, Blocks.STONE.defaultBlockState());
        helper.pickup().drop(perch.above(), List.of(new ItemStack(Items.OAK_LOG)));
        h.runAfterDelay(50, () -> {
            h.assertTrue(!helper.pickup().canChase() && helper.pickup().hasDeferredDrops(), "Unreachable harvest still monopolizes movement");
            h.assertTrue(!helper.pickup().isChasing(), "Deferred drop remains the active target");
            helper.discard();
            h.succeed();
        });
    }

    private static BlockPos buildPassage(final GameTestHelper h, final net.minecraft.world.level.block.Block door, final boolean gate, final boolean open) {
        for (int z = 0; z < 48; z++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(24, y, z), Blocks.STONE);
        BlockPos pos = h.absolutePos(new BlockPos(24, 1, 20));
        if (gate) {
            h.getLevel().setBlockAndUpdate(pos, door.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, net.minecraft.core.Direction.EAST)
                    .setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN, open));
            h.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        } else {
            var state = door.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST)
                    .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, open);
            h.getLevel().setBlockAndUpdate(pos, state.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
            h.getLevel().setBlockAndUpdate(pos.above(), state.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
        return pos;
    }

    private static void passage(final GameTestHelper h, final boolean gate, final boolean open) {
        NpcHelperEntity helper = helper(h);
        BlockPos entry = buildPassage(h, gate ? Blocks.OAK_FENCE_GATE : Blocks.OAK_DOOR, gate, open);
        BlockPos target = h.absolutePos(new BlockPos(29, 1, 20));
        h.assertTrue(helper.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0, 1.0D), "No route through usable door or gate");
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getX() > entry.getX() + 1.6D, "Did not pass through entrance");
            var state = h.getLevel().getBlockState(entry);
            boolean stillOpen = gate ? state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN) : state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
            h.assertTrue(!stillOpen, "Did not close the passage behind itself");
            helper.discard();
        });
    }

    private static void ironDoor(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        BlockPos entry = buildPassage(h, Blocks.IRON_DOOR, false, false);
        BlockPos target = h.absolutePos(new BlockPos(29, 1, 20));
        var path = helper.getNavigation().createPath(target, 0);
        h.assertTrue(path == null || !path.canReach(), "Closed iron door was considered manually passable");
        helper.passages().tick();
        h.assertTrue(!h.getLevel().getBlockState(entry).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "Manually opened an iron door");
        helper.discard();
        h.succeed();
    }

    private static void house(final GameTestHelper h) {
        for (int x = 26; x <= 32; x++) for (int z = 17; z <= 23; z++) {
            h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            if (x == 26 || x == 32 || z == 17 || z == 23) {
                for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST);
        h.setBlock(new BlockPos(26, 1, 20), door);
        h.setBlock(new BlockPos(26, 2, 20), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    private static void nightHome(final GameTestHelper h, final boolean marked) {
        NpcHelperEntity helper = helper(h);
        house(h);
        if (marked) {
            helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
            h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), h.absolutePos(new BlockPos(30, 1, 20)), HelperSigns.Mark.HOME), "Cannot mark home");
        }
        h.assertTrue(dev.npchelper.entity.HelperNightGoal.night(helper), "Night test environment was not applied");
        h.assertTrue(!dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), helper.blockPosition()), "Open street was mistaken for indoors");
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getX() > h.absolutePos(new BlockPos(27, 1, 20)).getX(), "Did not enter home at night: " + state(h, helper));
            h.assertTrue(dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), helper.blockPosition()), "Chose the street side of the door");
            h.assertTrue(!h.getBlockState(new BlockPos(26, 1, 20)).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "Home door remained open: " + state(h, helper));
            helper.discard();
        });
    }

    private static void nightScaffoldCleanup(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.DIRT, 2));
        h.assertTrue(helper.supports().step(h.getLevel(), helper.blockPosition().east(2).above(5)), "Cannot prepare leftover scaffold");
        helper.queue().clear();
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(!helper.supports().any(), "Night travel stranded a cancelled scaffold");
            h.assertTrue(helper.getBag().countItem(Items.DIRT) == 2, "Scaffold soil was not recovered before resting");
            h.assertTrue(helper.nightGoal().active(), "Night rest did not take over after cleanup");
            helper.discard();
        });
    }

    private static void nightDistantHome(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        BlockPos start = h.absolutePos(new BlockPos(4, 1, 20));
        helper.teleportTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        helper.setMode(HelperMode.STAY);
        for (int x = 40; x <= 46; x++) for (int z = 17; z <= 23; z++) {
            h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            if (x == 40 || x == 46 || z == 17 || z == 23) {
                for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST);
        h.setBlock(new BlockPos(40, 1, 20), door);
        h.setBlock(new BlockPos(40, 2, 20), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), h.absolutePos(new BlockPos(44, 1, 20)), HelperSigns.Mark.HOME), "Cannot mark distant home");
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getX() > h.absolutePos(new BlockPos(41, 1, 20)).getX(), "Did not reach a home beyond the ordinary 32-block path range");
            h.assertTrue(!h.getBlockState(new BlockPos(40, 1, 20)).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "Distant home was not closed");
            helper.discard();
        });
    }

    private static void shelterClassification(final GameTestHelper h) {
        for (int x = 10; x <= 14; x++) for (int z = 10; z <= 14; z++) {
            h.setBlock(new BlockPos(x, 4, z), Blocks.OAK_LEAVES);
            if (x == 10 || x == 14 || z == 10 || z == 14) {
                for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        BlockPos inside = h.absolutePos(new BlockPos(12, 1, 12));
        h.assertTrue(!dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), inside), "Leaves were accepted as a solid shelter roof");
        for (int x = 10; x <= 14; x++) for (int z = 10; z <= 14; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
        h.assertTrue(dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), inside), "Roofed enclosed room was rejected");
        for (int x = 10; x <= 14; x++) for (int y = 1; y <= 3; y++) {
            h.setBlock(new BlockPos(x, y, 10), Blocks.AIR);
            h.setBlock(new BlockPos(x, y, 14), Blocks.AIR);
        }
        h.assertTrue(!dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), inside), "Open canopy was mistaken for an enclosed room");
        h.succeed();
    }

    private static void expeditionAtNight(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        house(h);
        h.assertTrue(helper.nightGoal().canUse(), "Ordinary mode did not enable night shelter");
        var owner = h.makeMockServerPlayerInLevel();
        helper.setOwner(owner);
        HelperMenu menu = new HelperMenu(12, owner.getInventory(), helper);
        h.assertTrue(menu.clickMenuButton(owner, HelperMenu.BUTTON_EXPEDITION), "Expedition menu toggle was rejected");
        h.assertTrue(helper.isExpedition(), "Menu did not enable expedition mode");
        h.assertTrue(!helper.nightGoal().canUse(), "Expedition mode still sends NPC home at night");
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        helper.saveWithoutId(saved);
        var restored = NpcHelperMod.HELPER.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved.buildResult()));
        h.assertTrue(restored.isExpedition(), "Expedition mode did not survive reload");
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64)));
        h.assertTrue(new HelperWorkGoal(helper).canUse(), "Expedition mode blocked night work");
        var before = helper.queue().snapshot();
        h.assertTrue(menu.clickMenuButton(owner, HelperMenu.BUTTON_EXPEDITION), "Ordinary mode toggle was rejected");
        h.assertTrue(helper.queue().snapshot().equals(before), "Travel mode toggle discarded the work queue");
        h.assertTrue(helper.nightGoal().canUse(), "Ordinary night behavior did not return");
        owner.discard();
        helper.discard();
        h.succeed();
    }

    private static void nightWanderInside(final GameTestHelper h, final int radius) {
        NpcHelperEntity helper = helper(h);
        house(h);
        BlockPos start = h.absolutePos(new BlockPos(29, 1, 20));
        helper.teleportTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        helper.setMode(HelperMode.STAY);
        helper.setRoamRadius(radius);
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), start.east(), HelperSigns.Mark.HOME), "Cannot mark test shelter");
        net.minecraft.world.phys.Vec3 initial = helper.position();
        java.util.concurrent.atomic.AtomicBoolean moved = new java.util.concurrent.atomic.AtomicBoolean();
        helper.setNoAi(false);
        h.onEachTick(() -> {
            if (helper.position().distanceToSqr(initial) > 1) moved.set(true);
            h.assertTrue(dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), helper.blockPosition()), "Night wandering left the room");
            h.assertTrue(helper.position().distanceToSqr(initial) <= radius * radius, "Night wandering exceeded the selected radius");
        });
        h.runAfterDelay(500, () -> {
            h.assertTrue(moved.get(), "Night mode suppressed random movement after arrival");
            h.assertTrue(!h.getBlockState(new BlockPos(26, 1, 20)).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "Night wandering opened the outside door");
            helper.discard();
            h.succeed();
        });
    }

    private static void nightWanderWithoutHome(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        // This environment runs after the other night scenarios; remove their surviving markers if nearby.
        for (BlockPos sign : HelperSigns.around(h.getLevel(), helper.blockPosition(), 64, HelperSigns.Mark.HOME)) {
            h.getLevel().removeBlock(sign, false);
        }
        helper.setRoamRadius(4);
        net.minecraft.world.phys.Vec3 initial = helper.position();
        java.util.concurrent.atomic.AtomicBoolean moved = new java.util.concurrent.atomic.AtomicBoolean();
        helper.setNoAi(false);
        h.onEachTick(() -> {
            if (helper.position().distanceToSqr(initial) > 1) moved.set(true);
            h.assertTrue(helper.position().distanceToSqr(initial) <= 16, "Unhoused NPC left its selected night radius");
        });
        h.runAfterDelay(500, () -> {
            h.assertTrue(moved.get(), "Searching for a home froze ordinary random movement");
            helper.discard();
            h.succeed();
        });
    }

    private static void markerInGrass(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        for (int x = 12; x <= 29; x++) for (int z = 14; z <= 28; z++) {
            h.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            h.setBlock(new BlockPos(x, 2, z), Blocks.SHORT_GRASS);
        }
        BlockPos start = h.absolutePos(new BlockPos(20, 2, 20));
        helper.teleportTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        helper.setMode(HelperMode.STAY);
        for (int y = 2; y <= 5; y++) h.setBlock(new BlockPos(24, y, 20), Blocks.BIRCH_LOG);
        helper.getBag().addItem(new ItemStack(Items.BIRCH_SIGN, 3));
        helper.queue().set(List.of(new HelperStep(HelperStep.MARK_GROVE, "", 1)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            BlockPos sign = HelperSigns.nearest(h.getLevel(), helper.blockPosition(), 16, HelperSigns.Mark.GROVE);
            h.assertTrue(sign != null, "Marker in inventory remained stuck over dense grass: " + state(h, helper));
            h.assertTrue(h.getLevel().getBlockState(sign).is(Blocks.BIRCH_SIGN), "Wrong sign species placed");
            h.assertTrue(helper.getBag().countItem(Items.BIRCH_SIGN) == 2, "Sign was not spent exactly once");
            helper.discard();
        });
    }

    private static void soilStaircaseExit(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        for (int x = 13; x <= 27; x++) for (int z = 13; z <= 27; z++) for (int y = 1; y <= 5; y++) {
            h.setBlock(new BlockPos(x, y, z), y == 5 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        }
        BlockPos exit = h.absolutePos(new BlockPos(20, 6, 20));
        helper.teleportTo(exit.getX() + 0.5D, exit.getY(), exit.getZ() + 0.5D);
        helper.setMode(HelperMode.STAY);
        helper.getBag().addItem(new ItemStack(Items.IRON_SHOVEL));
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_DIRT, "", 16)));
        helper.setNoAi(false);
        h.onEachTick(() -> {
            h.assertTrue(h.getLevel().getBlockState(exit.below()).is(Blocks.GRASS_BLOCK), "Mined the initial support beneath itself");
            if (helper.onGround()) {
                var path = dev.npchelper.entity.HelperNavigation.probe(helper, exit, 0);
                h.assertTrue(path != null && path.canReach(), "Excavation destroyed the stair route back out: " + state(h, helper));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(helper.getBag().countItem(Items.DIRT) >= 16, "Did not gather the soil quota");
            h.assertTrue(helper.queue().isEmpty(), "Soil step did not finish after collection");
            h.assertTrue(helper.getY() >= exit.getY() - 0.1D, "Finished the soil step while trapped in a pit");
            helper.discard();
        });
    }

    private static void foodHealing(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.setHealth(8);
        helper.getBag().addItem(new ItemStack(Items.MUSHROOM_STEW));
        helper.eating().tick();
        h.assertTrue(helper.getHealth() == 14, "Food nutrition did not restore health");
        h.assertTrue(helper.getBag().countItem(Items.MUSHROOM_STEW) == 0 && helper.getBag().countItem(Items.BOWL) == 1, "Food or bowl accounting failed");
        helper.getBag().addItem(new ItemStack(Items.BREAD, 2));
        helper.eating().tick();
        h.assertTrue(helper.getBag().countItem(Items.BREAD) == 2, "Ate again without the cooldown");
        h.runAfterDelay(100, () -> {
            helper.setHealth(helper.getMaxHealth());
            helper.eating().tick();
            h.assertTrue(helper.getBag().countItem(Items.BREAD) == 2, "Healthy NPC wasted food");
            helper.discard();
            h.succeed();
        });
    }

    private static void playerFeeding(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        var player = h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 2));
        helper.setHealth(17);
        helper.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(helper.getHealth() == helper.getMaxHealth(), "Feeding from hand did not heal or exceeded max health");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "Feeding did not consume one bread");
        helper.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(player.getMainHandItem().getCount() == 1, "Fed a healthy NPC twice");
        player.discard();
        helper.discard();
        h.succeed();
    }

    private static void inventoryHarvestCount(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.OAK_LOG, 64));
        helper.getBag().addItem(new ItemStack(Items.BIRCH_LOG, 8));
        BlockPos tree = helper.blockPosition().east(2);
        h.getLevel().setBlockAndUpdate(tree, Blocks.OAK_LOG.defaultBlockState());
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64), new HelperStep(HelperStep.STORE, "", 1)));
        var viewer = h.makeMockServerPlayerInLevel();
        HelperMenu menu = new HelperMenu(23, viewer.getInventory(), helper);
        h.assertTrue(menu.actionSnapshot().steps().getFirst().done() == 72, "Menu ignored existing or mixed-species logs");
        new HelperWorkGoal(helper).tick();
        h.assertTrue(helper.queue().current().type().equals(HelperStep.STORE), "Started chopping despite sufficient inventory");
        h.assertTrue(h.getLevel().getBlockState(tree).is(Blocks.OAK_LOG), "Broke another block after inventory quota was met");
        helper.getBag().removeItem(0, 10);
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64)));
        h.assertTrue(menu.actionSnapshot().steps().getFirst().done() == 62, "Removing inventory did not update progress");
        viewer.discard();
        helper.discard();
        h.succeed();
    }

    private static void plannedInventoryTarget(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.OAK_LOG));
        var plan = HelperObtain.plan(h.getLevel(), List.of(new ItemStack(Items.OAK_LOG)), true, Items.WOODEN_AXE, 2);
        h.assertTrue(!plan.refused(), "Cannot plan from partially supplied inventory");
        for (HelperStep step : plan.steps()) {
            if (step.type().equals(HelperStep.GATHER_WOOD)) {
                int missing = Math.max(0, step.count() - helper.carriedLogs());
                if (missing > 0) helper.getBag().addItem(new ItemStack(Items.OAK_LOG, missing));
            } else if (step.type().equals(HelperStep.CRAFT)) {
                for (int count = 0; count < step.count(); count++) {
                    h.assertTrue(HelperCrafting.craft(h.getLevel(), helper.getBag(), step.item(), true) == HelperCrafting.Outcome.DONE,
                            "Inventory-based gather target under-supplied a later craft: " + step);
                }
            }
        }
        h.assertTrue(helper.getBag().countItem(Items.WOODEN_AXE) == 2, "Did not make both planned tools");
        helper.discard();
        h.succeed();
    }

    private static void movementAndMenu(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(helper.getX() + 20, helper.getY(), helper.getZ());
        helper.setOwner(owner);
        h.assertTrue(helper.getAnchor().equals(helper.blockPosition()), "New helper anchored at world origin");
        var planned = HelperTrades.planFor(helper, HelperProfession.LUMBERJACK);
        h.assertTrue(planned.stream().filter(step -> step.type().equals(HelperStep.CRAFT)
                && step.item().equals("minecraft:crafting_table")).count() <= 1, "Tool preparation planned duplicate tables");
        HelperMenu menu = new HelperMenu(1, owner.getInventory(), helper);
        for (int radius : NpcHelperEntity.ROAM_RADII) {
            helper.setRoamRadius(radius);
            for (HelperMode mode : HelperMode.values()) {
                helper.setMode(mode);
                h.assertTrue(helper.workRadius() == radius, "Radius depends on movement mode");
            }
        }
        helper.setRoamRadius(4);
        helper.setMode(HelperMode.FOLLOW);
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64)));
        h.assertTrue(helper.mustReturnToOwner() && !new HelperWorkGoal(helper).canUse(), "Work outranks following distant owner");
        h.assertTrue(menu.clickMenuButton(owner, HelperMenu.BUTTON_PROFESSION + HelperProfession.LUMBERJACK.ordinal()), "Profession selection failed");
        h.assertTrue(helper.getProfession() == HelperProfession.LUMBERJACK, "Wrong profession selected");
        h.assertTrue(menu.clickMenuButton(owner, HelperMenu.BUTTON_RADIUS + 2) && helper.getRoamRadius() == 16, "Direct radius selection failed");
        h.assertTrue(!menu.clickMenuButton(owner, 99), "Accepted invalid choice");
        helper.discard();
        owner.discard();
        h.succeed();
    }
}
