package com.pg85.otg.neoforge.materials;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.materials.LocalMaterials;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Arrays;
import java.util.stream.Collectors;

public class NeoForgeMaterials extends LocalMaterials
{
	// Default blocks in given tags.
	// Tags aren't loaded until datapacks are loaded, on world creation. We mirror the vanilla copy of the tag to solve this.
	private static final Block[] CORAL_BLOCKS_TAG = { Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK };
	private static final Block[] WALL_CORALS_TAG = { Blocks.TUBE_CORAL_WALL_FAN, Blocks.BRAIN_CORAL_WALL_FAN, Blocks.BUBBLE_CORAL_WALL_FAN, Blocks.FIRE_CORAL_WALL_FAN, Blocks.HORN_CORAL_WALL_FAN };
	private static final Block[] CORALS_TAG = { Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL, Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN };

	public static void init()
	{
		// Coral
		CORAL_BLOCKS = Arrays.stream(CORAL_BLOCKS_TAG).map(block -> NeoForgeMaterialData.ofBlockState(block.defaultBlockState())).collect(Collectors.toList());
		WALL_CORALS = Arrays.stream(WALL_CORALS_TAG).map(block -> NeoForgeMaterialData.ofBlockState(block.defaultBlockState())).collect(Collectors.toList());
		CORALS = Arrays.stream(CORALS_TAG).map(block -> NeoForgeMaterialData.ofBlockState(block.defaultBlockState())).collect(Collectors.toList());
				
		// Blocks used in OTG code
		
		AIR = NeoForgeMaterialData.ofBlockState(Blocks.AIR.defaultBlockState());
		CAVE_AIR = NeoForgeMaterialData.ofBlockState(Blocks.CAVE_AIR.defaultBlockState());
		STRUCTURE_VOID = NeoForgeMaterialData.ofBlockState(Blocks.STRUCTURE_VOID.defaultBlockState());
		COMMAND_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.COMMAND_BLOCK.defaultBlockState());
		STRUCTURE_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.STRUCTURE_BLOCK.defaultBlockState());
		GRASS = NeoForgeMaterialData.ofBlockState(Blocks.GRASS_BLOCK.defaultBlockState());
		DIRT = NeoForgeMaterialData.ofBlockState(Blocks.DIRT.defaultBlockState());
		CLAY = NeoForgeMaterialData.ofBlockState(Blocks.CLAY.defaultBlockState());
		TERRACOTTA = NeoForgeMaterialData.ofBlockState(Blocks.TERRACOTTA.defaultBlockState());
		WHITE_TERRACOTTA = NeoForgeMaterialData.ofBlockState(Blocks.WHITE_TERRACOTTA.defaultBlockState());
		ORANGE_TERRACOTTA = NeoForgeMaterialData.ofBlockState(Blocks.ORANGE_TERRACOTTA.defaultBlockState());
		YELLOW_TERRACOTTA = NeoForgeMaterialData.ofBlockState(Blocks.YELLOW_TERRACOTTA.defaultBlockState());
		BROWN_TERRACOTTA = NeoForgeMaterialData.ofBlockState(Blocks.BROWN_TERRACOTTA.defaultBlockState());
		RED_TERRACOTTA = NeoForgeMaterialData.ofBlockState(Blocks.RED_TERRACOTTA.defaultBlockState());
		SILVER_TERRACOTTA = NeoForgeMaterialData.ofBlockState(Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState());
		STONE = NeoForgeMaterialData.ofBlockState(Blocks.STONE.defaultBlockState());
		DEEPSLATE = NeoForgeMaterialData.ofBlockState(Blocks.DEEPSLATE.defaultBlockState());
		NETHERRACK = NeoForgeMaterialData.ofBlockState(Blocks.NETHERRACK.defaultBlockState());
		END_STONE = NeoForgeMaterialData.ofBlockState(Blocks.END_STONE.defaultBlockState());
		SAND = NeoForgeMaterialData.ofBlockState(Blocks.SAND.defaultBlockState());
		RED_SAND = NeoForgeMaterialData.ofBlockState(Blocks.RED_SAND.defaultBlockState());
		SANDSTONE = NeoForgeMaterialData.ofBlockState(Blocks.SANDSTONE.defaultBlockState());
		RED_SANDSTONE = NeoForgeMaterialData.ofBlockState(Blocks.RED_SANDSTONE.defaultBlockState());
		GRAVEL = NeoForgeMaterialData.ofBlockState(Blocks.GRAVEL.defaultBlockState());
		MOSSY_COBBLESTONE = NeoForgeMaterialData.ofBlockState(Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		SNOW = NeoForgeMaterialData.ofBlockState(Blocks.SNOW.defaultBlockState());
		SNOW_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.SNOW_BLOCK.defaultBlockState());
		TORCH = NeoForgeMaterialData.ofBlockState(Blocks.TORCH.defaultBlockState());
		BEDROCK = NeoForgeMaterialData.ofBlockState(Blocks.BEDROCK.defaultBlockState());
		MAGMA = NeoForgeMaterialData.ofBlockState(Blocks.MAGMA_BLOCK.defaultBlockState());
		ICE = NeoForgeMaterialData.ofBlockState(Blocks.ICE.defaultBlockState());
		PACKED_ICE = NeoForgeMaterialData.ofBlockState(Blocks.PACKED_ICE.defaultBlockState());
		BLUE_ICE = NeoForgeMaterialData.ofBlockState(Blocks.BLUE_ICE.defaultBlockState());
		FROSTED_ICE = NeoForgeMaterialData.ofBlockState(Blocks.FROSTED_ICE.defaultBlockState());
		GLOWSTONE = NeoForgeMaterialData.ofBlockState(Blocks.GLOWSTONE.defaultBlockState());
		MYCELIUM = NeoForgeMaterialData.ofBlockState(Blocks.MYCELIUM.defaultBlockState());
		STONE_SLAB = NeoForgeMaterialData.ofBlockState(Blocks.STONE_SLAB.defaultBlockState());

		// Liquids
		WATER = NeoForgeMaterialData.ofBlockState(Blocks.WATER.defaultBlockState());
		LAVA = NeoForgeMaterialData.ofBlockState(Blocks.LAVA.defaultBlockState());

		// Trees
		ACACIA_LOG = NeoForgeMaterialData.ofBlockState(Blocks.ACACIA_LOG.defaultBlockState());
		BIRCH_LOG = NeoForgeMaterialData.ofBlockState(Blocks.BIRCH_LOG.defaultBlockState());
		DARK_OAK_LOG = NeoForgeMaterialData.ofBlockState(Blocks.DARK_OAK_LOG.defaultBlockState());
		OAK_LOG = NeoForgeMaterialData.ofBlockState(Blocks.OAK_LOG.defaultBlockState());
		SPRUCE_LOG = NeoForgeMaterialData.ofBlockState(Blocks.SPRUCE_LOG.defaultBlockState());
		ACACIA_WOOD = NeoForgeMaterialData.ofBlockState(Blocks.ACACIA_WOOD.defaultBlockState());
		BIRCH_WOOD = NeoForgeMaterialData.ofBlockState(Blocks.BIRCH_WOOD.defaultBlockState());
		DARK_OAK_WOOD = NeoForgeMaterialData.ofBlockState(Blocks.DARK_OAK_WOOD.defaultBlockState());
		OAK_WOOD = NeoForgeMaterialData.ofBlockState(Blocks.OAK_WOOD.defaultBlockState());
		SPRUCE_WOOD = NeoForgeMaterialData.ofBlockState(Blocks.SPRUCE_WOOD.defaultBlockState());			
		STRIPPED_ACACIA_LOG = NeoForgeMaterialData.ofBlockState(Blocks.STRIPPED_ACACIA_LOG.defaultBlockState());
		STRIPPED_BIRCH_LOG = NeoForgeMaterialData.ofBlockState(Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
		STRIPPED_DARK_OAK_LOG = NeoForgeMaterialData.ofBlockState(Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
		STRIPPED_JUNGLE_LOG = NeoForgeMaterialData.ofBlockState(Blocks.STRIPPED_JUNGLE_LOG.defaultBlockState());
		STRIPPED_OAK_LOG = NeoForgeMaterialData.ofBlockState(Blocks.STRIPPED_OAK_LOG.defaultBlockState());
		STRIPPED_SPRUCE_LOG = NeoForgeMaterialData.ofBlockState(Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
		
		ACACIA_LEAVES = NeoForgeMaterialData.ofBlockState(Blocks.ACACIA_LEAVES.defaultBlockState());
		BIRCH_LEAVES = NeoForgeMaterialData.ofBlockState(Blocks.BIRCH_LEAVES.defaultBlockState());
		DARK_OAK_LEAVES = NeoForgeMaterialData.ofBlockState(Blocks.DARK_OAK_LEAVES.defaultBlockState());
		JUNGLE_LEAVES = NeoForgeMaterialData.ofBlockState(Blocks.JUNGLE_LEAVES.defaultBlockState());
		OAK_LEAVES = NeoForgeMaterialData.ofBlockState(Blocks.OAK_LEAVES.defaultBlockState());
		SPRUCE_LEAVES = NeoForgeMaterialData.ofBlockState(Blocks.SPRUCE_LEAVES.defaultBlockState());

		// Plants
		POPPY = NeoForgeMaterialData.ofBlockState(Blocks.POPPY.defaultBlockState());
		BLUE_ORCHID = NeoForgeMaterialData.ofBlockState(Blocks.BLUE_ORCHID.defaultBlockState());
		ALLIUM = NeoForgeMaterialData.ofBlockState(Blocks.ALLIUM.defaultBlockState());
		AZURE_BLUET = NeoForgeMaterialData.ofBlockState(Blocks.AZURE_BLUET.defaultBlockState());
		RED_TULIP = NeoForgeMaterialData.ofBlockState(Blocks.RED_TULIP.defaultBlockState());
		ORANGE_TULIP = NeoForgeMaterialData.ofBlockState(Blocks.ORANGE_TULIP.defaultBlockState());
		WHITE_TULIP = NeoForgeMaterialData.ofBlockState(Blocks.WHITE_TULIP.defaultBlockState());
		PINK_TULIP = NeoForgeMaterialData.ofBlockState(Blocks.PINK_TULIP.defaultBlockState());
		OXEYE_DAISY = NeoForgeMaterialData.ofBlockState(Blocks.OXEYE_DAISY.defaultBlockState());		
		YELLOW_FLOWER = NeoForgeMaterialData.ofBlockState(Blocks.DANDELION.defaultBlockState());
		DEAD_BUSH = NeoForgeMaterialData.ofBlockState(Blocks.DEAD_BUSH.defaultBlockState());
		FERN = NeoForgeMaterialData.ofBlockState(Blocks.FERN.defaultBlockState());
		LONG_GRASS = NeoForgeMaterialData.ofBlockState(Blocks.SHORT_GRASS.defaultBlockState());
		
		RED_MUSHROOM_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.RED_MUSHROOM_BLOCK.defaultBlockState());
		BROWN_MUSHROOM_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState());		
		RED_MUSHROOM = NeoForgeMaterialData.ofBlockState(Blocks.RED_MUSHROOM.defaultBlockState());
		BROWN_MUSHROOM = NeoForgeMaterialData.ofBlockState(Blocks.BROWN_MUSHROOM.defaultBlockState());

		DOUBLE_TALL_GRASS_LOWER = NeoForgeMaterialData.ofBlockState(Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		DOUBLE_TALL_GRASS_UPPER = NeoForgeMaterialData.ofBlockState(Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		LARGE_FERN_LOWER = NeoForgeMaterialData.ofBlockState(Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		LARGE_FERN_UPPER = NeoForgeMaterialData.ofBlockState(Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));		
		LILAC_LOWER = NeoForgeMaterialData.ofBlockState(Blocks.LILAC.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		LILAC_UPPER = NeoForgeMaterialData.ofBlockState(Blocks.LILAC.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));		
		PEONY_LOWER = NeoForgeMaterialData.ofBlockState(Blocks.PEONY.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		PEONY_UPPER = NeoForgeMaterialData.ofBlockState(Blocks.PEONY.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));		
		ROSE_BUSH_LOWER = NeoForgeMaterialData.ofBlockState(Blocks.ROSE_BUSH.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		ROSE_BUSH_UPPER = NeoForgeMaterialData.ofBlockState(Blocks.ROSE_BUSH.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));	
		SUNFLOWER_LOWER = NeoForgeMaterialData.ofBlockState(Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		SUNFLOWER_UPPER = NeoForgeMaterialData.ofBlockState(Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));

		ACACIA_SAPLING = NeoForgeMaterialData.ofBlockState(Blocks.ACACIA_SAPLING.defaultBlockState());
		BAMBOO_SAPLING = NeoForgeMaterialData.ofBlockState(Blocks.BAMBOO_SAPLING.defaultBlockState());
		BIRCH_SAPLING = NeoForgeMaterialData.ofBlockState(Blocks.BIRCH_SAPLING.defaultBlockState());
		DARK_OAK_SAPLING = NeoForgeMaterialData.ofBlockState(Blocks.DARK_OAK_SAPLING.defaultBlockState());
		JUNGLE_SAPLING = NeoForgeMaterialData.ofBlockState(Blocks.JUNGLE_SAPLING.defaultBlockState());
		OAK_SAPLING = NeoForgeMaterialData.ofBlockState(Blocks.OAK_SAPLING.defaultBlockState());
		SPRUCE_SAPLING = NeoForgeMaterialData.ofBlockState(Blocks.SPRUCE_SAPLING.defaultBlockState());
		
		PUMPKIN = NeoForgeMaterialData.ofBlockState(Blocks.PUMPKIN.defaultBlockState());
		CACTUS = NeoForgeMaterialData.ofBlockState(Blocks.CACTUS.defaultBlockState());
		MELON_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.MELON.defaultBlockState());
		VINE = NeoForgeMaterialData.ofBlockState(Blocks.VINE.defaultBlockState());
		WATER_LILY = NeoForgeMaterialData.ofBlockState(Blocks.LILY_PAD.defaultBlockState());
		SUGAR_CANE_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.SUGAR_CANE.defaultBlockState());
		
		BlockState bambooState = Blocks.BAMBOO.defaultBlockState().setValue(BambooStalkBlock.AGE, 1).setValue(BambooStalkBlock.LEAVES, BambooLeaves.NONE).setValue(BambooStalkBlock.STAGE, 0);
		BAMBOO = NeoForgeMaterialData.ofBlockState(bambooState);
		BAMBOO_SMALL = NeoForgeMaterialData.ofBlockState(bambooState.setValue(BambooStalkBlock.LEAVES, BambooLeaves.SMALL));
		BAMBOO_LARGE = NeoForgeMaterialData.ofBlockState(bambooState.setValue(BambooStalkBlock.LEAVES, BambooLeaves.LARGE));
		BAMBOO_LARGE_GROWING = NeoForgeMaterialData.ofBlockState(bambooState.setValue(BambooStalkBlock.LEAVES, BambooLeaves.LARGE).setValue(BambooStalkBlock.STAGE, 1));
		PODZOL = NeoForgeMaterialData.ofBlockState(Blocks.PODZOL.defaultBlockState());
		SEAGRASS = NeoForgeMaterialData.ofBlockState(Blocks.SEAGRASS.defaultBlockState());
		TALL_SEAGRASS_LOWER = NeoForgeMaterialData.ofBlockState(Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.LOWER));
		TALL_SEAGRASS_UPPER = NeoForgeMaterialData.ofBlockState(Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER));
		KELP = NeoForgeMaterialData.ofBlockState(Blocks.KELP.defaultBlockState());
		KELP_PLANT = NeoForgeMaterialData.ofBlockState(Blocks.KELP_PLANT.defaultBlockState());
		VINE_SOUTH = NeoForgeMaterialData.ofBlockState(Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true));
		VINE_NORTH = NeoForgeMaterialData.ofBlockState(Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true));
		VINE_WEST = NeoForgeMaterialData.ofBlockState(Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true));
		VINE_EAST = NeoForgeMaterialData.ofBlockState(Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true));
		SEA_PICKLE = NeoForgeMaterialData.ofBlockState(Blocks.SEA_PICKLE.defaultBlockState());

		// Ores
		COAL_ORE = NeoForgeMaterialData.ofBlockState(Blocks.COAL_ORE.defaultBlockState());
		DIAMOND_ORE = NeoForgeMaterialData.ofBlockState(Blocks.DIAMOND_ORE.defaultBlockState());
		EMERALD_ORE = NeoForgeMaterialData.ofBlockState(Blocks.EMERALD_ORE.defaultBlockState());
		GOLD_ORE = NeoForgeMaterialData.ofBlockState(Blocks.GOLD_ORE.defaultBlockState());
		IRON_ORE = NeoForgeMaterialData.ofBlockState(Blocks.IRON_ORE.defaultBlockState());
		LAPIS_ORE = NeoForgeMaterialData.ofBlockState(Blocks.LAPIS_ORE.defaultBlockState());
		QUARTZ_ORE = NeoForgeMaterialData.ofBlockState(Blocks.NETHER_QUARTZ_ORE.defaultBlockState());
		REDSTONE_ORE = NeoForgeMaterialData.ofBlockState(Blocks.REDSTONE_ORE.defaultBlockState());

		// Ore blocks
		GOLD_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.GOLD_BLOCK.defaultBlockState());
		IRON_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.IRON_BLOCK.defaultBlockState());
		REDSTONE_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.REDSTONE_BLOCK.defaultBlockState());
		DIAMOND_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.DIAMOND_BLOCK.defaultBlockState());
		LAPIS_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.LAPIS_BLOCK.defaultBlockState());
		COAL_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.COAL_BLOCK.defaultBlockState());
		QUARTZ_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.QUARTZ_BLOCK.defaultBlockState());
		EMERALD_BLOCK = NeoForgeMaterialData.ofBlockState(Blocks.EMERALD_BLOCK.defaultBlockState());

		BERRY_BUSH = NeoForgeMaterialData.ofBlockState(Blocks.SWEET_BERRY_BUSH.defaultBlockState());
	}
}
