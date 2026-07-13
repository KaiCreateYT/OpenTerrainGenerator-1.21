package com.pg85.otg.shared.gen;

import com.pg85.otg.OTG;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.constants.settings.structure.CustomStructureType;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.gen.OTGChunkDecorator;
import com.pg85.otg.gen.OTGChunkGenerator;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.shared.biome.IOTGBiomeProvider;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.gen.ChunkBuffer;
import com.pg85.otg.util.gen.JigsawStructureData;
import com.pg85.otg.util.gen.OTGWorldInfo;
import com.pg85.otg.util.helpers.MathHelper;
import com.pg85.otg.util.materials.LocalMaterialData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

@Getter
public abstract class SharedOTGChunkGenerator extends ChunkGenerator {

    // --- Abstract methods for platform-specific operations ---

    protected abstract SharedWorldGenRegion createWorldGenRegion(
            String presetFolderName, WorldGenLevel worldGenLevel, ChunkAccess chunkAccess);

    protected abstract ChunkBuffer createChunkBuffer(ChunkAccess chunkAccess);

    protected abstract BiomeGenerationSettings getBiomeGenerationSettings(IBiome biome);

    public abstract Boolean checkHasVanillaStructureWithoutLoading(ServerLevel level, ChunkCoordinate chunkCoord);

    public abstract int getHighestBlockYInUnloadedChunk(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow);

    public abstract LocalMaterialData getMaterialInUnloadedChunk(int x, int y, int z);

    // --- Fields ---

    protected final Holder<NoiseGeneratorSettings> settings;
    protected final IOTGBiomeProvider otgBiomeProvider;
    protected final OTGChunkGenerator internalGenerator;
    protected final DimensionPreset preset;
    protected Registry<Biome> biomeRegistry;
    protected final NoiseBasedChunkGenerator horribleDelegateForCarvers;
    protected Aquifer.FluidPicker globalFluidPicker = null;
    protected final OTGChunkDecorator chunkDecorator;
    protected CustomStructureCache structureCache = null;
    protected Long seed = 0L;
    protected ServerLevel serverLevel = null;
    protected final OTGWorldInfo otgWorldInfo;

    // --- Timing counters (thread-safe) ---
    private static final int TIMING_LOG_INTERVAL = 50;
    private final AtomicInteger fillNoiseCount = new AtomicInteger();
    private final AtomicLong fillNoiseTotalNs = new AtomicLong();
    private final AtomicLong populateNoiseTotalNs = new AtomicLong();
    private final AtomicInteger carversCount = new AtomicInteger();
    private final AtomicLong carversTotalNs = new AtomicLong();
    private final AtomicInteger structuresCount = new AtomicInteger();
    private final AtomicLong structuresTotalNs = new AtomicLong();
    private final AtomicInteger decorationCount = new AtomicInteger();
    private final AtomicLong decorationTotalNs = new AtomicLong();
    private final AtomicLong superDecorationTotalNs = new AtomicLong();

    // --- Constructor ---

    protected SharedOTGChunkGenerator(
            IOTGBiomeProvider otgBiomeProvider,
            Holder<NoiseGeneratorSettings> settings,
            Registry<Biome> biomeRegistry
    ) {
        super((net.minecraft.world.level.biome.BiomeSource) otgBiomeProvider);
        this.otgBiomeProvider = otgBiomeProvider;
        this.settings = settings;
        int minY = settings.value().noiseSettings().minY();
        int maxY = settings.value().noiseSettings().height() + minY - 1;
        this.otgWorldInfo = new OTGWorldInfo(minY, maxY);
        this.internalGenerator = new OTGChunkGenerator(
                OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByFolderName(otgBiomeProvider.getPresetFolderName()),
                (com.pg85.otg.interfaces.ILayerSource) otgBiomeProvider,
                OTG.getEngine().getDimensionPresetLoader().getGlobalIdMapping(otgBiomeProvider.getPresetFolderName()),
                otgWorldInfo
        );
        this.preset = OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByFolderName(otgBiomeProvider.getPresetFolderName());
        this.biomeRegistry = biomeRegistry;
        this.horribleDelegateForCarvers = new NoiseBasedChunkGenerator(
                (net.minecraft.world.level.biome.BiomeSource) otgBiomeProvider, settings);
        this.chunkDecorator = new OTGChunkDecorator();
        this.globalFluidPicker = createFluidPicker(settings.value());
    }

