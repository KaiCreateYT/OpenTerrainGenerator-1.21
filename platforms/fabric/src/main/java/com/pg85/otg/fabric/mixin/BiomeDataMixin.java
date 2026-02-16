package com.pg85.otg.fabric.mixin;

import com.pg85.otg.shared.biome.SharedPresetBiomeLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.biome.BiomeData;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeData.class)
public class BiomeDataMixin {

    // Inject code at the end of the bootstrap method
    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void storeHolderGetters(BootstrapContext<Biome> arg, CallbackInfo ci) {
        SharedPresetBiomeLoader.PLACED_FEATURE_HOLDER = arg.lookup(Registries.PLACED_FEATURE);
        SharedPresetBiomeLoader.CONFIGURED_CARVER_HOLDER = arg.lookup(Registries.CONFIGURED_CARVER);
        SharedPresetBiomeLoader.BIOME_DATA_INITIALIZED = true;
    }
}
