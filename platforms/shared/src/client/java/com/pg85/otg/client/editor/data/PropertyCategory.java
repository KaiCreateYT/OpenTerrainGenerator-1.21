package com.pg85.otg.client.editor.data;

public enum PropertyCategory {
    WORLD("World"),
    BIOME_DISTRIBUTION("Biome Distribution"),
    TERRAIN("Terrain"),
    CAVES_RAVINES("Caves & Ravines"),
    STRUCTURES("Structures"),
    DIMENSIONS("Dimensions"),
    GAME_RULES("Game Rules"),
    BIOME_PLACEMENT("Placement"),
    BIOME_TERRAIN("Terrain"),
    VEGETATION("Vegetation"),
    ORES("Ores"),
    BIOME_STRUCTURES("Structures"),
    WATER("Water"),
    MOBS("Mobs"),
    ADVANCED("Advanced"),
    UNCATEGORIZED("Other");

    private final String displayName;

    PropertyCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
