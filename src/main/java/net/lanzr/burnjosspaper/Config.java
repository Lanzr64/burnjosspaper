package net.lanzr.burnjosspaper;


import net.minecraft.resources.ResourceLocation;
import net.lanzr.burnjosspaper.api.ConfigApi;
import net.lanzr.burnjosspaper.api.ConfigApi.ConfigEntry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        for (ConfigEntry<?, ?> entry : ConfigApi.ENTRIES) {
            entry.load();
        }
    }
    private static final String COMMON_TAB = "BJP_COMMON";


    public static final ConfigEntry<ModConfigSpec.IntValue, Integer> CONTAINER_SIZE = new ConfigEntry<>(BUILDER
            .comment("how many slots for public wish inventory")
            .defineInRange(COMMON_TAB + ".scanInterval", 180, 1, Integer.MAX_VALUE));

    public static ConfigEntry<ModConfigSpec.ConfigValue<List<? extends String>>, List<? extends String>> BLACK_LIST_ITEM = new ConfigEntry<>(BUILDER
            .comment("black list for can burn, e.g. [\"minecraft:stone\", \"minecraft:stick\"]")
            .defineListAllowEmpty(COMMON_TAB + ".extraEntityIds", List.of("minecraft:cobblestone"),Config::validateItemName));

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
//        return obj instanceof final String itemName
//                && ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(itemName));
    }


}
