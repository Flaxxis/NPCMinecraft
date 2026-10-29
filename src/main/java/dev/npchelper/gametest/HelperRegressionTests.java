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
        tests.register("second_tall_tree", () -> HelperRegressionTests::secondTallTree);
        tests.register("spruce_low_leaves", () -> HelperRegressionTests::spruceLowLeaves);
        tests.register("keeper_sweeps_ground", () -> HelperRegressionTests::keeperSweepsGround);
        tests.register("warehouse_any_free_slot", () -> HelperRegressionTests::warehouseAnyFreeSlot);
        tests.register("keeper_unloads_amid_litter", () -> HelperRegressionTests::keeperUnloadsAmidLitter);
        tests.register("keeper_smelts_ore", () -> HelperRegressionTests::keeperSmeltsOre);
        tests.register("keeper_collects_furnace_output", () -> HelperRegressionTests::keeperCollectsFurnaceOutput);
        tests.register("keeper_splits_across_furnaces", () -> HelperRegressionTests::keeperSplitsAcrossFurnaces);
        tests.register("keeper_without_furnace", () -> HelperRegressionTests::keeperWithoutFurnace);
        tests.register("keeper_night_duty", () -> HelperRegressionTests::keeperNightDuty);
        tests.register("keeper_night_indoors", () -> HelperRegressionTests::keeperNightIndoors);
        tests.register("chest_lid_opens", () -> HelperRegressionTests::chestLidOpens);
        tests.register("guard_sword_without_iron", () -> HelperRegressionTests::guardSwordWithoutIron);
        tests.register("guard_shield_from_iron", () -> HelperRegressionTests::guardShieldFromIron);
        tests.register("guard_patrols", () -> HelperRegressionTests::guardPatrols);
        tests.register("guard_watches_far", () -> HelperRegressionTests::guardWatchesFar);
        tests.register("guard_defends_helper", () -> HelperRegressionTests::guardDefendsHelper);
        tests.register("keeper_furnace_behind_door", () -> HelperRegressionTests::keeperFurnaceBehindDoor);
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
        tests.register("common_profession_kit", () -> HelperRegressionTests::commonProfessionKit);
        tests.register("warehouse_resource_priority", () -> HelperRegressionTests::warehouseResourcePriority);
        tests.register("warehouse_crafting_materials", () -> HelperRegressionTests::warehouseCraftingMaterials);
        tests.register("keeper_marks_storage", () -> HelperRegressionTests::keeperMarksStorage);
        tests.register("keeper_haul_and_sort", () -> HelperRegressionTests::keeperHaulAndSort);
        tests.register("keeper_partial_stacks", () -> HelperRegressionTests::keeperPartialStacks);
        tests.register("keeper_full_storage", () -> HelperRegressionTests::keeperFullStorage);
        tests.register("cargo_save_and_cancel", () -> HelperRegressionTests::cargoSaveAndCancel);
        tests.register("storage_stack_limits", () -> HelperRegressionTests::storageStackLimits);
        tests.register("double_chest_inventory", () -> HelperRegressionTests::doubleChestInventory);
        tests.register("warehouse_soil_priority", () -> HelperRegressionTests::warehouseSoilPriority);
        tests.register("ready_tool_priority", () -> HelperRegressionTests::readyToolPriority);
        tests.register("sequential_profession_tools", () -> HelperRegressionTests::sequentialProfessionTools);
        tests.register("night_blocked_home_rescue", () -> HelperRegressionTests::nightBlockedHomeRescue);
        tests.register("night_water_pit_rescue", () -> HelperRegressionTests::nightWaterPitRescue);
        tests.register("expedition_no_rescue", () -> HelperRegressionTests::expeditionNoRescue);
        tests.register("unsafe_rescue_positions", () -> HelperRegressionTests::unsafeRescuePositions);
        tests.register("inventory_materials_before_stock", () -> HelperRegressionTests::inventoryMaterialsBeforeStock);
        tests.register("unreachable_warehouse_fallback", () -> HelperRegressionTests::unreachableWarehouseFallback);
        tests.register("keeper_across_full_radius", () -> HelperRegressionTests::keeperAcrossFullRadius);
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
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), chest.north(), HelperSigns.Mark.STORE), "Cannot mark this test's destination");
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
        h.assertTrue(helper.queue().current().type().equals(HelperStep.PREPARE_KIT), "Common supplies must come first");
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
        helper.getBag().addItem(new ItemStack(Items.DIRT, HelperSupports.CARRY));
        helper.getBag().addItem(new ItemStack(Items.CRAFTING_TABLE));
        helper.setProfession(HelperProfession.LUMBERJACK);
        HelperWorkGoal goal = new HelperWorkGoal(helper);
        goal.tick();
        goal.tick();
        h.assertTrue(helper.queue().current().type().equals(HelperStep.PREPARE_AXE), "Existing grove did not skip sign and bench preparation");
        h.assertTrue(helper.getBag().countItem(Items.CRAFTING_TABLE) == 1 && helper.placedBench() == null, "Existing grove created an extra table");
        helper.discard();
        h.succeed();
    }

    private static void signFromMaterials(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.DIRT, HelperSupports.CARRY));
        helper.getBag().addItem(new ItemStack(Items.BIRCH_PLANKS, 16));
        helper.getBag().addItem(new ItemStack(Items.IRON_AXE));
        helper.setProfession(HelperProfession.LUMBERJACK);
        new HelperWorkGoal(helper).tick();
        h.assertTrue(!helper.queue().current().type().equals(HelperStep.BOOTSTRAP_WOOD), "Ignored carried materials and tried to gather five logs");
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getBag().countItem(Items.BIRCH_SIGN) == 3, "Did not craft signs from carried birch planks");
            h.assertTrue(helper.getBag().countItem(Items.CRAFTING_TABLE) == 1 || (helper.placedBench() != null && h.getLevel().getBlockState(helper.placedBench()).is(Blocks.CRAFTING_TABLE)), "Own table was lost during sign preparation");
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

    /**
     * Two tall trunks, one after the other: the second climb must start from where the helper stands, not from the top of
     * the first pillar it already took down (game 28.10: it hopped on the spot under the second tree and never rose).
     */
    private static void secondTallTree(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.IRON_AXE));
        helper.getBag().addItem(new ItemStack(Items.DIRT, HelperSupports.CARRY));
        for (int y = 1; y <= 8; y++) {
            h.setBlock(new BlockPos(23, y, 20), Blocks.OAK_LOG);
            h.setBlock(new BlockPos(17, y, 20), Blocks.OAK_LOG);
        }
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(23, 8, 20))).isAir()
                    && h.getLevel().getBlockState(h.absolutePos(new BlockPos(17, 8, 20))).isAir(),
                    "Did not climb to the top of both trunks: " + state(h, helper));
            h.assertTrue(!helper.supports().any(), "Own pillar was not taken down");
            h.assertTrue(helper.getBag().countItem(Items.OAK_LOG) == 16, "Both trunks were not physically collected");
            h.assertTrue(helper.getBag().countItem(Items.DIRT) == HelperSupports.CARRY, "Own soil was lost between the climbs");
            helper.discard();
        });
    }

    /**
     * A spruce whose skirt of leaves hangs down to head height all round the trunk: the helper must clear a few leaves to
     * get in to the trunk, and no more than that (game 29.10: it dithered under the low leaves of a spruce).
     */
    private static void spruceLowLeaves(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.IRON_AXE));
        helper.getBag().addItem(new ItemStack(Items.DIRT, HelperSupports.CARRY));
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1);
        int placed = 0;
        for (int y = 2; y <= 10; y++) {
            int radius = y == 10 ? 0 : y % 2 == 0 ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (y < 10 && dx == 0 && dz == 0) continue;
                h.setBlock(new BlockPos(23 + dx, y, 20 + dz), leaves);
                placed++;
            }
        }
        for (int y = 1; y <= 9; y++) h.setBlock(new BlockPos(23, y, 20), Blocks.SPRUCE_LOG);
        int total = placed;
        helper.queue().set(List.of(new HelperStep(HelperStep.GATHER_WOOD, "", 64)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getBag().countItem(Items.SPRUCE_LOG) == 9, "Did not get the whole spruce in under its leaves: " + state(h, helper) + ",cut=" + helper.leavesCut() + ",problem=" + helper.workProblem());
            h.assertTrue(helper.leavesCut() >= 1 && helper.leavesCut() <= 5, "Leaves cut for one tree: " + helper.leavesCut() + " of " + total);
            h.assertTrue(!helper.supports().any(), "Own pillar was not taken down");
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
            h.assertFalse(menu.clickMenuButton(owner, HelperMenu.BUTTON_PROFESSION + HelperProfession.MINER.ordinal()),
                    "A trade that is not built yet was accepted");
            h.assertTrue(helper.getProfession() == HelperProfession.NONE && helper.breaking().target() != null,
                    "A refused trade still disturbed the running work");
            h.assertTrue(menu.clickMenuButton(owner, HelperMenu.BUTTON_PROFESSION + HelperProfession.KEEPER.ordinal()), "Profession button was rejected");
            h.assertTrue(helper.queue().current().type().equals(HelperStep.PREPARE_KIT) && helper.breaking().target() == null, "Switching profession retained chopping");
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
            helper.saveWithoutId(saved);
            h.assertTrue(saved.buildResult().getCompoundOrEmpty("TreeWork").isEmpty(), "Old tree context survived profession switch");
            h.runAfterDelay(30, () -> {
                h.assertTrue(h.getLevel().getBlockState(log).is(Blocks.OAK_LOG), "Old chopping resumed after profession switch");
                helper.setProfession(HelperProfession.LUMBERJACK);
                var actions = menu.actionSnapshot().steps();
                h.assertTrue(actions.getFirst().type().equals(HelperStep.PREPARE_KIT), "New lumberjack chain did not start from common preparation");
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

    private static void equipKit(final NpcHelperEntity helper) {
        helper.getBag().addItem(new ItemStack(Items.DIRT, 10));
        helper.getBag().addItem(new ItemStack(Items.CRAFTING_TABLE));
    }

    private static BlockPos chest(final GameTestHelper h, final int x, final int z) {
        BlockPos pos = h.absolutePos(new BlockPos(x, 1, z));
        h.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        return pos;
    }

    private static BlockPos warehouse(final GameTestHelper h, final NpcHelperEntity helper) {
        BlockPos primary = chest(h, 5, 20);
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), h.absolutePos(new BlockPos(4, 1, 20)), HelperSigns.Mark.STORE), "Cannot mark warehouse");
        return primary;
    }

    private static void commonProfessionKit(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        for (HelperProfession profession : HelperProfession.values()) {
            if (profession == HelperProfession.NONE) continue;
            var plan = HelperTrades.planFor(helper, profession);
            h.assertTrue(plan.getFirst().type().equals(HelperStep.PREPARE_KIT), "Profession skipped common supply check: " + profession);
        }
        helper.queue().set(List.of(new HelperStep(HelperStep.PREPARE_KIT, "", 1)));
        new HelperWorkGoal(helper).tick();
        h.assertTrue(helper.queue().isEmpty() && helper.getBag().countItem(Items.DIRT) == 10
                && helper.getBag().countItem(Items.CRAFTING_TABLE) == 1, "Existing supplies were unnecessarily crafted or consumed");
        helper.getBag().addItem(new ItemStack(Items.DIAMOND_AXE));
        h.assertTrue(HelperTrades.prepareAxe(helper).getFirst().item().equals("minecraft:diamond_axe"), "Ignored carried high-tier tool");
        helper.getBag().clearContent();
        helper.getBag().addItem(new ItemStack(Items.COPPER_AXE));
        h.assertTrue(HelperTrades.prepareAxe(helper).getFirst().item().equals("minecraft:copper_axe"), "Ignored carried copper tool from this Minecraft version");
        helper.discard();
        h.succeed();
    }

    private static void warehouseResourcePriority(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.setRoamRadius(32);
        helper.getBag().addItem(new ItemStack(Items.DIRT, 10));
        BlockPos store = warehouse(h, helper);
        BlockPos nearby = chest(h, 23, 20);
        HelperContainers.containerAt(h.getLevel(), store).setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        HelperContainers.containerAt(h.getLevel(), nearby).setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        helper.queue().set(List.of(new HelperStep(HelperStep.PREPARE_KIT, "", 1)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getBag().countItem(Items.CRAFTING_TABLE) == 1, "Did not obtain required table");
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), store).countItem(Items.CRAFTING_TABLE) == 0, "Ignored marked warehouse");
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), nearby).countItem(Items.CRAFTING_TABLE) == 1, "Took nearer unmarked supplies before warehouse");
            helper.discard();
        });
    }

    private static void warehouseCraftingMaterials(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(32);
        BlockPos store = warehouse(h, helper);
        BlockPos nearby = chest(h, 23, 20);
        var stock = HelperContainers.containerAt(h.getLevel(), store);
        stock.setItem(0, new ItemStack(Items.OAK_PLANKS, 6));
        stock.setItem(1, new ItemStack(Items.STICK, 4));
        HelperContainers.containerAt(h.getLevel(), nearby).setItem(0, new ItemStack(Items.OAK_LOG, 64));
        var plan = HelperTrades.prepareAxe(helper);
        h.assertTrue(plan.stream().noneMatch(step -> step.type().equals(HelperStep.GATHER_WOOD)), "Ignored existing crafting ingredients");
        helper.queue().set(plan);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(!HelperToolkit.best(helper, HelperToolkit.Tool.AXE).isEmpty(), "Did not craft profession tool using stored ingredients");
            h.assertTrue(stock.countItem(Items.OAK_PLANKS) == 3 && stock.countItem(Items.STICK) == 2, "Fetched more ingredients than required");
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), nearby).countItem(Items.OAK_LOG) == 64, "Used area resources before warehouse ingredients");
            helper.discard();
        });
    }

    private static void keeperMarksStorage(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        for (BlockPos sign : HelperSigns.around(h.getLevel(), helper.blockPosition(), 64, HelperSigns.Mark.STORE)) h.getLevel().removeBlock(sign, false);
        equipKit(helper);
        helper.getBag().addItem(new ItemStack(Items.OAK_PLANKS, 8));
        helper.getBag().addItem(new ItemStack(Items.STICK));
        BlockPos store = chest(h, 23, 20);
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(HelperSigns.nearest(h.getLevel(), helper.blockPosition(), 16, HelperSigns.Mark.STORE) != null, "Keeper never crafted and placed STORE marker: " + state(h, helper) + ",problem=" + helper.workProblem());
            h.assertTrue(helper.getBag().countItem(Items.DIRT) == 10 && helper.getBag().countItem(Items.CRAFTING_TABLE) == 1, "Keeper did not retain personal supplies after marking storage");
            // One craft makes three signs and one went up; the spare two may be in the bag or already in the warehouse.
            int spare = helper.getBag().countItem(Items.OAK_SIGN) + HelperContainers.containerAt(h.getLevel(), store).countItem(Items.OAK_SIGN);
            h.assertTrue(spare == 2, "Crafted or spent duplicate signs: " + spare + " spare, " + state(h, helper));
            helper.discard();
        });
    }

    private static void keeperHaulAndSort(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        BlockPos empty = chest(h, 10, 20);
        BlockPos inner = chest(h, 17, 20);
        BlockPos source = chest(h, 35, 20);
        BlockPos outside = chest(h, 45, 20);
        var target = HelperContainers.containerAt(h.getLevel(), store);
        target.setItem(0, new ItemStack(Items.COAL, 60));
        HelperContainers.containerAt(h.getLevel(), inner).setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        HelperContainers.containerAt(h.getLevel(), source).setItem(0, new ItemStack(Items.COAL, 20));
        HelperContainers.containerAt(h.getLevel(), source).setItem(1, new ItemStack(Items.STONE, 64));
        HelperContainers.containerAt(h.getLevel(), outside).setItem(0, new ItemStack(Items.DIAMOND, 7));
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), source).isEmpty() && helper.getCargo().isEmpty(), "Keeper did not complete physical source-to-warehouse trips: " + state(h, helper));
            h.assertTrue(target.countItem(Items.COAL) == 80 && target.getItem(0).getCount() == 64, "Did not prefer matching resource or top up partial stack first");
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), empty).countItem(Items.STONE) == 64, "New resource did not go to empty chest");
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), inner).countItem(Items.IRON_INGOT) == 3, "Drained a chest inside warehouse's 16-block zone");
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), outside).countItem(Items.DIAMOND) == 7, "Took resources outside movement radius");
            h.assertTrue(helper.getBag().countItem(Items.DIRT) == 10 && helper.getBag().countItem(Items.CRAFTING_TABLE) == 1, "Personal kit was mixed into cargo");
            helper.discard();
        });
    }

    /**
     * Whatever has lain ten seconds goes to the warehouse in one round, another helper's harvest included, while the
     * keeper's own kit stays in its bag (game 28.10: the keeper stood about while things lay on the ground).
     */
    private static void keeperSweepsGround(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        List<BlockPos> chests = new java.util.ArrayList<>(List.of(store));
        for (int z : new int[] {16, 18, 22, 24, 26}) chests.add(chest(h, 5, z));

        var level = h.getLevel();
        java.util.function.BiConsumer<ItemStack, BlockPos> lay = (stack, at) -> {
            var pos = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(at));
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, pos.x, pos.y, pos.z, stack));
        };
        lay.accept(new ItemStack(Items.COBBLESTONE, 32), new BlockPos(14, 1, 22));
        lay.accept(new ItemStack(Items.APPLE, 3), new BlockPos(24, 1, 18));
        lay.accept(new ItemStack(Items.STRING, 5), new BlockPos(26, 1, 24));
        lay.accept(new ItemStack(Items.DIRT, 4), new BlockPos(22, 1, 14));
        // Another helper's harvest, left lying.
        NpcHelperEntity other = h.spawn(NpcHelperMod.HELPER.get(), new BlockPos(30, 1, 30));
        other.setNoAi(true);
        other.pickup().drop(h.absolutePos(new BlockPos(18, 1, 26)), List.of(new ItemStack(Items.OAK_LOG, 10)));

        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            java.util.function.ToIntFunction<net.minecraft.world.item.Item> stored = item -> chests.stream()
                    .mapToInt(pos -> HelperContainers.containerAt(level, pos).countItem(item)).sum();
            h.assertTrue(stored.applyAsInt(Items.COBBLESTONE) == 32 && stored.applyAsInt(Items.APPLE) == 3
                    && stored.applyAsInt(Items.STRING) == 5, "Loose items were not carried to the warehouse: " + state(h, helper));
            h.assertTrue(stored.applyAsInt(Items.OAK_LOG) == 10, "Another helper's abandoned harvest was left lying");
            h.assertTrue(stored.applyAsInt(Items.DIRT) == 4, "Dirt beyond the keeper's own kit was not stored");
            h.assertTrue(helper.getBag().countItem(Items.DIRT) == 10 && helper.getBag().countItem(Items.CRAFTING_TABLE) == 1
                    && helper.getBag().countItem(Items.COBBLESTONE) == 0, "Kit was stored, or loot was kept back");
            helper.discard();
            other.discard();
        });
    }

    /**
     * A warehouse of one chest already holding something else still takes a new kind of thing while it has room: there is
     * no separate chest for it and no empty one, so the last resort is any free slot (game 28.10: the keeper stood holding
     * things the warehouse would not take).
     */
    private static void warehouseAnyFreeSlot(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        var target = HelperContainers.containerAt(h.getLevel(), store);
        for (int slot = 0; slot < 5; slot++) target.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));

        var level = h.getLevel();
        var at = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(24, 1, 18)));
        level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.APPLE, 3)));

        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(target.countItem(Items.APPLE) == 3, "A new kind was not put into the one warehouse chest with room: " + state(h, helper));
            h.assertTrue(target.countItem(Items.COBBLESTONE) == 5 * 64, "What was already stored was disturbed");
            h.assertTrue(helper.getBag().countItem(Items.APPLE) == 0, "Kept the loot in the bag although there was room");
            helper.discard();
        });
    }

    /**
     * Things keep landing on the ground, as they do in a lived-in place, and the keeper still gets what it carries into
     * the warehouse (game 28.10: it went round and round picking up and never took anything in, because each walk to the
     * warehouse was turned back by the next thing lying about).
     */
    private static void keeperUnloadsAmidLitter(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        List<BlockPos> chests = new java.util.ArrayList<>(List.of(store));
        for (int z : new int[] {16, 18, 22, 24}) chests.add(chest(h, 5, z));
        helper.getBag().addItem(new ItemStack(Items.COBBLESTONE, 20));

        var level = h.getLevel();
        // Eight spots apart, one item a second: there is always something lying long enough to be taken.
        List<net.minecraft.world.phys.Vec3> spots = new java.util.ArrayList<>();
        for (int[] at : new int[][] {{26, 14}, {30, 18}, {30, 24}, {26, 28}, {20, 30}, {14, 28}, {12, 22}, {16, 12}}) {
            spots.add(h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(at[0], 1, at[1]))));
        }
        h.onEachTick(() -> {
            if (h.getTick() % 20 == 0) {
                var at = spots.get((int) (h.getTick() / 20 % spots.size()));
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STICK)));
            }
        });
        // The litter gets a head start, so plenty of it has lain long enough by the time the keeper starts.
        h.runAfterDelay(600, () -> {
            helper.setProfession(HelperProfession.KEEPER);
            helper.setNoAi(false);
        });
        h.succeedWhen(() -> {
            int stored = chests.stream().mapToInt(pos -> HelperContainers.containerAt(level, pos).countItem(Items.COBBLESTONE)).sum();
            h.assertTrue(stored == 20, "Never took its load in while things kept landing: " + state(h, helper));
            helper.discard();
        });
    }

    /** Places a furnace of the kind given and returns its container. */
    private static net.minecraft.world.Container furnace(final GameTestHelper h, final BlockPos at, final net.minecraft.world.level.block.Block kind) {
        h.setBlock(at, kind);
        var placed = HelperContainers.furnaceAt(h.getLevel(), h.absolutePos(at));
        // A test failure, not a null that brings the whole test server down.
        h.assertTrue(placed != null, "No furnace where it was set: " + h.getLevel().getBlockState(h.absolutePos(at)));
        return placed;
    }

    /** How much of an item the warehouse chests hold between them. */
    private static int stored(final GameTestHelper h, final List<BlockPos> chests, final net.minecraft.world.item.Item item) {
        return chests.stream().mapToInt(pos -> HelperContainers.containerAt(h.getLevel(), pos).countItem(item)).sum();
    }

    /** Ore and coal from the warehouse go into the furnace: just enough coal for the ore, one coal to eight. */
    private static void keeperSmeltsOre(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        var chest = HelperContainers.containerAt(h.getLevel(), store);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 16));
        chest.setItem(1, new ItemStack(Items.COAL, 4));
        var blast = furnace(h, new BlockPos(7, 1, 20), Blocks.BLAST_FURNACE);
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            int inFurnace = blast.countItem(Items.RAW_IRON) + blast.countItem(Items.IRON_INGOT);
            h.assertTrue(chest.countItem(Items.RAW_IRON) == 0 && inFurnace == 16, "Ore was not loaded into the furnace: " + state(h, helper));
            h.assertTrue(chest.countItem(Items.COAL) == 2, "Not exactly the coal the ore needed was taken: " + chest.countItem(Items.COAL));
            helper.discard();
        });
    }

    /** What has finished smelting is taken out of the furnace and put into the warehouse. */
    private static void keeperCollectsFurnaceOutput(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        var furnace = furnace(h, new BlockPos(7, 1, 20), Blocks.FURNACE);
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 6));
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(furnace.getItem(2).isEmpty() && stored(h, List.of(store), Items.IRON_INGOT) == 6,
                    "Finished smelting was not moved into the warehouse: " + state(h, helper));
            h.assertTrue(helper.getBag().countItem(Items.IRON_INGOT) == 0, "Kept the ingots in the bag");
            helper.discard();
        });
    }

    /** A big batch is shared out over the furnaces, so they all work at once. */
    private static void keeperSplitsAcrossFurnaces(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        var chest = HelperContainers.containerAt(h.getLevel(), store);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 16));
        chest.setItem(1, new ItemStack(Items.COAL, 4));
        var first = furnace(h, new BlockPos(7, 1, 20), Blocks.FURNACE);
        var second = furnace(h, new BlockPos(7, 1, 23), Blocks.FURNACE);
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            int a = first.countItem(Items.RAW_IRON) + first.countItem(Items.IRON_INGOT);
            int b = second.countItem(Items.RAW_IRON) + second.countItem(Items.IRON_INGOT);
            h.assertTrue(a == 8 && b == 8, "Ore was not shared between the furnaces: " + a + " and " + b + ", " + state(h, helper));
            h.assertTrue(chest.countItem(Items.COAL) == 2, "Each furnace did not get just the one coal its eight ore needed");
            helper.discard();
        });
    }

    /** No furnace, no smelting: ore and coal stay where they are. */
    private static void keeperWithoutFurnace(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        var chest = HelperContainers.containerAt(h.getLevel(), store);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 10));
        chest.setItem(1, new ItemStack(Items.COAL, 2));
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.runAfterDelay(400, () -> {
            h.assertTrue(chest.countItem(Items.RAW_IRON) == 10 && chest.countItem(Items.COAL) == 2
                    && helper.getBag().countItem(Items.RAW_IRON) == 0, "Moved ore about with no furnace to smelt it in");
            helper.discard();
            h.succeed();
        });
    }

    /**
     * At night the keeper stays on at the warehouse rather than going home: it still empties the furnaces, but does not
     * go out gathering in the dark.
     */
    private static void keeperNightDuty(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        var furnace = furnace(h, new BlockPos(7, 1, 20), Blocks.FURNACE);
        furnace.setItem(2, new ItemStack(Items.GOLD_INGOT, 4));
        var level = h.getLevel();
        var at = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(26, 1, 26)));
        var litter = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STRING, 3));
        level.addFreshEntity(litter);
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.runAfterDelay(900, () -> {
            h.assertTrue(dev.npchelper.entity.HelperNightGoal.night(helper), "Night test environment was not applied");
            h.assertTrue(!helper.nightGoal().active(), "The keeper went home for the night instead of staying on");
            h.assertTrue(stored(h, List.of(store), Items.GOLD_INGOT) == 4, "Did not tend the furnaces at night: " + state(h, helper));
            h.assertTrue(litter.isAlive(), "Went out gathering in the dark");
            helper.discard();
            h.succeed();
        });
    }

    /**
     * The warehouse is in a house: at night the keeper stays inside it and leaves the chest out in the street alone
     * (game 29.10: it went out at night).
     */
    private static void keeperNightIndoors(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        house(h);
        BlockPos inside = h.absolutePos(new BlockPos(29, 1, 20));
        helper.teleportTo(inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D);
        helper.setMode(HelperMode.STAY);
        helper.setRoamRadius(32);
        BlockPos store = chest(h, 30, 22);
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), h.absolutePos(new BlockPos(30, 1, 18)), HelperSigns.Mark.STORE), "Cannot mark warehouse");
        BlockPos street = chest(h, 8, 20);
        var outside = HelperContainers.containerAt(h.getLevel(), street);
        outside.setItem(0, new ItemStack(Items.COAL, 10));
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        var room = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(h.absolutePos(new BlockPos(27, 1, 18)), h.absolutePos(new BlockPos(31, 3, 22)));
        h.onEachTick(() -> {
            h.assertTrue(room.isInside(helper.blockPosition()), "The keeper went outside at night: " + state(h, helper));
        });
        h.runAfterDelay(900, () -> {
            h.assertTrue(dev.npchelper.entity.HelperNightGoal.night(helper), "Night test environment was not applied");
            h.assertTrue(outside.countItem(Items.COAL) == 10 && stored(h, List.of(store), Items.COAL) == 0, "Hauled from a chest out in the dark");
            helper.discard();
            h.succeed();
        });
    }

    /**
     * The furnace is in a house whose door is on the far side, so the keeper must walk round it and in — and on the way
     * round it gets further from the furnace before it gets closer (game 29.10: it reached the door, opened and shut it,
     * backed off and stood about, and loaded the furnace only once it happened to be inside already).
     */
    private static void keeperFurnaceBehindDoor(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        for (int x = 26; x <= 32; x++) for (int z = 17; z <= 23; z++) {
            h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            if (x == 26 || x == 32 || z == 17 || z == 23) {
                for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        // The door is on the side away from the keeper.
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST);
        h.setBlock(new BlockPos(32, 1, 20), door);
        h.setBlock(new BlockPos(32, 2, 20), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        BlockPos store = chest(h, 22, 18);
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), h.absolutePos(new BlockPos(21, 1, 18)), HelperSigns.Mark.STORE), "Cannot mark warehouse");
        var chest = HelperContainers.containerAt(h.getLevel(), store);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 16));
        chest.setItem(1, new ItemStack(Items.COAL, 4));

        var blast = furnace(h, new BlockPos(28, 1, 20), Blocks.BLAST_FURNACE);
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            int inFurnace = blast.countItem(Items.RAW_IRON) + blast.countItem(Items.IRON_INGOT);
            h.assertTrue(inFurnace == 16 && chest.countItem(Items.COAL) == 2,
                    "Did not get round to the furnace behind the door: furnace " + inFurnace + ", " + state(h, helper) + ",problem=" + helper.workProblem());
            helper.discard();
        });
    }

    /** The lid goes up while the helper puts things in, the way it does for a player, and comes down after. */
    private static void chestLidOpens(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        BlockPos store = chest(h, 23, 20);
        // Its own warehouse sign, so the logs go here and not to a neighbouring test's warehouse.
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), h.absolutePos(new BlockPos(23, 1, 21)), HelperSigns.Mark.STORE), "Cannot mark warehouse");
        helper.getBag().addItem(new ItemStack(Items.OAK_LOG, 12));
        java.util.concurrent.atomic.AtomicBoolean opened = new java.util.concurrent.atomic.AtomicBoolean();
        h.onEachTick(() -> {
            if (net.minecraft.world.level.block.entity.ChestBlockEntity.getOpenCount(h.getLevel(), store) > 0) opened.set(true);
        });
        helper.queue().set(List.of(new HelperStep(HelperStep.STORE, "", 1)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), store).countItem(Items.OAK_LOG) == 12, "Logs were not stored");
            h.assertTrue(opened.get(), "The chest lid never went up");
            h.assertTrue(net.minecraft.world.level.block.entity.ChestBlockEntity.getOpenCount(h.getLevel(), store) == 0, "The chest was left open");
            helper.discard();
        });
    }

    /** No iron anywhere: a sword is made and the guard goes on its round without a shield, not off hunting for one. */
    private static void guardSwordWithoutIron(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.getBag().addItem(new ItemStack(Items.OAK_LOG, 4));
        helper.setProfession(HelperProfession.GUARD);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            var current = helper.queue().current();
            h.assertTrue(HelperToolkit.Tool.SWORD.is(helper.getMainHandItem()), "No sword in hand: " + state(h, helper));
            h.assertTrue(current != null && current.type().equals(HelperStep.GUARD_PATROL), "Not on its round: " + state(h, helper));
            h.assertTrue(helper.getOffhandItem().isEmpty(), "A shield appeared from nowhere");
            h.assertTrue(helper.queue().snapshot().stream().noneMatch(view -> view.type().equals(HelperStep.PREPARE_SHIELD)), "Still looking for a shield");
            helper.discard();
        });
    }

    /** With an iron ingot to hand the guard makes itself a shield and carries it in the other hand. */
    private static void guardShieldFromIron(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.getBag().addItem(new ItemStack(Items.OAK_LOG, 6));
        BlockPos store = chest(h, 23, 20);
        HelperContainers.containerAt(h.getLevel(), store).setItem(0, new ItemStack(Items.IRON_INGOT, 1));
        helper.setProfession(HelperProfession.GUARD);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(HelperToolkit.Tool.SWORD.is(helper.getMainHandItem()), "No sword in hand: " + state(h, helper));
            h.assertTrue(helper.getOffhandItem().is(Items.SHIELD), "No shield in the other hand: " + state(h, helper) + ",problem=" + helper.workProblem());
            helper.discard();
        });
    }

    /** The round takes it out across its range, with stops to look about, and never past the range. */
    private static void guardPatrols(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.getBag().addItem(new ItemStack(Items.WOODEN_SWORD));
        helper.setRoamRadius(8);
        net.minecraft.world.phys.Vec3 start = helper.position();
        java.util.concurrent.atomic.AtomicReference<Double> furthest = new java.util.concurrent.atomic.AtomicReference<>(0.0D);
        helper.setProfession(HelperProfession.GUARD);
        helper.setNoAi(false);
        h.onEachTick(() -> {
            double distance = Math.sqrt(helper.position().distanceToSqr(start.x, helper.getY(), start.z));
            furthest.set(Math.max(furthest.get(), distance));
            h.assertTrue(distance <= 8 + 2, "Patrol went past its range: " + distance);
        });
        h.runAfterDelay(1100, () -> {
            h.assertTrue(furthest.get() >= 3.0D, "Did not go out on its round: " + furthest.get() + " " + state(h, helper));
            h.assertTrue(helper.workGoal().guard().pauses() >= 2, "Did not stop to look about: " + helper.workGoal().guard().pauses());
            helper.discard();
            h.succeed();
        });
    }

    /** A monster beyond the ordinary sixteen blocks, out in the open: the guard sees it on its round and goes for it. */
    private static void guardWatchesFar(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.getBag().addItem(new ItemStack(Items.WOODEN_SWORD));
        helper.setRoamRadius(4);
        var husk = h.spawn(net.minecraft.world.entity.EntityTypes.HUSK, new BlockPos(44, 1, 20));
        husk.setNoAi(true);
        helper.setProfession(HelperProfession.GUARD);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getTarget() == husk || !husk.isAlive(), "Did not go for the monster in view: " + state(h, helper));
            helper.discard();
            husk.discard();
        });
    }

    /** A monster hitting another helper brings the guard to its aid. */
    private static void guardDefendsHelper(final GameTestHelper h) {
        NpcHelperEntity guard = helper(h);
        equipKit(guard);
        guard.getBag().addItem(new ItemStack(Items.WOODEN_SWORD));
        guard.setRoamRadius(4);
        NpcHelperEntity victim = h.spawn(NpcHelperMod.HELPER.get(), new BlockPos(30, 1, 20));
        victim.setMode(HelperMode.STAY);
        var husk = h.spawn(net.minecraft.world.entity.EntityTypes.HUSK, new BlockPos(32, 1, 20));
        husk.setNoAi(true);
        guard.setProfession(HelperProfession.GUARD);
        guard.setNoAi(false);
        h.runAfterDelay(40, () -> victim.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(husk), 1.0F));
        h.runAfterDelay(41, () -> h.succeedWhen(() -> {
            h.assertTrue(guard.getTarget() == husk || !husk.isAlive(), "Did not come to the other helper's aid: " + state(h, guard));
            guard.discard();
            victim.discard();
            husk.discard();
        }));
    }

    private static void keeperPartialStacks(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(32);
        BlockPos first = warehouse(h, helper);
        BlockPos later = chest(h, 10, 20);
        var target = HelperContainers.containerAt(h.getLevel(), first);
        var donor = HelperContainers.containerAt(h.getLevel(), later);
        target.setItem(0, new ItemStack(Items.COAL, 20));
        donor.setItem(0, new ItemStack(Items.COAL, 15));
        donor.setItem(5, new ItemStack(Items.COAL, 15));
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(target.countItem(Items.COAL) == 50 && donor.isEmpty() && helper.getCargo().isEmpty(), "Partial stacks were not merged within and between chests");
            helper.discard();
        });
    }

    private static void keeperFullStorage(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(16);
        BlockPos store = warehouse(h, helper);
        var target = HelperContainers.containerAt(h.getLevel(), store);
        for (int slot = 0; slot < target.getContainerSize(); slot++) target.setItem(slot, new ItemStack(Items.COAL, 64));
        BlockPos source = chest(h, 35, 20);
        HelperContainers.containerAt(h.getLevel(), source).setItem(0, new ItemStack(Items.COAL, 12));
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.runAfterDelay(100, () -> {
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), source).countItem(Items.COAL) == 12 && helper.getCargo().isEmpty(), "Took cargo with no destination space");
            h.assertTrue(target.countItem(Items.COAL) == 27 * 64, "Full warehouse lost or duplicated resources");
            helper.discard();
            h.succeed();
        });
    }

    private static void cargoSaveAndCancel(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        BlockPos store = warehouse(h, helper);
        helper.getCargo().setItem(0, new ItemStack(Items.DIAMOND, 12));
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        helper.saveWithoutId(saved);
        var restored = NpcHelperMod.HELPER.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved.buildResult()));
        h.assertTrue(restored.getCargo().countItem(Items.DIAMOND) == 12, "Cargo was lost on save/load");
        helper.setProfession(HelperProfession.NONE);
        h.assertTrue(helper.queue().current().type().equals(HelperStep.KEEPER_DELIVER), "Changing profession lost undelivered cargo");
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getCargo().isEmpty() && HelperContainers.containerAt(h.getLevel(), store).countItem(Items.DIAMOND) == 12, "Cancelled delivery lost cargo");
            helper.discard();
        });
    }

    private static void storageStackLimits(final GameTestHelper h) {
        SimpleContainer bag = new SimpleContainer(4);
        bag.setItem(1, new ItemStack(Items.IRON_INGOT, 60));
        h.assertTrue(HelperContainers.put(bag, new ItemStack(Items.IRON_INGOT, 8)).isEmpty(), "Cannot insert test stack");
        h.assertTrue(bag.getItem(1).getCount() == 64 && bag.getItem(0).getCount() == 4, "Used empty slot before topping up existing stack");
        SimpleContainer pearls = new SimpleContainer(4);
        pearls.setItem(0, new ItemStack(Items.ENDER_PEARL, 15));
        pearls.setItem(1, new ItemStack(Items.ENDER_PEARL, 15));
        ItemStack named = new ItemStack(Items.ENDER_PEARL, 5);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Reserved"));
        pearls.setItem(2, named);
        HelperContainers.compact(pearls);
        h.assertTrue(pearls.getItem(0).getCount() == 16 && pearls.getItem(1).getCount() == 14 && pearls.getItem(2).getCount() == 5, "Compaction exceeded limits or mixed components");
        h.succeed();
    }

    private static void doubleChestInventory(final GameTestHelper h) {
        var left = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        var right = left.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT);
        BlockPos first = h.absolutePos(new BlockPos(20, 1, 20));
        BlockPos second = first.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(left));
        h.getLevel().setBlock(first, left, 2);
        h.getLevel().setBlock(second, right, 2);
        var container = HelperContainers.containerAt(h.getLevel(), first);
        h.assertTrue(container != null && container.getContainerSize() == 54, "Double chest was not viewed as one inventory");
        container.setItem(0, new ItemStack(Items.COAL, 20));
        container.setItem(27, new ItemStack(Items.COAL, 30));
        h.assertTrue(HelperContainers.around(h.getLevel(), first, 4).size() == 1, "Scanned both halves as independent stores");
        HelperContainers.compact(container);
        h.assertTrue(container.countItem(Items.COAL) == 50 && container.getItem(0).getCount() == 50, "Double chest compaction changed total resources");
        h.succeed();
    }

    private static void warehouseSoilPriority(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.setRoamRadius(32);
        helper.getBag().addItem(new ItemStack(Items.CRAFTING_TABLE));
        BlockPos store = warehouse(h, helper);
        BlockPos nearby = chest(h, 23, 20);
        HelperContainers.containerAt(h.getLevel(), store).setItem(0, new ItemStack(Items.DIRT, 10));
        HelperContainers.containerAt(h.getLevel(), nearby).setItem(0, new ItemStack(Items.DIRT, 10));
        helper.queue().set(List.of(new HelperStep(HelperStep.PREPARE_KIT, "", 1)));
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.getBag().countItem(Items.DIRT) == 10, "Did not fetch common soil reserve");
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), store).countItem(Items.DIRT) == 0, "Did not prefer warehouse soil");
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), nearby).countItem(Items.DIRT) == 10, "Took local soil before warehouse stock");
            helper.discard();
        });
    }

    private static void readyToolPriority(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(32);
        BlockPos store = warehouse(h, helper);
        BlockPos nearby = chest(h, 23, 20);
        HelperContainers.containerAt(h.getLevel(), store).setItem(0, new ItemStack(Items.IRON_AXE));
        HelperContainers.containerAt(h.getLevel(), nearby).setItem(0, new ItemStack(Items.DIAMOND_AXE));
        h.assertTrue(HelperTrades.prepareAxe(helper).getFirst().item().equals("minecraft:iron_axe"), "Area tool took precedence over marked warehouse");
        helper.getBag().addItem(new ItemStack(Items.WOODEN_AXE));
        var plan = HelperTrades.prepareAxe(helper);
        h.assertTrue(plan.size() == 1 && plan.getFirst().type().equals(HelperStep.EQUIP)
                && plan.getFirst().item().equals("minecraft:wooden_axe"), "Ignored an already carried profession tool");
        helper.discard();
        h.succeed();
    }

    private static void sequentialProfessionTools(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(32);
        BlockPos store = warehouse(h, helper);
        var stock = HelperContainers.containerAt(h.getLevel(), store);
        stock.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        stock.setItem(1, new ItemStack(Items.STICK, 4));
        helper.setProfession(HelperProfession.MINER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(!HelperToolkit.best(helper, HelperToolkit.Tool.PICKAXE).isEmpty()
                    && !HelperToolkit.best(helper, HelperToolkit.Tool.SHOVEL).isEmpty(), "Did not prepare all required tools sequentially: " + state(h, helper));
            h.assertTrue(stock.countItem(Items.OAK_PLANKS) == 0 && stock.countItem(Items.STICK) == 0, "Crafting did not use exact shared ingredients");
            helper.discard();
        });
    }

    private static void markNightHome(final GameTestHelper h, final NpcHelperEntity helper, final boolean iron) {
        // Structure preparation can consume most of the night before the test body starts.
        var clock = h.getLevel().registryAccess().getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
        h.getLevel().getServer().clockManager().setTotalTicks(clock, 18000);
        house(h);
        if (iron) {
            var state = Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST);
            h.setBlock(new BlockPos(26, 1, 20), state);
            h.setBlock(new BlockPos(26, 2, 20), state.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), h.absolutePos(new BlockPos(30, 1, 20)), HelperSigns.Mark.HOME), "Cannot mark rescue destination");
    }

    private static void nightBlockedHomeRescue(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.setRoamRadius(4);
        markNightHome(h, helper, true);
        helper.setNoAi(false);
        h.runAfterDelay(590, () -> h.assertTrue(!dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), helper.blockPosition()), "Teleported before the 30-second timeout"));
        h.succeedWhen(() -> {
            h.assertTrue(dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), helper.blockPosition()), "Did not rescue NPC past an unreachable home entrance: " + state(h, helper) + ",status=" + helper.nightGoal().status());
            h.assertTrue(!h.getBlockState(new BlockPos(26, 1, 20)).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "Rescue changed the iron door");
            helper.discard();
        });
    }

    private static void nightWaterPitRescue(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.getCargo().setItem(0, new ItemStack(Items.DIAMOND, 6));
        markNightHome(h, helper, false);
        for (int x = 17; x <= 23; x++) for (int z = 17; z <= 23; z++) for (int y = 1; y <= 5; y++) {
            if (x == 17 || x == 23 || z == 17 || z == 23) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            else if (y <= 3) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        }
        BlockPos water = h.absolutePos(new BlockPos(20, 2, 20));
        helper.teleportTo(water.getX() + 0.5D, water.getY(), water.getZ() + 0.5D);
        helper.setMode(HelperMode.STAY);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(helper.isAlive() && dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), helper.blockPosition()), "NPC remained trapped in water instead of reaching shelter: " + state(h, helper));
            h.assertTrue(helper.getCargo().countItem(Items.DIAMOND) == 6 && helper.getBag().countItem(Items.DIRT) == 10
                    && helper.getBag().countItem(Items.CRAFTING_TABLE) == 1, "Rescue lost cargo or personal kit");
            h.assertTrue(h.getLevel().getFluidState(helper.blockPosition()).isEmpty(), "Rescue chose another water cell");
            helper.discard();
        });
    }

    private static void expeditionNoRescue(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.setRoamRadius(4);
        markNightHome(h, helper, true);
        helper.setExpedition(true);
        var origin = helper.position();
        helper.setNoAi(false);
        h.runAfterDelay(700, () -> {
            h.assertTrue(!dev.npchelper.entity.HelperShelter.indoors(h.getLevel(), helper.blockPosition())
                    && helper.position().distanceToSqr(origin) <= 16, "Expedition mode performed a night rescue");
            helper.discard();
            h.succeed();
        });
    }

    private static void unsafeRescuePositions(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        BlockPos candidate = helper.blockPosition().east(4);
        h.getLevel().setBlockAndUpdate(candidate, Blocks.WATER.defaultBlockState());
        h.assertTrue(!dev.npchelper.entity.HelperShelter.safeTeleport(helper, candidate), "Accepted water as a rescue target");
        h.getLevel().setBlockAndUpdate(candidate, Blocks.STONE.defaultBlockState());
        h.assertTrue(!dev.npchelper.entity.HelperShelter.safeTeleport(helper, candidate), "Accepted a solid block as a rescue target");
        h.getLevel().setBlockAndUpdate(candidate, Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(candidate.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        h.assertTrue(!dev.npchelper.entity.HelperShelter.safeTeleport(helper, candidate), "Accepted damaging floor as a rescue target");
        helper.discard();
        h.succeed();
    }

    private static void inventoryMaterialsBeforeStock(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        helper.getBag().addItem(new ItemStack(Items.OAK_PLANKS, 4));
        BlockPos store = warehouse(h, helper);
        HelperContainers.containerAt(h.getLevel(), store).setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        var plan = dev.npchelper.work.HelperResources.plan(helper, Items.CRAFTING_TABLE, 1);
        h.assertTrue(!plan.refused() && plan.steps().size() == 1 && plan.steps().getFirst().type().equals(HelperStep.CRAFT),
                "Warehouse stock took precedence over sufficient owned crafting materials");
        h.assertTrue(helper.getBag().countItem(Items.OAK_PLANKS) == 4 && HelperContainers.containerAt(h.getLevel(), store).countItem(Items.CRAFTING_TABLE) == 1,
                "Planning mutated inventories");
        helper.discard();
        h.succeed();
    }

    private static void unreachableWarehouseFallback(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        helper.setRoamRadius(4);
        for (BlockPos sign : HelperSigns.around(h.getLevel(), helper.blockPosition(), 64, HelperSigns.Mark.STORE)) h.getLevel().removeBlock(sign, false);
        BlockPos store = warehouse(h, helper);
        for (int z = 0; z < 48; z++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(12, y, z), Blocks.STONE);
        BlockPos local = chest(h, 23, 20);
        HelperContainers.containerAt(h.getLevel(), store).setItem(0, new ItemStack(Items.IRON_AXE));
        HelperContainers.containerAt(h.getLevel(), local).setItem(0, new ItemStack(Items.STONE_AXE));
        var plan = HelperTrades.prepareAxe(helper);
        h.assertTrue(!plan.isEmpty() && plan.getFirst().type().equals(HelperStep.FETCH)
                && plan.getFirst().item().equals("minecraft:stone_axe"), "Unreachable warehouse prevented use of reachable area stock");
        helper.discard();
        h.succeed();
    }

    private static void keeperAcrossFullRadius(final GameTestHelper h) {
        NpcHelperEntity helper = helper(h);
        equipKit(helper);
        BlockPos centre = h.absolutePos(new BlockPos(80, 1, 20));
        helper.teleportTo(centre.getX() + 0.5D, centre.getY(), centre.getZ() + 0.5D);
        helper.setMode(HelperMode.STAY);
        helper.setRoamRadius(64);
        BlockPos store = chest(h, 20, 20);
        BlockPos source = chest(h, 140, 20);
        helper.getBag().addItem(new ItemStack(Items.OAK_SIGN));
        h.assertTrue(HelperSigns.place(h.getLevel(), helper.getBag(), h.absolutePos(new BlockPos(18, 1, 20)), HelperSigns.Mark.STORE), "Cannot mark far warehouse");
        HelperContainers.containerAt(h.getLevel(), store).setItem(0, new ItemStack(Items.COAL, 60));
        HelperContainers.containerAt(h.getLevel(), source).setItem(0, new ItemStack(Items.COAL, 8));
        helper.setProfession(HelperProfession.KEEPER);
        helper.setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(HelperContainers.containerAt(h.getLevel(), store).countItem(Items.COAL) == 68
                    && HelperContainers.containerAt(h.getLevel(), source).isEmpty() && helper.getCargo().isEmpty(),
                    "Cannot carry across opposite sides of the 64-block movement area: " + state(h, helper));
            helper.discard();
        });
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
