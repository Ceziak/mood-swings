package net.ceziak.mood_swings.mixin;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.util.ShopEntry;
import net.ceziak.mood_swings.lockdown.LockdownShopEntry;
import net.ceziak.mood_swings.lockdown.LockdownSystem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Converts the custom shop slot from an instant item purchase into a validated area-selection flow. */
@Mixin(PlayerShopComponent.class)
public abstract class PlayerShopComponentMixin {
    @Inject(method = "tryBuy", at = @At("HEAD"), cancellable = true)
    private void moodSwings$openLockdownSelector(int index, CallbackInfo callback) {
        if (index < 0 || index >= GameConstants.SHOP_ENTRIES.size()) {
            return;
        }

        ShopEntry entry = GameConstants.SHOP_ENTRIES.get(index);
        if (!(entry instanceof LockdownShopEntry)) {
            return;
        }

        PlayerEntity owner = ((PlayerShopComponentAccessor) this).moodSwings$getPlayer();
        if (owner instanceof ServerPlayerEntity player) {
            LockdownSystem.openSelector(player);
        }
        callback.cancel();
    }
}
