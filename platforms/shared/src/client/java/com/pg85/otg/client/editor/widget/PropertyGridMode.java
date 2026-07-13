package com.pg85.otg.client.editor.widget;

/**
 * Defines which flag columns are visible in PropertyGridWidget.
 */
public enum PropertyGridMode {

    /** World/preset settings — no override flags */
    PRESET_EDITOR(false, false, false),

    /** Biome editor — Override + Merge columns */
    BIOME_EDITOR(true, true, false),

    /** Group editor — Override + Merge + OPV columns */
    GROUP_EDITOR(true, true, true);

    private final boolean showOverride;
    private final boolean showMerge;
    private final boolean showOpv;

    PropertyGridMode(boolean showOverride, boolean showMerge, boolean showOpv) {
        this.showOverride = showOverride;
        this.showMerge = showMerge;
        this.showOpv = showOpv;
    }

    public boolean showOverride() { return showOverride; }
    public boolean showMerge() { return showMerge; }
    public boolean showOpv() { return showOpv; }
}
