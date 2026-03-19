package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.settings.ConfigSection;
import com.pg85.otg.config.settings.preset.*;
import com.pg85.otg.config.settingtype.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PropertyExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyExtractor.class);

    private static final Map<Class<? extends ConfigSection>, PropertyCategory> PRESET_SETTING_CLASSES = new LinkedHashMap<>();
    static {
        PRESET_SETTING_CLASSES.put(DimensionPresetInfo.class, PropertyCategory.WORLD);
        PRESET_SETTING_CLASSES.put(VisualSettings.class, PropertyCategory.WORLD);
        PRESET_SETTING_CLASSES.put(GenerationSettings.class, PropertyCategory.BIOME_DISTRIBUTION);
        PRESET_SETTING_CLASSES.put(TerrainSettings.class, PropertyCategory.TERRAIN);
        PRESET_SETTING_CLASSES.put(BlockSettings.class, PropertyCategory.TERRAIN);
        PRESET_SETTING_CLASSES.put(CarverSettings.class, PropertyCategory.CAVES_RAVINES);
        PRESET_SETTING_CLASSES.put(NoiseCaveSettings.class, PropertyCategory.CAVES_RAVINES);
        PRESET_SETTING_CLASSES.put(StructureSettings.class, PropertyCategory.STRUCTURES);
        PRESET_SETTING_CLASSES.put(SpawnSettings.class, PropertyCategory.STRUCTURES);
        PRESET_SETTING_CLASSES.put(ResourceSettings.class, PropertyCategory.STRUCTURES);
        PRESET_SETTING_CLASSES.put(DimensionSettings.class, PropertyCategory.DIMENSIONS);
        PRESET_SETTING_CLASSES.put(PortalSettings.class, PropertyCategory.DIMENSIONS);
        PRESET_SETTING_CLASSES.put(ImageSettings.class, PropertyCategory.ADVANCED);
        PRESET_SETTING_CLASSES.put(GameRuleSettings.class, PropertyCategory.GAME_RULES);
    }

    public static Map<String, PropertyDefinition> extractPresetDefinitions() {
        Map<String, PropertyDefinition> definitions = new LinkedHashMap<>();

        for (var entry : PRESET_SETTING_CLASSES.entrySet()) {
            Map<String, Setting<?>> settings = ConfigSection.getSettings(entry.getKey());
            PropertyCategory category = entry.getValue();

            for (var settingEntry : settings.entrySet()) {
                Setting<?> setting = settingEntry.getValue();
                PropertyDefinition def = fromSetting(setting, category);
                if (def != null) {
                    definitions.put(setting.getName(), def);
                }
            }
        }

        LOG.info("Extracted {} property definitions for DimensionPresetConfig.ini", definitions.size());
        return definitions;
    }

    private static PropertyDefinition fromSetting(Setting<?> setting, PropertyCategory category) {
        PropertyType type = mapType(setting);
        if (type == null) {
            LOG.warn("Unknown setting type for '{}': {}", setting.getName(), setting.getClass().getSimpleName());
            type = PropertyType.STRING;
        }

        String min = setting.getMinValue() != null ? setting.getMinValue().toString() : null;
        String max = setting.getMaxValue() != null ? setting.getMaxValue().toString() : null;
        List<String> enumValues = setting.getEnumValues();
        String defaultValue = setting.getDefaultValueAsString();
        String comment = setting.getDescription() != null
            ? String.join(" ", setting.getDescription())
            : null;

        return new PropertyDefinition(setting.getName(), type, category, defaultValue, min, max, enumValues, comment);
    }

    private static PropertyType mapType(Setting<?> setting) {
        return switch (setting) {
            case BooleanSetting s -> PropertyType.BOOLEAN;
            case IntSetting s -> PropertyType.INT;
            case LongSetting s -> PropertyType.LONG;
            case FloatSetting s -> PropertyType.FLOAT;
            case DoubleSetting s -> PropertyType.DOUBLE;
            case StringSetting s -> PropertyType.STRING;
            case StringListSetting s -> PropertyType.STRING_LIST;
            case EnumSetting<?> s -> PropertyType.ENUM;
            case MaterialSetting s -> PropertyType.BLOCK;
            case ColorSetting s -> PropertyType.COLOR;
            default -> null;
        };
    }
}
