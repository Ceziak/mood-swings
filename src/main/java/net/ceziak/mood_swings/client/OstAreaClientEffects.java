package net.ceziak.mood_swings.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/** Smooth environmental camera motion and world-only darkening controlled by the selected OST Area. */
public final class OstAreaClientEffects {
    /** Full strength feels like a steady wind pushing against the camera without combat-style jitter. */
    private static final float MAX_SHAKE_DEGREES = 0.75F;
    /** Darkness 1.0 still leaves enough world detail to navigate; HUD and screens render above it. */
    private static final int MAX_DARK_ALPHA = 192;

    private static float previousShake;
    private static float currentShake;
    private static float shakeStart;
    private static float targetShake;

    private static float previousDarkness;
    private static float currentDarkness;
    private static float darknessStart;
    private static float targetDarkness;

    private static int transitionAge;
    private static int transitionTicks;

    private OstAreaClientEffects() {
    }

    public static void setTarget(float shakeIntensity, float darkness, int fadeTicks) {
        float clampedShake = Math.clamp(shakeIntensity, 0.0F, 1.0F);
        float clampedDarkness = Math.clamp(darkness, 0.0F, 1.0F);
        int duration = Math.max(0, fadeTicks);
        if (Float.compare(targetShake, clampedShake) == 0
                && Float.compare(targetDarkness, clampedDarkness) == 0
                && transitionTicks == duration) {
            return;
        }

        shakeStart = currentShake;
        darknessStart = currentDarkness;
        targetShake = clampedShake;
        targetDarkness = clampedDarkness;
        transitionAge = 0;
        transitionTicks = duration;
        if (transitionTicks == 0) {
            previousShake = clampedShake;
            currentShake = clampedShake;
            previousDarkness = clampedDarkness;
            currentDarkness = clampedDarkness;
        }
    }

    public static void tick(MinecraftClient client) {
        previousShake = currentShake;
        previousDarkness = currentDarkness;
        if (transitionTicks <= 0) {
            currentShake = targetShake;
            currentDarkness = targetDarkness;
            return;
        }

        transitionAge++;
        float linear = Math.min(1.0F, transitionAge / (float) transitionTicks);
        float smooth = linear * linear * (3.0F - 2.0F * linear);
        currentShake = MathHelper.lerp(smooth, shakeStart, targetShake);
        currentDarkness = MathHelper.lerp(smooth, darknessStart, targetDarkness);
        if (linear >= 1.0F) {
            transitionTicks = 0;
            transitionAge = 0;
            currentShake = targetShake;
            currentDarkness = targetDarkness;
        }
    }

    public static float shakeDegrees(float tickDelta) {
        return interpolated(previousShake, currentShake, tickDelta) * MAX_SHAKE_DEGREES;
    }

    public static int darknessArgb(float tickDelta) {
        float strength = interpolated(previousDarkness, currentDarkness, tickDelta);
        int alpha = MathHelper.clamp(Math.round(strength * MAX_DARK_ALPHA), 0, MAX_DARK_ALPHA);
        return alpha << 24;
    }

    public static boolean hasDarkness(float tickDelta) {
        return ((darknessArgb(tickDelta) >>> 24) & 0xFF) > 0;
    }

    private static float interpolated(float previous, float current, float tickDelta) {
        return MathHelper.lerp(MathHelper.clamp(tickDelta, 0.0F, 1.0F), previous, current);
    }

    public static void clear() {
        previousShake = 0.0F;
        currentShake = 0.0F;
        shakeStart = 0.0F;
        targetShake = 0.0F;
        previousDarkness = 0.0F;
        currentDarkness = 0.0F;
        darknessStart = 0.0F;
        targetDarkness = 0.0F;
        transitionAge = 0;
        transitionTicks = 0;
    }
}
