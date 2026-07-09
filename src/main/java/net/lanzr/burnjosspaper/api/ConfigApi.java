package net.lanzr.burnjosspaper.api;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class ConfigApi {
    public static final List<ConfigEntry<?, ?>> ENTRIES = new ArrayList<>();
    public static class ConfigEntry<T extends ForgeConfigSpec.ConfigValue<P>, P> {
        private final T configValue;
        private P value;

        public ConfigEntry(T configValue) {
            this.configValue = configValue;
            ENTRIES.add(this);
        }

        public void load() {
            this.value = configValue.get();
        }

        public P get() {
            return value;
        }
    }
}
