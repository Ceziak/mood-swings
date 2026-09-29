package net.ceziak.mood_swings.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.config.MoodSwingsConfig;
import net.ceziak.mood_swings.config.MoodSwingsConfigManager;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.ceziak.mood_swings.lockdown.LockdownSystem;
import net.ceziak.mood_swings.ost.OstArea;
import net.ceziak.mood_swings.network.ConfigActionPayload;
import net.ceziak.mood_swings.network.ConfigSnapshot;
import net.ceziak.mood_swings.task.TaskDefinitionRegistry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Save-aware Cloth Config menu for tasks, areas, OST regions, Lockdown, and local tools. */
public final class MoodSwingsConfigScreen {
    private MoodSwingsConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ClientConfigState.requestRefresh();
        ConfigSnapshot snapshot = ClientConfigState.snapshot();
        MoodSwingsConfig local = MoodSwingsConfigManager.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("config.mood_swings.title"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory tasksCategory = builder.getOrCreateCategory(Text.translatable("config.mood_swings.category.tasks"));
        ConfigCategory areasCategory = builder.getOrCreateCategory(Text.translatable("config.mood_swings.category.areas"));
        ConfigCategory ostAreasCategory = builder.getOrCreateCategory(Text.translatable("config.mood_swings.category.ost_areas"));
        ConfigCategory lockdownCategory = builder.getOrCreateCategory(Text.translatable("config.mood_swings.category.lockdown"));
        ConfigCategory debugCategory = builder.getOrCreateCategory(Text.translatable("config.mood_swings.category.debug"));

        Map<String, TaskDraft> taskDrafts = new LinkedHashMap<>();
        Map<String, AreaDraft> areaDrafts = new LinkedHashMap<>();
        Map<String, OstAreaDraft> ostAreaDrafts = new LinkedHashMap<>();
        Map<String, LockdownAreaDraft> lockdownAreaDrafts = new LinkedHashMap<>();
        OstSettingsDraft ostSettingsDraft = new OstSettingsDraft(snapshot.ostAreasEnabled());
        LockdownDraft lockdownDraft = new LockdownDraft(snapshot.lockdownEnabled());
        DebugDraft debugDraft = new DebugDraft(local.areaBorderColor, local.areaBorderOpacity, allAreasVisible(snapshot));

        buildTasks(entries, tasksCategory, snapshot, taskDrafts);
        buildAreas(entries, areasCategory, snapshot, areaDrafts);
        buildOstAreas(entries, ostAreasCategory, snapshot, ostSettingsDraft, ostAreaDrafts);
        buildLockdown(entries, lockdownCategory, snapshot, lockdownDraft, lockdownAreaDrafts);
        buildDebug(entries, debugCategory, snapshot, debugDraft);

        builder.setSavingRunnable(() -> {
            local.areaBorderColor = debugDraft.borderColor;
            local.areaBorderOpacity = (float) Math.clamp(debugDraft.borderOpacity, 0.05D, 1.0D);
            MoodSwingsConfigManager.save();

            applyBorderDrafts(snapshot, areaDrafts, debugDraft);
            applyOstBorderDrafts(snapshot, ostAreaDrafts);
            applyLockdownBorderDrafts(snapshot, lockdownAreaDrafts);
            if (snapshot.canEdit()) {
                saveTaskDrafts(taskDrafts);
                saveAreaDrafts(areaDrafts);
                saveOstDrafts(ostSettingsDraft, ostAreaDrafts);
                saveLockdownDraft(lockdownDraft);
                saveLockdownAreaDrafts(lockdownAreaDrafts);
                if (debugDraft.reloadTasks) {
                    ClientConfigState.send(new ConfigActionPayload(ConfigActionPayload.RELOAD_TASKS, "", ""));
                }
            }
            if (debugDraft.refreshSnapshot) {
                ClientConfigState.requestRefresh();
            }
        });
        return builder.build();
    }

