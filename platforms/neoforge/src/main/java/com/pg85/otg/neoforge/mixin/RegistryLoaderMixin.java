package com.pg85.otg.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistrationInfo;
import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.*;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.neoforge.biome.LegacyNeoForgeBiomeLoader;
import com.pg85.otg.neoforge.biome.OTGNeoForgeBiomeProvider;
import com.pg85.otg.neoforge.gen.OTGNeoForgeChunkGenerator;
import com.pg85.otg.neoforge.materials.NeoForgeMaterialData;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.minecraft.OTGDimensionType;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import com.pg85.otg.platform.noise.OTGNoiseRouterBuilder;
import com.pg85.otg.platform.noise.OTGNoiseParamRegistry;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(RegistryDataLoader.class)
@SuppressWarnings("unused") // Mixins are by nature unused
public class RegistryLoaderMixin {
    @Inject(
            method = "load(" +
                    "Lnet/minecraft/resources/RegistryDataLoader$LoadingFunction;" +
                    "Lnet/minecraft/core/RegistryAccess;" +
                    "Ljava/util/List;" +
                    ")Lnet/minecraft/core/RegistryAccess$Frozen;",
            at = @At(
                value = "INVOKE",
                target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
                ordinal = 1
            )
    )
    @SuppressWarnings("rawtypes")
    private static void loadOTGPresets(
            RegistryDataLoader.LoadingFunction loadingFunction,
            RegistryAccess registryAccess,
            List<RegistryDataLoader.RegistryData<?>> list,
            CallbackInfoReturnable ci,
            @Local(ordinal = 1) List<RegistryDataLoader.Loader<?>> loaders
    ) {
        if (getRegistry(loaders, Registries.DIMENSION) != null) {
            return;
        }

        if (getRegistry(loaders, Registries.NOISE_SETTINGS) == null) {
            return;
        }

        OTGLog.getLogger().info("Registering the following OTG presets: %s", OTG.getEngine().getPresetLoader().getAllPresets());

        registerBiomes(loaders);

        HashMap<Preset, ResourceKey<DimensionType>> dimensionTypes = registerDimensionTypes(loaders);

        for (Preset preset : dimensionTypes.keySet()) {
            registerNoiseGenSettings(preset, loaders, registryAccess);
        }

        WritableRegistry<WorldPreset> worldPresets = getRegistry(loaders, Registries.WORLD_PRESET);
        if (worldPresets == null) {
            OTGLog.getLogger().error("Could not find world preset registry");
            return;
        }

        for (Preset preset : dimensionTypes.keySet()) {
            if (!preset.getPresetConfig().getPresetInfo().isSelectableInWorldCreation()) {
                continue;
            }

            OTGLog.getLogger().info("Registering world preset: %s", dimensionTypes.get(preset).location());

            Map<ResourceKey<LevelStem>, LevelStem> levelStems = createLevelStems(preset, loaders);

            registerWorldPresets(preset, worldPresets, levelStems);
        }
    }

    private static void registerBiomes(List<RegistryDataLoader.Loader<?>> loaders) {
        if (OTG.getEngine().getPluginConfig().getDeveloperModeEnabled()) {
            OTG.getEngine().getCustomObjectManager().reloadCustomObjectFiles();
            OTG.getEngine().getPresetLoader().loadPresetsFromDisk();
        }

        // Override bootstrap HolderGetters with real registry lookups.
        // The bootstrap context (BiomeDataMixin) creates unbound holder references
        // that never get bound because they belong to a transient builder, not the
        // materialized registry. Using the actual loaded registries here gives us
        // holders that ARE (or will be) properly bound when the registry freezes.
        WritableRegistry<net.minecraft.world.level.levelgen.placement.PlacedFeature> pfRegistry =
                getRegistry(loaders, Registries.PLACED_FEATURE);
        if (pfRegistry != null) {
            LegacyNeoForgeBiomeLoader.PLACED_FEATURE_HOLDER = pfRegistry.asLookup();
        }
        WritableRegistry<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>> carverRegistry =
                getRegistry(loaders, Registries.CONFIGURED_CARVER);
        if (carverRegistry != null) {
            LegacyNeoForgeBiomeLoader.CONFIGURED_CARVER_HOLDER = carverRegistry.asLookup();
        }

        LegacyNeoForgeBiomeLoader loader = (LegacyNeoForgeBiomeLoader) OTG.getEngine().getPresetLoader();
        WritableRegistry<Biome> biomeWritableRegistry = getRegistry(loaders, Registries.BIOME);
        if (biomeWritableRegistry == null) {
            OTGLog.getLogger().error("Could not find biome registry");
            return;
        }
        loader.registerBiomes(biomeWritableRegistry);
    }

