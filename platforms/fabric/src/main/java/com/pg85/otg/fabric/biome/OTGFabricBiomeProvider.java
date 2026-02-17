package com.pg85.otg.fabric.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pg85.otg.shared.biome.SharedOTGBiomeProvider;
import net.minecraft.world.level.biome.BiomeSource;

public class OTGFabricBiomeProvider extends SharedOTGBiomeProvider {
    public static final MapCodec<OTGFabricBiomeProvider> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.fieldOf("preset_name").stable().forGetter(OTGFabricBiomeProvider::getPresetFolderName),
                    Codec.LONG.fieldOf("seed").stable().forGetter(OTGFabricBiomeProvider::getSeed)
            ).apply(instance, instance.stable(OTGFabricBiomeProvider::new)));

    public OTGFabricBiomeProvider(String presetFolderName, long seed) {
        super(presetFolderName, seed);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }
}
