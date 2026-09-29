package net.ceziak.mood_swings.task;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.util.Identifier;

/** Shared timing and identity rules for the killer-exclusive murder task. */
public final class MurderTaskRules {
    public static final Identifier TASK_ID = MoodSwings.id("murder");
    public static final int OVERDUE_TICKS = 2 * 60 * 20;
    public static final int RECOVERY_TICKS = 4 * 20;

    private MurderTaskRules() {
    }

    public static boolean isMurderTask(CustomTrainTask task) {
        return task != null && TASK_ID.equals(task.taskId());
    }
}