    private static void printAllRegistriesForDebug(List<RegistryDataLoader.Loader<?>> loaders) {
        System.out.println("--*--");
        for (RegistryDataLoader.Loader<?> entry : loaders) {
            OTGLog.info("Registry: %s", entry.registry().key());
        }
        System.out.println("--*--");
    }

    private static Map<ResourceKey<LevelStem>, LevelStem> createLevelStems(
            Preset preset,
            List<RegistryDataLoader.Loader<?>> loaders
    ) {
        var dimensionNames = preset.getDimensionNames();
        int counter = 0;

        Map<ResourceKey<LevelStem>, LevelStem> levelStems = new HashMap<>();
        HolderGetter<DimensionType> dimensionHolders = getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE).asLookup();
        HolderGetter<NoiseGeneratorSettings> noiseHolders = getRegistryOrThrow(loaders, Registries.NOISE_SETTINGS).asLookup();

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
                Preset dimPreset = null;
                for (Preset p : OTG.getEngine().getPresetLoader().getAllPresets()) {
                    if (dim.equals(Constants.MOD_ID_SHORT + ":" + p.getPresetRegistryName())) {
                        dimPreset = p;
                        break;
                    }
                }
                if (dimPreset == null) {
                    OTGLog.getLogger().error("Could not find preset for dimension %s", dim);
                    continue;
                }

