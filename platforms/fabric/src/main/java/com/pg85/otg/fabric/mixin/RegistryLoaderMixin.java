package com.pg85.otg.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.pg85.otg.fabric.biome.OTGFabricBiomeProvider;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.shared.materials.SharedMaterialData;
import com.pg85.otg.shared.registry.OTGRegistryHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Injects into the private vanilla 3-arg load() method, which in 1.21.5 has the signature:
 *   load(LoadingFunction, List<HolderLookup.RegistryLookup<?>>, List<RegistryDataLoader.RegistryData<?>>)
 * (In 1.21.4 the 2nd param was RegistryAccess; that changed in 1.21.5.)
 *
 * Local variable ordinals at the injection point (List type):
 *   ordinal 0 → registryLookups  (parameter)
 *   ordinal 1 → registryData     (parameter)
 *   ordinal 2 → loaders          (local, the List<Loader<?>> we want)
 */
@Mixin(RegistryDataLoader.class)
@SuppressWarnings("unused")
public class RegistryLoaderMixin {
    @Inject(
            method = "load(" +
                    "Lnet/minecraft/resources/RegistryDataLoader$LoadingFunction;" +
                    "Ljava/util/List;" +
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
            List<HolderLookup.RegistryLookup<?>> registryLookups,
            List<RegistryDataLoader.RegistryData<?>> registryData,
            CallbackInfoReturnable ci,
            @Local(ordinal = 2) List<RegistryDataLoader.Loader<?>> loaders
    ) {
        OTGRegistryHelper.loadOTGPresets(
                loaders,
                (presetFolder, noiseRef, biomeReg) ->
                        new OTGFabricChunkGenerator(
                                new OTGFabricBiomeProvider(presetFolder, 0L),
                                noiseRef,
                                biomeReg
                        ),
                mat -> ((SharedMaterialData) mat).getState()
        );
    }
}
