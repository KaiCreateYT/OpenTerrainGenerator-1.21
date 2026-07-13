package com.pg85.otg.shared.registry;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.minecraft.OTGDimensionType;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import com.pg85.otg.shared.i18n.OTGTranslations;

import java.util.*;

/**
 * Registers WorldPreset YAML configs as MC WorldPresets in the registry.
 * Called from OTGRegistryHelper.loadOTGPresets() after individual DimensionPresets
 * are registered.
 */
public class WorldPresetRegistrar {

    /**
     * Registers WorldPreset YAML configs as MC WorldPresets.
     *
     * @param configs        loaded WorldPresetConfig objects from YAML files
     * @param loadedPresets  map of presetFolderName → DimensionPreset (already loaded)
     * @param loaders        registry data loaders (for dimension types, noise, biomes, etc.)
     * @param factory        platform-specific chunk generator factory
     */
    public static void register(
            List<WorldPresetConfig> configs,
            Map<String, DimensionPreset> loadedPresets,
            List<RegistryDataLoader.Loader<?>> loaders,
            OTGRegistryHelper.ChunkGeneratorFactory factory
    ) {
        WritableRegistry<WorldPreset> worldPresets = OTGRegistryHelper.getRegistry(loaders, Registries.WORLD_PRESET);
        if (worldPresets == null) {
            OTGLog.error("Could not find world preset registry for WorldPreset YAML registration");
            return;
        }

        HolderGetter<DimensionType> dimensionTypes = OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseSettings = OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.NOISE_SETTINGS);
        Registry<Biome> biomeRegistry = OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.BIOME);

        Set<String> registeredNames = new HashSet<>();

        for (WorldPresetConfig config : configs) {
            if (config.DisplayName == null || config.DisplayName.isBlank()) {
                OTGLog.warn("WorldPreset YAML has no DisplayName, skipping registration");
                continue;
            }

            String normalizedId = normalizeId(config.DisplayName);
            if (registeredNames.contains(normalizedId)) {
                OTGLog.warn("Duplicate WorldPreset DisplayName '{}', skipping", config.DisplayName);
                continue;
            }

            Map<ResourceKey<LevelStem>, LevelStem> levelStems = buildLevelStems(
                config, loadedPresets, loaders, factory, dimensionTypes, noiseSettings, biomeRegistry);

            if (levelStems.isEmpty()) {
                OTGLog.error("WorldPreset '{}' produced no valid dimensions, skipping", config.DisplayName);
                continue;
            }

            WorldPreset preset = new WorldPreset(levelStems);
            ResourceKey<WorldPreset> key = ResourceKey.create(
                Registries.WORLD_PRESET,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, normalizedId));

            try {
                worldPresets.register(key, preset, RegistrationInfo.BUILT_IN);
                OTGTranslations.put(
                    "generator." + Constants.MOD_ID_SHORT + "." + normalizedId,
                    config.DisplayName);
                registeredNames.add(normalizedId);
                OTGLog.info("Registered WorldPreset '{}' as otg:{}", config.DisplayName, normalizedId);
            } catch (IllegalStateException e) {
                OTGLog.warn("WorldPreset '{}' already registered, skipping", config.DisplayName);
            }
        }
    }

    private static Map<ResourceKey<LevelStem>, LevelStem> buildLevelStems(
            WorldPresetConfig config,
            Map<String, DimensionPreset> loadedPresets,
            List<RegistryDataLoader.Loader<?>> loaders,
            OTGRegistryHelper.ChunkGeneratorFactory factory,
            HolderGetter<DimensionType> dimensionTypes,
            HolderGetter<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();

        // Overworld — vanilla only when NonOTGWorldType is set; otherwise always OTG terrain.
        if (isOverworldExplicitlyVanilla(config)) {
            LevelStem vanillaOverworld = createVanillaLevelStem(
                    LevelStem.OVERWORLD, loaders, dimensionTypes, noiseSettings, biomeRegistry);
            if (vanillaOverworld != null) {
                stems.put(LevelStem.OVERWORLD, vanillaOverworld);
            }
        } else {
            String requested = (config.Overworld != null && config.Overworld.PresetFolderName != null
                    && !config.Overworld.PresetFolderName.isBlank())
                    ? config.Overworld.PresetFolderName : null;
            String resolved = resolveOverworldDimensionPresetFolder(config, loadedPresets);
            if (resolved != null) {
                LevelStem stem = createOTGLevelStem(
                        resolved, LevelStem.OVERWORLD,
                        loadedPresets, factory, dimensionTypes, noiseSettings, biomeRegistry);
                if (stem != null) {
                    stems.put(LevelStem.OVERWORLD, stem);
                    if (requested != null && !requested.equals(resolved)) {
                        OTGLog.warn(
                                "WorldPreset '{}' overworld preset '{}' is missing or invalid; using '{}' instead.",
                                config.DisplayName, requested, resolved);
                    } else if (requested == null) {
                        OTGLog.info(
                                "WorldPreset '{}' has no overworld DimensionPreset in YAML; using '{}'.",
                                config.DisplayName, resolved);
                    }
                } else {
                    OTGLog.error("WorldPreset '{}' failed to build OTG overworld for preset '{}'.",
                            config.DisplayName, resolved);
                }
            } else {
                OTGLog.error(
                        "WorldPreset '{}' needs an OTG overworld but no DimensionPresets are loaded.",
                        config.DisplayName);
            }
        }

        // Nether
        if (config.Nether != null && config.Nether.PresetFolderName != null) {
            LevelStem stem = createOTGLevelStem(
                config.Nether.PresetFolderName, LevelStem.NETHER,
                loadedPresets, factory, dimensionTypes, noiseSettings, biomeRegistry);
            if (stem != null) {
                stems.put(LevelStem.NETHER, stem);
            }
        } else {
            LevelStem vanillaNether = createVanillaLevelStem(
                LevelStem.NETHER, loaders, dimensionTypes, noiseSettings, biomeRegistry);
            if (vanillaNether != null) {
                stems.put(LevelStem.NETHER, vanillaNether);
            }
        }

        // End
        if (config.End != null && config.End.PresetFolderName != null) {
            LevelStem stem = createOTGLevelStem(
                config.End.PresetFolderName, LevelStem.END,
                loadedPresets, factory, dimensionTypes, noiseSettings, biomeRegistry);
            if (stem != null) {
                stems.put(LevelStem.END, stem);
            }
        } else {
            LevelStem vanillaEnd = createVanillaLevelStem(
                LevelStem.END, loaders, dimensionTypes, noiseSettings, biomeRegistry);
            if (vanillaEnd != null) {
                stems.put(LevelStem.END, vanillaEnd);
            }
        }

        // Custom dimensions
        if (config.Dimensions != null) {
            Set<String> seenDimKeys = new HashSet<>();
            for (WorldPresetConfig.OTGDimension dim : config.Dimensions) {
                if (dim.PresetFolderName == null) continue;
                String normalizedName = dim.PresetFolderName.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_.-]", "_");
                if (!seenDimKeys.add(normalizedName)) {
                    OTGLog.warn("WorldPreset has duplicate custom dimension '{}', skipping duplicate", normalizedName);
                    continue;
                }
                ResourceKey<LevelStem> key = ResourceKey.create(
                    Registries.LEVEL_STEM,
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, normalizedName));
                LevelStem stem = createOTGLevelStem(
                    dim.PresetFolderName, key,
                    loadedPresets, factory, dimensionTypes, noiseSettings, biomeRegistry);
                if (stem != null) {
                    stems.put(key, stem);
                }
            }
        }

        return stems;
    }

    public static boolean isOverworldExplicitlyVanilla(WorldPresetConfig config) {
        return config != null && config.Overworld != null
                && config.Overworld.NonOTGWorldType != null
                && !config.Overworld.NonOTGWorldType.isBlank();
    }

    /**
     * Folder name of the DimensionPreset used for the overworld when this WorldPreset is selected.
     * Returns null if the overworld is explicitly vanilla ({@link #isOverworldExplicitlyVanilla}) or no presets exist.
     */
    public static String resolveOverworldDimensionPresetFolder(
            WorldPresetConfig config,
            Map<String, DimensionPreset> loadedPresets) {
        if (isOverworldExplicitlyVanilla(config)) {
            return null;
        }
        if (loadedPresets == null || loadedPresets.isEmpty()) {
            return null;
        }
        String requested = null;
        if (config != null && config.Overworld != null && config.Overworld.PresetFolderName != null) {
            String trimmed = config.Overworld.PresetFolderName.trim();
            if (!trimmed.isEmpty()) {
                requested = config.Overworld.PresetFolderName;
            }
        }
        if (requested != null && loadedPresets.containsKey(requested)) {
            return requested;
        }
        return defaultOverworldPresetFolderName(loadedPresets);
    }

    /**
     * Folder name of the DimensionPreset to use for the overworld when YAML omits one or names a missing preset.
     * Prefers {@link Constants#DEFAULT_PRESET_NAME}, otherwise the first loaded preset (sorted for stability).
     */
    private static String defaultOverworldPresetFolderName(Map<String, DimensionPreset> loadedPresets) {
        if (loadedPresets == null || loadedPresets.isEmpty()) {
            return null;
        }
        if (loadedPresets.containsKey(Constants.DEFAULT_PRESET_NAME)) {
            return Constants.DEFAULT_PRESET_NAME;
        }
        return loadedPresets.keySet().stream().sorted().findFirst().orElse(null);
    }

    private static LevelStem createOTGLevelStem(
            String presetFolderName,
            ResourceKey<LevelStem> stemKey,
            Map<String, DimensionPreset> loadedPresets,
            OTGRegistryHelper.ChunkGeneratorFactory factory,
            HolderGetter<DimensionType> dimensionTypes,
            HolderGetter<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        DimensionPreset preset = loadedPresets.get(presetFolderName);
        if (preset == null) {
            OTGLog.error("WorldPreset references unknown DimensionPreset '{}', skipping dimension", presetFolderName);
            return null;
        }

        // Dimension type: always respect the preset's own config, regardless of which slot it's in
        ResourceKey<DimensionType> dimTypeKey = switch (preset.getConfig().getDimensionSettings().getDimensionType()) {
            case OTG -> ResourceKey.create(Registries.DIMENSION_TYPE,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName()));
            case OVERWORLD -> BuiltinDimensionTypes.OVERWORLD;
            case NETHER -> BuiltinDimensionTypes.NETHER;
            case END -> BuiltinDimensionTypes.END;
        };

        Optional<Holder.Reference<DimensionType>> dimType = dimensionTypes.get(dimTypeKey);
        if (dimType.isEmpty()) {
            OTGLog.error("DimensionType {} not found for preset {}", dimTypeKey.location(), presetFolderName);
            return null;
        }

        // Noise key: always use otg:<name> — registerNoiseGenSettings() registers ALL OTG presets there
        ResourceKey<NoiseGeneratorSettings> noiseKey = ResourceKey.create(Registries.NOISE_SETTINGS,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName()));

        Optional<Holder.Reference<NoiseGeneratorSettings>> noiseRef = noiseSettings.get(noiseKey);
        if (noiseRef.isEmpty()) {
            OTGLog.error("NoiseGeneratorSettings {} not found for preset {}", noiseKey.location(), presetFolderName);
            return null;
        }

        ChunkGenerator generator = factory.create(presetFolderName, noiseRef.get(), biomeRegistry);
        return new LevelStem(dimType.get(), generator);
    }

    /**
     * Creates a vanilla LevelStem for overworld/nether/end.
     * Mirrors the vanilla dimension creation logic in OTGRegistryHelper.createLevelStems().
     */
    private static LevelStem createVanillaLevelStem(
            ResourceKey<LevelStem> stemKey,
            List<RegistryDataLoader.Loader<?>> loaders,
            HolderGetter<DimensionType> dimensionTypes,
            HolderGetter<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        ResourceKey<DimensionType> dimTypeKey;
        ChunkGenerator chunkGenerator;

        if (stemKey.equals(LevelStem.OVERWORLD)) {
            dimTypeKey = BuiltinDimensionTypes.OVERWORLD;
            Holder<MultiNoiseBiomeSourceParameterList> biomeSource =
                OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            Holder<NoiseGeneratorSettings> noise = noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            chunkGenerator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(biomeSource), noise);
        } else if (stemKey.equals(LevelStem.NETHER)) {
            dimTypeKey = BuiltinDimensionTypes.NETHER;
            Holder<MultiNoiseBiomeSourceParameterList> biomeSource =
                OTGRegistryHelper.getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
            Holder<NoiseGeneratorSettings> noise = noiseSettings.getOrThrow(NoiseGeneratorSettings.NETHER);
            chunkGenerator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(biomeSource), noise);
        } else if (stemKey.equals(LevelStem.END)) {
            dimTypeKey = BuiltinDimensionTypes.END;
            Holder<NoiseGeneratorSettings> noise = noiseSettings.getOrThrow(NoiseGeneratorSettings.END);
            chunkGenerator = new NoiseBasedChunkGenerator(
                TheEndBiomeSource.create(biomeRegistry), noise);
        } else {
            OTGLog.error("Cannot create vanilla LevelStem for non-vanilla dimension {}", stemKey.location());
            return null;
        }

        Optional<Holder.Reference<DimensionType>> dimType = dimensionTypes.get(dimTypeKey);
        if (dimType.isEmpty()) {
            OTGLog.error("DimensionType {} not found for vanilla dimension", dimTypeKey.location());
            return null;
        }

        return new LevelStem(dimType.get(), chunkGenerator);
    }

    static String normalizeId(String displayName) {
        return displayName.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.-]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }
}
