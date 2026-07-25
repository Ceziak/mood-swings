package net.ceziak.mood_swings.task;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum CustomTaskType {
    TAKE_A_SHOWER("take_a_shower"),
    SOCIALIZE("socialize"),
    GYM("gym"),
    STRANGE_NOISE("strange_noise"),
    WALK("walk"),
    ROOFTOP("rooftop");

    private final String id;

    CustomTaskType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<CustomTaskType> byId(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(task -> task.id.equals(normalized)).findFirst();
    }
}
