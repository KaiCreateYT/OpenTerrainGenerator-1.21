package com.pg85.otg.client.editor.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Base class for scrollable widgets. Provides shared scrollbar rendering,
 * scroll clamping, scroll-by-drag, and mouse wheel handling.
 */
public abstract class ScrollablePanel {

    protected final int x, y, width, height;
    protected final int itemHeight;
    protected int scrollOffset = 0;
    protected boolean draggingScrollbar = false;

    protected ScrollablePanel(int x, int y, int width, int height, int itemHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.itemHeight = itemHeight;
    }

    /** Total number of items (visible + hidden). */
    protected abstract int getTotalItemCount();

    protected int getMaxVisible() {
        return height / itemHeight;
    }

    protected int getMaxScroll() {
        return Math.max(0, getTotalItemCount() - getMaxVisible());
    }

    protected void clampScroll() {
        scrollOffset = Math.min(scrollOffset, getMaxScroll());
    }

    /**
     * Renders the scrollbar track and thumb.
     */
    protected void renderScrollbar(GuiGraphics graphics) {
        int totalItems = getTotalItemCount();
        int maxVisible = getMaxVisible();
        if (totalItems <= maxVisible) return;

        int sbX = x + width - 4;
        int sbW = 3;
        // Track
        graphics.fill(sbX, y, sbX + sbW, y + height, 0xFF111111);
        // Thumb
        float ratio = (float) maxVisible / totalItems;
        int thumbH = Math.max(8, (int) (height * ratio));
        int maxScroll = getMaxScroll();
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = y + (int) ((height - thumbH) * scrollRatio);
        graphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF555555);
    }

    /**
     * Scrolls to the position indicated by the mouse Y coordinate.
     */
    protected void scrollToY(double mouseY) {
        int maxScroll = getMaxScroll();
        float ratio = (float) Math.clamp((mouseY - y) / height, 0, 1);
        scrollOffset = Math.round(ratio * maxScroll);
    }

    /**
     * Handles mouse wheel scroll. Returns true if the event was consumed.
     */
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        int maxScroll = getMaxScroll();
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
        return true;
    }

    /**
     * Checks if a click is on the scrollbar area and starts dragging.
     * Returns true if the scrollbar captured the click.
     */
    protected boolean handleScrollbarClick(double mouseX, double mouseY) {
        if (mouseX >= x + width - 6 && getTotalItemCount() > getMaxVisible()) {
            draggingScrollbar = true;
            scrollToY(mouseY);
            return true;
        }
        return false;
    }

    /**
     * Handles mouse drag for scrollbar. Returns true if dragging.
     */
    protected boolean handleScrollbarDrag(double mouseX, double mouseY) {
        if (draggingScrollbar) {
            scrollToY(mouseY);
            return true;
        }
        return false;
    }

    /**
     * Handles mouse release for scrollbar. Returns true if was dragging.
     */
    protected boolean handleScrollbarRelease() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    protected boolean isInBounds(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
