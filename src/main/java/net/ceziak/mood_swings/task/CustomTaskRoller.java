package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomTaskRoller {
    private CustomTaskRoller() {
    }

    /**
     * Returns a custom task when a unified native/custom roll lands on one.
     * Returning null lets Wathe continue with its normal native generator.
     */
    public static CustomTrainTask tryRoll(
            ServerPlayerEntity player,
            Map<PlayerMoodComponent.Task, Integer> timesGotten
    ) {
        List<CustomTaskType> custom = enabledAvailableCustomTasks(player);
        int nativeCount = TaskRegistry.enabledNativeTasks().length;
        int total = custom.size() + nativeCount;

        if (total <= 0 || custom.isEmpty()) {
            return null;
        }

        int roll = player.getRandom().nextInt(total);
        if (roll >= custom.size()) {
            return null;
        }

        CustomTaskType selected = custom.get(roll);
        PlayerMoodComponent.Task storage = CustomTrainTask.chooseStorageType(timesGotten);
        return CustomTrainTask.create(player, selected, storage, false);
    }

    public static List<CustomTaskType> enabledAvailableCustomTasks(ServerPlayerEntity player) {
        List<CustomTaskType> available = new ArrayList<>();
        for (CustomTaskType type : CustomTaskType.values()) {
            if (!WorldDataStore.isTaskEnabled(type.id())) {
                continue;
            }
            if (!CustomTaskConditions.isTaskAvailable(player, type)) {
                continue;
            }
            available.add(type);
        }
        return available;
    }
}
