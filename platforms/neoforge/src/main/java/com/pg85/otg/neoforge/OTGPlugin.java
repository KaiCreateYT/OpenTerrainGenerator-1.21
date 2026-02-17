package com.pg85.otg.neoforge;

import com.pg85.otg.OTG;
import com.pg85.otg.neoforge.events.NeoForgeEventHandler;
import com.pg85.otg.shared.materials.SharedMaterialReader;
import com.pg85.otg.neoforge.portals.NeoForgePortalBlocks;
import com.pg85.otg.neoforge.portals.OTGAttachments;
import com.pg85.otg.neoforge.portals.PortalIgnitionHandler;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod("otg")
public class OTGPlugin {
    private static NeoForgeEventHandler eventHandler;

    public OTGPlugin(IEventBus modBus) {
        OTGLog.setLogger(new com.pg85.otg.shared.util.OTGLogger());
        OTGLog.getLogger().log(LogLevel.INFO, LogCategory.MAIN, "OTG Engine starting");
        OTGMaterialReader.set(new SharedMaterialReader());
        OTG.startEngine(new NeoForgeEngine());

        // Register Data Attachments
        OTGAttachments.ATTACHMENT_TYPES.register(modBus);

        // Register portal blocks via DeferredRegister
        NeoForgePortalBlocks.register(modBus);
        NeoForgePortalBlocks.initSharedCallbacks();

        // Register game event handlers
        NeoForge.EVENT_BUS.register(NeoForgeEventHandler.class);
        NeoForge.EVENT_BUS.register(PortalIgnitionHandler.class);

        OTG.log("OTG Engine started, presets loaded");
    }

    public static NeoForgeEventHandler getEventHandler() {
        return eventHandler;
    }

    public static com.pg85.otg.shared.dimensions.DimensionManager getDimensionManager() {
        return NeoForgeEventHandler.getDimensionManager();
    }
}
