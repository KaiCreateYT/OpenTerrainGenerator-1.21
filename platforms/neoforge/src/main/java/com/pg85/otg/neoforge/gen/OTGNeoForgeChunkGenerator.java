package com.pg85.otg.neoforge.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pg85.otg.neoforge.biome.OTGNeoForgeBiomeProvider;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.shared.biome.SharedBiome;
import com.pg85.otg.shared.gen.SharedChunkBuffer;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.gen.SharedShadowChunkGenerator;
import com.pg85.otg.shared.gen.SharedWorldGenRegion;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.gen.ChunkBuffer;
import com.pg85.otg.util.materials.LocalMaterialData;
import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import com.mojang.serialization.Codec;

@Getter
public class OTGNeoForgeChunkGenerator extends SharedOTGChunkGenerator {

    public static final MapCodec<OTGNeoForgeChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    instance.group(
                            OTGNeoForgeBiomeProvider.CODEC.forGetter(OTGNeoForgeChunkGenerator::getBiomeSource),
                            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(OTGNeoForgeChunkGenerator::getSettings)
                    ).apply(instance, instance.stable(OTGNeoForgeChunkGenerator::createFromCodec)));

    private final OTGNeoForgeBiomeProvider biomeSource;
    private final SharedShadowChunkGenerator shadowChunkGenerator;

    public static OTGNeoForgeChunkGenerator createFromCodec(
            OTGNeoForgeBiomeProvider biomeSource, Holder<NoiseGeneratorSettings> settings
    ) {
        return new OTGNeoForgeChunkGenerator(biomeSource, settings, null);
    }

    public OTGNeoForgeChunkGenerator(
            OTGNeoForgeBiomeProvider biomeSource, Holder<NoiseGeneratorSettings> settings, Registry<Biome> biomeRegistry
    ) {
        super(biomeSource, settings, biomeRegistry);
        this.biomeSource = biomeSource;
        this.shadowChunkGenerator = new SharedShadowChunkGenerator();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    protected SharedWorldGenRegion createWorldGenRegion(
            String presetFolderName, WorldGenLevel worldGenLevel, ChunkAccess chunkAccess) {
        return new NeoForgeWorldGenRegion(
                presetFolderName,
                com.pg85.otg.OTG.getEngine().getPluginConfig(),
                this.getPreset().getConfig(),
                this.getOtgWorldInfo(),
                worldGenLevel,
                chunkAccess,
                this
        );
    }

    @Override
    protected ChunkBuffer createChunkBuffer(ChunkAccess chunkAccess) {
        return new SharedChunkBuffer(chunkAccess);
    }

    @Override
    protected BiomeGenerationSettings getBiomeGenerationSettings(IBiome biome) {
        return ((SharedBiome) biome).getBiome().getGenerationSettings();
    }

    @Override
    public Boolean checkHasVanillaStructureWithoutLoading(ServerLevel level, ChunkCoordinate chunkCoord) {
        return this.shadowChunkGenerator.checkHasVanillaStructureWithoutLoading(
                level, chunkCoord, this.getInternalGenerator().getCachedBiomeProvider(), false);
    }

    @Override
    public int getHighestBlockYInUnloadedChunk(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow) {
        return this.shadowChunkGenerator.getHighestBlockYInUnloadedChunk(
                this.getServerLevel(), this, this.getOtgWorldInfo(), x, z, findSolid, findLiquid, ignoreLiquid, ignoreSnow);
    }

    @Override
    public LocalMaterialData getMaterialInUnloadedChunk(int x, int y, int z) {
        return this.shadowChunkGenerator.getMaterialInUnloadedChunk(
                this.getServerLevel(), this, this.getOtgWorldInfo(), x, y, z);
    }
}
