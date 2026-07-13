package com.pg85.otg.fabric.gen;

import com.pg85.otg.config.preset.DimensionPresetConfig;
import com.pg85.otg.gen.OTGChunkGenerator;
import com.pg85.otg.interfaces.IPluginConfig;
import com.pg85.otg.shared.gen.SharedWorldGenRegion;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.gen.OTGWorldInfo;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

public class FabricWorldGenRegion extends SharedWorldGenRegion {
    private final OTGFabricChunkGenerator fabricChunkGenerator;

    public FabricWorldGenRegion(
        String presetFolderName,
        IPluginConfig pluginConfig,
        DimensionPresetConfig presetConfig,
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

    @Override
    public void placeStructure(String structureName, int x, int y, int z, com.pg85.otg.util.bo3.Rotation rotation) {
        if (!(worldGenLevel instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) worldGenLevel;
        StructureTemplateManager manager = serverLevel.getStructureManager();
        Optional<StructureTemplate> template = manager.get(ResourceLocation.parse(structureName));

        if (template.isPresent()) {
            Rotation mcRotation = Rotation.NONE;
            switch (rotation) {
                case NORTH:
                    mcRotation = Rotation.NONE;
                    break;
                case EAST:
                    mcRotation = Rotation.CLOCKWISE_90;
                    break;
                case SOUTH:
                    mcRotation = Rotation.CLOCKWISE_180;
                    break;
                case WEST:
                    mcRotation = Rotation.COUNTERCLOCKWISE_90;
                    break;
            }

            StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(mcRotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

            template.get().placeInWorld(serverLevel, new BlockPos(x, y, z), new BlockPos(x, y, z), settings, serverLevel.getRandom(), 2);
        } else {
            OTGLog.warn(LogCategory.CUSTOM_OBJECTS, "Could not find vanilla structure template: {}", structureName);
        }
    }
}
