package net.ceziak.mood_swings;

import net.ceziak.mood_swings.command.MoodSwingsCommand;
import net.ceziak.mood_swings.config.MoodSwingsConfigManager;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

        ServerLifecycleEvents.SERVER_STARTED.register(WorldDataStore::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> WorldDataStore.close());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MoodSwingsCommand.register(dispatcher)
        );

        LOGGER.info("Mood Swings loaded.");
    }
}
