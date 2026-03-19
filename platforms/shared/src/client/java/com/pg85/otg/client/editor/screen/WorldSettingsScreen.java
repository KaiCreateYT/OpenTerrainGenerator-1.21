package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.data.*;
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
    private PropertyGridWidget propertyGrid;
    private List<PropertyValue> properties = List.of();
    private List<String> rawLines = List.of();
    private Path configPath;
    private String errorMessage;

    public WorldSettingsScreen(DimensionPreset preset) {
        super(Component.literal("OTG Editor — World Settings — " +
            (preset != null ? preset.getFolderName() : "?")));
        this.preset = preset;
    }

    @Override
    protected void init() {
        if (preset == null) {
            errorMessage = "No preset selected";
            return;
        }

        // Only load from disk on first init — rebuildWidgets() calls init() again
        // and must NOT reload, otherwise unsaved edits are lost on scroll/tab change.
        if (properties.isEmpty()) {
            configPath = preset.getFolder().resolve(Constants.DIMENSION_PRESET_CONFIG_FILE);
            Map<String, PropertyDefinition> definitions = PropertyExtractor.extractPresetDefinitions();
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
        propertyGrid = new PropertyGridWidget(gridX, gridY, gridW, gridH, false, false, false);
        propertyGrid.init(font, properties);

        // Register search EditBox
        addRenderableWidget(propertyGrid.getSearchEditBox());

        // Register all property EditBoxes
        for (EditBox eb : propertyGrid.getActiveEditBoxes()) {
            addRenderableWidget(eb);
        }

        // Bottom buttons
        int btnY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
            .bounds(10, btnY, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Preview"), btn -> {
            minecraft.setScreen(new PreviewScreen());
        }).bounds(80, btnY, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(width - 70, btnY, 60, 20).build());
    }

    private void save() {
        if (configPath == null || properties.isEmpty()) return;
        boolean success = ConfigWriter.save(configPath, rawLines, properties);
        if (success) {
            properties.forEach(PropertyValue::clearDirty);
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
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);

        // Error message
        if (errorMessage != null) {
            graphics.drawCenteredString(font, errorMessage, width / 2, height / 2, 0xFF4444);
        }

        // Property grid
        if (propertyGrid != null) {
            propertyGrid.render(graphics, mouseX, mouseY);
        }

        // Dirty indicator
        if (hasDirtyProperties()) {
            graphics.drawString(font, "\u25CF Unsaved changes", width - 130, height - 25, 0xFFAA8844);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (propertyGrid != null && propertyGrid.mouseClicked(mouseX, mouseY)) {
            // Rebuild EditBoxes after click (category change, etc.)
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        if (propertyGrid != null && propertyGrid.mouseScrolled(mouseX, mouseY, deltaV)) {
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new EditorHubScreen());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
