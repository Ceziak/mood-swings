package net.ceziak.mood_swings.area;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

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
    private static final double PLAYER_EDGE_TOLERANCE = 0.05D;

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

        // Treat selected coordinates as complete block volumes rather than only integer points.
        // A tiny expansion also prevents standing exactly on a border from flickering in/out.
        Box areaBox = new Box(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
        return areaBox.intersects(player.getBoundingBox().expand(PLAYER_EDGE_TOLERANCE));
    }

    public String cornersString() {
        return "(" + minX + ", " + minY + ", " + minZ + ") -> ("
                + maxX + ", " + maxY + ", " + maxZ + ")";
    }
}