    private static void buildTasks(
            ConfigEntryBuilder entries,
            ConfigCategory category,
            ConfigSnapshot snapshot,
            Map<String, TaskDraft> drafts
    ) {
        if (snapshot.tasks().isEmpty()) {
            category.addEntry(entries.startTextDescription(Text.translatable("config.mood_swings.no_server_data")).build());
            return;
        }
        if (!snapshot.canEdit()) {
            category.addEntry(entries.startTextDescription(Text.translatable("config.mood_swings.read_only")).build());
        }

        snapshot.tasks().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String id = entry.getKey();
            ConfigSnapshot.TaskSnapshot data = entry.getValue();
            TaskDraft draft = TaskDraft.from(id, data);
            drafts.put(id, draft);

            SubCategoryBuilder sub = entries.startSubCategory(Text.literal(prettify(id)));
            sub.add(entries.startTextDescription(Text.literal(id)).build());
            if (snapshot.canEdit()) {
                sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.task.enabled"), draft.enabled)
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> draft.enabled = value)
                        .build());
            } else {
                sub.add(entries.startTextDescription(Text.literal(draft.enabled ? "Enabled" : "Disabled")).build());
            }

            if (data.nativeTask()) {
                sub.add(entries.startTextDescription(Text.translatable("config.mood_swings.task.native_note")).build());
                category.addEntry(sub.build());
                return;
            }

            if (!snapshot.canEdit()) {
                addTaskSummary(entries, sub, draft.root);
                category.addEntry(sub.build());
                return;
            }

            sub.add(entries.startStrField(Text.translatable("config.mood_swings.task.civilian_text"),
                            getString(draft.root, "civilian_text", ""))
                    .setSaveConsumer(value -> draft.root.addProperty("civilian_text", value))
                    .build());
            sub.add(entries.startStrField(Text.translatable("config.mood_swings.task.killer_text"),
                            getString(draft.root, "killer_text", ""))
                    .setSaveConsumer(value -> draft.root.addProperty("killer_text", value))
                    .build());
            sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.task.mood_value"),
                            getDouble(draft.root, "mood_value", 1.0D))
                    .setDefaultValue(1.0D)
                    .setMin(0.0D)
                    .setMax(1.0D)
                    .setSaveConsumer(value -> draft.root.addProperty("mood_value", value))
                    .build());
            sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.task.weight"),
                            getDouble(draft.root, "weight", 1.0D))
                    .setDefaultValue(1.0D)
                    .setMin(0.001D)
                    .setMax(10_000.0D)
                    .setSaveConsumer(value -> draft.root.addProperty("weight", value))
                    .build());
            sub.add(entries.startStrField(Text.translatable("config.mood_swings.task.audience"),
                            getString(draft.root, "audience", "all"))
                    .setDefaultValue("all")
                    .setTooltip(Text.translatable("config.mood_swings.task.audience.tooltip"))
                    .setSaveConsumer(value -> draft.root.addProperty("audience", value))
                    .build());
            sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.task.blocks_passive_income"),
                            getBoolean(draft.root, "blocks_passive_income", false))
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.mood_swings.task.blocks_passive_income.tooltip"))
                    .setSaveConsumer(value -> draft.root.addProperty("blocks_passive_income", value))
                    .build());

            JsonObject condition = condition(draft.root);
            String type = getString(condition, "type", "");
            sub.add(entries.startTextDescription(Text.literal("Condition: " + type)).build());
            addConditionEntries(entries, sub, condition, type);

            if (data.overridden()) {
                sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.task.reset"), false)
                        .setDefaultValue(false)
                        .setSaveConsumer(value -> draft.resetToDefaults = value)
                        .build());
            }
            category.addEntry(sub.build());
        });
    }

    private static void addConditionEntries(
            ConfigEntryBuilder entries,
            SubCategoryBuilder sub,
            JsonObject condition,
            String rawType
    ) {
        String type = rawType.contains(":") ? rawType.substring(rawType.indexOf(':') + 1) : rawType;
        switch (type) {
            case "stay_in_area" -> {
                sub.add(entries.startStrField(Text.translatable("config.mood_swings.task.area"),
                                getString(condition, "area", ""))
                        .setSaveConsumer(value -> condition.addProperty("area", value))
                        .build());
                addDuration(entries, sub, condition);
            }
            case "random_area" -> {
                addDuration(entries, sub, condition);
                sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.task.exclude_current"),
                                getBoolean(condition, "exclude_current", true))
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> condition.addProperty("exclude_current", value))
                        .build());
            }
            case "travel_distance" -> {
                sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.task.distance"),
                                getDouble(condition, "distance_blocks", 50.0D))
                        .setMin(1.0D)
                        .setMax(1_000_000.0D)
                        .setSaveConsumer(value -> condition.addProperty("distance_blocks", value))
                        .build());
                sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.task.max_step"),
                                getDouble(condition, "max_counted_step", 2.0D))
                        .setMin(0.25D)
                        .setMax(128.0D)
                        .setSaveConsumer(value -> condition.addProperty("max_counted_step", value))
                        .build());
            }
            case "near_player" -> {
                addDuration(entries, sub, condition);
                sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.task.range"),
                                getDouble(condition, "range", 3.0D))
                        .setMin(0.5D)
                        .setMax(128.0D)
                        .setSaveConsumer(value -> condition.addProperty("range", value))
                        .build());
            }
            case "under_block" -> {
                addDuration(entries, sub, condition);
                addBlockTag(entries, sub, condition);
                sub.add(entries.startIntField(Text.translatable("config.mood_swings.task.scan_height"),
                                getInt(condition, "scan_height", 8))
                        .setMin(1)
                        .setMax(128)
                        .setSaveConsumer(value -> condition.addProperty("scan_height", value))
                        .build());
                addBlockStateRequirements(entries, sub, condition);
            }
            case "above_block" -> {
                addDuration(entries, sub, condition);
                addBlockTag(entries, sub, condition);
                sub.add(entries.startIntField(Text.translatable("config.mood_swings.task.scan_depth"),
                                getInt(condition, "scan_depth", 1))
                        .setMin(1)
                        .setMax(128)
                        .setSaveConsumer(value -> condition.addProperty("scan_depth", value))
                        .build());
                addBlockStateRequirements(entries, sub, condition);
            }
            case "kill_player" -> sub.add(entries.startIntField(
                            Text.translatable("config.mood_swings.task.required_count"),
                            getInt(condition, "required_count", 1))
                    .setDefaultValue(1)
                    .setMin(1)
                    .setMax(100_000)
                    .setSaveConsumer(value -> condition.addProperty("required_count", value))
                    .build());
            default -> sub.add(entries.startTextDescription(Text.translatable("config.mood_swings.task.unknown_condition")).build());
        }
    }

    private static void addBlockTag(ConfigEntryBuilder entries, SubCategoryBuilder sub, JsonObject condition) {
        sub.add(entries.startStrField(Text.translatable("config.mood_swings.task.block_tag"),
                        getString(condition, "block_tag", ""))
                .setSaveConsumer(value -> condition.addProperty("block_tag", value))
                .build());
    }

    private static void addBlockStateRequirements(
            ConfigEntryBuilder entries,
            SubCategoryBuilder sub,
            JsonObject condition
    ) {
        sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.task.require_powered"),
                        getBoolean(condition, "require_powered", false))
                .setSaveConsumer(value -> condition.addProperty("require_powered", value))
                .build());
        sub.add(entries.startStrField(Text.translatable("config.mood_swings.task.required_facing"),
                        getString(condition, "required_facing", ""))
                .setSaveConsumer(value -> condition.addProperty("required_facing", value))
                .build());
    }

    private static void addDuration(ConfigEntryBuilder entries, SubCategoryBuilder sub, JsonObject condition) {
        sub.add(entries.startIntField(Text.translatable("config.mood_swings.task.duration"),
                        getInt(condition, "duration_seconds", 10))
                .setMin(1)
                .setMax(3_600)
                .setSaveConsumer(value -> condition.addProperty("duration_seconds", value))
                .build());
    }

    private static void addTaskSummary(ConfigEntryBuilder entries, SubCategoryBuilder sub, JsonObject root) {
        sub.add(entries.startTextDescription(Text.literal(getString(root, "civilian_text", ""))).build());
        sub.add(entries.startTextDescription(Text.literal(getString(root, "killer_text", ""))).build());
    }

    private static void buildAreas(
            ConfigEntryBuilder entries,
            ConfigCategory category,
            ConfigSnapshot snapshot,
            Map<String, AreaDraft> drafts
    ) {
        category.addEntry(entries.startTextDescription(Text.translatable("config.mood_swings.areas.help")).build());
        if (snapshot.areas().isEmpty()) {
            category.addEntry(entries.startTextDescription(Text.translatable("config.mood_swings.areas.empty")).build());
            return;
        }

        snapshot.areas().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String id = entry.getKey();
            NamedArea area = entry.getValue();
            AreaDraft draft = new AreaDraft(id, id, false, AreaBorderRenderer.isVisible(id));
            drafts.put(id, draft);

            SubCategoryBuilder sub = entries.startSubCategory(Text.literal(displayName(id, area)));
            sub.add(entries.startTextDescription(Text.literal("ID: " + id)).build());
            sub.add(entries.startTextDescription(Text.literal("Dimension: " + area.dimension())).build());
            sub.add(entries.startTextDescription(Text.literal(area.cornersString())).build());
            sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.area.show_border"), draft.showBorder)
                    .setSaveConsumer(value -> draft.showBorder = value)
                    .build());

            if (snapshot.canEdit()) {
                sub.add(entries.startStrField(Text.translatable("config.mood_swings.area.rename"), id)
                        .setSaveConsumer(value -> draft.newName = value)
                        .build());
                sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.area.delete"), false)
                        .setDefaultValue(false)
                        .setSaveConsumer(value -> draft.delete = value)
                        .build());
            }
            category.addEntry(sub.build());
        });
    }

    private static void buildOstAreas(
            ConfigEntryBuilder entries,
            ConfigCategory category,
            ConfigSnapshot snapshot,
            OstSettingsDraft settings,
            Map<String, OstAreaDraft> drafts
    ) {
        category.addEntry(entries.startTextDescription(
                Text.translatable("config.mood_swings.ost_areas.description")
        ).build());
        category.addEntry(entries.startTextDescription(
                Text.translatable("config.mood_swings.ost_areas.help")
        ).build());

        if (!snapshot.canEdit()) {
            category.addEntry(entries.startTextDescription(Text.translatable("config.mood_swings.read_only")).build());
            category.addEntry(entries.startTextDescription(Text.translatable(
                    snapshot.ostAreasEnabled()
                            ? "config.mood_swings.ost_areas.status.enabled"
                            : "config.mood_swings.ost_areas.status.disabled"
            )).build());
        } else {
            category.addEntry(entries.startBooleanToggle(
                            Text.translatable("config.mood_swings.ost_areas.enabled"),
                            settings.enabled)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.mood_swings.ost_areas.enabled.tooltip"))
                    .setSaveConsumer(value -> settings.enabled = value)
                    .build());
        }

        if (snapshot.ostAreas().isEmpty()) {
            category.addEntry(entries.startTextDescription(Text.translatable("config.mood_swings.ost_areas.empty")).build());
            return;
        }

        snapshot.ostAreas().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String id = entry.getKey();
            OstArea area = entry.getValue();
            OstAreaDraft draft = new OstAreaDraft(id, area, AreaBorderRenderer.isVisible(ostBorderId(id)));
            drafts.put(id, draft);

            SubCategoryBuilder sub = entries.startSubCategory(Text.literal(displayName(id, area.displayName())));
            sub.add(entries.startTextDescription(Text.literal(id)).build());
            sub.add(entries.startTextDescription(Text.literal(area.dimension() + " | " + area.cornersString())).build());

            if (snapshot.canEdit()) {
                sub.add(entries.startStrField(Text.translatable("config.mood_swings.ost_areas.sound"), draft.soundId)
                        .setTooltip(Text.translatable("config.mood_swings.ost_areas.sound.tooltip"))
                        .setSaveConsumer(value -> draft.soundId = value.trim())
                        .build());
                sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.ost_areas.volume"), draft.volume)
                        .setDefaultValue((double) OstArea.DEFAULT_VOLUME)
                        .setMin(0.0D)
                        .setMax(1.0D)
                        .setSaveConsumer(value -> draft.volume = value)
                        .build());
                sub.add(entries.startIntField(Text.translatable("config.mood_swings.ost_areas.priority"), draft.priority)
                        .setDefaultValue(OstArea.DEFAULT_PRIORITY)
                        .setTooltip(Text.translatable("config.mood_swings.ost_areas.priority.tooltip"))
                        .setSaveConsumer(value -> draft.priority = value)
                        .build());
                sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.ost_areas.fade"), draft.fadeSeconds)
                        .setDefaultValue((double) OstArea.DEFAULT_FADE_SECONDS)
                        .setMin(0.0D)
                        .setMax(30.0D)
                        .setTooltip(Text.translatable("config.mood_swings.ost_areas.fade.tooltip"))
                        .setSaveConsumer(value -> draft.fadeSeconds = value)
                        .build());
                sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.ost_areas.shake"), draft.shakeIntensity)
                        .setDefaultValue((double) OstArea.DEFAULT_SHAKE_INTENSITY)
                        .setMin(0.0D)
                        .setMax(1.0D)
                        .setTooltip(Text.translatable("config.mood_swings.ost_areas.shake.tooltip"))
                        .setSaveConsumer(value -> draft.shakeIntensity = value)
                        .build());
                sub.add(entries.startDoubleField(Text.translatable("config.mood_swings.ost_areas.darkness"), draft.darkness)
                        .setDefaultValue((double) OstArea.DEFAULT_DARKNESS)
                        .setMin(0.0D)
                        .setMax(1.0D)
                        .setTooltip(Text.translatable("config.mood_swings.ost_areas.darkness.tooltip"))
                        .setSaveConsumer(value -> draft.darkness = value)
                        .build());
                sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.ost_areas.delete"), false)
                        .setDefaultValue(false)
                        .setSaveConsumer(value -> draft.delete = value)
                        .build());
            } else {
                sub.add(entries.startTextDescription(Text.translatable(
                        "config.mood_swings.ost_areas.summary",
                        area.soundId(), area.volume(), area.priority(), area.fadeSeconds(),
                        area.shakeIntensity(), area.darkness()
                )).build());
            }

            sub.add(entries.startBooleanToggle(Text.translatable("config.mood_swings.area.show_border"), draft.showBorder)
                    .setDefaultValue(false)
                    .setSaveConsumer(value -> draft.showBorder = value)
                    .build());
            category.addEntry(sub.build());
        });
    }

    private static void buildLockdown(
            ConfigEntryBuilder entries,
            ConfigCategory category,
            ConfigSnapshot snapshot,
            LockdownDraft draft,
            Map<String, LockdownAreaDraft> areaDrafts
    ) {
        category.addEntry(entries.startTextDescription(
                Text.translatable("config.mood_swings.lockdown.description")
        ).build());

        if (snapshot.tasks().isEmpty()) {
            category.addEntry(entries.startTextDescription(
                    Text.translatable("config.mood_swings.no_server_data")
            ).build());
            return;
        }

        if (snapshot.canEdit()) {
            category.addEntry(entries.startBooleanToggle(
                            Text.translatable("config.mood_swings.lockdown.enabled"),
                            draft.enabled
                    )
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.mood_swings.lockdown.enabled.tooltip"))
                    .setSaveConsumer(value -> draft.enabled = value)
                    .build());
        } else {
            category.addEntry(entries.startTextDescription(
                    Text.translatable("config.mood_swings.read_only")
            ).build());
            category.addEntry(entries.startTextDescription(
                    Text.translatable(draft.enabled
                            ? "config.mood_swings.lockdown.status.enabled"
                            : "config.mood_swings.lockdown.status.disabled")
            ).build());
        }

        category.addEntry(entries.startTextDescription(
                Text.translatable("config.mood_swings.lockdown.price", LockdownSystem.PRICE)
        ).build());
        category.addEntry(entries.startTextDescription(
                Text.translatable("config.mood_swings.lockdown.duration", LockdownSystem.DURATION_SECONDS)
        ).build());
        category.addEntry(entries.startTextDescription(
                Text.translatable("config.mood_swings.lockdown.cooldown", LockdownSystem.COOLDOWN_SECONDS / 60)
        ).build());
        category.addEntry(entries.startTextDescription(
                Text.translatable("config.mood_swings.lockdown.requirement")
        ).build());

        category.addEntry(entries.startTextDescription(
                Text.translatable("config.mood_swings.lockdown.areas.help")
        ).build());
        if (snapshot.lockdownAreas().isEmpty()) {
            category.addEntry(entries.startTextDescription(
                    Text.translatable("config.mood_swings.lockdown.areas.empty")
            ).build());
            return;
        }

        snapshot.lockdownAreas().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String id = entry.getKey();
            NamedArea area = entry.getValue();
            LockdownAreaDraft areaDraft = new LockdownAreaDraft(
                    id, AreaBorderRenderer.isVisible(lockdownBorderId(id))
            );
            areaDrafts.put(id, areaDraft);

            SubCategoryBuilder sub = entries.startSubCategory(Text.literal(displayName(id, area)));
            sub.add(entries.startTextDescription(Text.literal("ID: " + id)).build());
            sub.add(entries.startTextDescription(Text.literal(area.dimension() + " | " + area.cornersString())).build());
            sub.add(entries.startBooleanToggle(
                            Text.translatable("config.mood_swings.area.show_border"), areaDraft.showBorder)
                    .setDefaultValue(false)
                    .setSaveConsumer(value -> areaDraft.showBorder = value)
                    .build());
            if (snapshot.canEdit()) {
                sub.add(entries.startBooleanToggle(
                                Text.translatable("config.mood_swings.lockdown.areas.delete"), false)
                        .setDefaultValue(false)
                        .setSaveConsumer(value -> areaDraft.delete = value)
                        .build());
            }
            category.addEntry(sub.build());
        });
    }

    private static void buildDebug(
            ConfigEntryBuilder entries,
            ConfigCategory category,
            ConfigSnapshot snapshot,
            DebugDraft draft
    ) {
        category.addEntry(entries.startBooleanToggle(Text.translatable("config.mood_swings.debug.show_all"), draft.showAllBorders)
                .setSaveConsumer(value -> draft.showAllBorders = value)
                .build());
        category.addEntry(entries.startStrField(Text.translatable("config.mood_swings.debug.border_color"), draft.borderColor)
                .setSaveConsumer(value -> draft.borderColor = value)
                .build());
        category.addEntry(entries.startDoubleField(Text.translatable("config.mood_swings.debug.border_opacity"), draft.borderOpacity)
                .setMin(0.05D)
                .setMax(1.0D)
                .setSaveConsumer(value -> draft.borderOpacity = value)
                .build());
        category.addEntry(entries.startBooleanToggle(Text.translatable("config.mood_swings.debug.refresh"), false)
                .setDefaultValue(false)
                .setSaveConsumer(value -> draft.refreshSnapshot = value)
                .build());
        if (snapshot.canEdit()) {
            category.addEntry(entries.startBooleanToggle(Text.translatable("config.mood_swings.debug.reload_tasks"), false)
                    .setDefaultValue(false)
                    .setSaveConsumer(value -> draft.reloadTasks = value)
                    .build());
        }
    }

    private static void saveOstDrafts(
            OstSettingsDraft settings,
            Map<String, OstAreaDraft> drafts
    ) {
        if (settings.enabled != settings.initialEnabled) {
            ClientConfigState.send(new ConfigActionPayload(
                    ConfigActionPayload.SET_OST_AREAS_ENABLED,
                    "",
                    Boolean.toString(settings.enabled)
            ));
        }

        for (OstAreaDraft draft : drafts.values()) {
            if (draft.delete) {
                AreaBorderRenderer.hide(ostBorderId(draft.id));
                ClientConfigState.send(new ConfigActionPayload(
                        ConfigActionPayload.DELETE_OST_AREA,
                        draft.id,
                        ""
                ));
                continue;
            }
            if (!draft.changed()) {
                continue;
            }
            OstArea updated = new OstArea(
                    draft.original.dimension(),
                    draft.original.minX(), draft.original.minY(), draft.original.minZ(),
                    draft.original.maxX(), draft.original.maxY(), draft.original.maxZ(),
                    draft.original.displayName(),
                    draft.soundId,
                    (float) Math.clamp(draft.volume, 0.0D, 1.0D),
                    draft.priority,
                    (float) Math.clamp(draft.fadeSeconds, 0.0D, 30.0D),
                    (float) Math.clamp(draft.shakeIntensity, 0.0D, 1.0D),
                    (float) Math.clamp(draft.darkness, 0.0D, 1.0D)
            );
            ClientConfigState.send(new ConfigActionPayload(
                    ConfigActionPayload.SAVE_OST_AREA,
                    draft.id,
                    TaskDefinitionRegistry.GSON.toJson(updated)
            ));
        }
    }

    private static void saveLockdownDraft(LockdownDraft draft) {
        if (draft.enabled == draft.initialEnabled) {
            return;
        }
        ClientConfigState.send(new ConfigActionPayload(
                ConfigActionPayload.SET_LOCKDOWN_ENABLED,
                "",
                Boolean.toString(draft.enabled)
        ));
    }

    private static void saveLockdownAreaDrafts(Map<String, LockdownAreaDraft> drafts) {
        for (LockdownAreaDraft draft : drafts.values()) {
            if (!draft.delete) {
                continue;
            }
            AreaBorderRenderer.hide(lockdownBorderId(draft.id));
            ClientConfigState.send(new ConfigActionPayload(
                    ConfigActionPayload.DELETE_LOCKDOWN_AREA, draft.id, ""
            ));
        }
    }

    private static void saveTaskDrafts(Map<String, TaskDraft> drafts) {
        for (TaskDraft draft : drafts.values()) {
            if (!draft.changed()) {
                continue;
            }
            if (draft.resetToDefaults) {
                ClientConfigState.send(new ConfigActionPayload(
                        ConfigActionPayload.RESET_TASK,
                        draft.id,
                        Boolean.toString(draft.enabled)
                ));
                continue;
            }
            JsonObject request = new JsonObject();
            request.addProperty("enabled", draft.enabled);
            request.addProperty("definitionJson", draft.definitionChanged()
                    ? TaskDefinitionRegistry.GSON.toJson(draft.root)
                    : "");
            ClientConfigState.send(new ConfigActionPayload(
                    ConfigActionPayload.SAVE_TASK,
                    draft.id,
                    TaskDefinitionRegistry.GSON.toJson(request)
            ));
        }
    }

    private static void saveAreaDrafts(Map<String, AreaDraft> drafts) {
        for (AreaDraft draft : drafts.values()) {
            if (draft.delete) {
                AreaBorderRenderer.hide(draft.oldName);
                ClientConfigState.send(new ConfigActionPayload(ConfigActionPayload.DELETE_AREA, draft.oldName, ""));
                continue;
            }
            String normalized = WorldDataStore.normalize(draft.newName);
            if (!normalized.isBlank() && !normalized.equals(WorldDataStore.normalize(draft.oldName))) {
                AreaBorderRenderer.hide(draft.oldName);
                ClientConfigState.send(new ConfigActionPayload(
                        ConfigActionPayload.RENAME_AREA,
                        draft.oldName,
                        draft.newName
                ));
            }
        }
    }

    private static void applyOstBorderDrafts(
            ConfigSnapshot snapshot,
            Map<String, OstAreaDraft> drafts
    ) {
        drafts.forEach((id, draft) -> {
            OstArea area = snapshot.ostAreas().get(id);
            String borderId = ostBorderId(id);
            if (draft.showBorder && area != null && !draft.delete) {
                AreaBorderRenderer.show(borderId, new NamedArea(
                        area.dimension(), area.minX(), area.minY(), area.minZ(),
                        area.maxX(), area.maxY(), area.maxZ(), area.displayName()
                ));
            } else {
                AreaBorderRenderer.hide(borderId);
            }
        });
    }

    private static void applyLockdownBorderDrafts(
            ConfigSnapshot snapshot,
            Map<String, LockdownAreaDraft> drafts
    ) {
        drafts.forEach((id, draft) -> {
            NamedArea area = snapshot.lockdownAreas().get(id);
            String borderId = lockdownBorderId(id);
            if (draft.showBorder && area != null && !draft.delete) {
                AreaBorderRenderer.show(borderId, area);
            } else {
                AreaBorderRenderer.hide(borderId);
            }
        });
    }

    private static void applyBorderDrafts(
            ConfigSnapshot snapshot,
            Map<String, AreaDraft> drafts,
            DebugDraft debug
    ) {
        if (debug.showAllBorders) {
            snapshot.areas().forEach(AreaBorderRenderer::show);
            return;
        }
        if (debug.initialShowAllBorders && !debug.showAllBorders) {
            AreaBorderRenderer.clear();
            return;
        }
        drafts.forEach((id, draft) -> {
            NamedArea area = snapshot.areas().get(id);
            if (draft.showBorder && area != null) {
                AreaBorderRenderer.show(id, area);
            } else {
                AreaBorderRenderer.hide(id);
            }
        });
    }

    private static boolean allAreasVisible(ConfigSnapshot snapshot) {
        return !snapshot.areas().isEmpty()
                && snapshot.areas().keySet().stream().allMatch(AreaBorderRenderer::isVisible);
    }

    private static JsonObject condition(JsonObject root) {
        if (!root.has("condition") || !root.get("condition").isJsonObject()) {
            JsonObject condition = new JsonObject();
            root.add("condition", condition);
            return condition;
        }
        return root.getAsJsonObject("condition");
    }

    private static String getString(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        try {
            return object.has(key) ? object.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String displayName(String id, NamedArea area) {
        return area.displayName() == null || area.displayName().isBlank() ? prettify(id) : area.displayName();
    }

    private static String displayName(String id, String displayName) {
        return displayName == null || displayName.isBlank() ? prettify(id) : displayName;
    }

    private static String ostBorderId(String id) {
        return "ost/" + WorldDataStore.normalize(id);
    }

    private static String lockdownBorderId(String id) {
        return "lockdown/" + WorldDataStore.normalize(id);
    }

    private static String prettify(String value) {
        String path = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
        String[] words = path.replace('-', '_').split("_");
        List<String> pretty = new ArrayList<>();
        for (String word : words) {
            if (!word.isBlank()) {
                pretty.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
            }
        }
        return String.join(" ", pretty);
    }

    private static final class TaskDraft {
        private final String id;
        private final JsonObject root;
        private final JsonObject originalRoot;
        private final boolean initialEnabled;
        private boolean enabled;
        private boolean resetToDefaults;

        private TaskDraft(String id, boolean enabled, JsonObject root) {
            this.id = id;
            this.enabled = enabled;
            this.initialEnabled = enabled;
            this.root = root;
            this.originalRoot = root.deepCopy();
        }

        private boolean definitionChanged() {
            return !root.equals(originalRoot);
        }

        private boolean changed() {
            return resetToDefaults || enabled != initialEnabled || definitionChanged();
        }

        private static TaskDraft from(String id, ConfigSnapshot.TaskSnapshot data) {
            JsonObject root;
            try {
                root = JsonParser.parseString(data.effectiveJson()).getAsJsonObject();
            } catch (RuntimeException exception) {
                root = new JsonObject();
            }
            return new TaskDraft(id, data.enabled(), root);
        }
    }

    private static final class AreaDraft {
        private final String oldName;
        private String newName;
        private boolean delete;
        private boolean showBorder;

        private AreaDraft(String oldName, String newName, boolean delete, boolean showBorder) {
            this.oldName = oldName;
            this.newName = newName;
            this.delete = delete;
            this.showBorder = showBorder;
        }
    }

    private static final class OstSettingsDraft {
        private final boolean initialEnabled;
        private boolean enabled;

        private OstSettingsDraft(boolean enabled) {
            this.initialEnabled = enabled;
            this.enabled = enabled;
        }
    }

    private static final class OstAreaDraft {
        private final String id;
        private final OstArea original;
        private String soundId;
        private double volume;
        private int priority;
        private double fadeSeconds;
        private double shakeIntensity;
        private double darkness;
        private boolean delete;
        private boolean showBorder;

        private OstAreaDraft(String id, OstArea original, boolean showBorder) {
            this.id = id;
            this.original = original;
            this.soundId = original.soundId();
            this.volume = original.volume();
            this.priority = original.priority();
            this.fadeSeconds = original.fadeSeconds();
            this.shakeIntensity = original.shakeIntensity();
            this.darkness = original.darkness();
            this.showBorder = showBorder;
        }

        private boolean changed() {
            return !soundId.equals(original.soundId())
                    || Double.compare(volume, original.volume()) != 0
                    || priority != original.priority()
                    || Double.compare(fadeSeconds, original.fadeSeconds()) != 0
                    || Double.compare(shakeIntensity, original.shakeIntensity()) != 0
                    || Double.compare(darkness, original.darkness()) != 0;
        }
    }

    private static final class LockdownAreaDraft {
        private final String id;
        private boolean showBorder;
        private boolean delete;

        private LockdownAreaDraft(String id, boolean showBorder) {
            this.id = id;
            this.showBorder = showBorder;
        }
    }

    private static final class LockdownDraft {
        private final boolean initialEnabled;
        private boolean enabled;

        private LockdownDraft(boolean enabled) {
            this.enabled = enabled;
            this.initialEnabled = enabled;
        }
    }

    private static final class DebugDraft {
        private String borderColor;
        private double borderOpacity;
        private boolean showAllBorders;
        private final boolean initialShowAllBorders;
        private boolean refreshSnapshot;
        private boolean reloadTasks;

        private DebugDraft(String borderColor, double borderOpacity, boolean showAllBorders) {
            this.borderColor = borderColor;
            this.borderOpacity = borderOpacity;
            this.showAllBorders = showAllBorders;
            this.initialShowAllBorders = showAllBorders;
        }
    }
}
