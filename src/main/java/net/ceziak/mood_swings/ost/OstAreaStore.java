package net.ceziak.mood_swings.ost;

import net.ceziak.mood_swings.config.WorldDataStore;

import java.util.Map;
import java.util.Optional;

/** Save-backed access to ambience regions, intentionally separate from task areas. */
public final class OstAreaStore {
    private OstAreaStore() {
    }

    public static Optional<OstArea> get(String name) {
        return WorldDataStore.getOstArea(name);
    }

    public static Map<String, OstArea> all() {
        return WorldDataStore.allOstAreas();
    }

    public static void put(String name, OstArea area) {
        WorldDataStore.putOstArea(name, area.validated());
    }

    public static boolean remove(String name) {
        return WorldDataStore.removeOstArea(name);
    }
}
