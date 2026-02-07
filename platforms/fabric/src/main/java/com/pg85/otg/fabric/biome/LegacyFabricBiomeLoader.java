package com.pg85.otg.fabric.biome;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;

import net.minecraft.core.RegistrationInfo;
import com.pg85.otg.OTG;
import com.pg85.otg.config.ConfigFunction;
import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.config.biome.BiomeGroupFunction;
import com.pg85.otg.config.biome.BiomeTemplate;
import com.pg85.otg.config.biome.TemplateBiome;
import com.pg85.otg.config.preset.PresetConfig;
import com.pg85.otg.config.settings.biome.BiomeVisualSettings;
import com.pg85.otg.config.settings.biome.MobSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.gen.biome.BiomeData;
import com.pg85.otg.gen.biome.layers.BiomeLayerData;
import com.pg85.otg.gen.biome.layers.BiomeGroup;
import com.pg85.otg.gen.resource.RegistryResource;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.interfaces.IBiomeResourceLocation;
import com.pg85.otg.config.settings.preset.PresetSettings;
import com.pg85.otg.presets.LocalPresetLoader;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.biome.MCBiomeResourceLocation;
import com.pg85.otg.util.biome.OTGBiomeResourceLocation;
import com.pg85.otg.util.biome.WeightedMobSpawnGroup;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import static com.pg85.otg.util.logging.LogCategory.CONFIGS;


public class LegacyFabricBiomeLoader extends LocalPresetLoader {
    // Static fields to store the references
    public static HolderGetter<PlacedFeature> PLACED_FEATURE_HOLDER;
    public static HolderGetter<ConfiguredWorldCarver<?>> CONFIGURED_CARVER_HOLDER;
    public static boolean BIOME_DATA_INITIALIZED = false;
    private Map<String, List<ResourceKey<Biome>>> biomesByPresetFolderName = new LinkedHashMap<>();
    private Map<String, IBiome[]> globalIdMapping = new java.util.concurrent.ConcurrentHashMap<>();
    private Map<String, BiomeLayerData> presetGenerationData = new java.util.concurrent.ConcurrentHashMap<>();
    
    public LegacyFabricBiomeLoader(Path otgRootFolder)
    {
        super(otgRootFolder);
    }

    public List<ResourceKey<Biome>> getBiomeResourceKeys(String presetFolderName)
    {
        return this.biomesByPresetFolderName.get(presetFolderName);
    }

    @Override
    public IBiome[] getGlobalIdMapping(String presetFolderName)
    {
        return globalIdMapping.get(presetFolderName);
    }

    @Override
    public Map<String, BiomeLayerData> getPresetGenerationData()
    {
        // Return directly - ConcurrentHashMap is thread-safe
        return this.presetGenerationData;
    }

    // Note: BiomeGen and ChunkGen cache some settings during a session, so they'll only update on world exit/rejoin.
    public void reloadPresetFromDisk(String presetFolderName, WritableRegistry<Biome> biomeRegistry)
    {
        clearCaches();

        if(this.presetsDir.exists() && this.presetsDir.isDirectory())
        {
            for(File presetDir : Objects.requireNonNull(this.presetsDir.listFiles()))
            {
                if(presetDir.isDirectory() && presetDir.getName().equals(presetFolderName))
                {
                    for(File file : Objects.requireNonNull(presetDir.listFiles()))
                    {
                        if(file.getName().equals(Constants.PRESET_CONFIG_FILE))
                        {
                            Preset preset = loadPreset(presetDir.toPath());
                            Preset existingPreset = this.presets.get(preset.getFolderName());
                            existingPreset.update(preset);
                            break;
                        }
                    }
                }
            }
        }
        registerBiomes(biomeRegistry);
    }

    protected void clearCaches()
    {
        this.globalIdMapping = new java.util.concurrent.ConcurrentHashMap<>();
        this.presetGenerationData = new java.util.concurrent.ConcurrentHashMap<>();
        this.biomesByPresetFolderName = new LinkedHashMap<>();
    }

    public void reRegisterBiomes(String presetFolderName, WritableRegistry<Biome> biomeRegistry)
    {
        this.globalIdMapping.remove(presetFolderName);
        this.presetGenerationData.remove(presetFolderName);
        this.biomesByPresetFolderName.remove(presetFolderName);

        registerBiomes(biomeRegistry);
    }

    public void registerBiomes()
    {
        registerBiomes(null);
    }

    public void registerBiomes(WritableRegistry<Biome> biomeRegistry)
    {
        for(Preset preset : this.presets.values())
        {
            registerBiomesForPreset(preset, biomeRegistry);
        }
    }

