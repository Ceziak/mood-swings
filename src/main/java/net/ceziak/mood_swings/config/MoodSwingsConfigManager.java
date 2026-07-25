package net.ceziak.mood_swings.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

public final class MoodSwingsConfigManager {
    private MoodSwingsConfigManager() {
    }

    public static void initialize() {
        AutoConfig.register(MoodSwingsConfig.class, GsonConfigSerializer::new);
    }

    public static MoodSwingsConfig get() {
        return AutoConfig.getConfigHolder(MoodSwingsConfig.class).getConfig();
    }

    public static void reload() {
        AutoConfig.getConfigHolder(MoodSwingsConfig.class).load();
    }

    public static void save() {
        AutoConfig.getConfigHolder(MoodSwingsConfig.class).save();
    }
}
