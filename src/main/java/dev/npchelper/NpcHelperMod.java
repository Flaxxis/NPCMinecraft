package dev.npchelper;

import com.mojang.logging.LogUtils;
import dev.npchelper.entity.HelperSpawns;
import dev.npchelper.entity.NpcHelperEntity;
import dev.npchelper.inventory.HelperMenu;
import dev.npchelper.item.HelperDollItem;
import dev.npchelper.net.HelperNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/** Entry point. The mod id matches the one in META-INF/mods.toml. */
@Mod(NpcHelperMod.MODID)
public final class NpcHelperMod {
    public static final String MODID = "npchelper";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    public static final RegistryObject<MenuType<HelperMenu>> HELPER_MENU = MENUS.register("helper",
            () -> IForgeMenuType.create(HelperMenu::fromNetwork));

    public static final RegistryObject<EntityType<NpcHelperEntity>> HELPER = ENTITY_TYPES.register("helper",
            () -> EntityType.Builder.<NpcHelperEntity>of(NpcHelperEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .noLootTable()
                    .build(ENTITY_TYPES.key("helper")));

    public static final RegistryObject<Item> HELPER_DOLL = ITEMS.register("helper_doll",
            () -> new HelperDollItem(new Item.Properties().setId(ITEMS.key("helper_doll"))));

    public NpcHelperMod(final FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();
        ITEMS.register(modBusGroup);
        ENTITY_TYPES.register(modBusGroup);
        MENUS.register(modBusGroup);
        HelperNetwork.register();

        EntityAttributeCreationEvent.BUS.addListener(NpcHelperMod::createAttributes);
        BuildCreativeModeTabContentsEvent.BUS.addListener(NpcHelperMod::addCreative);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(NpcHelperMod::onPlayerLoggedIn);
    }

    private static void createAttributes(final EntityAttributeCreationEvent event) {
        event.put(HELPER.get(), NpcHelperEntity.createAttributes().build());
    }

    private static void addCreative(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(HELPER_DOLL);
    }

    private static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HelperSpawns.onPlayerLoggedIn(player);
    }
}
