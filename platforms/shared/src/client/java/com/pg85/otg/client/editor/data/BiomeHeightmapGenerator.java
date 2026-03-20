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
 * Generates terrain heightmap using OTG's actual noise pipeline.
 * Computes noise at grid points (every 4 blocks) and interpolates — same as OTG.
 * All formulas match OTGChunkGenerator.generateNoiseColumn().
 */
public class BiomeHeightmapGenerator {

    private static final double WORLD_GEN_CONSTANT = 684.412;
    private static final float REFERENCE_Y_SECTIONS = 33.5f;
    private static final int NOISE_SECTION_HEIGHT = 8;
    private static final int NOISE_SIZE_Y = 48;
    private static final int NOISE_GRID_SPACING = 4; // blocks per noise grid cell

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    public record GenerationResult(Vector3f center, float radius, int[] heightmap, int size) {}

    /**
     * Generates heightmap and places blocks into world.
     * Can be called from any thread (block placement is thread-safe in PreviewWorld).
     * Caller must do renderer.compileAll() on render thread AFTER this returns.
     */
    public static GenerationResult generate(PreviewWorld world,
                                             List<PropertyValue> biomeProperties,
                                             List<PropertyValue> presetProperties,
                                             long seed, int size) {
        world.clear();

        // Biome settings
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

        boolean useWorldWater = getBool(biomeProperties, "UseWorldWaterLevel", true);
        int waterLevel = useWorldWater
            ? getInt(presetProperties, "WaterLevelMax", 63)
            : getInt(biomeProperties, "WaterLevelMax", 63);
        double fractureH = getFloat(presetProperties, "FractureHorizontal", 0f);
        double fractureV = getFloat(presetProperties, "FractureVertical", 0f);

        float vol = biomeVolatility * 0.9f + 0.1f;
        float h = (biomeHeight * 4.0f - 1.0f) / 8.0f;

        double horizontalScale = WORLD_GEN_CONSTANT * fractureH;
        double verticalScale = WORLD_GEN_CONSTANT * fractureV;
        double horizontalStretch = horizontalScale / 80.0;
        double verticalStretch = verticalScale / 160.0;

        // Noise samplers — same creation order as OTGChunkGenerator
        Random rng = new Random(seed);
        OctavePerlinNoiseSampler lowerNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        OctavePerlinNoiseSampler upperNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));
        OctavePerlinNoiseSampler interpNoise = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-7, 0));
        OctavePerlinNoiseSampler depthNoiseSampler = new OctavePerlinNoiseSampler(rng, IntStream.rangeClosed(-15, 0));

        // === Phase 1: Compute noise columns at grid points (every 4 blocks) ===
        int noiseCountX = size / NOISE_GRID_SPACING + 1;
        int noiseCountZ = size / NOISE_GRID_SPACING + 1;
        double[][] noiseColumns = new double[noiseCountX * noiseCountZ][];

        for (int nx = 0; nx < noiseCountX; nx++) {
            for (int nz = 0; nz < noiseCountZ; nz++) {
                float extraHeight = (float)(getExtraHeightAt(
                    depthNoiseSampler, nx, nz, maxAverageDepth, maxAverageHeight) * 0.2);
                float columnRefY = REFERENCE_Y_SECTIONS * (2.0f + h + extraHeight) / 4.0f;

                double[] column = new double[NOISE_SIZE_Y + 1];
                for (int y = 0; y <= NOISE_SIZE_Y; y++) {
                    double falloff = (columnRefY - y) * 6.0 / vol;
                    if (falloff > 0) falloff *= 4.0;

                    double noise = sampleNoise(nx, y, nz,
                        horizontalScale, verticalScale,
                        horizontalStretch, verticalStretch,
                        volatility1, volatility2,
                        volatilityWeight1, volatilityWeight2,
                        interpNoise, lowerNoise, upperNoise);

                    noise += falloff;

                    double heightDiff = y - columnRefY;
                    if (heightDiff > 4) noise -= (heightDiff - 4) * (heightDiff - 4) * 0.5;

                    int reductionStartY = NOISE_SIZE_Y - 4;
                    if (y > reductionStartY) {
                        double t = ((double) y - reductionStartY) / 4.0;
                        noise = noise + (-10 - noise) * Math.max(0, Math.min(1, t));
                    }

                    column[y] = noise;
                }
                noiseColumns[nx * noiseCountZ + nz] = column;
            }
        }

        // === Phase 2: Interpolate and find surface per block ===
        int[] heightmap = new int[size * size];
        int minSurfaceY = 999, maxSurfaceY = 0;

        for (int bx = 0; bx < size; bx++) {
            for (int bz = 0; bz < size; bz++) {
                int nx = bx / NOISE_GRID_SPACING;
                int nz = bz / NOISE_GRID_SPACING;
                double fracX = (double)(bx % NOISE_GRID_SPACING) / NOISE_GRID_SPACING;
                double fracZ = (double)(bz % NOISE_GRID_SPACING) / NOISE_GRID_SPACING;

                int nx1 = Math.min(nx + 1, noiseCountX - 1);
                int nz1 = Math.min(nz + 1, noiseCountZ - 1);

                double[] col00 = noiseColumns[nx * noiseCountZ + nz];
                double[] col10 = noiseColumns[nx1 * noiseCountZ + nz];
                double[] col01 = noiseColumns[nx * noiseCountZ + nz1];
                double[] col11 = noiseColumns[nx1 * noiseCountZ + nz1];

                int surfaceY = 0;
                outer:
                for (int y = NOISE_SIZE_Y - 1; y >= 0; y--) {
                    for (int subY = NOISE_SECTION_HEIGHT - 1; subY >= 0; subY--) {
                        double fracY = (double) subY / NOISE_SECTION_HEIGHT;
                        int y1 = Math.min(y + 1, NOISE_SIZE_Y);

                        // Trilinear interpolation (same as OTG/ReEdited)
                        double d00 = col00[y] + (col00[y1] - col00[y]) * fracY;
                        double d10 = col10[y] + (col10[y1] - col10[y]) * fracY;
                        double d01 = col01[y] + (col01[y1] - col01[y]) * fracY;
                        double d11 = col11[y] + (col11[y1] - col11[y]) * fracY;

                        double dx0 = d00 + (d10 - d00) * fracX;
                        double dx1 = d01 + (d11 - d01) * fracX;
                        double rawDensity = dx0 + (dx1 - dx0) * fracZ;

                        // Density normalization
                        double density = Math.max(-1, Math.min(1, rawDensity / 200.0));
                        density = density / 2.0 - density * density * density / 24.0;

                        if (density > 0) {
                            surfaceY = y * NOISE_SECTION_HEIGHT + subY;
                            break outer;
                        }
                    }
                }

                surfaceY = Math.max(1, Math.min(319, surfaceY));
                heightmap[bx * size + bz] = surfaceY;
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

        return new GenerationResult(center, radius, heightmap, size);
    }

    // === Noise methods — identical to OTGChunkGenerator ===

    private static double sampleNoise(int x, int y, int z,
            double hScale, double vScale, double hStretch, double vStretch,
            double vol1, double vol2, double volW1, double volW2,
            OctavePerlinNoiseSampler interpNoise,
            OctavePerlinNoiseSampler lowerNoise, OctavePerlinNoiseSampler upperNoise) {

        double delta = getInterpolationNoise(interpNoise, x, y, z, hStretch, vStretch);

        if (delta < volW1) {
            return getInterpolatedNoise(lowerNoise, x, y, z, hScale, vScale) / 512.0 * vol1;
        } else if (delta > volW2) {
            return getInterpolatedNoise(upperNoise, x, y, z, hScale, vScale) / 512.0 * vol2;
        } else {
            double lower = getInterpolatedNoise(lowerNoise, x, y, z, hScale, vScale) / 512.0 * vol1;
            double upper = getInterpolatedNoise(upperNoise, x, y, z, hScale, vScale) / 512.0 * vol2;
            double t = (volW2 != volW1) ? (delta - volW1) / (volW2 - volW1) : 0.5;
            return lower + (upper - lower) * t;
        }
    }

    private static double getInterpolationNoise(OctavePerlinNoiseSampler sampler,
                                                  int x, int y, int z, double hS, double vS) {
        double interp = 0.0;
        double amp = 1.0;
        for (int i = 0; i < 8; i++) {
            PerlinNoiseSampler oct = sampler.getOctave(i);
            if (oct != null) {
                interp += oct.sample(
                    OctavePerlinNoiseSampler.maintainPrecision(x * hS * amp),
                    OctavePerlinNoiseSampler.maintainPrecision(y * vS * amp),
                    OctavePerlinNoiseSampler.maintainPrecision(z * hS * amp),
                    vS * amp, (double) y * vS * amp
                ) / amp;
            }
            amp /= 2.0;
        }
        return (interp / 10.0 + 1.0) / 2.0;
    }

    private static double getInterpolatedNoise(OctavePerlinNoiseSampler sampler,
                                                 int x, int y, int z, double hS, double vS) {
        double noise = 0.0;
        double amp = 1.0;
        for (int i = 0; i < 16; i++) {
            PerlinNoiseSampler oct = sampler.getOctave(i);
            if (oct != null) {
                noise += oct.sample(
                    OctavePerlinNoiseSampler.maintainPrecision(x * hS * amp),
                    OctavePerlinNoiseSampler.maintainPrecision(y * vS * amp),
                    OctavePerlinNoiseSampler.maintainPrecision(z * hS * amp),
                    vS * amp, (double) y * vS * amp
                ) / amp;
            }
            amp /= 2.0;
        }
        return noise;
    }

    private static double getExtraHeightAt(OctavePerlinNoiseSampler depthNoise,
                                            int x, int z, double maxAvgDepth, double maxAvgHeight) {
        double h = depthNoise.sample(x * 200, 10.0, z * 200, 1.0, 0.0, true) * 65535.0 / 8000.0;
        if (h < 0.0) h = -h * 0.3;
        h = h * 3.0 - 2.0;
        if (h < 0.0) {
            h /= 2.0;
            if (h < -1.0) h = -1.0;
            if (maxAvgDepth > 0.1) h /= maxAvgDepth;
            h /= 1.4;
            h /= 2.0;
        } else {
            if (h > 1.0) h = 1.0;
            h *= maxAvgHeight;
            h /= 8.0;
        }
        return h;
    }

    // --- Property helpers ---

    private static boolean getBool(List<PropertyValue> p, String n, boolean d) {
        if (p == null) return d;
        for (PropertyValue v : p) { if (v.getDefinition().name().equals(n)) return "true".equalsIgnoreCase(v.getValue()); }
        return d;
    }

    private static float getFloat(List<PropertyValue> p, String n, float d) {
        if (p == null) return d;
        for (PropertyValue v : p) { if (v.getDefinition().name().equals(n)) try { return Float.parseFloat(v.getValue()); } catch (NumberFormatException e) { return d; } }
        return d;
    }

    private static int getInt(List<PropertyValue> p, String n, int d) {
        if (p == null) return d;
        for (PropertyValue v : p) { if (v.getDefinition().name().equals(n)) try { return Integer.parseInt(v.getValue()); } catch (NumberFormatException e) { return d; } }
        return d;
    }
}
