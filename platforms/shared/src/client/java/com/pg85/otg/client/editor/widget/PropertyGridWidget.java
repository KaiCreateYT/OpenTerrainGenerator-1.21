package com.pg85.otg.client.editor.widget;

import com.pg85.otg.client.editor.data.PropertyCategory;
import com.pg85.otg.client.editor.data.PropertyValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PropertyGridWidget {

    private final int x, y, width, height;
    private final boolean showOverride, showMerge, showOpv;

    private List<PropertyValue> allProperties = List.of();
    private CategoryTabsWidget tabs;
    private SearchBoxWidget searchBox;

    private List<PropertyCategory> categories = List.of();
    private List<PropertyRowWidget> visibleRows = List.of();
    private int scrollOffset = 0;
    private String searchFilter = "";
    private Consumer<PropertyValue> onValueChanged;

    private List<EditBox> activeEditBoxes = new ArrayList<>();

    public PropertyGridWidget(int x, int y, int width, int height,
                               boolean showOverride, boolean showMerge, boolean showOpv) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.showOverride = showOverride;
        this.showMerge = showMerge;
        this.showOpv = showOpv;
    }

    public void setOnValueChanged(Consumer<PropertyValue> callback) { this.onValueChanged = callback; }
    public List<EditBox> getActiveEditBoxes() { return activeEditBoxes; }

    public void init(Font font, List<PropertyValue> properties) {
        this.allProperties = properties;

        categories = properties.stream()
            .map(p -> p.getDefinition().category())
            .distinct()
            .collect(Collectors.toList());

        tabs = new CategoryTabsWidget(x, y, width, 18);
        tabs.setCategories(categories.stream().map(PropertyCategory::getDisplayName).collect(Collectors.toList()));
        tabs.setOnSelect(idx -> { scrollOffset = 0; rebuildRows(font); });

        int searchY = y + 22;
        searchBox = new SearchBoxWidget(font, x + 4, searchY, Math.min(200, width - 8), 16, "Search properties...");
        searchBox.setOnTextChanged(text -> { searchFilter = text; scrollOffset = 0; rebuildRows(font); });

        rebuildRows(font);
    }

    private void rebuildRows(Font font) {
        activeEditBoxes.clear();

        PropertyCategory selectedCat = categories.isEmpty() ? null : categories.get(tabs.getSelectedIndex());
        List<PropertyValue> filtered = allProperties.stream()
            .filter(p -> p.getDefinition().category() == selectedCat)
            .filter(p -> searchFilter.isEmpty() || p.getDefinition().name().toLowerCase().contains(searchFilter.toLowerCase()))
            .collect(Collectors.toList());

        int rowY = y + 44;
        int availableHeight = height - 44 - 30;
        int maxRows = availableHeight / PropertyRowWidget.ROW_HEIGHT;

        visibleRows = new ArrayList<>();
        for (int i = scrollOffset; i < filtered.size() && visibleRows.size() < maxRows; i++) {
            PropertyValue pv = filtered.get(i);
            PropertyRowWidget row = new PropertyRowWidget(pv, showOverride, showMerge, showOpv);
            int ry = rowY + visibleRows.size() * PropertyRowWidget.ROW_HEIGHT;
            row.init(font, x, ry, width);
            row.setOnValueChanged(val -> {
                pv.setValue(val);
                if (onValueChanged != null) onValueChanged.accept(pv);
            });
            if (row.getEditBox() != null) {
                activeEditBoxes.add(row.getEditBox());
            }
            visibleRows.add(row);
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0xFF222222);
        tabs.render(graphics);

        graphics.enableScissor(x, y + 44, x + width, y + height - 30);
        for (PropertyRowWidget row : visibleRows) {
            row.render(graphics, mouseX, mouseY);
        }
        graphics.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (tabs.mouseClicked(mouseX, mouseY)) return true;
        for (PropertyRowWidget row : visibleRows) {
            if (row.mouseClicked(mouseX, mouseY)) return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        Font font = Minecraft.getInstance().font;
        int maxScroll = Math.max(0, getFilteredCount() - getMaxVisibleRows());
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
        rebuildRows(font);
        return true;
    }

    private int getFilteredCount() {
        PropertyCategory selectedCat = categories.isEmpty() ? null : categories.get(tabs.getSelectedIndex());
        return (int) allProperties.stream()
            .filter(p -> p.getDefinition().category() == selectedCat)
            .filter(p -> searchFilter.isEmpty() || p.getDefinition().name().toLowerCase().contains(searchFilter.toLowerCase()))
            .count();
    }

    private int getMaxVisibleRows() {
        int availableHeight = height - 44 - 30;
        return availableHeight / PropertyRowWidget.ROW_HEIGHT;
    }

    public EditBox getSearchEditBox() { return searchBox.getEditBox(); }
}
