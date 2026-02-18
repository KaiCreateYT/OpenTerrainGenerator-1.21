package com.pg85.otg.fabric;

import com.pg85.otg.OTG;
import com.pg85.otg.fabric.commands.FabricCommandWorldAccessor;
import com.pg85.otg.fabric.dimensions.FabricDimensionHelper;
import com.pg85.otg.shared.commands.OTGCommandRegistrar;
import com.pg85.otg.shared.dimensions.DimensionManager;
import com.pg85.otg.fabric.portals.FabricPortalBlocks;
import com.pg85.otg.fabric.portals.PortalIgnitionHandler;
import com.pg85.otg.fabric.events.WorldSaveCallback;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.shared.materials.SharedMaterialReader;
import com.pg85.otg.shared.util.OTGLogger;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.logging.LogCategory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.chunk.ChunkGenerator;

@SuppressWarnings("unused")
public class OTGPlugin implements ModInitializer {
	private static DimensionManager dimensionManager;

	public static DimensionManager getDimensionManager() {
		return dimensionManager;
	}

	@Override
	public void onInitialize() {
		OTGLog.setLogger(new OTGLogger());
		OTGLog.info("OTG Engine starting");
		OTGMaterialReader.set(new SharedMaterialReader());
		OTG.startEngine(new FabricEngine());

		registerWorldSave();
		registerCommands();
		registerServerEvents();
		registerPortals();

		OTGLog.info("OTG Engine started, presets loaded");
	}

	void registerWorldSave() {
		WorldSaveCallback.EVENT.register((serverLevel) -> {
			ChunkGenerator chunkGenerator = serverLevel.getChunkSource().getGenerator();
			if (chunkGenerator instanceof OTGFabricChunkGenerator fabricChunkGenerator) {
				OTGLog.info(LogCategory.STRUCTURE_PLOTTING, "Saving structure cache for world {}", fabricChunkGenerator.getPreset().getFolderName());
				fabricChunkGenerator.saveStructureCache();
			}
		});
	}

	void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			OTGCommandRegistrar.register(dispatcher, new FabricCommandWorldAccessor());
			OTGLog.info("Registered OTG commands");
		});
	}

	void registerServerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			dimensionManager = new DimensionManager(new FabricDimensionHelper());
			dimensionManager.initialize(server);
			OTGCommandRegistrar.setDimensionManager(dimensionManager);
			OTGLog.info("OTG Dimension Manager initialized");
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			OTGLog.info("Server stopping");
		});
	}

	void registerPortals() {
		FabricPortalBlocks.register();
		PortalIgnitionHandler.register();
		OTGLog.info("OTG Portal blocks and ignition handler registered");
	}
}
