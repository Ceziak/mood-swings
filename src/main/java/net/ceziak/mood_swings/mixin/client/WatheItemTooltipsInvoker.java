package net.ceziak.mood_swings.mixin.client;

import dev.doctor4t.wathe.client.util.WatheItemTooltips;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Reuses Wathe's native red, localized cooldown line for addon shop items. */
@Mixin(WatheItemTooltips.class)
public interface WatheItemTooltipsInvoker {
    @Invoker("addCooldownText")
    static void moodSwings$addCooldownText(Item item, List<Text> tooltip, ItemStack stack) {
        throw new AssertionError();
    }
}
