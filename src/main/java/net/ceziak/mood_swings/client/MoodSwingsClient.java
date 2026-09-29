package net.ceziak.mood_swings.client;

import net.ceziak.mood_swings.lockdown.LockdownSystem;
import net.ceziak.mood_swings.mixin.client.WatheItemTooltipsInvoker;
import net.ceziak.mood_swings.network.AreaBorderPayload;
import net.ceziak.mood_swings.network.ConfigSnapshotPayload;
import net.ceziak.mood_swings.network.OpenLockdownScreenPayload;
import net.ceziak.mood_swings.network.OstAreaStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class MoodSwingsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, type, tooltip) ->
                WatheItemTooltipsInvoker.moodSwings$addCooldownText(
                        LockdownSystem.LOCKDOWN_ICON,
                        tooltip,
                        stack
                )
        );
        ClientPlayNetworking.registerGlobalReceiver(
                AreaBorderPayload.ID,
                (payload, context) -> AreaBorderRenderer.handle(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
                ConfigSnapshotPayload.ID,
                (payload, context) -> ClientConfigState.handle(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
                OstAreaStatePayload.ID,
                (payload, context) -> OstAreaAudioManager.handle(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
                OpenLockdownScreenPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(LockdownAreaScreen.fromJson(payload.json())))
        );
        ClientTickEvents.END_CLIENT_TICK.register(MurderTaskClientEffects::tick);
        ClientTickEvents.END_CLIENT_TICK.register(OstAreaAudioManager::tick);
        ClientTickEvents.END_CLIENT_TICK.register(OstAreaClientEffects::tick);
        HudRenderCallback.EVENT.register(MurderTaskVignetteRenderer::render);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientConfigState.requestRefresh());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AreaBorderRenderer.clear();
            ClientConfigState.clear();
            MurderTaskClientEffects.clear();
            OstAreaAudioManager.clear();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(AreaBorderRenderer::render);
    }
}
