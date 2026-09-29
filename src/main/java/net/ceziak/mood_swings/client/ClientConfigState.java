package net.ceziak.mood_swings.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.ceziak.mood_swings.MoodSwings;
import net.ceziak.mood_swings.lockdown.LockdownSystem;
import net.ceziak.mood_swings.network.ConfigActionPayload;
import net.ceziak.mood_swings.network.ConfigSnapshot;
import net.ceziak.mood_swings.network.ConfigSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Client cache for server-owned per-save tasks, areas, and feature settings. */
public final class ClientConfigState {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static volatile ConfigSnapshot snapshot = ConfigSnapshot.empty();

    private ClientConfigState() {
    }

    public static void handle(ConfigSnapshotPayload payload) {
        MinecraftClient.getInstance().execute(() -> {
            try {
                ConfigSnapshot parsed = GSON.fromJson(payload.json(), ConfigSnapshot.class);
                snapshot = parsed == null ? ConfigSnapshot.empty() : parsed;
                LockdownSystem.setShopEntryEnabled(snapshot.lockdownEnabled());
            } catch (RuntimeException exception) {
                MoodSwings.LOGGER.error("Could not read Mood Swings config snapshot", exception);
                snapshot = ConfigSnapshot.empty();
                LockdownSystem.setShopEntryEnabled(false);
                OstAreaAudioManager.clear();
            }
        });
    }

    public static ConfigSnapshot snapshot() {
        return snapshot;
    }

    public static void clear() {
        snapshot = ConfigSnapshot.empty();
        LockdownSystem.setShopEntryEnabled(false);
        OstAreaAudioManager.clear();
    }

    public static void requestRefresh() {
        if (ClientPlayNetworking.canSend(ConfigActionPayload.ID)) {
            ClientPlayNetworking.send(new ConfigActionPayload(
                    ConfigActionPayload.REQUEST_SNAPSHOT, "", ""
            ));
        }
    }

    public static void send(ConfigActionPayload payload) {
        if (ClientPlayNetworking.canSend(ConfigActionPayload.ID)) {
            ClientPlayNetworking.send(payload);
        }
    }
}
