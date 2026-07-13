package com.pg85.otg.shared.registry;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.settings.preset.*;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.loader.WorldPresetConfigLoader;
import com.pg85.otg.platform.noise.OTGNoiseParamRegistry;
import com.pg85.otg.platform.noise.OTGNoiseRouterBuilder;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.shared.biome.SharedDimensionPresetBiomeLoader;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.minecraft.OTGDimensionType;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.NotNull;

import com.pg85.otg.shared.i18n.OTGTranslations;

import java.util.*;
import java.util.function.Function;

/**
 * Shared utility methods extracted from platform-specific RegistryLoaderMixin classes.
 * All methods are static and platform-agnostic.
 */
public final class OTGRegistryHelper {

    private OTGRegistryHelper() {}

    // --- Functional interfaces for platform-specific operations ---

    @FunctionalInterface
    public interface ChunkGeneratorFactory {
        ChunkGenerator create(String presetFolder, Holder<NoiseGeneratorSettings> noiseRef, Registry<Biome> biomeReg);
    }

    // --- Registry lookup ---

    @SuppressWarnings("unchecked")
    public static <T> WritableRegistry<T> getRegistry(List<RegistryDataLoader.Loader<?>> loaders, ResourceKey<Registry<T>> key) {
        for (RegistryDataLoader.Loader<?> entry : loaders) {
            if (entry.registry().key().equals(key)) {
                return (WritableRegistry<T>) entry.registry();
            }
        }
        return null;
    }

    public static <T> WritableRegistry<T> getRegistryOrThrow(List<RegistryDataLoader.Loader<?>> loaders, ResourceKey<Registry<T>> key) {
        WritableRegistry<T> registry = getRegistry(loaders, key);
        if (registry == null) {
            throw new RuntimeException("Could not find registry for key " + key.location());
        }
        return registry;
    }

    // --- Noise ---

    public static @NotNull NoiseRouter getZeroNoiseRouter() {
        return new NoiseRouter(
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero()
        );
    }

    // --- Dimension types ---

    public static @NotNull DimensionType getDimensionType(DimensionSettings settings) {
        return new DimensionType(
                settings.getFixedTime(),
                settings.isHasSkyLight(),
                settings.isHasCeiling(),
                settings.isUltraWarm(),
                settings.isNatural(),
                settings.getCoordinateScale(),
                settings.isBedWorks(),
                settings.isRespawnAnchorWorks(),
                settings.getMinY(),
                settings.getHeight(),
                settings.getLogicalHeight(),
                TagKey.create(Registries.BLOCK, ResourceLocation.parse(settings.getInfiniburn())),
                ResourceLocation.parse(settings.getEffectsLocation().toLowerCase(Locale.ROOT)),
                (float) settings.getAmbientLight(),
                new DimensionType.MonsterSettings(
                        settings.isPiglinSafe(),
                        settings.isHasRaids(),
                        UniformInt.of(
                                settings.getMonsterSpawnLightVariationMin(),
                                settings.getMonsterSpawnLightVariationMax()
                        ),
                        settings.getMonsterSpawnLightLimit()
                )
        );
    }

