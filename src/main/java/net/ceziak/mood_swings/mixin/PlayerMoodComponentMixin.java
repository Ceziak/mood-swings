package net.ceziak.mood_swings.mixin;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.MoodSwings;
import net.ceziak.mood_swings.access.PlayerMoodComponentAccess;
import net.ceziak.mood_swings.task.CustomTaskRoller;
import net.ceziak.mood_swings.task.CustomTaskRuntime;
import net.ceziak.mood_swings.task.CustomTrainTask;
import net.ceziak.mood_swings.task.TaskRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(PlayerMoodComponent.class)
public abstract class PlayerMoodComponentMixin implements PlayerMoodComponentAccess {
    @Unique private static final String DIMENSION_STATES_NBT = "mood_swings_dimension_task_states";
    @Unique private static final String DIMENSION_NBT = "dimension";
    @Unique private static final String TASKS_NBT = "tasks";
    @Unique private static final String NEXT_TASK_TIMER_NBT = "next_task_timer";

    @Shadow @Final private PlayerEntity player;
    @Shadow @Final public Map<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> tasks;
    @Shadow @Final public Map<PlayerMoodComponent.Task, Integer> timesGotten;
    @Shadow private int nextTaskTimer;

    /** Serialized task state for dimensions that are not currently active. */
    @Unique private final Map<String, NbtCompound> moodSwings$dimensionTaskStates = new LinkedHashMap<>();
    @Unique private String moodSwings$activeDimension;
    @Unique private boolean moodSwings$customTaskWasActive;

    @Override
    public void moodSwings$setNextTaskTimer(int ticks) {
        this.nextTaskTimer = Math.max(0, ticks);
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void moodSwings$tickCustomTasksReliably(CallbackInfo callback) {
        if (this.player instanceof ServerPlayerEntity serverPlayer) {
            moodSwings$switchDimensionIfNeeded(serverPlayer);
        }

        this.moodSwings$customTaskWasActive = this.tasks.values().stream()
                .anyMatch(CustomTrainTask.class::isInstance);

        if (this.player instanceof ServerPlayerEntity serverPlayer) {
            CustomTaskRuntime.tick((PlayerMoodComponent) (Object) this, serverPlayer);
        }
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void moodSwings$resumeRotationAfterCustomTask(CallbackInfo callback) {
        if (this.moodSwings$customTaskWasActive && this.tasks.isEmpty()) {
            this.nextTaskTimer = Math.max(this.nextTaskTimer, 2);
        }
        this.moodSwings$customTaskWasActive = false;
    }

    @Inject(method = "generateTask", at = @At("HEAD"), cancellable = true)
    private void moodSwings$rollCustomTask(CallbackInfoReturnable<PlayerMoodComponent.TrainTask> callback) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !tasks.isEmpty()) {
            return;
        }
        CustomTrainTask customTask = CustomTaskRoller.tryRoll(serverPlayer, timesGotten);
        if (customTask != null) {
            callback.setReturnValue(customTask);
        }
    }

    @Redirect(
            method = "generateTask",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/PlayerMoodComponent$Task;values()[Ldev/doctor4t/wathe/cca/PlayerMoodComponent$Task;"
            )
    )
    private PlayerMoodComponent.Task[] moodSwings$filterNativeTasks() {
        return TaskRegistry.enabledNativeTasks();
    }

    /** Final guard in case another generation path or mod returns a disabled task. */
    @Inject(method = "generateTask", at = @At("RETURN"), cancellable = true)
    private void moodSwings$rejectInvalidGeneratedTask(
            CallbackInfoReturnable<PlayerMoodComponent.TrainTask> callback
    ) {
        PlayerMoodComponent.TrainTask generated = callback.getReturnValue();
        if (generated != null
                && player instanceof ServerPlayerEntity serverPlayer
                && !CustomTaskRuntime.shouldRemainActive(generated, serverPlayer)) {
            callback.setReturnValue(null);
        }
    }

    /**
     * Rebuilds Wathe's task map ourselves so custom snapshots cannot leave native placeholders behind,
     * then selects the task state belonging to the player's current dimension.
     */
    @Inject(method = "readFromNbt", at = @At("TAIL"))
    private void moodSwings$restoreDimensionTasks(
            NbtCompound tag,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo callback
    ) {
        this.tasks.clear();
        if (tag.contains(TASKS_NBT, NbtElement.LIST_TYPE)) {
            moodSwings$restoreTaskList(tag.getList(TASKS_NBT, NbtElement.COMPOUND_TYPE));
        }

        this.moodSwings$dimensionTaskStates.clear();
        if (tag.contains(DIMENSION_STATES_NBT, NbtElement.LIST_TYPE)) {
            NbtList states = tag.getList(DIMENSION_STATES_NBT, NbtElement.COMPOUND_TYPE);
            for (NbtElement element : states) {
                if (!(element instanceof NbtCompound state)
                        || !state.contains(DIMENSION_NBT, NbtElement.STRING_TYPE)) {
                    continue;
                }
                String dimension = state.getString(DIMENSION_NBT);
                if (!dimension.isBlank()) {
                    this.moodSwings$dimensionTaskStates.put(dimension, state.copy());
                }
            }
        }

        String currentDimension = moodSwings$currentDimension();
        this.moodSwings$activeDimension = currentDimension;

        if (!this.moodSwings$dimensionTaskStates.isEmpty()) {
            moodSwings$loadDimensionState(currentDimension);
        } else {
            // One-time migration from the old global player task list.
            moodSwings$removeInvalidRestoredTasks();
            moodSwings$captureDimensionState(currentDimension);
        }
    }

