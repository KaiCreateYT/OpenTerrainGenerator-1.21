package com.pg85.otg.client.editor.data;

import com.pg85.otg.client.preview.world.PreviewWorld;
import com.pg85.otg.gen.noise.OctavePerlinNoiseSampler;
import com.pg85.otg.gen.noise.PerlinNoiseSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Generates terrain heightmap preview using OTG's actual noise formulas.
 * Uses same OctavePerlinNoiseSampler, falloff, volatility weighting,
 * and anti-floating-terrain logic as OTGChunkGenerator.generateNoiseColumn().
 *
 * Difference from real OTG: no biome blending (preview shows single biome),
 * no CustomHeightControl, no depthNoise extraHeight.
 */
public class BiomeHeightmapGenerator {

    // Same constants as OTGChunkGenerator
    private static final double WORLD_GEN_CONSTANT = 684.412;
    private static final float REFERENCE_Y_SECTIONS = 33.5f;
    private static final int NOISE_SECTION_HEIGHT = 8;
    private static final int NOISE_SIZE_Y = 48; // 384 / 8

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    public record GenerationResult(Vector3f center, float radius) {}

    public static GenerationResult generate(PreviewWorld world,
                                             List<PropertyValue> biomeProperties,
                                             List<PropertyValue> presetProperties,
                                             long seed, int size) {
        world.clear();

        // Biome settings (same names as BiomeTerrainSettings fields)
        float biomeHeight = getFloat(biomeProperties, "BiomeHeight", 0.1f);
        float biomeVolatility = getFloat(biomeProperties, "BiomeVolatility", 0.3f);
        float volatility1 = getFloat(biomeProperties, "Volatility1", 0.0f);
        float volatility2 = getFloat(biomeProperties, "Volatility2", 0.0f);
        float volatilityWeight1 = getFloat(biomeProperties, "VolatilityWeight1", 0.5f);
        float volatilityWeight2 = getFloat(biomeProperties, "VolatilityWeight2", 0.45f);
        float maxAverageDepth = getFloat(biomeProperties, "MaxAverageDepth", 0.0f);
        float maxAverageHeight = getFloat(biomeProperties, "MaxAverageHeight", 0.0f);

        // If volatility1/2 are zero, default to biomeVolatility
        if (volatility1 == 0) volatility1 = biomeVolatility;
        if (volatility2 == 0) volatility2 = biomeVolatility;

        // Preset settings
        boolean useWorldWater = getBool(biomeProperties, "UseWorldWaterLevel", true);
        int waterLevel = useWorldWater
            ? getInt(presetProperties, "WaterLevelMax", 63)
            : getInt(biomeProperties, "WaterLevelMax", 63);
        float fractureH = getFloat(presetProperties, "FractureHorizontal", 0f);
        float fractureV = getFloat(presetProperties, "FractureVertical", 0f);

        // OTG height transformation (same as OTGChunkGenerator lines 463-467)
        float vol = biomeVolatility * 0.9f + 0.1f;
        float h = (biomeHeight * 4.0f - 1.0f) / 8.0f;
        float referenceY = REFERENCE_Y_SECTIONS * (2.0f + h) / 4.0f;

        // Fracture scaling (same as OTGChunkGenerator line 481-482)
        double horizontalScale = WORLD_GEN_CONSTANT * (1.0 + fractureH * 0.001);
        double verticalScale = WORLD_GEN_CONSTANT * (1.0 + fractureV * 0.001);
        double horizontalStretch = horizontalScale / 80.0;
        double verticalStretch = verticalScale / 160.0;

        // Create noise samplers — same octave setup as OTGChunkGenerator constructor
        Random rng = new Random(seed);
        OctavePerlinNoiseSampler lowerNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        OctavePerlinNoiseSampler upperNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        OctavePerlinNoiseSampler interpNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-7, 0));

        int minSurfaceY = 999, maxSurfaceY = 0;

        for (int bx = 0; bx < size; bx++) {
            for (int bz = 0; bz < size; bz++) {
                // Noise coordinates (OTG uses noise grid spacing of 4 blocks)
                int noiseX = bx / 4;
                int noiseZ = bz / 4;

                // Find surface: scan noise column top-down for first positive density
                int surfaceY = 0;
                for (int y = NOISE_SIZE_Y; y >= 0; y--) {
                    double falloff = (referenceY - y) * 6.0 / vol;
                    if (falloff > 0) falloff *= 4.0;

                    // sampleNoise — same as OTGChunkGenerator.sampleNoise()
                    double noise = sampleNoise(
                        noiseX, y, noiseZ,
                        horizontalScale, verticalScale,
                        horizontalStretch, verticalStretch,
                        volatility1, volatility2,
                        volatilityWeight1, volatilityWeight2,
                        interpNoise, lowerNoise, upperNoise
                    );

                    noise += falloff;

                    // Anti-floating terrain (same as OTGChunkGenerator line 503-506)
                    double heightDiff = y - referenceY;
                    if (heightDiff > 4) {
                        noise -= (heightDiff - 4) * (heightDiff - 4) * 0.5;
                    }

                    // Top layer reduction (same as OTGChunkGenerator line 511-514)
                    int reductionStartY = NOISE_SIZE_Y - 4;
                    if (y > reductionStartY) {
                        double t = ((double) y - reductionStartY) / 4.0;
                        noise = noise + (-10 - noise) * Math.max(0, Math.min(1, t));
                    }

                    if (noise > 0) {
                        surfaceY = y * NOISE_SECTION_HEIGHT;
                        break;
                    }
                }

                surfaceY = Math.max(1, Math.min(319, surfaceY));
                if (surfaceY < minSurfaceY) minSurfaceY = surfaceY;
                if (surfaceY > maxSurfaceY) maxSurfaceY = surfaceY;

                // Place blocks
                world.setBlockState(new BlockPos(bx, 0, bz), BEDROCK);
                for (int y = 1; y < surfaceY - 3; y++) {
                    world.setBlockState(new BlockPos(bx, y, bz), STONE);
                }
                for (int y = Math.max(1, surfaceY - 3); y < surfaceY; y++) {
                    world.setBlockState(new BlockPos(bx, y, bz), DIRT);
                }
                if (surfaceY <= waterLevel + 2) {
                    world.setBlockState(new BlockPos(bx, surfaceY, bz), SAND);
                } else {
                    world.setBlockState(new BlockPos(bx, surfaceY, bz), GRASS);
                }
                for (int y = surfaceY + 1; y <= waterLevel; y++) {
                    world.setBlockState(new BlockPos(bx, y, bz), WATER);
                }
            }
        }

        float centerY = (minSurfaceY + maxSurfaceY) / 2f;
        Vector3f center = new Vector3f(size / 2f, centerY, size / 2f);
        float radius = Math.max(size, maxSurfaceY - minSurfaceY + 20);

        return new GenerationResult(center, radius);
    }

    /**
     * Same logic as OTGChunkGenerator.sampleNoise() — volatility weight blending.
     */
    private static double sampleNoise(
            int x, int y, int z,
            double horizontalScale, double verticalScale,
            double horizontalStretch, double verticalStretch,
            double vol1, double vol2,
            double volWeight1, double volWeight2,
            OctavePerlinNoiseSampler interpNoise,
            OctavePerlinNoiseSampler lowerNoise,
            OctavePerlinNoiseSampler upperNoise) {

        double delta = getInterpolationNoise(interpNoise, x, y, z, horizontalStretch, verticalStretch);

        if (delta < volWeight1) {
            return getInterpolatedNoise(lowerNoise, x, y, z, horizontalScale, verticalScale) / 512.0 * vol1;
        } else if (delta > volWeight2) {
            return getInterpolatedNoise(upperNoise, x, y, z, horizontalScale, verticalScale) / 512.0 * vol2;
        } else {
            double lower = getInterpolatedNoise(lowerNoise, x, y, z, horizontalScale, verticalScale) / 512.0 * vol1;
            double upper = getInterpolatedNoise(upperNoise, x, y, z, horizontalScale, verticalScale) / 512.0 * vol2;
            double t = (delta - volWeight1) / (volWeight2 - volWeight1);
            return lower + (upper - lower) * t;
        }
    }

    /**
     * Same as OTGChunkGenerator.getInterpolationNoise() — 8 octaves, normalized to [0,1]
     */
    private static double getInterpolationNoise(OctavePerlinNoiseSampler sampler,
                                                  int x, int y, int z,
                                                  double hStretch, double vStretch) {
        double interpolation = 0.0;
        double amplitude = 1.0;
        for (int i = 0; i < 8; i++) {
            PerlinNoiseSampler octave = sampler.getOctave(i);
            if (octave != null) {
                interpolation += octave.sample(
                    OctavePerlinNoiseSampler.maintainPrecision(x * hStretch * amplitude),
                    OctavePerlinNoiseSampler.maintainPrecision(y * vStretch * amplitude),
                    OctavePerlinNoiseSampler.maintainPrecision(z * hStretch * amplitude),
                    vStretch * amplitude,
                    y * vStretch * amplitude
                ) / amplitude;
            }
            amplitude /= 2.0;
        }
        return (interpolation / 10.0 + 1.0) / 2.0;
    }

    /**
     * Same as OTGChunkGenerator.getInterpolatedNoise() — 16 octaves
     */
    private static double getInterpolatedNoise(OctavePerlinNoiseSampler sampler,
                                                 int x, int y, int z,
                                                 double hScale, double vScale) {
        double noise = 0.0;
        double amplitude = 1.0;
        for (int i = 0; i < 16; i++) {
            PerlinNoiseSampler octave = sampler.getOctave(i);
            if (octave != null) {
                noise += octave.sample(
                    OctavePerlinNoiseSampler.maintainPrecision(x * hScale * amplitude),
                    OctavePerlinNoiseSampler.maintainPrecision(y * vScale * amplitude),
                    OctavePerlinNoiseSampler.maintainPrecision(z * hScale * amplitude),
                    vScale * amplitude,
                    y * vScale * amplitude
                ) / amplitude;
            }
            amplitude /= 2.0;
        }
        return noise;
    }

    // --- Property helpers ---

    private static boolean getBool(List<PropertyValue> props, String name, boolean def) {
        if (props == null) return def;
        for (PropertyValue pv : props) {
            if (pv.getDefinition().name().equals(name)) return "true".equalsIgnoreCase(pv.getValue());
        }
        return def;
    }

    private static float getFloat(List<PropertyValue> props, String name, float def) {
        if (props == null) return def;
        for (PropertyValue pv : props) {
            if (pv.getDefinition().name().equals(name)) {
                try { return Float.parseFloat(pv.getValue()); }
                catch (NumberFormatException e) { return def; }
            }
        }
        return def;
    }

    private static int getInt(List<PropertyValue> props, String name, int def) {
        if (props == null) return def;
        for (PropertyValue pv : props) {
            if (pv.getDefinition().name().equals(name)) {
                try { return Integer.parseInt(pv.getValue()); }
                catch (NumberFormatException e) { return def; }
            }
        }
        return def;
    }
}
