package com.pg85.otg.client.editor.widget;

import com.pg85.otg.client.editor.data.PropertyType;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reusable scrollable list of GameRule tri-state rows with search.
 * Mutates the bound WorldPresetConfig.GameRules instance in place via reflection.
 */
public class GameRulesListWidget extends ScrollablePanel {

    private static final Logger LOG = LoggerFactory.getLogger(GameRulesListWidget.class);
    private static final int SEARCH_HEIGHT = 16;
    private static final int SEARCH_MARGIN = 6;

    private final WorldPresetConfig.GameRules rules;
    private final SearchBoxWidget search;
    private final List<Field> allFields;
    private List<Field> visibleFields;
    private final List<GameRuleTriStateWidget> rowWidgets = new ArrayList<>();
    private String filter = "";

    public GameRulesListWidget(int x, int y, int width, int height, WorldPresetConfig.GameRules rules) {
        super(x, y + SEARCH_HEIGHT + SEARCH_MARGIN, width, height - SEARCH_HEIGHT - SEARCH_MARGIN,
            GameRuleTriStateWidget.ROW_HEIGHT);
        this.rules = rules;
        this.allFields = enumerateRuleFields();
        this.visibleFields = new ArrayList<>(allFields);

        var font = Minecraft.getInstance().font;
        this.search = new SearchBoxWidget(font, x, y, width - 20, SEARCH_HEIGHT, "Search rules...");
        this.search.setOnTextChanged(text -> {
            filter = text == null ? "" : text.toLowerCase(Locale.ROOT);
            applyFilter();
        });

        rebuildRowWidgets();
    }

    @Override
    protected int getTotalItemCount() {
        return visibleFields.size();
    }

    private void applyFilter() {
        visibleFields = new ArrayList<>();
        for (Field f : allFields) {
            if (filter.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(filter)) {
                visibleFields.add(f);
            }
        }
        clampScroll();
        rebuildRowWidgets();
    }

    private void rebuildRowWidgets() {
        rowWidgets.clear();
        for (Field f : visibleFields) {
            PropertyType type = f.getType() == Boolean.class ? PropertyType.BOOLEAN : PropertyType.INT;
            Object value;
            try {
                value = f.get(rules);
            } catch (IllegalAccessException e) {
                value = null;
            }
            Field captured = f;
            rowWidgets.add(new GameRuleTriStateWidget(
                f.getName(), type, value,
                newValue -> setFieldValue(captured, newValue)
            ));
        }
    }

    private void setFieldValue(Field field, Object value) {
        try {
            field.set(rules, value);
        } catch (IllegalAccessException e) {
            LOG.error("Failed to set rule {}: {}", field.getName(), e.getMessage());
        }
    }

    public EditBox getSearchEditBox() {
        return search.getEditBox();
    }

    /** EditBoxes for numeric GameRule inputs currently on-screen. Call after every scroll/filter change. */
    public List<EditBox> collectActiveEditBoxes() {
        List<EditBox> boxes = new ArrayList<>();
        int visibleRows = getMaxVisible();
        int start = scrollOffset;
        int end = Math.min(start + visibleRows, rowWidgets.size());
        for (int i = start; i < end; i++) {
            GameRuleTriStateWidget w = rowWidgets.get(i);
            w.init(x, y + (i - start) * itemHeight, width - 20);
            EditBox eb = w.getEditBox();
            if (eb != null) boxes.add(eb);
        }
        return boxes;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(x, y, x + width, y + height, 0xFF1A1A1A);
        g.enableScissor(x, y, x + width, y + height);

        int visibleRows = getMaxVisible();
        int start = scrollOffset;
        int end = Math.min(start + visibleRows, rowWidgets.size());
        for (int i = start; i < end; i++) {
            GameRuleTriStateWidget w = rowWidgets.get(i);
            w.init(x, y + (i - start) * itemHeight, width - 20);
            w.render(g, mouseX, mouseY);
        }

        g.disableScissor();
        renderScrollbar(g);
    }

    public boolean mouseClicked(double mx, double my) {
        if (handleScrollbarClick(mx, my)) return true;
        int visibleRows = getMaxVisible();
        int start = scrollOffset;
        int end = Math.min(start + visibleRows, rowWidgets.size());
        for (int i = start; i < end; i++) {
            if (rowWidgets.get(i).mouseClicked(mx, my)) return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        return handleScrollbarDrag(mx, my);
    }

    public boolean mouseReleased() {
        return handleScrollbarRelease();
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        return handleMouseScrolled(mx, my, delta);
    }

    private static List<Field> enumerateRuleFields() {
        List<Field> fields = new ArrayList<>();
        for (Field f : WorldPresetConfig.GameRules.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != Boolean.class && f.getType() != Integer.class) continue;
            f.setAccessible(true);
            fields.add(f);
        }
        fields.sort(Comparator.comparing(Field::getName));
        return fields;
    }
}
