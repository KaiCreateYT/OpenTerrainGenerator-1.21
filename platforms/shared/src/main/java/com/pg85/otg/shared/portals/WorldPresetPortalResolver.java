package com.pg85.otg.shared.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.dimensions.WorldPresetConfig.OTGDimension;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.IMaterialReader;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.shared.commands.OTGCommandRegistrar;
import com.pg85.otg.shared.dimensions.DimensionManager;
import com.pg85.otg.shared.registry.WorldPresetRegistrar;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.materials.LocalMaterialData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves WorldPreset YAML portal overrides and gating.
 * R1: YAML dimension entry portal fields override DimensionPreset defaults.
 * R2: Only dimension presets listed in the active YAML get portals.
 */
public final class WorldPresetPortalResolver {

    private WorldPresetPortalResolver() {}

    /**
     * Returns the active WorldPresetConfig, or null if none is active
     * (no YAML detected, or DimensionManager not yet initialized).
     */
    public static @Nullable WorldPresetConfig getActiveWorldPreset() {
        DimensionManager manager = OTGCommandRegistrar.getDimensionManager();
        if (manager == null) return null;
        return manager.getActiveWorldPresetConfig();
    }

    /**
     * Returns the set of preset folder names referenced by the active WorldPreset YAML.
     * Null means no gating (all presets allowed — backward compat when no YAML is active).
     */
    public static @Nullable Set<String> getAllowedPresetFolders(@Nullable WorldPresetConfig config) {
        if (config == null) return null;

        Set<String> allowed = new HashSet<>();
        Map<String, DimensionPreset> loaded = new HashMap<>();
        if (OTG.getEngine() != null) {
            for (DimensionPreset p : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
                loaded.put(p.getFolderName(), p);
            }
        }
        String overworldFolder = WorldPresetRegistrar.resolveOverworldDimensionPresetFolder(config, loaded);
        if (overworldFolder != null) {
            allowed.add(overworldFolder);
        }
        if (config.Nether != null && config.Nether.PresetFolderName != null) {
            allowed.add(config.Nether.PresetFolderName);
        }
        if (config.End != null && config.End.PresetFolderName != null) {
            allowed.add(config.End.PresetFolderName);
        }
        if (config.Dimensions != null) {
            for (OTGDimension dim : config.Dimensions) {
                if (dim.PresetFolderName != null) {
                    allowed.add(dim.PresetFolderName);
                }
            }
        }
        return allowed;
    }

    /**
     * Finds the OTGDimension entry in the WorldPreset config that matches the given
     * preset folder name. Searches custom Dimensions first, then Nether, End, Overworld.
     * Portal settings on Overworld are typically irrelevant (portals go TO custom dims,
     * not to the overworld), but we still check it as a fallback.
     */
    public static @Nullable OTGDimension findDimensionEntry(WorldPresetConfig config, String presetFolderName) {
        if (config.Dimensions != null) {
            for (OTGDimension dim : config.Dimensions) {
                if (presetFolderName.equals(dim.PresetFolderName)) {
                    return dim;
                }
            }
        }
        if (config.Nether != null && presetFolderName.equals(config.Nether.PresetFolderName)) {
            return config.Nether;
        }
        if (config.End != null && presetFolderName.equals(config.End.PresetFolderName)) {
            return config.End;
        }
        if (config.Overworld != null && presetFolderName.equals(config.Overworld.PresetFolderName)) {
            return config.Overworld;
        }
        Map<String, DimensionPreset> loaded = new HashMap<>();
        if (OTG.getEngine() != null) {
            for (DimensionPreset p : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
                loaded.put(p.getFolderName(), p);
            }
        }
        String effectiveOw = WorldPresetRegistrar.resolveOverworldDimensionPresetFolder(config, loaded);
        if (effectiveOw != null && effectiveOw.equals(presetFolderName)) {
            return config.Overworld != null
                    ? config.Overworld
                    : new WorldPresetConfig.OTGOverWorld(presetFolderName, -1, null, null);
        }
        return null;
    }

    /**
     * Parses a comma-separated block name string into a list of LocalMaterialData.
     * Returns null if input is null or blank (meaning "no override, use DimensionPreset default").
     */
    public static @Nullable ArrayList<LocalMaterialData> parsePortalBlocks(@Nullable String portalBlocksStr) {
        if (portalBlocksStr == null || portalBlocksStr.isBlank()) return null;

        IMaterialReader reader = OTG.getEngine().getDimensionPresetLoader().getMaterialReader();
        ArrayList<LocalMaterialData> blocks = new ArrayList<>();
        for (String part : portalBlocksStr.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                LocalMaterialData material = reader.readMaterial(trimmed);
                if (material != null) {
                    blocks.add(material);
                }
            } catch (InvalidConfigException e) {
                OTGLog.warn("Failed to parse portal block '{}': {}", trimmed, e.getMessage());
            }
        }
        return blocks.isEmpty() ? null : blocks;
    }

    /**
     * Returns true if the given string is non-null and non-blank (i.e. the YAML
     * entry has an actual override value).
     */
    public static boolean hasOverride(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
