package com.pg85.otg.neoforge.gen;

import java.util.*;

import com.pg85.otg.neoforge.biome.NeoForgeBiome;
import com.pg85.otg.shared.materials.SharedMaterialData;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.interfaces.ICachedBiomeProvider;
import com.pg85.otg.util.BlockPos2D;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.ThreadSafeLRUCache;
import com.pg85.otg.util.gen.JigsawStructureData;
import com.pg85.otg.util.gen.OTGWorldInfo;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.LocalMaterials;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.EndCityStructure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure;
import org.jetbrains.annotations.Nullable;

/**
 * On-demand shadow chunk generation for BO4 custom objects and /otg mapterrain.
 * Generates base terrain for chunks without using MC's world generation flow.
 * Used when BO4 decoration needs material/height data from unloaded chunks,
 * and for the /otg mapterrain command.
 *
 * <p>Note: As of the async fillFromNoise refactor, this class is NO LONGER
 * used for pre-generating chunks ahead of vanilla's pipeline. Chunk generation
 * is now async via CompletableFuture.supplyAsync in OTGNeoForgeChunkGenerator.
 */
public class ShadowChunkGenerator {
    // Dummy placement for checkStructurePresence calls -- always passes placement checks.
    private static final StructurePlacement DUMMY_PLACEMENT =
            new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 0);

    private final ThreadSafeLRUCache<BlockPos2D, LocalMaterialData[]> unloadedBlockColumnsCache;
    private final ThreadSafeLRUCache<ChunkCoordinate, ChunkAccess> unloadedChunksCache;
    private final ThreadSafeLRUCache<ChunkCoordinate, Integer> hasVanillaStructureChunkCache = new ThreadSafeLRUCache<>(4096);
    private final ThreadSafeLRUCache<ChunkCoordinate, Integer> hasVanillaNoiseStructureChunkCache = new ThreadSafeLRUCache<>(4096);

    // Pre-computed lookup: which noise-affecting structures can spawn in each biome?
    // Built lazily on first use, then reused for all subsequent checks.
    private volatile Map<Holder<Biome>, List<Structure>> biomeToNoiseStructures;
    private volatile Map<Holder<Biome>, List<Structure>> biomeToAllStructures;

    public ShadowChunkGenerator() {
        this.unloadedChunksCache = new ThreadSafeLRUCache<>(2048);
        this.unloadedBlockColumnsCache = new ThreadSafeLRUCache<>(2048);
    }

    private NeoForgeChunkBuffer getUnloadedChunk(
            ServerLevel serverLevel,
            OTGNeoForgeChunkGenerator otgChunkGenerator, OTGWorldInfo otgWorldInfo,
            ChunkCoordinate chunkCoordinate
    ) {
        Registry<Biome> biomeRegistry = getRegistry(serverLevel.registryAccess(), Registries.BIOME);

        if (biomeRegistry == null) {
            throw new RuntimeException("Could not get biome registry when loading chunk");
        }

        ProtoChunk chunk = new ProtoChunk(
                new ChunkPos(chunkCoordinate.getChunkX(), chunkCoordinate.getChunkZ()),
                UpgradeData.EMPTY,
                serverLevel,
                biomeRegistry,
                null
        );

        NeoForgeChunkBuffer buffer = new NeoForgeChunkBuffer(chunk);

        // This is where vanilla processes any noise affecting structures like villages, in order to spawn smoothing areas.
        // Doing this for unloaded chunks causes a hang on load since getChunk is called by StructureManager.
        // BO4's/shadowgen avoid villages, so this method should never be called to fetch unloaded chunks that contain villages,
        // so we can skip noisegen affecting structures here.

        ObjectList<JigsawStructureData> structures = new ObjectArrayList<>(10);
        Random random = otgChunkGenerator.getRandomFromChunkCoord(chunkCoordinate);
        otgChunkGenerator.getInternalGenerator().populateNoise(otgWorldInfo, buffer, buffer.getChunkCoordinate(), structures, random);
        return buffer;
    }

    // Vanilla structure detection (avoidance)
    // Some vanilla structures use density based smoothing of terrain underneath, which is factored into noisegen.
    // Unfortunately this requires fetching structure data in a non-thread-safe manner, so we can't do async
    // chunkgen (base terrain) for these chunks and have to avoid them.

    public boolean checkHasVanillaStructureWithoutLoading(
            ServerLevel serverWorld, ChunkCoordinate chunkCoordinate,
            ICachedBiomeProvider cachedBiomeProvider, boolean noiseAffectingOnly
    ) {
        StructureManager manager = serverWorld.structureManager();

        RegistryAccess registryAccess = manager.registryAccess();

        Registry<Structure> structureRegistry = getRegistry(registryAccess, Registries.STRUCTURE);
        if (structureRegistry == null) {
            OTGLog.error("Failed to get structure registry during shadow chunk gen");
            return false;
        }

        Registry<Biome> biomeRegistry = getRegistry(registryAccess, Registries.BIOME);
        if (biomeRegistry == null) {
            OTGLog.error("Failed to get structure registry during shadow chunk gen");
            return false;
        }

        // Since we can't check for structure components/references, only structure starts,
        // we'll keep a safe distance away from any vanilla structure start points.
        int radiusInChunks = 5;
        ChunkPos chunkpos;
        IBiome biome;
        if (!serverWorld.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return false;
        }

        List<ChunkCoordinate> chunksToHandle = new ArrayList<>();
        Map<ChunkCoordinate, Integer> chunksHandled = new HashMap<>();
        // Cache is now thread-safe, no need for external synchronization
        if (noiseAffectingOnly) {
            if (checkHasVanillaStructureWithoutLoadingCache(this.hasVanillaNoiseStructureChunkCache,
                                                            chunkCoordinate, radiusInChunks, chunksToHandle
            )) {
                return true;
            }
        } else {
            if (checkHasVanillaStructureWithoutLoadingCache(this.hasVanillaStructureChunkCache, chunkCoordinate,
                                                            radiusInChunks, chunksToHandle
            )) {
                return true;
            }
        }

        Map<Holder<Biome>, List<Structure>> structuresByBiome =
                getStructuresForBiome(structureRegistry, noiseAffectingOnly);

        for (ChunkCoordinate chunkToHandle : chunksToHandle) {
            chunkpos = new ChunkPos(chunkToHandle.getChunkX(), chunkToHandle.getChunkZ());

            int distanceFromQuery = (int) Math.sqrt(
                    (chunkToHandle.getChunkX() - chunkCoordinate.getChunkX()) *
                    (chunkToHandle.getChunkX() - chunkCoordinate.getChunkX()) +
                    (chunkToHandle.getChunkZ() - chunkCoordinate.getChunkZ()) *
                    (chunkToHandle.getChunkZ() - chunkCoordinate.getChunkZ()));

            biome = cachedBiomeProvider.getNoiseBiome((chunkpos.x << 2) + 2, (chunkpos.z << 2) + 2);
            Biome regBiome = ((NeoForgeBiome) biome).getBiome();
            Holder<Biome> biomeHolder = biomeRegistry.wrapAsHolder(regBiome);

            // Fast path: if this biome has no noise-affecting structures, skip entirely.
            List<Structure> candidateStructures = structuresByBiome.get(biomeHolder);
            if (candidateStructures == null || candidateStructures.isEmpty()) {
                chunksHandled.putIfAbsent(chunkToHandle, 0);
                continue;
            }

            // Only check structures that can actually spawn in this biome
            for (Structure structure : candidateStructures) {
                int radius = 1;
                if (structure instanceof JigsawStructure ||
                        structure instanceof EndCityStructure ||
                        structure instanceof OceanMonumentStructure ||
                        structure instanceof WoodlandMansionStructure) {
                    radius = 4;
                }

                for (int searchRadius = radiusInChunks; searchRadius > 0; searchRadius--) {
                    if (searchRadius == radius) {
                        if (hasStructureStart(structure, manager, chunkpos, noiseAffectingOnly)) {
                            chunksHandled.put(chunkToHandle, searchRadius);
                            if (searchRadius >= distanceFromQuery) {
                                if (noiseAffectingOnly) {
                                    this.hasVanillaNoiseStructureChunkCache.putAll(chunksHandled);
                                } else {
                                    this.hasVanillaStructureChunkCache.putAll(chunksHandled);
                                }
                                return true;
                            }
                        }
                        break;
                    }
                }
            }
            chunksHandled.putIfAbsent(chunkToHandle, 0);
        }
        if (noiseAffectingOnly) {
            this.hasVanillaNoiseStructureChunkCache.putAll(chunksHandled);
        } else {
            this.hasVanillaStructureChunkCache.putAll(chunksHandled);
        }
        return false;
    }

    private static @Nullable <T> Registry<T> getRegistry(RegistryAccess registryAccess, ResourceKey<Registry<T>> resourceKey) {
        Optional<Registry<T>> ops = registryAccess.registry(resourceKey);

        if (ops.isEmpty()) {
            OTGLog.error("Could not get structure registry during shadow chunk gen");
            return null;
        }

        return ops.get();
    }

    private Map<Holder<Biome>, List<Structure>> getStructuresForBiome(
            Registry<Structure> structureRegistry, boolean noiseAffectingOnly
    ) {
        Map<Holder<Biome>, List<Structure>> map = noiseAffectingOnly
                ? this.biomeToNoiseStructures : this.biomeToAllStructures;
        if (map != null) return map;

        synchronized (this) {
            map = noiseAffectingOnly ? this.biomeToNoiseStructures : this.biomeToAllStructures;
            if (map != null) return map;

            map = new HashMap<>();
            for (ResourceLocation resourceLocation : structureRegistry.keySet()) {
                Structure structure = structureRegistry.get(resourceLocation);
                if (structure == null) continue;
                if (structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) continue;

                TerrainAdjustment terrainAdjustment = structure.terrainAdaptation();
                if (noiseAffectingOnly &&
                    (terrainAdjustment == TerrainAdjustment.NONE || terrainAdjustment == TerrainAdjustment.BURY)) {
                    continue;
                }

                for (Holder<Biome> biomeHolder : structure.biomes()) {
                    map.computeIfAbsent(biomeHolder, k -> new ArrayList<>()).add(structure);
                }
            }
            map.replaceAll((k, v) -> List.copyOf(v));
            map = Map.copyOf(map);

            if (noiseAffectingOnly) {
                this.biomeToNoiseStructures = map;
            } else {
                this.biomeToAllStructures = map;
            }
            return map;
        }
    }

    private boolean checkHasVanillaStructureWithoutLoadingCache(
            ThreadSafeLRUCache<ChunkCoordinate, Integer> cache, ChunkCoordinate chunkCoordinate, int radiusInChunks,
            List<ChunkCoordinate> chunksToHandle
    ) {
        for (int cycle = 0; cycle < radiusInChunks; ++cycle) {
            for (int xOffset = -cycle; xOffset <= cycle; ++xOffset) {
                for (int zOffset = -cycle; zOffset <= cycle; ++zOffset) {
                    int distance = (int) Math.sqrt(xOffset * xOffset + zOffset * zOffset);
                    if (distance == cycle) {
                        ChunkCoordinate searchChunk = ChunkCoordinate.fromChunkCoords(
                                chunkCoordinate.getChunkX() + xOffset, chunkCoordinate.getChunkZ() + zOffset);
                        Integer result = cache.get(searchChunk);
                        if (result != null) {
                            if (result > 0 && result >= distance) {
                                return true;
                            }
                        } else {
                            chunksToHandle.add(searchChunk);
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasStructureStart(
            Structure structure, StructureManager manager, ChunkPos chunkPos, boolean noiseAffectingOnly
    ) {
        TerrainAdjustment terrainAdjustment = structure.terrainAdaptation();

        if (noiseAffectingOnly && (terrainAdjustment == TerrainAdjustment.NONE || terrainAdjustment == TerrainAdjustment.BURY)) {
            return false;
        }

        if (structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
            return false;
        }

        return switch (manager.checkStructurePresence(chunkPos, structure, DUMMY_PLACEMENT, false)) {
            case START_PRESENT -> true;
            case START_NOT_PRESENT -> false;
            case CHUNK_LOAD_NEEDED -> {
                OTGLog.info("Uncertain structure status, trying anyway");
                yield false;
            }
        };
    }

    // /otg mapterrain

    // /otg mapterrain fetches chunks in order to create a map of base terrain, without touching any of the caches or
    // resources used for worldgen or bo4 shadowgen, since the chunks aren't actually supposed to generate in the world.
    // We won't get any density based smoothing applied to noisegen for vanilla structures, but that's ok for /otg mapterrain.

    public NeoForgeChunkBuffer getChunkWithoutLoadingOrCaching(
            ServerLevel serverLevel,
            OTGNeoForgeChunkGenerator otgChunkGenerator, OTGWorldInfo otgWorldInfo, Random random,
            ChunkCoordinate chunkCoordinate
    ) {
        return getUnloadedChunk(serverLevel, otgChunkGenerator, otgWorldInfo, chunkCoordinate);
    }

    // BO4's / Smoothing Areas

    // BO4's and smoothing areas may do material and height checks in unloaded chunks during decoration.
    // Shadowgen is used to do this without causing cascades. Shadowgenned chunks are requested on-demand
    // for the worldgen thread. BO4's are always processed on the worldgen thread.

    private LocalMaterialData[] getBlockColumnInUnloadedChunk(
            ServerLevel serverLevel,
            OTGNeoForgeChunkGenerator otgChunkGenerator, OTGWorldInfo otgWorldInfo, int x, int z
    ) {
        BlockPos2D blockPos = new BlockPos2D(x, z);
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromBlockCoords(x, z);

        byte blockX = (byte) (x &= 0xF);
        byte blockZ = (byte) (z &= 0xF);

        LocalMaterialData[] cachedColumn = this.unloadedBlockColumnsCache.get(blockPos);
        if (cachedColumn != null) {
            return cachedColumn;
        }

        // Check chunk cache first, generate if miss
        ChunkAccess chunk = this.unloadedChunksCache.get(chunkCoord);
        if (chunk == null) {
            chunk = getUnloadedChunk(serverLevel, otgChunkGenerator, otgWorldInfo, chunkCoord).getChunkAccess();
            this.unloadedChunksCache.put(chunkCoord, chunk);
        }

        LocalMaterialData[] blocksInColumn = new LocalMaterialData[256];
        BlockState blockInChunk;
        for (short y = 0; y < 256; y++) {
            blockInChunk = chunk.getBlockState(new BlockPos(blockX, y, blockZ));
            if (blockInChunk != null) {
                blocksInColumn[y] = SharedMaterialData.ofBlockState(blockInChunk);
            } else {
                break;
            }
        }
        this.unloadedBlockColumnsCache.put(blockPos, blocksInColumn);

        return blocksInColumn;
    }

    public LocalMaterialData getMaterialInUnloadedChunk(
            ServerLevel serverLevel,
            OTGNeoForgeChunkGenerator otgChunkGenerator, OTGWorldInfo otgWorldInfo, int x, int y, int z
    ) {
        LocalMaterialData[] blockColumn = getBlockColumnInUnloadedChunk(
                serverLevel, otgChunkGenerator, otgWorldInfo, x, z
        );
        return blockColumn[y];
    }

    public int getHighestBlockYInUnloadedChunk(
            ServerLevel serverLevel,
            OTGNeoForgeChunkGenerator otgChunkGenerator, OTGWorldInfo otgWorldInfo, int x, int z,
            boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow
    ) {
        int height = otgWorldInfo.minY() - 1;

        LocalMaterialData[] blockColumn = getBlockColumnInUnloadedChunk(serverLevel, otgChunkGenerator, otgWorldInfo, x,
                                                                        z
        );
        SharedMaterialData material;
        boolean isLiquid;
        boolean isSolid;

        for (int y = 255; y >= 0; y--) {
            material = (SharedMaterialData) blockColumn[y];
            isLiquid = material.isLiquid();
            isSolid = material.isSolid() || (!ignoreSnow && material.isMaterial(LocalMaterials.SNOW));
            if (!(isLiquid && ignoreLiquid)) {
                if ((findSolid && isSolid) || (findLiquid && isLiquid)) {
                    return y;
                }
                if ((findSolid && isLiquid) || (findLiquid && isSolid)) {
                    return -1;
                }
            }
        }
        return height;
    }

}

