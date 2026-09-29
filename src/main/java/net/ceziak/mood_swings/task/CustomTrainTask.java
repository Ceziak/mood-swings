package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.area.AreaStore;
import net.ceziak.mood_swings.area.AreaTarget;
import net.ceziak.mood_swings.area.NamedArea;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A JSON-defined task stored directly inside Wathe's own mood component. */
public final class CustomTrainTask implements PlayerMoodComponent.TrainTask {
    public static final String NBT_CUSTOM_ID = "mood_swings_custom_task";
    private static final String NBT_DEFINITION = "mood_swings_definition_json";
    private static final String NBT_PROGRESS_TICKS = "mood_swings_progress_ticks";
    private static final String NBT_DISTANCE = "mood_swings_distance";
    private static final String NBT_EVENT_PROGRESS = "mood_swings_event_progress";
    private static final String NBT_CONDITION_MISS_TICKS = "mood_swings_condition_miss_ticks";
    private static final int CONDITION_GRACE_TICKS = 10;
    private static final String NBT_FORCED = "mood_swings_forced";
    private static final String NBT_TARGET_AREA = "mood_swings_target_area";
    private static final String NBT_TARGET_DISPLAY = "mood_swings_target_display";

    private final Identifier taskId;
    private final TaskDefinition definition;
    private final PlayerMoodComponent.Task storageType;
    private final boolean forced;
    private final String targetAreaId;
    private final String targetAreaDisplayName;

    private int progressTicks;
    private double distanceProgress;
    private int eventProgress;
    private int conditionMissTicks;
    private Vec3d previousPosition;
    private long lastAdvancedWorldTick = Long.MIN_VALUE;

    private CustomTrainTask(
            Identifier taskId,
            TaskDefinition definition,
            PlayerMoodComponent.Task storageType,
            boolean forced,
            String targetAreaId,
            String targetAreaDisplayName,
            int progressTicks,
            double distanceProgress,
            int eventProgress,
            int conditionMissTicks
    ) {
        this.taskId = taskId;
        this.definition = definition;
        this.storageType = storageType;
        this.forced = forced;
        this.targetAreaId = targetAreaId;
        this.targetAreaDisplayName = targetAreaDisplayName;
        this.progressTicks = Math.max(0, progressTicks);
        this.distanceProgress = Math.max(0.0D, distanceProgress);
        this.eventProgress = Math.max(0, eventProgress);
        this.conditionMissTicks = Math.max(0, conditionMissTicks);
    }

    public static CustomTrainTask create(
            ServerPlayerEntity player,
            Identifier taskId,
            TaskDefinition definition,
            PlayerMoodComponent.Task storageType,
            boolean forced
    ) {
        AreaTarget target = definition.condition().parsedType() == TaskConditionType.RANDOM_AREA
                ? chooseRandomAreaTarget(player, definition.condition().excludeCurrent())
                : null;

        if (definition.condition().parsedType() == TaskConditionType.RANDOM_AREA && target == null) {
            return null;
        }

        return new CustomTrainTask(
                taskId,
                definition,
                storageType,
                forced,
                target == null ? null : target.id(),
                target == null ? null : target.displayName(),
                0,
                0.0D,
                0,
                0
        );
    }

    private static AreaTarget chooseRandomAreaTarget(ServerPlayerEntity player, boolean excludeCurrent) {
        List<Map.Entry<String, NamedArea>> candidates = new ArrayList<>(AreaStore.inPlayerDimension(player));
        if (candidates.isEmpty()) {
            return null;
        }

        if (excludeCurrent && candidates.size() > 1) {
            candidates.removeIf(entry -> entry.getValue().contains(player));
        }
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(AreaStore.inPlayerDimension(player));
        }