                ResourceKey<DimensionType> dimensionKey = switch (dimPreset.getPresetConfig().getDimensionSettings().getDimensionType()) {
                    case OTG -> ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, dimPreset.getPresetRegistryName()));
                    case OVERWORLD -> BuiltinDimensionTypes.OVERWORLD;
                    case NETHER -> BuiltinDimensionTypes.NETHER;
                    case END -> BuiltinDimensionTypes.END;
                };

                ResourceKey<NoiseGeneratorSettings> noiseKey = switch (dimPreset.getPresetConfig().getDimensionSettings().getDimensionType()) {
                    case OVERWORLD -> NoiseGeneratorSettings.OVERWORLD;
                    case NETHER -> NoiseGeneratorSettings.NETHER;
                    case END -> NoiseGeneratorSettings.END;
                    case OTG -> ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, dimPreset.getPresetRegistryName()));
                };

                Holder.Reference<DimensionType> dimensionReference = dimensionHolders.getOrThrow(dimensionKey);
                if (!dimensionReference.isBound()) {
                    OTGLog.getLogger().error("Dimension reference for dimension %s is not bound", dim);
                }

                Holder.Reference<NoiseGeneratorSettings> noiseReference = noiseHolders.getOrThrow(noiseKey);
                if (!noiseReference.isBound()) {
                    OTGLog.getLogger().error("Noise reference for dimension %s is not bound", dim);
                }

                Registry<Biome> biomeRegistry = getRegistryOrThrow(loaders, Registries.BIOME);

                chunkGenerator = new OTGNeoForgeChunkGenerator(
                        new OTGNeoForgeBiomeProvider(preset.getFolderName(), 0L),
                        noiseReference,
                        biomeRegistry
                );
                levelStem = new LevelStem(
                        dimensionReference,
                        chunkGenerator
                );
            } else {
                ResourceKey<DimensionType> dimensionKey = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse(dim));
                Optional<Holder.Reference<DimensionType>> dimensionTypeHolder = dimensionHolders.get(dimensionKey);
                if (dimensionTypeHolder.isEmpty()) {
                    OTGLog.getLogger().error("Could not find dimension reference for dimension %s", dimensionKey.location());
                    continue;
                }
                if (!dimensionTypeHolder.get().isBound()) {
                    OTGLog.getLogger().error("Dimension reference for dimension %s is not bound", dimensionKey.location());
                    continue;
                }

                if (dimensionKey == BuiltinDimensionTypes.OVERWORLD) {
                    Holder<MultiNoiseBiomeSourceParameterList> overworldBiomeSource = getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .asLookup().getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
                    if (!overworldBiomeSource.isBound()) {
                        OTGLog.getLogger().error("Overworld biome source is not bound");
                    }
                    Holder<NoiseGeneratorSettings> overworldNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            MultiNoiseBiomeSource.createFromPreset(overworldBiomeSource),
                            overworldNoise
                    );
                } else if (dimensionKey == BuiltinDimensionTypes.NETHER) {
                    Holder<MultiNoiseBiomeSourceParameterList> netherBiomeSource = getRegistryOrThrow(loaders, Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .asLookup().getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
                    if (!netherBiomeSource.isBound()) {
                        OTGLog.getLogger().error("Nether biome source is not bound");
                    }
                    Holder<NoiseGeneratorSettings> netherNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.NETHER);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            MultiNoiseBiomeSource.createFromPreset(netherBiomeSource),
                            netherNoise
                    );
                } else if (dimensionKey == BuiltinDimensionTypes.END) {
                    var biomes = getRegistryOrThrow(loaders, Registries.BIOME).asLookup();
                    Holder<NoiseGeneratorSettings> endNoise = noiseHolders.getOrThrow(NoiseGeneratorSettings.END);
                    chunkGenerator = new NoiseBasedChunkGenerator(
                            TheEndBiomeSource.create(biomes),
                            endNoise
                    );
                } else {
                    OTGLog.error("Non-OTG dimension %s not yet supported", dimensionKey.location());
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

    private static void registerWorldPresets(
            Preset preset,
            WritableRegistry<WorldPreset> worldPresets,
            Map<ResourceKey<LevelStem>, LevelStem> levelStems
    ) {
        OTGLog.info("%s", levelStems.keySet());
        OTGLog.info("%s", levelStems.values());

        WorldPreset worldPreset = new WorldPreset(levelStems);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getPresetRegistryName().toLowerCase(Locale.ROOT));
        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, id);
        worldPresets.register(key, worldPreset, RegistrationInfo.BUILT_IN);
        OTGLog.getLogger().info("Registered world preset: " + key.location());
    }

    private static void registerNoiseGenSettings(
            Preset preset,
            List<RegistryDataLoader.Loader<?>> loaders,
            RegistryAccess registryAccess) {
        PresetSettings presetSettings = preset.getPresetConfig();
        DimensionSettings dimensionSettings = presetSettings.getDimensionSettings();
        BlockSettings blockSettings = presetSettings.getBlockSettings();
        ResourceSettings resourceSettings = presetSettings.getResourceSettings();
        boolean modernCaves = presetSettings.getCarverSettings().isUseModernCaves();
        boolean aquifers = presetSettings.getNoiseCaveSettings().isAquifersEnabled();
        boolean oreVeins = presetSettings.getNoiseCaveSettings().isVeinsEnabled() && !resourceSettings.isDisableOreGen();

        NoiseGeneratorSettings ngs;
        if (modernCaves) {
            WritableRegistry<NormalNoise.NoiseParameters> noiseRegistry = getRegistry(loaders, Registries.NOISE);
            if (noiseRegistry != null) {
                OTGNoiseParamRegistry.registerNoiseParameters(presetSettings, noiseRegistry);
            }
            HolderGetter<DensityFunction> densityGetter = getRegistryOrThrow(loaders, Registries.DENSITY_FUNCTION).asLookup();
            HolderGetter<NormalNoise.NoiseParameters> noiseGetter = getRegistryOrThrow(loaders, Registries.NOISE).asLookup();
            OTGNoiseRouterBuilder routerBuilder = new OTGNoiseRouterBuilder(densityGetter, noiseGetter);
            String presetName = preset.getPresetRegistryName().toLowerCase(Locale.ROOT);
            NoiseRouter router = routerBuilder.buildWithSettings(presetSettings.getNoiseCaveSettings(), presetName, false, false);

            ngs = new NoiseGeneratorSettings(
                    new NoiseSettings(dimensionSettings.getMinY(), dimensionSettings.getHeight(), 1, 2),
                    ((NeoForgeMaterialData) blockSettings.getDefaultStoneBlock()).getState(),
                    ((NeoForgeMaterialData) blockSettings.getWaterBlock()).getState(),
                    router,
                    SurfaceRuleData.overworld(),
                    new OverworldBiomeBuilder().spawnTarget(),
                    63,
                    aquifers,
                    oreVeins,
                    !resourceSettings.isDisableOreGen(),
                    false
            );
        } else {
            ngs = new NoiseGeneratorSettings(
                    new NoiseSettings(dimensionSettings.getMinY(), dimensionSettings.getHeight(), 1, 2),
                    ((NeoForgeMaterialData) blockSettings.getDefaultStoneBlock()).getState(),
                    ((NeoForgeMaterialData) blockSettings.getWaterBlock()).getState(),
                    getZeroNoiseRouter(),
                    SurfaceRuleData.overworld(),
                    new OverworldBiomeBuilder().spawnTarget(),
                    63,
                    false,
                    false,
                    !resourceSettings.isDisableOreGen(),
                    false
            );
        }

        WritableRegistry<NoiseGeneratorSettings> registry = getRegistry(loaders, Registries.NOISE_SETTINGS);
        if (registry == null) {
            throw new RuntimeException("Could not find noise settings registry");
        }
        ResourceKey<NoiseGeneratorSettings> key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getPresetRegistryName()));
        registry.register(key, ngs, RegistrationInfo.BUILT_IN);
    }

    private static @NotNull NoiseRouter getZeroNoiseRouter() {
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

    private static @NotNull HashMap<Preset, ResourceKey<DimensionType>> registerDimensionTypes(List<RegistryDataLoader.Loader<?>> loaders) {
        var map = new HashMap<Preset, ResourceKey<DimensionType>>();
        for (Preset preset : OTG.getEngine().getPresetLoader().getAllPresets()) {
            OTGDimensionType otgDimensionType = preset.getPresetConfig().getDimensionSettings().getDimensionType();
            ResourceKey<DimensionType> dimensionKey = switch (otgDimensionType) {
                case OVERWORLD -> BuiltinDimensionTypes.OVERWORLD;
                case NETHER -> BuiltinDimensionTypes.NETHER;
                case END -> BuiltinDimensionTypes.END;
                case OTG -> {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, preset.getPresetRegistryName().toLowerCase(Locale.ROOT));
                    ResourceKey<DimensionType> dimensionTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, id);
                    DimensionType dimensionType = getDimensionType(preset.getPresetConfig().getDimensionSettings());
                    WritableRegistry<DimensionType> dimensionTypes = getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE);
                    dimensionTypes.register(dimensionTypeKey, dimensionType, RegistrationInfo.BUILT_IN);
                    OTGLog.info("Registered dimension type: %s", dimensionTypeKey.location());
                    yield dimensionTypeKey;
                }
            };
            map.put(preset, dimensionKey);
        }
        return map;
    }

    private static @NotNull DimensionType getDimensionType(DimensionSettings settings) {
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

    private static <T> WritableRegistry<T> getRegistryOrThrow(List<RegistryDataLoader.Loader<?>> loaders, ResourceKey<Registry<T>> key) {
        WritableRegistry<T> registry = getRegistry(loaders, key);
        if (registry == null) {
            throw new RuntimeException("Could not find registry for key " + key.location());
        }
        return registry;
    }

    @SuppressWarnings("unchecked")
    private static <T> WritableRegistry<T> getRegistry(List<RegistryDataLoader.Loader<?>> loaders, ResourceKey<Registry<T>> key) {
        for (RegistryDataLoader.Loader<?> entry : loaders) {
            if (entry.registry().key().equals(key)) {
                return (WritableRegistry<T>) entry.registry();
            }
        }
        return null;
    }
}
