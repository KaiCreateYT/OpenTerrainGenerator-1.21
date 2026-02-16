package com.pg85.otg.shared.biome;

import com.pg85.otg.OTG;
import com.pg85.otg.config.ConfigFunction;
import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.config.settings.biome.BiomeVisualSettings;
import com.pg85.otg.config.settings.preset.PresetSettings;
import com.pg85.otg.gen.resource.RegistryResource;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.biome.WeightedMobSpawnGroup;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
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

import java.util.*;

import static com.pg85.otg.util.logging.LogCategory.CONFIGS;

/**
 * Constructs MC Biome objects from OTG BiomeConfig.
 * MC-dependent but pure functions (input -> output, no side effects beyond logging).
 * Feature/carver holders are constructor parameters, not global statics.
 */
public final class BiomeFactory {

    private final HolderGetter<PlacedFeature> featureHolder;
    private final HolderGetter<ConfiguredWorldCarver<?>> carverHolder;

    public BiomeFactory(
            HolderGetter<PlacedFeature> featureHolder,
            HolderGetter<ConfiguredWorldCarver<?>> carverHolder) {
        this.featureHolder = featureHolder;
        this.carverHolder = carverHolder;
    }

    public Biome createOTGBiome(PresetSettings presetConfig, BiomeConfig biomeConfig) {
        BiomeGenerationSettings.Builder generationSettings = new BiomeGenerationSettings.Builder(featureHolder, carverHolder);

        MobSpawnSettings.Builder mobSpawnSettings = createMobSpawnSettings(biomeConfig);

        // Collect Registry() resources per generation step, then sort by feature key
        // to ensure a globally consistent ordering. Without this, biomes with the same
        // features in different orders cause "Feature order cycle found" in MC 1.18+.
        Map<GenerationStep.Decoration, List<ResourceKey<PlacedFeature>>> featuresByStep = new TreeMap<>();
        for (ConfigFunction<BiomeSettings> res : biomeConfig.getResourceQueue()) {
            if (res instanceof RegistryResource registryResource) {
                GenerationStep.Decoration stage = GenerationStep.Decoration.valueOf(registryResource.getDecorationStage());
                Optional<Holder.Reference<PlacedFeature>> placedFeatureReference = featureHolder.get(ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.parse(registryResource.getFeatureKey())));
                if (
                        placedFeatureReference.isPresent()
                        && placedFeatureReference.get().isBound()
                        && placedFeatureReference.get().unwrapKey().isPresent()
                ) {
                    featuresByStep.computeIfAbsent(stage, k -> new ArrayList<>()).add(placedFeatureReference.get().unwrapKey().get());
                } else {
                    if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.DECORATION)) {
                        OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.DECORATION, "Registry() " + registryResource.getFeatureKey() + " could not be found for biomeconfig " + biomeConfig.getIdentitySettings().getBiomeName());
                    }
                }
            }
        }
        for (Map.Entry<GenerationStep.Decoration, List<ResourceKey<PlacedFeature>>> entry : featuresByStep.entrySet()) {
            entry.getValue().sort(Comparator.comparing(ResourceKey::location));
            for (ResourceKey<PlacedFeature> featureKey : entry.getValue()) {
                generationSettings.addFeature(entry.getKey(), featureKey);
            }
        }

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
        if (safeTemperature >= 0.1 && safeTemperature <= 0.2) {
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
                        );

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

        if (biomeVisualSettings.getFoliageColor().intValue() != 0xffffff) {
            specialEffects.foliageColorOverride(biomeVisualSettings.getFoliageColor().intValue());
        }

        if (biomeVisualSettings.getGrassColor().intValue() != 0xffffff) {
            specialEffects.grassColorOverride(biomeVisualSettings.getGrassColor().intValue());
        }

        switch (biomeVisualSettings.getGrassColorModifier()) {
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
        com.pg85.otg.config.settings.biome.MobSettings mobSettings = biomeConfig.getMergedMobSettings();
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

    private static void addMobGroup(MobCategory entityClassification, MobSpawnSettings.Builder mobSpawnInfoBuilder, List<WeightedMobSpawnGroup> mobSpawnGroupList, String biomeName) {
        for (WeightedMobSpawnGroup mobSpawnGroup : mobSpawnGroupList) {
            Optional<EntityType<?>> entityType = EntityType.byString(mobSpawnGroup.internalName());
            if (entityType.isPresent()) {
                mobSpawnInfoBuilder.addSpawn(entityClassification, new MobSpawnSettings.SpawnerData(entityType.get(), mobSpawnGroup.getWeight(), mobSpawnGroup.getMin(), mobSpawnGroup.getMax()));
            } else {
                if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.MOBS)) {
                    OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.MOBS, "Could not find entity for mob: " + mobSpawnGroup.getMob() + " in BiomeConfig " + biomeName);
                }
            }
        }
    }

    private static int getSkyColorForTemp(float temp) {
        float skyColor = temp / 3.0F;
        skyColor = Mth.clamp(skyColor, -1.0F, 1.0F);
        return Mth.hsvToRgb(0.62222224F - skyColor * 0.05F, 0.5F + skyColor * 0.1F, 1.0F);
    }
}
