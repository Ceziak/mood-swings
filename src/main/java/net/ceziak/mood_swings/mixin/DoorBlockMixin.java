package net.ceziak.mood_swings.mixin;

import dev.doctor4t.wathe.api.event.AllowPlayerOpenLockedDoor;
import dev.doctor4t.wathe.index.WatheSounds;
import net.ceziak.mood_swings.lockdown.VanillaWatheDoorLocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives Wathe's vanilla DoorBlock variants the same temporary jam semantics as its custom doors. */
@Mixin(DoorBlock.class)
public abstract class DoorBlockMixin {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void moodSwings$denyJammedUse(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (!VanillaWatheDoorLocks.isSupported(state) || !VanillaWatheDoorLocks.isJammed(world, pos)) {
            return;
        }
        if (player.isCreative() || AllowPlayerOpenLockedDoor.EVENT.invoker().allowOpen(player)) {
            return;
        }

        if (!world.isClient) {
            BlockPos lower = VanillaWatheDoorLocks.lowerPos(world, pos);
            world.playSound(
                    null,
                    lower.up(),
                    WatheSounds.BLOCK_DOOR_LOCKED,
                    SoundCategory.BLOCKS,
                    1.0F,
                    1.0F
            );
            player.sendMessage(Text.translatable("tip.door.jammed"), true);
        }
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    @Inject(method = "setOpen", at = @At("HEAD"), cancellable = true)
    private void moodSwings$denyProgrammaticOpen(
            Entity entity,
            World world,
            BlockState state,
            BlockPos pos,
            boolean open,
            CallbackInfo ci
    ) {
        if (open
                && VanillaWatheDoorLocks.isSupported(state)
                && VanillaWatheDoorLocks.isJammed(world, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "neighborUpdate", at = @At("TAIL"))
    private void moodSwings$keepJammedDoorClosed(
            BlockState state,
            World world,
            BlockPos pos,
            Block sourceBlock,
            BlockPos sourcePos,
            boolean notify,
            CallbackInfo ci
    ) {
        if (world.isClient) {
            return;
        }

        BlockState current = world.getBlockState(pos);
        if (!VanillaWatheDoorLocks.isSupported(current)
                || !VanillaWatheDoorLocks.isJammed(world, pos)
                || !current.contains(Properties.OPEN)
                || !current.get(Properties.OPEN)) {
            return;
        }

        ((DoorBlock) (Object) this).setOpen(null, world, current, pos, false);
    }
}
