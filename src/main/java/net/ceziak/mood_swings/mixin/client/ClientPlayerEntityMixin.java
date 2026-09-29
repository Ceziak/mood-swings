package net.ceziak.mood_swings.mixin.client;

import net.ceziak.mood_swings.client.MurderTaskClientEffects;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents the overdue murder compulsion from being bypassed by holding Sprint. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void moodSwings$stopOverdueMurderSprint(CallbackInfo callback) {
        if (MurderTaskClientEffects.isSprintBlocked()) {
            ((ClientPlayerEntity) (Object) this).setSprinting(false);
        }
    }
}
