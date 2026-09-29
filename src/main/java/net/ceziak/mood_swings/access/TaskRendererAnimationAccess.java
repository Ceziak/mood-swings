package net.ceziak.mood_swings.access;

import net.minecraft.text.Text;

/** Client-side bridge used to identify the renderer currently displaying the murder task. */
public interface TaskRendererAnimationAccess {
    boolean moodSwings$isMurderTask();

    Text moodSwings$getRenderedText();

    float moodSwings$getOffset();
}
