package net.ceziak.mood_swings.task;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Optional;

public enum TaskConditionType {
    STAY_IN_AREA(MoodSwings.id("stay_in_area")),
    RANDOM_AREA(MoodSwings.id("random_area")),
    TRAVEL_DISTANCE(MoodSwings.id("travel_distance")),
    NEAR_PLAYER(MoodSwings.id("near_player")),
    UNDER_BLOCK(MoodSwings.id("under_block")),
    ABOVE_BLOCK(MoodSwings.id("above_block")),
    KILL_PLAYER(MoodSwings.id("kill_player"));

    private final Identifier id;

    TaskConditionType(Identifier id) {
        this.id = id;
    }

    public Identifier id() {
        return id;
    }

    public static Optional<TaskConditionType> byId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Identifier parsed = value.contains(":")
                ? Identifier.tryParse(value)
                : Identifier.tryParse(MoodSwings.MOD_ID, value);
        if (parsed == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(type -> type.id.equals(parsed)).findFirst();
    }
}
