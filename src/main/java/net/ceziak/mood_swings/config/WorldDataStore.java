package net.ceziak.mood_swings.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.ceziak.mood_swings.MoodSwings;
import net.ceziak.mood_swings.area.NamedArea;
import net.minecraft.server.MinecraftServer;
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

/** Per-save named areas and task enable/disable overrides. */
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

    public static synchronized boolean isTaskEnabled(String taskId) {
        return !data.disabledTasks.contains(normalize(taskId));
    }

    public static synchronized boolean disableTask(String taskId) {
        boolean changed = data.disabledTasks.add(normalize(taskId));
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized boolean enableTask(String taskId) {
        boolean changed = data.disabledTasks.remove(normalize(taskId));
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized Set<String> disabledTasks() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(data.disabledTasks));
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
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
        private Set<String> disabledTasks = new LinkedHashSet<>();

        private void repairNulls() {
            if (areas == null) {
                areas = new LinkedHashMap<>();
            }
            if (disabledTasks == null) {
                disabledTasks = new LinkedHashSet<>();
            }
        }
    }
}
