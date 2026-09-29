package net.ceziak.mood_swings;

import net.ceziak.mood_swings.command.MoodSwingsCommand;
import net.ceziak.mood_swings.config.MoodSwingsConfigManager;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.ceziak.mood_swings.lockdown.LockdownSystem;
import net.ceziak.mood_swings.lockdown.VanillaWatheDoorLocks;
import net.ceziak.mood_swings.network.ActivateLockdownPayload;
import net.ceziak.mood_swings.network.AreaBorderPayload;
import net.ceziak.mood_swings.network.ConfigActionPayload;
import net.ceziak.mood_swings.network.ConfigSnapshotPayload;
import net.ceziak.mood_swings.network.ConfigSyncService;
import net.ceziak.mood_swings.network.OpenLockdownScreenPayload;
import net.ceziak.mood_swings.network.OstAreaStatePayload;
import net.ceziak.mood_swings.ost.OstAreaSystem;
import net.ceziak.mood_swings.task.CustomTaskRuntime;
import net.ceziak.mood_swings.task.RecentAttackTracker;
import net.ceziak.mood_swings.task.TaskDefinitionRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoodSwings implements ModInitializer {
    public static final String MOD_ID = "mood_swings";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        MoodSwingsConfigManager.initialize();
        PayloadTypeRegistry.playS2C().register(AreaBorderPayload.ID, AreaBorderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenLockdownScreenPayload.ID, OpenLockdownScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSnapshotPayload.ID, ConfigSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OstAreaStatePayload.ID, OstAreaStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigActionPayload.ID, ConfigActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActivateLockdownPayload.ID, ActivateLockdownPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConfigActionPayload.ID, (payload, context) ->
                context.server().execute(() -> ConfigSyncService.handle(context.player(), context.server(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(ActivateLockdownPayload.ID, (payload, context) ->
                context.server().execute(() -> LockdownSystem.activate(context.player(), payload.areaId()))
        );

        LockdownSystem.initialize();
        OstAreaSystem.initialize();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                RecentAttackTracker.recordDamage(victim, source, baseDamageTaken, blocked);
            }
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, attacker, killedEntity) -> {
            if (killedEntity instanceof ServerPlayerEntity victim) {
                RecentAttackTracker.clearVictim(victim);
            }
            if (attacker instanceof ServerPlayerEntity killer
                    && killedEntity instanceof ServerPlayerEntity victim
                    && killer != victim) {
                CustomTaskRuntime.queuePlayerKill(killer);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(CustomTaskRuntime::flushQueuedPlayerKills);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WorldDataStore.load(server);
            LockdownSystem.setShopEntryEnabled(WorldDataStore.isLockdownEnabled());
            TaskDefinitionRegistry.reload(server.getResourceManager());
            ConfigSyncService.broadcastSnapshots(server);
            OstAreaSystem.refreshAll(server);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                TaskDefinitionRegistry.reload(resourceManager);
                ConfigSyncService.broadcastSnapshots(server);
                OstAreaSystem.refreshAll(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CustomTaskRuntime.clearQueuedPlayerKills();
            RecentAttackTracker.clearAll();
            LockdownSystem.setShopEntryEnabled(false);
            VanillaWatheDoorLocks.clearAll();
            OstAreaSystem.stopAll(server);
            OstAreaSystem.clear();
            WorldDataStore.close();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MoodSwingsCommand.register(dispatcher)
        );

        LOGGER.info("Mood Swings loaded.");
    }
}
