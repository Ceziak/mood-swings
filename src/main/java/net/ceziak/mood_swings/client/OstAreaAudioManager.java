package net.ceziak.mood_swings.client;

import net.ceziak.mood_swings.MoodSwings;
import net.ceziak.mood_swings.network.OstAreaStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-side synchronized OST pool.
 *
 * Every unique track used by the current dimension remains alive while muted, so moving between
 * regions changes only gain and never restarts the soundtrack. The manager also survives short
 * server-side silent gaps and rebuilds the complete synchronized pool if Minecraft's sound engine
 * drops a streaming source or reloads underneath us.
 */
public final class OstAreaAudioManager {
    /** Keep streams alive across short round resets without holding them forever after a real stop. */
    private static final int SILENT_HOLD_TICKS = 20 * 10;
    /** Streaming OGGs are asynchronous, so do not judge a newly started pool immediately. */
    private static final int STARTUP_GRACE_TICKS = 20 * 5;
    /** Require a persistent failure instead of rebuilding for a one-tick SoundManager wobble. */
    private static final int MISSING_CONFIRM_TICKS = 20;
    /** Give OpenAL time to release old streaming sources before requesting replacements. */
    private static final int REBUILD_DELAY_TICKS = 10;
    /** Avoid a tight retry loop if another audio mod or the output device is temporarily unhappy. */
    private static final int MIN_REBUILD_INTERVAL_TICKS = 20 * 5;

    private static final Map<String, OstAreaSoundInstance> INSTANCES = new HashMap<>();
    private static final Map<String, OstAreaStatePayload.TrackState> DESIRED_TRACKS =
            new LinkedHashMap<>();

    private static String dimension = "";
    private static int clientTicks;
    private static int startupGraceUntil;
    private static int silentHoldUntil = -1;
    private static int pendingStartAt = -1;
    private static int nextRebuildAllowedAt;
    private static int missingTicks;

    private OstAreaAudioManager() {
    }

    public static void handle(OstAreaStatePayload payload) {
        MinecraftClient.getInstance().execute(() -> apply(payload));
    }

    private static void apply(OstAreaStatePayload payload) {
        OstAreaClientEffects.setTarget(payload.shakeIntensity(), payload.darkness(), payload.effectFadeTicks());

        if (payload.dimension().isBlank() || payload.tracks().isEmpty()) {
            beginSilentHold();
            return;
        }

        LinkedHashMap<String, OstAreaStatePayload.TrackState> nextTracks = new LinkedHashMap<>();
        for (OstAreaStatePayload.TrackState track : payload.tracks()) {
            Identifier soundId = Identifier.tryParse(track.soundId());
            if (soundId == null) {
                MoodSwings.LOGGER.warn("Ignoring invalid OST area sound identifier '{}'", track.soundId());
                continue;
            }
            nextTracks.put(soundId.toString(), track);
        }

        if (nextTracks.isEmpty()) {
            beginSilentHold();
            return;
        }

        boolean dimensionChanged = !dimension.isEmpty() && !dimension.equals(payload.dimension());
        boolean soundSetChanged = !DESIRED_TRACKS.keySet().equals(nextTracks.keySet());

        dimension = payload.dimension();
        silentHoldUntil = -1;
        DESIRED_TRACKS.clear();
        DESIRED_TRACKS.putAll(nextTracks);

        if (dimensionChanged || soundSetChanged || INSTANCES.isEmpty()) {
            requestSynchronizedRebuild(dimensionChanged || !INSTANCES.isEmpty());
            return;
        }

        updateVolumes();
    }

    /**
     * A train reset can briefly report no active OST state and then restore the same dimension.
     * Muting the existing pool for ten seconds keeps the playback cursors alive and avoids a rapid
     * OpenAL stop/start cycle. A real disconnect still calls {@link #clear()} immediately.
     */
    private static void beginSilentHold() {
        OstAreaClientEffects.setTarget(0.0F, 0.0F, 0);
        if (INSTANCES.isEmpty() || DESIRED_TRACKS.isEmpty()) {
            clearInternal(MinecraftClient.getInstance(), true);
            return;
        }

        silentHoldUntil = clientTicks + SILENT_HOLD_TICKS;
        pendingStartAt = -1;
        missingTicks = 0;
        for (Map.Entry<String, OstAreaSoundInstance> entry : INSTANCES.entrySet()) {
            OstAreaStatePayload.TrackState state = DESIRED_TRACKS.get(entry.getKey());
            int fadeTicks = state == null ? 0 : state.fadeTicks();
            entry.getValue().setTargetVolume(0.0F, fadeTicks);
        }
    }

