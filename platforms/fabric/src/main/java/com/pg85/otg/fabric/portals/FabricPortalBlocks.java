package com.pg85.otg.fabric.portals;

import com.pg85.otg.config.settings.preset.PortalColors;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.fabric.portals.components.OTGComponents;
import com.pg85.otg.shared.portals.SharedOTGPortalBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

public class FabricPortalBlocks {

    private static final Map<String, SharedOTGPortalBlock> PORTAL_BLOCKS = new HashMap<>();

    public static void register() {
        for (String color : PortalColors.COLORS) {
            SharedOTGPortalBlock block = new SharedOTGPortalBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .noCollission()
                            .randomTicks()
                            .strength(-1.0F)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> 11),
                    color
            );

            String id = "otg_portal_" + color;
            Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, id), block);
            PORTAL_BLOCKS.put(color, block);
        }

        SharedOTGPortalBlock.init(
                FabricPortalBlocks::getPortalBlock,
                player -> OTGComponents.get(player)
        );
    }

    public static SharedOTGPortalBlock getPortalBlock(String color) {
        String normalizedColor = color.toLowerCase().trim();
        return PORTAL_BLOCKS.getOrDefault(normalizedColor, PORTAL_BLOCKS.get("default"));
    }

    public static Map<String, SharedOTGPortalBlock> getAllPortalBlocks() {
        return PORTAL_BLOCKS;
    }
}
