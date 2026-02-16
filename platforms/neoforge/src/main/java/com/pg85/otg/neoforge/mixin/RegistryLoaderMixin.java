package com.pg85.otg.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.pg85.otg.neoforge.biome.OTGNeoForgeBiomeProvider;
import com.pg85.otg.neoforge.gen.OTGNeoForgeChunkGenerator;
import com.pg85.otg.neoforge.materials.NeoForgeMaterialData;
import com.pg85.otg.shared.registry.OTGRegistryHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RegistryDataLoader.class)
@SuppressWarnings("unused")
public class RegistryLoaderMixin {
    @Inject(
            method = "load(" +
                    "Lnet/minecraft/resources/RegistryDataLoader$LoadingFunction;" +
                    "Lnet/minecraft/core/RegistryAccess;" +
                    "Ljava/util/List;" +
                    ")Lnet/minecraft/core/RegistryAccess$Frozen;",
            at = @At(
                value = "INVOKE",
                target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
                ordinal = 1
            )
    )
    @SuppressWarnings("rawtypes")
    private static void loadOTGPresets(
            RegistryDataLoader.LoadingFunction loadingFunction,
            RegistryAccess registryAccess,
            List<RegistryDataLoader.RegistryData<?>> list,
            CallbackInfoReturnable ci,
            @Local(ordinal = 1) List<RegistryDataLoader.Loader<?>> loaders
    ) {
        OTGRegistryHelper.loadOTGPresets(
                loaders,
                registryAccess,
                (presetFolder, noiseRef, biomeReg) ->
                        new OTGNeoForgeChunkGenerator(
                                new OTGNeoForgeBiomeProvider(presetFolder, 0L),
                                noiseRef,
                                biomeReg
                        ),
                mat -> ((NeoForgeMaterialData) mat).getState()
        );
    }
}
