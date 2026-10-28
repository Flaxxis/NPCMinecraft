package dev.npchelper.client;

import dev.npchelper.NpcHelperMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only wiring. */
@Mod.EventBusSubscriber(modid = NpcHelperMod.MODID, value = Dist.CLIENT)
public final class NpcHelperClient {
    private NpcHelperClient() { }

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcHelperMod.HELPER.get(), HelperRenderer::new);
    }
}
