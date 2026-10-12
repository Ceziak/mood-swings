package net.ceziak.mood_swings.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.access.PlayerMoodComponentAccess;
import net.ceziak.mood_swings.area.AreaSelection;
import net.ceziak.mood_swings.area.AreaStore;
import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.ceziak.mood_swings.lockdown.LockdownAreaStore;
import net.ceziak.mood_swings.lockdown.LockdownSystem;
import net.ceziak.mood_swings.network.AreaBorderPayload;
import net.ceziak.mood_swings.network.ConfigSyncService;
import net.ceziak.mood_swings.ost.OstArea;
import net.ceziak.mood_swings.ost.OstAreaStore;
import net.ceziak.mood_swings.ost.OstAreaSystem;
import net.ceziak.mood_swings.task.CustomTrainTask;
import net.ceziak.mood_swings.task.TaskConditionEvaluator;
import net.ceziak.mood_swings.task.TaskDefinition;
import net.ceziak.mood_swings.task.TaskDefinitionRegistry;
import net.ceziak.mood_swings.task.TaskRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MoodSwingsCommand {
    private static final Map<UUID, AreaSelection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, AreaSelection> OST_SELECTIONS = new HashMap<>();
    private static final Map<UUID, AreaSelection> LOCKDOWN_SELECTIONS = new HashMap<>();

    private MoodSwingsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("moodswings")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("area")
                        .then(CommandManager.literal("pos1")
                                .executes(context -> setSelectionPosition(context.getSource(), true,
                                        context.getSource().getPlayerOrThrow().getBlockPos()))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(context -> setSelectionPosition(context.getSource(), true,
                                                BlockPosArgumentType.getBlockPos(context, "pos")))))
                        .then(CommandManager.literal("pos2")
                                .executes(context -> setSelectionPosition(context.getSource(), false,
                                        context.getSource().getPlayerOrThrow().getBlockPos()))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(context -> setSelectionPosition(context.getSource(), false,
                                                BlockPosArgumentType.getBlockPos(context, "pos")))))
                        .then(CommandManager.literal("save")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .executes(context -> saveArea(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(AreaStore.all().keySet(), builder))
                                        .executes(context -> removeArea(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("info")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(AreaStore.all().keySet(), builder))
                                        .executes(context -> showAreaInfo(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("show")
                                .then(CommandManager.literal("all")
                                        .executes(context -> showAllAreaBorders(context.getSource())))
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(AreaStore.all().keySet(), builder))
                                        .executes(context -> showAreaBorder(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("hide")
                                .then(CommandManager.literal("all")
                                        .executes(context -> hideAllAreaBorders(context.getSource())))
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(AreaStore.all().keySet(), builder))
                                        .executes(context -> hideAreaBorder(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("list").executes(context -> listAreas(context.getSource()))))
                .then(CommandManager.literal("task")
                        .then(CommandManager.literal("force")
                                .then(CommandManager.argument("task", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(TaskRegistry.customIds(), builder))
                                        .executes(context -> forceTask(context.getSource(),
                                                StringArgumentType.getString(context, "task")))))
                        .then(CommandManager.literal("clear")
                                .executes(context -> clearTask(
                                        context.getSource(), context.getSource().getPlayerOrThrow()))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> clearTask(
                                                context.getSource(),
                                                EntityArgumentType.getPlayer(context, "player")))))
                        .then(CommandManager.literal("disable")
                                .then(CommandManager.argument("task", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(TaskRegistry.allIds(), builder))
                                        .executes(context -> setTaskEnabled(context.getSource(),
                                                StringArgumentType.getString(context, "task"), false))))
                        .then(CommandManager.literal("enable")
                                .then(CommandManager.argument("task", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(TaskRegistry.allIds(), builder))
                                        .executes(context -> setTaskEnabled(context.getSource(),
                                                StringArgumentType.getString(context, "task"), true))))
                        .then(CommandManager.literal("list").executes(context -> listTaskSettings(context.getSource()))))
                .then(CommandManager.literal("ostarea")
                        .then(CommandManager.literal("pos1")
                                .executes(context -> setOstSelectionPosition(context.getSource(), true,
                                        context.getSource().getPlayerOrThrow().getBlockPos()))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(context -> setOstSelectionPosition(context.getSource(), true,
                                                BlockPosArgumentType.getBlockPos(context, "pos")))))
                        .then(CommandManager.literal("pos2")
                                .executes(context -> setOstSelectionPosition(context.getSource(), false,
                                        context.getSource().getPlayerOrThrow().getBlockPos()))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(context -> setOstSelectionPosition(context.getSource(), false,
                                                BlockPosArgumentType.getBlockPos(context, "pos")))))
                        .then(CommandManager.literal("save")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .then(CommandManager.argument("sound", IdentifierArgumentType.identifier())
                                                .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                                                .executes(context -> saveOstArea(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        IdentifierArgumentType.getIdentifier(context, "sound").toString())))))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .executes(context -> removeOstArea(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("info")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .executes(context -> showOstAreaInfo(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("show")
                                .then(CommandManager.literal("all")
                                        .executes(context -> showAllOstAreaBorders(context.getSource())))
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .executes(context -> showOstAreaBorder(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("hide")
                                .then(CommandManager.literal("all")
                                        .executes(context -> hideAllOstAreaBorders(context.getSource())))
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .executes(context -> hideOstAreaBorder(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("sound")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .then(CommandManager.argument("sound", IdentifierArgumentType.identifier())
                                                .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                                                .executes(context -> setOstAreaSound(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        IdentifierArgumentType.getIdentifier(context, "sound").toString())))))
                        .then(CommandManager.literal("volume")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .then(CommandManager.argument("value", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                .executes(context -> setOstAreaVolume(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        FloatArgumentType.getFloat(context, "value"))))))
                        .then(CommandManager.literal("priority")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .then(CommandManager.argument("value", IntegerArgumentType.integer())
                                                .executes(context -> setOstAreaPriority(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        IntegerArgumentType.getInteger(context, "value"))))))
                        .then(CommandManager.literal("fade")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .then(CommandManager.argument("seconds", FloatArgumentType.floatArg(0.0F, 30.0F))
                                                .executes(context -> setOstAreaFade(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        FloatArgumentType.getFloat(context, "seconds"))))))
                        .then(CommandManager.literal("shake")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .then(CommandManager.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                .executes(context -> setOstAreaShake(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        FloatArgumentType.getFloat(context, "strength"))))))
                        .then(CommandManager.literal("darkness")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(OstAreaStore.all().keySet(), builder))
                                        .then(CommandManager.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                .executes(context -> setOstAreaDarkness(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        FloatArgumentType.getFloat(context, "strength"))))))
                        .then(CommandManager.literal("enable")
                                .executes(context -> setOstAreasEnabled(context.getSource(), true)))
                        .then(CommandManager.literal("disable")
                                .executes(context -> setOstAreasEnabled(context.getSource(), false)))
                        .then(CommandManager.literal("list")
                                .executes(context -> listOstAreas(context.getSource()))))
                .then(CommandManager.literal("lockdown")
                        .then(CommandManager.literal("area")
                                .then(CommandManager.literal("pos1")
                                        .executes(context -> setLockdownSelectionPosition(context.getSource(), true,
                                                context.getSource().getPlayerOrThrow().getBlockPos()))
                                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(context -> setLockdownSelectionPosition(context.getSource(), true,
                                                        BlockPosArgumentType.getBlockPos(context, "pos")))))
                                .then(CommandManager.literal("pos2")
                                        .executes(context -> setLockdownSelectionPosition(context.getSource(), false,
                                                context.getSource().getPlayerOrThrow().getBlockPos()))
                                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(context -> setLockdownSelectionPosition(context.getSource(), false,
                                                        BlockPosArgumentType.getBlockPos(context, "pos")))))
                                .then(CommandManager.literal("save")
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .executes(context -> saveLockdownArea(context.getSource(),
                                                        StringArgumentType.getString(context, "name")))))
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(LockdownAreaStore.all().keySet(), builder))
                                                .executes(context -> removeLockdownArea(context.getSource(),
                                                        StringArgumentType.getString(context, "name")))))
                                .then(CommandManager.literal("info")
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(LockdownAreaStore.all().keySet(), builder))
                                                .executes(context -> showLockdownAreaInfo(context.getSource(),
                                                        StringArgumentType.getString(context, "name")))))
                                .then(CommandManager.literal("show")
                                        .then(CommandManager.literal("all")
                                                .executes(context -> showAllLockdownAreaBorders(context.getSource())))
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(LockdownAreaStore.all().keySet(), builder))
                                                .executes(context -> showLockdownAreaBorder(context.getSource(),
                                                        StringArgumentType.getString(context, "name")))))
                                .then(CommandManager.literal("hide")
                                        .then(CommandManager.literal("all")
                                                .executes(context -> hideAllLockdownAreaBorders(context.getSource())))
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(LockdownAreaStore.all().keySet(), builder))
                                                .executes(context -> hideLockdownAreaBorder(context.getSource(),
                                                        StringArgumentType.getString(context, "name")))))
                                .then(CommandManager.literal("list")
                                        .executes(context -> listLockdownAreas(context.getSource()))))
                        .then(CommandManager.literal("enable")
                                .executes(context -> setLockdownEnabled(context.getSource(), true)))
                        .then(CommandManager.literal("disable")
                                .executes(context -> setLockdownEnabled(context.getSource(), false)))
                )
                .then(CommandManager.literal("reload").executes(context -> reloadEverything(context.getSource())))
        );
    }

    private static int setSelectionPosition(ServerCommandSource source, boolean first, BlockPos position)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AreaSelection selection = SELECTIONS.computeIfAbsent(player.getUuid(), ignored -> new AreaSelection());
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (first) selection.setFirst(position, dimension); else selection.setSecond(position, dimension);
        source.sendFeedback(() -> Text.literal((first ? "Area position 1" : "Area position 2")
                + " set to " + position.toShortString()), false);
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
        NamedArea area = NamedArea.between(selection.dimension(), selection.first(), selection.second(), name.trim());
        AreaStore.put(name, area);
        ConfigSyncService.broadcastSnapshots(source.getServer());
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
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            sendBorderPayload(player, hiddenBorder(normalized));
        }
        ConfigSyncService.broadcastSnapshots(source.getServer());
        source.sendFeedback(() -> Text.literal("Removed area '" + name + "'."), true);
        return 1;
    }

    private static int showAreaInfo(ServerCommandSource source, String name) {
        NamedArea area = AreaStore.get(name).orElse(null);
        if (area == null) {
            source.sendError(Text.literal("No named area called '" + name + "'."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Area '" + areaDisplayName(name, area) + "' in "
                + area.dimension() + ": " + area.cornersString()), false);
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
                false));
        return AreaStore.all().size();
    }

    private static int showAreaBorder(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        NamedArea area = AreaStore.get(name).orElse(null);
        if (area == null) {
            source.sendError(Text.literal("No named area called '" + name + "'."));
            return 0;
        }
        sendBorderPayload(player, visibleBorder(WorldDataStore.normalize(name), area));
        source.sendFeedback(() -> Text.literal("Showing border for area '" + name
                + "'. Use /moodswings area hide " + quoteIfNeeded(name) + " to hide it."), false);
        return 1;
    }

    private static int showAllAreaBorders(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        int count = 0;
        for (Map.Entry<String, NamedArea> entry : AreaStore.all().entrySet()) {
            sendBorderPayload(player, visibleBorder(entry.getKey(), entry.getValue()));
            count++;
        }
        final int shown = count;
        source.sendFeedback(() -> Text.literal("Showing " + shown + " saved area border(s)."), false);
        return count;
    }

    private static int hideAreaBorder(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        sendBorderPayload(player, hiddenBorder(WorldDataStore.normalize(name)));
        source.sendFeedback(() -> Text.literal("Hidden border for area '" + name + "'."), false);
        return 1;
    }

    private static int hideAllAreaBorders(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        sendBorderPayload(player, new AreaBorderPayload(AreaBorderPayload.CLEAR, "", "", 0, 0, 0, 0, 0, 0));
        source.sendFeedback(() -> Text.literal("Hidden all Mood Swings area borders."), false);
        return 1;
    }

    private static AreaBorderPayload visibleBorder(String id, NamedArea area) {
        return new AreaBorderPayload(
                AreaBorderPayload.SHOW,
                id,
                area.dimension(),
                area.minX(), area.minY(), area.minZ(),
                area.maxX(), area.maxY(), area.maxZ()
        );
    }

    private static AreaBorderPayload hiddenBorder(String id) {
        return new AreaBorderPayload(AreaBorderPayload.HIDE, id, "", 0, 0, 0, 0, 0, 0);
    }

    private static void sendBorderPayload(ServerPlayerEntity player, AreaBorderPayload payload) {
        if (ServerPlayNetworking.canSend(player, AreaBorderPayload.ID)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static int setLockdownSelectionPosition(ServerCommandSource source, boolean first, BlockPos position)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AreaSelection selection = LOCKDOWN_SELECTIONS.computeIfAbsent(player.getUuid(), ignored -> new AreaSelection());
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (first) {
            selection.setFirst(position, dimension);
        } else {
            selection.setSecond(position, dimension);
        }
        source.sendFeedback(() -> Text.literal((first ? "Lockdown area position 1" : "Lockdown area position 2")
                + " set to " + position.toShortString()), false);
        return 1;
    }

    private static int saveLockdownArea(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AreaSelection selection = LOCKDOWN_SELECTIONS.get(player.getUuid());
        String normalized = WorldDataStore.normalize(name);
        if (normalized.isBlank()) {
            source.sendError(Text.literal("The Lockdown area name cannot be empty."));
            return 0;
        }
        if (selection == null || !selection.isComplete()) {
            source.sendError(Text.literal("Set both corners first with /moodswings lockdown area pos1 and pos2."));
            return 0;
        }
        NamedArea area = NamedArea.between(
                selection.dimension(), selection.first(), selection.second(), name.trim()
        );
        LockdownAreaStore.put(normalized, area);
        ConfigSyncService.broadcastSnapshots(source.getServer());
        source.sendFeedback(() -> Text.literal("Saved Lockdown area '" + normalized + "': " + area.cornersString()), true);
        return 1;
    }

    private static int removeLockdownArea(ServerCommandSource source, String name) {
        String normalized = WorldDataStore.normalize(name);
        if (!LockdownAreaStore.remove(normalized)) {
            source.sendError(Text.literal("No Lockdown area called '" + name + "'."));
            return 0;
        }
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            sendBorderPayload(player, hiddenBorder(lockdownBorderId(normalized)));
        }
        ConfigSyncService.broadcastSnapshots(source.getServer());
        source.sendFeedback(() -> Text.literal("Removed Lockdown area '" + normalized + "'."), true);
        return 1;
    }

    private static int showLockdownAreaInfo(ServerCommandSource source, String name) {
        String normalized = WorldDataStore.normalize(name);
        NamedArea area = LockdownAreaStore.get(normalized).orElse(null);
        if (area == null) {
            source.sendError(Text.literal("No Lockdown area called '" + name + "'."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Lockdown area '" + areaDisplayName(normalized, area) + "' in "
                + area.dimension() + ": " + area.cornersString()), false);
        return 1;
    }

    private static int listLockdownAreas(ServerCommandSource source) {
        if (LockdownAreaStore.all().isEmpty()) {
            source.sendFeedback(() -> Text.literal("No Lockdown areas have been saved in this world."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal("Lockdown areas in this save:"), false);
        LockdownAreaStore.all().forEach((id, area) -> source.sendFeedback(
                () -> Text.literal("• " + areaDisplayName(id, area) + " [" + id + "]  " + area.cornersString()), false));
        return LockdownAreaStore.all().size();
    }

    private static int showLockdownAreaBorder(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String normalized = WorldDataStore.normalize(name);
        NamedArea area = LockdownAreaStore.get(normalized).orElse(null);
        if (area == null) {
            source.sendError(Text.literal("No Lockdown area called '" + name + "'."));
            return 0;
        }
        sendBorderPayload(player, visibleBorder(lockdownBorderId(normalized), area));
        source.sendFeedback(() -> Text.literal("Showing border for Lockdown area '" + normalized + "'."), false);
        return 1;
    }

    private static int showAllLockdownAreaBorders(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        int count = 0;
        for (Map.Entry<String, NamedArea> entry : LockdownAreaStore.all().entrySet()) {
            sendBorderPayload(player, visibleBorder(lockdownBorderId(entry.getKey()), entry.getValue()));
            count++;
        }
        final int shown = count;
        source.sendFeedback(() -> Text.literal("Showing " + shown + " Lockdown area border(s)."), false);
        return count;
    }

    private static int hideLockdownAreaBorder(ServerCommandSource source, String name) throws CommandSyntaxException {
        String normalized = WorldDataStore.normalize(name);
        sendBorderPayload(source.getPlayerOrThrow(), hiddenBorder(lockdownBorderId(normalized)));
        source.sendFeedback(() -> Text.literal("Hidden border for Lockdown area '" + normalized + "'."), false);
        return 1;
    }

    private static int hideAllLockdownAreaBorders(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        for (String id : LockdownAreaStore.all().keySet()) {
            sendBorderPayload(player, hiddenBorder(lockdownBorderId(id)));
        }
        source.sendFeedback(() -> Text.literal("Hidden all Lockdown area borders."), false);
        return 1;
    }

    private static int setOstSelectionPosition(ServerCommandSource source, boolean first, BlockPos position)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AreaSelection selection = OST_SELECTIONS.computeIfAbsent(player.getUuid(), ignored -> new AreaSelection());
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (first) {
            selection.setFirst(position, dimension);
        } else {
            selection.setSecond(position, dimension);
        }
        source.sendFeedback(() -> Text.literal((first ? "OST area position 1" : "OST area position 2")
                + " set to " + position.toShortString()), false);
        return 1;
    }

    private static int saveOstArea(ServerCommandSource source, String name, String soundId)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AreaSelection selection = OST_SELECTIONS.get(player.getUuid());
        String normalized = WorldDataStore.normalize(name);
        if (normalized.isBlank()) {
            source.sendError(Text.literal("The OST area name cannot be empty."));
            return 0;
        }
        if (Identifier.tryParse(soundId) == null) {
            source.sendError(Text.literal("'" + soundId + "' is not a valid sound identifier."));
            return 0;
        }
        if (selection == null || !selection.isComplete()) {
            source.sendError(Text.literal("Set both corners first with /moodswings ostarea pos1 and pos2."));
            return 0;
        }
        OstArea area = OstArea.between(
                selection.dimension(), selection.first(), selection.second(), name.trim(), soundId
        ).validated();
        OstAreaStore.put(normalized, area);
        ConfigSyncService.broadcastSnapshots(source.getServer());
        OstAreaSystem.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal("Saved OST area '" + normalized + "' using " + soundId
                + ". Volume 1.0, priority 0, fade 2.0s, screen shake 0.0, darkness 0.0."), true);
        return 1;
    }

    private static int removeOstArea(ServerCommandSource source, String name) {
        String normalized = WorldDataStore.normalize(name);
        if (!OstAreaStore.remove(normalized)) {
            source.sendError(Text.literal("No OST area called '" + name + "'."));
            return 0;
        }
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            sendBorderPayload(player, hiddenBorder(ostBorderId(normalized)));
        }
        ConfigSyncService.broadcastSnapshots(source.getServer());
        OstAreaSystem.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal("Removed OST area '" + normalized + "'."), true);
        return 1;
    }

    private static int showOstAreaInfo(ServerCommandSource source, String name) {
        String normalized = WorldDataStore.normalize(name);
        OstArea area = OstAreaStore.get(normalized).orElse(null);
        if (area == null) {
            source.sendError(Text.literal("No OST area called '" + name + "'."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("OST area '" + normalized + "' in " + area.dimension()
                + ": " + area.cornersString()), false);
        source.sendFeedback(() -> Text.literal("Sound " + area.soundId() + " | volume " + area.volume()
                + " | priority " + area.priority() + " | fade " + area.fadeSeconds() + "s"
                + " | screen shake " + area.shakeIntensity() + " | darkness " + area.darkness()), false);
        return 1;
    }

    private static int listOstAreas(ServerCommandSource source) {
        if (OstAreaStore.all().isEmpty()) {
            source.sendFeedback(() -> Text.literal("No OST areas have been saved in this world."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal("OST areas in this save ("
                + (WorldDataStore.areOstAreasEnabled() ? "enabled" : "disabled") + "):"), false);
        OstAreaStore.all().forEach((id, area) -> source.sendFeedback(
                () -> Text.literal("• " + id + " | " + area.soundId() + " | volume " + area.volume()
                        + " | priority " + area.priority() + " | fade " + area.fadeSeconds() + "s"
                        + " | screen shake " + area.shakeIntensity() + " | darkness " + area.darkness()), false));
        return OstAreaStore.all().size();
    }

    private static int showOstAreaBorder(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String normalized = WorldDataStore.normalize(name);
        OstArea area = OstAreaStore.get(normalized).orElse(null);
        if (area == null) {
            source.sendError(Text.literal("No OST area called '" + name + "'."));
            return 0;
        }
        sendBorderPayload(player, visibleOstBorder(normalized, area));
        source.sendFeedback(() -> Text.literal("Showing border for OST area '" + normalized + "'."), false);
        return 1;
    }

    private static int showAllOstAreaBorders(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        int count = 0;
        for (Map.Entry<String, OstArea> entry : OstAreaStore.all().entrySet()) {
            sendBorderPayload(player, visibleOstBorder(entry.getKey(), entry.getValue()));
            count++;
        }
        final int shown = count;
        source.sendFeedback(() -> Text.literal("Showing " + shown + " OST area border(s)."), false);
        return count;
    }

    private static int hideOstAreaBorder(ServerCommandSource source, String name) throws CommandSyntaxException {
        sendBorderPayload(source.getPlayerOrThrow(), hiddenBorder(ostBorderId(WorldDataStore.normalize(name))));
        source.sendFeedback(() -> Text.literal("Hidden border for OST area '" + name + "'."), false);
        return 1;
    }

    private static int hideAllOstAreaBorders(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        for (String id : OstAreaStore.all().keySet()) {
            sendBorderPayload(player, hiddenBorder(ostBorderId(id)));
        }
        source.sendFeedback(() -> Text.literal("Hidden all OST area borders."), false);
        return 1;
    }

    private static int setOstAreaSound(ServerCommandSource source, String name, String soundId) {
        if (Identifier.tryParse(soundId) == null) {
            source.sendError(Text.literal("'" + soundId + "' is not a valid sound identifier."));
            return 0;
        }
        return updateOstArea(source, name, area -> area.withSound(soundId),
                "Set OST area '" + WorldDataStore.normalize(name) + "' sound to " + soundId + ".");
    }

    private static int setOstAreaVolume(ServerCommandSource source, String name, float volume) {
        return updateOstArea(source, name, area -> area.withVolume(volume),
                "Set OST area '" + WorldDataStore.normalize(name) + "' volume to " + volume + ".");
    }

    private static int setOstAreaPriority(ServerCommandSource source, String name, int priority) {
        return updateOstArea(source, name, area -> area.withPriority(priority),
                "Set OST area '" + WorldDataStore.normalize(name) + "' priority to " + priority + ".");
    }

    private static int setOstAreaFade(ServerCommandSource source, String name, float seconds) {
        return updateOstArea(source, name, area -> area.withFadeSeconds(seconds),
                "Set OST area '" + WorldDataStore.normalize(name) + "' fade to " + seconds + " seconds.");
    }

    private static int setOstAreaShake(ServerCommandSource source, String name, float strength) {
        return updateOstArea(source, name, area -> area.withShakeIntensity(strength),
                "Set OST area '" + WorldDataStore.normalize(name) + "' screen shake to " + strength + ".");
    }

    private static int setOstAreaDarkness(ServerCommandSource source, String name, float strength) {
        return updateOstArea(source, name, area -> area.withDarkness(strength),
                "Set OST area '" + WorldDataStore.normalize(name) + "' world darkening to " + strength + ".");
    }

    private static int updateOstArea(
            ServerCommandSource source,
            String name,
            java.util.function.UnaryOperator<OstArea> updater,
            String successMessage
    ) {
        String normalized = WorldDataStore.normalize(name);
        OstArea current = OstAreaStore.get(normalized).orElse(null);
        if (current == null) {
            source.sendError(Text.literal("No OST area called '" + name + "'."));
            return 0;
        }
        OstAreaStore.put(normalized, updater.apply(current));
        ConfigSyncService.broadcastSnapshots(source.getServer());
        OstAreaSystem.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal(successMessage), true);
        return 1;
    }

    private static int setOstAreasEnabled(ServerCommandSource source, boolean enabled) {
        boolean changed = WorldDataStore.setOstAreasEnabled(enabled);
        if (!enabled) {
            OstAreaSystem.stopAll(source.getServer());
        } else {
            OstAreaSystem.refreshAll(source.getServer());
        }
        ConfigSyncService.broadcastSnapshots(source.getServer());
        source.sendFeedback(() -> Text.literal("OST areas are now " + (enabled ? "enabled" : "disabled")
                + " in this save."), true);
        return changed ? 1 : 0;
    }

    private static int setLockdownEnabled(ServerCommandSource source, boolean enabled) {
        boolean changed = WorldDataStore.setLockdownEnabled(enabled);
        LockdownSystem.setShopEntryEnabled(enabled);
        ConfigSyncService.broadcastSnapshots(source.getServer());
        source.sendFeedback(() -> Text.literal("Lockdown is now " + (enabled ? "enabled" : "disabled")
                + " in this save."), true);
        return changed ? 1 : 0;
    }

    private static AreaBorderPayload visibleOstBorder(String id, OstArea area) {
        return new AreaBorderPayload(
                AreaBorderPayload.SHOW,
                ostBorderId(id),
                area.dimension(),
                area.minX(), area.minY(), area.minZ(),
                area.maxX(), area.maxY(), area.maxZ()
        );
    }

    private static String ostBorderId(String id) {
        return "ost/" + WorldDataStore.normalize(id);
    }

    private static String lockdownBorderId(String id) {
        return "lockdown/" + WorldDataStore.normalize(id);
    }

    private static int forceTask(ServerCommandSource source, String taskInput) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map.Entry<Identifier, TaskDefinition> resolved = TaskDefinitionRegistry.resolve(taskInput).orElse(null);
        if (resolved == null) {
            source.sendError(Text.literal("Unknown JSON task '" + taskInput + "'."));
            return 0;
        }
        String taskId = resolved.getKey().toString();
        if (!WorldDataStore.isTaskEnabled(taskId)) {
            source.sendError(Text.literal("Task '" + taskId + "' is disabled in this save."));
            return 0;
        }
        if (!resolved.getValue().isAvailableTo(player)) {
            source.sendError(Text.literal("Task '" + taskId + "' is not available for "
                    + player.getName().getString() + "'s role."));
            return 0;
        }
        if (!TaskConditionEvaluator.isTaskAvailable(player, resolved.getValue())) {
            source.sendError(Text.literal("Task '" + taskId + "' is missing a required named area."));
            return 0;
        }

        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
        component.tasks.clear();
        PlayerMoodComponent.Task storage = CustomTrainTask.chooseStorageType(component.timesGotten);
        CustomTrainTask task = CustomTrainTask.create(player, resolved.getKey(), resolved.getValue(), storage, true);
        if (task == null) {
            source.sendError(Text.literal("No valid target could be selected for that task."));
            return 0;
        }
        component.tasks.put(storage, task);
        ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
        component.sync();
        source.sendFeedback(() -> Text.literal("Forced task '" + taskId + "' for "
                + player.getName().getString() + "."), false);
        return 1;
    }

    private static int clearTask(ServerCommandSource source, ServerPlayerEntity player) {
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
        component.tasks.clear();
        ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
        component.sync();

        if (source.getEntity() == player) {
            source.sendFeedback(() -> Text.literal("Cleared your mood task. Normal rotation will resume."), false);
        } else {
            source.sendFeedback(() -> Text.literal("Cleared " + player.getName().getString()
                    + "'s mood task. Normal rotation will resume."), false);
        }
        return 1;
    }

    private static int setTaskEnabled(ServerCommandSource source, String taskInput, boolean enabled) {
        if (!TaskRegistry.isKnown(taskInput)) {
            source.sendError(Text.literal("Unknown task '" + taskInput + "'."));
            return 0;
        }
        String taskId = TaskRegistry.canonicalize(taskInput);
        boolean changed = enabled ? WorldDataStore.enableTask(taskId) : WorldDataStore.disableTask(taskId);
        if (!enabled) {
            removeTaskFromOnlinePlayers(source, taskId);
        }
        ConfigSyncService.broadcastSnapshots(source.getServer());
        source.sendFeedback(() -> Text.literal("Task '" + taskId + "' is now "
                + (enabled ? "enabled" : "disabled") + " in this save."), true);
        return changed ? 1 : 0;
    }

    private static int listTaskSettings(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Mood tasks for this save:"), false);
        for (String taskId : TaskRegistry.allIds()) {
            boolean enabled = WorldDataStore.isTaskEnabled(taskId);
            Formatting color = enabled ? Formatting.GREEN : Formatting.RED;
            source.sendFeedback(() -> Text.literal("• " + taskId + ": "
                    + (enabled ? "ENABLED" : "DISABLED")).formatted(color), false);
        }
        return TaskRegistry.allIds().size();
    }

    private static int reloadEverything(ServerCommandSource source) {
        int loaded = TaskDefinitionRegistry.reload(source.getServer().getResourceManager());
        removeUnavailableTasksFromOnlinePlayers(source);
        ConfigSyncService.broadcastSnapshots(source.getServer());
        source.sendFeedback(() -> Text.literal("Reloaded " + loaded
                + " Mood Swings JSON task definition(s)."), true);
        return loaded;
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
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
            boolean removed = component.tasks.entrySet().removeIf(entry ->
                    entry.getValue() instanceof CustomTrainTask custom
                            && TaskConditionEvaluator.referencesArea(custom, removedAreaId));
            if (removed) {
                ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
                component.sync();
            }
        }
    }

    private static void removeUnavailableTasksFromOnlinePlayers(ServerCommandSource source) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
            boolean removed = component.tasks.entrySet().removeIf(entry -> {
                if (!(entry.getValue() instanceof CustomTrainTask custom)) {
                    return false;
                }
                TaskDefinition current = TaskDefinitionRegistry.get(custom.taskId()).orElse(null);
                return current == null || !WorldDataStore.isTaskEnabled(custom.taskId().toString())
                        || !TaskConditionEvaluator.isTaskAvailable(player, current);
            });
            if (removed) {
                ((PlayerMoodComponentAccess) component).moodSwings$setNextTaskTimer(2);
                component.sync();
            }
        }
    }

    private static String areaDisplayName(String key, NamedArea area) {
        return area.displayName() != null && !area.displayName().isBlank() ? area.displayName() : key;
    }

    private static String quoteIfNeeded(String value) {
        return value.contains(" ") ? "\"" + value + "\"" : value;
    }
}
