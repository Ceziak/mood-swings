package net.ceziak.mood_swings.task;

import com.google.gson.annotations.SerializedName;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Complete data-driven definition of one custom Mood Swings task. */
public final class TaskDefinition {
    @SerializedName("civilian_text")
    private String civilianText = "";

    @SerializedName("killer_text")
    private String killerText = "";

    /** Final mood value after completion. 1.0 is a full refill. */
    @SerializedName("mood_value")
    private float moodValue = 1.0F;

    /** Relative selection weight. Decimal values allow genuinely rare tasks. */
    private double weight = 1.0D;
    private boolean repeatable = true;
    private String audience = "all";

    @SerializedName("blocks_passive_income")
    private boolean blocksPassiveIncome = false;

    private TaskConditionDefinition condition;

    public String civilianText() {
        return civilianText == null ? "" : civilianText;
    }

    public String killerText() {
        return killerText == null || killerText.isBlank() ? civilianText() : killerText;
    }

    public float safeMoodValue() {
        return Math.clamp(moodValue, 0.0F, 1.0F);
    }

    public double safeWeight() {
        return Math.clamp(weight, 0.001D, 10_000.0D);
    }

    public boolean repeatable() {
        return repeatable;
    }

    public TaskAudience parsedAudience() {
        return TaskAudience.byId(audience);
    }

    public boolean isAvailableTo(ServerPlayerEntity player) {
        return parsedAudience().matches(player);
    }

    public boolean blocksPassiveIncome() {
        return blocksPassiveIncome;
    }

    public TaskConditionDefinition condition() {
        return condition;
    }

    public void validate(Identifier id) {
        if (civilianText().isBlank()) {
            throw new IllegalArgumentException(id + " is missing civilian_text");
        }
        if (killerText().isBlank()) {
            throw new IllegalArgumentException(id + " is missing killer_text");
        }
        if (weight <= 0.0D || !Double.isFinite(weight)) {
            throw new IllegalArgumentException(id + " must use a positive finite weight");
        }
        String normalizedAudience = audience == null ? "all" : audience.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("all", "civilian", "killer").contains(normalizedAudience)) {
            throw new IllegalArgumentException(id + " has invalid audience '" + audience + "'");
        }
        if (!repeatable) {
            throw new IllegalArgumentException(id + " sets repeatable=false, which is not supported yet");
        }
        if (condition == null) {
            throw new IllegalArgumentException(id + " is missing condition");
        }
        condition.validate();
    }
}
