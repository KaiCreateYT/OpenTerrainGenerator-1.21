package com.pg85.otg.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.function.IntConsumer;

public class ScrollableListWidget {

    private final int x, y, width, height;
    private final int itemHeight;
    private List<String> items;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private IntConsumer onSelect;
    private boolean draggingScrollbar = false;

    public ScrollableListWidget(int x, int y, int width, int height, int itemHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.itemHeight = itemHeight;
    }

    public void setItems(List<String> items) { this.items = items; this.scrollOffset = 0; }
    public void setSelectedIndex(int index) { this.selectedIndex = index; }
    public void setOnSelect(IntConsumer onSelect) { this.onSelect = onSelect; }
    public int getSelectedIndex() { return selectedIndex; }

    public void render(GuiGraphics graphics) {
        if (items == null || items.isEmpty()) return;
        Font font = Minecraft.getInstance().font;

        graphics.fill(x, y, x + width, y + height, 0xFF1E1E1E);

        int maxVisible = height / itemHeight;
        int maxScroll = Math.max(0, items.size() - maxVisible);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        graphics.enableScissor(x, y, x + width, y + height);
        for (int i = 0; i < maxVisible && (i + scrollOffset) < items.size(); i++) {
            int idx = i + scrollOffset;
            int iy = y + i * itemHeight;

            if (idx == selectedIndex) {
                graphics.fill(x, iy, x + width, iy + itemHeight, 0xFF2A3A4A);
                graphics.fill(x, iy, x + 3, iy + itemHeight, 0xFF4A8AFF);
            }

            int color = idx == selectedIndex ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.drawString(font, items.get(idx), x + 6, iy + (itemHeight - 8) / 2, color);
        }
        graphics.disableScissor();

        // Scrollbar
        renderScrollbar(graphics, items.size(), maxVisible);
    }

    private void renderScrollbar(GuiGraphics graphics, int totalItems, int maxVisible) {
        if (totalItems <= maxVisible) return;
        int sbX = x + width - 4;
        int sbW = 3;
        // Track
        graphics.fill(sbX, y, sbX + sbW, y + height, 0xFF111111);
        // Thumb
        float ratio = (float) maxVisible / totalItems;
        int thumbH = Math.max(8, (int) (height * ratio));
        int maxScroll = totalItems - maxVisible;
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = y + (int) ((height - thumbH) * scrollRatio);
        graphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF555555);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        // Scrollbar click — start drag
        if (mouseX >= x + width - 6 && items != null && items.size() > height / itemHeight) {
            draggingScrollbar = true;
            scrollToY(mouseY);
            return true;
        }
        int clicked = (int) ((mouseY - y) / itemHeight) + scrollOffset;
        if (clicked >= 0 && clicked < items.size()) {
            selectedIndex = clicked;
            if (onSelect != null) onSelect.accept(clicked);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingScrollbar) {
            scrollToY(mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    private void scrollToY(double mouseY) {
        if (items == null || items.isEmpty()) return;
        int maxVisible = height / itemHeight;
        int maxScroll = Math.max(0, items.size() - maxVisible);
        float ratio = (float) Math.clamp((mouseY - y) / height, 0, 1);
        scrollOffset = Math.round(ratio * maxScroll);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        int maxVisible = height / itemHeight;
        int maxScroll = Math.max(0, items.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
        return true;
    }
}
