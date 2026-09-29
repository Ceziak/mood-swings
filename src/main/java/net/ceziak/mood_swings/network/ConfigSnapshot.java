package net.ceziak.mood_swings.network;

import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.ost.OstArea;

import java.util.LinkedHashMap;
import java.util.Map;

/** Complete server-owned configuration snapshot shown by the client config screen. */
public record ConfigSnapshot(
        boolean canEdit,
        boolean lockdownEnabled,
        boolean ostAreasEnabled,
        Map<String, TaskSnapshot> tasks,
        Map<String, NamedArea> areas,
        Map<String, NamedArea> lockdownAreas,
        Map<String, OstArea> ostAreas
) {
    public ConfigSnapshot {
        tasks = tasks == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tasks);
        areas = areas == null ? new LinkedHashMap<>() : new LinkedHashMap<>(areas);
        lockdownAreas = lockdownAreas == null ? new LinkedHashMap<>() : new LinkedHashMap<>(lockdownAreas);
        ostAreas = ostAreas == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ostAreas);
    }

    public record TaskSnapshot(
            boolean nativeTask,
            boolean enabled,
            boolean overridden,
            String effectiveJson,
            String defaultJson
    ) {
        public TaskSnapshot {
            effectiveJson = effectiveJson == null ? "{}" : effectiveJson;
            defaultJson = defaultJson == null ? effectiveJson : defaultJson;
        }
    }

    public static ConfigSnapshot empty() {
        return new ConfigSnapshot(false, false, false, Map.of(), Map.of(), Map.of(), Map.of());
    }
}
