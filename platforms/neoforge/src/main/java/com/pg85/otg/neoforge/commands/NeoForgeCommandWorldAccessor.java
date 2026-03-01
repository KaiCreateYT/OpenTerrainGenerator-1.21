package com.pg85.otg.neoforge.commands;

import com.pg85.otg.OTG;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.neoforge.gen.NeoForgeWorldGenRegion;
import com.pg85.otg.neoforge.gen.OTGNeoForgeChunkGenerator;
import com.pg85.otg.neoforge.util.NeoForgeNBTHelper;
import com.pg85.otg.shared.commands.CommandWorldAccessor;
import com.pg85.otg.shared.commands.WorldEditHelper;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.nbt.LocalNBTHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public class NeoForgeCommandWorldAccessor implements CommandWorldAccessor {
    @Override
    public LocalWorldGenRegion createCommandRegion(ServerLevel level, int chunkX, int chunkZ) {
        if (!(level.getChunkSource().getGenerator() instanceof OTGNeoForgeChunkGenerator otgGen)) {
            return null;
        }
        var preset = otgGen.getPreset();
        var otgWorldInfo = otgGen.getOtgWorldInfo();
        var chunkAccess = level.getChunk(chunkX, chunkZ);
        return new NeoForgeWorldGenRegion(
            preset.getFolderName(),
            OTG.getEngine().getPluginConfig(),
            preset.getConfig(),
            otgWorldInfo,
            level,
            chunkAccess,
            otgGen
        );
    }

    @Override
    public CustomStructureCache getStructureCache(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof OTGNeoForgeChunkGenerator otgGen)) {
            return null;
        }
        return otgGen.getStructureCache(level.getServer().getWorldPath(LevelResource.ROOT));
    }

    @Override
    public String getPresetFolderName(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof OTGNeoForgeChunkGenerator otgGen)) {
            return null;
        }
        return otgGen.getPreset().getFolderName();
    }

    @Override
    public LocalNBTHelper createNBTHelper() {
        return new NeoForgeNBTHelper();
    }

    @Override
    public int[] getWorldEditSelection(ServerPlayer player) {
        if (!WorldEditHelper.isWorldEditAvailable()) {
            return null;
        }
        // WorldEdit on NeoForge may use either ForgeAdapter or NeoForgeAdapter depending on the version
        return WorldEditHelper.getSelection(player,
            "com.sk89q.worldedit.neoforge.ForgeAdapter",
            "com.sk89q.worldedit.neoforge.NeoForgeAdapter"
        );
    }
}
