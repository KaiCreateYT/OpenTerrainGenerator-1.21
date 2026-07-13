package com.pg85.otg.client.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.pg85.otg.client.preview.world.PreviewWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles block meshes from PreviewWorld and renders them.
 */
public class PreviewRenderer {

    private final Map<Long, PreviewSection> sections = new ConcurrentHashMap<>();
    private final PreviewWorld world;

    public PreviewRenderer(PreviewWorld world) {
        this.world = world;
    }

    public void compileSection(int sectionX, int sectionY, int sectionZ) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        PoseStack poseStack = new PoseStack();

        int baseX = SectionPos.sectionToBlockCoord(sectionX);
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = SectionPos.sectionToBlockCoord(sectionZ);

        Map<RenderType, BufferBuilder> builders = new HashMap<>();
        Map<RenderType, ByteBufferBuilder> byteBuffers = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        PreviewSection section = new PreviewSection();
        try {
            ModelBlockRenderer.enableCaching();

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        pos.set(baseX + x, baseY + y, baseZ + z);
                        BlockState state = world.getBlockState(pos);

                        FluidState fluidState = state.getFluidState();
                        if (!fluidState.isEmpty()) {
                            RenderType fluidRenderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
                            BufferBuilder fluidBuilder = getOrCreateBuilder(builders, byteBuffers, fluidRenderType);
                            dispatcher.renderLiquid(pos, world, fluidBuilder, state, fluidState);
                        }

                        if (state.getRenderShape() == RenderShape.MODEL) {
                            RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(state);
                            BufferBuilder builder = getOrCreateBuilder(builders, byteBuffers, renderType);
                            poseStack.pushPose();
                            poseStack.translate(x, y, z);
                            dispatcher.renderBatched(state, pos, world, poseStack, builder, true, List.of());
                            poseStack.popPose();
                        }
                    }
                }
            }

            for (var entry : builders.entrySet()) {
                MeshData meshData = entry.getValue().build();
                if (meshData != null) {
                    section.setBuffer(entry.getKey(), meshData);
                }
            }
        } finally {
            ModelBlockRenderer.clearCache();
            byteBuffers.values().forEach(ByteBufferBuilder::close);
        }

        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        PreviewSection old = sections.put(key, section);
        if (old != null) old.close();
    }

    private static BufferBuilder getOrCreateBuilder(
            Map<RenderType, BufferBuilder> builders,
            Map<RenderType, ByteBufferBuilder> byteBuffers,
            RenderType renderType) {
        return builders.computeIfAbsent(renderType, rt -> {
            ByteBufferBuilder bb = new ByteBufferBuilder(rt.bufferSize());
            byteBuffers.put(rt, bb);
            return new BufferBuilder(bb, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        });
    }

    public void compileAll() {
        int minSection = world.getMinY() >> 4;
        int maxSection = (world.getMinY() + world.getHeight()) >> 4;

        for (long chunkKey : world.getChunkKeys()) {
            int cx = PreviewWorld.chunkXFromKey(chunkKey);
            int cz = PreviewWorld.chunkZFromKey(chunkKey);
            for (int sy = minSection; sy < maxSection; sy++) {
                if (sectionHasBlocks(cx, sy, cz)) {
                    compileSection(cx, sy, cz);
                }
            }
        }
    }

    private boolean sectionHasBlocks(int sectionX, int sectionY, int sectionZ) {
        int baseX = SectionPos.sectionToBlockCoord(sectionX);
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x += 2) {
            for (int y = 0; y < 16; y += 2) {
                for (int z = 0; z < 16; z += 2) {
                    pos.set(baseX + x, baseY + y, baseZ + z);
                    if (!world.getBlockState(pos).isAir()) return true;
                }
            }
        }
        return false;
    }

    public void draw(RenderType renderType, Matrix4f viewMatrix, Matrix4f projMatrix) {
        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();

        for (var entry : sections.entrySet()) {
            long key = entry.getKey();
            PreviewSection section = entry.getValue();
            MeshData meshData = section.getBuffer(renderType);
            if (meshData == null) continue;

            int sx = SectionPos.x(key);
            int sy = SectionPos.y(key);
            int sz = SectionPos.z(key);

            float tx = (float) SectionPos.sectionToBlockCoord(sx);
            float ty = (float) SectionPos.sectionToBlockCoord(sy);
            float tz = (float) SectionPos.sectionToBlockCoord(sz);

            Matrix4f modelMatrix = new Matrix4f(viewMatrix);
            modelMatrix.translate(tx, ty, tz);

            // RenderSystem.drawMeshData(meshData); // removed in 1.21.5
        }

        renderType.clearRenderState();
    }

    public void releaseBuffers() {
        sections.values().forEach(PreviewSection::close);
        sections.clear();
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }
}
