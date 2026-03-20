package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.preview.PreviewScreen;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditorHubScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(EditorHubScreen.class);

    private record PresetEntry(String displayName, String folderName, String resourceId, DimensionPreset preset) {}

    private final List<PresetEntry> presets = new ArrayList<>();
    private int presetIndex;
    private String errorMessage;

    public EditorHubScreen() {
        this(0);
    }

    public EditorHubScreen(int presetIndex) {
        super(Component.literal("OTG Editor"));
        this.presetIndex = presetIndex;
    }

    @Override
    protected void init() {
        presets.clear();
        try {
            var engine = OTG.getEngine();
            if (engine != null) {
                for (DimensionPreset preset : engine.getDimensionPresetLoader().getAllDimensionPresets()) {
                    String display = preset.getConfig().getPresetInfo().getDisplayName();
                    if (display == null || display.isBlank()) display = preset.getFolderName();
                    String resId = Constants.MOD_ID_SHORT + ":" + preset.getRegistryName().toLowerCase(Locale.ROOT);
                    presets.add(new PresetEntry(display, preset.getFolderName(), resId, preset));
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to load presets", e);
            errorMessage = "Failed to load presets: " + e.getMessage();
        }

        // Back button — always present
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            onClose();
        }).bounds(width / 2 - 40, height - 30, 80, 20).build());

        if (presets.isEmpty()) {
            if (errorMessage == null) errorMessage = "No presets found";
            return;
        }

        if (presetIndex >= presets.size()) presetIndex = 0;

        int cx = width / 2;
        int y = 50;

        // Preset selector arrows
        addRenderableWidget(Button.builder(Component.literal("\u25C0"), btn -> {
            presetIndex = (presetIndex - 1 + presets.size()) % presets.size();
            rebuildWidgets();
        }).bounds(cx - 120, y, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("\u25B6"), btn -> {
            presetIndex = (presetIndex + 1) % presets.size();
            rebuildWidgets();
        }).bounds(cx + 100, y, 20, 20).build());

        y += 30;

        // Navigation cards
        int cardW = 140;
        int cardH = 40;
        int gap = 10;

        addRenderableWidget(Button.builder(Component.literal("World Settings"), btn -> {
            minecraft.setScreen(new WorldSettingsScreen(getSelectedPreset(), presetIndex));
        }).bounds(cx - cardW - gap / 2, y, cardW, cardH).build());

        addRenderableWidget(Button.builder(Component.literal("Biome Editor"), btn -> {
            minecraft.setScreen(new BiomeEditorScreen(getSelectedPreset(), presetIndex));
        }).bounds(cx + gap / 2, y, cardW, cardH).build());

        y += cardH + gap;

        addRenderableWidget(Button.builder(Component.literal("Group Settings"), btn -> {
            // Phase 3
        }).bounds(cx - cardW - gap / 2, y, cardW, cardH).build()).active = false;

        addRenderableWidget(Button.builder(Component.literal("BO Store"), btn -> {
            minecraft.setScreen(new BOBrowserScreen(getSelectedPreset(), this));
        }).bounds(cx + gap / 2, y, cardW, cardH).build());

        y += cardH + 20;

        // Preview World
        addRenderableWidget(Button.builder(Component.literal("Preview World"), btn -> {
            minecraft.setScreen(new PreviewScreen());
        }).bounds(cx - 60, y, 120, 20).build());

        // Back button already added above (before empty presets check)
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw text AFTER super.render() so it's on top of blur/background
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        if (!presets.isEmpty()) {
            String presetName = presets.get(presetIndex).displayName;
            graphics.drawCenteredString(font, "Preset: " + presetName, width / 2, 54, 0xFFFFFF);
        }

        if (errorMessage != null) {
            graphics.drawCenteredString(font, errorMessage, width / 2, height / 2, 0xFF4444);
        }
    }

    private DimensionPreset getSelectedPreset() {
        return presets.isEmpty() ? null : presets.get(presetIndex).preset;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
