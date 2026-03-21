package com.pg85.otg.client.mixin;

import com.pg85.otg.client.editor.screen.EditorHubScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void otg$addEditorButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(
            Component.literal("OTG Editor"),
            btn -> minecraft.setScreen(new EditorHubScreen())
        ).bounds(width / 2 - 50, height / 4 + 96, 100, 20).build());
    }
}
