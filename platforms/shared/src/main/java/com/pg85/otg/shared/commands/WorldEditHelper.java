package com.pg85.otg.shared.commands;

import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared WorldEdit reflection utility. Extracts the common reflection logic
 * for getting a WorldEdit selection, so both Fabric and NeoForge can reuse it
 * with different adapter class names.
 */
public class WorldEditHelper {

    /**
     * Check if WorldEdit is available on the classpath.
     *
     * @return true if WorldEdit classes can be loaded
     */
    public static boolean isWorldEditAvailable() {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Get the player's WorldEdit selection via reflection, trying each adapter class name in order.
     * This avoids a compile-time dependency on WorldEdit.
     *
     * @param player            the server player
     * @param adapterClassNames adapter class names to try in order (e.g. FabricAdapter, NeoForgeAdapter)
     * @return [minX, minY, minZ, maxX, maxY, maxZ] or null if no selection / WorldEdit not present
     */
    public static int[] getSelection(ServerPlayer player, String... adapterClassNames) {
        for (String adapterClassName : adapterClassNames) {
            int[] result = getSelectionReflective(player, adapterClassName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static int[] getSelectionReflective(ServerPlayer player, String adapterClassName) {
        try {
            // Adapter.adaptPlayer(ServerPlayer) -> platform-specific Player
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
            OTGLog.log(LogLevel.WARN, LogCategory.MAIN, "Failed to get WorldEdit selection via " + adapterClassName + ": " + e.getMessage());
            return null;
        }
    }
}
