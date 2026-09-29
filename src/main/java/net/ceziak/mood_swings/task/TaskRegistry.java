package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TaskRegistry {
    public static final List<String> NATIVE_IDS = List.of("sleep", "outside", "eat", "drink");

    private TaskRegistry() {
    }

    public static List<String> customIds() {
        return TaskDefinitionRegistry.ids();
    }

    public static List<String> allIds() {
        List<String> ids = new ArrayList<>(NATIVE_IDS);
        ids.addAll(customIds());
        return List.copyOf(ids);
    }

    public static boolean isKnown(String value) {
        String normalized = normalize(value);
        return NATIVE_IDS.contains(normalized) || TaskDefinitionRegistry.resolve(normalized).isPresent();
    }

    public static String canonicalize(String value) {
        String normalized = normalize(value);
        if (NATIVE_IDS.contains(normalized)) {
            return normalized;
        }
        return TaskDefinitionRegistry.resolve(normalized)
                .map(entry -> entry.getKey().toString())
                .orElse(normalized);
    }

    public static String nativeId(PlayerMoodComponent.Task task) {
        return switch (task) {
            case SLEEP -> "sleep";
            case OUTSIDE -> "outside";
            case EAT -> "eat";
            case DRINK -> "drink";
        };
    }

    public static String idOf(PlayerMoodComponent.TrainTask task) {
        if (task instanceof CustomTrainTask custom) {
            return custom.taskId().toString();
        }
        return nativeId(task.getType());
    }

    public static PlayerMoodComponent.Task[] enabledNativeTasks() {
        return Arrays.stream(PlayerMoodComponent.Task.values())
                .filter(task -> WorldDataStore.isTaskEnabled(nativeId(task)))
                .toArray(PlayerMoodComponent.Task[]::new);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
