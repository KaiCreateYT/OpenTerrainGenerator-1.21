package com.pg85.otg.shared.biome;

import java.util.function.ToIntBiFunction;

/**
 * Interface for OTG-specific biome provider methods shared across platforms.
 * Both OTGFabricBiomeProvider and OTGNeoForgeBiomeProvider implement this.
 */
public interface IOTGBiomeProvider {
    String getPresetFolderName();
    void setSeed(long seed);
    void setSurfaceHeightEstimator(ToIntBiFunction<Integer, Integer> estimator);
}
