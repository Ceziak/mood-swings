package net.ceziak.mood_swings.task;

import com.google.gson.annotations.SerializedName;

/**
 * Flexible JSON-backed condition definition. Only fields used by the selected
 * condition type are read.
 */
public final class TaskConditionDefinition {
    private String type = "";

    @SerializedName("duration_seconds")
    private int durationSeconds = 10;

    private String area = "";

    @SerializedName("exclude_current")
    private boolean excludeCurrent = true;

    private double range = 3.0D;

    @SerializedName("distance_blocks")
    private double distanceBlocks = 50.0D;

    @SerializedName("max_counted_step")
    private double maxCountedStep = 2.0D;

    @SerializedName("block_tag")
    private String blockTag = "";

    @SerializedName("scan_height")
    private int scanHeight = 8;

    @SerializedName("scan_depth")
    private int scanDepth = 1;

    @SerializedName("require_powered")
    private boolean requirePowered = false;

    @SerializedName("required_facing")
    private String requiredFacing = "";

    @SerializedName("required_count")
    private int requiredCount = 1;

    public String type() {
        return type == null ? "" : type.trim();
    }

    public TaskConditionType parsedType() {
        return TaskConditionType.byId(type()).orElse(null);
    }

    public int durationTicks() {
        return Math.clamp(durationSeconds, 1, 3_600) * 20;
    }

    public String area() {
        return area == null ? "" : area.trim();
    }

    public boolean excludeCurrent() {
        return excludeCurrent;
    }

    public double safeRange() {
        return Math.clamp(range, 0.5D, 128.0D);
    }

    public double safeDistanceBlocks() {
        return Math.clamp(distanceBlocks, 1.0D, 1_000_000.0D);
    }

    public double safeMaximumCountedStep() {
        return Math.clamp(maxCountedStep, 0.25D, 128.0D);
    }

    public String blockTag() {
        return blockTag == null ? "" : blockTag.trim();
    }

    public int safeScanHeight() {
        return Math.clamp(scanHeight, 1, 128);
    }

    public int safeScanDepth() {
        return Math.clamp(scanDepth, 1, 128);
    }

    public boolean requirePowered() {
        return requirePowered;
    }

    public String requiredFacing() {
        return requiredFacing == null ? "" : requiredFacing.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public int safeRequiredCount() {
        return Math.clamp(requiredCount, 1, 100_000);
    }

    public void validate() {
        TaskConditionType parsed = parsedType();
        if (parsed == null) {
            throw new IllegalArgumentException("Unknown condition type: " + type());
        }

        switch (parsed) {
            case STAY_IN_AREA -> {
                if (area().isBlank()) {
                    throw new IllegalArgumentException("stay_in_area requires a non-empty 'area'");
                }
            }
            case UNDER_BLOCK, ABOVE_BLOCK -> {
                if (blockTag().isBlank()) {
                    throw new IllegalArgumentException(parsed.name().toLowerCase(java.util.Locale.ROOT)
                            + " requires a non-empty 'block_tag'");
                }
                if (net.minecraft.util.Identifier.tryParse(blockTag()) == null) {
                    throw new IllegalArgumentException("Invalid block_tag identifier: " + blockTag());
                }
                if (!requiredFacing().isBlank()
                        && net.minecraft.util.math.Direction.byName(requiredFacing()) == null) {
                    throw new IllegalArgumentException("Invalid required_facing: " + requiredFacing());
                }
            }
            case RANDOM_AREA, TRAVEL_DISTANCE, NEAR_PLAYER, KILL_PLAYER -> {
                // No additional required string fields.
            }
        }
    }
}
