package dev.npchelper.client;

import dev.npchelper.NpcHelperMod;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only wiring. */
@Mod.EventBusSubscriber(modid = NpcHelperMod.MODID, value = Dist.CLIENT)
public final class NpcHelperClient {
    private NpcHelperClient() { }

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcHelperMod.HELPER.get(), HelperRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(NpcHelperMod.HELPER_MENU.get(), HelperScreen::new));
    }
}
