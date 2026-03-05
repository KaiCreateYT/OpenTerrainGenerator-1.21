package com.pg85.otg.client.preview.world;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class ChunkGenerationManager {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger completedChunks = new AtomicInteger(0);
    private volatile int totalChunks;

    /**
     * Generate chunks in spiral order from center and feed them to PreviewWorld.
     * This blocks until all chunks are generated or cancelled.
     * Must be called on the server thread (via ServerLevel.getServer().execute()).
     *
     * @param level          ServerLevel from TempServerManager
     * @param previewWorld   Target PreviewWorld to populate
     * @param radiusChunks   Radius in chunks (e.g. 4 = 8x8 area, generates (2*4)²=64 chunks)
     * @param status         Target ChunkStatus (SURFACE, CARVERS, or FULL)
     * @param onProgress     Callback: (completed, total) — may be called from server thread
     * @param onChunkReady   Callback per completed chunk (for progressive rendering)
     */
    public void generate(ServerLevel level, PreviewWorld previewWorld, int radiusChunks,
                         ChunkStatus status, BiConsumer<Integer, Integer> onProgress,
                         Runnable onChunkReady) {
        cancelled.set(false);
        completedChunks.set(0);

        List<ChunkPos> positions = spiralOrder(radiusChunks);
        totalChunks = positions.size();

        ServerChunkCache chunkCache = level.getChunkSource();

        for (ChunkPos pos : positions) {
            if (cancelled.get()) break;

            ChunkAccess chunk = chunkCache.getChunk(pos.x, pos.z, status, true);
            if (chunk != null) {
                previewWorld.addChunkFromAccess(chunk);
                int done = completedChunks.incrementAndGet();
                onProgress.accept(done, totalChunks);
                onChunkReady.run();
            }
        }
    }

    public void cancel() {
        cancelled.set(true);
    }

    public float getProgress() {
        int total = totalChunks;
        return total > 0 ? (float) completedChunks.get() / total : 0f;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getCompletedChunks() {
        return completedChunks.get();
    }

    /**
     * Generate chunk positions in spiral order from (0,0) outward.
     * For radiusChunks=4, generates (2*4)²=64 positions from (-4,-4) to (3,3).
     * The spiral covers (2r+1)² positions total; bounds check clips to (2r)².
     */
    static List<ChunkPos> spiralOrder(int radius) {
        int diameter = radius * 2;
        List<ChunkPos> result = new ArrayList<>(diameter * diameter);
        int x = 0, z = 0;
        int dx = 0, dz = -1;
        int side = diameter + 1;
        int maxSteps = side * side;

        for (int i = 0; i < maxSteps; i++) {
            if (x >= -radius && x < radius && z >= -radius && z < radius) {
                result.add(new ChunkPos(x, z));
            }
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int temp = dx;
                dx = -dz;
                dz = temp;
            }
            x += dx;
            z += dz;
        }
        return result;
    }
}
