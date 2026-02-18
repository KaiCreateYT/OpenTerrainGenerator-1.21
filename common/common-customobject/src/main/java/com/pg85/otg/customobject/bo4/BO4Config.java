package com.pg85.otg.customobject.bo4;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.constants.settings.ConfigMode;
import com.pg85.otg.customobject.CustomObject;
import com.pg85.otg.customobject.CustomObjectManager;
import com.pg85.otg.customobject.bo3.BO3Config;
import com.pg85.otg.customobject.bo4.bo4function.BO4BlockFunction;
import com.pg85.otg.customobject.bo4.bo4function.BO4BranchFunction;
import com.pg85.otg.customobject.bo4.bo4function.BO4EntityFunction;
import com.pg85.otg.customobject.bo4.bo4function.BO4RandomBlockFunction;
import com.pg85.otg.customobject.bo4.bo4function.BO4WeightedBranchFunction;
import com.pg85.otg.customobject.bofunctions.BlockFunction;
import com.pg85.otg.customobject.bofunctions.BranchFunction;
import com.pg85.otg.customobject.config.CustomObjectConfigFile;
import com.pg85.otg.customobject.config.CustomObjectConfigFunction;
import com.pg85.otg.customobject.config.CustomObjectErroredFunction;
import com.pg85.otg.customobject.config.CustomObjectResourcesManager;
import com.pg85.otg.customobject.config.io.SettingsReaderBO4;
import com.pg85.otg.customobject.config.io.SettingsWriterBO4;
import com.pg85.otg.customobject.structures.bo4.BO4CustomStructureCoordinate;
import com.pg85.otg.customobject.util.BO3Enums.SpawnHeightEnum;
import com.pg85.otg.customobject.util.BoundingBox;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.ICustomObjectManager;
import com.pg85.otg.interfaces.IMaterialReader;
import com.pg85.otg.interfaces.IModLoadedChecker;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.nbt.NamedBinaryTag;
import com.pg85.otg.util.bo3.Rotation;
import com.pg85.otg.util.helpers.StreamHelper;
import com.pg85.otg.util.helpers.StringHelper;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.minecraft.DefaultStructurePart;

