package com.pg85.otg.client.mixin;

import com.pg85.otg.client.preview.PreviewState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ClientTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void otg$tickPreviewState(CallbackInfo ci) {
        PreviewState.tick();
    }
}
