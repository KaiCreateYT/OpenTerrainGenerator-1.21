package com.pg85.otg.neoforge.events;

import com.pg85.otg.neoforge.dimensions.NeoForgeDimensionCommands;
import com.pg85.otg.neoforge.dimensions.NeoForgeDimensionManager;
import com.pg85.otg.neoforge.gen.OTGNeoForgeChunkGenerator;
import com.pg85.otg.neoforge.portals.OTGAttachments;
import com.pg85.otg.neoforge.portals.OTGPlayerData;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class NeoForgeEventHandler {
    private static NeoForgeDimensionManager dimensionManager;

    public static NeoForgeDimensionManager getDimensionManager() {
        return dimensionManager;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        NeoForgeDimensionCommands.register(event.getDispatcher());
        OTGLog.info("Registered OTG dimension commands");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        dimensionManager = new NeoForgeDimensionManager();
        dimensionManager.initialize(event.getServer());
        NeoForgeDimensionCommands.setManager(dimensionManager);
        OTGLog.info("OTG Dimension Manager initialized");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        OTGLog.info("Server stopping");
    }

    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ChunkGenerator chunkGenerator = serverLevel.getChunkSource().getGenerator();
            if (chunkGenerator instanceof OTGNeoForgeChunkGenerator otgGen) {
                OTGLog.info(LogCategory.STRUCTURE_PLOTTING,
                    "Saving structure cache for world " + otgGen.getPreset().getFolderName());
                otgGen.saveStructureCache();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OTGPlayerData data = player.getData(OTGAttachments.OTG_PLAYER.get());
            data.tick();
        }
    }
}
