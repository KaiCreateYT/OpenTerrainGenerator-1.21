package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.data.BiomeFileScanner;
import com.pg85.otg.client.editor.widget.ScrollableListWidget;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BiomeSelectDialog extends Screen {

    private final DimensionPreset preset;
    private final Screen parent;
    private final Consumer<String> onSelect;
    private ScrollableListWidget biomeList;
    private List<String> biomeNames;

    public BiomeSelectDialog(DimensionPreset preset, Screen parent, Consumer<String> onSelect) {
        super(Component.literal("Select Biome"));
        this.preset = preset;
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        biomeNames = BiomeFileScanner.scan(preset.getFolder()).stream()
            .map(BiomeFileScanner.BiomeEntry::name)
            .collect(Collectors.toList());

        biomeList = new ScrollableListWidget(width / 4, 30, width / 2, height - 80, 16);
        biomeList.setItems(biomeNames);

        addRenderableWidget(Button.builder(Component.literal("Assign"), btn -> {
            int idx = biomeList.getSelectedIndex();
            if (idx >= 0 && idx < biomeNames.size()) {
                onSelect.accept(biomeNames.get(idx));
                minecraft.setScreen(parent);
            }
        }).bounds(width / 2 - 60, height - 35, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            minecraft.setScreen(parent);
        }).bounds(width / 2 + 10, height - 35, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        biomeList.render(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (biomeList.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dH, double dV) {
        if (biomeList.mouseScrolled(mouseX, mouseY, dV)) return true;
        return super.mouseScrolled(mouseX, mouseY, dH, dV);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (biomeList.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (biomeList.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
