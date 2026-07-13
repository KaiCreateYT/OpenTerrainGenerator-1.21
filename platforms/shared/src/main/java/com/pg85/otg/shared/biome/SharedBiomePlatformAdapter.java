package com.pg85.otg.shared.biome;

import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.interfaces.IBiome;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public class SharedBiomePlatformAdapter implements BiomePlatformAdapter {
    @Override
    public IBiome createPlatformBiome(BiomeSettings settings, Biome biome, Holder.Reference<Biome> ref) {
        return new SharedBiome(settings, biome, ref);
    }

    @Override
    public boolean biomeHasTag(Registry<Biome> registry, ResourceKey<Biome> key, String tag) {
        return BiomeTagMapper.biomeHasTag(registry, key, tag);
    }
}
