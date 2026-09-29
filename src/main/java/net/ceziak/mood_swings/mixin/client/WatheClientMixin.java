package net.ceziak.mood_swings.mixin.client;

import dev.doctor4t.wathe.client.WatheClient;
import net.ceziak.mood_swings.client.MurderTaskClientEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disables Wathe's instinct once the murder task has been ignored for two minutes. */
@Mixin(WatheClient.class)
public abstract class WatheClientMixin {
    @Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
    private static void moodSwings$blockOverdueMurderInstinct(CallbackInfoReturnable<Boolean> callback) {
        if (MurderTaskClientEffects.isInstinctBlocked()) {
            callback.setReturnValue(false);
        }
    }
}
