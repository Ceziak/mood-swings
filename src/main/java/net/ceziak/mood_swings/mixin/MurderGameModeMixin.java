package net.ceziak.mood_swings.mixin;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import net.ceziak.mood_swings.task.CustomTaskRuntime;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wathe awards passive killer income directly from MurderGameMode's server loop.
 * PlayerShopComponent.serverTick is empty, so the payment call itself must be intercepted.
 */
@Mixin(MurderGameMode.class)
public abstract class MurderGameModeMixin {
    @Redirect(
            method = "tickServerGameLoop",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;addToBalance(I)V"
            )
    )
    private void moodSwings$blockPassiveIncome(PlayerShopComponent shop, int amount) {
        PlayerEntity owner = ((PlayerShopComponentAccessor) shop).moodSwings$getPlayer();
        if (!(owner instanceof ServerPlayerEntity player)
                || !CustomTaskRuntime.blocksPassiveIncome(player)) {
            shop.addToBalance(amount);
        }
    }
}
