package com.pg85.otg.client.editor.data;

import com.pg85.otg.OTG;

/**
 * Utility for reloading DimensionPresets from disk.
 * Used by all editor screens after saving config changes.
 */
public final class PresetReloader {

    private PresetReloader() {}

    /**
     * Reloads all DimensionPresets from disk via the engine's preset loader.
     */
    public static void reload() {
        var engine = OTG.getEngine();
        if (engine != null) {
            engine.getDimensionPresetLoader().loadDimensionPresetsFromDisk();
        }
    }
}
