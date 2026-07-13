package com.pg85.otg.client.preview;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;

import java.util.HashMap;
import java.util.Map;

public class PreviewSection {

    private final Map<RenderType, MeshData> buffers = new HashMap<>();

    public void setBuffer(RenderType type, MeshData buffer) {
        MeshData old = buffers.put(type, buffer);
        if (old != null) old.close();
    }

    public MeshData getBuffer(RenderType type) {
        return buffers.get(type);
    }

    public void close() {
        buffers.values().forEach(MeshData::close);
        buffers.clear();
    }
}
