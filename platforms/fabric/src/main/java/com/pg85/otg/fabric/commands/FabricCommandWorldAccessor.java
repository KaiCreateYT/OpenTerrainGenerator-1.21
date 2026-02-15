package com.pg85.otg.fabric.commands;

import com.pg85.otg.OTG;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.fabric.gen.FabricWorldGenRegion;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.util.FabricNBTHelper;
import com.pg85.otg.shared.commands.CommandWorldAccessor;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import com.pg85.otg.util.nbt.LocalNBTHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
    public String getPresetFolderName(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator otgGen)) {
            return null;
        }
        return otgGen.getPreset().getFolderName();
    }

    @Override
    public LocalNBTHelper createNBTHelper() {
        return new FabricNBTHelper();
    }

    @Override
    public int[] getWorldEditSelection(ServerPlayer player) {
        try {
            // Check if WorldEdit is available
            Class.forName("com.sk89q.worldedit.WorldEdit");
            return getWorldEditSelectionReflective(player, "com.sk89q.worldedit.fabric.FabricAdapter");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Uses reflection to call WorldEdit APIs, so we don't need a compile-time dependency.
     * Adapter class name is platform-specific (FabricAdapter vs NeoForgeAdapter).
     */
    private static int[] getWorldEditSelectionReflective(ServerPlayer player, String adapterClassName) {
        try {
            // FabricAdapter.adaptPlayer(ServerPlayerEntity) -> FabricPlayer
            Class<?> adapterClass = Class.forName(adapterClassName);
            var adaptPlayerMethod = adapterClass.getMethod("adaptPlayer", ServerPlayer.class);
            Object wePlayer = adaptPlayerMethod.invoke(null, player);

            // wePlayer.getWorld() -> World
            Object world = wePlayer.getClass().getMethod("getWorld").invoke(wePlayer);

            // WorldEdit.getInstance().getSessionManager().get(wePlayer) -> LocalSession
            Class<?> worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object worldEditInstance = worldEditClass.getMethod("getInstance").invoke(null);
            Object sessionManager = worldEditInstance.getClass().getMethod("getSessionManager").invoke(worldEditInstance);
            Object session = sessionManager.getClass().getMethod("get", Class.forName("com.sk89q.worldedit.session.SessionOwner"))
                .invoke(sessionManager, wePlayer);

            // session.getSelection(world) -> Region
            Object selection = session.getClass().getMethod("getSelection", Class.forName("com.sk89q.worldedit.world.World"))
                .invoke(session, world);
            if (selection == null) return null;

            // selection.getMinimumPoint() / getMaximumPoint() -> BlockVector3
            Object min = selection.getClass().getMethod("getMinimumPoint").invoke(selection);
            Object max = selection.getClass().getMethod("getMaximumPoint").invoke(selection);

            // BlockVector3.x(), .y(), .z()
            Class<?> bv3Class = min.getClass();
            int minX = (int) bv3Class.getMethod("x").invoke(min);
            int minY = (int) bv3Class.getMethod("y").invoke(min);
            int minZ = (int) bv3Class.getMethod("z").invoke(min);
            int maxX = (int) bv3Class.getMethod("x").invoke(max);
            int maxY = (int) bv3Class.getMethod("y").invoke(max);
            int maxZ = (int) bv3Class.getMethod("z").invoke(max);

            return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
        } catch (Exception e) {
            OTGLog.log(LogLevel.WARN, LogCategory.MAIN, "Failed to get WorldEdit selection: " + e.getMessage());
            return null;
        }
    }
}
