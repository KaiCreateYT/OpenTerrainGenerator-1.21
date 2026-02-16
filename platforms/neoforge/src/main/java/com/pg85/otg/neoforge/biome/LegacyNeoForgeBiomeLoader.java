package com.pg85.otg.neoforge.biome;

import java.nio.file.Path;

import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.shared.biome.SharedBiome;
import com.pg85.otg.shared.biome.SharedLegacyBiomeLoader;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;


public class LegacyNeoForgeBiomeLoader extends SharedLegacyBiomeLoader {

    public LegacyNeoForgeBiomeLoader(Path otgRootFolder) {
        super(otgRootFolder);
    }

    @Override
    protected IBiome createPlatformBiome(BiomeSettings settings, Biome biome, Holder.Reference<Biome> ref) {
        return new SharedBiome(settings, biome, ref);
    }

    @Override
    protected boolean biomeHasTag(Registry<Biome> registry, ResourceKey<Biome> key, String tag) {
        return NeoForgeBiomeTagMapper.biomeHasTag(registry, key, tag);
    }
}
