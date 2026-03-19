package com.pg85.otg.client.editor.screen;

import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WorldSettingsScreen extends Screen {
    public WorldSettingsScreen(DimensionPreset preset) {
        super(Component.literal("World Settings"));
    }
    @Override
    public boolean isPauseScreen() { return false; }
}
