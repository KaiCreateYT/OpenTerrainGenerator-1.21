package com.pg85.otg.client.editor.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pg85.otg.client.editor.data.BiomeHeightmapGenerator;
import com.pg85.otg.client.editor.data.PropertyValue;
import com.pg85.otg.client.preview.OrbitCamera;
import com.pg85.otg.client.preview.PreviewRenderer;
import com.pg85.otg.client.preview.world.PreviewWorld;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BiomeTerrainPreviewScreen extends Screen {

    private final Screen parent;
    private final List<PropertyValue> biomeProperties;
    private final List<PropertyValue> presetProperties;
    private final String biomeName;

    private PreviewWorld world;
    private PreviewRenderer renderer;
    private final OrbitCamera camera = new OrbitCamera();
    private String statusText;
    private boolean generating = false;

    // User-configurable params
    private long seed = 12345L;
    private int size = 64;
    private EditBox seedInput;

    public BiomeTerrainPreviewScreen(Screen parent, List<PropertyValue> biomeProperties,
                                      List<PropertyValue> presetProperties, String biomeName) {
        super(Component.literal("Terrain Preview — " + biomeName));
        this.parent = parent;
        this.biomeProperties = biomeProperties;
        this.presetProperties = presetProperties;
        this.biomeName = biomeName;
    }

    @Override
    protected void init() {
        int barY = height - 25;

        // Seed input
        seedInput = new EditBox(font, 4, barY, 80, 18, Component.literal("Seed"));
        seedInput.setValue(String.valueOf(seed));
        seedInput.setHint(Component.literal("Seed"));
        addRenderableWidget(seedInput);

        // Size buttons
        int sx = 90;
        for (int s : new int[]{64, 128, 256}) {
            final int sz = s;
            var btn = addRenderableWidget(Button.builder(Component.literal(String.valueOf(s)), b -> {
                size = sz;
                regenerate();
            }).bounds(sx, barY, 32, 20).build());
            if (s == size) btn.active = false;
            sx += 36;
        }

        // Generate (re-seed)
        addRenderableWidget(Button.builder(Component.literal("Generate"), btn -> regenerate())
            .bounds(sx + 4, barY, 60, 20).build());

        // Back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(width - 54, barY, 50, 20).build());

        // Auto-generate on first open
        if (world == null) {
            regenerate();
        }
    }

    private void regenerate() {
        // Parse seed
        try {
            String seedText = seedInput.getValue().trim();
            if (seedText.isEmpty()) {
                seed = System.currentTimeMillis();
            } else {
                seed = Long.parseLong(seedText);
            }
        } catch (NumberFormatException e) {
            // Use string hash as seed
            seed = seedInput.getValue().hashCode();
        }

        // Size already set by button click

        if (generating) return;

        // Clean up old
        if (renderer != null) { renderer.releaseBuffers(); renderer = null; }
        if (world != null) { world.clear(); }

        generating = true;
        statusText = "Generating " + size + "x" + size + "...";

        // Heavy noise computation on background thread
        final long genSeed = seed;
        final int genSize = size;
        PreviewWorld genWorld = new PreviewWorld();
        world = genWorld;

        CompletableFuture.supplyAsync(() ->
            BiomeHeightmapGenerator.generate(genWorld, biomeProperties, presetProperties, genSeed, genSize)
        ).thenAcceptAsync(result -> {
            // Mesh compilation must happen on render thread
            renderer = new PreviewRenderer(genWorld);
            renderer.compileAll();
            camera.fitTo(result.center(), result.radius());
            generating = false;
            statusText = null;
        }, minecraft);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int vpH = height - 30;

        if (renderer != null && !renderer.isEmpty()) {
            graphics.enableScissor(0, 0, width, vpH);

            var window = minecraft.getWindow();
            double scale = window.getGuiScale();
            int fbY = (int) ((window.getGuiScaledHeight() - vpH) * scale);
            int fbW = (int) (width * scale);
            int fbH = (int) (vpH * scale);
            RenderSystem.viewport(0, fbY, fbW, fbH);

            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            float aspect = (float) width / vpH;
            Matrix4f viewMatrix = camera.getViewMatrix();
            Matrix4f projMatrix = camera.getProjectionMatrix(aspect);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            renderer.draw(RenderType.solid(), viewMatrix, projMatrix);
            renderer.draw(RenderType.cutoutMipped(), viewMatrix, projMatrix);
            renderer.draw(RenderType.cutout(), viewMatrix, projMatrix);

            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            renderer.draw(RenderType.translucent(), viewMatrix, projMatrix);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();

            RenderSystem.disableDepthTest();
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());

            graphics.disableScissor();
        } else {
            graphics.fill(0, 0, width, vpH, 0xFF111111);
            if (statusText != null) {
                graphics.drawCenteredString(font, statusText, width / 2, vpH / 2, 0xFFAAAA44);
            }
        }

        // Title
        graphics.drawCenteredString(font, title, width / 2, 4, 0xFFFFFF);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (mouseY < height - 30) {
            camera.rotate((float) (-dragX * 0.01), (float) (dragY * 0.01));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        if (mouseY < height - 30) {
            camera.zoom((float) (deltaV * 120));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    @Override
    public void onClose() {
        if (renderer != null) {
            renderer.releaseBuffers();
            renderer = null;
        }
        if (world != null) {
            world.clear();
            world = null;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
