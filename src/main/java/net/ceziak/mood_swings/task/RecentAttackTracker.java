package net.ceziak.mood_swings.task;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Extends Wathe's kill attribution beyond Minecraft's short built-in attacker timer.
 * The most recent successful player hit owns environmental eliminations for 30 seconds.
 */
public final class RecentAttackTracker {
    public static final int CREDIT_WINDOW_TICKS = 30 * 20;

    private static final Map<UUID, AttackCredit> RECENT_ATTACKS = new HashMap<>();

    private RecentAttackTracker() {
    }

    public static void recordDamage(
            ServerPlayerEntity victim,
            DamageSource source,
            float attemptedDamage,
            boolean blocked
    ) {
        if (blocked || attemptedDamage <= 0.0F) {
            return;
        }
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }
        recordAttack(attacker, victim);
    }

    public static void recordAttack(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        if (attacker == victim || attacker.getServerWorld() != victim.getServerWorld()) {
            return;
        }
        if (!GameWorldComponent.KEY.get(victim.getServerWorld()).isRunning()) {
            return;
        }

        long expiresAt = currentTick(victim) + CREDIT_WINDOW_TICKS;
        RECENT_ATTACKS.put(victim.getUuid(), new AttackCredit(attacker.getUuid(), expiresAt));
    }

    /**
     * Returns the still-valid last attacker without consuming the credit. The entry is
     * removed only after Wathe confirms the victim was actually eliminated.
     */
    public static ServerPlayerEntity findRecentAttacker(ServerPlayerEntity victim) {
        AttackCredit credit = RECENT_ATTACKS.get(victim.getUuid());
        if (credit == null) {
            return null;
        }

        if (currentTick(victim) > credit.expiresAtTick()) {
            RECENT_ATTACKS.remove(victim.getUuid());
            return null;
        }

        ServerWorld world = victim.getServerWorld();
        ServerPlayerEntity attacker = world.getServer()
                .getPlayerManager()
                .getPlayer(credit.attackerUuid());

        if (attacker == null || attacker == victim || attacker.getServerWorld() != world) {
            RECENT_ATTACKS.remove(victim.getUuid());
            return null;
        }

        return attacker;
    }

    public static void clearVictim(ServerPlayerEntity victim) {
        RECENT_ATTACKS.remove(victim.getUuid());
    }

    public static void clearAll() {
        RECENT_ATTACKS.clear();
    }

    private static long currentTick(ServerPlayerEntity player) {
        return player.getServerWorld().getTime();
    }

    private record AttackCredit(UUID attackerUuid, long expiresAtTick) {
    }
}
