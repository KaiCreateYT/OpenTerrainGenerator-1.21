package com.pg85.otg.shared.dimensions;

import java.nio.file.Path;
import java.util.List;

public interface PlatformDimensionHelper<TServer, TPlayer, TLevel> {

    Path getWorldPath(TServer server);

    Path getDatapackPath(TServer server);

    void teleportToOverworldSpawn(TPlayer player);

    void teleportToDimension(TPlayer player, String dimensionName);

    List<TPlayer> getPlayersInDimension(TServer server, String dimensionName);

    boolean isDimensionLoaded(TServer server, String dimensionName);

    void createDimensionRuntime(TServer server, String name, String presetName, long seed) throws Exception;

    void deleteDimensionRuntime(TServer server, String name) throws Exception;

    void purgeWorldData(TServer server, String name) throws Exception;
}
