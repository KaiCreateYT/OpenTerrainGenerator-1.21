package com.pg85.otg.client.editor.screen;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.client.editor.widget.TreeListWidget;
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
    private List<String> allObjectPaths = List.of();
    private TreeListWidget treeList;
    private EditBox searchBox;
    private String selectedPath;

    public BOBrowserScreen(DimensionPreset preset, int hubPresetIndex) {
        super(Component.literal("OTG Editor — Browse BO3/BO4"));
        this.preset = preset;
        this.hubPresetIndex = hubPresetIndex;
    }

    @Override
    protected void init() {
        // Scan only once
        if (allObjectPaths.isEmpty()) {
            allObjectPaths = scanObjects();
        }

        // Search box
        searchBox = new EditBox(font, 10, 30, 200, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search objects..."));
        searchBox.setResponder(filter -> {
            if (treeList != null) {
                treeList.filter(filter);
            }
        });
        addRenderableWidget(searchBox);

        // Tree list
        treeList = new TreeListWidget(10, 52, width - 20, height - 100, 14);
        treeList.buildFromPaths(allObjectPaths);
        treeList.setOnSelect(node -> selectedPath = node.fullPath());

        // Back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(width / 2 - 40, height - 30, 80, 20).build());
    }

    private List<String> scanObjects() {
        Path objectsDir = preset.getFolder().resolve(Constants.OBJECTS_FOLDER);
        if (!Files.isDirectory(objectsDir)) {
            objectsDir = preset.getFolder().resolve(Constants.LEGACY_WORLD_OBJECTS_FOLDER);
        }
        if (!Files.isDirectory(objectsDir)) return List.of();

        try (var walk = Files.walk(objectsDir)) {
            final Path root = objectsDir;
            return walk.filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".bo3") || name.endsWith(".bo4") || name.endsWith(".bo2");
                })
                .map(p -> root.relativize(p).toString())
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error("Failed to scan Objects folder", e);
            return List.of();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);

        int fileCount = treeList != null ? treeList.getTotalFileCount() : 0;
        graphics.drawString(font, fileCount + " objects found", 10, 20, 0xFF888888);

        if (treeList != null) {
            treeList.render(graphics);
        }

        if (selectedPath != null) {
            graphics.drawString(font, "Selected: " + selectedPath, 10, height - 50, 0xFF66CC66);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (treeList != null && treeList.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        if (treeList != null && treeList.mouseScrolled(mouseX, mouseY, deltaV)) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new BiomeEditorScreen(preset, hubPresetIndex));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
