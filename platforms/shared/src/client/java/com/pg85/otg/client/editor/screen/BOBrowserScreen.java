package com.pg85.otg.client.editor.screen;

import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BOBrowserScreen extends Screen {
    private final DimensionPreset preset;
    private final int hubPresetIndex;

    public BOBrowserScreen(DimensionPreset preset, int hubPresetIndex) {
        super(Component.literal("BO Browser"));
        this.preset = preset;
        this.hubPresetIndex = hubPresetIndex;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new BiomeEditorScreen(preset, hubPresetIndex));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
