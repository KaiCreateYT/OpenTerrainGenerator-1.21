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
 * Generates a 3D terrain heightmap from biome + preset settings for preview.
 * No MC server required — pure noise math + block placement.
 *
 * Uses biome settings: BiomeHeight, BiomeVolatility
 * Uses preset settings: WaterLevelMax, WorldHeightScaleBits, FractureHorizontal, FractureVertical
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
     * @param biomeProperties  from .bc file (BiomeHeight, BiomeVolatility)
     * @param presetProperties from DimensionPresetConfig.ini (WaterLevelMax, WorldHeightScaleBits, Fracture*)
     */
    public static GenerationResult generate(PreviewWorld world,
                                             List<PropertyValue> biomeProperties,
                                             List<PropertyValue> presetProperties,
                                             long seed, int size) {
        world.clear();

        // Biome settings
        float biomeHeight = getFloat(biomeProperties, "BiomeHeight", 0.1f);
        float biomeVolatility = getFloat(biomeProperties, "BiomeVolatility", 0.3f);

        // Preset settings
        int waterLevel = getInt(presetProperties, "WaterLevelMax", 63);
        int heightScaleBits = getInt(presetProperties, "WorldHeightScaleBits", 8);
        float fractureH = getFloat(presetProperties, "FractureHorizontal", 0f);
        float fractureV = getFloat(presetProperties, "FractureVertical", 0f);

        // WorldHeightScaleBits: 8 = 256 height, 7 = 128, 6 = 64, 9 = 512
        float heightScale = (1 << heightScaleBits) / 256f;

        // FractureHorizontal stretches/compresses terrain horizontally
        // positive = wider features, negative = narrower
        float hScale = 1.0f + fractureH * 0.002f;

        // FractureVertical stretches/compresses terrain vertically
        // positive = taller, negative = flatter
        float vScale = 1.0f + fractureV * 0.002f;

        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed));

        int minY = 256, maxY = 0;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                // Sample coordinates scaled by fracture horizontal
                double sx = x * 0.03 / hScale;
                double sz = z * 0.03 / hScale;

                // Multi-octave noise
                double n = 0;
                n += noise.noise(sx, 0, sz) * 1.0;
                n += noise.noise(sx * 2, 0, sz * 2) * 0.5;
                n += noise.noise(sx * 4, 0, sz * 4) * 0.25;

                // Apply biome settings + preset scaling
                float rawHeight = biomeHeight * 20 + (float) n * biomeVolatility * 30;
                rawHeight *= vScale * heightScale;

                int height = BASE_HEIGHT + (int) rawHeight;
                height = Math.max(1, Math.min(319, height));

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

        float centerY = (minY + maxY) / 2f;
        Vector3f center = new Vector3f(size / 2f, centerY, size / 2f);
        float radius = Math.max(size, maxY - minY + 20);

        return new GenerationResult(center, radius);
    }

    private static float getFloat(List<PropertyValue> properties, String name, float defaultVal) {
        if (properties == null) return defaultVal;
        for (PropertyValue pv : properties) {
            if (pv.getDefinition().name().equals(name)) {
                try { return Float.parseFloat(pv.getValue()); }
                catch (NumberFormatException e) { return defaultVal; }
            }
        }
        return defaultVal;
    }

    private static int getInt(List<PropertyValue> properties, String name, int defaultVal) {
        if (properties == null) return defaultVal;
        for (PropertyValue pv : properties) {
            if (pv.getDefinition().name().equals(name)) {
                try { return Integer.parseInt(pv.getValue()); }
                catch (NumberFormatException e) { return defaultVal; }
            }
        }
        return defaultVal;
    }
}
