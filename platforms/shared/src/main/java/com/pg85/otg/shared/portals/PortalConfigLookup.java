package com.pg85.otg.shared.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.dimensions.WorldPresetConfig.OTGDimension;
import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.materials.LocalMaterialData;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

/**
 * Platform-agnostic portal configuration lookup.
 * Contains logic that doesn't depend on Minecraft classes.
 */
public final class PortalConfigLookup {

    private PortalConfigLookup() {} // utility class

    /**
     * Find a preset by its effective portal color.
     * Respects R1 (YAML color override) and R2 (gating by active WorldPreset).
     * @param portalColor The color to search for (case-insensitive)
     * @return Optional containing the matching preset, or empty if not found
     */
    public static Optional<DimensionPreset> findPresetByColor(String portalColor) {
        String targetColor = DimensionNameUtils.normalizeColor(portalColor);
        WorldPresetConfig activeWorldPreset = WorldPresetPortalResolver.getActiveWorldPreset();
        Set<String> allowedPresets = WorldPresetPortalResolver.getAllowedPresetFolders(activeWorldPreset);

        return OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets().stream()
                .filter(p -> p.getConfig() != null)
                .filter(p -> p.getConfig().getPortalSettings() != null)
                // R2: Skip presets not in active WorldPreset YAML
                .filter(p -> allowedPresets == null || allowedPresets.contains(p.getFolderName()))
                // I1+R1: Check effective portal blocks (YAML override can provide blocks even if DimensionPreset has none)
                .filter(p -> {
                    PortalSettings settings = p.getConfig().getPortalSettings();
                    boolean hasPresetBlocks = settings.getPortalBlocks() != null && !settings.getPortalBlocks().isEmpty();
                    if (hasPresetBlocks) return true;
                    // Check YAML override
                    if (activeWorldPreset != null) {
                        OTGDimension dimEntry = WorldPresetPortalResolver.findDimensionEntry(activeWorldPreset, p.getFolderName());
                        if (dimEntry != null) {
                            ArrayList<LocalMaterialData> overrideBlocks = WorldPresetPortalResolver.parsePortalBlocks(dimEntry.PortalBlocks);
                            return overrideBlocks != null;
                        }
                    }
                    return false;
                })
                // R1: Match effective color (YAML override if present)
                .filter(p -> {
                    String effectiveColor = p.getConfig().getPortalSettings().getPortalColor();
                    if (activeWorldPreset != null) {
                        OTGDimension dimEntry = WorldPresetPortalResolver.findDimensionEntry(activeWorldPreset, p.getFolderName());
                        if (dimEntry != null && WorldPresetPortalResolver.hasOverride(dimEntry.PortalColor)) {
                            effectiveColor = dimEntry.PortalColor;
                        }
                    }
                    return targetColor.equals(DimensionNameUtils.normalizeColor(effectiveColor));
                })
                .findFirst();
    }

    /**
     * Get PortalSettings for a color, searching all presets.
     * @param portalColor The color to search for
     * @return Optional containing the matching settings, or empty if not found
     */
    public static Optional<PortalSettings> findSettingsByColor(String portalColor) {
        return findPresetByColor(portalColor)
                .map(p -> p.getConfig().getPortalSettings());
    }

    /**
     * Get portal minimum width from settings, with minimum bound.
     */
    public static int getPortalMinWidth(PortalSettings settings) {
        return settings != null ? Math.max(2, settings.getPortalMinWidth()) : 2;
    }

    /**
     * Get portal minimum height from settings, with minimum bound.
     */
    public static int getPortalMinHeight(PortalSettings settings) {
        return settings != null ? Math.max(3, settings.getPortalMinHeight()) : 3;
    }
}
