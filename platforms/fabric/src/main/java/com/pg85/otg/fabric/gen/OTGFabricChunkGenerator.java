package com.pg85.otg.fabric.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pg85.otg.OTG;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.constants.settings.structure.CustomStructureType;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.fabric.biome.FabricBiome;
import com.pg85.otg.fabric.biome.OTGFabricBiomeProvider;
import com.pg85.otg.gen.OTGChunkDecorator;
import com.pg85.otg.gen.OTGChunkGenerator;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.presets.Preset;
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
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

@Getter
public class OTGFabricChunkGenerator extends ChunkGenerator {
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        if (this.seed == 0L) {
            this.setSeed(BiomeManager.obfuscateSeed(seed));
        }
        return super.createState(structureSetLookup, randomState, seed);
    }

    public static final MapCodec<OTGFabricChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    instance.group(
                            OTGFabricBiomeProvider.CODEC.forGetter(OTGFabricChunkGenerator::getBiomeSource),
                            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(OTGFabricChunkGenerator::getSettings)
                    ).apply(instance, instance.stable(OTGFabricChunkGenerator::createFromCodec)));

    private final Holder<NoiseGeneratorSettings> settings;
    private final OTGFabricBiomeProvider biomeSource;
    private final OTGChunkGenerator internalGenerator;
    private final Preset preset;
    private Registry<Biome> biomeRegistry;
    private final NoiseBasedChunkGenerator horribleDelegateForCarvers;
    private final ShadowChunkGenerator shadowChunkGenerator;
    private Aquifer.FluidPicker globalFluidPicker = null;
    private final OTGChunkDecorator chunkDecorator;
    private CustomStructureCache structureCache = null;
    private Long seed = 0L;
    private ServerLevel serverLevel = null;
    private final OTGWorldInfo otgWorldInfo;

    /**
     * Factory method for CODEC deserialization - biomeRegistry will be set later from ServerLevel
     */
    public static OTGFabricChunkGenerator createFromCodec(
            OTGFabricBiomeProvider biomeSource, Holder<NoiseGeneratorSettings> settings
    ) {
        return new OTGFabricChunkGenerator(biomeSource, settings, null);
    }

    public OTGFabricChunkGenerator(
            OTGFabricBiomeProvider biomeSource, Holder<NoiseGeneratorSettings> settings, Registry<Biome> biomeHolderGetter
    ) {
        super(biomeSource);
        this.settings = settings;
        this.biomeSource = biomeSource;
        int minY = settings.value().noiseSettings().minY();
        int maxY = settings.value().noiseSettings().height() + minY - 1;
        this.otgWorldInfo = new OTGWorldInfo(minY, maxY);
        this.internalGenerator = new OTGChunkGenerator(
                OTG.getEngine().getPresetLoader().getPresetByFolderName(biomeSource.getPresetFolderName()),
                biomeSource,
                OTG.getEngine().getPresetLoader().getGlobalIdMapping(biomeSource.getPresetFolderName()),
                otgWorldInfo
        );
        this.preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(biomeSource.getPresetFolderName());
        this.biomeRegistry = biomeHolderGetter;
        this.horribleDelegateForCarvers = new NoiseBasedChunkGenerator(biomeSource, settings);
        this.chunkDecorator = new OTGChunkDecorator();
        this.shadowChunkGenerator = new ShadowChunkGenerator();
        this.globalFluidPicker = createFluidPicker(settings.value());
    }

    public void setSeed(Long seed) {
        synchronized (this) {
            if (this.seed == 0L) {
                this.seed = seed;
                biomeSource.setSeed(seed);
                internalGenerator.setSeed(seed);
            }
        }
    }

    public void setServerLevel(ServerLevel serverLevel) {
        synchronized (this) {
            if (this.serverLevel == null) {
                this.serverLevel = serverLevel;
                // Set biomeRegistry from server if not set during construction (datapack loading)
                if (this.biomeRegistry == null) {
                    this.biomeRegistry = serverLevel.registryAccess().registryOrThrow(Registries.BIOME);
                }
            }
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess, StructureManager structureManager) {
        if(!OTG.getEngine().getPluginConfig().getDecorationEnabled()) {
            return;
        }
        // Do OTG resource decoration, then MC decoration for any non-OTG resources registered to this biome, then snow.
        ChunkCoordinate chunkBeingDecorated = getChunkCoordinate(worldGenLevel, chunkAccess);
        FabricWorldGenRegion fabricWorldGenRegion = new FabricWorldGenRegion(this.preset.getFolderName(), OTG.getEngine().getPluginConfig(), this.preset.getPresetConfig(), otgWorldInfo, worldGenLevel, chunkAccess, this);
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome((chunkAccess.getPos().x << 2) + 2, (chunkAccess.getPos().z << 2) + 2);

        // World save folder name may not be identical to level name, fetch it.
        Path worldSaveFolder = worldGenLevel.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).getParent();

        this.chunkDecorator.decorate(chunkBeingDecorated, fabricWorldGenRegion, biome.getBiomeSettings(), getStructureCache(worldSaveFolder));
        super.applyBiomeDecoration(worldGenLevel, chunkAccess, structureManager);

        // Template biomes handle their own snow, OTG biomes use OTG snow.
        // TODO: Snow is handled per chunk, so this may cause some artifacts on biome borders.
        if(!biome.getBiomeSettings().getIdentitySettings().isTemplateForBiome()) {
            this.chunkDecorator.doSnowAndIce(fabricWorldGenRegion, chunkBeingDecorated);
        }

    }

    public void saveStructureCache() {
        if (this.chunkDecorator.getIsSaveRequired() && this.structureCache != null) {
            this.structureCache.saveToDisk(chunkDecorator);
        }
    }

    private static ChunkCoordinate getChunkCoordinate(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess) {
        int worldX = chunkAccess.getPos().x * Constants.CHUNK_SIZE;
        int worldZ = chunkAccess.getPos().z * Constants.CHUNK_SIZE;

        WorldgenRandom worldgenRandom = new WorldgenRandom(worldGenLevel.getRandom());
        worldgenRandom.setDecorationSeed(worldGenLevel.getSeed(), worldX, worldZ);

        return ChunkCoordinate.fromBlockCoords(worldX, worldZ);
    }

    public CustomStructureCache getStructureCache(Path worldSaveFolder)
    {
        if(this.structureCache == null)
        {
            this.structureCache = OTG.getEngine().createCustomStructureCache(
                    this.preset.getFolderName(),
                    worldSaveFolder,
                    this.seed,
                    CustomStructureType.BO4 == this.preset.getPresetConfig().getResourceSettings().getCustomStructureType());
        }
        return this.structureCache;
    }

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState chunkGeneratorStructureState,
            StructureManager structureManager,
            ChunkAccess chunkAccess,
            StructureTemplateManager structureTemplateManager
    ) {
        super.createStructures(registryAccess, chunkGeneratorStructureState, structureManager, chunkAccess, structureTemplateManager);
    }

    @Override
    public void createReferences(
            WorldGenLevel worldGenRegion,
            StructureManager structureManager,
            ChunkAccess chunkAccess
    ) {
        if (this.serverLevel == null) {
            this.setServerLevel(worldGenRegion.getLevel());
        }

        super.createReferences(worldGenRegion, structureManager, chunkAccess);
    }

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

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion worldGenRegion, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess, GenerationStep.Carving carving) {

        handleOTGCarvers(seed, chunkAccess, carving);

        //applyNonOTGCarvers(seed, biomeManager, chunkAccess, carving);

        List<String> defaultCavesAndRavines = Arrays.asList("minecraft:cave", "minecraft:underwater_cave", "minecraft:nether_cave", "minecraft:canyon", "minecraft:underwater_canyon");

        BiomeManager biomeManager2 = biomeManager.withDifferentSource((i, j, k) -> this.biomeSource.getNoiseBiome(i, j, k, randomState.sampler()));
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        int i2 = 8;
        ChunkPos chunkPos = chunkAccess.getPos();
        NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(chunkAccess2 -> this.createNoiseChunk(chunkAccess2, structureManager, Blender.of(worldGenRegion), randomState));
        CarvingMask carvingMask = ((ProtoChunk) chunkAccess).getOrCreateCarvingMask(carving);
        Aquifer aquifer = noiseChunk.aquifer();
        CarvingContext carvingContext = new CarvingContext(this.horribleDelegateForCarvers, worldGenRegion.registryAccess(), chunkAccess.getHeightAccessorForGeneration(), noiseChunk, randomState, this.settings.value().surfaceRule());
        for (int j2 = -8; j2 <= 8; ++j2) {
            for (int k2 = -8; k2 <= 8; ++k2) {
                ChunkPos chunkPos2 = new ChunkPos(chunkPos.x + j2, chunkPos.z + k2);
                ChunkAccess chunkAccess22 = worldGenRegion.getChunk(chunkPos2.x, chunkPos2.z);
                BiomeGenerationSettings biomeGenerationSettings = chunkAccess22.carverBiome(() -> this.getBiomeGenerationSettings(this.biomeSource.getNoiseBiome(QuartPos.fromBlock(chunkPos2.getMinBlockX()), 0, QuartPos.fromBlock(chunkPos2.getMinBlockZ()), randomState.sampler())));
                Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomeGenerationSettings.getCarvers(carving);
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

    private void handleOTGCarvers(long seed, ChunkAccess chunkAccess, GenerationStep.Carving carving) {
        FabricBiome biome = (FabricBiome) this.internalGenerator.getCachedBiomeProvider().getNoiseBiome(chunkAccess.getPos().x << 2, chunkAccess.getPos().z << 2);
        BiomeGenerationSettings biomegenerationsettings = biome.getBiome().getGenerationSettings();
        Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomegenerationsettings.getCarvers(carving);

        // Only use OTG carvers when default mc carvers are found
        List<String> defaultCaves = Arrays.asList("minecraft:cave", "minecraft:underwater_cave", "minecraft:nether_cave");
        boolean cavesEnabled = this.preset.getPresetConfig().getCarverSettings().isCavesEnabled();
        if (cavesEnabled)
        {
            for (Holder<ConfiguredWorldCarver<?>> carver : iterable)
            {
                if (defaultCaves.stream().noneMatch(
                        b -> b.equalsIgnoreCase(carver.unwrapKey().map(Objects::toString).orElse(""))
                ))
                {
                    cavesEnabled = false;
                    break;
                }
            }
        }

        List<String> defaultRavines = Arrays.asList("minecraft:canyon", "minecraft:underwater_canyon");
        boolean ravinesEnabled = this.preset.getPresetConfig().getCarverSettings().isRavinesEnabled();
        if (ravinesEnabled)
        {
            for (Holder<ConfiguredWorldCarver<?>> carver : iterable)
            {
                if (defaultRavines.stream().noneMatch(
                        b -> b.equalsIgnoreCase(carver.unwrapKey().map(Objects::toString).orElse(""))
                ))
                {
                    ravinesEnabled = false;
                    break;
                }
            }
        }

        ChunkBuffer chunkBuffer = new FabricChunkBuffer(chunkAccess);
        CarvingMask carvingMask = ((ProtoChunk) chunkAccess).getOrCreateCarvingMask(carving);
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

    @Override
    public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structureManager, RandomState randomState, ChunkAccess chunkAccess) {
        // surface is handled in fillFromNoise
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        ChunkPos chunkPos = worldGenRegion.getCenter();
        Holder<Biome> biome = worldGenRegion.getBiome(chunkPos.getWorldPosition().atY(worldGenRegion.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(worldGenRegion.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(worldGenRegion, biome, chunkPos, random);
    }

    @Override
    public int getGenDepth() {
        return this.settings.value().noiseSettings().height();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Executor executor, Blender blender, RandomState randomState, StructureManager structureManager,
            ChunkAccess chunkAccess
    ) {
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(
                chunkAccess.getPos().x, chunkAccess.getPos().z);

        // Structure data is already available — STRUCTURE ChunkStatus completed before NOISE.
        ObjectList<JigsawStructureData> structures = new ObjectArrayList<>(10);
        ChunkPos pos = chunkAccess.getPos();
        for (Map.Entry<Structure, StructureStart> n : chunkAccess.getAllStarts().entrySet()) {
            Structure structure = n.getKey();
            StructureStart start = n.getValue();
            if (structure.terrainAdaptation() != TerrainAdjustment.NONE
                    && structure.terrainAdaptation() != TerrainAdjustment.BURY
                    && start.isValid()) {
                for (StructurePiece piece : start.getPieces()) {
                    if (piece.isCloseToChunk(pos, 0)) {
                        BoundingBox box = piece.getBoundingBox();
                        structures.add(new JigsawStructureData(
                                box.minX(), box.minY(), box.minZ(),
                                box.maxX(), box.maxY(), box.maxZ(),
                                true, 0, 0, 0));
                    }
                }
            }
        }

        ChunkBuffer buffer = new FabricChunkBuffer(chunkAccess);
        Random random = getRandomFromChunkCoord(chunkCoord);
        this.internalGenerator.populateNoise(otgWorldInfo, buffer,
                buffer.getChunkCoordinate(), structures, random);

        return CompletableFuture.completedFuture(chunkAccess);
    }



    public @NotNull Random getRandomFromChunkCoord(ChunkCoordinate chunkCoord) {
        return new Random(this.seed + chunkCoord.getChunkX()*341873128712L + chunkCoord.getChunkZ()*132897987541L);
    }

    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }

    @Override
    public int getMinY() {
        return settings.value().noiseSettings().minY();
    }

    // For later lookup - will return the height of the terrain at the given x and z coordinates, but not save anything to the column
    @Override
    public int getBaseHeight(int i, int j, Heightmap.Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        return this.sampleHeightmap(i, j, null, types.isOpaque());
    }

    // For generation - will generate the chunk and save the data to the column
    @Override
    public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        BlockState[] blockStates = new BlockState[levelHeightAccessor.getHeight()];
        this.sampleHeightmap(i, j, blockStates, null);
        return new NoiseColumn(levelHeightAccessor.getMinBuildHeight(), blockStates);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos) {
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome(blockPos.getX(), blockPos.getZ());
        list.add("Preset: " + this.preset.getFolderName());
        list.add("Biome: " + biome.getBiomeSettings().getIdentitySettings().getDisplayName());
        list.add("OTG Debug { "+otgWorldInfo.toString()+" }");
    }

    private int sampleHeightmap(int x, int z, @Nullable BlockState[] blockStates, @Nullable Predicate<BlockState> predicate)
    {
        // Get all of the coordinate starts and positions
        int xStart = Math.floorDiv(x, 4);
        int zStart = Math.floorDiv(z, 4);
        int xProgress = Math.floorMod(x, 4);
        int zProgress = Math.floorMod(z, 4);
        double xLerp = (double) xProgress / 4.0;
        double zLerp = (double) zProgress / 4.0;
        // Create the noise data in a 2 * 2 * 32 grid for interpolation.
        double[][] noiseData = new double[4][this.internalGenerator.getNoiseSizeY() + 1];

        // Initialize noise array.
        for (int i = 0; i < noiseData.length; i++)
        {
            noiseData[i] = new double[this.internalGenerator.getNoiseSizeY() + 1];
        }

        // Sample all 4 nearby columns.
        this.internalGenerator.getNoiseColumn(noiseData[0], xStart, zStart);
        this.internalGenerator.getNoiseColumn(noiseData[1], xStart, zStart + 1);
        this.internalGenerator.getNoiseColumn(noiseData[2], xStart + 1, zStart);
        this.internalGenerator.getNoiseColumn(noiseData[3], xStart + 1, zStart + 1);

        // [0, 32] -> noise chunks
        for (int noiseY = this.internalGenerator.getNoiseSizeY() - 1; noiseY >= 0; --noiseY)
        {
            // Gets all the noise in a 2x2x2 cube and interpolates it together.
            // Lower pieces
            double val000 = noiseData[0][noiseY];
            double val010 = noiseData[1][noiseY];
            double val100 = noiseData[2][noiseY];
            double val110 = noiseData[3][noiseY];
            // Upper pieces
            double val001 = noiseData[0][noiseY + 1];
            double val011 = noiseData[1][noiseY + 1];
            double val101 = noiseData[2][noiseY + 1];
            double val111 = noiseData[3][noiseY + 1];

            // [0, 8] -> noise pieces
            for (int pieceY = 7; pieceY >= 0; --pieceY)
            {
                double yLerp = (double) pieceY / 8.0;
                // Density at this position given the current y interpolation
                //double density = MathHelper.lerp3(yLerp, xLerp, zLerp, val000, val001, val100, val101, val010, val011, val110, val111);
                double density = MathHelper.lerp3(xLerp, yLerp, zLerp, val000, val100, val010, val110, val001, val101, val011, val111);

                // Get the real y position (translate noise chunk and noise piece)
                int y = (noiseY * 8) + pieceY;

                BlockState state = this.getBlockState(density, y);
                if (blockStates != null)
                {
                    blockStates[y] = state;
                }

                // return y if it fails the check
                if (predicate != null && predicate.test(state))
                {
                    return y + 1;
                }
            }
        }

        return 0;
    }

    // MC's NoiseChunkGenerator returns defaultBlock and defaultFluid here, so callers
    // apparently don't rely on any blocks (re)placed after base terrain gen, only on
    // the default block/liquid set for the dimension (stone/water for overworld,
    // netherrack/lava for nether), that MC uses for base terrain gen.
    // We can do the same, no need to pass biome config and fetch replaced blocks etc.
    // OTG does place blocks other than defaultBlock/defaultLiquid during base terrain gen
    // (for replaceblocks/sagc), but that shouldn't matter for the callers of this method.
    // Actually, it's probably better if they don't see OTG's replaced blocks, and just see
    // the default blocks instead, as vanilla MC would do.
    private BlockState getBlockState(double density, int y)
    {
        if (density > 0.0D)
        {
            return this.settings.value().defaultBlock();
        }
        else if (y < this.getSeaLevel())
        {
            return this.settings.value().defaultFluid();
        } else {
            return Blocks.AIR.defaultBlockState();
        }
    }

    public Boolean checkHasVanillaStructureWithoutLoading(ServerLevel level, ChunkCoordinate chunkCoord)
    {
        return this.shadowChunkGenerator.checkHasVanillaStructureWithoutLoading(level, chunkCoord, this.internalGenerator.getCachedBiomeProvider(), false);
    }

    public int getHighestBlockYInUnloadedChunk(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow)
    {
        return this.shadowChunkGenerator.getHighestBlockYInUnloadedChunk(this.serverLevel, this, this.otgWorldInfo, x, z, findSolid, findLiquid, ignoreLiquid, ignoreSnow);
    }

    public LocalMaterialData getMaterialInUnloadedChunk(int x, int y, int z)
    {
        return this.shadowChunkGenerator.getMaterialInUnloadedChunk(this.serverLevel, this, this.otgWorldInfo, x, y, z);
    }

    // Portal settings getters
    public String getPortalColor() {
        if (preset != null && preset.getPresetConfig() != null) {
            return preset.getPresetConfig().getPortalSettings().getPortalColor();
        }
        return "default";
    }

    public List<LocalMaterialData> getPortalBlocks() {
        if (preset != null && preset.getPresetConfig() != null) {
            return preset.getPresetConfig().getPortalSettings().getPortalBlocks();
        }
        return new ArrayList<>();
    }

    public String getPortalMob() {
        if (preset != null && preset.getPresetConfig() != null) {
            return preset.getPresetConfig().getPortalSettings().getPortalMob();
        }
        return "minecraft:zombified_piglin";
    }

    public String getPortalIgnitionSource() {
        if (preset != null && preset.getPresetConfig() != null) {
            return preset.getPresetConfig().getPortalSettings().getPortalIgnitionSource();
        }
        return "minecraft:flint_and_steel";
    }

    public int getPortalMinWidth() {
        if (preset != null && preset.getPresetConfig() != null) {
            return preset.getPresetConfig().getPortalSettings().getPortalMinWidth();
        }
        return 2;
    }

    public int getPortalMaxWidth() {
        if (preset != null && preset.getPresetConfig() != null) {
            return preset.getPresetConfig().getPortalSettings().getPortalMaxWidth();
        }
        return 21;
    }

    public int getPortalMinHeight() {
        if (preset != null && preset.getPresetConfig() != null) {
            return preset.getPresetConfig().getPortalSettings().getPortalMinHeight();
        }
        return 3;
    }

    public int getPortalMaxHeight() {
        if (preset != null && preset.getPresetConfig() != null) {
            return preset.getPresetConfig().getPortalSettings().getPortalMaxHeight();
        }
        return 21;
    }
}
