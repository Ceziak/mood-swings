package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.block.SprinklerBlock;
import net.ceziak.mood_swings.area.AreaStore;
import net.ceziak.mood_swings.config.MoodSwingsConfig;
import net.ceziak.mood_swings.config.MoodSwingsConfigManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class CustomTaskConditions {
    private CustomTaskConditions() {
    }

    public static boolean isDurationConditionMet(ServerPlayerEntity player, CustomTrainTask task) {
        return switch (task.customType()) {
            case TAKE_A_SHOWER -> isUnderActiveShower(player);
            case SOCIALIZE -> isNearAnotherPlayer(player);
            case GYM -> isInsideConfiguredArea(player, MoodSwingsConfigManager.get().safeGymAreaId());
            case STRANGE_NOISE -> task.targetAreaId() != null && isInsideConfiguredArea(player, task.targetAreaId());
            case ROOFTOP -> isInsideConfiguredArea(player, MoodSwingsConfigManager.get().safeRooftopAreaId());
            case WALK -> false;
        };
    }

    public static int requiredTicks(CustomTaskType type) {
        MoodSwingsConfig config = MoodSwingsConfigManager.get();
        return switch (type) {
            case TAKE_A_SHOWER -> config.showerDurationTicks();
            case SOCIALIZE -> config.socializeDurationTicks();
            case GYM -> config.gymDurationTicks();
            case STRANGE_NOISE -> config.strangeNoiseDurationTicks();
            case ROOFTOP -> config.rooftopDurationTicks();
            case WALK -> 0;
        };
    }

    public static boolean isTaskAvailable(ServerPlayerEntity player, CustomTaskType type) {
        MoodSwingsConfig config = MoodSwingsConfigManager.get();
        return switch (type) {
            case GYM -> isAreaAvailableForPlayer(player, config.safeGymAreaId());
            case ROOFTOP -> isAreaAvailableForPlayer(player, config.safeRooftopAreaId());
            case STRANGE_NOISE -> !AreaStore.inPlayerDimension(player).isEmpty();
            default -> true;
        };
    }

    private static boolean isInsideConfiguredArea(ServerPlayerEntity player, String areaId) {
        return AreaStore.get(areaId).map(area -> area.contains(player)).orElse(false);
    }

    private static boolean isAreaAvailableForPlayer(ServerPlayerEntity player, String areaId) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        return AreaStore.get(areaId)
                .filter(area -> dimension.equals(area.dimension()))
                .isPresent();
    }

    private static boolean isUnderActiveShower(ServerPlayerEntity player) {
        MoodSwingsConfig config = MoodSwingsConfigManager.get();
        BlockPos playerPos = player.getBlockPos();
        BlockPos.Mutable checkPos = new BlockPos.Mutable();

        for (int offsetY = 1; offsetY <= config.safeShowerScanHeight(); offsetY++) {
            checkPos.set(playerPos.getX(), playerPos.getY() + offsetY, playerPos.getZ());
            BlockState state = player.getWorld().getBlockState(checkPos);
            if (!state.isIn(MoodSwingsTags.SHOWER_HEADS)) {
                continue;
            }

            if (state.getBlock() instanceof SprinklerBlock) {
                if (state.get(SprinklerBlock.POWERED)
                        && SprinklerBlock.getDirection(state) == Direction.DOWN) {
                    return true;
                }
                continue;
            }

            if (state.contains(Properties.POWERED) && !state.get(Properties.POWERED)) {
                continue;
            }
            if (state.contains(Properties.FACING) && state.get(Properties.FACING) != Direction.DOWN) {
                continue;
            }
            return true;
        }

        return false;
    }

    private static boolean isNearAnotherPlayer(ServerPlayerEntity player) {
        double range = MoodSwingsConfigManager.get().safeSocializeRange();
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
}
