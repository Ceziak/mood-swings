package net.ceziak.mood_swings.client;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.task.CustomTrainTask;
import net.ceziak.mood_swings.task.MurderTaskRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Tracks escalating client-side penalties while the murder task remains active. */
public final class MurderTaskClientEffects {
    public static final int INSTINCT_BLOCK_TICKS = MurderTaskRules.OVERDUE_TICKS;

    private static final int HEARTBEAT_START_TICKS = 30 * 20;
    private static final int PRESSURE_CAP_TICKS = INSTINCT_BLOCK_TICKS;
    private static final float MAX_SHAKE_DEGREES = 4.0F;
    private static final float SCROLL_PIXELS_PER_TICK = 0.85F;

    private static final int MIN_HOTBAR_DELAY_TICKS = 18;
    private static final int MAX_HOTBAR_DELAY_TICKS = 52;

    private static final List<String> MURDER_PHRASES = List.of(
            "REMOVE THE NOISE.",
            "MAKE THEM STOP MOVING.",
            "THEY SHOULD NOT BE HERE.",
            "YOU'LL FEEL BETTER AFTER.",
            "WHY HAVEN'T YOU DONE IT YET?",
            "SOMEONE HAS TO GO.",
            "QUIET THEM.",
            "NOW."
    );

    private static boolean murderTaskActive;
    private static int activeTicks;
    private static int effectClockTicks;
    private static int nextHeartbeatTick;
    private static int nextHotbarShuffleTick;

    private static int recoveryTicksRemaining;
    private static float recoveryStartShake;
    private static float recoveryStartVignette;
    private static float recoveryStartHeartbeatPressure;

    private static Text scrollingText = Text.empty();

    private MurderTaskClientEffects() {
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            clear();
            return;
        }

        CustomTrainTask murderTask = findActiveMurderTask(client);
        if (murderTask != null) {
            tickActiveTask(client, murderTask);
            return;
        }

        if (murderTaskActive) {
            beginRecovery();
        }
        murderTaskActive = false;
        activeTicks = 0;
        nextHotbarShuffleTick = 0;

        if (recoveryTicksRemaining > 0) {
            effectClockTicks++;
            tickHeartbeat(client);
            recoveryTicksRemaining--;
            if (recoveryTicksRemaining <= 0) {
                clearRecoveredState();
            }
            return;
        }

