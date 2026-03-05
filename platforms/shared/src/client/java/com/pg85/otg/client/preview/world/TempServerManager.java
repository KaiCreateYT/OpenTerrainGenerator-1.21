package com.pg85.otg.client.preview.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.Difficulty;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manages a temporary IntegratedServer for preview chunk generation.
 *
 * Uses MC's native createFreshLevel flow — this briefly shows a loading screen,
 * then the caller grabs chunks from the ServerLevel and disconnects.
 *
 * Flow:
 * 1. startServer() calls createFreshLevel with temp world name + OTG preset
 * 2. MC shows loading screen, starts IntegratedServer, connects client
 * 3. Caller polls isReady() until server is up
 * 4. Caller generates chunks via ChunkGenerationManager
 * 5. stopServer() disconnects and cleans up temp save
 */
public class TempServerManager {

    private static final Logger LOG = LoggerFactory.getLogger(TempServerManager.class);
    private String tempWorldName;

    public boolean isRunning() {
        MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
        return srv != null && srv.isReady();
    }

    public ServerLevel getOverworld() {
        MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
        return srv != null ? srv.getLevel(Level.OVERWORLD) : null;
    }

    /**
     * Start a temporary IntegratedServer with the given OTG preset.
     * MUST be called on the render thread (it drives MC's createFreshLevel).
     *
     * @param presetId  ResourceLocation of the OTG WorldPreset (e.g. "otg:default")
     *                  or null for vanilla normal
     * @param seed      world seed
     * @param statusCallback  status updates for UI
     */
    public void startServer(String presetId, long seed, Consumer<String> statusCallback) {
        Minecraft mc = Minecraft.getInstance();

        tempWorldName = "otg-preview-" + UUID.randomUUID().toString().substring(0, 8);
        statusCallback.accept("Creating preview world...");

        WorldOptions worldOptions = new WorldOptions(seed, false, false);

        GameRules gameRules = new GameRules();
        gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);

        LevelSettings levelSettings = new LevelSettings(
            tempWorldName,
            GameType.SPECTATOR,
            false,
            Difficulty.PEACEFUL,
            true,
            gameRules,
            WorldDataConfiguration.DEFAULT
        );

        // Resolve which WorldPreset to use
        ResourceKey<WorldPreset> presetKey;
        if (presetId != null && !presetId.isEmpty()) {
            ResourceLocation loc = ResourceLocation.tryParse(presetId);
            if (loc != null) {
                presetKey = ResourceKey.create(Registries.WORLD_PRESET, loc);
            } else {
                presetKey = WorldPresets.NORMAL;
            }
        } else {
            presetKey = WorldPresets.NORMAL;
        }

        statusCallback.accept("Starting server...");

        // createFreshLevel drives MC's full world creation flow:
        // - Shows loading screen
        // - Starts IntegratedServer on a new thread
        // - Connects client when ready
        mc.createWorldOpenFlows().createFreshLevel(
            tempWorldName,
            levelSettings,
            worldOptions,
            registryAccess -> registryAccess
                .registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(presetKey)
                .value()
                .createWorldDimensions(),
            null // lastScreen — null means back to title on failure
        );
    }

    /**
     * Stop the server and clean up temp save directory.
     * MUST be called on the render thread. Blocks until disconnect completes.
     */
    public void stopServer() {
        Minecraft mc = Minecraft.getInstance();
        String worldName = tempWorldName;
        tempWorldName = null;

        // disconnect() is synchronous — halts the IntegratedServer and waits
        mc.disconnect();

        if (worldName != null) {
            try {
                Path savesDir = mc.getLevelSource().getBaseDir();
                Path worldDir = savesDir.resolve(worldName);
                if (Files.exists(worldDir)) {
                    deleteDirRecursive(worldDir);
                    LOG.info("Deleted temp preview world: {}", worldName);
                }
            } catch (Exception e) {
                LOG.warn("Failed to delete temp preview world: {}", worldName, e);
            }
        }
    }

    public String getTempWorldName() {
        return tempWorldName;
    }

    private static void deleteDirRecursive(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warn("Error deleting directory: {}", dir, e);
        }
    }
}
