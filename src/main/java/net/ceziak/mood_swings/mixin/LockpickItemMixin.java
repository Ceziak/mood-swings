package net.ceziak.mood_swings.mixin;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.item.LockpickItem;
import dev.doctor4t.wathe.index.WatheSounds;
import net.ceziak.mood_swings.lockdown.VanillaWatheDoorLocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends Wathe's crouch-lockpick jam action to its two vanilla DoorBlock variants. */
@Mixin(LockpickItem.class)
public abstract class LockpickItemMixin {
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void moodSwings$jamVanillaWatheDoor(
            ItemUsageContext context,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isSneaking()) {
            return;
        }

        World world = context.getWorld();
        BlockPos clickedPos = context.getBlockPos();
        BlockState state = world.getBlockState(clickedPos);
        if (!VanillaWatheDoorLocks.isSupported(state)) {
            return;
        }

        if (world.isClient) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        BlockPos lower = VanillaWatheDoorLocks.lowerPos(world, clickedPos);
        if (!VanillaWatheDoorLocks.jam(serverWorld, lower, GameConstants.JAMMED_DOOR_TIME)) {
            return;
        }

        Item lockpick = (Item) (Object) this;
        if (!player.isCreative()) {
            Integer cooldown = GameConstants.ITEM_COOLDOWNS.get(lockpick);
            if (cooldown != null && cooldown > 0) {
                player.getItemCooldownManager().set(lockpick, cooldown);
            }
        }

        world.playSound(
                null,
                lower.up(),
                WatheSounds.ITEM_LOCKPICK_DOOR,
                SoundCategory.BLOCKS,
                1.0F,
                1.0F
        );
        cir.setReturnValue(ActionResult.SUCCESS);
    }
}