        clearRecoveredState();
    }

    private static void tickActiveTask(MinecraftClient client, CustomTrainTask murderTask) {
        if (!murderTaskActive) {
            beginActiveTask(murderTask);
        }

        murderTaskActive = true;
        recoveryTicksRemaining = 0;

        // Keep counting locally for smooth client effects, but never fall behind a
        // restored server snapshot after reconnecting or changing dimensions.
        activeTicks = Math.max(activeTicks + 1, murderTask.elapsedTicks());
        effectClockTicks = activeTicks;

        if (isControlPenaltyActive()) {
            client.player.setSprinting(false);
            tickHotbarChaos(client);
        } else {
            nextHotbarShuffleTick = 0;
        }

        tickHeartbeat(client);
    }

    private static void beginActiveTask(CustomTrainTask murderTask) {
        murderTaskActive = true;
        activeTicks = Math.max(0, murderTask.elapsedTicks());
        effectClockTicks = activeTicks;
        recoveryTicksRemaining = 0;
        recoveryStartShake = 0.0F;
        recoveryStartVignette = 0.0F;
        recoveryStartHeartbeatPressure = 0.0F;
        rebuildScrollingText();

        nextHeartbeatTick = activeTicks < HEARTBEAT_START_TICKS
                ? HEARTBEAT_START_TICKS
                : activeTicks + 4;
        nextHotbarShuffleTick = activeTicks >= INSTINCT_BLOCK_TICKS
                ? activeTicks + randomHotbarDelay()
                : 0;
    }

    private static void beginRecovery() {
        recoveryStartShake = activeShakeStrength(0.0F);
        recoveryStartVignette = activeVignetteStrength(0.0F);
        recoveryStartHeartbeatPressure = activeHeartbeatPressure(0.0F);
        recoveryTicksRemaining = MurderTaskRules.RECOVERY_TICKS;

        // Let a final beat happen soon if the pressure was high, then progressively
        // slow and quiet it as recovery runs out.
        if (recoveryStartHeartbeatPressure > 0.02F) {
            nextHeartbeatTick = Math.min(nextHeartbeatTick, effectClockTicks + 6);
        }
    }

    /** Hard reset used for disconnects and world unloads. */
    public static void clear() {
        murderTaskActive = false;
        activeTicks = 0;
        effectClockTicks = 0;
        nextHeartbeatTick = 0;
        nextHotbarShuffleTick = 0;
        recoveryTicksRemaining = 0;
        recoveryStartShake = 0.0F;
        recoveryStartVignette = 0.0F;
        recoveryStartHeartbeatPressure = 0.0F;
        scrollingText = Text.empty();
    }

    private static void clearRecoveredState() {
        effectClockTicks = 0;
        nextHeartbeatTick = 0;
        recoveryTicksRemaining = 0;
        recoveryStartShake = 0.0F;
        recoveryStartVignette = 0.0F;
        recoveryStartHeartbeatPressure = 0.0F;
        scrollingText = Text.empty();
    }

    public static boolean isInstinctBlocked() {
        return isControlPenaltyActive();
    }

    public static boolean isSprintBlocked() {
        return isControlPenaltyActive();
    }

    private static boolean isControlPenaltyActive() {
        return murderTaskActive && activeTicks >= INSTINCT_BLOCK_TICKS;
    }

    public static Text scrollingText() {
        return scrollingText;
    }

    public static float scrollOffset(float tickDelta) {
        return (activeTicks + Math.max(0.0F, tickDelta)) * SCROLL_PIXELS_PER_TICK;
    }

    public static float shakeStrength(float tickDelta) {
        if (murderTaskActive) {
            return activeShakeStrength(tickDelta);
        }
        if (recoveryTicksRemaining > 0) {
            return recoveryStartShake * recoveryMultiplier(tickDelta);
        }
        return 0.0F;
    }

    public static float vignetteStrength(float tickDelta) {
        if (murderTaskActive) {
            return activeVignetteStrength(tickDelta);
        }
        if (recoveryTicksRemaining > 0) {
            return recoveryStartVignette * recoveryMultiplier(tickDelta);
        }
        return 0.0F;
    }

    private static float activeShakeStrength(float tickDelta) {
        double elapsedSeconds = elapsedActiveTicks(tickDelta) / 20.0D;
        double strength = 0.01D * Math.exp(elapsedSeconds / 20.0D);
        return (float) Math.min(MAX_SHAKE_DEGREES, strength);
    }

    private static float activeVignetteStrength(float tickDelta) {
        float progress = delayedActiveProgress(HEARTBEAT_START_TICKS, tickDelta);
        return exponentialProgress(progress, 3.5F);
    }

    private static void tickHeartbeat(MinecraftClient client) {
        float pressure = heartbeatPressure(0.0F);
        if (pressure <= 0.002F || effectClockTicks < nextHeartbeatTick) {
            return;
        }

        float volume = MathHelper.clamp(pressure * 0.82F, 0.025F, 0.82F);
        float pitch = MathHelper.lerp(pressure, 0.76F, 1.22F);

        client.getSoundManager().play(PositionedSoundInstance.master(
                SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                pitch,
                volume
        ));

        int interval = Math.round(MathHelper.lerp(pressure, 38.0F, 10.0F));
        nextHeartbeatTick = effectClockTicks + Math.max(8, interval);
    }

    private static void tickHotbarChaos(MinecraftClient client) {
        if (nextHotbarShuffleTick <= 0) {
            nextHotbarShuffleTick = activeTicks + randomHotbarDelay();
            return;
        }
        if (activeTicks < nextHotbarShuffleTick) {
            return;
        }

        int currentSlot = client.player.getInventory().selectedSlot;
        int nextSlot = (currentSlot + 1 + ThreadLocalRandom.current().nextInt(8)) % 9;
        client.player.getInventory().selectedSlot = nextSlot;

        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(nextSlot));
        }

        nextHotbarShuffleTick = activeTicks + randomHotbarDelay();
    }

    private static int randomHotbarDelay() {
        return ThreadLocalRandom.current().nextInt(
                MIN_HOTBAR_DELAY_TICKS,
                MAX_HOTBAR_DELAY_TICKS + 1
        );
    }

    private static float heartbeatPressure(float tickDelta) {
        if (murderTaskActive) {
            return activeHeartbeatPressure(tickDelta);
        }
        if (recoveryTicksRemaining > 0) {
            return recoveryStartHeartbeatPressure * recoveryMultiplier(tickDelta);
        }
        return 0.0F;
    }

    private static float activeHeartbeatPressure(float tickDelta) {
        float progress = delayedActiveProgress(HEARTBEAT_START_TICKS, tickDelta);
        return exponentialProgress(progress, 3.0F);
    }

    private static float delayedActiveProgress(int startTick, float tickDelta) {
        float elapsed = elapsedActiveTicks(tickDelta);
        if (elapsed <= startTick) {
            return 0.0F;
        }

        return MathHelper.clamp(
                (elapsed - startTick) / (PRESSURE_CAP_TICKS - (float) startTick),
                0.0F,
                1.0F
        );
    }

    private static float recoveryMultiplier(float tickDelta) {
        float remaining = MathHelper.clamp(
                (recoveryTicksRemaining - Math.max(0.0F, tickDelta))
                        / (float) MurderTaskRules.RECOVERY_TICKS,
                0.0F,
                1.0F
        );
        // Smoothstep keeps the beginning and end of the fade from feeling abrupt.
        return remaining * remaining * (3.0F - 2.0F * remaining);
    }

    private static float exponentialProgress(float linearProgress, float steepness) {
        if (linearProgress <= 0.0F) {
            return 0.0F;
        }
        if (linearProgress >= 1.0F) {
            return 1.0F;
        }

        double numerator = Math.exp(steepness * linearProgress) - 1.0D;
        double denominator = Math.exp(steepness) - 1.0D;
        return (float) (numerator / denominator);
    }

    private static float elapsedActiveTicks(float tickDelta) {
        return Math.min(PRESSURE_CAP_TICKS, activeTicks + Math.max(0.0F, tickDelta));
    }

    private static void rebuildScrollingText() {
        ArrayList<String> shuffled = new ArrayList<>(MURDER_PHRASES);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        scrollingText = Text.literal(String.join(" ", shuffled) + " ");
    }

    private static CustomTrainTask findActiveMurderTask(MinecraftClient client) {
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(client.player);
        return component.tasks.values().stream()
                .filter(CustomTrainTask.class::isInstance)
                .map(CustomTrainTask.class::cast)
                .filter(MurderTaskRules::isMurderTask)
                .findFirst()
                .orElse(null);
    }
}
