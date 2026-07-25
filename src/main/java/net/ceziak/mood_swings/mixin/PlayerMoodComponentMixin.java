package net.ceziak.mood_swings.mixin;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.access.PlayerMoodComponentAccess;
import net.ceziak.mood_swings.task.CustomTaskRoller;
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

import java.util.Map;

@Mixin(PlayerMoodComponent.class)
public abstract class PlayerMoodComponentMixin implements PlayerMoodComponentAccess {
    @Shadow
    @Final
    private PlayerEntity player;

    @Shadow
    @Final
    public Map<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> tasks;

    @Shadow
    @Final
    public Map<PlayerMoodComponent.Task, Integer> timesGotten;

    @Shadow
    private int nextTaskTimer;

    @Unique
    private boolean moodSwings$customTaskWasActive;

    @Override
    public void moodSwings$setNextTaskTimer(int ticks) {
        this.nextTaskTimer = Math.max(0, ticks);
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void moodSwings$rememberCustomTask(CallbackInfo callback) {
        this.moodSwings$customTaskWasActive = this.tasks.values().stream()
                .anyMatch(CustomTrainTask.class::isInstance);
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void moodSwings$resumeRotationAfterCustomTask(CallbackInfo callback) {
        if (this.moodSwings$customTaskWasActive && this.tasks.isEmpty()) {
            this.nextTaskTimer = 2;
        }
        this.moodSwings$customTaskWasActive = false;
    }

    @Inject(method = "generateTask", at = @At("HEAD"), cancellable = true)
    private void moodSwings$rollCustomTask(
            CallbackInfoReturnable<PlayerMoodComponent.TrainTask> callback
    ) {
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

    @Inject(method = "readFromNbt", at = @At("TAIL"))
    private void moodSwings$restoreCustomTasks(
            NbtCompound tag,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo callback
    ) {
        if (!tag.contains("tasks", NbtElement.LIST_TYPE)) {
            return;
        }

        NbtList taskList = tag.getList("tasks", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : taskList) {
            if (!(element instanceof NbtCompound compound) || !compound.contains(CustomTrainTask.NBT_CUSTOM_ID)) {
                continue;
            }

            CustomTrainTask customTask = CustomTrainTask.fromNbt(compound);
            if (customTask != null) {
                tasks.put(customTask.getType(), customTask);
            }
        }
    }
}
