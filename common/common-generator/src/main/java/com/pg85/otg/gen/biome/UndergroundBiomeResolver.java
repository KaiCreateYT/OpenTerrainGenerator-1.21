package com.pg85.otg.gen.biome;

import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.config.settings.biome.UndergroundBiomeSettings;
import com.pg85.otg.interfaces.IBiome;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves underground biomes based on surface biome properties and Y coordinate.
 * Pre-computes lookup tables at initialization for minimal runtime cost.
 * Thread-safe: all state is immutable after construction.
 */
public class UndergroundBiomeResolver {

    /**
     * Immutable record for a candidate underground biome at runtime.
     * Only minY, maxY, and otgBiomeId are needed — conditions are pre-filtered.
     */
    public record UndergroundCandidate(int otgBiomeId, int minY, int maxY, int priority) {}

    /**
     * Pre-computed map: surfaceBiomeOtgId -> sorted list of underground candidates.
     * Candidates are already filtered by temperature/wetness conditions and allowed/disallowed lists.
     * Sorted by priority ascending (lower = higher priority).
     */
    private final Int2ObjectOpenHashMap<List<UndergroundCandidate>> candidatesBySurfaceBiome;

    /**
     * Map: otgBiomeId -> UndergroundBiomeStartOffset for surface biomes.
     */
    private final Int2ObjectOpenHashMap<Integer> startOffsetBySurfaceBiome;

    /**
     * Builds the resolver from all loaded biomes.
     *
     * @param biomesById Array of all biomes indexed by OTG biome ID.
     */
    public UndergroundBiomeResolver(IBiome[] biomesById) {
        this.candidatesBySurfaceBiome = new Int2ObjectOpenHashMap<>();
        this.startOffsetBySurfaceBiome = new Int2ObjectOpenHashMap<>();

        // Collect all underground biomes
        List<UndergroundBiomeInfo> undergroundBiomes = new ArrayList<>();
        for (int id = 0; id < biomesById.length; id++) {
            if (biomesById[id] == null) continue;
            BiomeSettings settings = biomesById[id].getBiomeSettings();
            if (settings.getUndergroundSettings() == null) continue;
            UndergroundBiomeSettings ubs = settings.getUndergroundSettings();
            if (!ubs.isUndergroundBiome()) continue;
            undergroundBiomes.add(new UndergroundBiomeInfo(
                    id,
                    settings.getConfigName(),
                    ubs.getUndergroundMinY(),
                    ubs.getUndergroundMaxY(),
                    ubs.getUndergroundPriority(),
                    ubs.getMinSurfaceTemperature(),
                    ubs.getMaxSurfaceTemperature(),
                    ubs.getMinSurfaceWetness(),
                    ubs.getMaxSurfaceWetness()
            ));
        }

        // For each surface biome, pre-compute matching underground candidates
        for (int surfaceId = 0; surfaceId < biomesById.length; surfaceId++) {
            if (biomesById[surfaceId] == null) continue;
            BiomeSettings surfaceSettings = biomesById[surfaceId].getBiomeSettings();
            UndergroundBiomeSettings surfaceUbs = surfaceSettings.getUndergroundSettings();

            // Store start offset
            int startOffset = (surfaceUbs != null)
                    ? surfaceUbs.getUndergroundBiomeStartOffset()
                    : 8; // default
            startOffsetBySurfaceBiome.put(surfaceId, Integer.valueOf(startOffset));

            // Skip underground biomes as surface providers
            if (surfaceUbs != null && surfaceUbs.isUndergroundBiome()) continue;

            float surfaceTemp = surfaceSettings.getVisualSettings().getBiomeTemperature();
            float surfaceWet = surfaceSettings.getVisualSettings().getBiomeWetness();

            // Get allowed/disallowed lists
            List<String> allowed = (surfaceUbs != null) ? surfaceUbs.getAllowedUndergroundBiomes() : List.of();
            List<String> disallowed = (surfaceUbs != null) ? surfaceUbs.getDisallowedUndergroundBiomes() : List.of();
            boolean hasAllowedFilter = allowed != null && !allowed.isEmpty();
            boolean hasDisallowedFilter = disallowed != null && !disallowed.isEmpty();

            List<UndergroundCandidate> candidates = new ArrayList<>();
            for (UndergroundBiomeInfo ub : undergroundBiomes) {
                // Check temperature conditions
                if (surfaceTemp < ub.minTemp || surfaceTemp > ub.maxTemp) continue;
                // Check wetness conditions
                if (surfaceWet < ub.minWet || surfaceWet > ub.maxWet) continue;
                // Check allowed list
                if (hasAllowedFilter && !allowed.contains(ub.biomeName)) continue;
                // Check disallowed list
                if (hasDisallowedFilter && disallowed.contains(ub.biomeName)) continue;

                candidates.add(new UndergroundCandidate(ub.otgBiomeId, ub.minY, ub.maxY, ub.priority));
            }

            // Sort by priority (ascending)
            candidates.sort(Comparator.comparingInt(UndergroundCandidate::priority));

            if (!candidates.isEmpty()) {
                candidatesBySurfaceBiome.put(surfaceId, List.copyOf(candidates));
            }
        }
    }

    /**
     * Resolves the underground biome at the given position.
     *
     * @param surfaceBiomeId OTG biome ID of the surface biome at (x, z)
     * @param worldY         World Y coordinate (NOT noise Y)
     * @param estimatedSurfaceY Estimated surface height at (x, z)
     * @return OTG biome ID of the underground biome, or -1 if no underground biome applies
     */
    public int resolve(int surfaceBiomeId, int worldY, int estimatedSurfaceY) {
        Integer startOffset = startOffsetBySurfaceBiome.get(surfaceBiomeId);
        if (startOffset == null) return -1;

        int undergroundStart = estimatedSurfaceY - startOffset;
        if (worldY >= undergroundStart) return -1;

        List<UndergroundCandidate> candidates = candidatesBySurfaceBiome.get(surfaceBiomeId);
        if (candidates == null) return -1;

        for (UndergroundCandidate candidate : candidates) {
            if (worldY >= candidate.minY && worldY <= candidate.maxY) {
                return candidate.otgBiomeId;
            }
        }

        return -1;
    }

    /**
     * Returns true if any underground biomes are configured.
     */
    public boolean hasUndergroundBiomes() {
        return !candidatesBySurfaceBiome.isEmpty();
    }

    private record UndergroundBiomeInfo(
            int otgBiomeId,
            String biomeName,
            int minY, int maxY, int priority,
            float minTemp, float maxTemp,
            float minWet, float maxWet
    ) {}
}
