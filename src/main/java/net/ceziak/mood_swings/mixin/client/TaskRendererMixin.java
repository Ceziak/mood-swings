package net.ceziak.mood_swings.mixin.client;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.ceziak.mood_swings.task.CustomTrainTask;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.doctor4t.wathe.client.gui.MoodRenderer$TaskRenderer")
public abstract class TaskRendererMixin {
    @Shadow
    public int index;

    @Shadow
    public float offset;

    @Shadow
    public float alpha;

    @Shadow
    public boolean present;

    @Shadow
    public Text text;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void moodSwings$useExactCustomTaskText(
            PlayerMoodComponent.TrainTask currentTask,
            float delta,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!(currentTask instanceof CustomTrainTask customTask)) {
            return;
        }

        this.text = customTask.displayText(WatheClient.isKiller());
        this.present = true;
        this.alpha = MathHelper.lerp(delta / 16.0F, this.alpha, 1.0F);
        this.offset = MathHelper.lerp(delta / 32.0F, this.offset, this.index);

        boolean invisible = ((((int) (this.alpha * 255.0F)) << 24) & -67108864) == 0;
        callback.setReturnValue(this.alpha < 0.075F || invisible);
    }
}
