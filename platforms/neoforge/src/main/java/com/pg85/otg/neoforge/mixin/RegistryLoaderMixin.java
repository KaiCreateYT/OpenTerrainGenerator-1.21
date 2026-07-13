package com.pg85.otg.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.pg85.otg.neoforge.biome.OTGNeoForgeBiomeProvider;
import com.pg85.otg.neoforge.gen.OTGNeoForgeChunkGenerator;
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
 * Injects into the private NeoForge 4-arg load() method, which in 1.21.5 has the signature:
 *   load(LoadingFunction, List<HolderLookup.RegistryLookup<?>>, List<RegistryDataLoader.RegistryData<?>>, boolean)
 *
 * NeoForge moved the actual implementation from the 3-arg method to this 4-arg variant
 * and made the 3-arg method a @Deprecated stub that delegates to this one. Both public
 * load() overloads now call this 4-arg method directly (bypassing the deprecated stub).
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
                    "Z" +
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
            boolean fromResources,
            CallbackInfoReturnable ci,
            @Local(ordinal = 2) List<RegistryDataLoader.Loader<?>> loaders
    ) {
        OTGRegistryHelper.loadOTGPresets(
                loaders,
                (presetFolder, noiseRef, biomeReg) ->
                        new OTGNeoForgeChunkGenerator(
                                new OTGNeoForgeBiomeProvider(presetFolder, 0L),
                                noiseRef,
                                biomeReg
                        ),
                mat -> ((SharedMaterialData) mat).getState()
        );
    }
}
