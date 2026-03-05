package com.pg85.otg.client.preview;

import com.pg85.otg.OTG;
import com.pg85.otg.client.preview.world.PreviewWorld;
import com.pg85.otg.customobject.CustomObject;
import com.pg85.otg.customobject.bofunctions.BlockFunction;
import com.pg85.otg.customobject.structures.StructuredCustomObject;
import com.pg85.otg.shared.materials.IBlockStateMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a BO3/BO4 custom object into PreviewWorld for 3D preview.
 * Uses OTG.getEngine() to resolve the object by name from a preset.
 */
public class BOPreviewHelper {

    private static final Logger LOG = LoggerFactory.getLogger(BOPreviewHelper.class);

    public record BOBounds(Vector3f center, float radius) {}

    /**
     * Load a named custom object from the given preset into PreviewWorld.
     * Returns bounding info for camera fitting, or null on failure.
     */
    public static BOBounds loadObject(String objectName, String presetName, PreviewWorld world) {
        var engine = OTG.getEngine();
        if (engine == null) {
            LOG.error("OTG engine not available");
            return null;
        }

        CustomObject object = engine.getCustomObjectManager().getGlobalObjects().getObjectByName(
            objectName,
            presetName,
            engine.getOTGRootFolder(),
            engine.getCustomObjectManager(),
            engine.getDimensionPresetLoader().getMaterialReader(),
            engine.getCustomObjectResourcesManager(),
            engine.getModLoadedChecker()
        );

        if (object == null) {
            LOG.warn("Custom object '{}' not found in preset '{}'", objectName, presetName);
            return null;
        }

        if (!(object instanceof StructuredCustomObject structured)) {
            LOG.warn("Custom object '{}' is not a BO3/BO4 (type: {})", objectName, object.getClass().getSimpleName());
            return null;
        }

        BlockFunction<?>[] blocks = structured.getConfig().getBlockFunctions(
            presetName,
            engine.getOTGRootFolder(),
            engine.getCustomObjectManager(),
            engine.getDimensionPresetLoader().getMaterialReader(),
            engine.getCustomObjectResourcesManager(),
            engine.getModLoadedChecker()
        );

        if (blocks == null || blocks.length == 0) {
            LOG.warn("Custom object '{}' has no blocks", objectName);
            return null;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int placed = 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (BlockFunction<?> block : blocks) {
            if (block.material == null || block.material.isBlank()) continue;
            if (!(block.material instanceof IBlockStateMaterial bsMat)) continue;

            BlockState state = bsMat.getState();
            if (state == null || state.isAir()) continue;

            pos.set(block.x, block.y, block.z);
            world.setBlockState(pos, state);
            placed++;

            minX = Math.min(minX, block.x);
            minY = Math.min(minY, block.y);
            minZ = Math.min(minZ, block.z);
            maxX = Math.max(maxX, block.x);
            maxY = Math.max(maxY, block.y);
            maxZ = Math.max(maxZ, block.z);
        }

        if (placed == 0) {
            LOG.warn("Custom object '{}' had 0 placeable blocks", objectName);
            return null;
        }

        LOG.info("Loaded BO '{}': {} blocks, bounds [{},{},{} -> {},{},{}]",
            objectName, placed, minX, minY, minZ, maxX, maxY, maxZ);

        Vector3f center = new Vector3f(
            (minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f
        );
        float radius = Math.max(Math.max(maxX - minX, maxY - minY), maxZ - minZ) / 2f + 2f;

        return new BOBounds(center, radius);
    }
}
