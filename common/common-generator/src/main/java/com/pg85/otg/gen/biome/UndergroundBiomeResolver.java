package com.pg85.otg.gen.biome;

import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.config.settings.biome.UndergroundBiomeSettings;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves underground biomes from a 3D noise field, independent of the surface biome
 * by default. Pre-computes per-surface-biome candidate lists at construction.
 * Thread-safe: all state immutable after construction.
 */
public class UndergroundBiomeResolver {

    /** Blocks over which the underground zone fades in below the surface threshold. */
    private static final int DEPTH_TRANSITION = 16;

    public record UndergroundCandidate(
            int otgBiomeId, int minY, int maxY, int priority,
            float coverage, int regionSize, float verticalScale) {}

    private final Int2ObjectOpenHashMap<List<UndergroundCandidate>> candidatesBySurfaceBiome;
    private final Int2ObjectOpenHashMap<Integer> startOffsetBySurfaceBiome;
    private final Int2ObjectOpenHashMap<UndergroundRegionNoise> noiseByBiomeId;

    public UndergroundBiomeResolver(IBiome[] biomesById, long worldSeed) {
        this.candidatesBySurfaceBiome = new Int2ObjectOpenHashMap<>();
        this.startOffsetBySurfaceBiome = new Int2ObjectOpenHashMap<>();
        this.noiseByBiomeId = new Int2ObjectOpenHashMap<>();

        // Collect underground biomes + build one noise field each.
        List<UndergroundBiomeInfo> undergroundBiomes = new ArrayList<>();
        for (int id = 0; id < biomesById.length; id++) {
            if (biomesById[id] == null) continue;
            BiomeSettings settings = biomesById[id].getBiomeSettings();
            if (settings.getUndergroundSettings() == null) continue;
            UndergroundBiomeSettings ubs = settings.getUndergroundSettings();
            if (!ubs.isUndergroundBiome()) continue;
            undergroundBiomes.add(new UndergroundBiomeInfo(
                    id, settings.getConfigName(),
                    ubs.getUndergroundMinY(), ubs.getUndergroundMaxY(), ubs.getUndergroundPriority(),
                    ubs.getUndergroundBiomeRarity(), ubs.getUndergroundRegionSize(), ubs.getUndergroundVerticalScale(),
                    ubs.getMinSurfaceTemperature(), ubs.getMaxSurfaceTemperature(),
                    ubs.getMinSurfaceWetness(), ubs.getMaxSurfaceWetness()));
            this.noiseByBiomeId.put(id, new UndergroundRegionNoise(worldSeed, id));
        }

        for (int surfaceId = 0; surfaceId < biomesById.length; surfaceId++) {
            if (biomesById[surfaceId] == null) continue;
            BiomeSettings surfaceSettings = biomesById[surfaceId].getBiomeSettings();
            UndergroundBiomeSettings surfaceUbs = surfaceSettings.getUndergroundSettings();

            int startOffset = (surfaceUbs != null) ? surfaceUbs.getUndergroundBiomeStartOffset() : 8;
            this.startOffsetBySurfaceBiome.put(surfaceId, Integer.valueOf(startOffset));

            if (surfaceUbs != null && surfaceUbs.isUndergroundBiome()) continue; // ug biomes aren't surfaces

            float surfaceTemp = surfaceSettings.getVisualSettings().getBiomeTemperature();
            float surfaceWet = surfaceSettings.getVisualSettings().getBiomeWetness();

            List<String> allowed = (surfaceUbs != null) ? surfaceUbs.getAllowedUndergroundBiomes() : List.of();
            List<String> disallowed = (surfaceUbs != null) ? surfaceUbs.getDisallowedUndergroundBiomes() : List.of();
            boolean hasAllowedFilter = allowed != null && !allowed.isEmpty();
            boolean hasDisallowedFilter = disallowed != null && !disallowed.isEmpty();

            List<UndergroundCandidate> candidates = new ArrayList<>();
            for (UndergroundBiomeInfo ub : undergroundBiomes) {
                // Optional soft coupling to the surface biome (defaults = no coupling).
                if (surfaceTemp < ub.minTemp || surfaceTemp > ub.maxTemp) continue;
                if (surfaceWet < ub.minWet || surfaceWet > ub.maxWet) continue;
                if (hasAllowedFilter && !allowed.contains(ub.biomeName)) continue;
                if (hasDisallowedFilter && disallowed.contains(ub.biomeName)) continue;
                candidates.add(new UndergroundCandidate(
                        ub.otgBiomeId, ub.minY, ub.maxY, ub.priority,
                        ub.coverage, ub.regionSize, ub.verticalScale));
            }
            candidates.sort(Comparator.comparingInt(UndergroundCandidate::priority));
            if (!candidates.isEmpty()) {
                this.candidatesBySurfaceBiome.put(surfaceId, List.copyOf(candidates));
            }
        }

        OTGLog.info(LogCategory.BIOME_REGISTRY,
                "Underground biome resolver initialized: %d underground biomes, %d surface biomes have candidates",
                undergroundBiomes.size(), candidatesBySurfaceBiome.size());
    }

    /**
     * @return OTG biome id of the underground biome at this position, or -1 for none.
     */
    public int resolve(int surfaceBiomeId, int worldX, int worldY, int worldZ, int estimatedSurfaceY) {
        Integer startOffset = this.startOffsetBySurfaceBiome.get(surfaceBiomeId);
        if (startOffset == null) return -1;

        // Cheap early exit before any arithmetic for surfaces with no candidates.
        List<UndergroundCandidate> candidates = this.candidatesBySurfaceBiome.get(surfaceBiomeId);
        if (candidates == null) return -1;

        int undergroundStart = estimatedSurfaceY - startOffset.intValue();
        if (worldY >= undergroundStart) return -1;

        // Smooth fade-in: 0 at the threshold, ramping to 1 over DEPTH_TRANSITION blocks below.
        double depthWeight = (undergroundStart - worldY) / (double) DEPTH_TRANSITION;
        if (depthWeight > 1.0) depthWeight = 1.0;

        for (UndergroundCandidate c : candidates) {
            if (worldY < c.minY() || worldY > c.maxY()) continue;
            if (c.coverage() <= 0.0f) continue;
            UndergroundRegionNoise noise = this.noiseByBiomeId.get(c.otgBiomeId());
            if (noise == null) continue;
            double n = noise.sample(worldX, worldY, worldZ, c.regionSize(), c.verticalScale());
            // coverage% -> threshold: 100 => 0 (present everywhere qualifying), 0 => 1 (never).
            double threshold = 1.0 - (c.coverage() / 100.0) * depthWeight;
            if (n >= threshold) return c.otgBiomeId();
        }
        return -1;
    }

    public boolean hasUndergroundBiomes() {
        return !this.candidatesBySurfaceBiome.isEmpty();
    }

    private record UndergroundBiomeInfo(
            int otgBiomeId, String biomeName,
            int minY, int maxY, int priority,
            float coverage, int regionSize, float verticalScale,
            float minTemp, float maxTemp, float minWet, float maxWet) {}
}