    public static @NotNull HashMap<DimensionPreset, ResourceKey<DimensionType>> registerDimensionTypes(List<RegistryDataLoader.Loader<?>> loaders) {
        var map = new HashMap<DimensionPreset, ResourceKey<DimensionType>>();
        for (DimensionPreset preset : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
            OTGDimensionType otgDimensionType = preset.getConfig().getDimensionSettings().getDimensionType();
            ResourceKey<DimensionType> dimensionKey = switch (otgDimensionType) {
                case OVERWORLD -> BuiltinDimensionTypes.OVERWORLD;
                case NETHER -> BuiltinDimensionTypes.NETHER;
                case END -> BuiltinDimensionTypes.END;
                case OTG -> {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName().toLowerCase(Locale.ROOT));
                    ResourceKey<DimensionType> dimensionTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, id);
                    WritableRegistry<DimensionType> dimensionTypes = getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE);
                    Optional<Holder.Reference<DimensionType>> existingD = dimensionTypes.get(dimensionTypeKey);
                    if (existingD.isEmpty()) {
                        DimensionType dimensionType = getDimensionType(preset.getConfig().getDimensionSettings());
                        dimensionTypes.register(dimensionTypeKey, dimensionType, RegistrationInfo.BUILT_IN);
                        OTGLog.info("Registered dimension type: {}", dimensionTypeKey.location());
                    } else {
                        OTGLog.info("Dimension type {} already registered, skipping", dimensionTypeKey.location());
                    }
                    yield dimensionTypeKey;
                }
            };
            map.put(preset, dimensionKey);
        }
        return map;
    }

    // --- World presets ---

    public static Holder.Reference<WorldPreset> registerWorldPresets(
            DimensionPreset preset,
            WritableRegistry<WorldPreset> worldPresets,
            Map<ResourceKey<LevelStem>, LevelStem> levelStems
    ) {
        WorldPreset worldPreset = new WorldPreset(levelStems);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName().toLowerCase(Locale.ROOT));
        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, id);
        Optional<Holder.Reference<WorldPreset>> existingWp = worldPresets.get(key);
        if (existingWp.isPresent()) {
            return existingWp.get();
        }
        Holder.Reference<WorldPreset> ref = worldPresets.register(key, worldPreset, RegistrationInfo.BUILT_IN);
        OTGTranslations.put(
            "generator." + Constants.MOD_ID_SHORT + "." + preset.getRegistryName().toLowerCase(Locale.ROOT),
            preset.getConfig().getPresetInfo().getDisplayName());
        return ref;
    }

    // --- Biome registration ---

    public static void registerBiomes(List<RegistryDataLoader.Loader<?>> loaders) {
        if (OTG.getEngine().getPluginConfig().getDeveloperModeEnabled()) {
            OTG.getEngine().getCustomObjectManager().reloadCustomObjectFiles();
            OTG.getEngine().getDimensionPresetLoader().loadDimensionPresetsFromDisk();
        }

        // Override bootstrap HolderGetters with real registry lookups.
        // The bootstrap context (BiomeDataMixin) creates unbound holder references
        // that never get bound because they belong to a transient builder, not the
        // materialized registry. Using the actual loaded registries here gives us
        // holders that ARE (or will be) properly bound when the registry freezes.
        WritableRegistry<net.minecraft.world.level.levelgen.placement.PlacedFeature> pfRegistry =
                getRegistry(loaders, Registries.PLACED_FEATURE);
        if (pfRegistry != null) {
            SharedDimensionPresetBiomeLoader.PLACED_FEATURE_HOLDER = pfRegistry;
        }
        WritableRegistry<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>> carverRegistry =
                getRegistry(loaders, Registries.CONFIGURED_CARVER);
        if (carverRegistry != null) {
            SharedDimensionPresetBiomeLoader.CONFIGURED_CARVER_HOLDER = carverRegistry;
        }

        SharedDimensionPresetBiomeLoader.BIOME_DATA_INITIALIZED = true;

        SharedDimensionPresetBiomeLoader loader = (SharedDimensionPresetBiomeLoader) OTG.getEngine().getDimensionPresetLoader();
        WritableRegistry<Biome> biomeWritableRegistry = getRegistry(loaders, Registries.BIOME);
        if (biomeWritableRegistry == null) {
            OTGLog.error("Could not find biome registry");
            return;
        }
        loader.registerBiomes(biomeWritableRegistry);
    }

    // --- Noise generator settings ---

    public static void registerNoiseGenSettings(
            DimensionPreset preset,
            List<RegistryDataLoader.Loader<?>> loaders,
            Function<LocalMaterialData, BlockState> toBlockState) {
        DimensionPresetSettings presetSettings = preset.getConfig();
        DimensionSettings dimensionSettings = presetSettings.getDimensionSettings();
        BlockSettings blockSettings = presetSettings.getBlockSettings();
        ResourceSettings resourceSettings = presetSettings.getResourceSettings();
        boolean modernCaves = presetSettings.getCarverSettings().isUseModernCaves();

        NoiseGeneratorSettings ngs;
        if (modernCaves) {
            WritableRegistry<NormalNoise.NoiseParameters> noiseRegistry = getRegistry(loaders, Registries.NOISE);
            if (noiseRegistry != null) {
                OTGNoiseParamRegistry.registerNoiseParameters(presetSettings, noiseRegistry);
            }
            HolderGetter<DensityFunction> densityGetter = getRegistryOrThrow(loaders, Registries.DENSITY_FUNCTION);
            HolderGetter<NormalNoise.NoiseParameters> noiseGetter = getRegistryOrThrow(loaders, Registries.NOISE);
            OTGNoiseRouterBuilder routerBuilder = new OTGNoiseRouterBuilder(densityGetter, noiseGetter);
            String presetName = preset.getRegistryName().toLowerCase(Locale.ROOT);
            NoiseRouter router = routerBuilder.buildWithSettings(presetSettings.getNoiseCaveSettings(), presetName, false, false);

            ngs = new NoiseGeneratorSettings(
                    new NoiseSettings(dimensionSettings.getMinY(), dimensionSettings.getHeight(), 1, 2),
                    toBlockState.apply(blockSettings.getDefaultStoneBlock()),
                    toBlockState.apply(blockSettings.getWaterBlock()),
                    router,
                    SurfaceRuleData.overworld(),
                    new OverworldBiomeBuilder().spawnTarget(),
                    63,
                    !resourceSettings.isDisableOreGen(),
                    true,
                    modernCaves,
                    false
            );
        } else {
            ngs = new NoiseGeneratorSettings(
                    new NoiseSettings(dimensionSettings.getMinY(), dimensionSettings.getHeight(), 1, 2),
                    toBlockState.apply(blockSettings.getDefaultStoneBlock()),
                    toBlockState.apply(blockSettings.getWaterBlock()),
                    getZeroNoiseRouter(),
                    SurfaceRuleData.overworld(),
                    new OverworldBiomeBuilder().spawnTarget(),
                    63,
                    !resourceSettings.isDisableOreGen(),
                    true,
                    false,
                    false
            );
        }

        WritableRegistry<NoiseGeneratorSettings> registry = getRegistry(loaders, Registries.NOISE_SETTINGS);
        if (registry == null) {
            throw new RuntimeException("Could not find noise settings registry");
        }
        ResourceKey<NoiseGeneratorSettings> key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getRegistryName()));
        if (registry.get(key).isEmpty()) {
            registry.register(key, ngs, RegistrationInfo.BUILT_IN);
            OTGLog.info("Registered noise generator settings: {}", key.location());
        } else {
            OTGLog.info("Noise generator settings {} already registered, skipping", key.location());
        }
    }

    // --- Level stems ---

    public static Map<ResourceKey<LevelStem>, LevelStem> createLevelStems(
            DimensionPreset preset,
            List<RegistryDataLoader.Loader<?>> loaders,
            ChunkGeneratorFactory factory
    ) {
        var dimensionNames = preset.getDimensionNames();
        int counter = 0;

        Map<ResourceKey<LevelStem>, LevelStem> levelStems = new HashMap<>();
        HolderGetter<DimensionType> dimensionHolders = getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseHolders = getRegistryOrThrow(loaders, Registries.NOISE_SETTINGS);

        for (String dim : dimensionNames) {
            ResourceKey<LevelStem> key;
            LevelStem levelStem;
            if (counter == 0) {
                key = LevelStem.OVERWORLD;
            } else if (counter == 1) {
                key = LevelStem.NETHER;
            } else if (counter == 2) {
                key = LevelStem.END;
            } else {
                key = ResourceKey.create(Registries.LEVEL_STEM, ResourceLocation.parse(dim));
            }

            ChunkGenerator chunkGenerator;

            if (dim.startsWith(Constants.MOD_ID_SHORT)) {
                DimensionPreset dimPreset = null;
                for (DimensionPreset p : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
                    if (dim.equals(Constants.MOD_ID_SHORT + ":" + p.getRegistryName())) {
                        dimPreset = p;
                        break;
                    }
                }
                if (dimPreset == null) {
                    OTGLog.error("Could not find preset for dimension {}", dim);
                    continue;
                }

                ResourceKey<DimensionType> dimensionKey = switch (dimPreset.getConfig().getDimensionSettings().getDimensionType()) {
                    case OTG -> ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, dimPreset.getRegistryName()));
                    case OVERWORLD -> BuiltinDimensionTypes.OVERWORLD;
                    case NETHER -> BuiltinDimensionTypes.NETHER;
                    case END -> BuiltinDimensionTypes.END;
                };

                // Always use otg:<name> — registerNoiseGenSettings() registers ALL OTG presets under this key
                ResourceKey<NoiseGeneratorSettings> noiseKey = ResourceKey.create(
                    Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, dimPreset.getRegistryName()));

                Holder.Reference<DimensionType> dimensionReference = dimensionHolders.getOrThrow(dimensionKey);
                if (!dimensionReference.isBound()) {
                    OTGLog.error("Dimension reference for dimension {} is not bound", dim);
                }

                Holder.Reference<NoiseGeneratorSettings> noiseReference = noiseHolders.getOrThrow(noiseKey);
                if (!noiseReference.isBound()) {
                    OTGLog.error("Noise reference for dimension {} is not bound", dim);
                }

                Registry<Biome> biomeRegistry = getRegistryOrThrow(loaders, Registries.BIOME);

                chunkGenerator = factory.create(preset.getFolderName(), noiseReference, biomeRegistry);
                levelStem = new LevelStem(
                        dimensionReference,
                        chunkGenerator
                );
            } else {
                ResourceKey<DimensionType> dimensionKey = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse(dim));
                Optional<Holder.Reference<DimensionType>> dimensionTypeHolder = dimensionHolders.get(dimensionKey);
                if (dimensionTypeHolder.isEmpty()) {
                    OTGLog.error("Could not find dimension reference for dimension {}", dimensionKey.location());
                    continue;
                }
                if (!dimensionTypeHolder.get().isBound()) {
                    OTGLog.error("Dimension reference for dimension {} is not bound", dimensionKey.location());
                    continue;
                }

                if (dimensionKey == BuiltinDimensionTypes.OVERWORLD) {
                    Holder<MultiNoiseBiomeSourceParameterList> overworldBiomeSource = getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
                    if (!overworldBiomeSource.isBound()) {
                        OTGLog.error("Overworld biome source is not bound");
                    }
                    Holder<NoiseGeneratorSettings> overworldNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            MultiNoiseBiomeSource.createFromPreset(overworldBiomeSource),
                            overworldNoise
                    );
                } else if (dimensionKey == BuiltinDimensionTypes.NETHER) {
                    Holder<MultiNoiseBiomeSourceParameterList> netherBiomeSource = getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
                    if (!netherBiomeSource.isBound()) {
                        OTGLog.error("Nether biome source is not bound");
                    }
                    Holder<NoiseGeneratorSettings> netherNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.NETHER);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            MultiNoiseBiomeSource.createFromPreset(netherBiomeSource),
                            netherNoise
                    );
                } else if (dimensionKey == BuiltinDimensionTypes.END) {
                    var biomes = getRegistryOrThrow(loaders, Registries.BIOME);
                    Holder<NoiseGeneratorSettings> endNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.END);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            TheEndBiomeSource.create(biomes),
                            endNoise
                    );
                } else {
                    OTGLog.error("Non-OTG dimension {} not yet supported", dimensionKey.location());
                    continue;
                }

                levelStem = new LevelStem(
                        dimensionTypeHolder.get(),
                        chunkGenerator
                );
            }
            levelStems.put(key, levelStem);
            counter++;
        }

        return levelStems;
    }

    // --- Main entry point (called from platform Mixin) ---

    public static void loadOTGPresets(
            List<RegistryDataLoader.Loader<?>> loaders,
            ChunkGeneratorFactory chunkGeneratorFactory,
            Function<LocalMaterialData, BlockState> toBlockState
    ) {
        OTGLog.info("Registering the following OTG presets: {}", OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets());

        // Check if biome registry is available (may not be in world creation flow)
        if (getRegistry(loaders, Registries.BIOME) != null) {
            registerBiomes(loaders);
        } else {
            OTGLog.info("Biome registry not available, skipping biome registration");
        }

        HashMap<DimensionPreset, ResourceKey<DimensionType>> dimensionTypes = null;
        try {
            dimensionTypes = registerDimensionTypes(loaders);
        } catch (Exception e) {
            OTGLog.warn("Could not register dimension types: {}", e.getMessage());
        }

        if (dimensionTypes != null) {
            for (DimensionPreset preset : dimensionTypes.keySet()) {
                try {
                    registerNoiseGenSettings(preset, loaders, toBlockState);
                } catch (Exception e) {
                    OTGLog.warn("Could not register noise gen settings for {}: {}", preset.getRegistryName(), e.getMessage());
                }
            }

            WritableRegistry<WorldPreset> worldPresets = getRegistry(loaders, Registries.WORLD_PRESET);
            if (worldPresets != null) {
                for (DimensionPreset preset : dimensionTypes.keySet()) {
                    if (!preset.getConfig().getPresetInfo().isSelectableInWorldCreation()) {
                        continue;
                    }
                    OTGLog.info("Registering world preset: {}", dimensionTypes.get(preset).location());
                    Map<ResourceKey<LevelStem>, LevelStem> levelStems = createLevelStems(preset, loaders, chunkGeneratorFactory);
                    registerWorldPresets(preset, worldPresets, levelStems);
                }
            }
        }

        // Always try to register YAML WorldPresets if the registry is available
        WritableRegistry<WorldPreset> wpReg = getRegistry(loaders, Registries.WORLD_PRESET);
        if (wpReg != null) {
            List<WorldPresetConfig> worldPresetConfigs = WorldPresetConfigLoader.loadAll(
                OTG.getEngine().getOTGRootFolder());
            if (!worldPresetConfigs.isEmpty()) {
                Map<String, DimensionPreset> presetMap = new HashMap<>();
                for (DimensionPreset p : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
                    presetMap.put(p.getFolderName(), p);
                }
                WorldPresetRegistrar.register(worldPresetConfigs, presetMap, loaders, chunkGeneratorFactory);
            }

            // After all OTG presets are registered, add them to WorldPresetTags.NORMAL and EXTENDED
            // so they appear in the world creation GUI (MC 1.21.5+ filters by these tags).
            addOTGPresetsToWorldPresetTags(wpReg);
        } else {
            OTGLog.warn("World preset registry not available, skipping YAML preset registration");
        }
    }

    private static void addOTGPresetsToWorldPresetTags(WritableRegistry<WorldPreset> registry) {
        // WARNING: Do NOT call registry.get(TagKey) here — MappedRegistry.freeze() hasn't been
        // called yet, so allTags is still TagSet.unbound() and get(TagKey) would throw.
        // Use registry.listElements() + bindTag() instead (bindTag uses frozenTags, not allTags).

        List<Holder<WorldPreset>> allPresets = new ArrayList<>();
        registry.listElements().forEach(ref -> allPresets.add(ref));
        if (allPresets.isEmpty()) return;

        registry.bindTag(WorldPresetTags.NORMAL, allPresets);
        registry.bindTag(WorldPresetTags.EXTENDED, allPresets);
        OTGLog.info("Bound all {} world presets to WorldPresetTags.NORMAL and EXTENDED", allPresets.size());
    }
}
