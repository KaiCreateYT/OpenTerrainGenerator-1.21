package com.pg85.otg.client.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class SearchBoxWidget {

    private final EditBox editBox;

    public SearchBoxWidget(Font font, int x, int y, int width, int height, String placeholder) {
        editBox = new EditBox(font, x, y, width, height, Component.literal(placeholder));
        editBox.setHint(Component.literal(placeholder));
    }

    public void setOnTextChanged(Consumer<String> callback) {
        editBox.setResponder(callback);
    }

    public EditBox getEditBox() { return editBox; }
    public String getText() { return editBox.getValue(); }
}
