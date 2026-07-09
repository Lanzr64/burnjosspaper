package net.lanzr.burnjosspaper;

import net.lanzr.burnjosspaper.event.ModEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;


@Mod(BurnJossPapaer.MODID)
public class BurnJossPapaer {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "burnjosspaper";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public BurnJossPapaer() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
