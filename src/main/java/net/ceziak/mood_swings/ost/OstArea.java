package net.ceziak.mood_swings.ost;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** A standalone ambience region. OST areas never participate in mood-task targeting. */
public record OstArea(
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        String displayName,
        String soundId,
        float volume,
        int priority,
        float fadeSeconds,
        float shakeIntensity,
        float darkness
) {
    public static final float DEFAULT_VOLUME = 1.0F;
    public static final int DEFAULT_PRIORITY = 0;
    public static final float DEFAULT_FADE_SECONDS = 2.0F;
    public static final float DEFAULT_SHAKE_INTENSITY = 0.0F;
    public static final float DEFAULT_DARKNESS = 0.0F;
    private static final double PLAYER_EDGE_TOLERANCE = 0.05D;

    public OstArea {
        dimension = dimension == null ? "" : dimension.trim();
        displayName = displayName == null ? "" : displayName.trim();
        soundId = soundId == null ? "" : soundId.trim();
        volume = Math.clamp(volume, 0.0F, 1.0F);
        fadeSeconds = Math.clamp(fadeSeconds, 0.0F, 30.0F);
        shakeIntensity = Math.clamp(shakeIntensity, 0.0F, 1.0F);
        darkness = Math.clamp(darkness, 0.0F, 1.0F);
    }

    public static OstArea between(
            String dimension,
            BlockPos first,
            BlockPos second,
            String displayName,
            String soundId
    ) {
        return new OstArea(
                dimension,
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()),
                displayName,
                soundId,
                DEFAULT_VOLUME,
                DEFAULT_PRIORITY,
                DEFAULT_FADE_SECONDS,
                DEFAULT_SHAKE_INTENSITY,
                DEFAULT_DARKNESS
        );
    }

    public OstArea validated() {
        if (dimension.isBlank() || Identifier.tryParse(dimension) == null) {
            throw new IllegalArgumentException("Invalid OST area dimension: " + dimension);
        }
        if (soundId.isBlank() || Identifier.tryParse(soundId) == null) {
            throw new IllegalArgumentException("Invalid OST sound identifier: " + soundId);
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("OST area minimum coordinates must not exceed its maximum coordinates");
        }
        if (!Float.isFinite(volume) || volume < 0.0F || volume > 1.0F) {
            throw new IllegalArgumentException("OST area volume must be between 0 and 1");
        }
        if (!Float.isFinite(fadeSeconds) || fadeSeconds < 0.0F || fadeSeconds > 30.0F) {
            throw new IllegalArgumentException("OST area fade must be between 0 and 30 seconds");
        }
        if (!Float.isFinite(shakeIntensity) || shakeIntensity < 0.0F || shakeIntensity > 1.0F) {
            throw new IllegalArgumentException("OST area screen shake must be between 0 and 1");
        }
        if (!Float.isFinite(darkness) || darkness < 0.0F || darkness > 1.0F) {
            throw new IllegalArgumentException("OST area world darkening must be between 0 and 1");
        }
        return this;
    }

    public boolean contains(ServerPlayerEntity player) {
        if (!dimension.equals(player.getWorld().getRegistryKey().getValue().toString())) {
            return false;
        }
        Box areaBox = new Box(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
        return areaBox.intersects(player.getBoundingBox().expand(PLAYER_EDGE_TOLERANCE));
    }

    public long blockVolume() {
        long x = (long) maxX - minX + 1L;
        long y = (long) maxY - minY + 1L;
        long z = (long) maxZ - minZ + 1L;
        try {
            return Math.multiplyExact(Math.multiplyExact(x, y), z);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public int fadeTicks() {
        return Math.max(0, Math.round(fadeSeconds * 20.0F));
    }

    public String cornersString() {
        return "(" + minX + ", " + minY + ", " + minZ + ") -> ("
                + maxX + ", " + maxY + ", " + maxZ + ")";
    }

    public OstArea withSound(String newSoundId) {
        return new OstArea(dimension, minX, minY, minZ, maxX, maxY, maxZ,
                displayName, newSoundId, volume, priority, fadeSeconds, shakeIntensity, darkness).validated();
    }

    public OstArea withVolume(float newVolume) {
        return new OstArea(dimension, minX, minY, minZ, maxX, maxY, maxZ,
                displayName, soundId, newVolume, priority, fadeSeconds, shakeIntensity, darkness).validated();
    }

    public OstArea withPriority(int newPriority) {
        return new OstArea(dimension, minX, minY, minZ, maxX, maxY, maxZ,
                displayName, soundId, volume, newPriority, fadeSeconds, shakeIntensity, darkness).validated();
    }

    public OstArea withFadeSeconds(float newFadeSeconds) {
        return new OstArea(dimension, minX, minY, minZ, maxX, maxY, maxZ,
                displayName, soundId, volume, priority, newFadeSeconds, shakeIntensity, darkness).validated();
    }

    public OstArea withShakeIntensity(float newShakeIntensity) {
        return new OstArea(dimension, minX, minY, minZ, maxX, maxY, maxZ,
                displayName, soundId, volume, priority, fadeSeconds, newShakeIntensity, darkness).validated();
    }

    public OstArea withDarkness(float newDarkness) {
        return new OstArea(dimension, minX, minY, minZ, maxX, maxY, maxZ,
                displayName, soundId, volume, priority, fadeSeconds, shakeIntensity, newDarkness).validated();
    }
}
