package com.pg85.otg.neoforge.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pg85.otg.shared.biome.SharedOTGBiomeProvider;
import net.minecraft.world.level.biome.BiomeSource;

public class OTGNeoForgeBiomeProvider extends SharedOTGBiomeProvider {
    public static final MapCodec<OTGNeoForgeBiomeProvider> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.fieldOf("preset_name").stable().forGetter(OTGNeoForgeBiomeProvider::getPresetFolderName),
                    Codec.LONG.fieldOf("seed").stable().forGetter(OTGNeoForgeBiomeProvider::getSeed)
            ).apply(instance, instance.stable(OTGNeoForgeBiomeProvider::new)));

    public OTGNeoForgeBiomeProvider(String presetFolderName, long seed) {
        super(presetFolderName, seed);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }
}
