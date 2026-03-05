package com.pg85.otg.client.preview;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;

import java.util.HashMap;
import java.util.Map;

public class PreviewSection {

    private final Map<RenderType, VertexBuffer> buffers = new HashMap<>();

    public void setBuffer(RenderType type, VertexBuffer buffer) {
        VertexBuffer old = buffers.put(type, buffer);
        if (old != null) old.close();
    }

    public VertexBuffer getBuffer(RenderType type) {
        return buffers.get(type);
    }

    public void close() {
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
    }
}
