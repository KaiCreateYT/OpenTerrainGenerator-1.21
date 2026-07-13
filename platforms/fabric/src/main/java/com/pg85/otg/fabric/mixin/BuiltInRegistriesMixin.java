package com.pg85.otg.fabric.mixin;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.fabric.biome.OTGFabricBiomeProvider;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltInRegistries.class)
public class BuiltInRegistriesMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void registerGeneratorAndBiomeSource(CallbackInfo ci) {
        Registry.register(BuiltInRegistries.BIOME_SOURCE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, Constants.MOD_ID_SHORT), OTGFabricBiomeProvider.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, Constants.MOD_ID_SHORT), OTGFabricChunkGenerator.CODEC);
    }
}
