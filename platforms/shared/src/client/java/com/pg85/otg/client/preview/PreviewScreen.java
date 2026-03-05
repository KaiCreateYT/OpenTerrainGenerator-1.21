package com.pg85.otg.client.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pg85.otg.OTG;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class PreviewScreen extends Screen {

    private static final int PANEL_WIDTH = 150;

    // Persisted across screen recreations via static
    private static long seed = 12345L;
    private static int radiusChunks = 4;
    private static ChunkStatus chunkStatus = ChunkStatus.FULL;
    private static String presetName = "";
    private static String boObjectName = "";

    private EditBox seedInput;
    private EditBox boInput;
    private int viewportX, viewportY, viewportW, viewportH;

    // Available preset names (populated in init)
    private List<String> presetNames = new ArrayList<>();
    private int presetIndex = 0;

    public PreviewScreen() {
        super(Component.literal("OTG Editor — Preview"));
    }

    @Override
    protected void init() {
        // Load available preset names
        presetNames = new ArrayList<>();
        try {
            var engine = OTG.getEngine();
            if (engine != null) {
                presetNames.addAll(engine.getDimensionPresetLoader().getAllDimensionPresetFolderNames());
            }
        } catch (Exception ignored) {}

        if (presetNames.isEmpty()) {
            presetNames.add("DefaultPreset");
        }

        // Restore preset selection
        if (presetName.isEmpty() || !presetNames.contains(presetName)) {
            presetName = presetNames.getFirst();
        }
        presetIndex = presetNames.indexOf(presetName);

        int panelX = 10;
        int y = 35;

        // --- TERRAIN SECTION ---

        // Preset selector button
        addRenderableWidget(Button.builder(
            Component.literal("Preset: " + presetName),
            btn -> {
                presetIndex = (presetIndex + 1) % presetNames.size();
                presetName = presetNames.get(presetIndex);
                btn.setMessage(Component.literal("Preset: " + presetName));
            }
        ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
        y += 24;

        // Seed input
        seedInput = new EditBox(font, panelX, y, PANEL_WIDTH - 20, 20, Component.literal("Seed"));
        seedInput.setValue(String.valueOf(seed));
        seedInput.setResponder(s -> {
            try { seed = Long.parseLong(s); } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(seedInput);
        y += 24;

        // Size buttons
        int btnX = panelX;
        for (int size : new int[]{4, 8, 16, 32}) {
            final int r = size / 2;
            addRenderableWidget(Button.builder(
                Component.literal(size + "x" + size),
                btn -> radiusChunks = r
            ).bounds(btnX, y, 30, 20).build());
            btnX += 33;
        }
        y += 24;

        // Generation level buttons
        record GenLevel(String label, ChunkStatus status) {}
        for (var level : new GenLevel[]{
                new GenLevel("Surface", ChunkStatus.SURFACE),
                new GenLevel("Caves", ChunkStatus.CARVERS),
                new GenLevel("Full", ChunkStatus.FULL)
        }) {
            addRenderableWidget(Button.builder(
                Component.literal(level.label),
                btn -> chunkStatus = level.status
            ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
            y += 22;
        }
        y += 4;

        // Generate button
        addRenderableWidget(Button.builder(
            Component.literal("Generate Preview"),
            btn -> {
                if (PreviewState.getPhase() == PreviewState.Phase.IDLE
                        || PreviewState.getPhase() == PreviewState.Phase.DONE) {
                    PreviewState.startGeneration(null, seed, radiusChunks, chunkStatus);
                }
            }
        ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
        y += 28;

        // --- BO SECTION ---

        // BO object name input
        boInput = new EditBox(font, panelX, y, PANEL_WIDTH - 20, 20, Component.literal("BO Name"));
        boInput.setValue(boObjectName);
        boInput.setResponder(s -> boObjectName = s);
        boInput.setHint(Component.literal("BO3/BO4 name"));
        addRenderableWidget(boInput);
        y += 24;

        // Load BO button
        addRenderableWidget(Button.builder(
            Component.literal("Load BO"),
            btn -> {
                if (!boObjectName.isEmpty() &&
                    (PreviewState.getPhase() == PreviewState.Phase.IDLE
                        || PreviewState.getPhase() == PreviewState.Phase.DONE)) {
                    PreviewState.loadBO(boObjectName, presetName);
                    rebuildWidgets();
                }
            }
        ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
        y += 28;

        // --- CONTROLS ---

        // Clear button (only when we have data)
        if (PreviewState.getPhase() == PreviewState.Phase.DONE) {
            addRenderableWidget(Button.builder(
                Component.literal("Clear"),
                btn -> {
                    PreviewState.reset();
                    rebuildWidgets();
                }
            ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
            y += 28;
        }

        // Back button
        addRenderableWidget(Button.builder(
            Component.literal("Back"),
            btn -> onClose()
        ).bounds(panelX, height - 30, PANEL_WIDTH - 20, 20).build());

        // Viewport bounds
        viewportX = PANEL_WIDTH;
        viewportY = 0;
        viewportW = width - PANEL_WIDTH;
        viewportH = height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);

        // Status text
        graphics.drawString(font, PreviewState.getStatusText(), 10, height - 50, 0xAAAAAA);

        // Selected params
        graphics.drawString(font, radiusChunks * 2 + "x" + radiusChunks * 2 + " chunks", 10, 22, 0x888888);

        // 3D viewport
        if (PreviewState.getPhase() == PreviewState.Phase.DONE && !PreviewState.getRenderer().isEmpty()) {
            renderViewport(graphics, partialTick);
        } else {
            // Empty viewport placeholder
            graphics.fill(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH, 0xFF1A1A1A);
            if (PreviewState.getPhase() == PreviewState.Phase.IDLE) {
                graphics.drawCenteredString(font, "Click 'Generate Preview' or 'Load BO' to start",
                    viewportX + viewportW / 2, viewportY + viewportH / 2, 0x666666);
            } else if (PreviewState.getPhase() != PreviewState.Phase.DONE) {
                // Show progress during generation
                String progress = PreviewState.getStatusText();
                int centerX = viewportX + viewportW / 2;
                int centerY = viewportY + viewportH / 2;
                graphics.drawCenteredString(font, progress, centerX, centerY - 10, 0x888888);

                // Progress bar
                if (PreviewState.getPhase() == PreviewState.Phase.GENERATING_CHUNKS) {
                    int barW = Math.min(viewportW - 40, 200);
                    int barH = 6;
                    int barX = centerX - barW / 2;
                    int barY = centerY + 6;
                    float pct = PreviewState.getProgress();
                    graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
                    graphics.fill(barX, barY, barX + (int)(barW * pct), barY + barH, 0xFF44AA44);
                }
            }
        }

        // UI widgets on top
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderViewport(GuiGraphics graphics, float partialTick) {
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);

        // Save GUI viewport and set 3D viewport to match our panel
        var window = minecraft.getWindow();
        double scale = window.getGuiScale();
        int fbX = (int) (viewportX * scale);
        int fbY = (int) ((window.getGuiScaledHeight() - viewportY - viewportH) * scale);
        int fbW = (int) (viewportW * scale);
        int fbH = (int) (viewportH * scale);
        RenderSystem.viewport(fbX, fbY, fbW, fbH);

        // Clear depth buffer so 3D content doesn't z-fight with GUI
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        OrbitCamera camera = PreviewState.getCamera();
        float aspect = (float) viewportW / viewportH;
        Matrix4f viewMatrix = camera.getViewMatrix();
        Matrix4f projMatrix = camera.getProjectionMatrix(aspect);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        PreviewRenderer renderer = PreviewState.getRenderer();

        // Opaque passes
        renderer.draw(RenderType.solid(), viewMatrix, projMatrix);
        renderer.draw(RenderType.cutoutMipped(), viewMatrix, projMatrix);
        renderer.draw(RenderType.cutout(), viewMatrix, projMatrix);

        // Translucent pass
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        renderer.draw(RenderType.translucent(), viewMatrix, projMatrix);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        // Restore depth test and viewport for GUI rendering
        RenderSystem.disableDepthTest();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());

        graphics.disableScissor();
    }

    // --- Input handling ---

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                 double deltaX, double deltaY) {
        if (isInViewport(mouseX, mouseY)) {
            PreviewState.getCamera().rotate((float) (-deltaX * 0.01), (float) (deltaY * 0.01));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  double deltaH, double deltaV) {
        if (isInViewport(mouseX, mouseY)) {
            PreviewState.getCamera().zoom((float) (deltaV * 120));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    private boolean isInViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX < viewportX + viewportW
            && mouseY >= viewportY && mouseY < viewportY + viewportH;
    }

    @Override
    public void onClose() {
        PreviewState.reset();
        minecraft.setScreen(null);
    }
}
