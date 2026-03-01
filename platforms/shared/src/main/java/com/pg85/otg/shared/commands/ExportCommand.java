package com.pg85.otg.shared.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pg85.otg.OTG;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.customobject.CustomObjectManager;
import com.pg85.otg.customobject.bo3.BO3;
import com.pg85.otg.customobject.bo3.BO3Config;
import com.pg85.otg.customobject.config.CustomObjectResourcesManager;
import com.pg85.otg.customobject.config.io.FileSettingsReaderBO4;
import com.pg85.otg.customobject.creator.ObjectCreator;
import com.pg85.otg.customobject.creator.ObjectType;
import com.pg85.otg.customobject.structures.StructuredCustomObject;
import com.pg85.otg.customobject.util.Corner;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.IMaterialReader;
import com.pg85.otg.interfaces.IModLoadedChecker;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.nbt.LocalNBTHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExportCommand {

    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (ctx, builder) -> {
        List<String> names = new ArrayList<>(OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresetFolderNames());
        names.add("global");
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> TEMPLATE_SUGGESTIONS = (ctx, builder) -> {
        String preset = StringArgumentType.getString(ctx, "preset");
        Path otgRootPath = OTG.getEngine().getOTGRootFolder();
        List<String> list;
        if (preset.equalsIgnoreCase("global")) {
            list = OTG.getEngine().getCustomObjectManager().getGlobalObjects()
                .getGlobalTemplates(otgRootPath);
        } else {
            list = OTG.getEngine().getCustomObjectManager().getGlobalObjects()
                .getTemplatesForPreset(preset, otgRootPath);
        }
        if (list == null) list = new ArrayList<>();
        list = list.stream()
            .map(name -> name.contains(" ") ? "\"" + name + "\"" : name)
            .collect(Collectors.toList());
        list.add("default");
        return SharedSuggestionProvider.suggest(list, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> FLAG_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{
            "-a", "-o", "-b", "-t", "-bo4",
            "-a -o", "-a -b", "-o -b", "-a -o -b",
            "-a -bo4", "-o -bo4", "-b -bo4",
            "-e stone,dirt,gravel"
        }, builder);

    public static void register(LiteralArgumentBuilder<CommandSourceStack> otgCommand) {
        otgCommand.then(
            Commands.literal("export")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("name", StringArgumentType.string())
                        .then(
                            Commands.argument("preset", StringArgumentType.string())
                                .suggests(PRESET_SUGGESTIONS)
                                .executes(ctx -> execute(ctx, "", ""))
                                .then(
                                    Commands.argument("template", StringArgumentType.string())
                                        .suggests(TEMPLATE_SUGGESTIONS)
                                        .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "template"), ""))
                                        .then(
                                            Commands.argument("flags", StringArgumentType.greedyString())
                                                .suggests(FLAG_SUGGESTIONS)
                                                .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "template"),
                                                    StringArgumentType.getString(ctx, "flags")))
                                        )
                                )
                        )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, String templateName, String flagsStr) {
        CommandSourceStack source = ctx.getSource();

        // Must be a player (we need WorldEdit selection)
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        String objectName = StringArgumentType.getString(ctx, "name");
        String presetName = StringArgumentType.getString(ctx, "preset");

        // Parse flags — split on whitespace, check each token individually
        Set<String> flags = new HashSet<>();
        String excludeBlocksStr = null;
        if (flagsStr != null && !flagsStr.isEmpty()) {
            String[] tokens = flagsStr.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                if (token.startsWith("-")) {
                    if (token.equalsIgnoreCase("-e") && i + 1 < tokens.length) {
                        // Next token is the comma-separated exclude list
                        excludeBlocksStr = tokens[++i];
                    } else {
                        flags.add(token.toLowerCase());
                    }
                }
            }
        }

        boolean includeAir = flags.contains("-a");
        boolean overwrite = flags.contains("-o");
        boolean isStructure = flags.contains("-b");
        boolean isBo4 = flags.contains("-bo4");
        boolean includeTiles = flags.contains("-t");
        ObjectType type = isBo4 ? ObjectType.BO4 : ObjectType.BO3;

        // Determine if global
        boolean isGlobal = presetName.equalsIgnoreCase("global");
        if (isGlobal) {
            presetName = OTG.getEngine().getDimensionPresetLoader().getDefaultDimensionPresetFolderName();
        }

        // Resolve the preset
        DimensionPreset preset = OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByShortNameOrFolderName(presetName);
        if (preset == null) {
            source.sendFailure(Component.literal("Could not find preset '" + presetName + "'."));
            return 0;
        }

        // Get WorldEdit selection
        CommandWorldAccessor accessor = OTGCommandRegistrar.getWorldAccessor();
        if (accessor == null) {
            source.sendFailure(Component.literal("Command world accessor not available."));
            return 0;
        }

        int[] selection = accessor.getWorldEditSelection(player);
        if (selection == null) {
            source.sendFailure(Component.literal("No WorldEdit selection found. Select a region with WorldEdit first (//wand, //pos1, //pos2)."));
            return 0;
        }

        Corner lowCorner = new Corner(
            Math.min(selection[0], selection[3]),
            Math.min(selection[1], selection[4]),
            Math.min(selection[2], selection[5])
        );
        Corner highCorner = new Corner(
            Math.max(selection[0], selection[3]),
            Math.max(selection[1], selection[4]),
            Math.max(selection[2], selection[5])
        );

        // Auto-detect if structure is needed based on region size
        int xLen = highCorner.x() - lowCorner.x();
        int zLen = highCorner.z() - lowCorner.z();
        if (type == ObjectType.BO3 && (xLen > 31 || zLen > 31)) {
            isStructure = true;
        } else if (type == ObjectType.BO4 && (xLen > 15 || zLen > 15)) {
            isStructure = true;
        }

        // Determine object path
        Path objectPath;
        if (isGlobal) {
            objectPath = OTG.getEngine().getGlobalObjectsFolder();
        } else {
            objectPath = preset.getFolder().resolve(Constants.OBJECTS_FOLDER);
        }
        // Fallback to WorldObjects if Objects doesn't exist
        if (!objectPath.toFile().exists()) {
            Path worldObjects = objectPath.resolveSibling("WorldObjects");
            if (worldObjects.toFile().exists()) {
                objectPath = worldObjects;
            } else {
                // Create the Objects folder
                objectPath.toFile().mkdirs();
            }
        }

        // Check for existing file (unless overwrite flag)
        if (!overwrite) {
            File existingFile = type.getObjectFilePathFromName(objectName, objectPath).toFile();
            if (existingFile.exists()) {
                source.sendFailure(Component.literal("File '" + objectName + "." + type.getType() + "' already exists. Use -o flag to overwrite."));
                return 0;
            }
        }

        // Create world gen region for reading blocks
        ServerLevel level = source.getLevel();
        int centerChunkX = (lowCorner.x() + highCorner.x()) / 2 >> 4;
        int centerChunkZ = (lowCorner.z() + highCorner.z()) / 2 >> 4;

        LocalWorldGenRegion worldGenRegion = accessor.createCommandRegion(level, centerChunkX, centerChunkZ);
        if (worldGenRegion == null) {
            source.sendFailure(Component.literal("Could not create world region. Is this an OTG world?"));
            return 0;
        }

        LocalNBTHelper nbtHelper = accessor.createNBTHelper();

        // Center is the midpoint at the lowest Y
        Corner center = new Corner(
            (highCorner.x() - lowCorner.x()) / 2 + lowCorner.x(),
            lowCorner.y(),
            (highCorner.z() - lowCorner.z()) / 2 + lowCorner.z()
        );

        // Resolve template and services
        CustomObjectManager customObjectManager = OTG.getEngine().getCustomObjectManager();
        IMaterialReader materialReader = OTG.getEngine().getDimensionPresetLoader().getMaterialReader();
        CustomObjectResourcesManager resourcesManager = OTG.getEngine().getCustomObjectResourcesManager();
        IModLoadedChecker modLoadedChecker = OTG.getEngine().getModLoadedChecker();
        Path otgRootFolder = OTG.getEngine().getOTGRootFolder();

        StructuredCustomObject templateObject;
        try {
            if (templateName != null && !templateName.isEmpty() && !templateName.equalsIgnoreCase("default")) {
                // Load a named template file
                templateObject = loadNamedTemplate(type, templateName, presetName, isGlobal,
                    otgRootFolder, customObjectManager, materialReader, resourcesManager, modLoadedChecker);
            } else {
                // Create a default template with default settings
                templateObject = createDefaultTemplate(type, objectName, objectPath, preset.getFolderName(),
                    otgRootFolder, customObjectManager, materialReader, resourcesManager, modLoadedChecker);
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to load template: " + e.getMessage()));
            OTGLog.error("Failed to load template for export", e);
            return 0;
        }

        if (templateObject == null || templateObject.getConfig() == null) {
            source.sendFailure(Component.literal("Failed to initialize template config"
                + (templateName != null && !templateName.isEmpty() ? " '" + templateName + "'" : "")
                + "."));
            return 0;
        }

        // Parse exclude blocks
        List<LocalMaterialData> excludes = new ArrayList<>();
        if (excludeBlocksStr != null && !excludeBlocksStr.isEmpty()) {
            String[] blockNames = excludeBlocksStr.split(",");
            for (String blockName : blockNames) {
                String trimmed = blockName.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    LocalMaterialData material = materialReader.readMaterial(trimmed);
                    if (material != null) {
                        excludes.add(material);
                    } else {
                        source.sendFailure(Component.literal("Unknown block in excludes: '" + trimmed + "'"));
                        return 0;
                    }
                } catch (InvalidConfigException e) {
                    source.sendFailure(Component.literal("Invalid block in excludes: '" + trimmed + "' — " + e.getMessage()));
                    return 0;
                }
            }
        }

        // Do the export
        try {
            StructuredCustomObject exportedObject = ObjectCreator.create(
                type,
                lowCorner,
                highCorner,
                center,
                null, // no center block
                objectName,
                includeAir,
                isStructure,
                false, // leaveIllegalLeaves
                objectPath,
                worldGenRegion,
                nbtHelper,
                null, // no extra blocks
                templateObject.getConfig(),
                preset.getFolderName(),
                otgRootFolder,
                customObjectManager,
                materialReader,
                resourcesManager,
                modLoadedChecker,
                excludes
            );

            if (exportedObject != null) {
                // Register the object for immediate use
                if (isGlobal) {
                    customObjectManager.registerGlobalObject(exportedObject, exportedObject.getConfig().getFile());
                } else {
                    customObjectManager.getGlobalObjects().addObjectToPreset(
                        preset.getFolderName(),
                        exportedObject.getName().toLowerCase(Locale.ROOT),
                        exportedObject.getConfig().getFile(),
                        exportedObject
                    );
                }

                String finalObjectName = objectName;
                boolean finalIsStructure = isStructure;
                String usedTemplateName = (templateName != null && !templateName.isEmpty() && !templateName.equalsIgnoreCase("default"))
                    ? templateName : null;
                source.sendSuccess(
                    () -> Component.literal("Exported " + type.getType() + " '" + finalObjectName + "'"
                        + (usedTemplateName != null ? " (template: " + usedTemplateName + ")" : "")
                        + (finalIsStructure ? " (as structure with branches)" : "")
                        + (!excludes.isEmpty() ? " (excluded " + excludes.size() + " block type(s))" : "")
                        + " [" + (xLen + 1) + "x" + (highCorner.y() - lowCorner.y() + 1) + "x" + (zLen + 1) + " blocks]"),
                    true
                );
                return 1;
            } else {
                source.sendFailure(Component.literal("Failed to create " + type.getType() + " '" + objectName + "'. Check the logs."));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error during export: " + e.getMessage()));
            OTGLog.error("Error during export command", e);
            return 0;
        }
    }

    /**
     * Loads a named template (.BO3Template / .BO4Template) from preset or global templates.
     * Mirrors the old Forge ExportCommand behavior.
     */
    private static StructuredCustomObject loadNamedTemplate(
        ObjectType type, String templateName, String presetFolderName, boolean isGlobal,
        Path otgRootFolder, CustomObjectManager customObjectManager, IMaterialReader materialReader,
        CustomObjectResourcesManager resourcesManager, IModLoadedChecker modLoadedChecker
    ) throws InvalidConfigException {
        // Try to find the template file in the preset's objects folder, or globally
        File templateFile = customObjectManager.getGlobalObjects().getTemplateFileForPreset(
            presetFolderName, templateName, otgRootFolder);

        // Load the template from the found file, or fall back to a dummy file with the template filename pattern
        File fileToLoad = templateFile != null ? templateFile : new File(type.getFileNameForTemplate(templateName));

        StructuredCustomObject template = (StructuredCustomObject) customObjectManager.getObjectLoaders()
            .get(type.getType().toLowerCase())
            .loadFromFile(templateName, fileToLoad);

        if (template == null) {
            return null;
        }

        if (!template.onEnable(presetFolderName, otgRootFolder, customObjectManager, materialReader, resourcesManager, modLoadedChecker)) {
            return null;
        }

        return template;
    }

    /**
     * Creates a default template BO3/BO4 for use when no template file is specified.
     * The template provides default config values that ObjectCreator.makeNewConfig will clone.
     */
    private static StructuredCustomObject createDefaultTemplate(
        ObjectType type, String objectName, Path objectPath,
        String presetFolderName, Path otgRootFolder,
        CustomObjectManager customObjectManager, IMaterialReader materialReader,
        CustomObjectResourcesManager resourcesManager, IModLoadedChecker modLoadedChecker
    ) throws InvalidConfigException {
        // Create a BO3/BO4 from a non-existent file path — this gives us default settings
        String dummyName = objectName + "_template_tmp";
        Path dummyPath = objectPath.resolve(dummyName + "." + type.getType());
        File dummyFile = dummyPath.toFile();

        // Load a BO3/BO4 from the dummy file (which doesn't exist, so all settings are defaults)
        StructuredCustomObject template = (StructuredCustomObject) customObjectManager.getObjectLoaders()
            .get(type.getType().toLowerCase())
            .loadFromFile(dummyName, dummyFile);

        if (template == null) {
            return null;
        }

        // Initialize the template with default settings
        template.onEnable(presetFolderName, otgRootFolder, customObjectManager, materialReader, resourcesManager, modLoadedChecker);
        return template;
    }
}
