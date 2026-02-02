package com.pg85.otg.fabric.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.PortalColors;
import com.pg85.otg.config.settings.preset.PortalSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.fabric.dimensions.FabricDimensionHelper;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.materials.FabricMaterialData;
import com.pg85.otg.fabric.portals.components.OTGComponents;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OTGPortalBlock extends NetherPortalBlock {

    private final String portalColor;

    public OTGPortalBlock(Properties settings, String portalColor) {
        super(settings);
        this.portalColor = portalColor;
    }

    public String getPortalColor() {
        return portalColor;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!entity.isPassenger() && !entity.isVehicle() && entity.canChangeDimensions()) {
            // Trigger visual effect on client side
            if (level.isClientSide) {
                entity.handleInsidePortal(pos);
                return;
            }

            if (entity.isOnPortalCooldown()) {
                entity.setPortalCooldown();
            } else {
                if (entity instanceof Player player) {
                    var component = OTGComponents.get(player);
                    component.setPortalState(true, this.portalColor);

                    int portalTime = component.getPortalTime();
                    int waitTime = player.getPortalWaitTime();

                    if (portalTime >= waitTime) {
                        doTeleport(player, (ServerLevel) level);
                        component.setPortalTime(0);
                    }
                } else {
                    doTeleport(entity, (ServerLevel) level);
                }
            }
        }
    }

    private void doTeleport(Entity entity, ServerLevel serverLevel) {
        if (serverLevel == null) return;

        MinecraftServer server = serverLevel.getServer();
        ServerLevel destination = findDestination(entity, serverLevel);

        System.out.println("[OTG Portal] doTeleport: destination=" + (destination != null ? destination.dimension().location() : "null"));

        if (destination != null && !entity.isPassenger()) {
            entity.setPortalCooldown();
            OTGTeleporter.teleport(entity, destination, this.portalColor);
        } else {
            System.out.println("[OTG Portal] Cannot teleport: destination is null or entity is passenger");
        }
    }

    private ServerLevel findDestination(Entity entity, ServerLevel currentLevel) {
        MinecraftServer server = currentLevel.getServer();

        // If in overworld, find OTG dimension with matching color
        if (currentLevel.dimension() == Level.OVERWORLD) {
            return findOTGDimensionByColor(server, this.portalColor);
        }

        // If in OTG dimension, check if color matches and go to overworld
        if (currentLevel.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator) {
            String dimColor = getWorldPortalColor(currentLevel);
            if (this.portalColor.equals(dimColor)) {
                return server.overworld();
            }
        }

        return null;
    }

    private ServerLevel findOTGDimensionByColor(MinecraftServer server, String targetColor) {
        System.out.println("[OTG Portal] Looking for dimension with color: " + targetColor);

        // First check already loaded dimensions - direct color match, no collision handling
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD ||
                level.dimension() == Level.NETHER ||
                level.dimension() == Level.END) {
                continue;
            }

            if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator) {
                String dimColor = getWorldPortalColor(level);
                System.out.println("[OTG Portal] Checking loaded dimension: " + level.dimension().location() + " color=" + dimColor);
                if (targetColor.equals(dimColor)) {
                    System.out.println("[OTG Portal] Found loaded dimension: " + level.dimension().location());
                    return level;
                }
            }
        }

        // Not found in loaded dimensions - look through presets and load dynamically
        System.out.println("[OTG Portal] Dimension not loaded, checking presets...");
        return findAndLoadDimensionByColor(server, targetColor);
    }

    private ServerLevel findAndLoadDimensionByColor(MinecraftServer server, String targetColor) {
        List<Preset> presets = new ArrayList<>(OTG.getEngine().getPresetLoader().getAllPresets());

        // First, find preset with EXACTLY matching configured color
        for (Preset preset : presets) {
            if (preset.getPresetConfig() == null) continue;

            PortalSettings portalSettings = preset.getPresetConfig().getPortalSettings();
            if (portalSettings == null || portalSettings.getPortalBlocks() == null ||
                portalSettings.getPortalBlocks().isEmpty()) {
                continue;
            }

            String configuredColor = portalSettings.getPortalColor().toLowerCase().trim();

            if (targetColor.equals(configuredColor)) {
                System.out.println("[OTG Portal] Found preset with matching color: " + preset.getFolderName() + " color=" + configuredColor);
                return loadOrCreateDimension(server, preset);
            }
        }

        System.out.println("[OTG Portal] No preset found with color: " + targetColor);
        return null;
    }

    private ServerLevel loadOrCreateDimension(MinecraftServer server, Preset preset) {
        String dimName = preset.getFolderName().toLowerCase().replace(" ", "_");
        ResourceKey<Level> levelKey = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(Constants.MOD_ID_SHORT, dimName)
        );

        // Check if dimension already exists
        ServerLevel existing = server.getLevel(levelKey);
        if (existing != null) {
            System.out.println("[OTG Portal] Dimension already loaded: " + levelKey.location());
            return existing;
        }

        // Create dimension at runtime
        try {
            System.out.println("[OTG Portal] Creating dimension at runtime: " + dimName);
            FabricDimensionHelper helper = new FabricDimensionHelper();
            helper.createDimensionRuntime(server, dimName, preset.getFolderName(), server.overworld().getSeed());

            ServerLevel newLevel = server.getLevel(levelKey);
            if (newLevel != null) {
                System.out.println("[OTG Portal] Successfully created dimension: " + levelKey.location());
                return newLevel;
            }
        } catch (Exception e) {
            System.err.println("[OTG Portal] Failed to create dimension: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private String getWorldPortalColor(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator gen) {
            return gen.getPortalColor().toLowerCase().trim();
        }
        return "default";
    }

    // Portal frame validation
    public static boolean tryCreatePortal(LevelAccessor level, BlockPos pos, List<LocalMaterialData> frameBlocks, String portalColor) {
        return tryCreatePortal(level, pos, frameBlocks, portalColor, 2, 21, 3, 21);
    }

    public static boolean tryCreatePortal(LevelAccessor level, BlockPos pos, List<LocalMaterialData> frameBlocks, String portalColor,
                                          int minWidth, int maxWidth, int minHeight, int maxHeight) {
        System.out.println("[OTG Portal] Checking X axis at " + pos);
        PortalSize sizeX = new PortalSize(level, pos, Direction.Axis.X, frameBlocks, minWidth, maxWidth, minHeight, maxHeight);
        System.out.println("[OTG Portal] X axis: width=" + sizeX.width + " height=" + sizeX.height +
                " bottomLeft=" + sizeX.bottomLeft + " valid=" + sizeX.isValid() + " portalBlocks=" + sizeX.portalBlockCount);
        if (sizeX.isValid() && sizeX.portalBlockCount == 0) {
            sizeX.placePortalBlocks(portalColor);
            return true;
        }

        System.out.println("[OTG Portal] Checking Z axis at " + pos);
        PortalSize sizeZ = new PortalSize(level, pos, Direction.Axis.Z, frameBlocks, minWidth, maxWidth, minHeight, maxHeight);
        System.out.println("[OTG Portal] Z axis: width=" + sizeZ.width + " height=" + sizeZ.height +
                " bottomLeft=" + sizeZ.bottomLeft + " valid=" + sizeZ.isValid() + " portalBlocks=" + sizeZ.portalBlockCount);
        if (sizeZ.isValid() && sizeZ.portalBlockCount == 0) {
            sizeZ.placePortalBlocks(portalColor);
            return true;
        }

        return false;
    }

    public static class PortalSize {
        private final LevelAccessor level;
        private final Direction.Axis axis;
        private final Direction rightDir;
        private final Direction leftDir;
        private final List<LocalMaterialData> frameBlocks;
        private final int minWidth;
        private final int maxWidth;
        private final int minHeight;
        private final int maxHeight;
        public int portalBlockCount = 0;
        public BlockPos bottomLeft;
        public int height;
        public int width;

        public PortalSize(LevelAccessor level, BlockPos pos, Direction.Axis axis, List<LocalMaterialData> frameBlocks,
                          int minWidth, int maxWidth, int minHeight, int maxHeight) {
            this.level = level;
            this.axis = axis;
            this.frameBlocks = frameBlocks;
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;

            if (axis == Direction.Axis.X) {
                this.leftDir = Direction.EAST;
                this.rightDir = Direction.WEST;
            } else {
                this.leftDir = Direction.NORTH;
                this.rightDir = Direction.SOUTH;
            }

            // Find bottom
            BlockPos bottomPos = pos;
            while (bottomPos.getY() > level.getMinBuildHeight() && isEmpty(level.getBlockState(bottomPos.below()))) {
                bottomPos = bottomPos.below();
            }
            System.out.println("[OTG Portal] Bottom pos: " + bottomPos + " block below: " + level.getBlockState(bottomPos.below()));

            int distLeft = getDistanceToEdge(bottomPos, leftDir);
            System.out.println("[OTG Portal] Distance to left edge (" + leftDir + "): " + distLeft);
            if (distLeft > 0) {
                // bottomLeft should be the leftmost INTERIOR position (not on the wall)
                // distLeft is how far to the wall, so interior is at distLeft-1
                this.bottomLeft = bottomPos.relative(leftDir, distLeft - 1);
                // Width = distance from bottomLeft to right wall
                this.width = getDistanceToEdge(bottomLeft, rightDir);
                System.out.println("[OTG Portal] bottomLeft: " + bottomLeft + " Width: " + width + " (min=" + minWidth + " max=" + maxWidth + ")");
                if (width < minWidth || width > maxWidth) {
                    System.out.println("[OTG Portal] Width out of range!");
                    this.bottomLeft = null;
                    this.width = 0;
                }
            } else {
                // Already at the left wall or no wall found
                this.bottomLeft = bottomPos;
                this.width = getDistanceToEdge(bottomLeft, rightDir);
                System.out.println("[OTG Portal] At left edge, Width: " + width);
                if (width < minWidth || width > maxWidth) {
                    this.bottomLeft = null;
                    this.width = 0;
                }
            }

            if (this.bottomLeft != null) {
                this.height = calculateHeight();
                System.out.println("[OTG Portal] Height: " + height + " (min=" + minHeight + " max=" + maxHeight + ")");
            }
        }

        private int getDistanceToEdge(BlockPos pos, Direction dir) {
            for (int i = 0; i <= maxWidth; i++) {
                BlockPos checkPos = pos.relative(dir, i);
                if (!isEmpty(level.getBlockState(checkPos)) || !isFrameBlock(level.getBlockState(checkPos.below()))) {
                    BlockPos framePos = pos.relative(dir, i);
                    return isFrameBlock(level.getBlockState(framePos)) ? i : 0;
                }
            }
            return 0;
        }

        private int calculateHeight() {
            outer:
            for (int h = 0; h <= maxHeight; h++) {
                for (int w = 0; w < this.width; w++) {
                    BlockPos checkPos = bottomLeft.relative(rightDir, w).above(h);
                    BlockState state = level.getBlockState(checkPos);

                    if (!isEmpty(state)) {
                        break outer;
                    }

                    if (state.getBlock() instanceof OTGPortalBlock) {
                        portalBlockCount++;
                    }

                    // Check side frames
                    if (w == 0 && !isFrameBlock(level.getBlockState(checkPos.relative(leftDir)))) {
                        break outer;
                    }
                    if (w == width - 1 && !isFrameBlock(level.getBlockState(checkPos.relative(rightDir)))) {
                        break outer;
                    }
                }
                this.height = h + 1;
            }

            // Verify top frame
            for (int w = 0; w < this.width; w++) {
                if (!isFrameBlock(level.getBlockState(bottomLeft.relative(rightDir, w).above(height)))) {
                    this.height = 0;
                    break;
                }
            }

            return (height >= minHeight && height <= maxHeight) ? height : 0;
        }

        private boolean isEmpty(BlockState state) {
            return state.isAir() || state.getBlock() == Blocks.WATER || state.getBlock() instanceof OTGPortalBlock;
        }

        private boolean isFrameBlock(BlockState state) {
            for (LocalMaterialData frameMaterial : frameBlocks) {
                if (((FabricMaterialData) frameMaterial).getState().getBlock() == state.getBlock()) {
                    return true;
                }
            }
            return false;
        }

        public boolean isValid() {
            return bottomLeft != null && width >= minWidth && width <= maxWidth && height >= minHeight && height <= maxHeight;
        }

        public void placePortalBlocks(String portalColor) {
            Block portalBlock = FabricPortalBlocks.getPortalBlock(portalColor);
            if (portalBlock == null) return;

            BlockState portalState = portalBlock.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);

            for (int w = 0; w < this.width; w++) {
                for (int h = 0; h < this.height; h++) {
                    BlockPos portalPos = bottomLeft.relative(rightDir, w).above(h);
                    if (level instanceof Level realLevel) {
                        realLevel.setBlock(portalPos, portalState, Block.UPDATE_ALL);
                    }
                }
            }
        }
    }
}
