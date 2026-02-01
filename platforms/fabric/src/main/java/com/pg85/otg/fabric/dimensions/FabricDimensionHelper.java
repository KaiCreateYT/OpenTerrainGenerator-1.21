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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
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
            BlockPos safeSpawn = findSafeSpawn(level);
            player.teleportTo(level, safeSpawn.getX() + 0.5, safeSpawn.getY(), safeSpawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        } else {
            OTGLog.warn("Cannot teleport to dimension %s - not loaded (server restart may be required)", dimensionName);
        }
    }

    /**
     * Finds a safe spawn location in the given level.
     * Searches in a spiral pattern from 0,0 for solid ground with air above.
     */
    public BlockPos findSafeSpawn(ServerLevel level) {
        // First try the world spawn
        BlockPos worldSpawn = level.getSharedSpawnPos();
        BlockPos safe = findSafeY(level, worldSpawn.getX(), worldSpawn.getZ());
        if (safe != null) {
            return safe;
        }

        // Search in spiral pattern from 0,0
        int maxRadius = 1000;
        int step = 16; // Check every chunk

        for (int radius = 0; radius <= maxRadius; radius += step) {
            // Check points at this radius
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    // Only check points on the edge of the square
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    safe = findSafeY(level, dx, dz);
                    if (safe != null) {
                        OTGLog.info("Found safe spawn at %d, %d, %d", safe.getX(), safe.getY(), safe.getZ());
                        return safe;
                    }
                }
            }
        }

        // Fallback: return high Y at 0,0 and hope for the best
        OTGLog.warn("Could not find safe spawn, using fallback at 0, 256, 0");
        return new BlockPos(0, 256, 0);
    }

    /**
     * Finds a safe Y level at the given X,Z coordinates.
     * Returns null if no safe spot found.
     */
    private BlockPos findSafeY(ServerLevel level, int x, int z) {
        // Make sure chunk is loaded/generated
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        if (chunk == null) {
            return null;
        }

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        // Scan from top down to find solid ground with air above
        for (int y = maxY - 2; y > minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos above1 = pos.above();
            BlockPos above2 = above1.above();

            BlockState ground = level.getBlockState(pos);
            BlockState air1 = level.getBlockState(above1);
            BlockState air2 = level.getBlockState(above2);

            // Need solid ground, with 2 blocks of air above for player
            if (isSolidGround(ground) && isPassable(air1) && isPassable(air2)) {
                return above1; // Return the position where player feet will be
            }
        }

        return null;
    }

    private boolean isSolidGround(BlockState state) {
        // Check if block is solid and not liquid
        return state.isSolid() && !state.liquid();
    }

    private boolean isPassable(BlockState state) {
        // Air or non-solid blocks player can stand in
        return state.isAir() || (!state.isSolid() && !state.liquid());
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
