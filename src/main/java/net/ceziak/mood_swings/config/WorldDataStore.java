package net.ceziak.mood_swings.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.ceziak.mood_swings.MoodSwings;
import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.ost.OstArea;
import net.ceziak.mood_swings.task.TaskDefinitionRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Per-save task areas, Lockdown areas, OST areas, task overrides, and gameplay feature settings. */
public final class WorldDataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file;
    private static Data data = new Data();

    private WorldDataStore() {
    }

    public static synchronized void load(MinecraftServer server) {
        Path root = server.getSavePath(WorldSavePath.ROOT);
        Path folder = root.resolve(MoodSwings.MOD_ID);
        file = folder.resolve("settings.json");
        data = new Data();

        try {
            Files.createDirectories(folder);
            if (Files.exists(file)) {
                readCurrentFile();
            } else {
                save();
            }
            MoodSwings.LOGGER.info("Loaded Mood Swings save data from {}", file.toAbsolutePath());
        } catch (JsonSyntaxException exception) {
            MoodSwings.LOGGER.error("Mood Swings save data is malformed. Defaults will be used.", exception);
            data = new Data();
        } catch (IOException exception) {
            MoodSwings.LOGGER.error("Could not load Mood Swings save data", exception);
            data = new Data();
        }
    }

    private static void readCurrentFile() throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) {
                data = loaded;
                data.repairNulls();
            }
        }
    }

    public static synchronized void close() {
        save();
        file = null;
        data = new Data();
    }

    public static synchronized Optional<NamedArea> getArea(String name) {
        return Optional.ofNullable(data.areas.get(normalize(name)));
    }

    public static synchronized Map<String, NamedArea> allAreas() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(data.areas));
    }

    public static synchronized void putArea(String name, NamedArea area) {
        data.areas.put(normalize(name), area);
        save();
    }

    public static synchronized boolean removeArea(String name) {
        boolean removed = data.areas.remove(normalize(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static synchronized Optional<NamedArea> getLockdownArea(String name) {
        return Optional.ofNullable(data.lockdownAreas.get(normalize(name)));
    }

    public static synchronized Map<String, NamedArea> allLockdownAreas() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(data.lockdownAreas));
    }

    public static synchronized void putLockdownArea(String name, NamedArea area) {
        data.lockdownAreas.put(normalize(name), area);
        save();
    }

    public static synchronized boolean removeLockdownArea(String name) {
        boolean removed = data.lockdownAreas.remove(normalize(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static synchronized boolean renameArea(String oldName, String newName) {
        String oldId = normalize(oldName);
        String newId = normalize(newName);
        if (oldId.isBlank() || newId.isBlank() || !data.areas.containsKey(oldId) || data.areas.containsKey(newId)) {
            return false;
        }
        NamedArea old = data.areas.remove(oldId);
        NamedArea renamed = new NamedArea(
                old.dimension(), old.minX(), old.minY(), old.minZ(),
                old.maxX(), old.maxY(), old.maxZ(), newName.trim()
        );
        data.areas.put(newId, renamed);
        save();
        return true;
    }


    public static synchronized Optional<OstArea> getOstArea(String name) {
        return Optional.ofNullable(data.ostAreas.get(normalize(name)));
    }

    public static synchronized Map<String, OstArea> allOstAreas() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(data.ostAreas));
    }

    public static synchronized void putOstArea(String name, OstArea area) {
        data.ostAreas.put(normalize(name), area.validated());
        save();
    }

    public static synchronized boolean removeOstArea(String name) {
        boolean removed = data.ostAreas.remove(normalize(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static synchronized boolean areOstAreasEnabled() {
        return Boolean.TRUE.equals(data.ostAreasEnabled);
    }

    public static synchronized boolean setOstAreasEnabled(boolean enabled) {
        boolean changed = !Boolean.valueOf(enabled).equals(data.ostAreasEnabled);
        data.ostAreasEnabled = enabled;
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized Optional<String> taskOverride(String taskId) {
        return Optional.ofNullable(data.taskOverrides.get(canonicalTaskId(taskId)));
    }

    public static synchronized Map<String, String> taskOverrides() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(data.taskOverrides));
    }

    public static synchronized void setTaskOverride(String taskId, String json) {
        data.taskOverrides.put(canonicalTaskId(taskId), json);
        save();
    }

    public static synchronized boolean clearTaskOverride(String taskId) {
        boolean changed = data.taskOverrides.remove(canonicalTaskId(taskId)) != null;
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized boolean isTaskEnabled(String taskId) {
        String canonical = canonicalTaskId(taskId);
        if (data.disabledTasks.contains(canonical)) {
            return false;
        }
        // Backward compatibility with 1.0.0, which stored built-in custom IDs without a namespace.
        Identifier parsed = TaskDefinitionRegistry.parseTaskId(canonical);
        return parsed == null || !data.disabledTasks.contains(parsed.getPath());
    }

    public static synchronized boolean disableTask(String taskId) {
        String canonical = canonicalTaskId(taskId);
        boolean changed = data.disabledTasks.add(canonical);
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized boolean enableTask(String taskId) {
        String canonical = canonicalTaskId(taskId);
        boolean changed = data.disabledTasks.remove(canonical);
        Identifier parsed = TaskDefinitionRegistry.parseTaskId(canonical);
        if (parsed != null) {
            changed |= data.disabledTasks.remove(parsed.getPath());
        }
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized Set<String> disabledTasks() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(data.disabledTasks));
    }

    public static synchronized boolean isLockdownEnabled() {
        return Boolean.TRUE.equals(data.lockdownEnabled);
    }

    public static synchronized boolean setLockdownEnabled(boolean enabled) {
        boolean changed = !Boolean.valueOf(enabled).equals(data.lockdownEnabled);
        data.lockdownEnabled = enabled;
        if (changed) {
            save();
        }
        return changed;
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String canonicalTaskId(String value) {
        String normalized = normalize(value);
        if (java.util.List.of("sleep", "outside", "eat", "drink").contains(normalized)) {
            return normalized;
        }
        Identifier parsed = TaskDefinitionRegistry.parseTaskId(normalized);
        return parsed == null ? normalized : parsed.toString();
    }

    private static synchronized void save() {
        if (file == null) {
            return;
        }

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException exception) {
            MoodSwings.LOGGER.error("Could not save Mood Swings world data", exception);
        }
    }

    private static final class Data {
        private Map<String, NamedArea> areas = new LinkedHashMap<>();
        private Map<String, NamedArea> lockdownAreas = new LinkedHashMap<>();
        private Map<String, OstArea> ostAreas = new LinkedHashMap<>();
        private Set<String> disabledTasks = new LinkedHashSet<>();
        private Map<String, String> taskOverrides = new LinkedHashMap<>();
        private Boolean lockdownEnabled = true;
        private Boolean ostAreasEnabled = true;

        private void repairNulls() {
            if (areas == null) {
                areas = new LinkedHashMap<>();
            }
            if (lockdownAreas == null) {
                lockdownAreas = new LinkedHashMap<>();
            }
            if (ostAreas == null) {
                ostAreas = new LinkedHashMap<>();
            } else {
                ostAreas.entrySet().removeIf(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null
                            || normalize(entry.getKey()).isBlank()) {
                        return true;
                    }
                    try {
                        entry.getValue().validated();
                        return false;
                    } catch (RuntimeException exception) {
                        MoodSwings.LOGGER.warn("Ignoring invalid saved OST area '{}'", entry.getKey(), exception);
                        return true;
                    }
                });
            }
            if (disabledTasks == null) {
                disabledTasks = new LinkedHashSet<>();
            }
            if (taskOverrides == null) {
                taskOverrides = new LinkedHashMap<>();
            }
            if (lockdownEnabled == null) {
                lockdownEnabled = true;
            }
            if (ostAreasEnabled == null) {
                ostAreasEnabled = true;
            }
        }
    }
}
