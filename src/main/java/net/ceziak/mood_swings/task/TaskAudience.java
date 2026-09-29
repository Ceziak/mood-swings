package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;

/** Controls which Wathe alignment may receive a JSON task. */
public enum TaskAudience {
    ALL,
    CIVILIAN,
    KILLER;

    public static TaskAudience byId(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean matches(ServerPlayerEntity player) {
        if (this == ALL) {
            return true;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null) {
            return false;
        }

        boolean civilian = role.getMoodType() == Role.MoodType.REAL;
        return this == CIVILIAN ? civilian : !civilian;
    }
}
