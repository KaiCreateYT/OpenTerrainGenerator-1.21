package com.pg85.otg.fabric.mixin;

import com.pg85.otg.fabric.events.WorldSaveCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("unused")
@Mixin(ServerLevel.class)
public class WorldSaveMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void onWorldSave(ProgressListener progressListener, boolean saveAll, boolean noSave, CallbackInfo ci) {
        if (!noSave) {
            // We cast via object to avoid compiler/IDE error
            // Since this is run in a ServerLevel object, that is what "this" actually refers to
            WorldSaveCallback.EVENT.invoker().onWorldSave(((ServerLevel) (Object) this));
        }
    }
}