    /** Saves every dimension state into the player's component NBT. */
    @Inject(method = "writeToNbt", at = @At("TAIL"))
    private void moodSwings$saveDimensionTasks(
            NbtCompound tag,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo callback
    ) {
        String currentDimension = moodSwings$currentDimension();
        if (this.moodSwings$activeDimension == null) {
            this.moodSwings$activeDimension = currentDimension;
        }
        moodSwings$captureDimensionState(this.moodSwings$activeDimension);

        NbtList states = new NbtList();
        for (Map.Entry<String, NbtCompound> entry : this.moodSwings$dimensionTaskStates.entrySet()) {
            NbtCompound state = entry.getValue().copy();
            state.putString(DIMENSION_NBT, entry.getKey());
            states.add(state);
        }
        tag.put(DIMENSION_STATES_NBT, states);
    }

    /** A new Wathe round must not resurrect tasks cached in another dimension. */
    @Inject(method = "reset", at = @At("TAIL"))
    private void moodSwings$clearAllDimensionTasks(CallbackInfo callback) {
        this.moodSwings$dimensionTaskStates.clear();
        this.moodSwings$activeDimension = moodSwings$currentDimension();
    }

    @Unique
    private void moodSwings$switchDimensionIfNeeded(ServerPlayerEntity serverPlayer) {
        String currentDimension = serverPlayer.getWorld().getRegistryKey().getValue().toString();
        if (this.moodSwings$activeDimension == null) {
            this.moodSwings$activeDimension = currentDimension;
            moodSwings$captureDimensionState(currentDimension);
            return;
        }
        if (this.moodSwings$activeDimension.equals(currentDimension)) {
            return;
        }

        moodSwings$captureDimensionState(this.moodSwings$activeDimension);
        this.moodSwings$activeDimension = currentDimension;
        moodSwings$loadDimensionState(currentDimension);
        ((PlayerMoodComponent) (Object) this).sync();
    }

    @Unique
    private void moodSwings$captureDimensionState(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return;
        }

        NbtCompound state = new NbtCompound();
        state.putInt(NEXT_TASK_TIMER_NBT, this.nextTaskTimer);
        NbtList taskList = new NbtList();
        for (PlayerMoodComponent.TrainTask task : this.tasks.values()) {
            taskList.add(task.toNbt());
        }
        state.put(TASKS_NBT, taskList);
        this.moodSwings$dimensionTaskStates.put(dimension, state);
    }

    @Unique
    private void moodSwings$loadDimensionState(String dimension) {
        this.tasks.clear();
        NbtCompound state = this.moodSwings$dimensionTaskStates.get(dimension);
        if (state == null) {
            this.nextTaskTimer = Math.max(this.nextTaskTimer, 2);
            return;
        }

        this.nextTaskTimer = state.contains(NEXT_TASK_TIMER_NBT, NbtElement.INT_TYPE)
                ? Math.max(0, state.getInt(NEXT_TASK_TIMER_NBT))
                : 2;
        if (state.contains(TASKS_NBT, NbtElement.LIST_TYPE)) {
            moodSwings$restoreTaskList(state.getList(TASKS_NBT, NbtElement.COMPOUND_TYPE));
        }
        moodSwings$removeInvalidRestoredTasks();
    }

    @Unique
    private void moodSwings$restoreTaskList(NbtList taskList) {
        PlayerMoodComponent.Task[] storageTypes = PlayerMoodComponent.Task.values();
        for (NbtElement element : taskList) {
            if (!(element instanceof NbtCompound compound)) {
                continue;
            }

            int storageOrdinal = compound.getInt("type");
            if (storageOrdinal < 0 || storageOrdinal >= storageTypes.length) {
                continue;
            }
            PlayerMoodComponent.Task storageType = storageTypes[storageOrdinal];

            try {
                PlayerMoodComponent.TrainTask restored;
                if (compound.contains(CustomTrainTask.NBT_CUSTOM_ID)) {
                    restored = CustomTrainTask.fromNbt(compound);
                } else {
                    restored = storageType.setFunction.apply(compound);
                }
                if (restored == null) {
                    continue;
                }
                if (this.player instanceof ServerPlayerEntity serverPlayer
                        && !CustomTaskRuntime.shouldRemainActive(restored, serverPlayer)) {
                    continue;
                }
                this.tasks.put(storageType, restored);
            } catch (RuntimeException exception) {
                MoodSwings.LOGGER.warn("Could not restore a saved mood task for dimension {}.",
                        moodSwings$currentDimension(), exception);
            }
        }
    }

    @Unique
    private void moodSwings$removeInvalidRestoredTasks() {
        if (this.player instanceof ServerPlayerEntity serverPlayer) {
            this.tasks.entrySet().removeIf(entry ->
                    !CustomTaskRuntime.shouldRemainActive(entry.getValue(), serverPlayer));
        }
    }

    @Unique
    private String moodSwings$currentDimension() {
        return this.player.getWorld().getRegistryKey().getValue().toString();
    }
}
