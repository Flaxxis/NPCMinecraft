package dev.npchelper;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** Entry point. The mod id matches the one in META-INF/mods.toml. */
@Mod(NpcHelperMod.MODID)
public final class NpcHelperMod {
    public static final String MODID = "npchelper";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NpcHelperMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} loaded", MODID);
    }
}
