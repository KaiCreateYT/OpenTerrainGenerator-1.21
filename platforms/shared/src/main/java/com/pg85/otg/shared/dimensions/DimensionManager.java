package com.pg85.otg.shared.dimensions;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.settings.preset.GameRuleSettings;
import com.pg85.otg.dimensions.DimensionDatapack;
import com.pg85.otg.dimensions.DimensionInfo;
import com.pg85.otg.dimensions.OTGWorldStorage;
import com.pg85.otg.loader.WorldPresetConfigLoader;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.gamerules.GameRuleApplier;
import com.pg85.otg.shared.gamerules.GameRuleManager;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.OTGLog;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class DimensionManager {

    private final PlatformDimensionHelper helper;
    private OTGWorldStorage storage;
    private DimensionDatapack datapack;
    private MinecraftServer server;

    public DimensionManager(PlatformDimensionHelper helper) {
        this.helper = helper;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        this.storage = new OTGWorldStorage(helper.getWorldPath(server));
        this.datapack = new DimensionDatapack(helper.getDatapackPath(server));
        this.storage.load();

        for (DimensionInfo info : storage.getAllDimensions()) {
            DimensionPreset preset = OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByFolderName(info.getPreset());
            if (preset != null) {
                try {
                    datapack.createDimensionFiles(info, preset.getConfig().getDimensionSettings());
                } catch (Exception e) {
                    OTGLog.error("Failed to regenerate datapack for {}: {}", info.getName(), e.getMessage());
                }
            } else {
                OTGLog.warn("Preset {} not found for dimension {}", info.getPreset(), info.getName());
            }
        }

        // Restore persisted GameRules for all dimensions
        for (var entry : storage.getAllGameRules().entrySet()) {
            String dimKeyStr = entry.getKey();
            ResourceKey<Level> levelKey = parseDimensionKey(dimKeyStr);
            if (levelKey != null) {
                GameRules rules = GameRuleApplier.fromMap(entry.getValue());
                GameRuleManager.register(levelKey, rules);
            }
        }
        if (!storage.getAllGameRules().isEmpty()) {
            OTGLog.info("Restored GameRules for {} dimensions", storage.getAllGameRules().size());
        }

        // Apply GameRules for overworld if using OTG preset (first-time only)
        if (storage.getGameRules("minecraft:overworld").isEmpty()) {
            applyOverworldGameRulesIfOTG(server);
        }
    }

    public CreateResult createDimension(String presetName) {
        DimensionPreset preset = OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByFolderName(presetName);
        if (preset == null) {
            return CreateResult.error("Unknown preset '" + presetName + "'. Use /otg preset list");
        }

        String normalizedName = DimensionNameUtils.normalizeName(presetName);

        if (storage.exists(normalizedName)) {
            return CreateResult.error("Dimension otg:" + normalizedName + " already exists");
        }

        long seed = new Random().nextLong();
        DimensionInfo info = DimensionInfo.create(presetName, seed);

        try {
            datapack.createDimensionFiles(info, preset.getConfig().getDimensionSettings());
            storage.addDimension(info);

            // Apply GameRules from preset + optional WorldPresetConfig override
            GameRuleSettings gameRuleSettings = preset.getConfig().getGameRuleSettings();
            WorldPresetConfig.GameRules dimConfigOverrides = loadDimensionConfigGameRules(presetName);
            GameRules gameRules = GameRuleApplier.createGameRules(gameRuleSettings, dimConfigOverrides, server);
            ResourceKey<Level> levelKey = DimensionKeys.otg(normalizedName);
            GameRuleManager.register(levelKey, gameRules);
            storage.putGameRules("otg:" + normalizedName, GameRuleApplier.toMap(gameRules));

            helper.createDimensionRuntime(server, normalizedName, presetName, seed);
            return CreateResult.success(info);
        } catch (Exception e) {
            OTGLog.error("Failed to create dimension: {}", e.getMessage());
            try {
                datapack.deleteDimensionFiles(normalizedName);
                storage.removeDimension(normalizedName);
                GameRuleManager.unregister(DimensionKeys.otg(normalizedName));
                storage.removeGameRules("otg:" + normalizedName);
            } catch (Exception ignored) { OTGLog.error("Failed to cleanup after failed dimension creation: {}", ignored.getMessage()); }
            return CreateResult.error("Failed to create dimension: " + e.getMessage());
        }
    }

    public DeleteResult deleteDimension(String name, boolean purge, boolean confirmed) {
        String normalizedName = DimensionNameUtils.normalizeName(name);

        Optional<DimensionInfo> dimOpt = storage.getDimension(normalizedName);
        if (dimOpt.isEmpty()) {
            return DeleteResult.error("Unknown dimension '" + name + "'. Use /otg dimension list");
        }

        if (normalizedName.equals("overworld") || normalizedName.equals("the_nether") || normalizedName.equals("the_end")) {
            return DeleteResult.error("Cannot delete vanilla dimensions");
        }

        if (purge && !confirmed) {
            return DeleteResult.needsConfirmation(normalizedName);
        }

        try {
            List<ServerPlayer> players = helper.getPlayersInDimension(server, normalizedName);
            int playerCount = players.size();

            if (helper.isDimensionLoaded(server, normalizedName)) {
                helper.deleteDimensionRuntime(server, normalizedName);
            }

            datapack.deleteDimensionFiles(normalizedName);
            storage.removeDimension(normalizedName);
            GameRuleManager.unregister(DimensionKeys.otg(normalizedName));
            storage.removeGameRules("otg:" + normalizedName);

            if (purge) {
                helper.purgeWorldData(server, normalizedName);
            }

            return DeleteResult.success(normalizedName, playerCount, purge);
        } catch (Exception e) {
            OTGLog.error("Failed to delete dimension: {}", e.getMessage());
            return DeleteResult.error("Failed to delete dimension: " + e.getMessage());
        }
    }

    public List<DimensionInfo> listDimensions() {
        return storage.getAllDimensions();
    }

    public Optional<DimensionInfo> getDimensionInfo(String name) {
        return storage.getDimension(DimensionNameUtils.normalizeName(name));
    }

    public void teleportPlayer(ServerPlayer player, String dimensionName) {
        helper.teleportToDimension(player, DimensionNameUtils.normalizeName(dimensionName));
    }

    public boolean loadDimensionRuntime(String name) {
        String normalizedName = DimensionNameUtils.normalizeName(name);
        var info = storage.getDimension(normalizedName);
        if (info.isEmpty()) {
            return false;
        }

        try {
            helper.createDimensionRuntime(server, normalizedName, info.get().getPreset(), info.get().getSeed());
            return true;
        } catch (Exception e) {
            OTGLog.error("Failed to load dimension {} at runtime: {}", normalizedName, e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        GameRuleManager.clear();
    }

    public PlatformDimensionHelper getHelper() {
        return helper;
    }

    private void applyOverworldGameRulesIfOTG(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ChunkGenerator gen = overworld.getChunkSource().getGenerator();
        if (gen instanceof SharedOTGChunkGenerator otgGen) {
            DimensionPreset preset = otgGen.getPreset();
            if (preset == null) return;

            String presetName = preset.getFolderName();
            GameRuleSettings gameRuleSettings = preset.getConfig().getGameRuleSettings();
            if (!gameRuleSettings.isOverrideGameRules()) return;

            WorldPresetConfig.GameRules overrides = loadDimensionConfigGameRules(presetName);
            GameRules rules = GameRuleApplier.createGameRules(gameRuleSettings, overrides, server);
            GameRuleManager.register(Level.OVERWORLD, rules);
            storage.putGameRules("minecraft:overworld", GameRuleApplier.toMap(rules));
            OTGLog.info("Applied GameRules for OTG overworld (preset: {})", presetName);
        }
    }

    private @Nullable WorldPresetConfig.GameRules loadDimensionConfigGameRules(String presetName) {
        // TODO: Task 11 will replace this with WorldPreset-aware GameRules loading.
        // The old fromDisk(presetName) lookup was always broken (searched by preset name
        // but YAMLs are world-level configs, not per-preset). Returns null for now.
        return null;
    }

    private static @Nullable ResourceKey<Level> parseDimensionKey(String dimKeyStr) {
        int colonIdx = dimKeyStr.indexOf(':');
        if (colonIdx < 0) {
            OTGLog.warn("Invalid dimension key in storage: {}", dimKeyStr);
            return null;
        }
        return ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(
                        dimKeyStr.substring(0, colonIdx),
                        dimKeyStr.substring(colonIdx + 1)));
    }

    public record CreateResult(boolean success, String error, DimensionInfo info) {
        public static CreateResult success(DimensionInfo info) {
            return new CreateResult(true, null, info);
        }
        public static CreateResult error(String message) {
            return new CreateResult(false, message, null);
        }
    }

    public record DeleteResult(boolean success, String error, String dimensionName,
                               int playersRelocated, boolean purged, boolean needsConfirmation) {
        public static DeleteResult success(String name, int players, boolean purged) {
            return new DeleteResult(true, null, name, players, purged, false);
        }
        public static DeleteResult error(String message) {
            return new DeleteResult(false, message, null, 0, false, false);
        }
        public static DeleteResult needsConfirmation(String name) {
            return new DeleteResult(false, null, name, 0, false, true);
        }
    }
}
