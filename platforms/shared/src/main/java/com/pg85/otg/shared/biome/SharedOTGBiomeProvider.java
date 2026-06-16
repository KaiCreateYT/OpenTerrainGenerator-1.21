package com.pg85.otg.shared.biome;

import com.mojang.datafixers.util.Pair;
import com.pg85.otg.OTG;
import com.pg85.otg.gen.biome.UndergroundBiomeResolver;
import com.pg85.otg.gen.biome.layers.BiomeLayers;
import com.pg85.otg.gen.biome.layers.util.CachingLayerSampler;
import com.pg85.otg.interfaces.ILayerSampler;
import com.pg85.otg.interfaces.ILayerSource;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.stream.Stream;

@Getter
public abstract class SharedOTGBiomeProvider extends BiomeSource implements ILayerSource, BiomeManager.NoiseBiomeSource, IOTGBiomeProvider {
    private final String presetFolderName;
    private final CountDownLatch latch = new CountDownLatch(1);
    private long seed;
    private ThreadLocal<CachingLayerSampler> layer;
    private final Int2ObjectOpenHashMap<Holder<Biome>> keyLookup = new Int2ObjectOpenHashMap<>();
    private UndergroundBiomeResolver undergroundResolver;
    private volatile ToIntBiFunction<Integer, Integer> surfaceHeightEstimator;

    protected SharedOTGBiomeProvider(String presetFolderName, long seed) {
        this.presetFolderName = presetFolderName;
        this.seed = seed;
    }

    @Override
    public ILayerSampler getSampler() {
        return layer.get();
    }

    @Override
    protected @NotNull Stream<Holder<Biome>> collectPossibleBiomes() {
        var iBiomes = OTG.getEngine().getDimensionPresetLoader().getGlobalIdMapping(presetFolderName);
        if (iBiomes == null) {
            OTGLog.error(LogCategory.BIOME_REGISTRY,
                    "Biome mapping for preset {} is null.", presetFolderName);
            return Stream.empty();
        }
        for (int otgBiomeID = 0; otgBiomeID < iBiomes.length; otgBiomeID++) {
            keyLookup.put(otgBiomeID, ((SharedBiome) iBiomes[otgBiomeID]).getBiomeHolder());
        }
        this.undergroundResolver = new UndergroundBiomeResolver(iBiomes, this.seed);
        return Stream.of(iBiomes).map(iBiome -> ((SharedBiome) iBiome).getBiomeHolder());
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int i, int j, int k, int l, int m, Predicate<Holder<Biome>> predicate, RandomSource randomSource, boolean bl, Climate.Sampler sampler) {
        if (this.getLayer() == null) {
            throw new IllegalStateException("Layer is null. Seed was not set properly.");
        }
        return super.findBiomeHorizontal(i, j, k, l, m, predicate, randomSource, bl, sampler);
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int i, int j, int k, int l, Predicate<Holder<Biome>> predicate, RandomSource randomSource, Climate.Sampler sampler) {
        if (this.getLayer() == null) {
            throw new IllegalStateException("Layer is null. Seed was not set properly.");
        }
        return super.findBiomeHorizontal(i, j, k, l, predicate, randomSource, sampler);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int i, int j, int k, Climate.Sampler sampler) {
        return resolveNoiseBiome(i, j, k);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int i, int j, int k) {
        return resolveNoiseBiome(i, j, k);
    }

    private Holder<Biome> resolveNoiseBiome(int noiseX, int noiseY, int noiseZ) {
        int surfaceBiomeId = this.getLayer().get().sample(noiseX, noiseZ);

        if (undergroundResolver != null && undergroundResolver.hasUndergroundBiomes()) {
            int worldY = noiseY << 2;
            int worldX = noiseX << 2;
            int worldZ = noiseZ << 2;

            int estimatedSurfaceY = surfaceHeightEstimator != null
                    ? surfaceHeightEstimator.applyAsInt(worldX, worldZ)
                    : 64;

            int undergroundBiomeId = undergroundResolver.resolve(surfaceBiomeId, worldX, worldY, worldZ, estimatedSurfaceY);
            if (undergroundBiomeId >= 0) {
                Holder<Biome> underground = keyLookup.get(undergroundBiomeId);
                if (underground != null) {
                    return underground;
                }
            }
        }

        return keyLookup.get(surfaceBiomeId);
    }

    public void setSurfaceHeightEstimator(ToIntBiFunction<Integer, Integer> estimator) {
        this.surfaceHeightEstimator = estimator;
    }

    public void setSeed(long seed) {
        synchronized (this) {
            if (this.seed == seed && this.layer != null) {
                return;
            }
            if (this.seed != seed && this.layer != null) {
                OTGLog.error(LogCategory.MAIN,
                        "Biome provider seed changed from {} to {}. This is unexpected and may lead to inconsistent biome generation.",
                        this.seed, seed);
            }
            this.seed = seed;
            layer = ThreadLocal.withInitial(() -> BiomeLayers.create(seed, OTG.getEngine().getDimensionPresetLoader().getPresetGenerationData().get(presetFolderName), OTG.getEngine().getLogger()));
            latch.countDown();
        }
    }
}
