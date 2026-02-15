package com.pg85.otg.shared.commands;

import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.nbt.LocalNBTHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface CommandWorldAccessor {
    LocalWorldGenRegion createCommandRegion(ServerLevel level, int chunkX, int chunkZ);
    CustomStructureCache getStructureCache(ServerLevel level);
    LocalNBTHelper createNBTHelper();

    /**
     * Gets the WorldEdit selection for the given player.
     * Returns [minX, minY, minZ, maxX, maxY, maxZ] or null if WorldEdit is
     * not installed or the player has no selection.
     */
    int[] getWorldEditSelection(ServerPlayer player);
}
