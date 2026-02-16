package com.pg85.otg.shared.biome;

import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.interfaces.IBiome;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Platform-specific adapter for biome operations.
 * Replaces the template method pattern (abstract methods on SharedLegacyBiomeLoader)
 * with composition — each platform provides its own implementation.
 */
public interface BiomePlatformAdapter {
    IBiome createPlatformBiome(BiomeSettings settings, Biome biome, Holder.Reference<Biome> ref);
    boolean biomeHasTag(Registry<Biome> registry, ResourceKey<Biome> key, String tag);
}
