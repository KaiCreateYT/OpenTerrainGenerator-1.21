package com.pg85.otg.platform.noise;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class ResourceKeyUtil {
    public static ResourceKey<DensityFunction> df(ResourceLocation id) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DENSITY_FUNCTION, id);
    }

    public static ResourceKey<NormalNoise.NoiseParameters> noise(ResourceLocation id) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.NOISE, id);
    }
}
