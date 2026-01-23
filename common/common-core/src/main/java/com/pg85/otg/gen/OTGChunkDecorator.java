package com.pg85.otg.gen;

import com.pg85.otg.OTG;
import com.pg85.otg.config.ConfigFunction;
import com.pg85.otg.config.ErroredFunction;
import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.constants.settings.structure.CustomStructureType;
import com.pg85.otg.customobject.CustomObject;
import com.pg85.otg.customobject.CustomObjectManager;
import com.pg85.otg.customobject.bo3.BO3;
import com.pg85.otg.customobject.config.CustomObjectResourcesManager;
import com.pg85.otg.customobject.resource.ICustomObjectResource;
import com.pg85.otg.customobject.resource.ICustomStructureResource;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.customobject.util.BO3Enums.SpawnHeightEnum;
import com.pg85.otg.gen.resource.IBasicResource;
import com.pg85.otg.gen.surface.FrozenSurfaceHelper;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.interfaces.IChunkDecorator;
import com.pg85.otg.interfaces.IMaterialReader;
import com.pg85.otg.interfaces.IModLoadedChecker;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.bo3.Rotation;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Random;

/**
 * Takes care of resource decoration. Spawns all OTG resources. Some of the decoration steps (like vanilla 
 * structures and mob spawning) use mc logic and are spawned by mc itself. For those decoration steps, OTG 
 * only fills in the required configurations when registering the biomes in the platform-specific layer (see OTGBiome/ForgeBiome).
 */
public class OTGChunkDecorator implements IChunkDecorator
{
	private final Random rand;

	// Locking objects / checks to prevent decorate running on multiple threads,
	// or when the world is waiting for an opportunity to save.
	// TODO: Is this still required for 1.16?
	private final Object lockingObject = new Object();
	private int decorating = 0;
	private boolean saving;
	private boolean saveRequired;
	private final Object asynChunkDecorationLock = new Object();

	public OTGChunkDecorator()
	{
		this.rand = new Random();
	}
	
	@Override
	public boolean getIsSaveRequired()
	{
		return this.saveRequired;
	}

	@Override
	public boolean isDecorating()
	{
		return this.decorating != 0;
	}

	@Override
	public void beginSave()
	{
		this.saving = true;
	}

	@Override
	public void endSave()
	{
		this.saveRequired = false;
		this.saving = false;
	}

	@Override
	public Object getLockingObject()
	{
		return this.lockingObject;
	}

	public void decorate(ChunkCoordinate chunkCoord, IWorldGenRegion worldGenRegion, BiomeSettings biomeConfig, CustomStructureCache structureCache)
	{
		// Wait for another thread running SaveToDisk, then place a lock.
		boolean firstLog = false;
		while(true)
		{
			synchronized(this.lockingObject)
			{
				if(!this.saving)
				{
					this.decorating++;
					this.saveRequired = true;
					break;
				} else {
					if(firstLog)
					{
						OTGLog.log(LogLevel.WARN, LogCategory.MAIN, "Decorate waiting on SaveToDisk. Although other mods could be causing this and there may not be any problem, this can potentially cause an endless loop!");
						firstLog = false;
					}
				}
			}
		}

		Path otgRootFolder = OTG.getEngine().getOTGRootFolder();
		CustomObjectManager customObjectManager = OTG.getEngine().getCustomObjectManager();
		IMaterialReader materialReader = OTGMaterialReader.get();
		CustomObjectResourcesManager customObjectResourcesManager = OTG.getEngine().getCustomObjectResourcesManager();
		IModLoadedChecker modLoadedChecker = OTG.getEngine().getModLoadedChecker();

		doDecorate(chunkCoord, worldGenRegion, biomeConfig, materialReader, otgRootFolder, structureCache, customObjectManager, customObjectResourcesManager, modLoadedChecker);			
		
		// Release the lock
		synchronized(this.lockingObject)
		{
			this.decorating--;
		}
	}

