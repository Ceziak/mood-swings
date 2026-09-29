package net.ceziak.mood_swings.lockdown;

import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Save-backed Lockdown regions. These never participate in mood-task or OST-area logic. */
public final class LockdownAreaStore {
    private LockdownAreaStore() {
    }

    public static Optional<NamedArea> get(String name) {
        return WorldDataStore.getLockdownArea(name);
    }

    public static Map<String, NamedArea> all() {
        return WorldDataStore.allLockdownAreas();
    }

    public static void put(String name, NamedArea area) {
        WorldDataStore.putLockdownArea(name, area);
    }

    public static boolean remove(String name) {
        return WorldDataStore.removeLockdownArea(name);
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
