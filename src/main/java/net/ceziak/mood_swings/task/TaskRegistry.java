package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.config.WorldDataStore;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TaskRegistry {
    public static final List<String> NATIVE_IDS = List.of("sleep", "outside", "eat", "drink");
    public static final List<String> CUSTOM_IDS = Arrays.stream(CustomTaskType.values())
            .map(CustomTaskType::id)
            .toList();
    public static final List<String> ALL_IDS = java.util.stream.Stream.concat(
            NATIVE_IDS.stream(),
            CUSTOM_IDS.stream()
    ).toList();

    private TaskRegistry() {
    }

    public static boolean isKnown(String value) {
        return ALL_IDS.contains(normalize(value));
    }

    public static String nativeId(PlayerMoodComponent.Task task) {
        return switch (task) {
            case SLEEP -> "sleep";
            case OUTSIDE -> "outside";
            case EAT -> "eat";
            case DRINK -> "drink";
        };
    }

    public static Optional<CustomTaskType> custom(String value) {
        return CustomTaskType.byId(value);
    }

    public static String idOf(PlayerMoodComponent.TrainTask task) {
        if (task instanceof CustomTrainTask custom) {
            return custom.customType().id();
        }
        return task.getName().toLowerCase(Locale.ROOT);
    }

    public static PlayerMoodComponent.Task[] enabledNativeTasks() {
        return Arrays.stream(PlayerMoodComponent.Task.values())
                .filter(task -> WorldDataStore.isTaskEnabled(nativeId(task)))
                .toArray(PlayerMoodComponent.Task[]::new);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
