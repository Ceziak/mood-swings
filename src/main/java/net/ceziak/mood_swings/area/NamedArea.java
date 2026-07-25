package net.ceziak.mood_swings.area;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public record NamedArea(
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        String displayName
) {
    public static NamedArea between(String dimension, BlockPos first, BlockPos second, String displayName) {
        return new NamedArea(
                dimension,
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()),
                displayName
        );
    }

    public boolean contains(ServerPlayerEntity player) {
        if (!dimension.equals(player.getWorld().getRegistryKey().getValue().toString())) {
            return false;
        }

        BlockPos pos = player.getBlockPos();
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public String cornersString() {
        return "(" + minX + ", " + minY + ", " + minZ + ") -> ("
                + maxX + ", " + maxY + ", " + maxZ + ")";
    }
}
