package com.pg85.otg.client.preview.world;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

public class PreviewChunk {

    private final int minY;
    private final int height;
    private final BlockState[] blocks;        // flat array: [x + z*16 + (y-minY)*256]
    @SuppressWarnings("unchecked")
    private final Holder<Biome>[] biomes;     // quarter-res: [bx + bz*4 + by*16]

    @SuppressWarnings("unchecked")
    public PreviewChunk(int minY, int height) {
        this.minY = minY;
        this.height = height;
        this.blocks = new BlockState[16 * 16 * height];
        this.biomes = new Holder[4 * 4 * (height / 4)];
        Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
    }

    public BlockState getBlockState(int x, int y, int z) {
        int ry = y - minY;
        if (x < 0 || x >= 16 || z < 0 || z >= 16 || ry < 0 || ry >= height) {
            return Blocks.AIR.defaultBlockState();
        }
        return blocks[x + z * 16 + ry * 256];
    }

    public void setBlockState(int x, int y, int z, BlockState state) {
        int ry = y - minY;
        if (x >= 0 && x < 16 && z >= 0 && z < 16 && ry >= 0 && ry < height) {
            blocks[x + z * 16 + ry * 256] = state;
        }
    }

    public Holder<Biome> getBiome(int x, int y, int z) {
        int bx = (x & 15) >> 2;
        int bz = (z & 15) >> 2;
        int by = (y - minY) >> 2;
        by = Math.clamp(by, 0, (height / 4) - 1);
        int idx = bx + bz * 4 + by * 16;
        return idx >= 0 && idx < biomes.length && biomes[idx] != null
            ? biomes[idx] : null;
    }

    public void setBiome(int bx, int by, int bz, Holder<Biome> biome) {
        int idx = bx + bz * 4 + by * 16;
        if (idx >= 0 && idx < biomes.length) {
            biomes[idx] = biome;
        }
    }

    public int getMinY() { return minY; }
    public int getHeight() { return height; }
}
