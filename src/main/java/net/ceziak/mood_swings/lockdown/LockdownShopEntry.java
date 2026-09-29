package net.ceziak.mood_swings.lockdown;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;

/** Marker entry intercepted before Wathe performs its ordinary instant purchase. */
public final class LockdownShopEntry extends ShopEntry {
    public LockdownShopEntry() {
        super(LockdownSystem.LOCKDOWN_ICON.getDefaultStack(), LockdownSystem.PRICE, ShopEntry.Type.TOOL);
    }

    @Override
    public boolean onBuy(PlayerEntity player) {
        // PlayerShopComponentMixin opens the area selector before this fallback can run.
        return false;
    }
}