    // --- Seed management ---

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        if (this.seed == 0L) {
            this.setSeed(BiomeManager.obfuscateSeed(seed));
        }
        return super.createState(structureSetLookup, randomState, seed);
    }

    public void setSeed(Long seed) {
        synchronized (this) {
            if (this.seed == 0L) {
                this.seed = seed;
                otgBiomeProvider.setSeed(seed);
                internalGenerator.setSeed(seed);
            }
        }
    }

    // --- Server level ---

    public void setServerLevel(ServerLevel serverLevel) {
        synchronized (this) {
            if (this.serverLevel == null) {
                this.serverLevel = serverLevel;
                if (this.biomeRegistry == null) {
                    this.biomeRegistry = serverLevel.registryAccess().lookupOrThrow(Registries.BIOME);
                }
                otgBiomeProvider.setSurfaceHeightEstimator((worldX, worldZ) ->
                    this.getHighestBlockYInUnloadedChunk(worldX, worldZ, true, true, true, true)
                );
            }
        }
    }

    // --- Biome decoration ---

    @Override
    public void applyBiomeDecoration(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess, StructureManager structureManager) {
        long t0 = System.nanoTime();
        if (!OTG.getEngine().getPluginConfig().getDecorationEnabled()) {
            return;
        }
        ChunkCoordinate chunkBeingDecorated = getChunkCoordinate(worldGenLevel, chunkAccess);
        SharedWorldGenRegion worldGenRegion = createWorldGenRegion(
                this.preset.getFolderName(), worldGenLevel, chunkAccess);
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome(
                (chunkAccess.getPos().x << 2) + 2, (chunkAccess.getPos().z << 2) + 2);

        Path worldSaveFolder = worldGenLevel.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).getParent();

        this.chunkDecorator.decorate(chunkBeingDecorated, worldGenRegion, biome.getBiomeSettings(), getStructureCache(worldSaveFolder));
        long tSuper = System.nanoTime();
        super.applyBiomeDecoration(worldGenLevel, chunkAccess, structureManager);
        long superElapsed = System.nanoTime() - tSuper;
        superDecorationTotalNs.addAndGet(superElapsed);

        if (!biome.getBiomeSettings().getIdentitySettings().isTemplateForBiome()) {
            this.chunkDecorator.doSnowAndIce(worldGenRegion, chunkBeingDecorated);
        }
        long totalElapsed = System.nanoTime() - t0;
        decorationTotalNs.addAndGet(totalElapsed);
        int count = decorationCount.incrementAndGet();
        if (count % TIMING_LOG_INTERVAL == 0) {
            OTGLog.info(LogCategory.PERFORMANCE, "decoration #{} avg={}ms (superDecorate avg={}ms)", count,
                    String.format("%.1f", decorationTotalNs.get() / 1_000_000.0 / count),
                    String.format("%.1f", superDecorationTotalNs.get() / 1_000_000.0 / count));
        }
    }

    // --- Structure cache ---

    public void saveStructureCache() {
        if (this.chunkDecorator.getIsSaveRequired() && this.structureCache != null) {
            this.structureCache.saveToDisk(chunkDecorator);
        }
    }

    public CustomStructureCache getStructureCache(Path worldSaveFolder) {
        if (this.structureCache == null) {
            this.structureCache = OTG.getEngine().createCustomStructureCache(
                    this.preset.getFolderName(),
                    worldSaveFolder,
                    this.seed,
                    CustomStructureType.BO4 == this.preset.getConfig().getResourceSettings().getCustomStructureType());
        }
        return this.structureCache;
    }

    // --- Structure generation ---

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState chunkGeneratorStructureState,
            StructureManager structureManager,
            ChunkAccess chunkAccess,
            StructureTemplateManager structureTemplateManager,
            net.minecraft.resources.ResourceKey<Level> levelKey
    ) {
        long t0 = System.nanoTime();
        super.createStructures(registryAccess, chunkGeneratorStructureState, structureManager, chunkAccess, structureTemplateManager, levelKey);
        long elapsed = System.nanoTime() - t0;
        structuresTotalNs.addAndGet(elapsed);
        int count = structuresCount.incrementAndGet();
        if (count % TIMING_LOG_INTERVAL == 0) {
            OTGLog.info(LogCategory.PERFORMANCE, "createStructures #{} avg={}ms", count,
                    String.format("%.1f", structuresTotalNs.get() / 1_000_000.0 / count));
        }
    }

    private static ChunkCoordinate getChunkCoordinate(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess) {
        int worldX = chunkAccess.getPos().x * Constants.CHUNK_SIZE;
        int worldZ = chunkAccess.getPos().z * Constants.CHUNK_SIZE;

        WorldgenRandom worldgenRandom = new WorldgenRandom(worldGenLevel.getRandom());
        worldgenRandom.setDecorationSeed(worldGenLevel.getSeed(), worldX, worldZ);

        return ChunkCoordinate.fromBlockCoords(worldX, worldZ);
    }

    // --- Fluid picker ---

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings noiseGeneratorSettings) {
        Aquifer.FluidStatus fluidStatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int i = noiseGeneratorSettings.seaLevel();
        Aquifer.FluidStatus fluidStatus2 = new Aquifer.FluidStatus(i, noiseGeneratorSettings.defaultFluid());
        return (j, k, l) -> {
            if (k < Math.min(-54, i)) {
                return fluidStatus;
            }
            return fluidStatus2;
        };
    }

    // --- Carvers ---

    @Override
    public void applyCarvers(WorldGenRegion worldGenRegion, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess) {
        long t0 = System.nanoTime();
        if (this.preset.getConfig().getCarverSettings().isUseModernCaves()) {
            this.horribleDelegateForCarvers.applyCarvers(worldGenRegion, seed, randomState, biomeManager, structureManager, chunkAccess);
            long elapsed = System.nanoTime() - t0;
            carversTotalNs.addAndGet(elapsed);
            int count = carversCount.incrementAndGet();
            if (count % TIMING_LOG_INTERVAL == 0) {
                OTGLog.info(LogCategory.PERFORMANCE, "applyCarvers(modern) #{} avg={}ms", count,
                        String.format("%.1f", carversTotalNs.get() / 1_000_000.0 / count));
            }
            return;
        }

        handleOTGCarvers(seed, chunkAccess);

        List<String> defaultCavesAndRavines = Arrays.asList("minecraft:cave", "minecraft:underwater_cave", "minecraft:nether_cave", "minecraft:canyon", "minecraft:underwater_canyon");

        BiomeManager biomeManager2 = biomeManager.withDifferentSource((i, j, k) -> this.getBiomeSource().getNoiseBiome(i, j, k, randomState.sampler()));
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        int i2 = 8;
        ChunkPos chunkPos = chunkAccess.getPos();
        NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(chunkAccess2 -> this.createNoiseChunk(chunkAccess2, structureManager, Blender.of(worldGenRegion), randomState));
        CarvingMask carvingMask = ((ProtoChunk) chunkAccess).getOrCreateCarvingMask();
        Aquifer aquifer = noiseChunk.aquifer();
        CarvingContext carvingContext = new CarvingContext(this.horribleDelegateForCarvers, worldGenRegion.registryAccess(), chunkAccess.getHeightAccessorForGeneration(), noiseChunk, randomState, this.settings.value().surfaceRule());
        for (int j2 = -8; j2 <= 8; ++j2) {
            for (int k2 = -8; k2 <= 8; ++k2) {
                ChunkPos chunkPos2 = new ChunkPos(chunkPos.x + j2, chunkPos.z + k2);
                ChunkAccess chunkAccess22 = worldGenRegion.getChunk(chunkPos2.x, chunkPos2.z);
                BiomeGenerationSettings biomeGenerationSettings = chunkAccess22.carverBiome(() -> this.getBiomeGenerationSettings(this.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(chunkPos2.getMinBlockX()), 0, QuartPos.fromBlock(chunkPos2.getMinBlockZ()), randomState.sampler())));
                Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomeGenerationSettings.getCarvers();
                int m = 0;
                for (Holder<ConfiguredWorldCarver<?>> carver : iterable) {
                    if (defaultCavesAndRavines.stream().noneMatch(
                            b -> b.equalsIgnoreCase(carver.unwrapKey().map(Objects::toString).orElse(""))
                    ) && carver.isBound())
                    {
                        ConfiguredWorldCarver<?> configuredWorldCarver = carver.value();
                        worldgenRandom.setLargeFeatureSeed(seed + (long) m, chunkPos2.x, chunkPos2.z);
                        if (configuredWorldCarver.isStartChunk(worldgenRandom)) {
                            configuredWorldCarver.carve(carvingContext, chunkAccess, biomeManager2::getBiome, worldgenRandom, aquifer, chunkPos2, carvingMask);
                        }
                        ++m;
                    }
                }
            }
        }
    }

    private void handleOTGCarvers(long seed, ChunkAccess chunkAccess) {
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome(chunkAccess.getPos().x << 2, chunkAccess.getPos().z << 2);
        BiomeGenerationSettings biomegenerationsettings = getBiomeGenerationSettings(biome);
        Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomegenerationsettings.getCarvers();

        List<String> defaultCaves = Arrays.asList("minecraft:cave", "minecraft:underwater_cave", "minecraft:nether_cave");
        boolean cavesEnabled = this.preset.getConfig().getCarverSettings().isCavesEnabled();
        if (cavesEnabled) {
            for (Holder<ConfiguredWorldCarver<?>> carver : iterable) {
                if (defaultCaves.stream().noneMatch(
                        b -> b.equalsIgnoreCase(carver.unwrapKey().map(Objects::toString).orElse(""))
                )) {
                    cavesEnabled = false;
                    break;
                }
            }
        }

        List<String> defaultRavines = Arrays.asList("minecraft:canyon", "minecraft:underwater_canyon");
        boolean ravinesEnabled = this.preset.getConfig().getCarverSettings().isRavinesEnabled();
        if (ravinesEnabled) {
            for (Holder<ConfiguredWorldCarver<?>> carver : iterable) {
                if (defaultRavines.stream().noneMatch(
                        b -> b.equalsIgnoreCase(carver.unwrapKey().map(Objects::toString).orElse(""))
                )) {
                    ravinesEnabled = false;
                    break;
                }
            }
        }

        ChunkBuffer chunkBuffer = createChunkBuffer(chunkAccess);
        CarvingMask carvingMask = ((ProtoChunk) chunkAccess).getOrCreateCarvingMask();
        BitSet bitSet = BitSet.valueOf(carvingMask.toArray());
        internalGenerator.carve(
                chunkBuffer,
                seed,
                bitSet,
                cavesEnabled,
                ravinesEnabled
        );
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunkAccess, StructureManager structureManager, Blender blender, RandomState randomState) {
        return NoiseChunk.forChunk(chunkAccess, randomState, Beardifier.forStructuresInChunk(structureManager, chunkAccess.getPos()), this.settings.value(), this.globalFluidPicker, blender);
    }

    // --- Surface / spawning ---

    @Override
    public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structureManager, RandomState randomState, ChunkAccess chunkAccess) {
        // surface is handled in fillFromNoise
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        ChunkPos chunkPos = worldGenRegion.getCenter();
        Holder<Biome> biome = worldGenRegion.getBiome(chunkPos.getWorldPosition().atY(worldGenRegion.getMaxY()));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(worldGenRegion.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(worldGenRegion, biome, chunkPos, random);
    }

    // --- Noise generation ---

    @Override
    public int getGenDepth() {
        return this.settings.value().noiseSettings().height();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender, RandomState randomState, StructureManager structureManager,
            ChunkAccess chunkAccess
    ) {
        long t0 = System.nanoTime();
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(
                chunkAccess.getPos().x, chunkAccess.getPos().z);

        ObjectList<JigsawStructureData> structures = new ObjectArrayList<>(10);
        ChunkPos pos = chunkAccess.getPos();
        for (StructureStart start : structureManager.startsForStructure(pos,
                s -> s.terrainAdaptation() != TerrainAdjustment.NONE
                        && s.terrainAdaptation() != TerrainAdjustment.BURY)) {
            if (start.isValid()) {
                for (StructurePiece piece : start.getPieces()) {
                    if (piece instanceof PoolElementStructurePiece poolPiece
                            && poolPiece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID
                            && piece.isCloseToChunk(pos, 12)) {
                        BoundingBox box = piece.getBoundingBox();
                        structures.add(new JigsawStructureData(
                                box.minX(), box.minY(), box.minZ(),
                                box.maxX(), poolPiece.getGroundLevelDelta(), box.maxZ(),
                                true, 0, 0, 0));
                    }
                }
            }
        }

        ChunkBuffer buffer = createChunkBuffer(chunkAccess);
        Random random = getRandomFromChunkCoord(chunkCoord);

        long tNoise = System.nanoTime();
        this.internalGenerator.populateNoise(otgWorldInfo, buffer,
                buffer.getChunkCoordinate(), structures, random);
        long noiseElapsed = System.nanoTime() - tNoise;
        populateNoiseTotalNs.addAndGet(noiseElapsed);

        long totalElapsed = System.nanoTime() - t0;
        fillNoiseTotalNs.addAndGet(totalElapsed);
        int count = fillNoiseCount.incrementAndGet();
        if (count % TIMING_LOG_INTERVAL == 0) {
            OTGLog.info(LogCategory.PERFORMANCE, "fillFromNoise #{} avg={}ms (populateNoise={}ms)", count,
                    String.format("%.1f", fillNoiseTotalNs.get() / 1_000_000.0 / count),
                    String.format("%.1f", populateNoiseTotalNs.get() / 1_000_000.0 / count));
        }

        return CompletableFuture.completedFuture(chunkAccess);
    }

    // --- Utility ---

    public @NotNull Random getRandomFromChunkCoord(ChunkCoordinate chunkCoord) {
        return new Random(this.seed + chunkCoord.getChunkX() * 341873128712L + chunkCoord.getChunkZ() * 132897987541L);
    }

    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }

    @Override
    public int getMinY() {
        return settings.value().noiseSettings().minY();
    }

    @Override
    public int getBaseHeight(int i, int j, Heightmap.Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        return this.sampleHeightmap(i, j, null, types.isOpaque());
    }

    @Override
    public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        BlockState[] blockStates = new BlockState[levelHeightAccessor.getHeight()];
        this.sampleHeightmap(i, j, blockStates, null);
        return new NoiseColumn(levelHeightAccessor.getMinY(), blockStates);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos) {
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome(blockPos.getX(), blockPos.getZ());
        list.add("Preset: " + this.preset.getFolderName());
        list.add("Biome: " + biome.getBiomeSettings().getIdentitySettings().getDisplayName());
        list.add("OTG Debug { " + otgWorldInfo.toString() + " }");
    }

    // --- Heightmap sampling ---

    private int sampleHeightmap(int x, int z, @Nullable BlockState[] blockStates, @Nullable Predicate<BlockState> predicate) {
        int minY = this.settings.value().noiseSettings().minY();
        int xStart = Math.floorDiv(x, 4);
        int zStart = Math.floorDiv(z, 4);
        int xProgress = Math.floorMod(x, 4);
        int zProgress = Math.floorMod(z, 4);
        double xLerp = (double) xProgress / 4.0;
        double zLerp = (double) zProgress / 4.0;
        double[][] noiseData = new double[4][this.internalGenerator.getNoiseSizeY() + 1];

        for (int i = 0; i < noiseData.length; i++) {
            noiseData[i] = new double[this.internalGenerator.getNoiseSizeY() + 1];
        }

        this.internalGenerator.getNoiseColumn(noiseData[0], xStart, zStart);
        this.internalGenerator.getNoiseColumn(noiseData[1], xStart, zStart + 1);
        this.internalGenerator.getNoiseColumn(noiseData[2], xStart + 1, zStart);
        this.internalGenerator.getNoiseColumn(noiseData[3], xStart + 1, zStart + 1);

        for (int noiseY = this.internalGenerator.getNoiseSizeY() - 1; noiseY >= 0; --noiseY) {
            double val000 = noiseData[0][noiseY];
            double val010 = noiseData[1][noiseY];
            double val100 = noiseData[2][noiseY];
            double val110 = noiseData[3][noiseY];
            double val001 = noiseData[0][noiseY + 1];
            double val011 = noiseData[1][noiseY + 1];
            double val101 = noiseData[2][noiseY + 1];
            double val111 = noiseData[3][noiseY + 1];

            for (int pieceY = 7; pieceY >= 0; --pieceY) {
                double yLerp = (double) pieceY / 8.0;
                double density = MathHelper.lerp3(xLerp, yLerp, zLerp, val000, val100, val010, val110, val001, val101, val011, val111);

                int y = (noiseY * 8) + pieceY;

                BlockState state = this.getBlockState(density, y + minY);
                if (blockStates != null) {
                    blockStates[y] = state;
                }

                if (predicate != null && predicate.test(state)) {
                    return y + minY + 1;
                }
            }
        }

        return minY;
    }

    private BlockState getBlockState(double density, int y) {
        if (density > 0.0D) {
            return this.settings.value().defaultBlock();
        } else if (y < this.getSeaLevel()) {
            return this.settings.value().defaultFluid();
        } else {
            return Blocks.AIR.defaultBlockState();
        }
    }

    // --- Portal settings ---

    public String getPortalColor() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalColor();
        }
        return "default";
    }

    public List<LocalMaterialData> getPortalBlocks() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalBlocks();
        }
        return new ArrayList<>();
    }

    public String getPortalMob() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMob();
        }
        return "minecraft:zombified_piglin";
    }

    public String getPortalIgnitionSource() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalIgnitionSource();
        }
        return "minecraft:flint_and_steel";
    }

    public int getPortalMinWidth() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMinWidth();
        }
        return 2;
    }

    public int getPortalMaxWidth() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMaxWidth();
        }
        return 21;
    }

    public int getPortalMinHeight() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMinHeight();
        }
        return 3;
    }

    public int getPortalMaxHeight() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMaxHeight();
        }
        return 21;
    }
}
