package net.ceziak.mood_swings.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.access.PlayerMoodComponentAccess;
import net.ceziak.mood_swings.area.AreaSelection;
import net.ceziak.mood_swings.area.AreaStore;
import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.config.MoodSwingsConfigManager;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.ceziak.mood_swings.task.CustomTaskConditions;
import net.ceziak.mood_swings.task.CustomTaskType;
import net.ceziak.mood_swings.task.CustomTrainTask;
import net.ceziak.mood_swings.task.TaskRegistry;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MoodSwingsCommand {
    private static final Map<UUID, AreaSelection> SELECTIONS = new HashMap<>();

    private MoodSwingsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("moodswings")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("area")
                        .then(CommandManager.literal("pos1")
                                .executes(context -> setSelectionPosition(
                                        context.getSource(),
                                        true,
                                        context.getSource().getPlayerOrThrow().getBlockPos()
                                ))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(context -> setSelectionPosition(
                                                context.getSource(),
                                                true,
                                                BlockPosArgumentType.getBlockPos(context, "pos")
                                        )))
                        )
                        .then(CommandManager.literal("pos2")
                                .executes(context -> setSelectionPosition(
                                        context.getSource(),
                                        false,
                                        context.getSource().getPlayerOrThrow().getBlockPos()
                                ))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(context -> setSelectionPosition(
                                                context.getSource(),
                                                false,
                                                BlockPosArgumentType.getBlockPos(context, "pos")
                                        )))
                        )
                        .then(CommandManager.literal("save")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .executes(context -> saveArea(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        )))
                        )
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(AreaStore.all().keySet(), builder))
                                        .executes(context -> removeArea(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        )))
                        )
                        .then(CommandManager.literal("info")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(AreaStore.all().keySet(), builder))
                                        .executes(context -> showAreaInfo(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        )))
                        )
                        .then(CommandManager.literal("show")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(AreaStore.all().keySet(), builder))
                                        .executes(context -> showAreaParticles(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        )))
                        )
                        .then(CommandManager.literal("list")
                                .executes(context -> listAreas(context.getSource())))
                )
                .then(CommandManager.literal("task")
                        .then(CommandManager.literal("force")
                                .then(CommandManager.argument("task", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(TaskRegistry.CUSTOM_IDS, builder))
                                        .executes(context -> forceTask(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "task")
                                        )))
                        )
                        .then(CommandManager.literal("clear")
                                .executes(context -> clearTask(context.getSource())))
                        .then(CommandManager.literal("disable")
                                .then(CommandManager.argument("task", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(TaskRegistry.ALL_IDS, builder))
                                        .executes(context -> setTaskEnabled(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "task"),
                                                false
                                        )))
                        )
                        .then(CommandManager.literal("enable")
                                .then(CommandManager.argument("task", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(TaskRegistry.ALL_IDS, builder))
                                        .executes(context -> setTaskEnabled(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "task"),
                                                true
                                        )))
                        )
                        .then(CommandManager.literal("list")
                                .executes(context -> listTaskSettings(context.getSource())))
                )
                .then(CommandManager.literal("reload")
                        .executes(context -> reloadConfig(context.getSource())))
        );
    }

    private static int setSelectionPosition(ServerCommandSource source, boolean first, BlockPos position)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AreaSelection selection = SELECTIONS.computeIfAbsent(player.getUuid(), ignored -> new AreaSelection());

        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (first) {
            selection.setFirst(position, dimension);
        } else {
            selection.setSecond(position, dimension);
        }

        source.sendFeedback(
                () -> Text.literal((first ? "Area position 1" : "Area position 2") + " set to " + position.toShortString()),
                false
        );
        return 1;
    }

    private static int saveArea(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AreaSelection selection = SELECTIONS.get(player.getUuid());

        if (name.isBlank()) {
            source.sendError(Text.literal("The area name cannot be empty."));
            return 0;
        }
        if (selection == null || !selection.isComplete()) {
            source.sendError(Text.literal("Set both corners first with /moodswings area pos1 and pos2."));
            return 0;
        }

        NamedArea area = NamedArea.between(
                selection.dimension(),
                selection.first(),
                selection.second(),
                name.trim()
        );
        AreaStore.put(name, area);
        source.sendFeedback(() -> Text.literal("Saved area '" + name + "': " + area.cornersString()), true);
        return 1;
    }

    private static int removeArea(ServerCommandSource source, String name) {
        String normalized = WorldDataStore.normalize(name);
        if (!AreaStore.remove(name)) {
            source.sendError(Text.literal("No named area called '" + name + "'."));
            return 0;
        }

        removeTasksUsingArea(source, normalized);
        source.sendFeedback(() -> Text.literal("Removed area '" + name + "'."), true);
        return 1;
    }

    private static int showAreaInfo(ServerCommandSource source, String name) {
        NamedArea area = AreaStore.get(name).orElse(null);
        if (area == null) {
            source.sendError(Text.literal("No named area called '" + name + "'."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal(
                "Area '" + areaDisplayName(name, area) + "' in " + area.dimension() + ": " + area.cornersString()
        ), false);
        return 1;
    }

    private static int listAreas(ServerCommandSource source) {
        if (AreaStore.all().isEmpty()) {
            source.sendFeedback(() -> Text.literal("No named areas have been saved yet."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("Named Mood Swings areas in this save:"), false);
        AreaStore.all().forEach((name, area) -> source.sendFeedback(
                () -> Text.literal("• " + areaDisplayName(name, area) + " [" + name + "]  " + area.cornersString()),
                false
        ));
        return AreaStore.all().size();
    }

    private static int showAreaParticles(ServerCommandSource source, String name) {
        NamedArea area = AreaStore.get(name).orElse(null);
        if (area == null) {
            source.sendError(Text.literal("No named area called '" + name + "'."));
            return 0;
        }

        ServerWorld world = source.getWorld();
        if (!world.getRegistryKey().getValue().toString().equals(area.dimension())) {
            source.sendError(Text.literal("That area is in dimension " + area.dimension() + "."));
            return 0;
        }

        int[] xs = {area.minX(), area.maxX()};
        int[] ys = {area.minY(), area.maxY()};
        int[] zs = {area.minZ(), area.maxZ()};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    world.spawnParticles(ParticleTypes.END_ROD, x + 0.5D, y + 0.5D, z + 0.5D,
                            12, 0.15D, 0.15D, 0.15D, 0.01D);
                }
            }
        }

        source.sendFeedback(() -> Text.literal("Outlined the eight corners of area '" + name + "'."), false);
        return 1;
    }

    private static int forceTask(ServerCommandSource source, String taskId) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        CustomTaskType type = CustomTaskType.byId(taskId).orElse(null);
        if (type == null) {
            source.sendError(Text.literal("Unknown custom task '" + taskId + "'."));
            return 0;
        }
        if (!WorldDataStore.isTaskEnabled(type.id())) {
            source.sendError(Text.literal("Task '" + type.id() + "' is disabled in this save."));
            return 0;
        }
        if (!CustomTaskConditions.isTaskAvailable(player, type)) {
            source.sendError(Text.literal("Task '" + type.id() + "' is missing a required named area."));
            return 0;
        }

        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
        component.tasks.clear();
        PlayerMoodComponent.Task storage = CustomTrainTask.chooseStorageType(component.timesGotten);
        CustomTrainTask task = CustomTrainTask.create(player, type, storage, true);
        if (task == null) {
            source.sendError(Text.literal("No valid target could be selected for that task."));
            return 0;
        }

        component.tasks.put(storage, task);
        ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
        component.sync();

        source.sendFeedback(() -> Text.literal("Forced task '" + type.id() + "' for " + player.getName().getString() + "."), false);
        return 1;
    }

    private static int clearTask(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
        component.tasks.clear();
        ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
        component.sync();
        source.sendFeedback(() -> Text.literal("Cleared your mood task. Normal rotation will resume."), false);
        return 1;
    }

    private static int setTaskEnabled(ServerCommandSource source, String taskId, boolean enabled) {
        String normalized = taskId.trim().toLowerCase(Locale.ROOT);
        if (!TaskRegistry.isKnown(normalized)) {
            source.sendError(Text.literal("Unknown task '" + taskId + "'."));
            return 0;
        }

        boolean changed = enabled
                ? WorldDataStore.enableTask(normalized)
                : WorldDataStore.disableTask(normalized);

        if (!enabled) {
            removeTaskFromOnlinePlayers(source, normalized);
        }

        source.sendFeedback(
                () -> Text.literal("Task '" + normalized + "' is now " + (enabled ? "enabled" : "disabled") + " in this save."),
                true
        );
        return changed ? 1 : 0;
    }

    private static int listTaskSettings(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Mood tasks for this save:"), false);
        for (String taskId : TaskRegistry.ALL_IDS) {
            boolean enabled = WorldDataStore.isTaskEnabled(taskId);
            Formatting color = enabled ? Formatting.GREEN : Formatting.RED;
            source.sendFeedback(
                    () -> Text.literal("• " + taskId + ": " + (enabled ? "ENABLED" : "DISABLED")).formatted(color),
                    false
            );
        }
        return TaskRegistry.ALL_IDS.size();
    }

    private static int reloadConfig(ServerCommandSource source) {
        MoodSwingsConfigManager.reload();
        removeUnavailableTasksFromOnlinePlayers(source);
        source.sendFeedback(() -> Text.literal("Reloaded config/mood_swings.json."), true);
        return 1;
    }

    private static void removeTaskFromOnlinePlayers(ServerCommandSource source, String taskId) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
            boolean removed = component.tasks.entrySet().removeIf(entry -> TaskRegistry.idOf(entry.getValue()).equals(taskId));
            if (removed) {
                ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
                component.sync();
            }
        }
    }

    private static void removeTasksUsingArea(ServerCommandSource source, String removedAreaId) {
        String gymArea = WorldDataStore.normalize(MoodSwingsConfigManager.get().safeGymAreaId());
        String rooftopArea = WorldDataStore.normalize(MoodSwingsConfigManager.get().safeRooftopAreaId());

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
            boolean removed = component.tasks.entrySet().removeIf(entry -> {
                if (!(entry.getValue() instanceof CustomTrainTask custom)) {
                    return false;
                }
                return switch (custom.customType()) {
                    case STRANGE_NOISE -> removedAreaId.equals(WorldDataStore.normalize(custom.targetAreaId()));
                    case GYM -> removedAreaId.equals(gymArea);
                    case ROOFTOP -> removedAreaId.equals(rooftopArea);
                    default -> false;
                };
            });
            if (removed) {
                ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
                component.sync();
            }
        }
    }

    private static void removeUnavailableTasksFromOnlinePlayers(ServerCommandSource source) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
            boolean removed = component.tasks.entrySet().removeIf(entry ->
                    entry.getValue() instanceof CustomTrainTask custom
                            && !CustomTaskConditions.isTaskAvailable(player, custom.customType())
            );
            if (removed) {
                ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
                component.sync();
            }
        }
    }

    private static String areaDisplayName(String key, NamedArea area) {
        if (area.displayName() != null && !area.displayName().isBlank()) {
            return area.displayName();
        }
        return key;
    }
}
