package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.data.*;
import com.pg85.otg.client.editor.widget.PropertyGridWidget;
import com.pg85.otg.client.editor.widget.ScrollableListWidget;
import com.pg85.otg.client.preview.PreviewScreen;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class BiomeEditorScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(BiomeEditorScreen.class);
    private static final int LEFT_PANEL_WIDTH = 160;

    private final DimensionPreset preset;
    private final int hubPresetIndex;

    private List<BiomeFileScanner.BiomeEntry> biomeEntries = List.of();
    private ScrollableListWidget biomeList;
    private EditBox biomeSearchBox;
    private List<String> filteredBiomeNames = List.of();
    private int selectedBiomeIndex = -1;

    private PropertyGridWidget propertyGrid;
    private List<PropertyValue> properties = List.of();
    private List<String> rawLines = List.of();
    private List<String> resourceQueueLines = List.of();
    private Path currentBiomePath;
    private Map<String, PropertyDefinition> biomeDefinitions;

    private String errorMessage;
    private String statusMessage;

    // Track registered property EditBoxes for lightweight refresh
    private final List<EditBox> registeredPropertyEditBoxes = new ArrayList<>();

    public BiomeEditorScreen(DimensionPreset preset, int hubPresetIndex) {
        super(Component.literal("OTG Editor — Biome Editor — " +
            (preset != null ? preset.getFolderName() : "?")));
        this.preset = preset;
        this.hubPresetIndex = hubPresetIndex;
    }

    @Override
    protected void init() {
        if (preset == null) {
            errorMessage = "No preset selected";
            return;
        }

        if (biomeDefinitions == null) {
            biomeDefinitions = PropertyExtractor.extractBiomeDefinitions();
        }

        if (biomeEntries.isEmpty()) {
            biomeEntries = BiomeFileScanner.scan(preset.getFolder());
            updateFilteredBiomes("");
        }

        // Biome search box
        biomeSearchBox = new EditBox(font, 6, 26, LEFT_PANEL_WIDTH - 12, 16, Component.literal("Search"));
        biomeSearchBox.setHint(Component.literal("Search biomes..."));
        biomeSearchBox.setResponder(this::updateFilteredBiomes);
        addRenderableWidget(biomeSearchBox);

        // Biome list
        biomeList = new ScrollableListWidget(4, 46, LEFT_PANEL_WIDTH - 8, height - 120, 16);
        biomeList.setItems(filteredBiomeNames);
        biomeList.setSelectedIndex(selectedBiomeIndex);
        biomeList.setOnSelect(this::onBiomeSelected);

        // CRUD buttons
        int btnY = height - 68;
        addRenderableWidget(Button.builder(Component.literal("New"), btn -> newBiome())
            .bounds(4, btnY, 48, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Clone"), btn -> cloneBiome())
            .bounds(56, btnY, 48, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> deleteBiome())
            .bounds(108, btnY, 48, 16).build());

        // Property grid (right of biome list)
        int gridX = LEFT_PANEL_WIDTH + 4;
        int gridW = width - gridX - 4;
        int gridH = height - 70;
        propertyGrid = new PropertyGridWidget(gridX, 14, gridW, gridH, true, true, false);

        // Load selected biome if any
        if (selectedBiomeIndex >= 0 && selectedBiomeIndex < filteredBiomeNames.size()) {
            loadBiome(selectedBiomeIndex);
        }

        registeredPropertyEditBoxes.clear();
        if (!properties.isEmpty()) {
            propertyGrid.init(font, properties);
            addRenderableWidget(propertyGrid.getSearchEditBox());
            for (EditBox eb : propertyGrid.getActiveEditBoxes()) {
                addRenderableWidget(eb);
                registeredPropertyEditBoxes.add(eb);
            }
        }

        // Bottom buttons
        int btnBarY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
            .bounds(LEFT_PANEL_WIDTH + 4, btnBarY, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Preview"), btn ->
            minecraft.setScreen(new PreviewScreen())
        ).bounds(LEFT_PANEL_WIDTH + 60, btnBarY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Browse BO3"), btn ->
            minecraft.setScreen(new BOBrowserScreen(preset, hubPresetIndex))
        ).bounds(LEFT_PANEL_WIDTH + 126, btnBarY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(width - 54, btnBarY, 50, 20).build());
    }

    private void updateFilteredBiomes(String filter) {
        if (filter == null || filter.isBlank()) {
            filteredBiomeNames = biomeEntries.stream()
                .map(BiomeFileScanner.BiomeEntry::name)
                .collect(Collectors.toList());
        } else {
            String lower = filter.toLowerCase();
            filteredBiomeNames = biomeEntries.stream()
                .map(BiomeFileScanner.BiomeEntry::name)
                .filter(n -> n.toLowerCase().contains(lower))
                .collect(Collectors.toList());
        }
        if (biomeList != null) {
            biomeList.setItems(filteredBiomeNames);
            biomeList.setSelectedIndex(selectedBiomeIndex < filteredBiomeNames.size() ? selectedBiomeIndex : -1);
        }
    }

    private void onBiomeSelected(int index) {
        if (index < 0 || index >= filteredBiomeNames.size()) return;
        if (hasDirtyProperties()) {
            statusMessage = "Unsaved changes! Save before switching biomes.";
            return;
        }
        selectedBiomeIndex = index;
        properties = List.of();
        rebuildWidgets();
    }

    private void loadBiome(int index) {
        String biomeName = filteredBiomeNames.get(index);
        BiomeFileScanner.BiomeEntry entry = biomeEntries.stream()
            .filter(e -> e.name().equals(biomeName))
            .findFirst().orElse(null);
        if (entry == null) return;

        currentBiomePath = entry.path();
        ConfigLoader.LoadResult result = ConfigLoader.load(currentBiomePath, biomeDefinitions);
        if (result == null) {
            errorMessage = "Failed to load " + biomeName;
            properties = List.of();
            return;
        }

        properties = result.properties();
        rawLines = result.rawLines();
        resourceQueueLines = result.resourceQueueLines();
        errorMessage = null;
        statusMessage = null;
    }

    private void save() {
        if (currentBiomePath == null || properties.isEmpty()) return;
        boolean success = ConfigWriter.save(currentBiomePath, rawLines, properties);
        if (success) {
            properties.forEach(PropertyValue::clearDirty);
            statusMessage = "Saved!";
        } else {
            statusMessage = "Save failed!";
        }
    }

    private boolean hasDirtyProperties() {
        return properties.stream().anyMatch(PropertyValue::isDirty);
    }

    private void newBiome() {
        String name = "NewBiome";
        Path biomesDir = BiomeFileScanner.getBiomesDirectory(preset.getFolder());
        Path newPath = biomesDir.resolve(name + ".bc");
        int counter = 1;
        while (Files.exists(newPath)) {
            name = "NewBiome" + counter++;
            newPath = biomesDir.resolve(name + ".bc");
        }

        if (ConfigWriter.createFromDefaults(newPath, biomeDefinitions)) {
            biomeEntries = BiomeFileScanner.scan(preset.getFolder());
            updateFilteredBiomes(biomeSearchBox != null ? biomeSearchBox.getValue() : "");
            String finalName = name;
            selectedBiomeIndex = filteredBiomeNames.indexOf(finalName);
            properties = List.of();
            rebuildWidgets();
        }
    }

    private void cloneBiome() {
        if (selectedBiomeIndex < 0 || selectedBiomeIndex >= filteredBiomeNames.size()) return;
        String sourceName = filteredBiomeNames.get(selectedBiomeIndex);
        BiomeFileScanner.BiomeEntry source = biomeEntries.stream()
            .filter(e -> e.name().equals(sourceName))
            .findFirst().orElse(null);
        if (source == null) return;

        String cloneName = sourceName + "_copy";
        Path biomesDir = BiomeFileScanner.getBiomesDirectory(preset.getFolder());
        Path clonePath = biomesDir.resolve(cloneName + ".bc");
        int counter = 1;
        while (Files.exists(clonePath)) {
            cloneName = sourceName + "_copy" + counter++;
            clonePath = biomesDir.resolve(cloneName + ".bc");
        }

        try {
            Files.copy(source.path(), clonePath);
            biomeEntries = BiomeFileScanner.scan(preset.getFolder());
            updateFilteredBiomes(biomeSearchBox != null ? biomeSearchBox.getValue() : "");
            String finalCloneName = cloneName;
            selectedBiomeIndex = filteredBiomeNames.indexOf(finalCloneName);
            properties = List.of();
            rebuildWidgets();
        } catch (IOException e) {
            LOG.error("Failed to clone biome", e);
            statusMessage = "Clone failed!";
        }
    }

    private void deleteBiome() {
        if (selectedBiomeIndex < 0 || selectedBiomeIndex >= filteredBiomeNames.size()) return;
        String biomeName = filteredBiomeNames.get(selectedBiomeIndex);
        BiomeFileScanner.BiomeEntry entry = biomeEntries.stream()
            .filter(e -> e.name().equals(biomeName))
            .findFirst().orElse(null);
        if (entry == null) return;

        try {
            Files.deleteIfExists(entry.path());
            biomeEntries = BiomeFileScanner.scan(preset.getFolder());
            updateFilteredBiomes(biomeSearchBox != null ? biomeSearchBox.getValue() : "");
            selectedBiomeIndex = Math.min(selectedBiomeIndex, filteredBiomeNames.size() - 1);
            properties = List.of();
            currentBiomePath = null;
            rebuildWidgets();
        } catch (IOException e) {
            LOG.error("Failed to delete biome", e);
            statusMessage = "Delete failed!";
        }
    }

    private void refreshPropertyEditBoxes() {
        for (EditBox eb : registeredPropertyEditBoxes) {
            removeWidget(eb);
        }
        registeredPropertyEditBoxes.clear();
        for (EditBox eb : propertyGrid.getActiveEditBoxes()) {
            addRenderableWidget(eb);
            registeredPropertyEditBoxes.add(eb);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 3, 0xFFFFFF);
        graphics.drawString(font, "Biomes (" + biomeEntries.size() + ")", 6, 16, 0xFF888888);
        graphics.fill(LEFT_PANEL_WIDTH, 0, LEFT_PANEL_WIDTH + 1, height, 0xFF333333);

        if (biomeList != null) {
            biomeList.render(graphics);
        }

        if (propertyGrid != null && !properties.isEmpty()) {
            propertyGrid.render(graphics, mouseX, mouseY);
        } else if (selectedBiomeIndex < 0) {
            graphics.drawCenteredString(font, "Select a biome to edit",
                LEFT_PANEL_WIDTH + (width - LEFT_PANEL_WIDTH) / 2, height / 2, 0xFF666666);
        }

        if (errorMessage != null) {
            graphics.drawString(font, errorMessage, LEFT_PANEL_WIDTH + 4, height - 48, 0xFFFF4444);
        }
        if (statusMessage != null) {
            graphics.drawString(font, statusMessage, LEFT_PANEL_WIDTH + 4, height - 48, 0xFFAAAA44);
        }

        if (hasDirtyProperties()) {
            graphics.drawString(font, "\u25CF Unsaved", width - 80, height - 25, 0xFFAA8844);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (biomeList != null && biomeList.mouseClicked(mouseX, mouseY)) return true;
        if (propertyGrid != null && propertyGrid.mouseClicked(mouseX, mouseY)) {
            refreshPropertyEditBoxes();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        if (biomeList != null && biomeList.mouseScrolled(mouseX, mouseY, deltaV)) return true;
        if (propertyGrid != null && propertyGrid.mouseScrolled(mouseX, mouseY, deltaV)) {
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
