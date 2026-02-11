package com.pg85.otg.neoforge.noise;

import com.pg85.otg.config.settings.preset.NoiseCaveSettings;
import com.pg85.otg.config.settings.preset.PresetSettings;
import com.pg85.otg.constants.Constants;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;
import java.util.Locale;

public final class NeoForgeNoiseParamRegistry {
    private NeoForgeNoiseParamRegistry() {
    }

    public static void registerNoiseParameters(PresetSettings presetSettings, WritableRegistry<NormalNoise.NoiseParameters> registry) {
        String presetName = presetSettings.getPresetInfo().getRegistryName().toLowerCase(Locale.ROOT);
        NoiseCaveSettings settings = presetSettings.getNoiseCaveSettings();

        register(registry, presetName, "aquifer_barrier", settings.getAquiferBarrierFirstOctave(), settings.getAquiferBarrierAmplitudes());
        register(registry, presetName, "aquifer_floodedness", settings.getAquiferFloodednessFirstOctave(), settings.getAquiferFloodednessAmplitudes());
        register(registry, presetName, "aquifer_lava", settings.getAquiferLavaFirstOctave(), settings.getAquiferLavaAmplitudes());
        register(registry, presetName, "aquifer_spread", settings.getAquiferSpreadFirstOctave(), settings.getAquiferSpreadAmplitudes());

        register(registry, presetName, "pillar", settings.getPillarFirstOctave(), settings.getPillarAmplitudes());
        register(registry, presetName, "pillar_rareness", settings.getPillarRarenessFirstOctave(), settings.getPillarRarenessAmplitudes());
        register(registry, presetName, "pillar_thickness", settings.getPillarThicknessFirstOctave(), settings.getPillarThicknessAmplitudes());

        register(registry, presetName, "spaghetti_2d", settings.getSpaghetti2dFirstOctave(), settings.getSpaghetti2dAmplitudes());
        register(registry, presetName, "spaghetti_2d_elevation", settings.getSpaghetti2dElevationFirstOctave(), settings.getSpaghetti2dElevationAmplitudes());
        register(registry, presetName, "spaghetti_2d_modulator", settings.getSpaghetti2dModulatorFirstOctave(), settings.getSpaghetti2dModulatorAmplitudes());
        register(registry, presetName, "spaghetti_2d_thickness", settings.getSpaghetti2dThicknessFirstOctave(), settings.getSpaghetti2dThicknessAmplitudes());

        register(registry, presetName, "spaghetti_3d_1", settings.getSpaghetti3d1FirstOctave(), settings.getSpaghetti3d1Amplitudes());
        register(registry, presetName, "spaghetti_3d_2", settings.getSpaghetti3d2FirstOctave(), settings.getSpaghetti3d2Amplitudes());
        register(registry, presetName, "spaghetti_3d_rarity", settings.getSpaghetti3dRarityFirstOctave(), settings.getSpaghetti3dRarityAmplitudes());
        register(registry, presetName, "spaghetti_3d_thickness", settings.getSpaghetti3dThicknessFirstOctave(), settings.getSpaghetti3dThicknessAmplitudes());

        register(registry, presetName, "spaghetti_roughness", settings.getSpaghettiRoughnessFirstOctave(), settings.getSpaghettiRoughnessAmplitudes());
        register(registry, presetName, "spaghetti_roughness_modulator", settings.getSpaghettiRoughnessModulatorFirstOctave(), settings.getSpaghettiRoughnessModulatorAmplitudes());

        register(registry, presetName, "cave_entrance", settings.getCaveEntranceFirstOctave(), settings.getCaveEntranceAmplitudes());
        register(registry, presetName, "cave_layer", settings.getCaveLayerFirstOctave(), settings.getCaveLayerAmplitudes());
        register(registry, presetName, "cave_cheese", settings.getCaveCheeseFirstOctave(), settings.getCaveCheeseAmplitudes());

        register(registry, presetName, "noodle", settings.getNoodleFirstOctave(), settings.getNoodleAmplitudes());
        register(registry, presetName, "noodle_thickness", settings.getNoodleThicknessFirstOctave(), settings.getNoodleThicknessAmplitudes());
        register(registry, presetName, "noodle_ridge_a", settings.getNoodleRidgeAFirstOctave(), settings.getNoodleRidgeAAmplitudes());
        register(registry, presetName, "noodle_ridge_b", settings.getNoodleRidgeBFirstOctave(), settings.getNoodleRidgeBAmplitudes());

        register(registry, presetName, "ore_veininess", settings.getOreVeininessFirstOctave(), settings.getOreVeininessAmplitudes());
        register(registry, presetName, "ore_vein_a", settings.getOreVeinAFirstOctave(), settings.getOreVeinAAmplitudes());
        register(registry, presetName, "ore_vein_b", settings.getOreVeinBFirstOctave(), settings.getOreVeinBAmplitudes());
        register(registry, presetName, "ore_gap", settings.getOreGapFirstOctave(), settings.getOreGapAmplitudes());
    }

    private static void register(
            WritableRegistry<NormalNoise.NoiseParameters> registry,
            String presetName,
            String noiseName,
            int firstOctave,
            List<Double> amplitudes
    ) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, presetName + "/" + noiseName);
        ResourceKey<NormalNoise.NoiseParameters> key = ResourceKey.create(Registries.NOISE, id);
        if (registry.containsKey(key)) {
            return;
        }
        registry.register(key, new NormalNoise.NoiseParameters(firstOctave, amplitudes), RegistrationInfo.BUILT_IN);
    }
}
