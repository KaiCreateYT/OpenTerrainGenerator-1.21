package com.pg85.otg.client.editor.widget;

import com.pg85.otg.client.editor.data.PropertyType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Tri-state editor for a single GameRule field.
 * Booleans: 3 radios (default / true / false).
 * Integers: 2 radios (default / custom) + numeric input when custom selected.
 *
 * "default" = null value in YAML (don't override vanilla).
 */
public class GameRuleTriStateWidget {

    public static final int ROW_HEIGHT = 18;

    private static final int NAME_W = 170;
    private static final int RADIO_DEFAULT_W = 60;
    private static final int RADIO_BOOL_W = 50;

    private final String ruleName;
    private final PropertyType type;
    private final Consumer<Object> onChange;

    private int x, y, width;
    private @Nullable Object currentValue;

    private @Nullable EditBox intEditBox;

    public GameRuleTriStateWidget(String ruleName, PropertyType type, @Nullable Object currentValue,
                                   Consumer<Object> onChange) {
        this.ruleName = ruleName;
        this.type = type;
        this.currentValue = currentValue;
        this.onChange = onChange;
    }

    /** Positions the widget and, for INT type, creates the EditBox for numeric input. */
    public void init(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;

        if (type == PropertyType.INT) {
            var font = Minecraft.getInstance().font;
            int ebX = x + NAME_W + RADIO_DEFAULT_W + RADIO_BOOL_W + 10;
            intEditBox = new EditBox(font, ebX, y, 60, 14, Component.empty());
            intEditBox.setValue(currentValue instanceof Integer i ? i.toString() : "");
            intEditBox.setEditable(currentValue instanceof Integer);
            intEditBox.setResponder(val -> {
                if (!(currentValue instanceof Integer)) return;
                try {
                    int parsed = Integer.parseInt(val);
                    currentValue = parsed;
                    onChange.accept(parsed);
                } catch (NumberFormatException ignored) {
                    // keep last valid value, don't push to callback
                }
            });
        } else {
            intEditBox = null;
        }
    }

    public @Nullable EditBox getEditBox() {
        return intEditBox;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, ruleName, x, y + 5, 0xFFCCCCCC);

        int rx = x + NAME_W;
        renderRadio(g, rx, y, currentValue == null, "default");

        if (type == PropertyType.BOOLEAN) {
            renderRadio(g, rx + RADIO_DEFAULT_W,           y, Boolean.TRUE.equals(currentValue),  "true");
            renderRadio(g, rx + RADIO_DEFAULT_W + RADIO_BOOL_W, y, Boolean.FALSE.equals(currentValue), "false");
        } else {
            renderRadio(g, rx + RADIO_DEFAULT_W, y, currentValue instanceof Integer, "custom");
            // EditBox rendered by MC widget system (registered by caller)
        }
    }

    private void renderRadio(GuiGraphics g, int rx, int ry, boolean selected, String label) {
        var font = Minecraft.getInstance().font;
        int cx = rx + 5;
        int cy = ry + 7;
        int r = 4;
        // Outer
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF333333);
        g.renderOutline(cx - r, cy - r, 2 * r, 2 * r, 0xFF666666);
        if (selected) {
            g.fill(cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2, 0xFF66CC66);
        }
        g.drawString(font, label, rx + 12, ry + 5, selected ? 0xFF66CC66 : 0xFFAAAAAA);
    }

    public boolean mouseClicked(double mx, double my) {
        if (my < y || my > y + ROW_HEIGHT) return false;
        int rx = x + NAME_W;

        if (clickInRadio(mx, rx)) { setValue(null); return true; }

        if (type == PropertyType.BOOLEAN) {
            if (clickInRadio(mx, rx + RADIO_DEFAULT_W)) { setValue(Boolean.TRUE); return true; }
            if (clickInRadio(mx, rx + RADIO_DEFAULT_W + RADIO_BOOL_W)) { setValue(Boolean.FALSE); return true; }
        } else {
            if (clickInRadio(mx, rx + RADIO_DEFAULT_W)) {
                if (!(currentValue instanceof Integer)) setValue(0);
                return true;
            }
        }
        return false;
    }

    private boolean clickInRadio(double mx, int rx) {
        return mx >= rx && mx <= rx + 50;
    }

    private void setValue(@Nullable Object newValue) {
        currentValue = newValue;
        onChange.accept(newValue);
        if (intEditBox != null) {
            intEditBox.setEditable(newValue instanceof Integer);
            if (newValue instanceof Integer i) {
                intEditBox.setValue(i.toString());
            } else {
                intEditBox.setValue("");
            }
        }
    }
}
