package com.pg85.otg.fabric.portals;

import com.pg85.otg.config.settings.preset.PortalColors;
import com.pg85.otg.constants.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

public class FabricPortalBlocks {

    private static final Map<String, OTGPortalBlock> PORTAL_BLOCKS = new HashMap<>();

    public static void register() {
        for (String color : PortalColors.COLORS) {
            OTGPortalBlock block = new OTGPortalBlock(
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
            Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(Constants.MOD_ID_SHORT, id), block);
            PORTAL_BLOCKS.put(color, block);
        }
    }

    public static OTGPortalBlock getPortalBlock(String color) {
        String normalizedColor = color.toLowerCase().trim();
        return PORTAL_BLOCKS.getOrDefault(normalizedColor, PORTAL_BLOCKS.get("default"));
    }

    public static Map<String, OTGPortalBlock> getAllPortalBlocks() {
        return PORTAL_BLOCKS;
    }
}
