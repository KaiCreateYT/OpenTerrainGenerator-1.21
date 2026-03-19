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

    // Pending close — set by PreviewScreen.Back, handled in tick()
    private static volatile boolean pendingClose;
    // Tick counter: 0 = not closing, 1 = screen closed this tick, 2 = disconnect next tick
    private static int closeTickCounter;

    /**
     * Called every client tick from ClientTickMixin.
     *
     * After createFreshLevel(), the player is briefly in a live world (spectator mode).
     * We show progress via the action bar, then disconnect when generation completes.
     */
    public static void requestClose() {
        pendingClose = true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        // Two-tick close: tick 1 closes PreviewScreen (back to spectator),
        // tick 2 calls disconnect (from clean in-game state, like vanilla Save & Quit).
        // Single-tick setScreen(null)+disconnect() doesn't work — MC needs a full
        // tick/frame without PreviewScreen before disconnect works.
        if (pendingClose) {
            pendingClose = false;
            reset();
            mc.setScreen(null); // close PreviewScreen → spectator
            closeTickCounter = 1;
            LOG.info("Close requested — PreviewScreen removed, will disconnect next tick");
            return;
        }
        if (closeTickCounter > 0) {
            closeTickCounter++;
            if (closeTickCounter >= 3) {
                closeTickCounter = 0;
                LOG.info("Disconnecting (non-blocking — nulling server ref to skip while loop)...");
                serverManager.stopServer();
                LOG.info("Disconnect complete");
                return;
            }
        }

        if (phase == Phase.WAITING_FOR_SERVER) {
            // Wait for BOTH server ready AND player connected.
            // createFreshLevel() is async — server becomes ready before client connects.
            // If we generate+disconnect before the player logs in, disconnect is a no-op
            // and the player ends up in a live world.
            if (serverManager.isRunning() && mc.player != null) {
                LOG.info("Server ready and player connected — starting chunk generation");
                phase = Phase.GENERATING_CHUNKS;
                statusText = "Generating chunks...";
                showActionBar(mc, "OTG Preview: generating chunks...");
                generateChunksAsync();
            } else if (System.currentTimeMillis() - waitStartTime > SERVER_TIMEOUT_MS) {
                LOG.error("Server start timed out after {}ms", SERVER_TIMEOUT_MS);
                statusText = "Error: server start timed out";
                phase = Phase.IDLE;
            }
        }

        // Show progress in action bar while in the temp world
        if (phase == Phase.GENERATING_CHUNKS) {
            int done = chunkManager.getCompletedChunks();
            int total = chunkManager.getTotalChunks();
            if (total > 0) {
                showActionBar(mc, "OTG Preview: " + done + "/" + total + " chunks");
            }
        } else if (phase == Phase.COMPILING) {
            showActionBar(mc, "OTG Preview: compiling meshes...");
        }
    }

    private static void showActionBar(Minecraft mc, String message) {
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(message), false);
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

        LOG.info("Starting chunk generation: radius={}, status={}", radiusChunks, chunkStatus);

        // Generate chunks on the server thread
        overworld.getServer().execute(() -> {
            try {
                chunkManager.generate(
                    overworld, previewWorld, radiusChunks, chunkStatus,
                    (done, total) -> statusText = "Generating: " + done + "/" + total,
                    () -> {}
                );

                LOG.info("Chunk generation complete: {} chunks", chunkManager.getCompletedChunks());

                // Switch back to render thread for compilation
                Minecraft.getInstance().execute(() -> {
                    try {
                        phase = Phase.COMPILING;
                        statusText = "Compiling meshes...";
                        LOG.info("Compiling meshes...");

                        renderer.compileAll();
                        camera.fitTo(
                            new org.joml.Vector3f(0, 100, 0),
                            radiusChunks * 16f
                        );

                        phase = Phase.DONE;
                        statusText = "Ready — " + chunkManager.getCompletedChunks() + " chunks";
                        LOG.info("Preview ready: {} chunks compiled", chunkManager.getCompletedChunks());

                        // Show PreviewScreen over the live world — server stays alive.
                        // Server stops when user closes PreviewScreen (onClose → mc.disconnect()).
                        Minecraft.getInstance().setScreen(new PreviewScreen());
                        LOG.info("PreviewScreen set successfully");
                    } catch (Exception e) {
                        LOG.error("Error during compilation phase", e);
                        phase = Phase.IDLE;
                        statusText = "Error: " + e.getMessage();
                        Minecraft.getInstance().setScreen(new PreviewScreen());
                    }
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
        renderer.releaseBuffers();
        previewWorld.clear();
        phase = Phase.IDLE;
        statusText = "Ready";
        // Don't call serverManager.stopServer() here — mc.disconnect() blocks
        // the render thread indefinitely. Let onClose() handle it by calling
        // mc.disconnect() as the last action (result is title screen anyway).
    }
}
