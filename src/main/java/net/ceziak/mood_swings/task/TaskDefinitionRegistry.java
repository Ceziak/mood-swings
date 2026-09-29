package net.ceziak.mood_swings.task;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.ceziak.mood_swings.MoodSwings;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads task definitions from server-data resources under mood_swings/tasks. */
public final class TaskDefinitionRegistry {
    public static final String RESOURCE_DIRECTORY = "mood_swings/tasks";
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static volatile Map<Identifier, TaskDefinition> baseDefinitions = Map.of();
    private static volatile Map<Identifier, TaskDefinition> definitions = Map.of();

    private TaskDefinitionRegistry() {
    }

    public static synchronized int reload(ResourceManager manager) {
        Map<Identifier, TaskDefinition> loaded = new LinkedHashMap<>();
        Map<Identifier, Resource> resources = manager.findResources(
                RESOURCE_DIRECTORY,
                id -> id.getPath().endsWith(".json")
        );

        List<Map.Entry<Identifier, Resource>> sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)));

        for (Map.Entry<Identifier, Resource> entry : sorted) {
            Identifier resourceId = entry.getKey();
            try (Reader reader = entry.getValue().getReader()) {
                Identifier taskId = taskIdFromResource(resourceId);
                TaskDefinition definition = GSON.fromJson(reader, TaskDefinition.class);
                if (definition == null) {
                    throw new IllegalArgumentException("JSON contains no task object");
                }
                definition.validate(taskId);
                loaded.put(taskId, definition);
            } catch (Exception exception) {
                MoodSwings.LOGGER.error("Could not load Mood Swings task definition {}", resourceId, exception);
            }
        }

        baseDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        rebuildEffective();
        MoodSwings.LOGGER.info("Loaded {} Mood Swings JSON task definitions.", loaded.size());
        return loaded.size();
    }


    public static synchronized void rebuildEffective() {
        Map<Identifier, TaskDefinition> effective = new LinkedHashMap<>();
        for (Map.Entry<Identifier, TaskDefinition> entry : baseDefinitions.entrySet()) {
            Identifier id = entry.getKey();
            TaskDefinition selected = entry.getValue();
            String overrideJson = WorldDataStore.taskOverride(id.toString()).orElse(null);
            if (overrideJson != null && !overrideJson.isBlank()) {
                try {
                    TaskDefinition override = GSON.fromJson(overrideJson, TaskDefinition.class);
                    if (override == null) {
                        throw new IllegalArgumentException("Override contains no task object");
                    }
                    override.validate(id);
                    selected = override;
                } catch (Exception exception) {
                    MoodSwings.LOGGER.error("Ignoring malformed per-save override for task {}", id, exception);
                }
            }
            effective.put(id, selected);
        }
        definitions = Collections.unmodifiableMap(effective);
    }

    public static Map<Identifier, TaskDefinition> baseAll() {
        return baseDefinitions;
    }

    public static Optional<TaskDefinition> getBase(Identifier id) {
        return Optional.ofNullable(baseDefinitions.get(id));
    }

    private static Identifier taskIdFromResource(Identifier resourceId) {
        String prefix = RESOURCE_DIRECTORY + "/";
        String path = resourceId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            throw new IllegalArgumentException("Unexpected task resource path: " + resourceId);
        }
        String taskPath = path.substring(prefix.length(), path.length() - ".json".length());
        return Identifier.of(resourceId.getNamespace(), taskPath);
    }

    public static Map<Identifier, TaskDefinition> all() {
        return definitions;
    }

    public static List<String> ids() {
        return definitions.keySet().stream().map(Identifier::toString).sorted().toList();
    }

    public static Optional<Map.Entry<Identifier, TaskDefinition>> resolve(String input) {
        Identifier id = parseTaskId(input);
        if (id == null) {
            return Optional.empty();
        }
        TaskDefinition definition = definitions.get(id);
        return definition == null ? Optional.empty() : Optional.of(Map.entry(id, definition));
    }

    public static Optional<TaskDefinition> get(Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public static Identifier parseTaskId(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim().toLowerCase(java.util.Locale.ROOT);
        return trimmed.contains(":")
                ? Identifier.tryParse(trimmed)
                : Identifier.tryParse(MoodSwings.MOD_ID, trimmed);
    }

    public static String toJson(TaskDefinition definition) {
        return GSON.toJson(definition);
    }

    public static TaskDefinition fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(json, TaskDefinition.class);
        } catch (RuntimeException exception) {
            MoodSwings.LOGGER.warn("Could not decode a saved Mood Swings task snapshot.", exception);
            return null;
        }
    }
}
