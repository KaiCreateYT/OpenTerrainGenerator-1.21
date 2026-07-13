package com.pg85.otg.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Floating dropdown overlay for enum selection.
 * Managed by PropertyGridWidget — only one can be open at a time.
 */
public class DropdownWidget {

    private static final int ITEM_HEIGHT = 16;
    private static final int MAX_VISIBLE = 12;
    private static final int PADDING = 2;

    private final int x, y, width;
    private final List<String> values;
    private final String currentValue;
    private int scrollOffset = 0;
    private int hoveredIndex = -1;

    public DropdownWidget(int x, int y, int width, List<String> values, String currentValue) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.values = values;
        this.currentValue = currentValue;
    }

    public int getHeight() {
        return Math.min(values.size(), MAX_VISIBLE) * ITEM_HEIGHT + PADDING * 2;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int visibleCount = Math.min(values.size(), MAX_VISIBLE);
        int totalH = visibleCount * ITEM_HEIGHT + PADDING * 2;

        // Push z-level forward so dropdown renders on top of everything (like MC tooltips)
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        // Shadow
        graphics.fill(x + 2, y + 2, x + width + 2, y + totalH + 2, 0x88000000);
        // Background
        graphics.fill(x, y, x + width, y + totalH, 0xFF1A1A1A);
        // Border
        graphics.renderOutline(x, y, width, totalH, 0xFF4A7A4A);

        hoveredIndex = -1;

        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= values.size()) break;

            int iy = y + PADDING + i * ITEM_HEIGHT;
            String val = values.get(idx);
            boolean isSelected = val.equals(currentValue);
            boolean isHovered = mouseX >= x && mouseX < x + width
                && mouseY >= iy && mouseY < iy + ITEM_HEIGHT;

            if (isHovered) {
                hoveredIndex = idx;
                graphics.fill(x + 1, iy, x + width - 1, iy + ITEM_HEIGHT, 0xFF2A3A4A);
            } else if (isSelected) {
                graphics.fill(x + 1, iy, x + width - 1, iy + ITEM_HEIGHT, 0xFF2A3A2A);
            }

            int color = isSelected ? 0xFF66CC66 : (isHovered ? 0xFFFFFFFF : 0xFFAAAAAA);
            graphics.drawString(font, val, x + 4, iy + (ITEM_HEIGHT - 8) / 2, color);

            if (isSelected) {
                graphics.drawString(font, "\u2713", x + width - 14, iy + (ITEM_HEIGHT - 8) / 2, 0xFF66CC66);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(font, "\u25B2", x + width / 2, y + 1, 0xFF888888);
        }
        if (scrollOffset + visibleCount < values.size()) {
            graphics.drawCenteredString(font, "\u25BC", x + width / 2, y + totalH - 9, 0xFF888888);
        }

        graphics.pose().popPose();
    }

    /**
     * @return selected value, or null if click was outside/not on an item
     */
    public String mouseClicked(double mouseX, double mouseY) {
        int visibleCount = Math.min(values.size(), MAX_VISIBLE);
        int totalH = visibleCount * ITEM_HEIGHT + PADDING * 2;

        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + totalH) {
            return null; // outside — caller should close dropdown
        }

        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= values.size()) break;
            int iy = y + PADDING + i * ITEM_HEIGHT;
            if (mouseY >= iy && mouseY < iy + ITEM_HEIGHT) {
                return values.get(idx);
            }
        }
        return null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visibleCount = Math.min(values.size(), MAX_VISIBLE);
        int totalH = visibleCount * ITEM_HEIGHT + PADDING * 2;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + totalH) return false;

        int maxScroll = Math.max(0, values.size() - MAX_VISIBLE);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
        return true;
    }

    public boolean isInside(double mouseX, double mouseY) {
        int visibleCount = Math.min(values.size(), MAX_VISIBLE);
        int totalH = visibleCount * ITEM_HEIGHT + PADDING * 2;
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + totalH;
    }
}
