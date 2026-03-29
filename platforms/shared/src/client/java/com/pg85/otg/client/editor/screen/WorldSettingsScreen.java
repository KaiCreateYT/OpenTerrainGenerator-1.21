package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.data.*;
import com.pg85.otg.client.editor.widget.PropertyGridMode;
import com.pg85.otg.client.editor.widget.PropertyGridWidget;
import com.pg85.otg.client.preview.PreviewScreen;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class WorldSettingsScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(WorldSettingsScreen.class);

    private final DimensionPreset preset;
    private final int hubPresetIndex;
    private PropertyGridWidget propertyGrid;
    private List<PropertyValue> properties = List.of();
    private List<String> rawLines = List.of();
    private Path configPath;
    private String errorMessage;

    // Track registered property EditBoxes so we can swap them on scroll/tab change
    private final List<EditBox> registeredPropertyEditBoxes = new ArrayList<>();

    public WorldSettingsScreen(DimensionPreset preset, int hubPresetIndex) {
        super(Component.literal("OTG Editor — World Settings — " +
            (preset != null ? preset.getFolderName() : "?")));
        this.preset = preset;
        this.hubPresetIndex = hubPresetIndex;
    }

    @Override
    protected void init() {
        // Back button — always present, even on error
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(width - 70, height - 30, 60, 20).build());

        if (preset == null) {
            errorMessage = "No preset selected";
            return;
        }

        // Only load from disk on first init — rebuildWidgets() calls init() again
        // and must NOT reload, otherwise unsaved edits are lost on scroll/tab change.
        if (properties.isEmpty()) {
            configPath = resolveConfigPath(preset.getFolder());
            LOG.info("Loading config from: {}", configPath);

            Map<String, PropertyDefinition> definitions = PropertyExtractor.extractPresetDefinitions();
            if (definitions.isEmpty()) {
                errorMessage = "Failed to extract property definitions";
                LOG.error("PropertyExtractor returned empty definitions");
                return;
            }

            ConfigLoader.LoadResult result = ConfigLoader.load(configPath, definitions);
            if (result == null) {
                errorMessage = "Failed to load " + configPath.getFileName();
                return;
            }

            properties = result.properties();
            rawLines = result.rawLines();
        }

        // Property grid (full width, no side panels)
        int gridX = 10;
        int gridY = 25;
        int gridW = width - 20;
        int gridH = height - 70;
        propertyGrid = new PropertyGridWidget(gridX, gridY, gridW, gridH, PropertyGridMode.PRESET_EDITOR);
        propertyGrid.init(font, properties);

        // Register search EditBox
        addRenderableWidget(propertyGrid.getSearchEditBox());
        refreshPropertyEditBoxes();

        // Bottom buttons
        int btnY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
            .bounds(10, btnY, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Preview"), btn -> {
            minecraft.setScreen(new PreviewScreen(this, preset != null ? preset.getFolderName() : null));
        }).bounds(80, btnY, 60, 20).build());
    }

    private void refreshPropertyEditBoxes() {
        propertyGrid.syncEditBoxes(registeredPropertyEditBoxes, this::removeWidget, this::addRenderableWidget);
    }

    static Path resolveConfigPath(Path presetFolder) {
        Path newPath = presetFolder.resolve(Constants.DIMENSION_PRESET_CONFIG_FILE);
        if (java.nio.file.Files.exists(newPath)) return newPath;
        Path legacyPath = presetFolder.resolve(Constants.LEGACY_WORLD_CONFIG_FILE);
        if (java.nio.file.Files.exists(legacyPath)) return legacyPath;
        LOG.warn("Config file not found in {}, falling back to {}", presetFolder, newPath.getFileName());
        return newPath;
    }

    private void save() {
        if (configPath == null || properties.isEmpty()) return;
        boolean success = ConfigWriter.save(configPath, rawLines, properties);
        if (success) {
            properties.forEach(PropertyValue::clearDirty);
            PresetReloader.reload();
            LOG.info("Saved world settings to {}", configPath.getFileName());
        } else {
            errorMessage = "Failed to save!";
        }
    }

    private boolean hasDirtyProperties() {
        return properties.stream().anyMatch(PropertyValue::isDirty);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render background + widgets (buttons, EditBoxes)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw text AFTER super.render() so it's on top of blur/background
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);

        // Error message
        if (errorMessage != null) {
            graphics.drawCenteredString(font, errorMessage, width / 2, height / 2, 0xFF4444);
        }

        // Property grid (custom rendering — tabs, row backgrounds, labels)
        if (propertyGrid != null) {
            propertyGrid.render(graphics, mouseX, mouseY);
        }

        // Dirty indicator
        if (hasDirtyProperties()) {
            graphics.drawString(font, "\u25CF Unsaved changes", width - 130, height - 25, 0xFFAA8844);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (propertyGrid != null && propertyGrid.mouseClicked(mouseX, mouseY)) {
            // Tab change — need to swap EditBoxes (different category = different rows)
            refreshPropertyEditBoxes();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        if (propertyGrid != null && propertyGrid.mouseScrolled(mouseX, mouseY, deltaV)) {
            // Scroll — swap EditBoxes without full rebuildWidgets()
            refreshPropertyEditBoxes();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new EditorHubScreen(hubPresetIndex));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
