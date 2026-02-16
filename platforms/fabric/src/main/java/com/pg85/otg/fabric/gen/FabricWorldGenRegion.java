package com.pg85.otg.fabric.gen;

import com.pg85.otg.config.preset.PresetConfig;
import com.pg85.otg.fabric.materials.FabricMaterialData;
import com.pg85.otg.fabric.util.FabricNBTHelper;
import com.pg85.otg.gen.OTGChunkGenerator;
import com.pg85.otg.interfaces.IPluginConfig;
import com.pg85.otg.shared.gen.SharedWorldGenRegion;
import com.pg85.otg.util.gen.OTGWorldInfo;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.nbt.NamedBinaryTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public class FabricWorldGenRegion extends SharedWorldGenRegion {
    private final OTGFabricChunkGenerator fabricChunkGenerator;

    public FabricWorldGenRegion(
        String presetFolderName,
        IPluginConfig pluginConfig,
        PresetConfig presetConfig,
        OTGWorldInfo otgWorldInfo,
        WorldGenLevel worldGenLevel,
        ChunkAccess chunkAccess,
        OTGFabricChunkGenerator chunkGenerator
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
        this.fabricChunkGenerator = chunkGenerator;
    }

    @Override
    protected LocalMaterialData fromBlockState(BlockState blockState) {
        return FabricMaterialData.ofBlockState(blockState);
    }

    @Override
    protected BlockState toBlockState(LocalMaterialData material) {
        return ((FabricMaterialData) material).getState();
    }

    @Override
    protected CompoundTag convertNBT(NamedBinaryTag tag) {
        return FabricNBTHelper.getNMSFromNBTTagCompound(tag);
    }

    @Override
    protected OTGChunkGenerator getInternalGenerator() {
        return fabricChunkGenerator.getInternalGenerator();
    }

    @Override
    protected LocalMaterialData getMaterialInUnloadedChunk(int x, int y, int z) {
        return fabricChunkGenerator.getMaterialInUnloadedChunk(x, y, z);
    }

    @Override
    protected int getHighestBlockYInUnloadedChunk(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow) {
        return fabricChunkGenerator.getHighestBlockYInUnloadedChunk(x, z, findSolid, findLiquid, ignoreLiquid, ignoreSnow);
    }
}
