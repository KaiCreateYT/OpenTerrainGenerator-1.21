package com.pg85.otg.neoforge.portals;

import com.pg85.otg.shared.portals.SharedPortalIgnitionHandler;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class PortalIgnitionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = SharedPortalIgnitionHandler.onUseBlock(
                event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        if (result == InteractionResult.SUCCESS) {
            event.setCanceled(true);
        }
    }
}
