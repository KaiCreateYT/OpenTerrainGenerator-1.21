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
 * Generates terrain heightmap preview using OTG's actual noise pipeline.
 * Uses OctavePerlinNoiseSampler directly — same noise code as OTGChunkGenerator.
 *
 * Difference from real OTG: no biome blending (single biome), no CHC.
 * All formulas match OTGChunkGenerator.generateNoiseColumn() line-for-line.
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

        // Biome settings (same names as BiomeTerrainSettings)
        float biomeHeight = getFloat(biomeProperties, "BiomeHeight", 0.1f);
        float biomeVolatility = getFloat(biomeProperties, "BiomeVolatility", 0.3f);
        double volatility1 = getFloat(biomeProperties, "Volatility1", 0.0f);
        double volatility2 = getFloat(biomeProperties, "Volatility2", 0.0f);
        double volatilityWeight1 = getFloat(biomeProperties, "VolatilityWeight1", 0.5f);
        double volatilityWeight2 = getFloat(biomeProperties, "VolatilityWeight2", 0.45f);
        double maxAverageDepth = getFloat(biomeProperties, "MaxAverageDepth", 0.0f);
        double maxAverageHeight = getFloat(biomeProperties, "MaxAverageHeight", 0.0f);

        if (volatility1 == 0) volatility1 = biomeVolatility;
        if (volatility2 == 0) volatility2 = biomeVolatility;

        // Preset settings
        boolean useWorldWater = getBool(biomeProperties, "UseWorldWaterLevel", true);
        int waterLevel = useWorldWater
            ? getInt(presetProperties, "WaterLevelMax", 63)
            : getInt(biomeProperties, "WaterLevelMax", 63);
        double fractureH = getFloat(presetProperties, "FractureHorizontal", 0f);
        double fractureV = getFloat(presetProperties, "FractureVertical", 0f);

        // OTG height transformation (OTGChunkGenerator lines 463-464)
        float vol = biomeVolatility * 0.9f + 0.1f;
        float h = (biomeHeight * 4.0f - 1.0f) / 8.0f;

        // Fracture scaling (OTGChunkGenerator line 481-482)
        double horizontalScale = WORLD_GEN_CONSTANT * fractureH;
        double verticalScale = WORLD_GEN_CONSTANT * fractureV;

        // Noise samplers — same creation order as OTGChunkGenerator constructor
        Random rng = new Random(seed);
        OctavePerlinNoiseSampler lowerNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        OctavePerlinNoiseSampler upperNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        OctavePerlinNoiseSampler interpNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-7, 0));
        OctavePerlinNoiseSampler depthNoiseSampler = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));

        int minSurfaceY = 999, maxSurfaceY = 0;

        for (int bx = 0; bx < size; bx++) {
            for (int bz = 0; bz < size; bz++) {
                int noiseX = bx / 4;
                int noiseZ = bz / 4;

                // depthNoise extraHeight (OTGChunkGenerator line 460)
                float extraHeight = (float)(getExtraHeightAt(
                    depthNoiseSampler, noiseX, noiseZ, maxAverageDepth, maxAverageHeight) * 0.2);

                // Reference Y with extraHeight (OTGChunkGenerator line 467)
                float columnRefY = REFERENCE_Y_SECTIONS * (2.0f + h + extraHeight) / 4.0f;

                // Generate noise column (OTGChunkGenerator lines 473-527)
                double[] noiseColumn = new double[NOISE_SIZE_Y + 1];
                for (int y = 0; y <= NOISE_SIZE_Y; y++) {
                    double falloff = (columnRefY - y) * 6.0 / vol;
                    if (falloff > 0) falloff *= 4.0;

                    double hScale = horizontalScale;
                    double vScale = verticalScale;

                    double noise = sampleNoise(
                        noiseX, y, noiseZ,
                        hScale, vScale,
                        hScale / 80.0, vScale / 160.0,
                        volatility1, volatility2,
                        volatilityWeight1, volatilityWeight2,
                        interpNoise, lowerNoise, upperNoise
                    );

                    noise += falloff;

                    double heightDiff = y - columnRefY;
                    if (heightDiff > 4) {
                        noise -= (heightDiff - 4) * (heightDiff - 4) * 0.5;
                    }

                    int reductionStartY = NOISE_SIZE_Y - 4;
                    if (y > reductionStartY) {
                        double t = ((double) y - reductionStartY) / 4.0;
                        noise = noise + (-10 - noise) * Math.max(0, Math.min(1, t));
                    }

                    noiseColumn[y] = noise;
                }

                // Find surface — scan top-down with Y interpolation within sections
                int surfaceY = 0;
                outer:
                for (int y = NOISE_SIZE_Y - 1; y >= 0; y--) {
                    for (int subY = NOISE_SECTION_HEIGHT - 1; subY >= 0; subY--) {
                        double fracY = (double) subY / NOISE_SECTION_HEIGHT;
                        double rawDensity = noiseColumn[y] + (noiseColumn[Math.min(y + 1, NOISE_SIZE_Y)] - noiseColumn[y]) * fracY;
                        double density = Math.max(-1, Math.min(1, rawDensity / 200.0));
                        density = density / 2.0 - density * density * density / 24.0;

                        if (density > 0) {
                            surfaceY = y * NOISE_SECTION_HEIGHT + subY;
                            break outer;
                        }
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
     * Same as OTGChunkGenerator.sampleNoise() — volatility weight blending.
     * Uses PerlinNoiseSampler directly for per-octave access.
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
            // Same lerp as OTGChunkGenerator — MathHelper.lerp(delta, lower, upper)
            double t = (volWeight2 != volWeight1) ? (delta - volWeight1) / (volWeight2 - volWeight1) : 0.5;
            return lower + (upper - lower) * t;
        }
    }

    /**
     * Same as OTGChunkGenerator.getInterpolationNoise() — 8 octaves
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
                    (double) y * vStretch * amplitude
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
                    (double) y * vScale * amplitude
                ) / amplitude;
            }
            amplitude /= 2.0;
        }
        return noise;
    }

    /**
     * Same as OTGChunkGenerator.getExtraHeightAt() — depth noise variation.
     */
    private static double getExtraHeightAt(OctavePerlinNoiseSampler depthNoise,
                                            int x, int z,
                                            double maxAverageDepth, double maxAverageHeight) {
        double noiseHeight = depthNoise.sample(x * 200, 10.0, z * 200, 1.0, 0.0, true) * 65535.0 / 8000.0;

        if (noiseHeight < 0.0) noiseHeight = -noiseHeight * 0.3;
        noiseHeight = noiseHeight * 3.0 - 2.0;

        if (noiseHeight < 0.0) {
            noiseHeight /= 2.0;
            if (noiseHeight < -1.0) noiseHeight = -1.0;
            if (maxAverageDepth > 0.1) noiseHeight /= maxAverageDepth;
            noiseHeight /= 1.4;
            noiseHeight /= 2.0;
        } else {
            if (noiseHeight > 1.0) noiseHeight = 1.0;
            noiseHeight *= maxAverageHeight;
            noiseHeight /= 8.0;
        }

        return noiseHeight;
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
