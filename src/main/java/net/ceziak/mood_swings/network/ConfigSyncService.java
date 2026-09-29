package net.ceziak.mood_swings.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.MoodSwings;
import net.ceziak.mood_swings.access.PlayerMoodComponentAccess;
import net.ceziak.mood_swings.area.AreaStore;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.ceziak.mood_swings.lockdown.LockdownAreaStore;
import net.ceziak.mood_swings.lockdown.LockdownSystem;
import net.ceziak.mood_swings.ost.OstArea;
import net.ceziak.mood_swings.ost.OstAreaStore;
import net.ceziak.mood_swings.ost.OstAreaSystem;
import net.ceziak.mood_swings.task.CustomTrainTask;
import net.ceziak.mood_swings.task.TaskConditionEvaluator;
import net.ceziak.mood_swings.task.TaskDefinition;
import net.ceziak.mood_swings.task.TaskDefinitionRegistry;
import net.ceziak.mood_swings.task.TaskRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server authority for the client-facing config menu. */
public final class ConfigSyncService {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private ConfigSyncService() {
    }

    public static void sendSnapshot(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ConfigSnapshotPayload.ID)) {
            return;
        }
        ConfigSnapshot snapshot = createSnapshot(canEdit(player));
        ServerPlayNetworking.send(player, new ConfigSnapshotPayload(GSON.toJson(snapshot)));
    }

    public static void broadcastSnapshots(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendSnapshot(player);
        }
    }

    public static ConfigSnapshot createSnapshot(boolean canEdit) {
        Map<String, ConfigSnapshot.TaskSnapshot> tasks = new LinkedHashMap<>();
        for (String nativeId : TaskRegistry.NATIVE_IDS) {
            tasks.put(nativeId, new ConfigSnapshot.TaskSnapshot(
                    true,
                    WorldDataStore.isTaskEnabled(nativeId),
                    false,
                    "{}",
                    "{}"
            ));
        }

        for (Map.Entry<Identifier, TaskDefinition> entry : TaskDefinitionRegistry.baseAll().entrySet()) {
            Identifier id = entry.getKey();
            TaskDefinition effective = TaskDefinitionRegistry.get(id).orElse(entry.getValue());
            tasks.put(id.toString(), new ConfigSnapshot.TaskSnapshot(
                    false,
                    WorldDataStore.isTaskEnabled(id.toString()),
                    WorldDataStore.taskOverride(id.toString()).isPresent(),
                    TaskDefinitionRegistry.toJson(effective),
                    TaskDefinitionRegistry.toJson(entry.getValue())
            ));
        }
        return new ConfigSnapshot(
                canEdit,
                WorldDataStore.isLockdownEnabled(),
                WorldDataStore.areOstAreasEnabled(),
                tasks,
                AreaStore.all(),
                LockdownAreaStore.all(),
                OstAreaStore.all()
        );
    }

    public static void handle(ServerPlayerEntity player, MinecraftServer server, ConfigActionPayload payload) {
        if (payload.action() == ConfigActionPayload.REQUEST_SNAPSHOT) {
            sendSnapshot(player);
            return;
        }

        if (!canEdit(player)) {
            player.sendMessage(Text.literal("You need operator permission to edit Mood Swings save settings."), false);
            sendSnapshot(player);
            return;
        }

        try {
            switch (payload.action()) {
                case ConfigActionPayload.SAVE_TASK -> saveTask(player, payload.key(), payload.value());
                case ConfigActionPayload.RESET_TASK -> resetTask(player, payload.key(), payload.value());
                case ConfigActionPayload.RENAME_AREA -> renameArea(player, payload.key(), payload.value());
                case ConfigActionPayload.DELETE_AREA -> deleteArea(player, payload.key());
                case ConfigActionPayload.RELOAD_TASKS -> {
                    TaskDefinitionRegistry.reload(server.getResourceManager());
                    removeUnavailableTasks(server);
                    player.sendMessage(Text.literal("Reloaded Mood Swings task definitions."), false);
                }
                case ConfigActionPayload.SET_LOCKDOWN_ENABLED -> setLockdownEnabled(payload.value());
                case ConfigActionPayload.SET_OST_AREAS_ENABLED -> setOstAreasEnabled(server, payload.value());
                case ConfigActionPayload.SAVE_OST_AREA -> saveOstArea(server, payload.key(), payload.value());
                case ConfigActionPayload.DELETE_OST_AREA -> deleteOstArea(server, payload.key());
                case ConfigActionPayload.DELETE_LOCKDOWN_AREA -> deleteLockdownArea(payload.key());
                default -> MoodSwings.LOGGER.warn("Ignoring unknown config action {}", payload.action());
            }
        } catch (Exception exception) {
            MoodSwings.LOGGER.error("Could not apply Mood Swings config action", exception);
            player.sendMessage(Text.literal("Mood Swings could not save that change: " + exception.getMessage()), false);
        }
        broadcastSnapshots(server);
    }

    private static boolean canEdit(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        return player.hasPermissionLevel(2) || (server != null && server.isSingleplayer());
    }

    private static void saveTask(ServerPlayerEntity player, String taskId, String payloadJson) {
        TaskSaveRequest request = GSON.fromJson(payloadJson, TaskSaveRequest.class);
        if (request == null) {
            throw new IllegalArgumentException("Missing task settings");
        }
        String canonical = TaskRegistry.canonicalize(taskId);
        if (!TaskRegistry.isKnown(canonical)) {
            throw new IllegalArgumentException("Unknown task " + taskId);
        }

        setEnabled(canonical, request.enabled());
        if (!TaskRegistry.NATIVE_IDS.contains(canonical)
                && request.definitionJson() != null
                && !request.definitionJson().isBlank()) {
            Identifier id = TaskDefinitionRegistry.parseTaskId(canonical);
            TaskDefinition parsed = TaskDefinitionRegistry.fromJson(request.definitionJson());
            if (id == null || parsed == null) {
                throw new IllegalArgumentException("Invalid task JSON");
            }
            parsed.validate(id);
            WorldDataStore.setTaskOverride(canonical, TaskDefinitionRegistry.toJson(parsed));
            TaskDefinitionRegistry.rebuildEffective();
        }
        removeTaskFromPlayers(player.getServer(), canonical);
    }

    private static void resetTask(ServerPlayerEntity player, String taskId, String enabledValue) {
        String canonical = TaskRegistry.canonicalize(taskId);
        if (!TaskRegistry.isKnown(canonical)) {
            throw new IllegalArgumentException("Unknown task " + taskId);
        }
        setEnabled(canonical, Boolean.parseBoolean(enabledValue));
        if (!TaskRegistry.NATIVE_IDS.contains(canonical)) {
            WorldDataStore.clearTaskOverride(canonical);
            TaskDefinitionRegistry.rebuildEffective();
        }
        removeTaskFromPlayers(player.getServer(), canonical);
    }

    private static void setEnabled(String taskId, boolean enabled) {
        if (enabled) {
            WorldDataStore.enableTask(taskId);
        } else {
            WorldDataStore.disableTask(taskId);
        }
    }

    private static void setLockdownEnabled(String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Invalid Lockdown enabled value");
        }
        boolean enabled = Boolean.parseBoolean(value);
        WorldDataStore.setLockdownEnabled(enabled);
        LockdownSystem.setShopEntryEnabled(enabled);
    }

    private static void setOstAreasEnabled(MinecraftServer server, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Invalid OST areas enabled value");
        }
        boolean enabled = Boolean.parseBoolean(value);
        WorldDataStore.setOstAreasEnabled(enabled);
        if (enabled) {
            OstAreaSystem.refreshAll(server);
        } else {
            OstAreaSystem.stopAll(server);
        }
    }

    private static void saveOstArea(MinecraftServer server, String areaId, String json) {
        String normalized = WorldDataStore.normalize(areaId);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("OST area ID cannot be empty");
        }
        OstArea current = OstAreaStore.get(normalized).orElse(null);
        if (current == null) {
            throw new IllegalArgumentException("Unknown OST area " + areaId);
        }
        OstArea parsed = GSON.fromJson(json, OstArea.class);
        if (parsed == null) {
            throw new IllegalArgumentException("Missing OST area settings");
        }
        OstArea updated = new OstArea(
                current.dimension(),
                current.minX(), current.minY(), current.minZ(),
                current.maxX(), current.maxY(), current.maxZ(),
                current.displayName(),
                parsed.soundId(), parsed.volume(), parsed.priority(), parsed.fadeSeconds(),
                parsed.shakeIntensity(), parsed.darkness()
        ).validated();
        OstAreaStore.put(normalized, updated);
        OstAreaSystem.refreshAll(server);
    }

    private static void deleteLockdownArea(String areaId) {
        String normalized = WorldDataStore.normalize(areaId);
        if (!LockdownAreaStore.remove(normalized)) {
            throw new IllegalArgumentException("Unknown Lockdown area " + areaId);
        }
    }

    private static void deleteOstArea(MinecraftServer server, String areaId) {
        String normalized = WorldDataStore.normalize(areaId);
        if (!OstAreaStore.remove(normalized)) {
            throw new IllegalArgumentException("Unknown OST area " + areaId);
        }
        OstAreaSystem.refreshAll(server);
    }

    private static void renameArea(ServerPlayerEntity player, String oldName, String newName) {
        String oldId = WorldDataStore.normalize(oldName);
        String newId = WorldDataStore.normalize(newName);
        if (newId.isBlank()) {
            throw new IllegalArgumentException("Area name cannot be empty");
        }
        if (!WorldDataStore.renameArea(oldId, newName)) {
            throw new IllegalArgumentException("Area could not be renamed. The new name may already exist.");
        }
        rewriteTaskAreaReferences(oldId, newId);
        removeUnavailableTasks(player.getServer());
        player.sendMessage(Text.literal("Renamed area '" + oldId + "' to '" + newId + "'."), false);
    }

    private static void deleteArea(ServerPlayerEntity player, String name) {
        String normalized = WorldDataStore.normalize(name);
        if (!WorldDataStore.removeArea(normalized)) {
            throw new IllegalArgumentException("No area called " + name);
        }
        removeUnavailableTasks(player.getServer());
        player.sendMessage(Text.literal("Deleted area '" + normalized + "'."), false);
    }

    private static void rewriteTaskAreaReferences(String oldId, String newId) {
        for (Map.Entry<Identifier, TaskDefinition> entry : TaskDefinitionRegistry.all().entrySet()) {
            JsonObject root = JsonParser.parseString(TaskDefinitionRegistry.toJson(entry.getValue())).getAsJsonObject();
            if (!root.has("condition") || !root.get("condition").isJsonObject()) {
                continue;
            }
            JsonObject condition = root.getAsJsonObject("condition");
            if (!condition.has("area")) {
                continue;
            }
            String current = WorldDataStore.normalize(condition.get("area").getAsString());
            if (!oldId.equals(current)) {
                continue;
            }
            condition.addProperty("area", newId);
            String json = GSON.toJson(root);
            TaskDefinition parsed = TaskDefinitionRegistry.fromJson(json);
            if (parsed != null) {
                parsed.validate(entry.getKey());
                WorldDataStore.setTaskOverride(entry.getKey().toString(), TaskDefinitionRegistry.toJson(parsed));
            }
        }
        TaskDefinitionRegistry.rebuildEffective();
    }

    private static void removeTaskFromPlayers(MinecraftServer server, String taskId) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            PlayerMoodComponent component = PlayerMoodComponent.KEY.get(online);
            boolean removed = component.tasks.entrySet().removeIf(entry -> TaskRegistry.idOf(entry.getValue()).equals(taskId));
            if (removed) {
                ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
                component.sync();
            }
        }
    }

    private static void removeUnavailableTasks(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            PlayerMoodComponent component = PlayerMoodComponent.KEY.get(online);
            boolean removed = component.tasks.entrySet().removeIf(entry -> {
                if (!(entry.getValue() instanceof CustomTrainTask custom)) {
                    return !WorldDataStore.isTaskEnabled(TaskRegistry.idOf(entry.getValue()));
                }
                TaskDefinition current = TaskDefinitionRegistry.get(custom.taskId()).orElse(null);
                return current == null
                        || !WorldDataStore.isTaskEnabled(custom.taskId().toString())
                        || !TaskConditionEvaluator.isTaskAvailable(online, current);
            });
            if (removed) {
                ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
                component.sync();
            }
        }
    }

    private record TaskSaveRequest(boolean enabled, String definitionJson) {
    }
}
