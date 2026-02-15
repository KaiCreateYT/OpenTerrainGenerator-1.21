package com.pg85.otg.shared.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pg85.otg.OTG;
import com.pg85.otg.config.ConfigFunction;
import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.constants.settings.structure.CustomStructureType;
import com.pg85.otg.customobject.CustomObject;
import com.pg85.otg.customobject.CustomObjectManager;
import com.pg85.otg.customobject.bo4.BO4;
import com.pg85.otg.customobject.bo4.BO4Data;
import com.pg85.otg.customobject.config.CustomObjectResourcesManager;
import com.pg85.otg.customobject.resource.CustomStructureResource;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.customobject.structures.bo4.BO4CustomStructure;
import com.pg85.otg.customobject.structures.bo4.BO4CustomStructureCoordinate;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.interfaces.IMaterialReader;
import com.pg85.otg.interfaces.IModLoadedChecker;
import com.pg85.otg.interfaces.IStructuredCustomObject;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.bo3.Rotation;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExportBO4DataCommand {

    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static volatile boolean isDone = false;
    private static volatile String errorMessage = null;
    private static volatile int current = 0;
    private static volatile int total = 0;
    private static volatile String currentBoName = "";

    public static void register(LiteralArgumentBuilder<CommandSourceStack> otgCommand) {
        otgCommand.then(
            Commands.literal("exportbo4data")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> execute(ctx.getSource()))
        );
    }

    private static int execute(CommandSourceStack source) {
        CommandWorldAccessor accessor = OTGCommandRegistrar.getWorldAccessor();
        if (accessor == null) {
            source.sendFailure(Component.literal("Command world accessor not available."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        CustomStructureCache structureCache = accessor.getStructureCache(level);
        if (structureCache == null) {
            source.sendFailure(Component.literal("OTG is not enabled in this world."));
            return 0;
        }

        // Get the preset from the chunk generator
        String presetFolderName = accessor.getPresetFolderName(level);
        Preset preset = presetFolderName != null
            ? OTG.getEngine().getPresetLoader().getPresetByShortNameOrFolderName(presetFolderName)
            : null;
        if (preset == null) {
            source.sendFailure(Component.literal("Could not find OTG preset for this world."));
            return 0;
        }

        if (preset.getPresetConfig().getResourceSettings().getCustomStructureType() != CustomStructureType.BO4) {
            source.sendSuccess(
                () -> Component.literal("The ExportBO4Data command is only available for CustomStructureType:BO4 worlds."),
                false
            );
            return 0;
        }

        if (isRunning.compareAndSet(false, true)) {
            isDone = false;
            errorMessage = null;
            current = 0;
            total = 0;
            currentBoName = "";

            source.sendSuccess(
                () -> Component.literal("Exporting .BO4Data files for world, this may take a while."),
                false
            );
            source.sendSuccess(
                () -> Component.literal("Run this command again to see progress or check the logs."),
                false
            );

            // Capture values on the main thread to avoid cross-thread ServerLevel access
            final Preset bgPreset = preset;
            final CustomStructureCache bgStructureCache = structureCache;
            final CommandWorldAccessor bgAccessor = accessor;
            final ServerLevel bgLevel = level;
            final long bgSeed = level.getSeed();

            new Thread(() -> {
                try {
                    // NOTE: bgLevel is still passed for createCommandRegion which requires it.
                    // This is a known threading concern - Minecraft's ServerLevel is not thread-safe.
                    // A proper fix would require queuing chunk access back to the server thread.
                    exportOnBackground(bgPreset, bgStructureCache, bgAccessor, bgLevel, bgSeed);
                } catch (Exception e) {
                    errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
                    OTGLog.log(LogLevel.ERROR, LogCategory.MAIN, "Error during BO4Data export: " + e.getMessage());
                    OTGLog.printStackTrace(LogLevel.ERROR, LogCategory.MAIN, e);
                } finally {
                    isDone = true;
                    isRunning.set(false);
                }
            }, "OTG-ExportBO4Data").start();

        } else {
            if (isDone) {
                isDone = false;
                String error = errorMessage;
                errorMessage = null;
                if (error != null) {
                    source.sendFailure(Component.literal("OTG exportbo4data failed: " + error));
                } else {
                    source.sendSuccess(
                        () -> Component.literal("OTG exportbo4data is done."),
                        false
                    );
                }
            } else {
                final int progressCurrent = current;
                final int progressTotal = total;
                final String progressName = currentBoName;
                source.sendSuccess(
                    () -> Component.literal("OTG exportbo4data is running, "
                        + (progressCurrent == 0
                            ? "exporting structure start " + progressName
                            : "exporting " + progressCurrent + "/" + progressTotal)),
                    false
                );
            }
        }

        return 0;
    }

    private static void exportOnBackground(Preset preset, CustomStructureCache structureCache,
                                           CommandWorldAccessor accessor, ServerLevel level, long seed) {
        CustomObjectManager customObjectManager = OTG.getEngine().getCustomObjectManager();
        IMaterialReader materialReader = OTG.getEngine().getPresetLoader().getMaterialReader();
        CustomObjectResourcesManager resourcesManager = OTG.getEngine().getCustomObjectResourcesManager();
        IModLoadedChecker modLoadedChecker = OTG.getEngine().getModLoadedChecker();
        Path otgRootFolder = OTG.getEngine().getOTGRootFolder();
        String presetFolderName = preset.getFolderName();

        OTGLog.log(LogLevel.INFO, LogCategory.MAIN, "Initializing and exporting structure starts");

        // Phase 1: Export structure starts from biome configs
        // Make sure all structure starts have been initialised (getMinimumSize computed)
        // so that the data can be saved with the BO4Data.
        for (BiomeConfig biomeConfig : preset.getBiomeConfigList()) {
            for (ConfigFunction<BiomeSettings> res : biomeConfig.getResourceQueue()) {
                if (res instanceof CustomStructureResource customStructureRes) {
                    List<IStructuredCustomObject> objects = customStructureRes.getObjects(
                        presetFolderName, otgRootFolder,
                        customObjectManager, materialReader,
                        resourcesManager, modLoadedChecker
                    );

                    for (IStructuredCustomObject structure : objects) {
                        if (structure == null) {
                            continue; // Structure in resource list but file not found
                        }
                        if (!(structure instanceof BO4 bo4)) {
                            continue;
                        }
                        if (BO4Data.bo4DataExists(bo4.getConfig())) {
                            continue;
                        }

                        BO4CustomStructureCoordinate structureCoord = new BO4CustomStructureCoordinate(
                            presetFolderName, structure, null,
                            Rotation.NORTH, 0, (short) 0, 0, 0,
                            false, false, null
                        );

                        BO4CustomStructure structureStart = new BO4CustomStructure(
                            seed, structureCoord,
                            otgRootFolder, customObjectManager,
                            materialReader, resourcesManager, modLoadedChecker
                        );

                        // Get minimum size (size if spawned with branchDepth 0)
                        try {
                            // Create a world gen region for getMinimumSize
                            LocalWorldGenRegion worldGenRegion = accessor.createCommandRegion(level, 0, 0);
                            if (worldGenRegion != null) {
                                structureStart.getMinimumSize(
                                    structureCache, worldGenRegion,
                                    otgRootFolder, customObjectManager,
                                    materialReader, resourcesManager, modLoadedChecker
                                );
                            }
                        } catch (InvalidConfigException e) {
                            bo4.isInvalidConfig = true;
                        }

                        OTGLog.log(LogLevel.INFO, LogCategory.MAIN,
                            "Exporting .BO4Data for structure start " + bo4.getName());
                        currentBoName = bo4.getName();

                        BO4Data.generateBO4Data(
                            bo4.getConfig(), presetFolderName,
                            otgRootFolder, customObjectManager,
                            materialReader, resourcesManager, modLoadedChecker
                        );

                        customObjectManager.getGlobalObjects().unloadCustomObjectFiles();
                    }
                }
            }
        }

        // Phase 2: Export all remaining BO4 objects in the preset
        ArrayList<String> boNames = customObjectManager.getGlobalObjects()
            .getAllBONamesForPreset(presetFolderName, otgRootFolder);

        if (boNames == null) {
            OTGLog.log(LogLevel.INFO, LogCategory.MAIN, "No BO objects found for preset " + presetFolderName);
            OTGLog.log(LogLevel.INFO, LogCategory.MAIN, "Exporting .BO4Data done.");
            return;
        }

        current = 0;
        total = boNames.size();

        for (String boName : boNames) {
            current++;
            CustomObject bo = customObjectManager.getGlobalObjects().getObjectByName(
                boName, presetFolderName, otgRootFolder,
                customObjectManager, materialReader,
                resourcesManager, modLoadedChecker
            );

            if (bo instanceof BO4 bo4 && !BO4Data.bo4DataExists(bo4.getConfig())) {
                OTGLog.log(LogLevel.INFO, LogCategory.MAIN,
                    "Exporting .BO4Data " + current + "/" + total + " " + boName);

                BO4Data.generateBO4Data(
                    bo4.getConfig(), presetFolderName,
                    otgRootFolder, customObjectManager,
                    materialReader, resourcesManager, modLoadedChecker
                );

                customObjectManager.getGlobalObjects().unloadCustomObjectFiles();
            }
        }

        OTGLog.log(LogLevel.INFO, LogCategory.MAIN, "Exporting .BO4Data done.");
    }

}
