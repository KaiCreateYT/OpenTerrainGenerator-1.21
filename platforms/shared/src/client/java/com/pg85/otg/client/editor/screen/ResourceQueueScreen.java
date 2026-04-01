package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.data.ResourceEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ResourceQueueScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int MARGIN = 10;

    private final Screen parent;
    private final List<ResourceEntry> entries;
    private final Consumer<List<ResourceEntry>> onSave;

    private int scrollOffset = 0;
    private List<EditBox> entryEditBoxes = new ArrayList<>();

    public ResourceQueueScreen(Screen parent, List<ResourceEntry> entries, Consumer<List<ResourceEntry>> onSave) {
        super(Component.literal("Resource Queue Editor"));
        this.parent = parent;
        this.entries = new ArrayList<>(entries);
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        entryEditBoxes.clear();

        int listY = 30;
        int listH = height - 70;
        int maxVisible = listH / ROW_HEIGHT;
        int editW = width - 100;

        // Filter out deleted for display count
        List<ResourceEntry> visible = entries.stream().filter(e -> !e.isDeleted()).collect(java.util.stream.Collectors.toList());

        int maxScroll = Math.max(0, visible.size() - maxVisible);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < maxVisible && (i + scrollOffset) < visible.size(); i++) {
            int visIdx = i + scrollOffset;
            ResourceEntry entry = visible.get(visIdx);
            // Find actual index in entries list
            int actualIdx = entries.indexOf(entry);

            int ey = listY + i * ROW_HEIGHT;

            EditBox eb = new EditBox(font, MARGIN + 6, ey + 1, editW, ROW_HEIGHT - 3, Component.empty());
            eb.setMaxLength(500);
            eb.setValue(entry.getLine());
            final int entryIdx = actualIdx;
            eb.setResponder(val -> entries.get(entryIdx).setLine(val));
            addRenderableWidget(eb);
            entryEditBoxes.add(eb);

            // Delete button
            final int delIdx = actualIdx;
            addRenderableWidget(Button.builder(Component.literal("X"), btn -> {
                entries.get(delIdx).setDeleted(true);
                rebuildWidgets();
            }).bounds(width - 85, ey, 18, ROW_HEIGHT - 3).build());

            // Move up
            if (actualIdx > 0) {
                final int upIdx = actualIdx;
                addRenderableWidget(Button.builder(Component.literal("\u25B2"), btn -> {
                    swap(upIdx, upIdx - 1);
                    rebuildWidgets();
                }).bounds(width - 63, ey, 18, ROW_HEIGHT - 3).build());
            }

            // Move down
            if (actualIdx < entries.size() - 1) {
                final int downIdx = actualIdx;
                addRenderableWidget(Button.builder(Component.literal("\u25BC"), btn -> {
                    swap(downIdx, downIdx + 1);
                    rebuildWidgets();
                }).bounds(width - 42, ey, 18, ROW_HEIGHT - 3).build());
            }
        }

        // Bottom buttons
        int btnY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Add Entry"), btn -> {
            entries.add(new ResourceEntry("CustomObject()"));
            rebuildWidgets();
        }).bounds(MARGIN, btnY, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            List<ResourceEntry> result = new ArrayList<>();
            for (ResourceEntry e : entries) {
                if (!e.isDeleted()) result.add(e);
            }
            onSave.accept(result);
            minecraft.setScreen(parent);
        }).bounds(width - 60, btnY, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            minecraft.setScreen(parent);
        }).bounds(width - 120, btnY, 50, 20).build());
    }

    private void swap(int a, int b) {
        ResourceEntry tmp = entries.get(a);
        entries.set(a, entries.get(b));
        entries.set(b, tmp);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
        long count = entries.stream().filter(e -> !e.isDeleted()).count();
        graphics.drawString(font, count + " entries", MARGIN, 20, 0xFF888888);

        // Colored dots per function type
        int listY = 30;
        int maxVisible = (height - 70) / ROW_HEIGHT;
        List<ResourceEntry> visible = entries.stream().filter(e -> !e.isDeleted()).collect(java.util.stream.Collectors.toList());
        for (int i = 0; i < maxVisible && (i + scrollOffset) < visible.size(); i++) {
            int ey = listY + i * ROW_HEIGHT;
            String funcName = visible.get(i + scrollOffset).getFunctionName();
            graphics.fill(MARGIN, ey + 4, MARGIN + 4, ey + ROW_HEIGHT - 6, getFunctionColor(funcName));
        }
    }

    private int getFunctionColor(String funcName) {
        return switch (funcName) {
            case "Ore", "UnderWaterOre" -> 0xFF8888CC;
            case "CustomObject" -> 0xFF88CC88;
            case "CustomStructure" -> 0xFFCC8888;
            case "Tree" -> 0xFF44AA44;
            case "Dungeon" -> 0xFFAAAA44;
            case "Grass", "Plant" -> 0xFF66CC66;
            case "SmallLake", "UnderGroundLake" -> 0xFF4488CC;
            default -> 0xFF888888;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        long visibleCount = entries.stream().filter(e -> !e.isDeleted()).count();
        int maxVisible = (height - 70) / ROW_HEIGHT;
        int maxScroll = Math.max(0, (int) visibleCount - maxVisible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) deltaV));
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
