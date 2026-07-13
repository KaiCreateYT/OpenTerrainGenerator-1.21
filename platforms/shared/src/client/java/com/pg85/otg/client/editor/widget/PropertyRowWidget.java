package com.pg85.otg.client.editor.widget;

import com.pg85.otg.client.editor.data.PropertyType;
import com.pg85.otg.client.editor.data.PropertyValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class PropertyRowWidget {

    public static final int ROW_HEIGHT = 20;
    private static final int NAME_WIDTH = 180;
    private static final int FLAG_WIDTH = 50;

    private final PropertyValue property;
    private final boolean showOverride, showMerge, showOpv;
    private EditBox editBox;
    private Consumer<String> onValueChanged;
    private Consumer<PropertyRowWidget> onDropdownRequested;

    private int x, y, width;

    public PropertyRowWidget(PropertyValue property, boolean showOverride, boolean showMerge, boolean showOpv) {
        this.property = property;
        this.showOverride = showOverride;
        this.showMerge = showMerge;
        this.showOpv = showOpv;
    }

    public void init(Font font, int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;

        PropertyType type = property.getDefinition().type();
        if (type != PropertyType.BOOLEAN && type != PropertyType.ENUM) {
            int editX = x + NAME_WIDTH + 4;
            int editW = calcEditWidth();
            editBox = new EditBox(font, editX, y + 1, editW, ROW_HEIGHT - 2, Component.empty());
            editBox.setValue(property.getValue());
            editBox.setResponder(val -> {
                if (onValueChanged != null) onValueChanged.accept(val);
            });
        }
    }

    public void setOnValueChanged(Consumer<String> callback) { this.onValueChanged = callback; }
    public void setOnDropdownRequested(Consumer<PropertyRowWidget> callback) { this.onDropdownRequested = callback; }
    public EditBox getEditBox() { return editBox; }
    public PropertyValue getProperty() { return property; }
    public int getEnumBoxX() { return x + NAME_WIDTH + 4; }
    public int getEnumBoxY() { return y + ROW_HEIGHT; }
    public int getEnumBoxWidth() { return calcEditWidth(); }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;

        // Background
        graphics.fill(x, y, x + width, y + ROW_HEIGHT, 0xFF1A1A1A);
        graphics.fill(x, y + ROW_HEIGHT - 1, x + width, y + ROW_HEIGHT, 0xFF2A2A2A);

        // Property name
        String name = property.getDefinition().name();
        graphics.drawString(font, name, x + 4, y + (ROW_HEIGHT - 8) / 2, 0xFFAAAAAA);

        PropertyType type = property.getDefinition().type();

        // Type-specific rendering
        if (type == PropertyType.BOOLEAN) {
            renderBoolean(graphics, font, mouseX, mouseY);
        } else if (type == PropertyType.ENUM) {
            renderEnum(graphics, font, mouseX, mouseY);
        }
        // EditBox types are rendered by MC's widget system

        // Flags
        int flagX = x + width;
        if (showOpv) {
            flagX -= FLAG_WIDTH;
            renderFlag(graphics, font, flagX, "OPV", property.isOpv());
        }
        if (showMerge) {
            flagX -= FLAG_WIDTH;
            renderFlag(graphics, font, flagX, "Merge", property.isMerge());
        }
        if (showOverride) {
            flagX -= FLAG_WIDTH;
            renderFlag(graphics, font, flagX, "Override", property.isOverride());
        }
    }

    private void renderBoolean(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int bx = x + NAME_WIDTH + 4;
        boolean val = "true".equalsIgnoreCase(property.getValue());
        int boxColor = val ? 0xFF2A3A2A : 0xFF2A2A2A;
        int borderColor = val ? 0xFF4A7A4A : 0xFF555555;
        graphics.fill(bx, y + 2, bx + 16, y + ROW_HEIGHT - 2, boxColor);
        graphics.renderOutline(bx, y + 2, 16, ROW_HEIGHT - 4, borderColor);
        if (val) {
            graphics.drawString(font, "\u2713", bx + 4, y + (ROW_HEIGHT - 8) / 2, 0xFF66CC66);
        }
        String label = val ? "true" : "false";
        graphics.drawString(font, label, bx + 20, y + (ROW_HEIGHT - 8) / 2, val ? 0xFF66CC66 : 0xFF888888);
    }

    private void renderEnum(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int bx = x + NAME_WIDTH + 4;
        int bw = calcEditWidth();
        graphics.fill(bx, y + 2, bx + bw, y + ROW_HEIGHT - 2, 0xFF2A2A2A);
        graphics.renderOutline(bx, y + 2, bw, ROW_HEIGHT - 4, 0xFF444444);
        String display = property.getValue() + " \u25BC";
        graphics.drawString(font, display, bx + 4, y + (ROW_HEIGHT - 8) / 2, 0xFFFFFFFF);
    }

    private void renderFlag(GuiGraphics graphics, Font font, int fx, String label, boolean active) {
        int bgColor = active ? 0xFF2A3A2A : 0xFF2A2A2A;
        int borderColor = active ? 0xFF4A7A4A : 0xFF555555;
        int textColor = active ? 0xFF66CC66 : 0xFF888888;
        String text = active ? "\u2713 " + label : label;

        graphics.fill(fx + 2, y + 2, fx + FLAG_WIDTH - 2, y + ROW_HEIGHT - 2, bgColor);
        graphics.renderOutline(fx + 2, y + 2, FLAG_WIDTH - 4, ROW_HEIGHT - 4, borderColor);
        graphics.drawString(font, text, fx + 4, y + (ROW_HEIGHT - 8) / 2, textColor, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (mouseY < y || mouseY > y + ROW_HEIGHT) return false;

        PropertyType type = property.getDefinition().type();

        // Boolean toggle
        if (type == PropertyType.BOOLEAN) {
            int bx = x + NAME_WIDTH + 4;
            if (mouseX >= bx && mouseX < bx + 60) {
                boolean current = "true".equalsIgnoreCase(property.getValue());
                property.setValue(String.valueOf(!current));
                if (onValueChanged != null) onValueChanged.accept(property.getValue());
                return true;
            }
        }

        // Enum dropdown
        if (type == PropertyType.ENUM) {
            int bx = x + NAME_WIDTH + 4;
            int bw = calcEditWidth();
            if (mouseX >= bx && mouseX < bx + bw) {
                if (onDropdownRequested != null) {
                    onDropdownRequested.accept(this);
                }
                return true;
            }
        }

        // Flag toggles
        int flagX = x + width;
        if (showOpv) {
            flagX -= FLAG_WIDTH;
            if (mouseX >= flagX && mouseX < flagX + FLAG_WIDTH) {
                property.setOpv(!property.isOpv());
                return true;
            }
        }
        if (showMerge) {
            flagX -= FLAG_WIDTH;
            if (mouseX >= flagX && mouseX < flagX + FLAG_WIDTH) {
                property.setMerge(!property.isMerge());
                return true;
            }
        }
        if (showOverride) {
            flagX -= FLAG_WIDTH;
            if (mouseX >= flagX && mouseX < flagX + FLAG_WIDTH) {
                property.setOverride(!property.isOverride());
                return true;
            }
        }

        return false;
    }

    private int calcEditWidth() {
        int flagsWidth = (showOverride ? FLAG_WIDTH : 0) + (showMerge ? FLAG_WIDTH : 0) + (showOpv ? FLAG_WIDTH : 0);
        return width - NAME_WIDTH - flagsWidth - 8;
    }
}
