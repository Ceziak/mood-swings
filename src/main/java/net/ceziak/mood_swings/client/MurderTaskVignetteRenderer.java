package net.ceziak.mood_swings.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Draws an exponentially darkening edge vignette for an overdue murder task. */
public final class MurderTaskVignetteRenderer {
    private static final int BANDS = 28;
    private static final int MAX_SIDE_ALPHA = 205;
    private static final int MAX_VERTICAL_ALPHA = 145;

    private MurderTaskVignetteRenderer() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        float strength = MurderTaskClientEffects.vignetteStrength(tickCounter.getTickDelta(true));
        if (strength <= 0.001F) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int sideWidth = Math.min(width / 2, Math.round(width * (0.12F + strength * 0.23F)));
        int verticalHeight = Math.min(height / 2, Math.round(height * (0.08F + strength * 0.17F)));

        for (int band = 0; band < BANDS; band++) {
            float edgeFactor = 1.0F - band / (float) BANDS;
            float falloff = edgeFactor * edgeFactor;

            int sideAlpha = Math.round(MAX_SIDE_ALPHA * strength * falloff);
            int verticalAlpha = Math.round(MAX_VERTICAL_ALPHA * strength * falloff);

            int sideStart = band * sideWidth / BANDS;
            int sideEnd = (band + 1) * sideWidth / BANDS;
            int topStart = band * verticalHeight / BANDS;
            int topEnd = (band + 1) * verticalHeight / BANDS;

            context.fill(sideStart, 0, sideEnd, height, sideAlpha << 24);
            context.fill(width - sideEnd, 0, width - sideStart, height, sideAlpha << 24);
            context.fill(0, topStart, width, topEnd, verticalAlpha << 24);
            context.fill(0, height - topEnd, width, height - topStart, verticalAlpha << 24);
        }
    }
}
