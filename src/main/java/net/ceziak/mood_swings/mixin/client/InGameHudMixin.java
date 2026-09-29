package net.ceziak.mood_swings.mixin.client;

import net.ceziak.mood_swings.client.OstAreaClientEffects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Darkens the rendered world before vanilla draws any HUD elements. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void moodSwings$darkenWorld(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        float tickDelta = tickCounter.getTickDelta(false);
        int color = OstAreaClientEffects.darknessArgb(tickDelta);
        if (((color >>> 24) & 0xFF) == 0) {
            return;
        }
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), color);
    }
}
