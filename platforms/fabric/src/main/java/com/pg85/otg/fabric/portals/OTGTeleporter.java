package com.pg85.otg.fabric.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.materials.FabricMaterialData;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class OTGTeleporter {

    public static void teleport(Entity entity, ServerLevel destination, String portalColor) {
        BlockPos destPos = findOrCreatePortal(entity, destination, portalColor);
        if (destPos != null) {
            Vec3 teleportPos = findTeleportPosition(destination, destPos);

            if (entity instanceof ServerPlayer player) {
                player.teleportTo(destination, teleportPos.x, teleportPos.y, teleportPos.z,
                        player.getYRot(), player.getXRot());
            } else {
                entity.teleportTo(destination, teleportPos.x, teleportPos.y, teleportPos.z,
                        Set.of(), entity.getYRot(), entity.getXRot());
            }
        }
    }

    private static BlockPos findOrCreatePortal(Entity entity, ServerLevel destination, String portalColor) {
        WorldBorder border = destination.getWorldBorder();

        // Use coordinate scaling 1:1 for all dimensions
        double sourceScale = entity.level().dimensionType().coordinateScale();
        double destScale = destination.dimensionType().coordinateScale();
        double scale = sourceScale / destScale;

        BlockPos sourcePos = entity.blockPosition();
        int destX = Mth.clamp((int)(sourcePos.getX() * scale),
                (int)border.getMinX() + 16, (int)border.getMaxX() - 16);
        int destZ = Mth.clamp((int)(sourcePos.getZ() * scale),
                (int)border.getMinZ() + 16, (int)border.getMaxZ() - 16);
        BlockPos searchPos = new BlockPos(destX, sourcePos.getY(), destZ);

        System.out.println("[OTG Portal] Search position: " + searchPos + " (scale=" + scale + ")");

        // Try to find existing portal
        Optional<BlockPos> existingPortal = findExistingPortal(destination, searchPos, portalColor);
        if (existingPortal.isPresent()) {
            System.out.println("[OTG Portal] Found existing portal at: " + existingPortal.get());
            return existingPortal.get();
        }

        // Create new portal
        System.out.println("[OTG Portal] Creating new portal near: " + searchPos);
        return createPortal(destination, searchPos, portalColor);
    }

    private static Optional<BlockPos> findExistingPortal(ServerLevel level, BlockPos searchPos, String portalColor) {
        OTGPortalBlock targetBlock = FabricPortalBlocks.getPortalBlock(portalColor);
        if (targetBlock == null) return Optional.empty();

        // Search in expanding squares from search position
        int searchRadius = 128;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // Search in expanding rings for closer portals first
        for (int radius = 0; radius <= searchRadius; radius += 8) {
            // Search the perimeter at this radius
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    // Only check perimeter points (skip inner area already checked)
                    if (radius > 0 && Math.abs(dx) < radius && Math.abs(dz) < radius) {
                        continue;
                    }

                    int x = searchPos.getX() + dx;
                    int z = searchPos.getZ() + dz;

                    // Scan entire Y column at this X/Z
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        mutable.set(x, y, z);
                        if (level.getBlockState(mutable).getBlock() == targetBlock) {
                            // Found portal, find bottom
                            while (level.getBlockState(mutable.below()).getBlock() == targetBlock) {
                                mutable.move(Direction.DOWN);
                            }
                            System.out.println("[OTG Portal] Found portal at " + mutable);
                            return Optional.of(mutable.immutable());
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static BlockPos createPortal(ServerLevel level, BlockPos pos, String portalColor) {
        OTGPortalBlock portalBlock = FabricPortalBlocks.getPortalBlock(portalColor);
        if (portalBlock == null) return null;

        // Get frame block - try destination dimension config first, then fall back to preset by color
        BlockState frameBlock = getFrameBlock(level, portalColor);

        // Find suitable location
        BlockPos portalPos = findSuitableLocation(level, pos);

        Direction.Axis axis = Direction.Axis.X;
        Direction facing = Direction.get(Direction.AxisDirection.POSITIVE, axis);

        // Get portal size from config (use min values for auto-created portals)
        int width = getPortalWidth(level, portalColor);
        int height = getPortalHeight(level, portalColor);

        System.out.println("[OTG Portal] Creating portal at " + portalPos + " with frame=" + frameBlock.getBlock() + " size=" + width + "x" + height);

        // Bottom frame
        for (int i = -1; i <= width; i++) {
            level.setBlockAndUpdate(portalPos.relative(facing, i), frameBlock);
        }

        // Top frame
        for (int i = -1; i <= width; i++) {
            level.setBlockAndUpdate(portalPos.relative(facing, i).above(height), frameBlock);
        }

        // Side frames
        for (int h = 0; h <= height; h++) {
            level.setBlockAndUpdate(portalPos.relative(facing, -1).above(h), frameBlock);
            level.setBlockAndUpdate(portalPos.relative(facing, width).above(h), frameBlock);
        }

        // Portal blocks
        BlockState portalState = portalBlock.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
        for (int w = 0; w < width; w++) {
            for (int h = 1; h < height; h++) {
                level.setBlockAndUpdate(portalPos.relative(facing, w).above(h), portalState);
            }
        }

        return portalPos.above();
    }

    private static BlockPos findSuitableLocation(ServerLevel level, BlockPos searchPos) {
        // Try to find solid ground
        int y = Math.min(level.getMaxBuildHeight() - 10, searchPos.getY());
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(searchPos.getX(), y, searchPos.getZ());

        // Search down for solid ground
        while (mutable.getY() > level.getMinBuildHeight() + 5) {
            if (level.getBlockState(mutable).isSolidRender(level, mutable)) {
                return mutable.above().immutable();
            }
            mutable.move(Direction.DOWN);
        }

        // Fallback: create platform at y=70
        mutable.setY(70);
        for (int x = -1; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(mutable.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }

        return mutable.immutable();
    }

    private static BlockState getFrameBlock(ServerLevel level, String portalColor) {
        // First try destination dimension's config
        if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator gen) {
            List<LocalMaterialData> portalBlocks = gen.getPortalBlocks();
            if (!portalBlocks.isEmpty()) {
                return ((FabricMaterialData) portalBlocks.get(0)).getState();
            }
        }

        // Fall back to preset config by portal color
        PortalSettings settings = findPresetByColor(portalColor);
        if (settings != null && settings.getPortalBlocks() != null && !settings.getPortalBlocks().isEmpty()) {
            LocalMaterialData material = settings.getPortalBlocks().get(0);
            if (material instanceof FabricMaterialData fabricMaterial) {
                return fabricMaterial.getState();
            }
        }

        return Blocks.QUARTZ_BLOCK.defaultBlockState();
    }

    private static int getPortalWidth(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator gen) {
            return Math.max(2, gen.getPortalMinWidth());
        }

        PortalSettings settings = findPresetByColor(portalColor);
        if (settings != null) {
            return Math.max(2, settings.getPortalMinWidth());
        }
        return 2;
    }

    private static int getPortalHeight(ServerLevel level, String portalColor) {
        if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator gen) {
            return Math.max(3, gen.getPortalMinHeight());
        }

        PortalSettings settings = findPresetByColor(portalColor);
        if (settings != null) {
            return Math.max(3, settings.getPortalMinHeight());
        }
        return 3;
    }

    private static PortalSettings findPresetByColor(String portalColor) {
        String targetColor = portalColor.toLowerCase().trim();
        for (Preset preset : OTG.getEngine().getPresetLoader().getAllPresets()) {
            if (preset.getPresetConfig() == null) continue;
            PortalSettings settings = preset.getPresetConfig().getPortalSettings();
            if (settings == null) continue;

            String configColor = settings.getPortalColor().toLowerCase().trim();
            if (targetColor.equals(configColor)) {
                return settings;
            }
        }
        return null;
    }

    private static Vec3 findTeleportPosition(ServerLevel level, BlockPos portalPos) {
        return new Vec3(portalPos.getX() + 0.5, portalPos.getY(), portalPos.getZ() + 0.5);
    }
}
