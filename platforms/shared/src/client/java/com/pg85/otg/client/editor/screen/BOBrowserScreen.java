package com.pg85.otg.client.editor.screen;

import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.client.editor.widget.ScrollableListWidget;
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

public class BOBrowserScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(BOBrowserScreen.class);

    private final DimensionPreset preset;
    private final int hubPresetIndex;
    private List<String> allObjectNames = new ArrayList<>();
    private List<String> filteredNames = new ArrayList<>();
    private ScrollableListWidget objectList;
    private int selectedIndex = -1;

    public BOBrowserScreen(DimensionPreset preset, int hubPresetIndex) {
        super(Component.literal("OTG Editor — Browse BO3/BO4"));
        this.preset = preset;
        this.hubPresetIndex = hubPresetIndex;
    }

    @Override
    protected void init() {
        if (allObjectNames.isEmpty()) {
            Path objectsDir = preset.getFolder().resolve(com.pg85.otg.constants.Constants.OBJECTS_FOLDER);
            if (!Files.isDirectory(objectsDir)) {
                objectsDir = preset.getFolder().resolve(com.pg85.otg.constants.Constants.LEGACY_WORLD_OBJECTS_FOLDER);
            }
            if (Files.isDirectory(objectsDir)) {
                try (var walk = Files.walk(objectsDir)) {
                    final Path finalObjectsDir = objectsDir;
                    allObjectNames = walk.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".bo3") || name.endsWith(".bo4");
                        })
                        .map(p -> finalObjectsDir.relativize(p).toString())
                        .sorted()
                        .collect(Collectors.toList());
                } catch (IOException e) {
                    LOG.error("Failed to scan Objects folder", e);
                }
            }
        }

        filteredNames = new ArrayList<>(allObjectNames);

        EditBox searchBox = new EditBox(font, 10, 30, 200, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search objects..."));
        searchBox.setResponder(filter -> {
            if (filter == null || filter.isBlank()) {
                filteredNames = new ArrayList<>(allObjectNames);
            } else {
                String lower = filter.toLowerCase();
                filteredNames = allObjectNames.stream()
                    .filter(n -> n.toLowerCase().contains(lower))
                    .collect(Collectors.toList());
            }
            objectList.setItems(filteredNames);
        });
        addRenderableWidget(searchBox);

        objectList = new ScrollableListWidget(10, 52, width - 20, height - 100, 14);
        objectList.setItems(filteredNames);
        objectList.setOnSelect(idx -> selectedIndex = idx);

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(width / 2 - 40, height - 30, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
        graphics.drawString(font, allObjectNames.size() + " objects found", 10, 20, 0xFF888888);

        objectList.render(graphics);

        if (selectedIndex >= 0 && selectedIndex < filteredNames.size()) {
            graphics.drawString(font, "Selected: " + filteredNames.get(selectedIndex),
                10, height - 50, 0xFF66CC66);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (objectList.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        if (objectList.mouseScrolled(mouseX, mouseY, deltaV)) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new BiomeEditorScreen(preset, hubPresetIndex));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
