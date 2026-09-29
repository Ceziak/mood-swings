package net.ceziak.mood_swings.mixin.client;

import net.ceziak.mood_swings.client.MurderTaskClientEffects;
import net.ceziak.mood_swings.client.OstAreaClientEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Combines murder-task pressure with smooth, wind-like OST-area camera motion. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void moodSwings$applyCameraEffects(
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo callback
    ) {
        float murderStrength = MurderTaskClientEffects.shakeStrength(tickDelta);
        float ostStrength = OstAreaClientEffects.shakeDegrees(tickDelta);
        if ((murderStrength <= 0.0F && ostStrength <= 0.0F)
                || focusedEntity == null
                || MinecraftClient.getInstance().player != focusedEntity) {
            return;
        }

        float phase = focusedEntity.age + tickDelta;
        float yawShake = 0.0F;
        float pitchShake = 0.0F;

        if (murderStrength > 0.0F) {
            yawShake += (
                    MathHelper.sin(phase * 0.73F) * 0.65F
                            + MathHelper.sin(phase * 2.37F) * 0.35F
            ) * murderStrength;
            pitchShake += (
                    MathHelper.cos(phase * 0.89F) * 0.45F
                            + MathHelper.sin(phase * 3.11F) * 0.25F
            ) * murderStrength;
        }

        if (ostStrength > 0.0F) {
            // Slow overlapping waves feel like gusting wind rather than vibration.
            float gust = 0.72F + MathHelper.sin(phase * 0.031F) * 0.28F;
            yawShake += (
                    MathHelper.sin(phase * 0.082F) * 0.72F
                            + MathHelper.sin(phase * 0.193F) * 0.28F
            ) * ostStrength * gust;
            pitchShake += (
                    MathHelper.cos(phase * 0.097F) * 0.52F
                            + MathHelper.sin(phase * 0.217F) * 0.24F
            ) * ostStrength * gust;
        }

        setRotation(
                getYaw() + yawShake,
                MathHelper.clamp(getPitch() + pitchShake, -90.0F, 90.0F)
        );
    }
}
