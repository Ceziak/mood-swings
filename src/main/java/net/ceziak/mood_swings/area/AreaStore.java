package net.ceziak.mood_swings.area;

import net.ceziak.mood_swings.config.WorldDataStore;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AreaStore {
    private AreaStore() {
    }

    public static Optional<NamedArea> get(String name) {
        return WorldDataStore.getArea(name);
    }

    public static Map<String, NamedArea> all() {
        return WorldDataStore.allAreas();
    }

    public static void put(String name, NamedArea area) {
        WorldDataStore.putArea(name, area);
    }

    public static boolean remove(String name) {
        return WorldDataStore.removeArea(name);
    }

    public static List<Map.Entry<String, NamedArea>> inPlayerDimension(ServerPlayerEntity player) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        List<Map.Entry<String, NamedArea>> matching = new ArrayList<>();
        for (Map.Entry<String, NamedArea> entry : all().entrySet()) {
            if (dimension.equals(entry.getValue().dimension())) {
                matching.add(entry);
            }
        }
        return matching;
    }
}
