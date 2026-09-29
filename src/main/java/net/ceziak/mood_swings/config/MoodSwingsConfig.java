package net.ceziak.mood_swings.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "mood_swings")
public final class MoodSwingsConfig implements ConfigData {
    @ConfigEntry.Category("area_borders")
    public String areaBorderColor = "#ff4f9a";

    @ConfigEntry.Category("area_borders")
    public float areaBorderOpacity = 0.95F;

    public int packedAreaBorderColor() {
        int rgb = 0xFF4F9A;
        if (areaBorderColor != null) {
            String normalized = areaBorderColor.trim();
            if (normalized.startsWith("#")) {
                normalized = normalized.substring(1);
            }
            if (normalized.matches("[0-9a-fA-F]{6}")) {
                rgb = Integer.parseInt(normalized, 16);
            }
        }
        int alpha = Math.round(Math.clamp(areaBorderOpacity, 0.05F, 1.0F) * 255.0F);
        return (alpha << 24) | rgb;
    }
}
