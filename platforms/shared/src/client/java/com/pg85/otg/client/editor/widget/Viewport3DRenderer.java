package com.pg85.otg.client.editor.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pg85.otg.client.preview.OrbitCamera;
import com.pg85.otg.client.preview.PreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Shared 3D viewport rendering utility.
 * Handles scissor, viewport calc, depth buffer clear, 4 RenderType draw calls,
 * blend setup, and state restore. Used by PreviewScreen, BOBrowserScreen,
 * and BiomeTerrainPreviewScreen.
 */
public final class Viewport3DRenderer {

    private Viewport3DRenderer() {}

    /**
     * Renders a 3D viewport within the given GUI bounds.
     *
     * @param graphics   GuiGraphics for scissor control
     * @param vpX        viewport X in GUI coords
     * @param vpY        viewport Y in GUI coords
     * @param vpW        viewport width in GUI coords
     * @param vpH        viewport height in GUI coords
     * @param camera     OrbitCamera for view/projection matrices
     * @param renderer   PreviewRenderer with compiled mesh data
     */
    public static void render(GuiGraphics graphics, int vpX, int vpY, int vpW, int vpH,
                               OrbitCamera camera, PreviewRenderer renderer) {
        graphics.enableScissor(vpX, vpY, vpX + vpW, vpY + vpH);

        // Compute framebuffer coordinates from GUI coordinates
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int fbX = (int) (vpX * scale);
        int fbY = (int) ((window.getGuiScaledHeight() - vpY - vpH) * scale);
        int fbW = (int) (vpW * scale);
        int fbH = (int) (vpH * scale);
        RenderSystem.viewport(fbX, fbY, fbW, fbH);

        // Clear depth buffer so 3D content doesn't z-fight with GUI
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        float aspect = (float) vpW / vpH;
        Matrix4f viewMatrix = camera.getViewMatrix();
        Matrix4f projMatrix = camera.getProjectionMatrix(aspect);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

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
}
