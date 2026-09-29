package net.ceziak.mood_swings.lockdown;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Shop icon item whose tooltip follows Wathe's standard muted description style. */
public final class LockdownItem extends Item {
    private static final int WATHE_TOOLTIP_COLOR = 0x808080;

    public LockdownItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            List<Text> tooltip,
            TooltipType type
    ) {
        super.appendTooltip(stack, context, tooltip, type);
        addWatheStyleLine(tooltip, "item.mood_swings.lockdown.tooltip.primary");
        addWatheStyleLine(tooltip, "item.mood_swings.lockdown.tooltip.crowbar");
        addWatheStyleLine(tooltip, "item.mood_swings.lockdown.tooltip.cooldown");
    }

    private static void addWatheStyleLine(List<Text> tooltip, String translationKey) {
        tooltip.add(Text.translatable(translationKey)
                .styled(style -> style.withColor(WATHE_TOOLTIP_COLOR)));
    }
}