        Map.Entry<String, NamedArea> chosen = candidates.get(player.getRandom().nextInt(candidates.size()));
        String display = chosen.getValue().displayName();
        if (display == null || display.isBlank()) {
            display = prettifyAreaId(chosen.getKey());
        }
        return new AreaTarget(chosen.getKey(), display);
    }

    public Identifier taskId() {
        return taskId;
    }

    public TaskDefinition definition() {
        return definition;
    }

    public String targetAreaId() {
        return targetAreaId;
    }

    public boolean isForced() {
        return forced;
    }

    @Override
    public void tick(@NotNull PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            advanceOnce(serverPlayer);
        }
    }

    /** Advances progress at most once for each world tick, even when several hooks call it. */
    public void advanceOnce(ServerPlayerEntity player) {
        long currentWorldTick = player.getWorld().getTime();
        if (lastAdvancedWorldTick == currentWorldTick) {
            return;
        }
        lastAdvancedWorldTick = currentWorldTick;

        TaskConditionType type = definition.condition().parsedType();
        if (type == TaskConditionType.TRAVEL_DISTANCE) {
            tickTravel(player);
            return;
        }
        if (type == TaskConditionType.KILL_PLAYER) {
            // Kill tasks use eventProgress for completion, while progressTicks tracks
            // how long the compulsion has been active for pressure effects.
            if (progressTicks < Integer.MAX_VALUE) {
                progressTicks++;
            }
            return;
        }

        if (TaskConditionEvaluator.isDurationConditionMet(player, this)) {
            conditionMissTicks = 0;
            if (progressTicks < TaskConditionEvaluator.requiredTicks(definition)) {
                progressTicks++;
            }
        } else if (progressTicks > 0) {
            // One bad packet, edge step, or block-state update should not erase ten seconds of work.
            conditionMissTicks++;
            if (conditionMissTicks > CONDITION_GRACE_TICKS) {
                progressTicks = 0;
                conditionMissTicks = 0;
            }
        } else {
            conditionMissTicks = 0;
        }
    }

    private void tickTravel(ServerPlayerEntity player) {
        Vec3d current = player.getPos();
        if (previousPosition != null) {
            double dx = current.x - previousPosition.x;
            double dz = current.z - previousPosition.z;
            double horizontalStep = Math.sqrt(dx * dx + dz * dz);
            if (horizontalStep <= definition.condition().safeMaximumCountedStep()) {
                distanceProgress += horizontalStep;
            }
        }
        previousPosition = current;
    }

    public int elapsedTicks() {
        return progressTicks;
    }

    public void recordPlayerKill() {
        if (definition.condition().parsedType() == TaskConditionType.KILL_PLAYER) {
            eventProgress = Math.min(definition.condition().safeRequiredCount(), eventProgress + 1);
        }
    }

    @Override
    public boolean isFulfilled(PlayerEntity player) {
        TaskConditionType type = definition.condition().parsedType();
        if (type == null) {
            return false;
        }
        return switch (type) {
            case TRAVEL_DISTANCE -> distanceProgress >= definition.condition().safeDistanceBlocks();
            case KILL_PLAYER -> eventProgress >= definition.condition().safeRequiredCount();
            default -> progressTicks >= TaskConditionEvaluator.requiredTicks(definition);
        };
    }

    public Text displayText(boolean killer) {
        String raw = killer ? definition.killerText() : definition.civilianText();
        String area = targetAreaDisplayName == null || targetAreaDisplayName.isBlank()
                ? prettifyAreaId(targetAreaId)
                : targetAreaDisplayName;
        return Text.literal(raw.replace("<area>", area).replace("<area name>", area));
    }

    @Override
    public String getName() {
        return taskId.toString();
    }

    @Override
    public PlayerMoodComponent.Task getType() {
        return storageType;
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("type", storageType.ordinal());
        nbt.putString(NBT_CUSTOM_ID, taskId.toString());
        nbt.putString(NBT_DEFINITION, TaskDefinitionRegistry.toJson(definition));
        nbt.putInt(NBT_PROGRESS_TICKS, progressTicks);
        nbt.putDouble(NBT_DISTANCE, distanceProgress);
        nbt.putInt(NBT_EVENT_PROGRESS, eventProgress);
        nbt.putInt(NBT_CONDITION_MISS_TICKS, conditionMissTicks);
        nbt.putBoolean(NBT_FORCED, forced);
        if (targetAreaId != null) {
            nbt.putString(NBT_TARGET_AREA, targetAreaId);
        }
        if (targetAreaDisplayName != null) {
            nbt.putString(NBT_TARGET_DISPLAY, targetAreaDisplayName);
        }
        return nbt;
    }

    public static CustomTrainTask fromNbt(NbtCompound nbt) {
        Identifier taskId = TaskDefinitionRegistry.parseTaskId(nbt.getString(NBT_CUSTOM_ID));
        if (taskId == null) {
            return null;
        }

        TaskDefinition definition = nbt.contains(NBT_DEFINITION)
                ? TaskDefinitionRegistry.fromJson(nbt.getString(NBT_DEFINITION))
                : TaskDefinitionRegistry.get(taskId).orElse(null);
        if (definition == null) {
            return null;
        }
        try {
            definition.validate(taskId);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        int storageOrdinal = nbt.getInt("type");
        PlayerMoodComponent.Task[] values = PlayerMoodComponent.Task.values();
        if (storageOrdinal < 0 || storageOrdinal >= values.length) {
            return null;
        }

        String targetArea = nbt.contains(NBT_TARGET_AREA) ? nbt.getString(NBT_TARGET_AREA) : null;
        String targetDisplay = nbt.contains(NBT_TARGET_DISPLAY) ? nbt.getString(NBT_TARGET_DISPLAY) : null;

        return new CustomTrainTask(
                taskId,
                definition,
                values[storageOrdinal],
                nbt.getBoolean(NBT_FORCED),
                targetArea,
                targetDisplay,
                nbt.getInt(NBT_PROGRESS_TICKS),
                nbt.getDouble(NBT_DISTANCE),
                nbt.getInt(NBT_EVENT_PROGRESS),
                nbt.getInt(NBT_CONDITION_MISS_TICKS)
        );
    }

    public static PlayerMoodComponent.Task chooseStorageType(Map<PlayerMoodComponent.Task, Integer> timesGotten) {
        return java.util.Arrays.stream(PlayerMoodComponent.Task.values())
                .min(Comparator.comparingInt(task -> timesGotten.getOrDefault(task, 1)))
                .orElse(PlayerMoodComponent.Task.SLEEP);
    }

    private static String prettifyAreaId(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown Area";
        }

        String[] parts = id.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
