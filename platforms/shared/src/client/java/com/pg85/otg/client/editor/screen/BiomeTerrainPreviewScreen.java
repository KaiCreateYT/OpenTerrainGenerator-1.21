package com.pg85.otg.client.editor.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pg85.otg.client.editor.data.BiomeHeightmapGenerator;
import com.pg85.otg.client.editor.data.PropertyValue;
import com.pg85.otg.client.preview.OrbitCamera;
import com.pg85.otg.client.preview.PreviewRenderer;
import com.pg85.otg.client.preview.world.PreviewWorld;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private boolean generated = false;
    private String statusText = "Generating...";

    public BiomeTerrainPreviewScreen(Screen parent, List<PropertyValue> properties, String biomeName) {
        super(Component.literal("Terrain Preview — " + biomeName));
        this.parent = parent;
        this.properties = properties;
        this.biomeName = biomeName;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(width / 2 - 40, height - 25, 80, 20).build());

        if (!generated) {
            world = new PreviewWorld();
            BiomeHeightmapGenerator.GenerationResult result =
                BiomeHeightmapGenerator.generate(world, properties);
            renderer = new PreviewRenderer(world);
            renderer.compileAll();
            camera.fitTo(result.center(), result.radius());
            generated = true;
            statusText = null;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 3D viewport (full screen minus bottom bar)
        int vpH = height - 35;

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

        // Bottom bar with title + buttons
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height - 34, 0xFFFFFF);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        camera.rotate((float) (-dragX * 0.01), (float) (dragY * 0.01));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        camera.zoom((float) (deltaV * 120));
        return true;
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
