package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.block.SprinklerBlock;
import net.ceziak.mood_swings.area.AreaStore;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public final class TaskConditionEvaluator {
    private static final double FOOTPRINT_MARGIN = 0.12D;

    private TaskConditionEvaluator() {
    }

    public static boolean isDurationConditionMet(ServerPlayerEntity player, CustomTrainTask task) {
        TaskConditionDefinition condition = task.definition().condition();
        TaskConditionType type = condition.parsedType();
        if (type == null) {
            return false;
        }

        return switch (type) {
            case STAY_IN_AREA -> isInsideArea(player, condition.area());
            case RANDOM_AREA -> task.targetAreaId() != null && isInsideArea(player, task.targetAreaId());
            case NEAR_PLAYER -> isNearAnotherPlayer(player, condition.safeRange());
            case UNDER_BLOCK -> isMatchingBlockInVerticalDirection(player, condition, true);
            case ABOVE_BLOCK -> isMatchingBlockInVerticalDirection(player, condition, false);
            case TRAVEL_DISTANCE, KILL_PLAYER -> false;
        };
    }

    public static int requiredTicks(TaskDefinition definition) {
        return definition.condition().durationTicks();
    }

    public static boolean isTaskAvailable(ServerPlayerEntity player, TaskDefinition definition) {
        if (!definition.isAvailableTo(player)) {
            return false;
        }

        TaskConditionDefinition condition = definition.condition();
        TaskConditionType type = condition.parsedType();
        if (type == null) {
            return false;
        }

        return switch (type) {
            case STAY_IN_AREA -> isAreaAvailableForPlayer(player, condition.area());
            case RANDOM_AREA -> !AreaStore.inPlayerDimension(player).isEmpty();
            case TRAVEL_DISTANCE, NEAR_PLAYER, UNDER_BLOCK, ABOVE_BLOCK, KILL_PLAYER -> true;
        };
    }

    /** Checks validity of an already-created task, including its chosen random-area target. */
    public static boolean isActiveTaskValid(ServerPlayerEntity player, CustomTrainTask task) {
        TaskDefinition definition = task.definition();
        if (!definition.isAvailableTo(player)) {
            return false;
        }

        TaskConditionType type = definition.condition().parsedType();
        if (type == null) {
            return false;
        }

        return switch (type) {
            case STAY_IN_AREA -> isAreaAvailableForPlayer(player, definition.condition().area());
            case RANDOM_AREA -> task.targetAreaId() != null
                    && isAreaAvailableForPlayer(player, task.targetAreaId());
            case TRAVEL_DISTANCE, NEAR_PLAYER, UNDER_BLOCK, ABOVE_BLOCK, KILL_PLAYER -> true;
        };
    }

    public static boolean referencesArea(CustomTrainTask task, String areaId) {
        String normalized = net.ceziak.mood_swings.config.WorldDataStore.normalize(areaId);
        TaskConditionDefinition condition = task.definition().condition();
        TaskConditionType type = condition.parsedType();
        if (type == TaskConditionType.STAY_IN_AREA) {
            return normalized.equals(net.ceziak.mood_swings.config.WorldDataStore.normalize(condition.area()));
        }
        return type == TaskConditionType.RANDOM_AREA
                && normalized.equals(net.ceziak.mood_swings.config.WorldDataStore.normalize(task.targetAreaId()));
    }

    private static boolean isInsideArea(ServerPlayerEntity player, String areaId) {
        return AreaStore.get(areaId).map(area -> area.contains(player)).orElse(false);
    }

    private static boolean isAreaAvailableForPlayer(ServerPlayerEntity player, String areaId) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        return AreaStore.get(areaId)
                .filter(area -> dimension.equals(area.dimension()))
                .isPresent();
    }

    private static boolean isNearAnotherPlayer(ServerPlayerEntity player, double range) {
        double rangeSquared = range * range;
        for (PlayerEntity other : player.getWorld().getPlayers()) {
            if (other == player || other.isSpectator() || !other.isAlive()) {
                continue;
            }
            if (player.squaredDistanceTo(other) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMatchingBlockInVerticalDirection(
            ServerPlayerEntity player,
            TaskConditionDefinition condition,
            boolean abovePlayer
    ) {
        Identifier tagId = Identifier.tryParse(condition.blockTag());
        if (tagId == null) {
            return false;
        }
        TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, tagId);
        Direction requiredDirection = condition.requiredFacing().isBlank()
                ? null
                : Direction.byName(condition.requiredFacing());

        Box footprint = player.getBoundingBox().expand(FOOTPRINT_MARGIN, 0.0D, FOOTPRINT_MARGIN);
        int minX = MathHelper.floor(footprint.minX + 1.0E-6D);
        int maxX = MathHelper.floor(footprint.maxX - 1.0E-6D);
        int minZ = MathHelper.floor(footprint.minZ + 1.0E-6D);
        int maxZ = MathHelper.floor(footprint.maxZ - 1.0E-6D);
        int playerY = player.getBlockY();
        int scanDistance = abovePlayer ? condition.safeScanHeight() : condition.safeScanDepth();
        BlockPos.Mutable checkPos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int offsetY = 1; offsetY <= scanDistance; offsetY++) {
                    int y = playerY + (abovePlayer ? offsetY : -offsetY);
                    checkPos.set(x, y, z);
                    BlockState state = player.getWorld().getBlockState(checkPos);
                    if (!state.isIn(tag)) {
                        continue;
                    }
                    if (condition.requirePowered() && !isPowered(state)) {
                        continue;
                    }
                    if (requiredDirection != null && getFacing(state) != requiredDirection) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPowered(BlockState state) {
        if (state.getBlock() instanceof SprinklerBlock) {
            return state.get(SprinklerBlock.POWERED);
        }
        return state.contains(Properties.POWERED) && state.get(Properties.POWERED);
    }

    private static Direction getFacing(BlockState state) {
        if (state.getBlock() instanceof SprinklerBlock) {
            return SprinklerBlock.getDirection(state);
        }
        if (state.contains(Properties.FACING)) {
            return state.get(Properties.FACING);
        }
        return null;
    }
}