    private static void updateVolumes() {
        for (Map.Entry<String, OstAreaStatePayload.TrackState> entry : DESIRED_TRACKS.entrySet()) {
            OstAreaSoundInstance instance = INSTANCES.get(entry.getKey());
            if (instance != null && !instance.isDone()) {
                OstAreaStatePayload.TrackState state = entry.getValue();
                instance.setTargetVolume(state.volume(), state.fadeTicks());
            }
        }
    }

    private static void requestSynchronizedRebuild(boolean delayForSourceRelease) {
        MinecraftClient client = MinecraftClient.getInstance();
        stopInstances(client);
        pendingStartAt = clientTicks + (delayForSourceRelease ? REBUILD_DELAY_TICKS : 0);
        startupGraceUntil = pendingStartAt + STARTUP_GRACE_TICKS;
        missingTicks = 0;
    }

    private static void startSynchronizedPool(MinecraftClient client) {
        if (DESIRED_TRACKS.isEmpty() || dimension.isBlank()) {
            pendingStartAt = -1;
            return;
        }

        SoundManager soundManager = client.getSoundManager();
        for (Map.Entry<String, OstAreaStatePayload.TrackState> entry : DESIRED_TRACKS.entrySet()) {
            Identifier soundId = Identifier.tryParse(entry.getKey());
            if (soundId == null) {
                continue;
            }

            OstAreaStatePayload.TrackState state = entry.getValue();
            OstAreaSoundInstance instance = new OstAreaSoundInstance(soundId);
            instance.setTargetVolume(state.volume(), state.fadeTicks());
            INSTANCES.put(entry.getKey(), instance);
            soundManager.play(instance);
        }

        pendingStartAt = -1;
        startupGraceUntil = clientTicks + STARTUP_GRACE_TICKS;
        nextRebuildAllowedAt = clientTicks + MIN_REBUILD_INTERVAL_TICKS;
        missingTicks = 0;
    }

    public static void tick(MinecraftClient client) {
        clientTicks++;

        if (silentHoldUntil >= 0 && clientTicks >= silentHoldUntil) {
            clearInternal(client, true);
            return;
        }

        if (pendingStartAt >= 0) {
            if (clientTicks >= pendingStartAt) {
                startSynchronizedPool(client);
            }
            return;
        }

        Iterator<Map.Entry<String, OstAreaSoundInstance>> iterator = INSTANCES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, OstAreaSoundInstance> entry = iterator.next();
            if (entry.getValue().isDone()) {
                client.getSoundManager().stop(entry.getValue());
                iterator.remove();
            }
        }

        if (DESIRED_TRACKS.isEmpty() || INSTANCES.isEmpty() || silentHoldUntil >= 0
                || clientTicks < startupGraceUntil) {
            return;
        }

        SoundManager soundManager = client.getSoundManager();
        Set<String> expected = DESIRED_TRACKS.keySet();
        boolean missing = !INSTANCES.keySet().containsAll(expected);
        if (!missing) {
            for (String key : expected) {
                OstAreaSoundInstance instance = INSTANCES.get(key);
                if (instance == null || instance.isDone() || !soundManager.isPlaying(instance)) {
                    missing = true;
                    break;
                }
            }
        }

        if (!missing) {
            missingTicks = 0;
            return;
        }

        missingTicks++;
        if (missingTicks < MISSING_CONFIRM_TICKS || clientTicks < nextRebuildAllowedAt) {
            return;
        }

        MoodSwings.LOGGER.warn(
                "An OST stream stopped or failed to allocate; rebuilding the synchronized pool for dimension {}",
                dimension
        );
        requestSynchronizedRebuild(true);
    }

    private static void stopInstances(MinecraftClient client) {
        for (OstAreaSoundInstance instance : INSTANCES.values()) {
            instance.stopImmediately();
            client.getSoundManager().stop(instance);
        }
        INSTANCES.clear();
    }

    private static void clearInternal(MinecraftClient client, boolean clearDesiredState) {
        stopInstances(client);
        if (clearDesiredState) {
            DESIRED_TRACKS.clear();
            dimension = "";
        }
        silentHoldUntil = -1;
        pendingStartAt = -1;
        startupGraceUntil = 0;
        nextRebuildAllowedAt = 0;
        missingTicks = 0;
    }

    public static void clear() {
        clearInternal(MinecraftClient.getInstance(), true);
        OstAreaClientEffects.clear();
        clientTicks = 0;
    }
}
