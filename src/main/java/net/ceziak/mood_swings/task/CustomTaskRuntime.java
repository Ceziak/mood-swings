package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.network.TaskCompletePayload;
import net.ceziak.mood_swings.access.PlayerMoodComponentAccess;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side runtime for reliable custom task progression and event completion. */
public final class CustomTaskRuntime {
    private static final Map<UUID, Integer> PENDING_PLAYER_KILLS = new HashMap<>();

    private CustomTaskRuntime() {
    }

    public static void tick(PlayerMoodComponent component, ServerPlayerEntity player) {
        boolean changed = removeInvalidTasks(component, player);
        Iterator<Map.Entry<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask>> iterator =
                component.tasks.entrySet().iterator();

        while (iterator.hasNext()) {
            PlayerMoodComponent.TrainTask value = iterator.next().getValue();
            if (!(value instanceof CustomTrainTask task)) {
                continue;
            }

            task.advanceOnce(player);
            if (MurderTaskRules.isMurderTask(task)
                    && task.elapsedTicks() >= MurderTaskRules.OVERDUE_TICKS) {
                player.setSprinting(false);
            }
            if (!task.isFulfilled(player)) {
                continue;
            }

            iterator.remove();
            component.setMood(task.definition().safeMoodValue());
            ServerPlayNetworking.send(player, new TaskCompletePayload());
            changed = true;
        }

        if (changed) {
            ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(3);
            component.sync();
        }
    }

    /**
     * Removes disabled, deleted, role-incompatible, or otherwise unusable tasks.
     * This runs continuously so stale NBT and configuration races cannot leave ghost tasks behind.
     */
    public static boolean removeInvalidTasks(PlayerMoodComponent component, ServerPlayerEntity player) {
        return component.tasks.entrySet().removeIf(entry -> !shouldRemainActive(entry.getValue(), player));
    }

    public static boolean shouldRemainActive(
            PlayerMoodComponent.TrainTask task,
            ServerPlayerEntity player
    ) {
        if (!WorldDataStore.isTaskEnabled(TaskRegistry.idOf(task))) {
            return false;
        }

        if (!(task instanceof CustomTrainTask custom)) {
            return true;
        }

        if (TaskDefinitionRegistry.get(custom.taskId()).isEmpty()) {
            return false;
        }

        return TaskConditionEvaluator.isActiveTaskValid(player, custom);
    }

    /**
     * Lightweight guard used by the Wathe kill hook. It intentionally avoids running
     * the general task ticker or touching native Wathe tasks.
     */
    public static boolean hasPlayerKillTask(ServerPlayerEntity killer) {
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(killer);
        return component.tasks.values().stream()
                .filter(CustomTrainTask.class::isInstance)
                .map(CustomTrainTask.class::cast)
                .anyMatch(task -> task.definition().condition().parsedType() == TaskConditionType.KILL_PLAYER);
    }

    /**
     * Queues kill processing until the end of the server tick. This prevents task-map
     * mutation and component synchronization from running inside another mod's death logic.
     */
    public static void queuePlayerKill(ServerPlayerEntity killer) {
        if (!hasPlayerKillTask(killer)) {
            return;
        }
        PENDING_PLAYER_KILLS.merge(killer.getUuid(), 1, Integer::sum);
    }

    /** Processes queued kills after all normal game/death logic for the tick has finished. */
    public static void flushQueuedPlayerKills(MinecraftServer server) {
        if (PENDING_PLAYER_KILLS.isEmpty()) {
            return;
        }

        Map<UUID, Integer> queued = new HashMap<>(PENDING_PLAYER_KILLS);
        PENDING_PLAYER_KILLS.clear();

        for (Map.Entry<UUID, Integer> entry : queued.entrySet()) {
            ServerPlayerEntity killer = server.getPlayerManager().getPlayer(entry.getKey());
            if (killer == null) {
                continue;
            }

            for (int index = 0; index < entry.getValue(); index++) {
                if (!hasPlayerKillTask(killer)) {
                    break;
                }
                recordPlayerKill(killer);
            }
        }
    }

    public static void clearQueuedPlayerKills() {
        PENDING_PLAYER_KILLS.clear();
    }

    /**
     * Records one successful elimination and resolves only matching custom
     * kill-player tasks. Unrelated and native tasks are never ticked from this path.
     */
    public static void recordPlayerKill(ServerPlayerEntity killer) {
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(killer);
        Iterator<Map.Entry<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask>> iterator =
                component.tasks.entrySet().iterator();

        boolean progressed = false;
        boolean completed = false;

        while (iterator.hasNext()) {
            PlayerMoodComponent.TrainTask value = iterator.next().getValue();
            if (!(value instanceof CustomTrainTask task)
                    || task.definition().condition().parsedType() != TaskConditionType.KILL_PLAYER) {
                continue;
            }

            if (!shouldRemainActive(task, killer)) {
                iterator.remove();
                completed = true;
                continue;
            }

            task.recordPlayerKill();
            progressed = true;

            if (!task.isFulfilled(killer)) {
                continue;
            }

            iterator.remove();
            component.setMood(task.definition().safeMoodValue());
            ServerPlayNetworking.send(killer, new TaskCompletePayload());
            completed = true;
        }

        if (completed) {
            ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(3);
        }
        if (progressed || completed) {
            component.sync();
        }
    }

    public static boolean blocksPassiveIncome(ServerPlayerEntity player) {
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
        return component.tasks.values().stream()
                .filter(CustomTrainTask.class::isInstance)
                .map(CustomTrainTask.class::cast)
                .filter(task -> shouldRemainActive(task, player))
                .anyMatch(task -> task.definition().blocksPassiveIncome() && !task.isFulfilled(player));
    }
}
