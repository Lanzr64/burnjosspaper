package net.lanzr.burnjosspaper;

import net.lanzr.burnjosspaper.event.ModEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;


@Mod(BurnJossPapaer.MODID)
public class BurnJossPapaer {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "burnjosspaper";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public BurnJossPapaer(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
