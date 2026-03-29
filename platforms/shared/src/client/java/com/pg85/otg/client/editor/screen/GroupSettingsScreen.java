package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.data.*;
import com.pg85.otg.client.editor.widget.PropertyGridWidget;
import com.pg85.otg.client.editor.widget.ScrollableListWidget;
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
import java.util.stream.Collectors;

public class GroupSettingsScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(GroupSettingsScreen.class);
    private static final int LEFT_PANEL_WIDTH = 150;
    private static final int CENTER_PANEL_WIDTH = 200;

    private final DimensionPreset preset;
    private final int hubPresetIndex;

    // Groups
    private List<BiomeGroupData> groups;
    private ScrollableListWidget groupList;
    private int selectedGroupIndex = -1;

    // Group params (center)
    private EditBox depthInput, rarityInput, minTempInput, maxTempInput;

    // Biome assignment (center)
    private ScrollableListWidget availableBiomesList;
    private ScrollableListWidget inGroupBiomesList;
    private List<String> allBiomeNames;

    // Property overrides (right)
    private PropertyGridWidget propertyGrid;
    private List<PropertyValue> overrideProperties = List.of();
    private Map<String, Map<String, GroupOverrideStore.GroupPropertyOverride>> allOverrides;
    private Map<String, PropertyDefinition> biomeDefinitions;

    // Raw lines for saving
    private List<String> rawLines;
    private Path iniPath;
    private String statusMessage;
    private String errorMessage;

    // Track registered property EditBoxes for lightweight refresh
    private final List<EditBox> registeredPropertyEditBoxes = new ArrayList<>();

    public GroupSettingsScreen(DimensionPreset preset, int hubPresetIndex) {
        super(Component.literal("OTG Editor — Group Settings — " +
            (preset != null ? preset.getFolderName() : "?")));
        this.preset = preset;
        this.hubPresetIndex = hubPresetIndex;
    }

    @Override
    protected void init() {
        if (preset == null) {
            errorMessage = "No preset selected";
            addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
                .bounds(width / 2 - 30, height - 30, 60, 20).build());
            return;
        }

        // Load data on first init only (rebuildWidgets calls init again)
        if (groups == null) {
            loadData();
        }

        // --- Left panel: Group list ---
        int leftX = 4;
        int listY = 26;
        int listH = height - 80;

        groupList = new ScrollableListWidget(leftX, listY, LEFT_PANEL_WIDTH - 8, listH, 16);
        groupList.setItems(groups.stream()
            .filter(g -> !g.isDeleted())
            .map(BiomeGroupData::getName)
            .collect(Collectors.toList()));
        groupList.setSelectedIndex(selectedGroupIndex);
        groupList.setOnSelect(this::onGroupSelected);

        // New / Delete buttons
        int btnY = height - 50;
        addRenderableWidget(Button.builder(Component.literal("New"), btn -> newGroup())
            .bounds(leftX, btnY, 65, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> deleteGroup())
            .bounds(leftX + 70, btnY, 65, 16).build());

        // --- Center panel: Group params + biome assignment ---
        int centerX = LEFT_PANEL_WIDTH + 4;

        if (selectedGroupIndex >= 0) {
            BiomeGroupData group = getSelectedGroup();
            if (group != null) {
                initCenterPanel(centerX, group);
            }
        }

        // --- Right panel: PropertyGrid ---
        int gridX = LEFT_PANEL_WIDTH + CENTER_PANEL_WIDTH + 8;
        int gridW = width - gridX - 4;
        int gridH = height - 70;
        propertyGrid = new PropertyGridWidget(gridX, 14, gridW, gridH, true, true, true);

        if (selectedGroupIndex >= 0 && !overrideProperties.isEmpty()) {
            propertyGrid.init(font, overrideProperties);
            addRenderableWidget(propertyGrid.getSearchEditBox());
            refreshPropertyEditBoxes();
        }

        // --- Bottom buttons ---
        int barY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
            .bounds(LEFT_PANEL_WIDTH + 4, barY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(width - 54, barY, 50, 20).build());
    }

    private void initCenterPanel(int centerX, BiomeGroupData group) {
        int py = 26;
        int labelW = 60;
        int inputW = CENTER_PANEL_WIDTH - labelW - 12;

        // Depth
        depthInput = new EditBox(font, centerX + labelW, py, inputW, 14, Component.literal("Depth"));
        depthInput.setValue(String.valueOf(group.getGenerationDepth()));
        depthInput.setResponder(val -> {
            try { group.setGenerationDepth(Integer.parseInt(val)); } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(depthInput);
        py += 18;

        // Rarity
        rarityInput = new EditBox(font, centerX + labelW, py, inputW, 14, Component.literal("Rarity"));
        rarityInput.setValue(String.valueOf(group.getRarity()));
        rarityInput.setResponder(val -> {
            try { group.setRarity(Integer.parseInt(val)); } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(rarityInput);
        py += 18;

        // Min Temp
        minTempInput = new EditBox(font, centerX + labelW, py, inputW, 14, Component.literal("MinTemp"));
        minTempInput.setValue(String.valueOf(group.getMinTemp()));
        minTempInput.setResponder(val -> {
            try { group.setMinTemp(Double.parseDouble(val)); } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(minTempInput);
        py += 18;

        // Max Temp
        maxTempInput = new EditBox(font, centerX + labelW, py, inputW, 14, Component.literal("MaxTemp"));
        maxTempInput.setValue(String.valueOf(group.getMaxTemp()));
        maxTempInput.setResponder(val -> {
            try { group.setMaxTemp(Double.parseDouble(val)); } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(maxTempInput);
        py += 24;

        // Biome assignment lists
        int biomeListW = (CENTER_PANEL_WIDTH - 30) / 2;
        int biomeListH = height - py - 90;

        // Available biomes (not in group)
        List<String> available = allBiomeNames.stream()
            .filter(b -> !group.getBiomes().contains(b))
            .collect(Collectors.toList());

        availableBiomesList = new ScrollableListWidget(centerX, py + 12, biomeListW, biomeListH, 14);
        availableBiomesList.setItems(available);

        // In-group biomes
        inGroupBiomesList = new ScrollableListWidget(centerX + biomeListW + 26, py + 12, biomeListW, biomeListH, 14);
        inGroupBiomesList.setItems(new ArrayList<>(group.getBiomes()));

        // Add/Remove buttons
        int arrowX = centerX + biomeListW + 4;
        int arrowY = py + 12 + biomeListH / 2 - 18;
        addRenderableWidget(Button.builder(Component.literal("\u25B6"), btn -> addBiomeToGroup())
            .bounds(arrowX, arrowY, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("\u25C0"), btn -> removeBiomeFromGroup())
            .bounds(arrowX, arrowY + 20, 18, 16).build());
    }

    private void loadData() {
        iniPath = WorldSettingsScreen.resolveConfigPath(preset.getFolder());

        // Load raw lines for group parsing
        try {
            rawLines = java.nio.file.Files.readAllLines(iniPath);
        } catch (Exception e) {
            LOG.error("Failed to read config: {}", iniPath, e);
            rawLines = List.of();
            errorMessage = "Failed to read " + iniPath.getFileName();
        }

        groups = BiomeGroupParser.parse(rawLines);
        allOverrides = GroupOverrideStore.load(preset.getFolder());

        // Load biome definitions for property grid
        if (biomeDefinitions == null) {
            biomeDefinitions = PropertyExtractor.extractBiomeDefinitions();
        }

        // Scan biome files for names
        allBiomeNames = BiomeFileScanner.scan(preset.getFolder()).stream()
            .map(BiomeFileScanner.BiomeEntry::name)
            .collect(Collectors.toList());

        LOG.info("Loaded {} groups, {} biomes, {} group overrides", groups.size(), allBiomeNames.size(), allOverrides.size());
    }

    private void onGroupSelected(int index) {
        List<BiomeGroupData> visible = groups.stream().filter(g -> !g.isDeleted()).collect(Collectors.toList());
        if (index < 0 || index >= visible.size()) return;

        selectedGroupIndex = index;
        statusMessage = null;

        // Load overrides for this group into PropertyValues
        BiomeGroupData group = visible.get(index);
        loadOverridesForGroup(group.getName());

        rebuildWidgets();
    }

    private void loadOverridesForGroup(String groupName) {
        Map<String, GroupOverrideStore.GroupPropertyOverride> groupOverrides =
            allOverrides.getOrDefault(groupName, Map.of());

        List<PropertyValue> props = new ArrayList<>();
        for (PropertyDefinition def : biomeDefinitions.values()) {
            PropertyValue pv = new PropertyValue(def, def.defaultValue());
            GroupOverrideStore.GroupPropertyOverride ov = groupOverrides.get(def.name());
            if (ov != null) {
                pv.setValue(ov.value());
                pv.setOverride(ov.override());
                pv.setMerge(ov.merge());
                pv.setOpv(ov.opv());
                pv.clearDirty(); // loaded state is not dirty
            }
            props.add(pv);
        }
        overrideProperties = props;
    }

    private BiomeGroupData getSelectedGroup() {
        List<BiomeGroupData> visible = groups.stream().filter(g -> !g.isDeleted()).collect(Collectors.toList());
        if (selectedGroupIndex < 0 || selectedGroupIndex >= visible.size()) return null;
        return visible.get(selectedGroupIndex);
    }

    private void newGroup() {
        String name = "NewGroup";
        int counter = 1;
        Set<String> names = groups.stream().map(BiomeGroupData::getName).collect(Collectors.toSet());
        while (names.contains(name)) {
            name = "NewGroup" + counter++;
        }
        BiomeGroupData group = new BiomeGroupData(name, 0, 100, List.of(), 0.0, 0.0);
        group.setNew(true);
        groups.add(group);

        List<BiomeGroupData> visible = groups.stream().filter(g -> !g.isDeleted()).collect(Collectors.toList());
        selectedGroupIndex = visible.size() - 1;
        loadOverridesForGroup(name);
        rebuildWidgets();
    }

    private void deleteGroup() {
        BiomeGroupData group = getSelectedGroup();
        if (group == null) return;

        group.setDeleted(true);
        allOverrides.remove(group.getName());

        // Adjust selection
        List<BiomeGroupData> visible = groups.stream().filter(g -> !g.isDeleted()).collect(Collectors.toList());
        if (visible.isEmpty()) {
            selectedGroupIndex = -1;
            overrideProperties = List.of();
        } else {
            selectedGroupIndex = Math.min(selectedGroupIndex, visible.size() - 1);
            loadOverridesForGroup(visible.get(selectedGroupIndex).getName());
        }
        rebuildWidgets();
    }

    private void addBiomeToGroup() {
        BiomeGroupData group = getSelectedGroup();
        if (group == null || availableBiomesList == null) return;
        int idx = availableBiomesList.getSelectedIndex();
        List<String> available = allBiomeNames.stream()
            .filter(b -> !group.getBiomes().contains(b))
            .collect(Collectors.toList());
        if (idx < 0 || idx >= available.size()) return;

        String biome = available.get(idx);
        group.getBiomes().add(biome);
        group.setRarity(group.getRarity()); // mark dirty
        rebuildWidgets();
    }

    private void removeBiomeFromGroup() {
        BiomeGroupData group = getSelectedGroup();
        if (group == null || inGroupBiomesList == null) return;
        int idx = inGroupBiomesList.getSelectedIndex();
        if (idx < 0 || idx >= group.getBiomes().size()) return;

        group.getBiomes().remove(idx);
        group.setRarity(group.getRarity()); // mark dirty
        rebuildWidgets();
    }

    private void save() {
        if (iniPath == null || groups == null) return;

        // Collect property overrides from PropertyGrid back to allOverrides
        BiomeGroupData group = getSelectedGroup();
        if (group != null && !overrideProperties.isEmpty()) {
            collectOverridesFromGrid(group.getName());
        }

        // Save groups to .ini
        rawLines = ConfigWriter.saveBiomeGroups(iniPath, rawLines, groups);

        // Save overrides to .otg-editor.json
        GroupOverrideStore.save(preset.getFolder(), allOverrides);

        // Clear dirty flags
        for (BiomeGroupData g : groups) g.clearDirty();
        overrideProperties.forEach(PropertyValue::clearDirty);

        PresetReloader.reload();
        statusMessage = "Saved!";
        LOG.info("Saved groups and overrides for {}", preset.getFolderName());
    }

    private void collectOverridesFromGrid(String groupName) {
        Map<String, GroupOverrideStore.GroupPropertyOverride> map = new LinkedHashMap<>();
        for (PropertyValue pv : overrideProperties) {
            if (pv.isOverride()) {
                map.put(pv.getDefinition().name(),
                    new GroupOverrideStore.GroupPropertyOverride(
                        pv.getValue(), pv.isOverride(), pv.isMerge(), pv.isOpv()));
            }
        }
        if (map.isEmpty()) {
            allOverrides.remove(groupName);
        } else {
            allOverrides.put(groupName, map);
        }
    }

    private boolean hasDirtyState() {
        if (groups != null && groups.stream().anyMatch(BiomeGroupData::isDirty)) return true;
        return overrideProperties.stream().anyMatch(PropertyValue::isDirty);
    }

    private void refreshPropertyEditBoxes() {
        propertyGrid.syncEditBoxes(registeredPropertyEditBoxes, this::removeWidget, this::addRenderableWidget);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 3, 0xFFFFFF);

        // Left panel header
        int visibleCount = groups != null ? (int) groups.stream().filter(g -> !g.isDeleted()).count() : 0;
        graphics.drawString(font, "Groups (" + visibleCount + ")", 6, 16, 0xFF888888);

        // Left panel divider
        graphics.fill(LEFT_PANEL_WIDTH, 0, LEFT_PANEL_WIDTH + 1, height, 0xFF333333);

        // Group list
        if (groupList != null) {
            groupList.render(graphics);
        }

        // Center panel
        int centerX = LEFT_PANEL_WIDTH + 4;
        graphics.fill(LEFT_PANEL_WIDTH + CENTER_PANEL_WIDTH + 4, 0,
            LEFT_PANEL_WIDTH + CENTER_PANEL_WIDTH + 5, height, 0xFF333333);

        if (selectedGroupIndex >= 0) {
            BiomeGroupData group = getSelectedGroup();
            if (group != null) {
                int py = 26;
                graphics.drawString(font, "Depth:", centerX, py + 3, 0xFFAAAAAA);
                py += 18;
                graphics.drawString(font, "Rarity:", centerX, py + 3, 0xFFAAAAAA);
                py += 18;
                graphics.drawString(font, "MinTemp:", centerX, py + 3, 0xFFAAAAAA);
                py += 18;
                graphics.drawString(font, "MaxTemp:", centerX, py + 3, 0xFFAAAAAA);
                py += 24;

                // Biome list headers
                int biomeListW = (CENTER_PANEL_WIDTH - 30) / 2;
                graphics.drawString(font, "Available", centerX, py, 0xFF888888);
                graphics.drawString(font, "In Group", centerX + biomeListW + 26, py, 0xFF888888);

                // Render biome lists
                if (availableBiomesList != null) availableBiomesList.render(graphics);
                if (inGroupBiomesList != null) inGroupBiomesList.render(graphics);
            }
        } else {
            graphics.drawCenteredString(font, "Select a group",
                LEFT_PANEL_WIDTH + CENTER_PANEL_WIDTH / 2, height / 2, 0xFF666666);
        }

        // Right panel: PropertyGrid
        if (propertyGrid != null && !overrideProperties.isEmpty()) {
            propertyGrid.render(graphics, mouseX, mouseY);
        } else if (selectedGroupIndex >= 0) {
            int gridX = LEFT_PANEL_WIDTH + CENTER_PANEL_WIDTH + 8;
            int gridCenterX = gridX + (width - gridX - 4) / 2;
            graphics.drawCenteredString(font, "Property Overrides", gridCenterX, height / 2, 0xFF666666);
        }

        // Error/status messages
        if (errorMessage != null) {
            graphics.drawCenteredString(font, errorMessage, width / 2, height / 2, 0xFF4444);
        }
        if (statusMessage != null) {
            graphics.drawString(font, statusMessage, LEFT_PANEL_WIDTH + 70, height - 25, 0xFFAAAA44);
        }

        // Dirty indicator
        if (hasDirtyState()) {
            graphics.drawString(font, "\u25CF Unsaved", width - 80, height - 25, 0xFFAA8844);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (groupList != null && groupList.mouseClicked(mouseX, mouseY)) return true;
        if (availableBiomesList != null && availableBiomesList.mouseClicked(mouseX, mouseY)) return true;
        if (inGroupBiomesList != null && inGroupBiomesList.mouseClicked(mouseX, mouseY)) return true;
        if (propertyGrid != null && propertyGrid.mouseClicked(mouseX, mouseY)) {
            refreshPropertyEditBoxes();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        if (groupList != null && groupList.mouseScrolled(mouseX, mouseY, deltaV)) return true;
        if (availableBiomesList != null && availableBiomesList.mouseScrolled(mouseX, mouseY, deltaV)) return true;
        if (inGroupBiomesList != null && inGroupBiomesList.mouseScrolled(mouseX, mouseY, deltaV)) return true;
        if (propertyGrid != null && propertyGrid.mouseScrolled(mouseX, mouseY, deltaV)) {
            refreshPropertyEditBoxes();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (groupList != null && groupList.mouseDragged(mouseX, mouseY)) return true;
        if (availableBiomesList != null && availableBiomesList.mouseDragged(mouseX, mouseY)) return true;
        if (inGroupBiomesList != null && inGroupBiomesList.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (groupList != null && groupList.mouseReleased()) return true;
        if (availableBiomesList != null && availableBiomesList.mouseReleased()) return true;
        if (inGroupBiomesList != null && inGroupBiomesList.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new EditorHubScreen(hubPresetIndex));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
