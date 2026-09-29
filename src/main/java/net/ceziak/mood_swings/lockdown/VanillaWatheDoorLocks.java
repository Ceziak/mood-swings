package net.ceziak.mood_swings.lockdown;

import dev.doctor4t.wathe.index.WatheBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Adds Wathe-style temporary jamming to the two Wathe doors that are vanilla
 * DoorBlock instances rather than DoorBlockEntity-backed SmallDoorBlocks.
 */
public final class VanillaWatheDoorLocks {
    private static final Map<String, Map<Long, Long>> JAMMED_UNTIL = new HashMap<>();

    private VanillaWatheDoorLocks() {
    }

    public static boolean isSupported(BlockState state) {
        return state != null
                && (state.isOf(WatheBlocks.METAL_SHEET_DOOR) || state.isOf(WatheBlocks.COCKPIT_DOOR));
    }

    public static BlockPos lowerPos(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isSupported(state)) {
            return pos.toImmutable();
        }
        return state.contains(Properties.DOUBLE_BLOCK_HALF)
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                ? pos.down().toImmutable()
                : pos.toImmutable();
    }

    public static boolean jam(ServerWorld world, BlockPos pos, int ticks) {
        BlockPos lower = lowerPos(world, pos);
        BlockState state = world.getBlockState(lower);
        if (!isSupported(state) || !(state.getBlock() instanceof DoorBlock door)) {
            return false;
        }

        long expiresAt = world.getTime() + Math.max(1, ticks);
        Map<Long, Long> jams = JAMMED_UNTIL.computeIfAbsent(dimensionKey(world), ignored -> new HashMap<>());
        jams.merge(lower.asLong(), expiresAt, Math::max);

        if (state.contains(Properties.OPEN) && state.get(Properties.OPEN)) {
            door.setOpen(null, world, state, lower, false);
        }
        return true;
    }

    public static boolean isJammed(World world, BlockPos pos) {
        BlockPos lower = lowerPos(world, pos);
        BlockState state = world.getBlockState(lower);
        if (!isSupported(state)) {
            remove(world, lower);
            return false;
        }

        Map<Long, Long> jams = JAMMED_UNTIL.get(dimensionKey(world));
        if (jams == null) {
            return false;
        }
        Long expiresAt = jams.get(lower.asLong());
        if (expiresAt == null) {
            return false;
        }
        if (world.getTime() >= expiresAt) {
            jams.remove(lower.asLong());
            if (jams.isEmpty()) {
                JAMMED_UNTIL.remove(dimensionKey(world));
            }
            return false;
        }
        return true;
    }

    public static void clear(World world, BlockPos pos) {
        remove(world, lowerPos(world, pos));
    }

    public static void clearAll() {
        JAMMED_UNTIL.clear();
    }

    private static void remove(World world, BlockPos lower) {
        Map<Long, Long> jams = JAMMED_UNTIL.get(dimensionKey(world));
        if (jams == null) {
            return;
        }
        jams.remove(lower.asLong());
        if (jams.isEmpty()) {
            JAMMED_UNTIL.remove(dimensionKey(world));
        }
    }

    private static String dimensionKey(World world) {
        return world.getRegistryKey().getValue().toString();
    }
}
