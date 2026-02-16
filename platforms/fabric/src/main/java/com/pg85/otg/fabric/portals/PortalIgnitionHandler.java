package com.pg85.otg.fabric.portals;

import com.pg85.otg.shared.portals.SharedPortalIgnitionHandler;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public class PortalIgnitionHandler {

    public static void register() {
        UseBlockCallback.EVENT.register(SharedPortalIgnitionHandler::onUseBlock);
    }
}
