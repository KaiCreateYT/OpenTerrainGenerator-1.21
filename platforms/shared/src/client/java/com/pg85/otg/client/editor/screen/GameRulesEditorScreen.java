package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.widget.GameRulesListWidget;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Editor for a single WorldPresetConfig.GameRules instance.
 * Mutates the passed-in rules object in place via GameRulesListWidget.
 * Calls back onSave when user clicks Save; does nothing on Cancel.
 */
public class GameRulesEditorScreen extends Screen {

    private final WorldPresetConfig.GameRules rules;
    private final Screen parent;
    private final Consumer<WorldPresetConfig.GameRules> onSave;

    private GameRulesListWidget listWidget;

    public GameRulesEditorScreen(WorldPresetConfig.GameRules rules,
                                   Screen parent,
                                   Consumer<WorldPresetConfig.GameRules> onSave,
                                   String title) {
        super(Component.literal(title));
        this.rules = rules;
        this.parent = parent;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int listX = 10;
        int listY = 30;
        int listW = width - 20;
        int listH = height - 70;

        listWidget = new GameRulesListWidget(listX, listY, listW, listH, rules, this::rebuildWidgets);

        addRenderableWidget(listWidget.getSearchEditBox());
        for (var eb : listWidget.collectActiveEditBoxes()) {
            addRenderableWidget(eb);
        }

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            onSave.accept(rules);
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 100, height - 30, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b ->
            minecraft.setScreen(parent)
        ).bounds(width / 2 + 10, height - 30, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        listWidget.render(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int result = listWidget.mouseClickedResult(mouseX, mouseY);
        if (result != 0) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (listWidget.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (listWidget.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (listWidget.mouseScrolled(mouseX, mouseY, dy)) return true;
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
