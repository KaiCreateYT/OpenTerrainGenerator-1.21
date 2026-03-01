package com.pg85.otg.shared.portals;

import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.materials.IBlockStateMaterial;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public final class SharedPortalConfigResolver {

    private SharedPortalConfigResolver() {}

    public static Optional<DimensionPreset> findPresetByColor(String portalColor) {
        return PortalConfigLookup.findPresetByColor(portalColor);
    }

    public static Optional<PortalSettings> findSettingsByColor(String portalColor) {
        return PortalConfigLookup.findSettingsByColor(portalColor);
    }

    public static boolean isFrameBlock(Block block, List<LocalMaterialData> frameBlocks) {
        if (frameBlocks == null || frameBlocks.isEmpty()) {
            return false;
        }
        for (LocalMaterialData material : frameBlocks) {
            if (((IBlockStateMaterial) material).getState().getBlock() == block) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFrameBlock(BlockState state, List<LocalMaterialData> frameBlocks) {
        return isFrameBlock(state.getBlock(), frameBlocks);
    }

    public static BlockState getFrameBlock(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            List<LocalMaterialData> portalBlocks = gen.getPortalBlocks();
            if (portalBlocks != null && !portalBlocks.isEmpty()) {
                return ((IBlockStateMaterial) portalBlocks.get(0)).getState();
            }
        }

        return findSettingsByColor(portalColor)
                .filter(s -> s.getPortalBlocks() != null && !s.getPortalBlocks().isEmpty())
                .map(s -> ((IBlockStateMaterial) s.getPortalBlocks().get(0)).getState())
                .orElse(Blocks.QUARTZ_BLOCK.defaultBlockState());
    }

    public static int getPortalMinWidth(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            return Math.max(2, gen.getPortalMinWidth());
        }
        return findSettingsByColor(portalColor)
                .map(PortalConfigLookup::getPortalMinWidth)
                .orElse(2);
    }

    public static int getPortalMinHeight(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            return Math.max(3, gen.getPortalMinHeight());
        }
        return findSettingsByColor(portalColor)
                .map(PortalConfigLookup::getPortalMinHeight)
                .orElse(3);
    }

    public static String normalizeColor(String color) {
        return DimensionNameUtils.normalizeColor(color);
    }
}
