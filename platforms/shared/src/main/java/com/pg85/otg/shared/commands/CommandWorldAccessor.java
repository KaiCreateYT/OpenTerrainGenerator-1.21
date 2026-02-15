package com.pg85.otg.shared.commands;

import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.nbt.LocalNBTHelper;
import net.minecraft.server.level.ServerLevel;

public interface CommandWorldAccessor {
    LocalWorldGenRegion createCommandRegion(ServerLevel level, int chunkX, int chunkZ);
    CustomStructureCache getStructureCache(ServerLevel level);
    LocalNBTHelper createNBTHelper();
}