import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class BO4Config extends CustomObjectConfigFile
{
	public String author;
	public String description;
	public boolean doReplaceBlocks;
	public int frequency;
	public Rotation fixedRotation;

	private final int xSize = 16;
	private final int zSize = 16;
	public int minHeight;
	public int maxHeight;

	public SpawnHeightEnum spawnHeight;
	public boolean useCenterForHighestBlock;
	public final BoundingBox[] boundingBoxes = new BoundingBox[4];

	boolean inheritedBO3Loaded;

	// These are used in CustomObjectStructure when determining the minimum area in chunks that
	// this branching structure needs to be able to spawn
	public int minimumSizeTop = -1;
	public int minimumSizeBottom = -1;
	public int minimumSizeLeft = -1;
	public int minimumSizeRight = -1;

	public int timesSpawned = 0;

	public int branchFrequency;
	// Define groups that this BO3 belongs to with a range in chunks that members of each group should have to each other
	String branchFrequencyGroup;
	public HashMap<String, Integer> branchFrequencyGroups;

	int minX;
	int maxX;
	int minY;
	int maxY;
	int minZ;
	int maxZ;

	ArrayList<String> inheritedBO3s;

	// Adjusts the height by this number before spawning. Handy when using "highestblock" for lowering BO3s that have a lot of ground under them included
	public int heightOffset;
	Rotation inheritBO3Rotation;
	// If this is set to true then any air blocks in the bo3 will not be spawned
	boolean removeAir;
	private boolean configRemoveAir;
	// Defaults to false. Set to true if this BO3 should spawn at the player spawn point. When the server starts one of the structures that has IsSpawnPoint set to true is selected randomly and is spawned, the others never get spawned.)
	public boolean isSpawnPoint;

	// Replaces all the non-air blocks that are above this BO3 or its smoothing area with the given block material (should be WATER or AIR or NONE), also applies to smoothing areas although it intentionally leaves some of the terrain above them intact. WATER can be used in combination with SpawnUnderWater to fill any air blocks underneath waterlevel with water (and any above waterlevel with air).
	public String replaceAbove;
	public String configReplaceAbove;
	// Replaces all non-air blocks underneath the BO3 (but not its smoothing area) with the designated material until a solid block is found.
	public String replaceBelow;
	public String configReplaceBelow;
	// Defaults to true. If set to true then every block in the BO3 of the materials defined in ReplaceWithGroundBlock or ReplaceWithSurfaceBlock will be replaced by the GroundBlock or SurfaceBlock materials configured for the biome the block is spawned in.
	public boolean replaceWithBiomeBlocks;
	// Replaces all the blocks of the given material in the BO3 with the GroundBlock configured for the biome it spawns in
	public String replaceWithGroundBlock;
	// Replaces all the blocks of the given material in the BO3 with the SurfaceBlock configured for the biome it spawns in
	public String replaceWithSurfaceBlock;
	// Replaces all the blocks of the given material in the BO3 with the StoneBlock configured for the biome it spawns in
	public String replaceWithStoneBlock;
	// Define a group that this BO3 belongs to and a range in chunks that members of this group should have to each other
	String bo3Group;
	public HashMap<String, Integer> bo4Groups;
	// If this is set to true then this BO3 can spawn on top of or inside other BO3's
	public boolean canOverride;

	// Copies the blocks and branches of an existing BO3 into this one
	String inheritBO3;
	// Should the smoothing area go to the top or the bottom blocks in the bo3?
	public boolean smoothStartTop;
	public boolean smoothStartWood;
	// The size of the smoothing area
	public int smoothRadius;
	// The materials used for the smoothing area
	public String smoothingSurfaceBlock;
	public String smoothingGroundBlock;
	// If true then root BO3 smoothing and height settings are used for all children
	public boolean overrideChildSettings;
	public boolean overrideParentHeight;

	// Used to make sure that dungeons can only spawn underneath other structures
	public boolean mustBeBelowOther;

	// Used to make sure that dungeons can only spawn inside worldborders
	public boolean mustBeInsideWorldBorders;

	String replacesBO3;
	public ArrayList<String> replacesBO3Branches;
	String mustBeInside;
	public ArrayList<String> mustBeInsideBranches;
	String cannotBeInside;
	public ArrayList<String> cannotBeInsideBranches;

	public int smoothHeightOffset;
	public boolean canSpawnOnWater;
	public boolean spawnOnWaterOnly;
	public boolean spawnUnderWater;
	public boolean spawnAtWaterLevel;

	private String presetFolderName;

	final BO4BlockStorage blockStorage = new BO4BlockStorage();

	boolean isBO4Data = false;

	/**
	 * Creates a BO4Config from a file.
	 *
	 * @param reader		The settings of the BO4.
	 */
	public BO4Config(SettingsReaderBO4 reader, boolean init, String presetFolderName, Path otgRootFolder,  CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker) throws InvalidConfigException
	{
		super(reader);
		if(init)
		{
			init(presetFolderName, otgRootFolder,  customObjectManager, materialReader, manager, modLoadedChecker);
		}
	}

	private BO4Config(SettingsReaderBO4 reader)
	{
		super(reader);
	}

	BO4Config createBlankCopy()
	{
		return new BO4Config((SettingsReaderBO4) this.reader);
	}

	private void init(String presetFolderName, Path otgRootFolder,  CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker) throws InvalidConfigException
	{
		this.minX = Integer.MAX_VALUE;
		this.maxX = Integer.MIN_VALUE;
		this.minY = Integer.MAX_VALUE;
		this.maxY = Integer.MIN_VALUE;
		this.minZ = Integer.MAX_VALUE;
		this.maxZ = Integer.MIN_VALUE;
		if(!this.reader.getFile().getAbsolutePath().toLowerCase().endsWith(".bo4data"))
		{
			readConfigSettings(presetFolderName, otgRootFolder,  customObjectManager, materialReader, manager, modLoadedChecker);
		} else {
			this.readFromBO4DataFile(false,  materialReader);
		}

		// When writing, we'll need to read some raw data from the file,
		// so can't flush the cache yet. Flush after writing.
		if(this.settingsMode == ConfigMode.WriteDisable)
		{
			this.reader.flushCache();
		}
	}

	public int getXOffset()
	{
		return minX < -8 ? -minX : maxX > 7 ? -minX : 8;
	}

	public int getZOffset()
	{
		return minZ < -7 ? -minZ : maxZ > 8 ? -minZ : 7;
	}

	public int getminX()
	{
		return minX + this.getXOffset(); // + xOffset makes sure that the value returned is never negative which is necessary for the collision detection code for CustomStructures in OTG (it assumes the furthest top and left blocks are at => 0 x or >= 0 z in the BO3)
	}

	public int getmaxX()
	{
		return maxX + this.getXOffset(); // + xOffset makes sure that the value returned is never negative which is necessary for the collision detection code for CustomStructures in OTG (it assumes the furthest top and left blocks are at => 0 x or >= 0 z in the BO3)
	}

	public int getminY()
	{
		return minY;
	}

	public int getmaxY()
	{
		return maxY;
	}

	public int getminZ()
	{
		return minZ + this.getZOffset(); // + zOffset makes sure that the value returned is never negative which is necessary for the collision detection code for CustomStructures in OTG (it assumes the furthest top and left blocks are at => 0 x or >= 0 z in the BO3)
	}

	public int getmaxZ()
	{
		return maxZ + this.getZOffset(); // + zOffset makes sure that the value returned is never negative which is necessary for the collision detection code for CustomStructures in OTG (it assumes the furthest top and left blocks are at => 0 x or >= 0 z in the BO3)
	}

	public ArrayList<String> getInheritedBO3s()
	{
		return this.inheritedBO3s;
	}

	public BO4BlockFunction[][] getSmoothingHeightMap(BO4 start, String presetFolderName, Path otgRootFolder,  CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker)
	{
		return blockStorage.getSmoothingHeightMap(this, start, true, presetFolderName, otgRootFolder, customObjectManager, materialReader, manager, modLoadedChecker);
	}

	BO4BlockFunction[] getBlocks(String presetFolderName, Path otgRootFolder,  CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker)
	{
		return blockStorage.getBlocks(this, true, presetFolderName, otgRootFolder, customObjectManager, materialReader, manager, modLoadedChecker);
	}

	public BO4BranchFunction[] getbranches()
	{
		return blockStorage.getbranches();
	}

	public BO4EntityFunction[] getEntityData()
	{
		return blockStorage.getEntityData();
	}

	public boolean isCollidable()
	{
		return blockStorage.isCollidable();
	}

	public void setBlocks(List<BlockFunction<?>> newBlocks)
	{
		blockStorage.setBlocks(newBlocks);
	}

	public void setBranches(List<BranchFunction<?>> branches)
	{
		blockStorage.setBranches(branches);
	}

	void loadInheritedBO3(String presetFolderName, Path otgRootFolder,  ICustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker)
	{
		if(this.inheritBO3 != null && !this.inheritBO3.trim().isEmpty() && !this.inheritedBO3Loaded)
		{
			File currentFile = this.getFile().getParentFile();
			this.presetFolderName = currentFile.getName();
			while(currentFile.getParentFile() != null && !currentFile.getName().equals(Constants.PRESETS_FOLDER))
			{
				this.presetFolderName = currentFile.getName();
				currentFile = currentFile.getParentFile();
				if(this.presetFolderName.equals(Constants.GLOBAL_OBJECTS_FOLDER))
				{
					this.presetFolderName = null;
					break;
				}
			}

			// TODO: Re-wire this so we don't have to cast CustomObjectManager :(
			CustomObjectManager customObjectManager2 = (CustomObjectManager)customObjectManager;
			CustomObject parentBO3 = customObjectManager2.getGlobalObjects().getObjectByName(this.inheritBO3, this.presetFolderName, otgRootFolder,  customObjectManager2, materialReader, manager, modLoadedChecker);
			if(parentBO3 != null)
			{
				BO4BlockFunction[] blocks = getBlocks(this.presetFolderName, otgRootFolder,  customObjectManager2, materialReader, manager, modLoadedChecker);

				this.inheritedBO3Loaded = true;

				this.inheritedBO3s.addAll(((BO4)parentBO3).getConfig().getInheritedBO3s());

				this.removeAir = ((BO4)parentBO3).getConfig().removeAir;
				this.replaceAbove = this.replaceAbove == null || this.replaceAbove.isEmpty() ? ((BO4)parentBO3).getConfig().replaceAbove : this.replaceAbove;
				this.replaceBelow = this.replaceBelow == null || this.replaceBelow.isEmpty() ? ((BO4)parentBO3).getConfig().replaceBelow : this.replaceBelow;

				BO4CustomStructureCoordinate rotatedParentMaxCoords = BO4CustomStructureCoordinate.getRotatedBO3Coords(((BO4)parentBO3).getConfig().maxX, ((BO4)parentBO3).getConfig().maxY, ((BO4)parentBO3).getConfig().maxZ, this.inheritBO3Rotation);
				BO4CustomStructureCoordinate rotatedParentMinCoords = BO4CustomStructureCoordinate.getRotatedBO3Coords(((BO4)parentBO3).getConfig().minX, ((BO4)parentBO3).getConfig().minY, ((BO4)parentBO3).getConfig().minZ, this.inheritBO3Rotation);

				int parentMaxX = Math.max(rotatedParentMaxCoords.getX(), rotatedParentMinCoords.getX());
				int parentMinX = Math.min(rotatedParentMaxCoords.getX(), rotatedParentMinCoords.getX());

				int parentMaxY = rotatedParentMaxCoords.getY() > rotatedParentMinCoords.getY() ? rotatedParentMaxCoords.getY() : rotatedParentMinCoords.getY();
				int parentMinY = rotatedParentMaxCoords.getY() < rotatedParentMinCoords.getY() ? rotatedParentMaxCoords.getY() : rotatedParentMinCoords.getY();

				int parentMaxZ = Math.max(rotatedParentMaxCoords.getZ(), rotatedParentMinCoords.getZ());
				int parentMinZ = Math.min(rotatedParentMaxCoords.getZ(), rotatedParentMinCoords.getZ());

				if(parentMaxX > this.maxX)
				{
					this.maxX = parentMaxX;
				}
				if(parentMinX < this.minX)
				{
					this.minX = parentMinX;
				}
				if(parentMaxY > this.maxY)
				{
					this.maxY = parentMaxY;
				}
				if(parentMinY < this.minY)
				{
					this.minY = parentMinY;
				}
				if(parentMaxZ > this.maxZ)
				{
					this.maxZ = parentMaxZ;
				}
				if(parentMinZ < this.minZ)
				{
					this.minZ = parentMinZ;
				}

				BO4BlockFunction[] parentBlocks = ((BO4)parentBO3).getConfig().getBlocks(presetFolderName, otgRootFolder,  customObjectManager2, materialReader, manager, modLoadedChecker);
				ArrayList<BlockFunction<?>> newBlocks = new ArrayList<>();
				newBlocks.addAll(new ArrayList<>(Arrays.asList(parentBlocks)));
				newBlocks.addAll(new ArrayList<>(Arrays.asList(blocks)));

				short[][] columnSizes = new short[16][16];
				for(BlockFunction<?> block : newBlocks)
				{
					columnSizes[block.x][block.z]++;
				}

				blockStorage.loadBlockArrays(newBlocks, columnSizes);
				blockStorage.setCollidable(!newBlocks.isEmpty());

				ArrayList<BO4BranchFunction> newBranches = new ArrayList<>();
				if(blockStorage.getbranches() != null)
				{
                    newBranches.addAll(Arrays.asList(blockStorage.getbranches()));
				}
				for(BO4BranchFunction branch : ((BO4)parentBO3).getConfig().blockStorage.getbranches())
				{
					newBranches.add(branch.rotate(this.inheritBO3Rotation, presetFolderName, otgRootFolder,  customObjectManager2, materialReader, manager, modLoadedChecker));
				}
				blockStorage.setBranchesArray(newBranches.toArray(new BO4BranchFunction[0]));

				ArrayList<BO4EntityFunction> newEntityData = new ArrayList<>();
				if(blockStorage.getEntityData() != null)
				{
                    newEntityData.addAll(Arrays.asList(blockStorage.getEntityData()));
				}
				for(BO4EntityFunction entityData : ((BO4)parentBO3).getConfig().blockStorage.getEntityData())
				{
					newEntityData.add(entityData.rotate(this.inheritBO3Rotation));
				}
				blockStorage.setEntityData(newEntityData.toArray(new BO4EntityFunction[0]));

				this.inheritedBO3s.addAll(((BO4)parentBO3).getConfig().getInheritedBO3s());
			}
			if(!this.inheritedBO3Loaded)
			{
				OTGLog.error(LogCategory.CUSTOM_OBJECTS, "could not load BO4 parent for InheritBO3: {} in BO4 {}", this.inheritBO3, this.getName());
			}
		}
	}

	private void readResources( IMaterialReader materialReader, CustomObjectResourcesManager manager) throws InvalidConfigException
	{
		List<BO4BlockFunction> tempBlocksList = new ArrayList<>();
		List<BO4BranchFunction> tempBranchesList = new ArrayList<>();
		List<BO4EntityFunction> tempEntitiesList = new ArrayList<>();

		short[][] columnSizes = new short[xSize][zSize];

		ArrayList<CustomObjectConfigFunction<BO4Config>> resources = new ArrayList<>();
		int minX = 0;
		int maxX = 0;
		int minZ = 0;
		int maxZ = 0;
		for (CustomObjectConfigFunction<BO4Config> res : reader.getConfigFunctions(this, true,  materialReader, manager))
		{
			if (res.isValid())
			{
				resources.add(res);
				if( // TODO: Add interface instead?
					!(res instanceof BranchFunction) &&
					!(res instanceof CustomObjectErroredFunction)
				)
				{
					if(res.x < minX)
					{
						minX = res.x;
					}
					if(res.x > maxX)
					{
						maxX = res.x;
					}
					if(res.z < minZ)
					{
						minZ = res.z;
					}
					if(res.z > maxZ)
					{
						maxZ = res.z;
					}
				}
			}
		}

		int xSize = Math.abs(minX - maxX);
		int zSize = Math.abs(minZ - maxZ);
		if(xSize > 15 || zSize > 15)
		{
			OTGLog.error(LogCategory.CUSTOM_OBJECTS, "BO4 {} was too large ({}x{}), BO4's can be max 16x16 blocks.", this.getName(), xSize, zSize);
			throw new InvalidConfigException("BO4 " + this.getName() + " was too large, BO4's can be max 16x16 blocks.");
		}

		int xOffset = 0;
		int zOffset = 0;

		if(minX < -8)
		{
			xOffset = -minX - 8;
		}
		if(maxX > 7)
		{
			xOffset = -(maxX - 7);
		}
		if(minZ < -7)
		{
			zOffset = -minZ - 7;
		}
		if(maxZ > 8)
		{
			zOffset = -(maxZ - 8);
		}

		boolean hasBlocks = false;
		for (CustomObjectConfigFunction<BO4Config> res : resources)
		{
			if( // TODO: Add interface instead?
				!(res instanceof BranchFunction) &&
				!(res instanceof CustomObjectErroredFunction)
				)
			{
				res.x += xOffset;
				res.z += zOffset;
			}

			if (res instanceof BO4BlockFunction)
			{
				hasBlocks = true;

				if(res instanceof BO4RandomBlockFunction)
				{
					tempBlocksList.add((BO4RandomBlockFunction)res);
					columnSizes[res.x + (this.xSize / 2)][res.z + (this.zSize / 2) - 1]++;
				} else {
					if(!this.removeAir || !((BO4BlockFunction)res).material.isAir())
					{
						tempBlocksList.add((BO4BlockFunction)res);
						columnSizes[res.x + (this.xSize / 2)][res.z + (this.zSize / 2) - 1]++;
					}
				}

				// Get the real size of this BO3
				if(res.x < this.minX)
				{
					this.minX = res.x;
				}
				if(res.x > this.maxX)
				{
					this.maxX = res.x;
				}
				if(((BO4BlockFunction)res).y < this.minY)
				{
					this.minY = ((BO4BlockFunction)res).y;
				}
				if(((BO4BlockFunction)res).y > this.maxY)
				{
					this.maxY = ((BO4BlockFunction)res).y;
				}
				if(res.z < this.minZ)
				{
					this.minZ = res.z;
				}
				if(res.z > this.maxZ)
				{
					this.maxZ = res.z;
				}
			} else {
                switch (res) {
                    case BO4WeightedBranchFunction bo4WeightedBranchFunction -> tempBranchesList.add(bo4WeightedBranchFunction);
                    case BO4BranchFunction bo4BranchFunction -> tempBranchesList.add(bo4BranchFunction);
                    case BO4EntityFunction bo4EntityFunction -> tempEntitiesList.add(bo4EntityFunction);
                    default -> {
                    }
                }
			}
		}

		if(this.minX == Integer.MAX_VALUE)
		{
			this.minX = -8;
		}
		if(this.maxX == Integer.MIN_VALUE)
		{
			this.maxX = -8;
		}
		if(this.minY == Integer.MAX_VALUE)
		{
			this.minY = 0;
		}
		if(this.maxY == Integer.MIN_VALUE)
		{
			this.maxY = 0;
		}
		if(this.minZ == Integer.MAX_VALUE)
		{
			this.minZ = -7;
		}
		if(this.maxZ == Integer.MIN_VALUE)
		{
			this.maxZ = -7;
		}

		// TODO: OTG+ Doesn't do CustomObject BO3's, only check for 16x16, not 32x32?
		boolean illegalBlock = false;
		for(BO4BlockFunction block1 : tempBlocksList)
		{
			block1.x += this.getXOffset();
			block1.z += this.getZOffset();

			if(block1.x > 15 || block1.z > 15)
			{
				illegalBlock = true;
			}

			if(block1.x < 0 || block1.z < 0)
			{
				illegalBlock = true;
			}
		}

		blockStorage.loadBlockArrays(new ArrayList<>(tempBlocksList), columnSizes);
		blockStorage.setCollidable(hasBlocks);

		boolean illegalEntityData = false;
		for(BO4EntityFunction entityData : tempEntitiesList)
		{
			entityData.x += this.getXOffset();
			entityData.z += this.getZOffset();

			if(entityData.x > 15 || entityData.z > 15)
			{
				illegalEntityData = true;
			}

			if(entityData.x < 0 || entityData.z < 0)
			{
				illegalEntityData = true;
			}
		}
		blockStorage.setEntityData(tempEntitiesList.toArray(new BO4EntityFunction[0]));

		if(illegalBlock)
		{
			OTGLog.warn(LogCategory.CUSTOM_OBJECTS, "Warning: BO4 contains Blocks or RandomBlocks that are placed outside the chunk(s) that the BO3 will be placed in. This can slow down world generation. BO4: {}", this.getName());
		}
		if(illegalEntityData)
		{
			OTGLog.warn(LogCategory.CUSTOM_OBJECTS, "Warning: BO4 contains an Entity() that may be placed outside the chunk(s) that the BO3 will be placed in. This can slow down world generation. BO4: {}", this.getName());
		}

		blockStorage.setBranchesArray(tempBranchesList.toArray(new BO4BranchFunction[0]));
    }

	/**
	 * Gets the file this config will be written to. May be null if the config
	 * will never be written.
	 * @return The file.
	 */
	public File getFile()
	{
		return this.reader.getFile();
	}

	@Override
	public BlockFunction<?>[] getBlockFunctions(String presetFolderName, Path otgRootFolder,  ICustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker)
	{
		return getBlocks(presetFolderName, otgRootFolder,  (CustomObjectManager) customObjectManager, materialReader, manager, modLoadedChecker);
	}

	@Override
	protected void writeConfigSettings(SettingsWriterBO4 writer,  IMaterialReader materialReader, CustomObjectResourcesManager manager) throws IOException
	{
		writeSettings(writer, null, null,  materialReader, manager);
	}

	public void writeWithData(SettingsWriterBO4 writer, List<BlockFunction<?>> blocksList, List<BranchFunction<?>> branchesList,  IMaterialReader materialReader, CustomObjectResourcesManager manager) throws IOException
	{
		writer.setConfigMode(ConfigMode.WriteAll);
		try
		{
			writer.open();
			writeSettings(writer, blocksList, branchesList,  materialReader, manager);
		} finally {
			writer.close();
		}
	}

	private void writeSettings(SettingsWriterBO4 writer, List<BlockFunction<?>> blocksList, List<BranchFunction<?>> branchesList,  IMaterialReader materialReader, CustomObjectResourcesManager manager) throws IOException
	{
		// The object
		writer.bigTitle("BO4 object");
		writer.comment("This is the config file of a custom object.");
		writer.comment("If you add this object correctly to your BiomeConfigs, it will spawn in the world.");
		writer.comment("");

		writer.comment("This is the creator of this BO4 object");
		writer.setting(BO4Settings.AUTHOR, this.author);

		writer.comment("A short description of this BO4 object");
		writer.setting(BO4Settings.DESCRIPTION, this.description);

		if(writer.getFile().getName().toUpperCase().endsWith(".BO3"))
		{
			writer.comment("Legacy setting, always true for BO4's. Only used if the file has a .BO3 extension.");
			writer.comment("Rename your file to .BO4 and remove this setting.");
			writer.setting(BO4Settings.ISOTGPLUS, true);
		}

		writer.comment("The settings mode, WriteAll, WriteWithoutComments or WriteDisable. See PresetConfig.");
		writer.setting(BO3Config.SETTINGS_MODE_BO3, this.settingsMode);

		// Main settings
		writer.bigTitle("Main settings");

		writer.comment("If this BO4 should spawn with a fixed rotation, set it here.");
		writer.comment("For example: NORTH, EAST, SOUTH or WEST. Empty by default");
		writer.setting(BO4Settings.FIXED_ROTATION, this.fixedRotation == null ? "" : this.fixedRotation.name());

		writer.comment("This BO4 can only spawn at least Frequency chunks distance away from any other BO4 with the exact same name.");
		writer.comment("You can use this to make this BO4 spawn in groups or make sure that this BO4 only spawns once every X chunks.");
		writer.setting(BO4Settings.FREQUENCY, this.frequency);

		writer.comment("The spawn height of the BO4: randomY, highestBlock or highestSolidBlock.");
		writer.setting(BO4Settings.SPAWN_HEIGHT, this.spawnHeight);

		writer.comment("When set to true, uses the center of the structure (determined by minimum structure size) when checking the highestBlock to spawn at.");
		writer.setting(BO4Settings.USE_CENTER_FOR_HIGHEST_BLOCK, this.useCenterForHighestBlock);

		writer.smallTitle("Height Limits for the BO4.");

		writer.comment("When in randomY mode used as the minimum Y or in atMinY mode as the actual Y to spawn this BO4 at.");
		writer.setting(BO4Settings.MIN_HEIGHT, this.minHeight);

		writer.comment("When in randomY mode used as the maximum Y to spawn this BO4 at.");
		writer.setting(BO4Settings.MAX_HEIGHT, this.maxHeight);

		writer.comment("Copies the blocks and branches of an existing BO4 into this BO4. You can still add blocks and branches in this BO4, they will be added on top of the inherited blocks and branches.");
		writer.setting(BO4Settings.INHERITBO3, this.inheritBO3);
		writer.comment("Rotates the inheritedBO3's resources (blocks, spawners, checks etc) and branches, defaults to NORTH (no rotation).");
		writer.setting(BO4Settings.INHERITBO3ROTATION, this.inheritBO3Rotation);

		writer.comment("Defaults to true, if true and this is the starting BO4 for this branching structure then this BO4's smoothing and height settings are used for all children (branches).");
		writer.setting(BO4Settings.OVERRIDECHILDSETTINGS, this.overrideChildSettings);
		writer.comment("Defaults to false, if true then this branch uses it's own height settings (SpawnHeight, minHeight, maxHeight, spawnAtWaterLevel) instead of those defined in the starting BO4 for this branching structure.");
		writer.setting(BO4Settings.OVERRIDEPARENTHEIGHT, this.overrideParentHeight);
		writer.comment("If this is set to true then this BO4 can spawn on top of or inside an existing BO4. If this is set to false then this BO4 will use a bounding box to detect collisions with other BO4's, if a collision is detected then this BO4 won't spawn and the current branch is rolled back.");
		writer.setting(BO4Settings.CANOVERRIDE, this.canOverride);

		writer.comment("This branch can only spawn at least branchFrequency chunks (x,z) distance away from any other branch with the exact same name.");
		writer.setting(BO4Settings.BRANCH_FREQUENCY, this.branchFrequency);
		writer.comment("Define groups that this branch belongs to along with a minimum (x,z) range in chunks that this branch must have between it and any other members of this group if it is to be allowed to spawn. Syntax is \"GroupName:Frequency, GoupName2:Frequency2\" etc so for example a branch that belongs to 3 groups: \"BranchFrequencyGroup: Ships:10, Vehicles:5, FloatingThings:3\".");
		writer.setting(BO4Settings.BRANCH_FREQUENCY_GROUP, this.branchFrequencyGroup);

		writer.comment("If this is set to true then this BO4 can only spawn underneath an existing BO4. Used to make sure that dungeons only appear underneath buildings.");
		writer.setting(BO4Settings.MUSTBEBELOWOTHER, this.mustBeBelowOther);

		writer.comment("Used with CanOverride: true. A comma-seperated list of BO4s, this BO4's bounding box must collide with one of the BO4's in the list or this BO4 fails to spawn and the current branch is rolled back. AND/OR is supported, comma is OR, space is AND, f.e: branch1, branch2 branch3, branch 4.");
		writer.setting(BO4Settings.MUSTBEINSIDE, this.mustBeInside);

		writer.comment("Used with CanOverride: true. A comma-seperated list of BO4s, this BO4's bounding box cannot collide with any of the BO4's in the list or this BO4 fails to spawn and the current branch is rolled back.");
		writer.setting(BO4Settings.CANNOTBEINSIDE, this.cannotBeInside);

		writer.comment("Used with CanOverride: true. A comma-seperated list of BO4s, if this BO4's bounding box collides with any of the BO4's in the list then those BO4's won't spawn any blocks. This does not remove or roll back any BO4's.");
		writer.setting(BO4Settings.REPLACESBO3, this.replacesBO3);

		writer.comment("If this is set to true then this BO4 can only spawn inside world borders. Used to make sure that dungeons only appear inside the world borders.");
		writer.setting(BO4Settings.MUSTBEINSIDEWORLDBORDERS, this.mustBeInsideWorldBorders);

		writer.comment("Defaults to true. Set to false if the BO4 is not allowed to spawn on a water block");
		writer.setting(BO4Settings.CANSPAWNONWATER, this.canSpawnOnWater);

		writer.comment("Defaults to false. Set to true if the BO4 is allowed to spawn only on a water block");
		writer.setting(BO4Settings.SPAWNONWATERONLY, this.spawnOnWaterOnly);

		writer.comment("Defaults to false. Set to true if the BO4 and its smoothing area should ignore water when looking for the highest block to spawn on. Defaults to false (things spawn on top of water)");
		writer.setting(BO4Settings.SPAWNUNDERWATER, this.spawnUnderWater);

		writer.comment("Defaults to false. Set to true if the BO4 should spawn at water level");
		writer.setting(BO4Settings.SPAWNATWATERLEVEL, this.spawnAtWaterLevel);

		writer.comment("Spawns the BO4 at a Y offset of this value. Handy when using highestBlock for lowering BO4s into the surrounding terrain when there are layers of ground included in the BO4, also handy when using SpawnAtWaterLevel to lower objects like ships into the water.");
		writer.setting(BO4Settings.HEIGHT_OFFSET, this.heightOffset);

		writer.comment("If set to true removes all AIR blocks from the BO4 so that it can be flooded or buried.");
		writer.setting(BO4Settings.REMOVEAIR, this.configRemoveAir);

		writer.comment("Replaces all the non-air blocks that are above this BO4 or its smoothing area with the given block material (should be WATER or AIR or NONE), also applies to smoothing areas although OTG intentionally leaves some of the terrain above them intact. WATER can be used in combination with SpawnUnderWater to fill any air blocks underneath waterlevel with water (and any above waterlevel with air).");
		writer.setting(BO4Settings.REPLACEABOVE, this.configReplaceAbove);

		writer.comment("Replaces all air blocks underneath the BO4 (but not its smoothing area) with the specified material until a solid block is found.");
		writer.setting(BO4Settings.REPLACEBELOW, this.configReplaceBelow);

		writer.comment("Defaults to true. If set to true then every block in the BO4 of the materials defined in ReplaceWithGroundBlock or ReplaceWithSurfaceBlock will be replaced by the GroundBlock or SurfaceBlock materials configured for the biome the block is spawned in.");
		writer.setting(BO4Settings.REPLACEWITHBIOMEBLOCKS, this.replaceWithBiomeBlocks);

		writer.comment("Defaults to GRASS, Replaces all the blocks of the given material in the BO4 with the SurfaceBlock configured for the biome it spawns in.");
		writer.setting(BO4Settings.REPLACEWITHSURFACEBLOCK, this.replaceWithSurfaceBlock);

		writer.comment("Defaults to DIRT, Replaces all the blocks of the given material in the BO4 with the GroundBlock configured for the biome it spawns in.");
		writer.setting(BO4Settings.REPLACEWITHGROUNDBLOCK, this.replaceWithGroundBlock);

		writer.comment("Defaults to STONE, Replaces all the blocks of the given material in the BO4 with the StoneBlock configured for the biome it spawns in.");
		writer.setting(BO4Settings.REPLACEWITHSTONEBLOCK, this.replaceWithStoneBlock);

		writer.comment("Makes the terrain around the BO4 slope evenly towards the edges of the BO4. The given value is the distance in blocks around the BO4 from where the slope should start and can be any positive number.");
		writer.setting(BO4Settings.SMOOTHRADIUS, this.smoothRadius);

		writer.comment("Moves the smoothing area up or down relative to the BO4 (at the points where the smoothing area is connected to the BO4). Handy when using SmoothStartTop: false and the BO4 has some layers of ground included, in that case we can set the HeightOffset to a negative value to lower the BO4 into the ground and we can set the SmoothHeightOffset to a positive value to move the smoothing area starting height up.");
		writer.setting(BO4Settings.SMOOTH_HEIGHT_OFFSET, this.smoothHeightOffset);

		writer.comment("Should the smoothing area be attached at the bottom or the top of the edges of the BO4? Defaults to false (bottom). Using this setting can make things slower so try to avoid using it and use SmoothHeightOffset instead if for instance you have a BO4 with some ground layers included. The only reason you should need to use this setting is if you have a BO4 with edges that have an irregular height (like some hills).");
		writer.setting(BO4Settings.SMOOTHSTARTTOP, this.smoothStartTop);

		writer.comment("Should the smoothing area attach itself to \"log\" block or ignore them? Defaults to false (ignore logs).");
		writer.setting(BO4Settings.SMOOTHSTARTWOOD, this.smoothStartWood);

		writer.comment("The block used for smoothing area surface blocks, defaults to biome SurfaceBlock.");
		writer.setting(BO4Settings.SMOOTHINGSURFACEBLOCK, this.smoothingSurfaceBlock);

		writer.comment("The block used for smoothing area ground blocks, defaults to biome GroundBlock.");
		writer.setting(BO4Settings.SMOOTHINGGROUNDBLOCK, this.smoothingGroundBlock);

		writer.comment("Define groups that this BO4 belongs to along with a minimum range in chunks that this BO4 must have between it and any other members of this group if it is to be allowed to spawn. Syntax is \"GroupName:Frequency, GoupName2:Frequency2\" etc so for example a BO4 that belongs to 3 groups: \"BO4Group: Ships:10, Vehicles:5, FloatingThings:3\".");
		writer.setting(BO4Settings.BO3GROUP, this.bo3Group);

		writer.comment("Defaults to false. Set to true if this BO4 should spawn at the player spawn point. When the server starts the spawn point is determined and the BO4's for the biome it is in are loaded, one of these BO4s that has IsSpawnPoint set to true (if any) is selected randomly and is spawned at the spawn point regardless of its rarity (so even Rarity:0, IsSpawnPoint: true BO4's can get spawned as the spawn point!).");
		writer.setting(BO4Settings.ISSPAWNPOINT, this.isSpawnPoint);

		writer.comment("Defaults to true. Set to false to make the BO4 ignore any ReplacedBlocks settings in Biome Configs.");
		writer.setting(BO4Settings.DO_REPLACE_BLOCKS, this.doReplaceBlocks);

		// Blocks and other things
		writeResources(writer, blocksList, branchesList,  materialReader, manager);

		if(this.reader != null) // Can be true for BO4Creator?
		{
			this.reader.flushCache();
		}
	}

	@Override
	protected void readConfigSettings(String presetFolderName, Path otgRootFolder,  ICustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker) throws InvalidConfigException
	{
		this.branchFrequency = readSettings(BO4Settings.BRANCH_FREQUENCY,  materialReader, manager);

		this.branchFrequencyGroup = readSettings(BO4Settings.BRANCH_FREQUENCY_GROUP,  materialReader, manager);
		this.branchFrequencyGroups = StringHelper.parseGroupMap(this.branchFrequencyGroup);

		this.heightOffset = readSettings(BO4Settings.HEIGHT_OFFSET,  materialReader, manager);
		this.inheritBO3Rotation = readSettings(BO4Settings.INHERITBO3ROTATION,  materialReader, manager);

		this.configRemoveAir = readSettings(BO4Settings.REMOVEAIR,  materialReader, manager);
		this.removeAir = this.configRemoveAir;
		this.isSpawnPoint = readSettings(BO4Settings.ISSPAWNPOINT,  materialReader, manager);
		this.useCenterForHighestBlock = readSettings(BO4Settings.USE_CENTER_FOR_HIGHEST_BLOCK,  materialReader, manager);
		this.configReplaceAbove = readSettings(BO4Settings.REPLACEABOVE,  materialReader, manager);
		this.replaceAbove = this.configReplaceAbove;
		this.configReplaceBelow = readSettings(BO4Settings.REPLACEBELOW,  materialReader, manager);
		this.replaceBelow = this.configReplaceBelow;
		this.replaceWithBiomeBlocks = readSettings(BO4Settings.REPLACEWITHBIOMEBLOCKS,  materialReader, manager);
		this.replaceWithGroundBlock = readSettings(BO4Settings.REPLACEWITHGROUNDBLOCK,  materialReader, manager);
		this.replaceWithSurfaceBlock = readSettings(BO4Settings.REPLACEWITHSURFACEBLOCK,  materialReader, manager);
		this.replaceWithStoneBlock = readSettings(BO4Settings.REPLACEWITHSTONEBLOCK,  materialReader, manager);

		this.bo3Group = readSettings(BO4Settings.BO3GROUP,  materialReader, manager);
		this.bo4Groups = StringHelper.parseGroupMap(this.bo3Group);

		this.canOverride = readSettings(BO4Settings.CANOVERRIDE,  materialReader, manager);
		this.mustBeBelowOther = readSettings(BO4Settings.MUSTBEBELOWOTHER,  materialReader, manager);
		this.mustBeInsideWorldBorders = readSettings(BO4Settings.MUSTBEINSIDEWORLDBORDERS,  materialReader, manager);

		this.mustBeInside = readSettings(BO4Settings.MUSTBEINSIDE,  materialReader, manager);
		this.mustBeInsideBranches = StringHelper.splitTrimmedList(this.mustBeInside);

		this.cannotBeInside =  readSettings(BO4Settings.CANNOTBEINSIDE,  materialReader, manager);
		this.cannotBeInsideBranches = StringHelper.splitTrimmedList(this.cannotBeInside);

		this.replacesBO3 = readSettings(BO4Settings.REPLACESBO3,  materialReader, manager);
		this.replacesBO3Branches = StringHelper.splitTrimmedList(this.replacesBO3);

		//smoothHeightOffset = readSettings(BO3Settings.SMOOTH_HEIGHT_OFFSET).equals("HeightOffset") ? heightOffset : Integer.parseInt(readSettings(BO3Settings.SMOOTH_HEIGHT_OFFSET));
		this.smoothHeightOffset = readSettings(BO4Settings.SMOOTH_HEIGHT_OFFSET,  materialReader, manager);
		this.canSpawnOnWater = readSettings(BO4Settings.CANSPAWNONWATER,  materialReader, manager);
		this.spawnOnWaterOnly = readSettings(BO4Settings.SPAWNONWATERONLY,  materialReader, manager);
		this.spawnUnderWater = readSettings(BO4Settings.SPAWNUNDERWATER,  materialReader, manager);
		this.spawnAtWaterLevel = readSettings(BO4Settings.SPAWNATWATERLEVEL,  materialReader, manager);
		this.inheritBO3 = readSettings(BO4Settings.INHERITBO3,  materialReader, manager);
		this.overrideChildSettings = readSettings(BO4Settings.OVERRIDECHILDSETTINGS,  materialReader, manager);
		this.overrideParentHeight = readSettings(BO4Settings.OVERRIDEPARENTHEIGHT,  materialReader, manager);
		this.smoothRadius = readSettings(BO4Settings.SMOOTHRADIUS,  materialReader, manager);
		this.smoothStartTop = readSettings(BO4Settings.SMOOTHSTARTTOP,  materialReader, manager);
		this.smoothStartWood = readSettings(BO4Settings.SMOOTHSTARTWOOD,  materialReader, manager);
		this.smoothingSurfaceBlock = readSettings(BO4Settings.SMOOTHINGSURFACEBLOCK,  materialReader, manager);
		this.smoothingGroundBlock = readSettings(BO4Settings.SMOOTHINGGROUNDBLOCK,  materialReader, manager);

		// Make sure that the BO3 wont try to spawn below Y 0 because of the height offset
		if(this.heightOffset < 0 && this.minHeight < -this.heightOffset)
		{
			this.minHeight = -this.heightOffset;
		}

		this.inheritedBO3s = new ArrayList<>();
		this.inheritedBO3s.add(this.getName()); // TODO: Make this cleaner?
		if(this.inheritBO3 != null && !this.inheritBO3.trim().isEmpty())
		{
			this.inheritedBO3s.add(this.inheritBO3);
		}

		this.author = readSettings(BO4Settings.AUTHOR,  materialReader, manager);
		this.description = readSettings(BO4Settings.DESCRIPTION,  materialReader, manager);
		this.settingsMode = readSettings(BO3Config.SETTINGS_MODE_BO3,  materialReader, manager);

		this.frequency = readSettings(BO4Settings.FREQUENCY,  materialReader, manager);
		this.spawnHeight = readSettings(BO4Settings.SPAWN_HEIGHT,  materialReader, manager);
		this.minHeight = readSettings(BO4Settings.MIN_HEIGHT,  materialReader, manager);
		this.maxHeight = readSettings(BO4Settings.MAX_HEIGHT,  materialReader, manager);
		this.maxHeight = Math.max(this.maxHeight, this.minHeight);

		this.doReplaceBlocks = readSettings(BO4Settings.DO_REPLACE_BLOCKS,  materialReader, manager);

		String fixedRotation = readSettings(BO4Settings.FIXED_ROTATION,  materialReader, manager);
		this.fixedRotation = Rotation.getRotation(fixedRotation);

		// Read the resources
		readResources( materialReader, manager);
	}

	private void writeResources(SettingsWriterBO4 writer, List<BlockFunction<?>> blocksList, List<BranchFunction<?>> branchesList,  IMaterialReader materialReader, CustomObjectResourcesManager manager) throws IOException
	{
		writer.bigTitle("Blocks");
		writer.comment("All the blocks used in the BO4 are listed here. Possible blocks:");
		writer.comment("Block(x,y,z,id[.data][,nbtfile.nbt)");
		writer.comment("RandomBlock(x,y,z,id[:data][,nbtfile.nbt],chance[,id[:data][,nbtfile.nbt],chance[,...]])");
		writer.comment(" So RandomBlock(0,0,0,CHEST,chest.nbt,50,CHEST,anotherchest.nbt,100) will spawn a chest at");
		writer.comment(" the BO4 origin, and give it a 50% chance to have the contents of chest.nbt, or, if that");
		writer.comment(" fails, a 100% percent chance to have the contents of anotherchest.nbt.");
		writer.comment("MinecraftObject(x,y,z,name) (NOT IMPLEMENTED - will log a warning and be skipped).");
		writer.comment(" Spawns an object in the Mojang NBT structure format. For example, ");
		writer.comment(" MinecraftObject(0,0,0," + DefaultStructurePart.IGLOO_BOTTOM.getPath() + ")");
		writer.comment(" spawns the bottom part of an igloo.");

		ArrayList<BO4EntityFunction> entitiesList = new ArrayList<>();

		// Re-read the raw data, if no data was supplied. Don't save any loaded data, since it has been processed/transformed.
		if(blocksList == null || branchesList == null)
		{
			blocksList = new ArrayList<>();
			branchesList = new ArrayList<>();

			for (CustomObjectConfigFunction<BO4Config> res : reader.getConfigFunctions(this, true,  materialReader, manager))
			{
				if (res.isValid())
				{
                    switch (res) {
                        case BO4RandomBlockFunction bo4RandomBlockFunction -> blocksList.add(bo4RandomBlockFunction);
                        case BO4BlockFunction bo4BlockFunction -> blocksList.add(bo4BlockFunction);
                        case BO4WeightedBranchFunction bo4WeightedBranchFunction -> branchesList.add(bo4WeightedBranchFunction);
                        case BO4BranchFunction bo4BranchFunction -> branchesList.add(bo4BranchFunction);
                        case BO4EntityFunction bo4EntityFunction -> entitiesList.add(bo4EntityFunction);
                        default -> {
                        }
                    }
				}
			}
		}
		else {
			// The blockslist passed is not used after this,
			// so it's ok to edit the block objects.
			for(BlockFunction<?> block : blocksList)
			{
				block.x -= this.getXOffset();
				block.z -= this.getZOffset();
			}
		}
		for(BlockFunction<?> block : blocksList)
		{
			writer.function(block);
		}

		writer.bigTitle("Branches");
		writer.comment("Branches are child-BO4's that spawn if this BO4 is configured to spawn as a");
		writer.comment("CustomStructure resource in a biome config. Branches can have branches,");
		writer.comment("making complex structures possible. See the wiki for more details.");
		writer.comment("");
		writer.comment("Regular Branches spawn each branch with an independent chance of spawning.");
		writer.comment("Branch(x,y,z,isRequiredBranch,branchName,rotation,chance,branchDepth[,anotherBranchName,rotation,chance,branchDepth[,...]][IndividualChance])");
		writer.comment("branchName - name of the object to spawn.");
		writer.comment("rotation - NORTH, SOUTH, EAST or WEST.");
		writer.comment("IndividualChance - The chance each branch has to spawn, assumed to be 100 when left blank");
		writer.comment("isRequiredBranch - If this is set to true then at least one of the branches in this BO4 must spawn at these x,y,z coordinates. If no branch can spawn there then this BO4 fails to spawn and its branch is rolled back.");
		writer.comment("isRequiredBranch:true branches must spawn or the current branch is rolled back entirely. This is useful for grouping BO4's that must spawn together, for instance a single room made of multiple BO4's/branches.");
		writer.comment("If all parts of the room are connected together via isRequiredBranch:true branches then either the entire room will spawns or no part of it will spawn.");
		writer.comment("*Note: When isRequiredBranch:true only one BO4 can be added per Branch() and it will automatically have a rarity of 100.0.");
		writer.comment("isRequiredBranch:false branches are used to make optional parts of structures, for instance the middle section of a tunnel that has a beginning, middle and end BO4/branch and can have a variable length by repeating the middle BO4/branch.");
		writer.comment("By making the start and end branches isRequiredBranch:true and the middle branch isRequiredbranch:false you can make it so that either:");
		writer.comment("A. A tunnel spawns with at least a beginning and end branch");
		writer.comment("B. A tunnel spawns with a beginning and end branch and as many middle branches as will fit in the available space.");
		writer.comment("C. No tunnel spawns at all because there wasn't enough space to spawn at least a beginning and end branch.");
		writer.comment("branchDepth - When creating a chain of branches that contains optional (isRequiredBranch:false) branches branch depth is configured for the first BO4 in the chain to determine the maximum length of the chain.");
		writer.comment("branchDepth - 1 is inherited by each isRequiredBranch:false branch in the chain. When branchDepth is zero isRequiredBranch:false branches cannot spawn and the chain ends. In the case of the tunnel this means the last middle branch would be");
		writer.comment("rolled back and an IsRequiredBranch:true end branch could be spawned in its place to make sure the tunnel has a proper ending.");
		writer.comment("Instead of inheriting branchDepth - 1 from the parent branchDepth can be overridden by child branches if it is set higher than 0 (the default value).");
		writer.comment("isRequiredBranch:true branches do inherit branchDepth and pass it on to their own branches, however they cannot be prevented from spawning by it and also don't subtract 1 from branchDepth when inheriting it.");
		writer.comment("");
		writer.comment("Weighted Branches spawn branches with a dependent chance of spawning.");
		writer.comment("WeightedBranch(x,y,z,isRequiredBranch,branchName,rotation,chance,branchDepth[,anotherBranchName,rotation,chance,branchDepth[,...]][MaxChanceOutOf])");
		writer.comment("*Note: isRequiredBranch must be set to false. It is not possible to use isRequiredBranch:true with WeightedBranch() since isRequired:true branches must spawn and automatically have a rarity of 100.0.");
		writer.comment("MaxChanceOutOf - The chance all branches have to spawn out of, assumed to be 100 when left blank");

		for(BranchFunction<?> func : branchesList)
		{
			writer.function(func);
		}

		writer.bigTitle("Entities");
		writer.comment("Forge only (this may have changed, check for updates).");
		writer.comment("An EntityFunction spawns an entity instead of a block. The entity is spawned only once when the BO4 is spawned.");
		writer.comment("Entities are persistent by default so they don't de-spawn when no player is near, they are only unloaded.");
		writer.comment("Usage: Entity(x,y,z,entityName,groupSize,NameTagOrNBTFileName) or Entity(x,y,z,mobName,groupSize)");
		writer.comment("Use /otg entities to get a list of entities that can be used as entityName, this includes entities added by other mods and non-living entities.");
		writer.comment("NameTagOrNBTFileName can be either a nametag for the mob or an .txt file with nbt data (such as myentityinfo.txt).");
		writer.comment("In the text file you can use the same mob spawning parameters used with the /summon command to equip the");
		writer.comment("entity and give it custom attributes etc. You can copy the DATA part of a summon command including surrounding ");
		writer.comment("curly braces to a .txt file, for instance for: \"/summon Skeleton x y z {DATA}\"");

		for(BO4EntityFunction func : entitiesList)
		{
			writer.function(func);
		}
	}

	void writeToStream(DataOutput stream, String presetFolderName, Path otgRootFolder,  CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker) throws IOException
	{
		BO4DataSerializer.writeToStream(this, stream, presetFolderName, otgRootFolder, customObjectManager, materialReader, manager, modLoadedChecker);
	}

	BO4Config readFromBO4DataFile(boolean getBlocks,  IMaterialReader materialReader) throws InvalidConfigException
	{
		return BO4DataSerializer.readFromBO4DataFile(this, getBlocks, materialReader);
	}

	@Override
	protected void correctSettings() { }

	@Override
	protected void renameOldSettings() { }
}
