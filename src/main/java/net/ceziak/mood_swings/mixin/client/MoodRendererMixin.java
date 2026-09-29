package net.ceziak.mood_swings.mixin.client;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.ceziak.mood_swings.access.TaskRendererAnimationAccess;
import net.ceziak.mood_swings.client.MurderTaskClientEffects;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Random;

@Mixin(targets = "dev.doctor4t.wathe.client.gui.MoodRenderer")
public abstract class MoodRendererMixin {
    @Shadow
    @Final
    private static Map<PlayerMoodComponent.Task, ?> renderers;

    @Shadow
    public static Random random;

    @Redirect(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"
            )
    )
    private static int moodSwings$animateMurderTask(
            DrawContext context,
            TextRenderer textRenderer,
            Text text,
            int x,
            int y,
            int color,
            PlayerEntity player,
            TextRenderer hudTextRenderer,
            DrawContext hudContext,
            RenderTickCounter tickCounter
    ) {
        TaskRendererAnimationAccess murderRenderer = moodSwings$findMurderRenderer(text);
        if (murderRenderer == null) {
            return context.drawTextWithShadow(textRenderer, text, x, y, color);
        }

        Text scrollingText = MurderTaskClientEffects.scrollingText();
        if (scrollingText.getString().isEmpty()) {
            scrollingText = text;
        }

        int stripWidth = Math.max(1, textRenderer.getWidth(scrollingText));
        float tickDelta = tickCounter.getTickDelta(true);
        float scrollOffset = MurderTaskClientEffects.scrollOffset(tickDelta) % stripWidth;

        // Wathe's psycho HUD reseeds every frame, then applies a tiny Gaussian shake.
        random.setSeed(System.currentTimeMillis());
        context.getMatrices().push();
        context.getMatrices().translate(
                random.nextGaussian() / 3.0D,
                random.nextGaussian() / 3.0D,
                0.0D
        );

        // The caller has already translated the matrix by 10 * task offset.
        // Scissor coordinates are screen-space, so include that offset explicitly.
        int scissorTop = Math.round(y + 10.0F * murderRenderer.moodSwings$getOffset());
        context.enableScissor(x, scissorTop, x + 180, scissorTop + 17);

        for (int copy = -1; copy <= 1; copy++) {
            int drawX = Math.round(x + copy * stripWidth - scrollOffset);
            context.drawTextWithShadow(textRenderer, scrollingText, drawX, y, color);
        }

        context.disableScissor();
        context.getMatrices().pop();
        return x + textRenderer.getWidth(text);
    }

    @Unique
    private static TaskRendererAnimationAccess moodSwings$findMurderRenderer(Text text) {
        for (Object renderer : renderers.values()) {
            if (renderer instanceof TaskRendererAnimationAccess access
                    && access.moodSwings$isMurderTask()
                    && access.moodSwings$getRenderedText() == text) {
                return access;
            }
        }
        return null;
    }
}
