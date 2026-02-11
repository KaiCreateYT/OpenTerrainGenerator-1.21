package com.pg85.otg.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistrationInfo;
import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.*;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.fabric.biome.LegacyFabricBiomeLoader;
import com.pg85.otg.fabric.biome.OTGFabricBiomeProvider;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.materials.FabricMaterialData;
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
import com.pg85.otg.platform.noise.OTGNoiseParamRegistry;
import com.pg85.otg.platform.noise.OTGNoiseRouterBuilder;
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
    // In 1.21.1, the actual registry loading logic moved to a private overload:
    //   private static load(LoadingFunction, RegistryAccess, List<RegistryData<?>>)
    // The local variable List<Loader<?>> holds all writable registries being loaded.
    // We use @Local from MixinExtras to capture it (access widener exposes Loader/LoadingFunction).
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
            // vanilla auto-registers the level stems based on the world preset, so we can ignore this
            return;
        }

        // In 1.21.1, load() is also called during client-side registry sync (SYNCHRONIZED_REGISTRIES).
        // The synced set includes biomes and dimension types but NOT noise settings or world presets.
        // We only want to run during server-side WORLDGEN_REGISTRIES loading.
        if (getRegistry(loaders, Registries.NOISE_SETTINGS) == null) {
            return;
        }

        OTGLog.getLogger().info("Registering the following OTG presets: %s", OTG.getEngine().getPresetLoader().getAllPresets());

        //printAllRegistriesForDebug(loaders);

        // register biomes
        registerBiomes(loaders);

        // Register our dimension types in the format otg:preset
        HashMap<Preset, ResourceKey<DimensionType>> dimensionTypes = registerDimensionTypes(loaders);

        // register noise generator settings for each preset
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

            // create level stems, with at least one using the Overworld resource key
            Map<ResourceKey<LevelStem>, LevelStem> levelStems = createLevelStems(preset, loaders);

            // register world presets, using the level stems
            registerWorldPresets(preset, worldPresets, levelStems);
        }
    }

    private static void registerBiomes(List<RegistryDataLoader.Loader<?>> loaders) {
        if (OTG.getEngine().getPluginConfig().getDeveloperModeEnabled()) {
            // clear all the caches
            OTG.getEngine().getCustomObjectManager().reloadCustomObjectFiles();
            OTG.getEngine().getPresetLoader().loadPresetsFromDisk();
        }

        LegacyFabricBiomeLoader loader = (LegacyFabricBiomeLoader) OTG.getEngine().getPresetLoader();
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
                    // OTG dimension
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
                    //continue;
                }

                Holder.Reference<NoiseGeneratorSettings> noiseReference = noiseHolders.getOrThrow(noiseKey);
                if (!noiseReference.isBound()) {
                    OTGLog.getLogger().error("Noise reference for dimension %s is not bound", dim);
                    //continue;
                }

                Registry<Biome> biomeRegistry = getRegistryOrThrow(loaders, Registries.BIOME);

                chunkGenerator = new OTGFabricChunkGenerator(
                        new OTGFabricBiomeProvider(preset.getFolderName(), 0L),
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
                        //continue;
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
                        //continue;
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
                    // how do we find an appropriate generator for a given non-otg dimension?
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
        // for this preset, we set up a WorldPreset with an overworld, nether and end according to its settings
        // potentially also with custom dimensions set up under OTG tags
        // or with all vanilla biomes, plus an OTG dimension
        // (latter will be good for testing a single preset, hopefully)

        WorldPreset worldPreset = new WorldPreset(levelStems);
        // create a world preset for thepreset
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
                    ((FabricMaterialData) blockSettings.getDefaultStoneBlock()).getState(),
                    ((FabricMaterialData) blockSettings.getWaterBlock()).getState(),
                    router,
                    SurfaceRuleData.overworld(),
                    new OverworldBiomeBuilder().spawnTarget(),
                    63,
                    aquifers,
                    oreVeins,
                    !resourceSettings.isDisableOreGen(), // vanilla ore toggle
                    false // use legacy random source
            );
        } else {
            ngs = new NoiseGeneratorSettings(
                    new NoiseSettings(dimensionSettings.getMinY(), dimensionSettings.getHeight(), 1, 2),
                    ((FabricMaterialData) blockSettings.getDefaultStoneBlock()).getState(),
                    ((FabricMaterialData) blockSettings.getWaterBlock()).getState(),
                    getZeroNoiseRouter(),
                    SurfaceRuleData.overworld(),
                    new OverworldBiomeBuilder().spawnTarget(),
                    63,
                    false,
                    false, // is veins enabled
                    !resourceSettings.isDisableOreGen(), // vanilla ore toggle
                    false // use legacy random source
            );
        }

        // register the noise settings
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
                    // create settings for OTG dimension
                    DimensionType dimensionType = getDimensionType(preset.getPresetConfig().getDimensionSettings());
                    // register the dimension
                    WritableRegistry<DimensionType> dimensionTypes = getRegistryOrThrow(loaders, Registries.DIMENSION_TYPE);
                    dimensionTypes.register(dimensionTypeKey, dimensionType, RegistrationInfo.BUILT_IN);
                    OTGLog.info("Registered dimension type: %s", dimensionTypeKey.location());
                    // return the key for use elsewhere
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
                        // monsterSpawnLightTest
                        UniformInt.of(
                                settings.getMonsterSpawnLightVariationMin(),
                                settings.getMonsterSpawnLightVariationMax()
                        ),
                        // monsterSpawnBlockLightLimit
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
