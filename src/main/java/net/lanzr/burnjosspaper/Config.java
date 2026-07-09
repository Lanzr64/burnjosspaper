package net.lanzr.burnjosspaper;


import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.lanzr.burnjosspaper.api.ConfigApi;
import net.lanzr.burnjosspaper.api.ConfigApi.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = BurnJossPapaer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        for (ConfigEntry<?, ?> entry : ConfigApi.ENTRIES) {
            entry.load();
        }
    }
    private static final String COMMON_TAB = "BJP_COMMON";


    public static final ConfigEntry<ForgeConfigSpec.IntValue, Integer> CONTAINER_SIZE = new ConfigEntry<>(BUILDER
            .comment("how many slots for public wish inventory")
            .defineInRange(COMMON_TAB + ".scanInterval", 180, 1, Integer.MAX_VALUE));

    public static ConfigEntry<ForgeConfigSpec.ConfigValue<List<? extends String>>, List<? extends String>> BLACK_LIST_ITEM = new ConfigEntry<>(BUILDER
            .comment("black list for can burn, e.g. [\"minecraft:stone\", \"minecraft:stick\"]")
            .defineListAllowEmpty(COMMON_TAB + ".extraEntityIds", List.of("minecraft:cobblestone"),Config::validateItemName));

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
//        return obj instanceof final String itemName
//                && ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(itemName));
    }


}
