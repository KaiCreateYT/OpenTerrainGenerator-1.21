package com.pg85.otg.neoforge.mixin;

import com.pg85.otg.neoforge.biome.NeoForgeBiomeLoader;
import com.pg85.otg.neoforge.biome.LegacyNeoForgeBiomeLoader;
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
        LegacyNeoForgeBiomeLoader.PLACED_FEATURE_HOLDER = arg.lookup(Registries.PLACED_FEATURE);
        LegacyNeoForgeBiomeLoader.CONFIGURED_CARVER_HOLDER = arg.lookup(Registries.CONFIGURED_CARVER);
        LegacyNeoForgeBiomeLoader.BIOME_DATA_INITIALIZED = true;

        NeoForgeBiomeLoader.PLACED_FEATURE_HOLDER = arg.lookup(Registries.PLACED_FEATURE);
        NeoForgeBiomeLoader.CONFIGURED_CARVER_HOLDER = arg.lookup(Registries.CONFIGURED_CARVER);
        NeoForgeBiomeLoader.BIOME_DATA_INITIALIZED = true;
    }
}
