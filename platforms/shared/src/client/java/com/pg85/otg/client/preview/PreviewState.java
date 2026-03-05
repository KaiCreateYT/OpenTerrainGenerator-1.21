package com.pg85.otg.client.preview;

import com.pg85.otg.client.preview.world.ChunkGenerationManager;
import com.pg85.otg.client.preview.world.PreviewWorld;
import com.pg85.otg.client.preview.world.TempServerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static state machine for the preview generation flow.
 *
 * States:
 * IDLE -> WAITING_FOR_SERVER -> GENERATING_CHUNKS -> COMPILING -> DONE
 *
 * The server stays alive after generation completes — it is reused for BO preview.
 * Server only stops on reset() or screen close.
 */
public class PreviewState {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewState.class);

    public enum Phase { IDLE, WAITING_FOR_SERVER, GENERATING_CHUNKS, COMPILING, DONE }

    private static volatile Phase phase = Phase.IDLE;
    private static volatile String statusText = "";

    // Shared data — survives screen transitions
    private static final PreviewWorld previewWorld = new PreviewWorld();
    private static final PreviewRenderer renderer = new PreviewRenderer(previewWorld);
    private static final TempServerManager serverManager = new TempServerManager();
    private static final ChunkGenerationManager chunkManager = new ChunkGenerationManager();
    private static final OrbitCamera camera = new OrbitCamera();

    // Generation params (set before startServer)
    private static int radiusChunks = 4;
    private static ChunkStatus chunkStatus = ChunkStatus.FULL;

    public static Phase getPhase() { return phase; }
    public static String getStatusText() { return statusText; }
    public static PreviewWorld getPreviewWorld() { return previewWorld; }
    public static PreviewRenderer getRenderer() { return renderer; }
    public static OrbitCamera getCamera() { return camera; }
    public static TempServerManager getServerManager() { return serverManager; }
    public static float getProgress() { return chunkManager.getProgress(); }

    /**
     * Start terrain preview generation. Called from PreviewScreen on render thread.
     */
    public static void startGeneration(String presetId, long seed, int radius, ChunkStatus status) {
        if (phase != Phase.IDLE && phase != Phase.DONE) return;

        renderer.releaseBuffers();
        previewWorld.clear();
        radiusChunks = radius;
        chunkStatus = status;
        phase = Phase.WAITING_FOR_SERVER;
        statusText = "Starting server...";
        waitStartTime = System.currentTimeMillis();

        // Stop existing server if running (different seed/preset)
        if (serverManager.isRunning()) {
            serverManager.stopServer();
        }

        serverManager.startServer(presetId, seed, s -> statusText = s);
    }

    /**
     * Load a BO3/BO4 custom object into the preview. No server needed.
     * Called from PreviewScreen on render thread.
     */
    public static void loadBO(String objectName, String presetName) {
        if (phase != Phase.IDLE && phase != Phase.DONE) return;

        renderer.releaseBuffers();
        previewWorld.clear();
        phase = Phase.COMPILING;
        statusText = "Loading " + objectName + "...";

        BOPreviewHelper.BOBounds bounds = BOPreviewHelper.loadObject(objectName, presetName, previewWorld);
        if (bounds == null) {
            statusText = "Failed to load " + objectName;
            phase = Phase.IDLE;
            return;
        }

        statusText = "Compiling meshes...";
        renderer.compileAll();
        camera.fitTo(bounds.center(), bounds.radius());

        phase = Phase.DONE;
        statusText = "BO: " + objectName;
    }

    // Server start timeout — 60 seconds
    private static long waitStartTime;
    private static final long SERVER_TIMEOUT_MS = 60_000;

    /**
     * Called every client tick from ClientTickMixin.
     */
    public static void tick() {
        if (phase == Phase.WAITING_FOR_SERVER) {
            if (serverManager.isRunning()) {
                phase = Phase.GENERATING_CHUNKS;
                statusText = "Generating chunks...";
                generateChunksAsync();
            } else if (System.currentTimeMillis() - waitStartTime > SERVER_TIMEOUT_MS) {
                LOG.error("Server start timed out after {}ms", SERVER_TIMEOUT_MS);
                statusText = "Error: server start timed out";
                phase = Phase.IDLE;
            }
        }
    }

    private static void generateChunksAsync() {
        ServerLevel overworld = serverManager.getOverworld();
        if (overworld == null) {
            LOG.error("Server ready but no overworld found");
            statusText = "Error: no overworld";
            phase = Phase.IDLE;
            return;
        }

        // Generate chunks on the server thread
        overworld.getServer().execute(() -> {
            try {
                chunkManager.generate(
                    overworld, previewWorld, radiusChunks, chunkStatus,
                    (done, total) -> statusText = "Generating: " + done + "/" + total,
                    () -> {}
                );

                // Switch back to render thread for compilation — keep server alive
                Minecraft.getInstance().execute(() -> {
                    phase = Phase.COMPILING;
                    statusText = "Compiling meshes...";

                    renderer.compileAll();
                    camera.fitTo(
                        new org.joml.Vector3f(0, 100, 0),
                        radiusChunks * 16f
                    );

                    // Server stays alive — reused for BO preview
                    phase = Phase.DONE;
                    statusText = "Ready — " + chunkManager.getCompletedChunks() + " chunks";

                    // Show PreviewScreen in view mode
                    Minecraft.getInstance().setScreen(new PreviewScreen());
                });
            } catch (OutOfMemoryError e) {
                LOG.error("Out of memory during chunk generation", e);
                Minecraft.getInstance().execute(() -> {
                    statusText = "Error: out of memory — try smaller size";
                    previewWorld.clear();
                    renderer.releaseBuffers();
                    phase = Phase.IDLE;
                    Minecraft.getInstance().setScreen(new PreviewScreen());
                });
            } catch (Exception e) {
                LOG.error("Chunk generation failed", e);
                Minecraft.getInstance().execute(() -> {
                    statusText = "Error: " + e.getMessage();
                    phase = Phase.IDLE;
                    Minecraft.getInstance().setScreen(new PreviewScreen());
                });
            }
        });
    }

    public static void reset() {
        chunkManager.cancel();
        if (serverManager.isRunning()) {
            serverManager.stopServer();
        }
        renderer.releaseBuffers();
        previewWorld.clear();
        phase = Phase.IDLE;
        statusText = "Ready";
    }
}