    private void registerBiomesForPreset(Preset preset, WritableRegistry<Biome> biomeRegistry)
    {
        if (!BIOME_DATA_INITIALIZED) {
            // Should always be initialized, but better safe than sorry. Would rather have a sensical error message than nonsensical
            throw new IllegalStateException("BiomeDataMixin not initialized");
        }
        HolderGetter<PlacedFeature> featureHolder = PLACED_FEATURE_HOLDER;
        HolderGetter<ConfiguredWorldCarver<?>> carverHolder = CONFIGURED_CARVER_HOLDER;

        // Index BiomeColors for FromImageMode and /otg map
        HashMap<Integer, Integer> biomeColorMap = new HashMap<>();

        // Start at 1, 0 is the fallback for the biome generator (the world's ocean biome).
        int currentId = 1;

        List<ResourceKey<Biome>> presetBiomes = new ArrayList<>();
        this.biomesByPresetFolderName.put(preset.getFolderName(), presetBiomes);

        PresetSettings presetConfig = preset.getPresetConfig();
        BiomeSettings oceanBiomeConfig = null;
        int[] oceanTemperatures = new int[]{0, 0, 0, 0};

        List<BiomeConfig> biomeConfigs = preset.getBiomeConfigList();
        List<BiomeTemplate> biomeTemplates = preset.getBiomeTemplateList();

        Map<Integer, List<BiomeData>> isleBiomesAtDepth = new HashMap<>();
        Map<Integer, List<BiomeData>> borderBiomesAtDepth = new HashMap<>();

        Map<String, List<Integer>> worldBiomes = new HashMap<>();
        Map<String, BiomeSettings> biomeConfigsByName = new HashMap<>();

        // Create registry keys for each biomeconfig, create template
        // biome configs for any non-otg biomes targeted via TemplateForBiome.
        Map<IBiomeResourceLocation, BiomeSettings> biomeConfigsByResourceLocation = new LinkedHashMap<>();
        List<String> blackListedBiomes = presetConfig.getGenerationSettings().getBlackListedBiomes();

        processTemplateBiomes(preset.getFolderName(), presetConfig, biomeTemplates, biomeConfigsByResourceLocation, biomeConfigsByName, blackListedBiomes, biomeRegistry);

        for(BiomeConfig biomeConfig : biomeConfigs)
        {
            if(!biomeConfig.getIdentitySettings().isTemplateForBiome())
            {
                // Normal OTG biome, not a template biome.
                IBiomeResourceLocation otgLocation = new OTGBiomeResourceLocation(preset.getPresetFolder(), preset.getPresetRegistryName(), biomeConfig.getIdentitySettings().getBiomeName());
                biomeConfig.setRegistryKey(otgLocation);
                biomeConfigsByResourceLocation.put(otgLocation, biomeConfig);
                biomeConfigsByName.put(biomeConfig.getIdentitySettings().getBiomeName(), biomeConfig);
            }
        }

        MobInheritanceHandler.handleMobInheritance(biomeRegistry, biomeConfigs);

        IBiome[] presetIdMapping = new IBiome[biomeConfigsByResourceLocation.size()];
        boolean hasOceanBiome = false;
        for(Entry<IBiomeResourceLocation, BiomeSettings> biomeSettingsEntry : biomeConfigsByResourceLocation.entrySet())
        {
            IBiomeResourceLocation iBiomeResourceLocation = biomeSettingsEntry.getKey();
            BiomeSettings biomeSettings = biomeSettingsEntry.getValue();
            boolean isOceanBiome = false;
            // Biome id 0 is reserved for ocean, used when a land column has
            // no biome assigned, which can happen due to biome group rarity.
            if(biomeSettings.getIdentitySettings().getBiomeName().equals(presetConfig.getGenerationSettings().getDefaultOceanBiome()))
            {
                oceanBiomeConfig = biomeSettings;
                isOceanBiome = true;
                hasOceanBiome = true;
            }

            int otgBiomeId = isOceanBiome ? 0 : currentId;

            // Some legacy presets have invalid ocean biomes
            // This check forces the final biome into ID 0 to be ocean biome
            // This avoids a crash on modern versions
            if(otgBiomeId == presetIdMapping.length && !hasOceanBiome) {
                otgBiomeId = 0;
            }

            if(otgBiomeId > presetIdMapping.length)
            {
                OTGLog.fatal(CONFIGS, "Fatal error while registering OTG biome id's for preset " + preset.getFolderName());

                OTGLog.info(CONFIGS, "Total number of biomes to register: " + presetIdMapping.length);
                OTGLog.info(CONFIGS, "Current id: " + otgBiomeId);
                OTGLog.info(CONFIGS, "List of biomes: " + Arrays.toString(presetIdMapping));

                throw new RuntimeException("Fatal error while registering OTG biome id's for preset " + preset.getFolderName());
            }

            // When using TemplateForBiome, we'll fetch the non-OTG biome from the registry, including any settings registered to it.
            // For normal biomes we create our own new OTG biome and apply settings from the biome config.
            ResourceLocation resourceLocation = ResourceLocation.parse(iBiomeResourceLocation.toResourceLocationString());
            ResourceKey<Biome> resourceKey;
            Biome biome;
            Holder.Reference<Biome> ref;
            // templates, and non-developer refresh, both just get the biome from the registry
            if(
                biomeSettings.getIdentitySettings().isTemplateForBiome()
            ) {
                biome = biomeRegistry.get(resourceLocation);
                if (biome == null) {
                    if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY))
                    {
                        OTG.log(LogLevel.ERROR, LogCategory.BIOME_REGISTRY, "Could not find biome " + resourceLocation + " for biomeconfig " + biomeSettings.getIdentitySettings().getBiomeName());
                    }
                    continue;
                }
                Optional<ResourceKey<Biome>> key = biomeRegistry.getResourceKey(biome);
                resourceKey = key.orElse(null);
                if (resourceKey == null) {
                    if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY))
                    {
                        OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.BIOME_REGISTRY, "Could not find resource key for biome " + resourceLocation + " for biomeconfig " + biomeSettings.getIdentitySettings().getBiomeName());
                    }
                    continue;
                }
                ref = biomeRegistry.getHolder(resourceKey).orElseThrow();

            } else {
                if(!(iBiomeResourceLocation instanceof OTGBiomeResourceLocation))
                {
                    if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY))
                    {
                        OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.BIOME_REGISTRY, "Could not process template biomeconfig " + biomeSettings.getIdentitySettings().getBiomeName() + ", did you set TemplateForBiome:true in the BiomeConfig?");
                    }
                    continue;
                }
                resourceKey = ResourceKey.create(Registries.BIOME, resourceLocation);
                // For OTG biomes, add Fabric biome dictionary tags.
                // Cast to BiomeConfig - we know it's not a template biome here
                BiomeConfig biomeConfig = (BiomeConfig) biomeSettings;
                biome = LegacyFabricBiomeLoader.createOTGBiome(preset.getPresetConfig(), biomeConfig, featureHolder, carverHolder);

                ref = biomeRegistry.register(resourceKey, biome, RegistrationInfo.BUILT_IN);
            }
            presetBiomes.add(resourceKey);

            biomeSettings.setOTGBiomeId(otgBiomeId);

            // Populate our map for syncing
            //OTGClientSyncManager.getSyncedData().put(resourceLocation.toString(), new FabricBiomeSyncWrapper(biomeSettings));

            // Ocean temperature mappings. Probably a better way to do this?
            if (biomeSettings.getIdentitySettings().getBiomeName().equals(presetConfig.getGenerationSettings().getDefaultWarmOceanBiome()))
            {
                oceanTemperatures[0] = otgBiomeId;
            }
            if (biomeSettings.getIdentitySettings().getBiomeName().equals(presetConfig.getGenerationSettings().getDefaultLukewarmOceanBiome()))
            {
                oceanTemperatures[1] = otgBiomeId;
            }
            if (biomeSettings.getIdentitySettings().getBiomeName().equals(presetConfig.getGenerationSettings().getDefaultColdOceanBiome()))
            {
                oceanTemperatures[2] = otgBiomeId;
            }
            if (biomeSettings.getIdentitySettings().getBiomeName().equals(presetConfig.getGenerationSettings().getDefaultFrozenOceanBiome()))
            {
                oceanTemperatures[3] = otgBiomeId;
            }

            IBiome otgBiome = new FabricBiome(biomeSettings, biome, ref);

            presetIdMapping[otgBiomeId] = otgBiome;

            List<Integer> idsForBiome = worldBiomes.computeIfAbsent(biomeSettings.getIdentitySettings().getBiomeName(), k -> new ArrayList<>());
            idsForBiome.add(otgBiomeId);

            // Make a list of isle and border biomes per generation depth
            if(biomeSettings.getGenerationSettings().isIsleBiome())
            {
                // Make or get a list for this group depth, then add
                List<BiomeData> biomesAtDepth = isleBiomesAtDepth.getOrDefault(biomeSettings.getGenerationSettings().getBiomeSizeWhenIsle(), new ArrayList<>());
                biomesAtDepth.add(
                        new BiomeData(
                                otgBiomeId,
                                biomeSettings.getGenerationSettings().getBiomeRarityWhenIsle(),
                                biomeSettings.getGenerationSettings().getBiomeSizeWhenIsle(),
                                biomeSettings.getVisualSettings().getBiomeTemperature(),
                                biomeSettings.getGenerationSettings().getIsleInBiomes(),
                                biomeSettings.getGenerationSettings().getBorderInBiomes(),
                                biomeSettings.getGenerationSettings().getOnlyBorderNear(),
                                biomeSettings.getGenerationSettings().getNotBorderNear()
                        )
                );
                isleBiomesAtDepth.put(biomeSettings.getGenerationSettings().getBiomeSizeWhenIsle(), biomesAtDepth);
            }

            if(biomeSettings.getGenerationSettings().isBorderBiome())
            {
                // Make or get a list for this group depth, then add
                List<BiomeData> biomesAtDepth = borderBiomesAtDepth.getOrDefault(biomeSettings.getGenerationSettings().getBiomeSizeWhenBorder(), new ArrayList<>());
                biomesAtDepth.add(
                        new BiomeData(
                                otgBiomeId,
                                biomeSettings.getGenerationSettings().getBiomeRarity(),
                                biomeSettings.getGenerationSettings().getBiomeSizeWhenBorder(),
                                biomeSettings.getVisualSettings().getBiomeTemperature(),
                                biomeSettings.getGenerationSettings().getIsleInBiomes(),
                                biomeSettings.getGenerationSettings().getBorderInBiomes(),
                                biomeSettings.getGenerationSettings().getOnlyBorderNear(),
                                biomeSettings.getGenerationSettings().getNotBorderNear()
                        )
                );
                borderBiomesAtDepth.put(biomeSettings.getGenerationSettings().getBiomeSizeWhenBorder(), biomesAtDepth);
            }

            // Index BiomeColor for FromImageMode and /otg map
            biomeColorMap.put(biomeSettings.getGenerationSettings().getBiomeMapColor().getColor(), otgBiomeId);

            if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY))
            {
                OTG.getEngine().getLogger().log(LogLevel.INFO, LogCategory.BIOME_REGISTRY, "Registered biome " + resourceLocation.toString() + " | " + biomeSettings.getIdentitySettings().getBiomeName() + " with OTG id " + otgBiomeId);
            }

            currentId += isOceanBiome ? 0 : 1;
        }

        // If the ocean config is null, shift the array downwards to fill id 0
        if (oceanBiomeConfig == null)
        {
            System.arraycopy(presetIdMapping, 1, presetIdMapping, 0, presetIdMapping.length - 1);
        }

        this.globalIdMapping.put(preset.getFolderName(), presetIdMapping);


        Set<Integer> biomeDepths = new HashSet<>();
        Map<Integer, List<BiomeGroup>> groupDepths = new HashMap<>();

        // Iterate through the groups and add it to the layer data
        Map<Integer, BiomeGroup> groupRegistry = processBiomeGroups(preset.getFolderName(), presetConfig, biomeConfigsByResourceLocation, biomeConfigsByName, blackListedBiomes, biomeDepths, groupDepths);

        // Set the base data
        BiomeLayerData data = new BiomeLayerData(preset.getPresetFolder(), presetConfig, oceanBiomeConfig, oceanTemperatures, groupRegistry, biomeDepths, groupDepths, isleBiomesAtDepth, borderBiomesAtDepth, worldBiomes, biomeColorMap, presetIdMapping);

        // Set data for this preset
        this.presetGenerationData.put(preset.getFolderName(), data);
    }

    public static Biome createOTGBiome(PresetSettings presetConfig, BiomeConfig biomeConfig, HolderGetter<PlacedFeature> featureHolderGetter, HolderGetter<ConfiguredWorldCarver<?>> carverHolderGetter) {

        BiomeGenerationSettings.Builder generationSettings = new BiomeGenerationSettings.Builder(featureHolderGetter, carverHolderGetter);

        // Mob spawning
        MobSpawnSettings.Builder mobSpawnSettings = createMobSpawnSettings(biomeConfig);

        //BiomeDefaultFeatures.addDefaultCarversAndLakes(generationSettings);


        // Register any Registry() resources to the biome, to be handled by MC.
        for (ConfigFunction<BiomeSettings> res : biomeConfig.getResourceQueue())
        {
            if (res instanceof RegistryResource registryResource)
            {
                GenerationStep.Decoration stage = GenerationStep.Decoration.valueOf(registryResource.getDecorationStage());
                Optional<Holder.Reference<PlacedFeature>> placedFeatureReference = featureHolderGetter.get(ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.parse(registryResource.getFeatureKey())));
                if(
                        placedFeatureReference.isPresent()
                        && placedFeatureReference.get().isBound()
                        && placedFeatureReference.get().unwrapKey().isPresent()
                ) {
                    generationSettings.addFeature(stage, placedFeatureReference.get().unwrapKey().get());
                } else {
                    if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.DECORATION))
                    {
                        OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.DECORATION, "Registry() " + registryResource.getFeatureKey() + " could not be found for biomeconfig " + biomeConfig.getIdentitySettings().getBiomeName());
                    }
                }
            }
        }

        // Add default structures
        // TODO: Find a way to add our biomes to the relevant structure biome tags...


        float temperature = biomeConfig.getVisualSettings().getBiomeTemperature();
        
        float downfall = biomeConfig.getVisualSettings().getBiomeWetness();

        BiomeSpecialEffects.Builder specialEffects = getSpecialEffects(presetConfig, biomeConfig);

        return new Biome.BiomeBuilder()
                .generationSettings(generationSettings.build())
                .mobSpawnSettings(mobSpawnSettings.build())
                .specialEffects(specialEffects.build())
                .downfall(downfall)
                .temperature(temperature)
                .build();
    }

    private static BiomeSpecialEffects.Builder getSpecialEffects(PresetSettings presetConfig, BiomeSettings biomeConfig) {
        BiomeVisualSettings biomeVisualSettings = biomeConfig.getVisualSettings();
        float safeTemperature = biomeConfig.getVisualSettings().getBiomeTemperature();
        if (safeTemperature >= 0.1 && safeTemperature <= 0.2)
        {
            // Avoid temperatures between 0.1 and 0.2, Minecraft restriction
            safeTemperature = safeTemperature >= 1.5 ? 0.2f : 0.1f;
        }

        BiomeSpecialEffects.Builder specialEffects =
                new BiomeSpecialEffects.Builder()
                        .fogColor(
                                (!Objects.equals(biomeVisualSettings.getFogColor(), BiomeVisualSettings.FOG_COLOR.getDefaultValue())
                                        ? biomeVisualSettings.getFogColor()
                                        : presetConfig.getVisualSettings().getFogColor()
                                ).intValue()
                        )
                        .waterFogColor(
                                !Objects.equals(biomeVisualSettings.getWaterFogColor(), BiomeVisualSettings.WATER_FOG_COLOR.getDefaultValue())
                                        ? biomeVisualSettings.getWaterFogColor().intValue()
                                        : 329011
                        )
                        .waterColor(
                                !Objects.equals(biomeVisualSettings.getWaterColor(), BiomeVisualSettings.WATER_COLOR.getDefaultValue())
                                        ? biomeVisualSettings.getWaterColor().intValue()
                                        : 4159204
                        )
                        .skyColor(
                                !Objects.equals(biomeVisualSettings.getSkyColor(), BiomeVisualSettings.SKY_COLOR.getDefaultValue())
                                        ? biomeVisualSettings.getSkyColor().intValue()
                                        : getSkyColorForTemp(safeTemperature)
                        ) // TODO: Sky color is normally based on temp, make a setting for that?
                ;
        //Optional<Holder.Reference<ParticleType<?>>> ambientParticle = getFromRegistry(BuiltInRegistries.PARTICLE_TYPE, Registries.PARTICLE_TYPE, biomeVisualSettings.getParticleType());
        // TODO: Particles have become incredibly tricky to work with, let's avoid this for now

        Optional<Holder.Reference<SoundEvent>> ambientLoopSoundEvent = getFromRegistry(BuiltInRegistries.SOUND_EVENT, Registries.SOUND_EVENT, biomeVisualSettings.getAmbientSound());
        ambientLoopSoundEvent.ifPresent(specialEffects::ambientLoopSound);
        Optional<Holder.Reference<SoundEvent>> ambientMoodSound = getFromRegistry(BuiltInRegistries.SOUND_EVENT, Registries.SOUND_EVENT, biomeVisualSettings.getMoodSound());
        if (ambientMoodSound.isPresent()) {
            AmbientMoodSettings ambientMoodSettings = new AmbientMoodSettings(
                    ambientMoodSound.get(),
                    biomeVisualSettings.getMoodSoundDelay(),
                    biomeVisualSettings.getMoodSearchRange(),
                    biomeVisualSettings.getMoodOffset()
            );
            specialEffects.ambientMoodSound(ambientMoodSettings);
        }
        Optional<Holder.Reference<SoundEvent>> ambientAdditionsSound = getFromRegistry(BuiltInRegistries.SOUND_EVENT, Registries.SOUND_EVENT, biomeVisualSettings.getAdditionsSound());
        if (ambientAdditionsSound.isPresent()) {
            AmbientAdditionsSettings ambientAdditionsSettings = new AmbientAdditionsSettings(
                    ambientAdditionsSound.get(),
                    biomeVisualSettings.getAdditionsTickChance()
            );
            specialEffects.ambientAdditionsSound(ambientAdditionsSettings);
        }
        Optional<Holder.Reference<SoundEvent>> backgroundMusic = getFromRegistry(BuiltInRegistries.SOUND_EVENT, Registries.SOUND_EVENT, biomeVisualSettings.getMusic());
        if (backgroundMusic.isPresent()) {
            Music music = new Music(
                    backgroundMusic.get(),
                    biomeVisualSettings.getMusicMinDelay(),
                    biomeVisualSettings.getMusicMaxDelay(),
                    biomeVisualSettings.isReplaceCurrentMusic()
            );
            specialEffects.backgroundMusic(music);
        }

        if(biomeVisualSettings.getFoliageColor().intValue() != 0xffffff) {
            specialEffects.foliageColorOverride(biomeVisualSettings.getFoliageColor().intValue());
        }

        if(biomeVisualSettings.getGrassColor().intValue() != 0xffffff) {
            specialEffects.grassColorOverride(biomeVisualSettings.getGrassColor().intValue());
        }

        switch(biomeVisualSettings.getGrassColorModifier()) {
            case Swamp:
                specialEffects.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.SWAMP);
                break;
            case DarkForest:
                specialEffects.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.DARK_FOREST);
                break;
            default:
                break;
        }

        return specialEffects;
    }

    private static <T> Optional<Holder.Reference<T>> getFromRegistry(Registry<T> registry, ResourceKey<Registry<T>> registryResourceKey, String locationString) {
        try {
            return registry.getHolder(ResourceKey.create(registryResourceKey, ResourceLocation.parse(locationString)));
        } catch (Exception e) {
            OTGLog.error(CONFIGS, "Could not find registry entry for '" + locationString + "'");
            return Optional.empty();
        }
    }


    private static MobSpawnSettings.Builder createMobSpawnSettings(BiomeConfig biomeConfig) {
        MobSettings mobSettings = biomeConfig.getMergedMobSettings();
        String biomeName = biomeConfig.getIdentitySettings().getBiomeName();
        MobSpawnSettings.Builder mobSpawnInfoBuilder = new MobSpawnSettings.Builder();

        addMobGroup(MobCategory.MONSTER, mobSpawnInfoBuilder, mobSettings.getMonsters(), biomeName);
        addMobGroup(MobCategory.CREATURE, mobSpawnInfoBuilder, mobSettings.getCreatures(), biomeName);
        addMobGroup(MobCategory.WATER_CREATURE, mobSpawnInfoBuilder, mobSettings.getWaterCreatures(), biomeName);
        addMobGroup(MobCategory.AMBIENT, mobSpawnInfoBuilder, mobSettings.getAmbientCreatures(), biomeName);
        addMobGroup(MobCategory.WATER_AMBIENT, mobSpawnInfoBuilder, mobSettings.getWaterAmbientCreatures(), biomeName);
        addMobGroup(MobCategory.MISC, mobSpawnInfoBuilder, mobSettings.getMiscCreatures(), biomeName);
        return mobSpawnInfoBuilder;
    }

    private static void addMobGroup(MobCategory entitiClassification, MobSpawnSettings.Builder mobSpawnInfoBuilder, List<WeightedMobSpawnGroup> mobSpawnGroupList, String biomeName)
    {
        for(WeightedMobSpawnGroup mobSpawnGroup : mobSpawnGroupList)
        {
            Optional<EntityType<?>> entityType = EntityType.byString(mobSpawnGroup.internalName());
            if(entityType.isPresent())
            {
                mobSpawnInfoBuilder.addSpawn(entitiClassification, new MobSpawnSettings.SpawnerData(entityType.get(), mobSpawnGroup.getWeight(), mobSpawnGroup.getMin(), mobSpawnGroup.getMax()));
            } else {
                if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.MOBS))
                {
                    OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.MOBS, "Could not find entity for mob: " + mobSpawnGroup.getMob() + " in BiomeConfig " + biomeName);
                }
            }
        }
    }

    private Map<Integer, BiomeGroup> processBiomeGroups(String presetFolderName, PresetSettings presetConfig, Map<IBiomeResourceLocation, BiomeSettings> biomeConfigsByResourceLocation, Map<String, BiomeSettings> biomeConfigsByName, List<String> blackListedBiomes, Set<Integer> biomeDepths, Map<Integer, List<BiomeGroup>> groupDepths)
    {
        int genDepth = presetConfig.getGenerationSettings().getGenerationDepth();
        Map<Integer, BiomeGroup> groupRegistry = new HashMap<>();
        for (BiomeGroupFunction group : presetConfig.getGenerationSettings().getBiomeGroupManager().getGroups())
        {
            if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY))
            {
                OTG.getEngine().getLogger().log(LogLevel.INFO, LogCategory.BIOME_REGISTRY, "Processing " + group.toString());
            }

            // Initialize biome group data
            List<BiomeData> biomes = new ArrayList<>();

            // init to genDepth as it will have one value per depth
            var totalDepthRarity = new int[genDepth + 1];
            var maxRarityPerDepth = new int[genDepth + 1];

            float totalTemp = 0;

            HashMap<String, BiomeSettings> groupBiomes = new LinkedHashMap<>();

            for (String biomeGroupEntry : group.getBiomes()) {
                BiomeSettings biomeSettings = biomeConfigsByName.get(biomeGroupEntry);
                if(biomeSettings == null)
                {
                    if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY))
                    {
                        OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.BIOME_REGISTRY, "Could not find biome " + biomeGroupEntry + " in biome group " + group.getGroupId());
                    }
                    continue;
                }
                groupBiomes.put(biomeGroupEntry, biomeSettings);
            }


            // Add each biome to the group
            for (Entry<String, BiomeSettings> biome : groupBiomes.entrySet())
            {
                if(biome.getValue() != null)
                {
                    BiomeSettings config = biome.getValue();
                    // Make and add the generation data
                    BiomeData newBiomeData = new BiomeData(
                            config.getOTGBiomeID().id(),
                            config.getGenerationSettings().getBiomeRarity(),
                            config.getGenerationSettings().getBiomeSize(),
                            config.getVisualSettings().getBiomeTemperature(),
                            config.getGenerationSettings().getIsleInBiomes(),
                            config.getGenerationSettings().getBorderInBiomes(),
                            config.getGenerationSettings().getOnlyBorderNear(),
                            config.getGenerationSettings().getNotBorderNear()
                    );
                    biomes.add(newBiomeData);

                    // Add the biome size- if it's already there, nothing is done
                    biomeDepths.add(config.getGenerationSettings().getBiomeSize());

                    totalTemp += config.getVisualSettings().getBiomeTemperature();

                    // Add this biome's rarity to the total for its depth in the group
                    totalDepthRarity[config.getGenerationSettings().getBiomeSize()] += config.getGenerationSettings().getBiomeRarity();
                }
            }

            // We have filled out the biome group's totalDepthRarity array, use it to fill the maxRarityPerDepth array
            for (int depth = 0; depth < totalDepthRarity.length; depth++)
            {
                // maxRarityPerDepth is the sum of totalDepthRarity for this and subsequent depths
                for (int j = depth; j < totalDepthRarity.length; j++)
                {
                    maxRarityPerDepth[depth] += totalDepthRarity[j];
                }
            }

            float avgTemp = totalTemp / group.getBiomes().size();
            BiomeGroup bg = new BiomeGroup(group.getGroupId(), group.getGroupRarity(), biomes, avgTemp, totalDepthRarity, maxRarityPerDepth);

            int groupSize = group.getGenerationDepth();

            // Make or get a list for this group depth, then add
            List<BiomeGroup> groupsAtDepth = groupDepths.getOrDefault(groupSize, new ArrayList<>());
            groupsAtDepth.add(bg);

            // Replace entry
            groupDepths.put(groupSize, groupsAtDepth);

            // Register group id
            groupRegistry.put(bg.id, bg);
        }
        return groupRegistry;
    }

    private static int getSkyColorForTemp(float temp) {
        float skyColor = temp / 3.0F;
        skyColor = Mth.clamp(skyColor, -1.0F, 1.0F);
        return Mth.hsvToRgb(0.62222224F - skyColor * 0.05F, 0.5F + skyColor * 0.1F, 1.0F);
    }

    private void processTemplateBiomes(
        String presetFolderName,
        PresetSettings presetConfig,
        List<BiomeTemplate> biomeTemplates,
        Map<IBiomeResourceLocation, BiomeSettings> biomeConfigsByResourceLocation,
        Map<String, BiomeSettings> biomeConfigsByName,
        List<String> blackListedBiomes,
        Registry<Biome> biomeRegistry
    ) {
        if (!(presetConfig instanceof PresetConfig)) {
            return;
        }

        for (TemplateBiome templateBiome : ((PresetConfig) presetConfig).getGenerationSettings().getTemplateBiomes()) {
            if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY)) {
                OTG.getEngine().getLogger().log(LogLevel.INFO, LogCategory.BIOME_REGISTRY, "Processing template biome: " + templateBiome.toString());
            }

            // Find the OTG biome template that defines this template biome
            BiomeTemplate biomeTemplate = biomeTemplates.stream()
                .filter(bt -> bt.getIdentitySettings().getBiomeName().equalsIgnoreCase(templateBiome.getName()))
                .findFirst()
                .orElse(null);

            if (biomeTemplate == null) {
                OTG.getEngine().getLogger().log(LogLevel.WARN, LogCategory.BIOME_REGISTRY,
                    "No BiomeTemplate found for template biome: " + templateBiome.getName());
                continue;
            }

            // Add the template to biomeConfigsByName so BiomeGroups can find it
            // BiomeTemplate extends BiomeSettings which is compatible with where BiomeConfig is expected
            // We use a workaround by storing the template name -> first matched biome config mapping
            // This allows BiomeGroups to resolve template names like "tagWater" to actual biomes

            // Parse tag criteria
            List<String> includeTags = new ArrayList<>();
            List<String> excludeTags = new ArrayList<>();
            List<String> includeMods = new ArrayList<>();
            List<String> excludeMods = new ArrayList<>();

            for (String tagString : templateBiome.getTags()) {
                String tag = tagString.trim().toLowerCase();

                if (tag.startsWith(Constants.MOD_BIOME_DICT_TAG_LABEL_EXCLUDE) ||
                    tag.startsWith(Constants.MC_BIOME_DICT_TAG_LABEL_EXCLUDE) ||
                    tag.startsWith(Constants.BIOME_DICT_TAG_LABEL_EXCLUDE)) {
                    // Exclude tag: -modtag.*, -mctag.*, -tag.*
                    String tagName = tag.replace(Constants.MOD_BIOME_DICT_TAG_LABEL_EXCLUDE, "")
                                        .replace(Constants.MC_BIOME_DICT_TAG_LABEL_EXCLUDE, "")
                                        .replace(Constants.BIOME_DICT_TAG_LABEL_EXCLUDE, "");
                    excludeTags.add(tagName);
                } else if (tag.startsWith(Constants.MOD_BIOME_CATEGORY_LABEL_EXCLUDE) ||
                           tag.startsWith(Constants.MC_BIOME_CATEGORY_LABEL_EXCLUDE) ||
                           tag.startsWith(Constants.BIOME_CATEGORY_LABEL_EXCLUDE)) {
                    // Exclude category (treat as tag): -modcategory.*, -mccategory.*, -category.*
                    String tagName = tag.replace(Constants.MOD_BIOME_CATEGORY_LABEL_EXCLUDE, "")
                                        .replace(Constants.MC_BIOME_CATEGORY_LABEL_EXCLUDE, "")
                                        .replace(Constants.BIOME_CATEGORY_LABEL_EXCLUDE, "");
                    excludeTags.add(tagName);
                } else if (tag.startsWith(Constants.MOD_LABEL_EXCLUDE)) {
                    // Exclude mod: -mod.*
                    excludeMods.add(tag.replace(Constants.MOD_LABEL_EXCLUDE, ""));
                } else if (tag.startsWith(Constants.MOD_BIOME_DICT_TAG_LABEL) ||
                           tag.startsWith(Constants.MC_BIOME_DICT_TAG_LABEL) ||
                           tag.startsWith(Constants.BIOME_DICT_TAG_LABEL)) {
                    // Include tag: modtag.*, mctag.*, tag.*
                    String tagName = tag.replace(Constants.MOD_BIOME_DICT_TAG_LABEL, "")
                                        .replace(Constants.MC_BIOME_DICT_TAG_LABEL, "")
                                        .replace(Constants.BIOME_DICT_TAG_LABEL, "");
                    includeTags.add(tagName);
                } else if (tag.startsWith(Constants.MOD_BIOME_CATEGORY_LABEL) ||
                           tag.startsWith(Constants.MC_BIOME_CATEGORY_LABEL) ||
                           tag.startsWith(Constants.BIOME_CATEGORY_LABEL)) {
                    // Include category (treat as tag): modcategory.*, mccategory.*, category.*
                    String tagName = tag.replace(Constants.MOD_BIOME_CATEGORY_LABEL, "")
                                        .replace(Constants.MC_BIOME_CATEGORY_LABEL, "")
                                        .replace(Constants.BIOME_CATEGORY_LABEL, "");
                    includeTags.add(tagName);
                } else if (tag.startsWith(Constants.MOD_LABEL)) {
                    // Include mod: mod.*
                    includeMods.add(tag.replace(Constants.MOD_LABEL, ""));
                } else if (tag.contains(":")) {
                    // Direct biome reference like minecraft:plains
                    processDirectBiomeReference(tag, presetFolderName, biomeTemplate, biomeConfigsByResourceLocation, biomeConfigsByName, biomeRegistry);
                }
            }

            // If we have tag criteria, iterate all biomes and find matches
            if (!includeTags.isEmpty()) {
                for (ResourceKey<Biome> biomeKey : biomeRegistry.registryKeySet()) {
                    // Check blacklist
                    if (blackListedBiomes.contains(biomeKey.location().toString())) {
                        continue;
                    }

                    // Check mod namespace filter
                    String namespace = biomeKey.location().getNamespace();
                    if (!includeMods.isEmpty() && !includeMods.contains(namespace)) {
                        continue;
                    }
                    if (excludeMods.contains(namespace)) {
                        continue;
                    }

                    // Check include tags (all must match)
                    boolean matchesAllInclude = true;
                    for (String includeTag : includeTags) {
                        if (!FabricBiomeTagMapper.biomeHasTag(biomeRegistry, biomeKey, includeTag)) {
                            matchesAllInclude = false;
                            break;
                        }
                    }
                    if (!matchesAllInclude) {
                        continue;
                    }

                    // Check exclude tags (none must match)
                    boolean matchesAnyExclude = false;
                    for (String excludeTag : excludeTags) {
                        if (FabricBiomeTagMapper.biomeHasTag(biomeRegistry, biomeKey, excludeTag)) {
                            matchesAnyExclude = true;
                            break;
                        }
                    }
                    if (matchesAnyExclude) {
                        continue;
                    }

                    // Check temperature filter
                    Biome biome = biomeRegistry.get(biomeKey);
                    if (biome != null && !templateBiome.temperatureAllowed(biome.getBaseTemperature())) {
                        continue;
                    }

                    // Biome matches! Add to maps using MCBiomeResourceLocation for template biomes
                    IBiomeResourceLocation location = new MCBiomeResourceLocation(
                        biomeKey.location().getNamespace(),
                        biomeKey.location().getPath(),
                        presetFolderName
                    );

                    if (!biomeConfigsByResourceLocation.containsKey(location)) {
                        biomeTemplate.setRegistryKey(location);
                        biomeConfigsByResourceLocation.put(location, biomeTemplate);
                        biomeConfigsByName.put(biomeTemplate.getIdentitySettings().getBiomeName(), biomeTemplate);

                        if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY)) {
                            OTG.getEngine().getLogger().log(LogLevel.INFO, LogCategory.BIOME_REGISTRY,
                                "Template biome " + templateBiome.getName() + " matched: " + biomeKey.location());
                        }
                    }
                }
            }
        }
    }

    private void processDirectBiomeReference(
        String biomeId,
        String presetFolderName,
        BiomeSettings biomeSettings,
        Map<IBiomeResourceLocation, BiomeSettings> biomeConfigsByResourceLocation,
        Map<String, BiomeSettings> biomeConfigsByName,
        Registry<Biome> biomeRegistry
    ) {
        ResourceLocation location = ResourceLocation.parse(biomeId);
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, location);

        if (biomeRegistry.containsKey(biomeKey)) {
            IBiomeResourceLocation otgLocation = new MCBiomeResourceLocation(
                location.getNamespace(),
                location.getPath(),
                presetFolderName
            );

            if (!biomeConfigsByResourceLocation.containsKey(otgLocation)) {
                biomeSettings.setRegistryKey(otgLocation);
                biomeConfigsByResourceLocation.put(otgLocation, biomeSettings);
                biomeConfigsByName.put(biomeSettings.getIdentitySettings().getBiomeName(), biomeSettings);

                if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY)) {
                    OTG.getEngine().getLogger().log(LogLevel.INFO, LogCategory.BIOME_REGISTRY,
                        "Direct biome reference matched: " + biomeId);
                }
            }
        } else {
            if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY)) {
                OTG.getEngine().getLogger().log(LogLevel.WARN, LogCategory.BIOME_REGISTRY,
                    "Direct biome reference not found in registry: " + biomeId);
            }
        }
    }
}
