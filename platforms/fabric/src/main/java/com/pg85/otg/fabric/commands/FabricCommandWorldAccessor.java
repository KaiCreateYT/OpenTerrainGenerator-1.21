package com.pg85.otg.fabric.commands;

import com.pg85.otg.OTG;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.fabric.gen.FabricWorldGenRegion;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.util.FabricNBTHelper;
import com.pg85.otg.shared.commands.CommandWorldAccessor;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.nbt.LocalNBTHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

public class FabricCommandWorldAccessor implements CommandWorldAccessor {
    @Override
    public LocalWorldGenRegion createCommandRegion(ServerLevel level, int chunkX, int chunkZ) {
        if (!(level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator otgGen)) {
            return null;
        }
        var preset = otgGen.getPreset();
        var otgWorldInfo = otgGen.getOtgWorldInfo();
        var chunkAccess = level.getChunk(chunkX, chunkZ);
        return new FabricWorldGenRegion(
            preset.getFolderName(),
            OTG.getEngine().getPluginConfig(),
            preset.getPresetConfig(),
            otgWorldInfo,
            level,
            chunkAccess,
            otgGen
        );
    }

    @Override
    public CustomStructureCache getStructureCache(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator otgGen)) {
            return null;
        }
        return otgGen.getStructureCache(level.getServer().getWorldPath(LevelResource.ROOT));
    }

    @Override
    public LocalNBTHelper createNBTHelper() {
        return new FabricNBTHelper();
    }
}
