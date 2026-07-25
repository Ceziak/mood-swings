package net.ceziak.mood_swings.task;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class MoodSwingsTags {
    public static final TagKey<Block> SHOWER_HEADS = TagKey.of(
            RegistryKeys.BLOCK,
            MoodSwings.id("shower_heads")
    );

    private MoodSwingsTags() {
    }
}
