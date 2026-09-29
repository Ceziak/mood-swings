package net.ceziak.mood_swings.mixin;

import dev.doctor4t.wathe.game.GameFunctions;
import net.ceziak.mood_swings.task.CustomTaskRuntime;
import net.ceziak.mood_swings.task.RecentAttackTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe eliminates players through GameFunctions.killPlayer instead of Minecraft's
 * normal damage/death pipeline. This mixin supplies recent environmental-kill credit
 * and then forwards successful player eliminations to custom murder tasks.
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsMixin {
    @Unique
    private static final ThreadLocal<Boolean> moodSwings$reapplyingKillCredit =
            ThreadLocal.withInitial(() -> false);

    /**
     * Wathe passes null after Minecraft forgets a player's last attacker. Re-run the
     * untouched Wathe method with our still-valid 30-second attacker, then cancel the
     * uncredited outer call. The guard prevents recursive re-entry.
     */
    @Inject(
            method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void moodSwings$restoreRecentEnvironmentalKillCredit(
            PlayerEntity victim,
            boolean spawnBody,
            PlayerEntity attacker,
            Identifier deathReason,
            CallbackInfo callback
    ) {
        if (moodSwings$reapplyingKillCredit.get()
                || attacker != null
                || !(victim instanceof ServerPlayerEntity serverVictim)) {
            return;
        }

        ServerPlayerEntity recentAttacker = RecentAttackTracker.findRecentAttacker(serverVictim);
        if (recentAttacker == null) {
            return;
        }

        moodSwings$reapplyingKillCredit.set(true);
        try {
            GameFunctions.killPlayer(victim, spawnBody, recentAttacker, deathReason);
        } finally {
            moodSwings$reapplyingKillCredit.remove();
        }

        callback.cancel();
    }

    @Inject(
            method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V",
            at = @At("TAIL")
    )
    private static void moodSwings$recordSuccessfulWatheKill(
            PlayerEntity victim,
            boolean spawnBody,
            PlayerEntity attacker,
            Identifier deathReason,
            CallbackInfo callback
    ) {
        if (victim instanceof ServerPlayerEntity serverVictim) {
            RecentAttackTracker.clearVictim(serverVictim);
        }

        if (attacker instanceof ServerPlayerEntity killer && attacker != victim) {
            CustomTaskRuntime.queuePlayerKill(killer);
        }
    }
}
