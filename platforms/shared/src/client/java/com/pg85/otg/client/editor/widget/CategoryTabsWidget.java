package com.pg85.otg.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.function.IntConsumer;

public class CategoryTabsWidget {

    private final int x, y, height;
    private final int maxWidth;
    private List<String> categories;
    private int selectedIndex = 0;
    private IntConsumer onSelect;

    public CategoryTabsWidget(int x, int y, int maxWidth, int height) {
        this.x = x;
        this.y = y;
        this.maxWidth = maxWidth;
        this.height = height;
    }

    public void setCategories(List<String> categories) { this.categories = categories; }
    public void setSelectedIndex(int index) { this.selectedIndex = index; }
    public void setOnSelect(IntConsumer onSelect) { this.onSelect = onSelect; }
    public int getSelectedIndex() { return selectedIndex; }

    public void render(GuiGraphics graphics) {
        if (categories == null) return;
        Font font = Minecraft.getInstance().font;
        int tabX = x;

        for (int i = 0; i < categories.size(); i++) {
            String label = categories.get(i);
            int tabW = font.width(label) + 16;

            if (i == selectedIndex) {
                graphics.fill(tabX, y, tabX + tabW, y + height, 0xFF2A3A2A);
                graphics.renderOutline(tabX, y, tabW, height, 0xFF4A7A4A);
                graphics.drawString(font, label, tabX + 8, y + (height - 8) / 2, 0xFF66CC66);
            } else {
                graphics.fill(tabX, y, tabX + tabW, y + height, 0xFF2A2A2A);
                graphics.renderOutline(tabX, y, tabW, height, 0xFF444444);
                graphics.drawString(font, label, tabX + 8, y + (height - 8) / 2, 0xFF888888);
            }

            tabX += tabW + 2;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (categories == null || mouseY < y || mouseY > y + height) return false;
        Font font = Minecraft.getInstance().font;
        int tabX = x;

        for (int i = 0; i < categories.size(); i++) {
            int tabW = font.width(categories.get(i)) + 16;
            if (mouseX >= tabX && mouseX < tabX + tabW) {
                selectedIndex = i;
                if (onSelect != null) onSelect.accept(i);
                return true;
            }
            tabX += tabW + 2;
        }
        return false;
    }

    public int getRenderedHeight() { return height; }
}
