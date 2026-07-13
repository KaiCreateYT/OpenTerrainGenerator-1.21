package com.pg85.otg.platform.noise;

import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import com.pg85.otg.config.settings.preset.NoiseCaveSettings;

/**
 * Thin wrapper to build a NoiseRouter using vanilla Overworld defaults.
 * Later can be parameterized with OTGNoiseCaveSettings to adjust amplitudes.
 */
public class OTGNoiseRouterBuilder {
    private final HolderGetter<DensityFunction> densityGetter;
    private final HolderGetter<NormalNoise.NoiseParameters> noiseGetter;

    public OTGNoiseRouterBuilder(HolderGetter<DensityFunction> densityGetter, HolderGetter<NormalNoise.NoiseParameters> noiseGetter) {
        this.densityGetter = densityGetter;
        this.noiseGetter = noiseGetter;
    }

    public NoiseRouter buildVanillaLike(boolean large, boolean amplified) {
        return buildWithSettings(null, null, large, amplified);
    }

    /**
     * Builds a NoiseRouter, optionally tweaking final_density using NoiseCaveSettings
     * (offset/scale or disabling caves entirely). Everything else stays vanilla.
     */
    public NoiseRouter buildWithSettings(NoiseCaveSettings settings, String presetName, boolean large, boolean amplified) {
        if (settings == null || presetName == null || presetName.isEmpty()) {
            return callOverworldRouter(large, amplified);
        }
        return OTGNoiseRouterData.overworld(this.densityGetter, this.noiseGetter, settings, presetName, large, amplified);
    }

    private NoiseRouter callOverworldRouter(boolean large, boolean amplified) {
        return NoiseRouterData.overworld(this.densityGetter, this.noiseGetter, large, amplified);
    }
}
