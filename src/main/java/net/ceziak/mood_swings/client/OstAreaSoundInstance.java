package net.ceziak.mood_swings.client;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/**
 * Persistent non-positional OST stream.
 * Muting changes only its gain, so its playback cursor keeps advancing.
 */
final class OstAreaSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    /**
     * A true zero can be culled by parts of Minecraft's audio pipeline. -80 dB is inaudible in
     * practice while keeping the streaming source alive for synchronized crossfades.
     */
    private static final float SILENT_KEEP_ALIVE_VOLUME = 0.0001F;

    private float startVolume;
    private float targetVolume;
    private int transitionAge;
    private int transitionTicks;
    private boolean retireAfterTransition;
    private boolean done;

    OstAreaSoundInstance(Identifier soundId) {
        super(soundId, SoundCategory.MASTER, SoundInstance.createRandom());
        this.volume = SILENT_KEEP_ALIVE_VOLUME;
        this.pitch = 1.0F;
        this.repeat = true;
        this.repeatDelay = 0;
        this.relative = true;
        this.attenuationType = SoundInstance.AttenuationType.NONE;
    }

    @Override
    public void tick() {
        if (done) {
            return;
        }

        if (transitionTicks <= 0) {
            volume = targetVolume;
            if (retireAfterTransition && targetVolume <= 0.0001F) {
                done = true;
            }
            return;
        }

        transitionAge++;
        float progress = Math.min(1.0F, transitionAge / (float) transitionTicks);
        volume = startVolume + (targetVolume - startVolume) * progress;

        if (progress >= 1.0F) {
            transitionTicks = 0;
            transitionAge = 0;
            volume = targetVolume;
            if (retireAfterTransition && targetVolume <= 0.0001F) {
                done = true;
            }
        }
    }

    void setTargetVolume(float newTargetVolume, int fadeTicks) {
        if (done) {
            return;
        }

        float logicalTarget = Math.clamp(newTargetVolume, 0.0F, 1.0F);
        float engineTarget = logicalTarget <= 0.0F ? SILENT_KEEP_ALIVE_VOLUME : logicalTarget;
        int clampedFade = Math.max(0, fadeTicks);
        retireAfterTransition = false;

        if (Float.compare(targetVolume, engineTarget) == 0
                && transitionTicks == clampedFade
                && (transitionTicks == 0 || Float.compare(volume, engineTarget) == 0)) {
            return;
        }

        startVolume = volume;
        targetVolume = engineTarget;
        transitionAge = 0;
        transitionTicks = clampedFade;

        if (transitionTicks <= 0) {
            volume = targetVolume;
        }
    }

    void retire(int fadeTicks) {
        if (done) {
            return;
        }
        retireAfterTransition = true;
        startVolume = volume;
        targetVolume = 0.0F;
        transitionAge = 0;
        transitionTicks = Math.max(0, fadeTicks);
        if (transitionTicks <= 0 || volume <= 0.0001F) {
            volume = 0.0F;
            done = true;
        }
    }

    void stopImmediately() {
        volume = 0.0F;
        done = true;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }
}
