package net.ceziak.mood_swings.client;

import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.config.MoodSwingsConfigManager;
import net.ceziak.mood_swings.network.AreaBorderPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/** Renders persistent, Wathe-style 3D cuboid outlines for named areas. */
public final class AreaBorderRenderer {
    private static final Map<String, Border> BORDERS = new LinkedHashMap<>();

    private AreaBorderRenderer() {
    }

    public static void handle(AreaBorderPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            switch (payload.action()) {
                case AreaBorderPayload.CLEAR -> BORDERS.clear();
                case AreaBorderPayload.HIDE -> BORDERS.remove(payload.areaId());
                case AreaBorderPayload.SHOW -> BORDERS.put(payload.areaId(), Border.from(payload));
                default -> {
                    // Ignore unknown actions so newer servers fail gracefully on older clients.
                }
            }
        });
    }

    public static void clear() {
        BORDERS.clear();
    }


    public static void show(String areaId, NamedArea area) {
        if (areaId == null || area == null) {
            return;
        }
        BORDERS.put(areaId, Border.from(area));
    }

    public static void hide(String areaId) {
        if (areaId != null) {
            BORDERS.remove(areaId);
        }
    }

    public static boolean isVisible(String areaId) {
        return areaId != null && BORDERS.containsKey(areaId);
    }

    public static boolean anyVisible() {
        return !BORDERS.isEmpty();
    }


    public static void render(WorldRenderContext context) {
        if (BORDERS.isEmpty() || context.world() == null) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        int color = MoodSwingsConfigManager.get().packedAreaBorderColor();
        float alpha = ((color >>> 24) & 255) / 255.0F;
        float red = ((color >>> 16) & 255) / 255.0F;
        float green = ((color >>> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        String dimension = context.world().getRegistryKey().getValue().toString();
        Vec3d camera = context.camera().getPos();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        for (Border border : BORDERS.values()) {
            if (!dimension.equals(border.dimension())) {
                continue;
            }
            WorldRenderer.drawBox(matrices, lines, border.box(), red, green, blue, alpha);
        }
        matrices.pop();
    }

    private record Border(String dimension, Box box) {
        private static Border from(NamedArea area) {
            return new Border(
                    area.dimension(),
                    new Box(
                            area.minX(), area.minY(), area.minZ(),
                            area.maxX() + 1.0D, area.maxY() + 1.0D, area.maxZ() + 1.0D
                    )
            );
        }

        private static Border from(AreaBorderPayload payload) {
            // Named areas use inclusive block coordinates. Adding one to the max
            // corner makes the wireframe wrap the complete outer block faces.
            return new Border(
                    payload.dimension(),
                    new Box(
                            payload.minX(), payload.minY(), payload.minZ(),
                            payload.maxX() + 1.0D, payload.maxY() + 1.0D, payload.maxZ() + 1.0D
                    )
            );
        }
    }
}
