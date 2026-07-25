package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.area.AreaStore;
import net.ceziak.mood_swings.area.AreaTarget;
import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.config.MoodSwingsConfig;
import net.ceziak.mood_swings.config.MoodSwingsConfigManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A custom task stored directly inside Wathe's own mood component. */
public final class CustomTrainTask implements PlayerMoodComponent.TrainTask {
    public static final String NBT_CUSTOM_ID = "mood_swings_custom_task";
    private static final String NBT_PROGRESS_TICKS = "mood_swings_progress_ticks";
    private static final String NBT_DISTANCE = "mood_swings_distance";
    private static final String NBT_FORCED = "mood_swings_forced";
    private static final String NBT_TARGET_AREA = "mood_swings_target_area";
    private static final String NBT_TARGET_DISPLAY = "mood_swings_target_display";

    private final CustomTaskType customType;
    private final PlayerMoodComponent.Task storageType;
    private final boolean forced;
    private final String targetAreaId;
    private final String targetAreaDisplayName;

    private int progressTicks;
    private double distanceProgress;
    private Vec3d previousPosition;
    private boolean completionHandled;

    private CustomTrainTask(
            CustomTaskType customType,
            PlayerMoodComponent.Task storageType,
            boolean forced,
            String targetAreaId,
            String targetAreaDisplayName,
            int progressTicks,
            double distanceProgress
    ) {
        this.customType = customType;
        this.storageType = storageType;
        this.forced = forced;
        this.targetAreaId = targetAreaId;
        this.targetAreaDisplayName = targetAreaDisplayName;
        this.progressTicks = Math.max(0, progressTicks);
        this.distanceProgress = Math.max(0.0D, distanceProgress);
    }

    public static CustomTrainTask create(
            ServerPlayerEntity player,
            CustomTaskType customType,
            PlayerMoodComponent.Task storageType,
            boolean forced
    ) {
        AreaTarget target = customType == CustomTaskType.STRANGE_NOISE
                ? chooseStrangeNoiseTarget(player)
                : null;

        if (customType == CustomTaskType.STRANGE_NOISE && target == null) {
            return null;
        }

        return new CustomTrainTask(
                customType,
                storageType,
                forced,
                target == null ? null : target.id(),
                target == null ? null : target.displayName(),
                0,
                0.0D
        );
    }

    private static AreaTarget chooseStrangeNoiseTarget(ServerPlayerEntity player) {
        List<Map.Entry<String, NamedArea>> candidates = new ArrayList<>(AreaStore.inPlayerDimension(player));
        if (candidates.isEmpty()) {
            return null;
        }

        MoodSwingsConfig config = MoodSwingsConfigManager.get();
        if (config.strangeNoiseExcludeCurrentArea && candidates.size() > 1) {
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

    public CustomTaskType customType() {
        return customType;
    }

    public String targetAreaId() {
        return targetAreaId;
    }

    public int progressTicks() {
        return progressTicks;
    }

    public double distanceProgress() {
        return distanceProgress;
    }

    public boolean isForced() {
        return forced;
    }

    @Override
    public void tick(@NotNull PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        if (customType == CustomTaskType.WALK) {
            tickWalk(serverPlayer);
            return;
        }

        if (CustomTaskConditions.isDurationConditionMet(serverPlayer, this)) {
            if (progressTicks < CustomTaskConditions.requiredTicks(customType)) {
                progressTicks++;
            }
        } else {
            progressTicks = 0;
        }
    }

    private void tickWalk(ServerPlayerEntity player) {
        Vec3d current = player.getPos();
        if (previousPosition != null) {
            double dx = current.x - previousPosition.x;
            double dz = current.z - previousPosition.z;
            double horizontalStep = Math.sqrt(dx * dx + dz * dz);
            if (horizontalStep <= MoodSwingsConfigManager.get().safeWalkMaximumCountedStep()) {
                distanceProgress += horizontalStep;
            }
        }
        previousPosition = current;
    }

    @Override
    public boolean isFulfilled(PlayerEntity player) {
        boolean complete = customType == CustomTaskType.WALK
                ? distanceProgress >= MoodSwingsConfigManager.get().safeWalkDistance()
                : progressTicks >= CustomTaskConditions.requiredTicks(customType);

        if (complete && !completionHandled && player instanceof ServerPlayerEntity serverPlayer) {
            completionHandled = true;
            PlayerMoodComponent.KEY.get(serverPlayer).setMood(1.0F);
        }
        return complete;
    }

    public Text displayText(boolean killer) {
        String variant = killer ? "fake" : "feel";
        String key = "task.mood_swings." + customType.id() + "." + variant;
        if (customType == CustomTaskType.STRANGE_NOISE) {
            String area = targetAreaDisplayName == null || targetAreaDisplayName.isBlank()
                    ? prettifyAreaId(targetAreaId)
                    : targetAreaDisplayName;
            return Text.translatable(key, Text.literal(area));
        }
        return Text.translatable(key);
    }

    @Override
    public String getName() {
        return "mood_swings." + customType.id();
    }

    @Override
    public PlayerMoodComponent.Task getType() {
        return storageType;
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("type", storageType.ordinal());
        nbt.putString(NBT_CUSTOM_ID, customType.id());
        nbt.putInt(NBT_PROGRESS_TICKS, progressTicks);
        nbt.putDouble(NBT_DISTANCE, distanceProgress);
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
        CustomTaskType customType = CustomTaskType.byId(nbt.getString(NBT_CUSTOM_ID)).orElse(null);
        if (customType == null) {
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
                customType,
                values[storageOrdinal],
                nbt.getBoolean(NBT_FORCED),
                targetArea,
                targetDisplay,
                nbt.getInt(NBT_PROGRESS_TICKS),
                nbt.getDouble(NBT_DISTANCE)
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
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }
}
