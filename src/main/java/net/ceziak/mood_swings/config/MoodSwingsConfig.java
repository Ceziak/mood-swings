package net.ceziak.mood_swings.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "mood_swings")
public final class MoodSwingsConfig implements ConfigData {
    @ConfigEntry.Category("take_a_shower")
    public int showerDurationSeconds = 10;

    @ConfigEntry.Category("take_a_shower")
    public int showerScanHeight = 8;

    @ConfigEntry.Category("socialize")
    public int socializeDurationSeconds = 10;

    @ConfigEntry.Category("socialize")
    public double socializeRange = 3.0D;

    @ConfigEntry.Category("gym")
    public int gymDurationSeconds = 10;

    @ConfigEntry.Category("gym")
    public String gymAreaId = "gym";

    @ConfigEntry.Category("strange_noise")
    public int strangeNoiseDurationSeconds = 10;

    @ConfigEntry.Category("strange_noise")
    public boolean strangeNoiseExcludeCurrentArea = true;

    @ConfigEntry.Category("walk")
    public double walkDistanceBlocks = 50.0D;

    @ConfigEntry.Category("walk")
    public double walkMaximumCountedStep = 2.0D;

    @ConfigEntry.Category("rooftop")
    public int rooftopDurationSeconds = 10;

    @ConfigEntry.Category("rooftop")
    public String rooftopAreaId = "rooftop";

    public int showerDurationTicks() {
        return secondsToTicks(showerDurationSeconds);
    }

    public int socializeDurationTicks() {
        return secondsToTicks(socializeDurationSeconds);
    }

    public int gymDurationTicks() {
        return secondsToTicks(gymDurationSeconds);
    }

    public int strangeNoiseDurationTicks() {
        return secondsToTicks(strangeNoiseDurationSeconds);
    }

    public int rooftopDurationTicks() {
        return secondsToTicks(rooftopDurationSeconds);
    }

    public int safeShowerScanHeight() {
        return Math.clamp(showerScanHeight, 1, 64);
    }

    public double safeSocializeRange() {
        return Math.clamp(socializeRange, 0.5D, 64.0D);
    }

    public double safeWalkDistance() {
        return Math.clamp(walkDistanceBlocks, 1.0D, 100_000.0D);
    }

    public double safeWalkMaximumCountedStep() {
        return Math.clamp(walkMaximumCountedStep, 0.25D, 64.0D);
    }

    public String safeGymAreaId() {
        return sanitizeAreaId(gymAreaId, "gym");
    }

    public String safeRooftopAreaId() {
        return sanitizeAreaId(rooftopAreaId, "rooftop");
    }

    private static int secondsToTicks(int seconds) {
        return Math.clamp(seconds, 1, 3_600) * 20;
    }

    private static String sanitizeAreaId(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
