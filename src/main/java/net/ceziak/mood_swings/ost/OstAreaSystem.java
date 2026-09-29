package net.ceziak.mood_swings.ost;

import net.ceziak.mood_swings.config.WorldDataStore;
import net.ceziak.mood_swings.network.OstAreaStatePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Selects the audible OST region per player while synchronizing a persistent pool
 * containing every unique OST sound used in the player's current dimension.
 */
public final class OstAreaSystem {
    private static final int CHECK_INTERVAL_TICKS = 5;
    private static final Map<UUID, ActiveState> ACTIVE = new HashMap<>();
    private static final Comparator<Map.Entry<String, OstArea>> AREA_ORDER = Comparator
            .<Map.Entry<String, OstArea>>comparingInt(entry -> entry.getValue().priority()).reversed()
            .thenComparingLong(entry -> entry.getValue().blockVolume())
            .thenComparing(Map.Entry::getKey);
    private static int tickCounter;

    private OstAreaSystem() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(OstAreaSystem::tick);
    }

    private static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        refreshAll(server);
    }

    public static void refreshAll(MinecraftServer server) {
        if (server == null) {
            return;
        }

        Set<UUID> online = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            online.add(player.getUuid());
            updatePlayer(player);
        }
        ACTIVE.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    private static void updatePlayer(ServerPlayerEntity player) {
        ActiveState next = select(player);
        ActiveState previous = ACTIVE.get(player.getUuid());
        if (next.equals(previous) || (next.isSilent() && previous == null)) {
            return;
        }

        if (next.isSilent()) {
            if (send(player, OstAreaStatePayload.stop())) {
                ACTIVE.remove(player.getUuid());
            }
        } else {
            int effectFadeTicks = next.effectFadeTicks() > 0
                    ? next.effectFadeTicks()
                    : previous == null ? 0 : previous.effectFadeTicks();
            OstAreaStatePayload payload = new OstAreaStatePayload(
                    next.dimension(), next.tracks(), next.shakeIntensity(), next.darkness(), effectFadeTicks
            );
            if (send(player, payload)) {
                ACTIVE.put(player.getUuid(), next);
            }
        }
    }

    private static ActiveState select(ServerPlayerEntity player) {
        if (!WorldDataStore.areOstAreasEnabled() || !player.isAlive() || player.isSpectator()) {
            return ActiveState.SILENT;
        }

        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        List<Map.Entry<String, OstArea>> dimensionAreas = OstAreaStore.all().entrySet().stream()
                .filter(entry -> dimension.equals(entry.getValue().dimension()))
                .toList();
        if (dimensionAreas.isEmpty()) {
            return ActiveState.SILENT;
        }

        Map.Entry<String, OstArea> audible = dimensionAreas.stream()
                .filter(entry -> entry.getValue().contains(player))
                .sorted(AREA_ORDER)
                .findFirst()
                .orElse(null);

        Map<String, Map.Entry<String, OstArea>> representativeBySound = new TreeMap<>();
        for (Map.Entry<String, OstArea> entry : dimensionAreas) {
            representativeBySound.merge(
                    entry.getValue().soundId(),
                    entry,
                    (left, right) -> AREA_ORDER.compare(left, right) <= 0 ? left : right
            );
        }

        List<OstAreaStatePayload.TrackState> tracks = new ArrayList<>(representativeBySound.size());
        for (Map.Entry<String, Map.Entry<String, OstArea>> soundEntry : representativeBySound.entrySet()) {
            String soundId = soundEntry.getKey();
            OstArea representative = soundEntry.getValue().getValue();
            boolean isAudible = audible != null && soundId.equals(audible.getValue().soundId());
            OstArea settings = isAudible ? audible.getValue() : representative;
            tracks.add(new OstAreaStatePayload.TrackState(
                    soundId,
                    isAudible ? settings.volume() : 0.0F,
                    settings.fadeTicks()
            ));
        }

        return new ActiveState(
                dimension,
                List.copyOf(tracks),
                audible == null ? 0.0F : audible.getValue().shakeIntensity(),
                audible == null ? 0.0F : audible.getValue().darkness(),
                audible == null ? 0 : audible.getValue().fadeTicks()
        );
    }

    private static boolean send(ServerPlayerEntity player, OstAreaStatePayload payload) {
        if (!ServerPlayNetworking.canSend(player, OstAreaStatePayload.ID)) {
            return false;
        }
        ServerPlayNetworking.send(player, payload);
        return true;
    }

    public static void stopAll(MinecraftServer server) {
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                send(player, OstAreaStatePayload.stop());
            }
        }
        ACTIVE.clear();
    }

    public static void clear() {
        ACTIVE.clear();
        tickCounter = 0;
    }

    private record ActiveState(
            String dimension,
            List<OstAreaStatePayload.TrackState> tracks,
            float shakeIntensity,
            float darkness,
            int effectFadeTicks
    ) {
        private static final ActiveState SILENT = new ActiveState("", List.of(), 0.0F, 0.0F, 0);

        private ActiveState {
            dimension = dimension == null ? "" : dimension;
            tracks = tracks == null ? List.of() : List.copyOf(tracks);
            shakeIntensity = Math.clamp(shakeIntensity, 0.0F, 1.0F);
            darkness = Math.clamp(darkness, 0.0F, 1.0F);
            effectFadeTicks = Math.max(0, effectFadeTicks);
        }

        private boolean isSilent() {
            return dimension.isBlank() || tracks.isEmpty();
        }
    }
}
