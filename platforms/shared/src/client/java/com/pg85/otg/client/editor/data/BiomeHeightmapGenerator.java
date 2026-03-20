package com.pg85.otg.client.editor.data;

import com.pg85.otg.client.preview.world.PreviewWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

import java.util.List;

/**
 * Generates a 3D terrain heightmap from biome settings for preview.
 * No MC server required — pure noise math + block placement.
 */
public class BiomeHeightmapGenerator {

    private static final int BASE_HEIGHT = 64;
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    public record GenerationResult(Vector3f center, float radius) {}

    /**
     * Generate terrain preview from biome property values.
     *
     * @param world       PreviewWorld to fill with blocks
     * @param properties  biome PropertyValues (searches for BiomeHeight, BiomeVolatility, WaterLevelMax)
     * @return center and radius for camera fitting
     */
    public static GenerationResult generate(PreviewWorld world, List<PropertyValue> properties, long seed, int size) {
        world.clear();

        float biomeHeight = getFloat(properties, "BiomeHeight", 0.1f);
        float biomeVolatility = getFloat(properties, "BiomeVolatility", 0.3f);
        int waterLevel = getInt(properties, "WaterLevelMax", 63);

        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed));

        int minY = 256, maxY = 0;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                // Multi-octave noise modulated by biome settings
                double n = 0;
                n += noise.noise(x * 0.03, 0, z * 0.03) * 1.0;
                n += noise.noise(x * 0.06, 0, z * 0.06) * 0.5;
                n += noise.noise(x * 0.12, 0, z * 0.12) * 0.25;

                // BiomeHeight shifts the base, BiomeVolatility scales the amplitude
                int height = BASE_HEIGHT + (int)(biomeHeight * 20 + n * biomeVolatility * 30);
                height = Math.max(1, Math.min(255, height));

                if (height < minY) minY = height;
                if (height > maxY) maxY = height;

                // Bedrock
                world.setBlockState(new BlockPos(x, 0, z), BEDROCK);

                // Stone fill
                for (int y = 1; y < height - 3; y++) {
                    world.setBlockState(new BlockPos(x, y, z), STONE);
                }

                // Dirt layers
                for (int y = Math.max(1, height - 3); y < height; y++) {
                    world.setBlockState(new BlockPos(x, y, z), DIRT);
                }

                // Surface block
                if (height <= waterLevel + 2) {
                    // Beach sand near water
                    world.setBlockState(new BlockPos(x, height, z), SAND);
                } else {
                    world.setBlockState(new BlockPos(x, height, z), GRASS);
                }

                // Water fill
                for (int y = height + 1; y <= waterLevel; y++) {
                    world.setBlockState(new BlockPos(x, y, z), WATER);
                }
            }
        }

        // Center of the terrain
        float centerY = (minY + maxY) / 2f;
        Vector3f center = new Vector3f(size / 2f, centerY, size / 2f);
        float radius = Math.max(size, maxY - minY + 20);

        return new GenerationResult(center, radius);
    }

    private static float getFloat(List<PropertyValue> properties, String name, float defaultVal) {
        for (PropertyValue pv : properties) {
            if (pv.getDefinition().name().equals(name)) {
                try { return Float.parseFloat(pv.getValue()); }
                catch (NumberFormatException e) { return defaultVal; }
            }
        }
        return defaultVal;
    }

    private static int getInt(List<PropertyValue> properties, String name, int defaultVal) {
        for (PropertyValue pv : properties) {
            if (pv.getDefinition().name().equals(name)) {
                try { return Integer.parseInt(pv.getValue()); }
                catch (NumberFormatException e) { return defaultVal; }
            }
        }
        return defaultVal;
    }
}
