package net.lanzr.burnjosspaper;


import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = BurnJossPapaer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    public static final ForgeConfigSpec SPEC;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        setup(builder);
        SPEC = builder.build();
    }

    private static final String COMMON_TAB = "BJP_COMMON";

    public static ForgeConfigSpec.IntValue CONTAINER_SIZE;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> BLACK_LIST_ITEM;


    public static int containerSize;
    public static List<? extends String> blackListItem;


    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
//        return obj instanceof final String itemName
//                && ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(itemName));
    }

    private static void setup(ForgeConfigSpec.Builder builder) {
        CONTAINER_SIZE = builder
                .comment("how many slots for public wish inventory")
                .defineInRange(COMMON_TAB + ".scanInterval", 180, 1, Integer.MAX_VALUE);
        BLACK_LIST_ITEM = builder
                .comment("black list for can burn, e.g. [\"minecraft:stone\", \"minecraft:stick\"]")
                .defineListAllowEmpty(COMMON_TAB + ".extraEntityIds", List.of("minecraft:cobblestone"),Config::validateItemName);
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        containerSize = CONTAINER_SIZE.get();
        blackListItem = BLACK_LIST_ITEM.get();
    }
}
