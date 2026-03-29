package com.pg85.otg.neoforge.gen;

import com.pg85.otg.config.preset.DimensionPresetConfig;
import com.pg85.otg.gen.OTGChunkGenerator;
import com.pg85.otg.interfaces.IPluginConfig;
import com.pg85.otg.shared.gen.SharedWorldGenRegion;
import com.pg85.otg.util.gen.OTGWorldInfo;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public class NeoForgeWorldGenRegion extends SharedWorldGenRegion {
    private final OTGNeoForgeChunkGenerator neoForgeChunkGenerator;

    public NeoForgeWorldGenRegion(
        String presetFolderName,
        IPluginConfig pluginConfig,
        DimensionPresetConfig presetConfig,
        OTGWorldInfo otgWorldInfo,
        WorldGenLevel worldGenLevel,
        ChunkAccess chunkAccess,
        OTGNeoForgeChunkGenerator chunkGenerator
    ) {
        super(
            presetFolderName,
            pluginConfig,
            presetConfig,
            otgWorldInfo,
            worldGenLevel,
            chunkAccess,
            chunkGenerator,
            chunkGenerator.getInternalGenerator()
        );
        this.neoForgeChunkGenerator = chunkGenerator;
    }

    @Override
    protected OTGChunkGenerator getInternalGenerator() {
        return neoForgeChunkGenerator.getInternalGenerator();
    }

    @Override
    protected LocalMaterialData getMaterialInUnloadedChunk(int x, int y, int z) {
        return neoForgeChunkGenerator.getMaterialInUnloadedChunk(x, y, z);
    }

    @Override
    protected int getHighestBlockYInUnloadedChunk(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow) {
        return neoForgeChunkGenerator.getHighestBlockYInUnloadedChunk(x, z, findSolid, findLiquid, ignoreLiquid, ignoreSnow);
    }
}
