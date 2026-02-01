package com.pg85.otg.fabric.dimensions;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.fabric.mixin.MinecraftServerAccessor;
import com.pg85.otg.util.OTGLog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class FabricDimensionHelper {

    public Path getWorldPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    public Path getDatapackPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR);
    }

    public void teleportToOverworldSpawn(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    public void teleportToDimension(ServerPlayer player, String dimensionName) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(Constants.MOD_ID_SHORT, dimensionName));
        ServerLevel level = player.server.getLevel(dimKey);
        if (level != null) {
            BlockPos spawn = level.getSharedSpawnPos();
            player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        } else {
            OTGLog.warn("Cannot teleport to dimension %s - not loaded (server restart may be required)", dimensionName);
        }
    }

    public List<ServerPlayer> getPlayersInDimension(MinecraftServer server, String dimensionName) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(Constants.MOD_ID_SHORT, dimensionName));
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            return List.of();
        }
        return level.players();
    }

    public boolean isDimensionLoaded(MinecraftServer server, String dimensionName) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(Constants.MOD_ID_SHORT, dimensionName));
        return server.getLevel(dimKey) != null;
    }

    public void createDimensionRuntime(MinecraftServer server, String name, String presetName, long seed) throws Exception {
        // Runtime dimension creation in Minecraft is complex and typically requires server restart.
        // The datapack files are created by DimensionDatapack, and the dimension will be loaded on next restart.
        OTGLog.info("Dimension %s created via datapack. Server restart required for full activation.", name);
    }

    public void deleteDimensionRuntime(MinecraftServer server, String name) throws Exception {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(Constants.MOD_ID_SHORT, name));

        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            OTGLog.info("Dimension %s not currently loaded", name);
            return;
        }

        // Teleport all players out first
        for (ServerPlayer player : List.copyOf(level.players())) {
            teleportToOverworldSpawn(player);
        }

        // Save the level
        level.save(null, true, false);

        // Close chunk source
        try {
            level.getChunkSource().close();
        } catch (IOException e) {
            OTGLog.error("Error closing chunk source: %s", e.getMessage());
        }

        // Remove from server's level map
        ((MinecraftServerAccessor) server).getLevels().remove(dimKey);

        OTGLog.info("Dimension %s unloaded", name);
    }

    public void purgeWorldData(MinecraftServer server, String name) throws Exception {
        Path dimensionFolder = getWorldPath(server)
                .resolve("dimensions")
                .resolve(Constants.MOD_ID_SHORT)
                .resolve(name);

        if (Files.exists(dimensionFolder)) {
            Files.walk(dimensionFolder)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            OTGLog.error("Failed to delete %s: %s", path, e.getMessage());
                        }
                    });
            OTGLog.info("Purged world data for dimension %s", name);
        }
    }
}
