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

public class BiomeTerrainPreviewScreen extends Screen {

    private final Screen parent;
    private final List<PropertyValue> properties;
    private final String biomeName;

    private PreviewWorld world;
    private PreviewRenderer renderer;
    private final OrbitCamera camera = new OrbitCamera();
    private String statusText;

    // User-configurable params
    private long seed = 12345L;
    private int size = 64;
    private EditBox seedInput;
    private EditBox sizeInput;

    public BiomeTerrainPreviewScreen(Screen parent, List<PropertyValue> properties, String biomeName) {
        super(Component.literal("Terrain Preview — " + biomeName));
        this.parent = parent;
        this.properties = properties;
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

        // Size input
        sizeInput = new EditBox(font, 90, barY, 40, 18, Component.literal("Size"));
        sizeInput.setValue(String.valueOf(size));
        sizeInput.setHint(Component.literal("Size"));
        addRenderableWidget(sizeInput);

        // Generate button
        addRenderableWidget(Button.builder(Component.literal("Generate"), btn -> regenerate())
            .bounds(136, barY, 60, 20).build());

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

        // Parse size
        try {
            size = Integer.parseInt(sizeInput.getValue().trim());
            size = Math.max(16, Math.min(256, size));
        } catch (NumberFormatException e) {
            size = 64;
        }
        sizeInput.setValue(String.valueOf(size));

        // Clean up old
        if (renderer != null) renderer.releaseBuffers();
        if (world != null) world.clear();

        statusText = "Generating...";
        world = new PreviewWorld();
        BiomeHeightmapGenerator.GenerationResult result =
            BiomeHeightmapGenerator.generate(world, properties, seed, size);
        renderer = new PreviewRenderer(world);
        renderer.compileAll();
        camera.fitTo(result.center(), result.radius());
        statusText = null;
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
