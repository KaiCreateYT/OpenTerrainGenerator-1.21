package com.pg85.otg.neoforge.portals;

import com.pg85.otg.config.settings.preset.PortalColors;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.shared.portals.SharedOTGPortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

public class NeoForgePortalBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Constants.MOD_ID_SHORT);

    private static final Map<String, DeferredHolder<Block, SharedOTGPortalBlock>> PORTAL_BLOCKS = new HashMap<>();

    static {
        for (String color : PortalColors.COLORS) {
            String id = "otg_portal_" + color;
            final String c = color;
            DeferredHolder<Block, SharedOTGPortalBlock> holder = BLOCKS.register(id, () -> new SharedOTGPortalBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .noCollission()
                            .noOcclusion()
                            .randomTicks()
                            .strength(-1.0F)
                            .sound(SoundType.GLASS)
                            .requiredFeatures(FeatureFlags.VANILLA)
                            .lightLevel(state -> 11),
                    c
            ));
            PORTAL_BLOCKS.put(color, holder);
        }
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    public static void initSharedCallbacks() {
        SharedOTGPortalBlock.init(
                NeoForgePortalBlocks::getPortalBlock,
                player -> player.getData(OTGAttachments.OTG_PLAYER.get())
        );
    }

    public static SharedOTGPortalBlock getPortalBlock(String color) {
        String normalizedColor = color.toLowerCase().trim();
        DeferredHolder<Block, SharedOTGPortalBlock> holder = PORTAL_BLOCKS.get(normalizedColor);
        if (holder == null) {
            holder = PORTAL_BLOCKS.get("default");
        }
        return holder != null ? holder.get() : null;
    }

    public static Map<String, SharedOTGPortalBlock> getAllPortalBlocks() {
        Map<String, SharedOTGPortalBlock> result = new HashMap<>();
        for (Map.Entry<String, DeferredHolder<Block, SharedOTGPortalBlock>> entry : PORTAL_BLOCKS.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }
}