	// TODO: Fire decoration events.
	private void doDecorate(ChunkCoordinate chunkCoord, IWorldGenRegion worldGenRegion, BiomeSettings biomeConfig, IMaterialReader materialReader, Path otgRootFolder, CustomStructureCache structureCache, CustomObjectManager customObjectManager, CustomObjectResourcesManager customObjectResourcesManager, IModLoadedChecker modLoadedChecker)
	{		
		if (biomeConfig == null)
		{
			if(OTGLog.getLogCategoryEnabled(LogCategory.DECORATION))
			{
				OTGLog.log(
					LogLevel.ERROR,
					LogCategory.DECORATION,
					MessageFormat.format(
						"Unknown biome at {0},{1}  (chunk {2}). Could not decorate chunk.", 
						chunkCoord.getChunkX(), 
						chunkCoord.getChunkZ(), 
						chunkCoord
					)
				);
			}
			return;
		}

		// Get the random generator
		long resourcesSeed = worldGenRegion.getSeed();
		this.rand.setSeed(resourcesSeed);
		long l1 = this.rand.nextLong() / 2L * 2L + 1L;
		long l2 = this.rand.nextLong() / 2L * 2L + 1L;
		this.rand.setSeed(chunkCoord.getChunkX() * l1 + chunkCoord.getChunkZ() * l2 ^ resourcesSeed);

		// Use BO4 logic for BO4 worlds
		if(worldGenRegion.getPresetConfig().getResourceSettings().getCustomStructureType() == CustomStructureType.BO4)
		{
			// BO4 Plotting cannot currently be done in a thread-safe/non-blocking way,
			// Paper may try to do async chunkgen, so lock here. This will ofcourse 
			// slow down any multithreaded chunk decoration implementation.
			synchronized(asynChunkDecorationLock)
			{
				plotAndSpawnBO4s(structureCache, worldGenRegion, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX(), chunkCoord.getChunkZ()), chunkCoord, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
			}
		}

		if(
			worldGenRegion.getSpawnChunk().equals(chunkCoord) &&
			worldGenRegion.getPresetConfig().getResourceSettings().getBO3AtSpawn() != null &&
			!worldGenRegion.getPresetConfig().getResourceSettings().getBO3AtSpawn().trim().isEmpty()
		)
		{
			handleBO3AtSpawn(worldGenRegion, chunkCoord, worldGenRegion.getPresetConfig().getResourceSettings().getBO3AtSpawn(), worldGenRegion.getPresetFolderName(), otgRootFolder, structureCache, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		}
		
		long startTimeAll = System.currentTimeMillis();
		// Resource sequence
		var resourceQueue = ((BiomeConfig)biomeConfig).getResourceQueue();
		// DEBUG: Log resource queue size
		if (resourceQueue.isEmpty()) {
			System.err.println("DEBUG DECORATE: Empty resource queue for biome " + biomeConfig.getIdentitySettings().getBiomeName() + " at chunk [" + chunkCoord.getChunkX() + "," + chunkCoord.getChunkZ() + "]");
		} else if (chunkCoord.getChunkX() == 0 && chunkCoord.getChunkZ() == 0) {
			System.err.println("DEBUG DECORATE: " + resourceQueue.size() + " resources for biome " + biomeConfig.getIdentitySettings().getBiomeName());
			for (var r : resourceQueue) {
				System.err.println("  - " + r.getClass().getSimpleName() + ": " + r.toString());
			}
		}
		for (ConfigFunction<BiomeSettings> res : resourceQueue)
		{
			long startTime = System.currentTimeMillis();
			if (res instanceof ICustomObjectResource)
			{
				((ICustomObjectResource)res).processForChunkDecoration(structureCache, worldGenRegion, this.rand, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
				if(OTGLog.getLogCategoryEnabled(LogCategory.PERFORMANCE) && (System.currentTimeMillis() - startTime) > 50)
				{
					OTGLog.log(LogLevel.WARN, LogCategory.PERFORMANCE, "Warning: Processing resource " + res.toString() + " in biome " + biomeConfig.getIdentitySettings().getBiomeName() + " took " + (System.currentTimeMillis() - startTime) + " Ms.");
				}
			}
			else if (res instanceof ICustomStructureResource)
			{
				((ICustomStructureResource)res).processForChunkDecoration(structureCache, worldGenRegion, this.rand, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
				if(OTGLog.getLogCategoryEnabled(LogCategory.PERFORMANCE) && (System.currentTimeMillis() - startTime) > 50)
				{
					OTGLog.log(LogLevel.WARN, LogCategory.PERFORMANCE, "Warning: Processing resource " + res.toString() + " in biome " + biomeConfig.getIdentitySettings().getBiomeName() + " took " + (System.currentTimeMillis() - startTime) + " Ms.");
				}
			}
			else if (res instanceof IBasicResource)
			{
				((IBasicResource)res).processForChunkDecoration(worldGenRegion, this.rand);
				if(OTGLog.getLogCategoryEnabled(LogCategory.PERFORMANCE) && (System.currentTimeMillis() - startTime) > 50)
				{
					OTGLog.log(LogLevel.WARN, LogCategory.PERFORMANCE, "Warning: Processing resource " + res.toString() + " in biome " + biomeConfig.getIdentitySettings().getBiomeName() + " took " + (System.currentTimeMillis() - startTime) + " Ms.");
				}				
			}
			else if(res instanceof ErroredFunction)
			{
				if(OTGLog.getLogCategoryEnabled(LogCategory.DECORATION))
				{
					if(!((ErroredFunction<BiomeSettings>)res).isLogged)
					{
						((ErroredFunction<BiomeSettings>)res).isLogged = true;
						if(OTGLog.getLogCategoryEnabled(LogCategory.DECORATION))
						{
							OTGLog.log(LogLevel.ERROR, LogCategory.DECORATION, "Errored setting ignored for biome " + biomeConfig.getIdentitySettings().getBiomeName() + " : " + toString());
						}
					}					
				}
			}
		}
		if(OTGLog.getLogCategoryEnabled(LogCategory.PERFORMANCE) && (System.currentTimeMillis() - startTimeAll) > 50)
		{
			OTGLog.log(LogLevel.WARN, LogCategory.PERFORMANCE, "Warning: Processing resources in biome " + biomeConfig.getIdentitySettings().getBiomeName() + " took " + (System.currentTimeMillis() - startTimeAll) + " Ms.");
		}
	}

	public void doSnowAndIce(IWorldGenRegion worldGenRegion, ChunkCoordinate chunkCoord)
	{
		// Snow and ice
		// TODO: Snow is appearing below structures, indicating it spawned before 
		// it should. Check and align decoration bounds for resources and make sure
		// freezing is done during the correct decoration step.
		FrozenSurfaceHelper.freezeChunk(worldGenRegion, chunkCoord);
	}

	private void plotAndSpawnBO4s(CustomStructureCache structureCache, IWorldGenRegion worldGenRegion, ChunkCoordinate chunkCoord, ChunkCoordinate chunkBeingDecorated, Path otgRootFolder, CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager customObjectResourcesManager, IModLoadedChecker modLoadedChecker)
	{
		// Plot and spawn BO4's for all chunks that may have blocks spawned on them while decorating this chunk, 
		// so we can be sure those chunks have had a chance to plot+spawn bo4's before other resources.

		structureCache.plotBo4Structures(worldGenRegion, this.rand, chunkCoord, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);		
		structureCache.plotBo4Structures(worldGenRegion, this.rand, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() + 1, chunkCoord.getChunkZ()), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);		
		structureCache.plotBo4Structures(worldGenRegion, this.rand, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() , chunkCoord.getChunkZ() + 1), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);			
		structureCache.plotBo4Structures(worldGenRegion, this.rand, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() + 1, chunkCoord.getChunkZ() + 1), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);	

		spawnBO4(structureCache, worldGenRegion, chunkCoord, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		spawnBO4(structureCache, worldGenRegion, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() + 1, chunkCoord.getChunkZ()), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);	
		spawnBO4(structureCache, worldGenRegion, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX(), chunkCoord.getChunkZ() + 1), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		spawnBO4(structureCache, worldGenRegion, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() + 1, chunkCoord.getChunkZ() + 1), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);		
	}

	private void spawnBO4(CustomStructureCache structureCache, IWorldGenRegion worldGenRegion, ChunkCoordinate chunkCoord, Path otgRootFolder, CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker)
	{
		structureCache.spawnBo4Chunk(worldGenRegion, chunkCoord, otgRootFolder, customObjectManager, materialReader, manager, modLoadedChecker);
	}
	
	private void handleBO3AtSpawn(IWorldGenRegion worldGenRegion, ChunkCoordinate targetChunk, String bo3AtSpawn, String presetFolderName, Path otgRootFolder, CustomStructureCache structureCache, CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager customObjectResourcesManager, IModLoadedChecker modLoadedChecker)
	{	
		// If a BO3AtSpawn has been defined, spawn it.
		CustomObject customObject = customObjectManager.getGlobalObjects().getObjectByName(
			bo3AtSpawn,
			presetFolderName,
			otgRootFolder,
			customObjectManager,
			materialReader,
			customObjectResourcesManager,
			modLoadedChecker
		);
		if(customObject != null)
		{
			if(customObject instanceof BO3)
			{
				int y = 1;
				if(((BO3)customObject).getConfig().getSpawnHeight() == SpawnHeightEnum.highestBlock)
				{
					y = worldGenRegion.getHighestBlockAboveYAt(targetChunk.getBlockX() + 15, targetChunk.getBlockZ() + 15) - 1;
				}
				else if(((BO3)customObject).getConfig().getSpawnHeight() == SpawnHeightEnum.highestSolidBlock)
				{
					y = worldGenRegion.getBlockAboveSolidHeight(targetChunk.getBlockX() + 15, targetChunk.getBlockZ() + 15) - 1;
				}
				else if(((BO3)customObject).getConfig().getSpawnHeight() == SpawnHeightEnum.randomY)
				{
					y = (int) (((BO3)customObject).getConfig().minHeight + (Math.random() * (((BO3)customObject).getConfig().maxHeight - ((BO3)customObject).getConfig().minHeight)));
				}

				y += ((BO3)customObject).getConfig().getSpawnHeightOffset();
				// This may spawn the structure across chunk borders.
				((BO3)customObject).spawnForced(
                        structureCache,
                        worldGenRegion,
                        this.rand,
                        Rotation.NORTH,
					targetChunk.getBlockX() + 16 + ((BO3)customObject).getXOffset(Rotation.NORTH),
                        y,
					targetChunk.getBlockZ() + 16 + ((BO3)customObject).getZOffset(Rotation.NORTH),
                        true
				);
			}
		}
	}
}
