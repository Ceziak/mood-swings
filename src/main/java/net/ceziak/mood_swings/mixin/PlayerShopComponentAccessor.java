package net.ceziak.mood_swings.mixin;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Direct access to the owner of Wathe's shop component. */
@Mixin(PlayerShopComponent.class)
public interface PlayerShopComponentAccessor {
    @Accessor("player")
    PlayerEntity moodSwings$getPlayer();
}
