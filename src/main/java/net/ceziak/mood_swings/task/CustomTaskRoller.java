package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomTaskRoller {
    private CustomTaskRoller() {
    }

    /** Returns null when the unified roll lands on a native Wathe task. */
    public static CustomTrainTask tryRoll(
            ServerPlayerEntity player,
            Map<PlayerMoodComponent.Task, Integer> timesGotten
    ) {
        List<Map.Entry<Identifier, TaskDefinition>> custom = enabledAvailableCustomTasks(player);
        double customWeight = custom.stream().mapToDouble(entry -> entry.getValue().safeWeight()).sum();
        double nativeWeight = TaskRegistry.enabledNativeTasks().length;
        double totalWeight = customWeight + nativeWeight;

        if (totalWeight <= 0.0D || customWeight <= 0.0D) {
            return null;
        }

        double roll = player.getRandom().nextDouble() * totalWeight;
        if (roll >= customWeight) {
            return null;
        }

        Map.Entry<Identifier, TaskDefinition> selected = null;
        double cursor = 0.0D;
        for (Map.Entry<Identifier, TaskDefinition> entry : custom) {
            cursor += entry.getValue().safeWeight();
            if (roll < cursor) {
                selected = entry;
                break;
            }
        }
        if (selected == null) {
            return null;
        }

        PlayerMoodComponent.Task storage = CustomTrainTask.chooseStorageType(timesGotten);
        return CustomTrainTask.create(player, selected.getKey(), selected.getValue(), storage, false);
    }

    public static List<Map.Entry<Identifier, TaskDefinition>> enabledAvailableCustomTasks(ServerPlayerEntity player) {
        List<Map.Entry<Identifier, TaskDefinition>> available = new ArrayList<>();
        for (Map.Entry<Identifier, TaskDefinition> entry : TaskDefinitionRegistry.all().entrySet()) {
            if (!WorldDataStore.isTaskEnabled(entry.getKey().toString())) {
                continue;
            }
            if (!TaskConditionEvaluator.isTaskAvailable(player, entry.getValue())) {
                continue;
            }
            available.add(entry);
        }
        return available;
    }
}
