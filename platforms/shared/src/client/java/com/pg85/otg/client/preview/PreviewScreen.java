package com.pg85.otg.client.preview;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class PreviewScreen extends Screen {

    public PreviewScreen() {
        super(Component.literal("OTG Editor"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
            Component.literal("Back"),
            btn -> minecraft.setScreen(null)
        ).bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
