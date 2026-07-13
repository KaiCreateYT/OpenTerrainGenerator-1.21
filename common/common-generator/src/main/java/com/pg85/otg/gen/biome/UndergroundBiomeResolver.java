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
 * Resolves underground biomes based on surface biome properties and Y coordinate.
 * Pre-computes lookup tables at initialization for minimal runtime cost.
 * Thread-safe: all state is immutable after construction.
 */
public class UndergroundBiomeResolver {

    /**
     * Immutable record for a candidate underground biome at runtime.
     * Only minY, maxY, and otgBiomeId are needed — conditions are pre-filtered.
     */
    public record UndergroundCandidate(int otgBiomeId, int minY, int maxY, int priority, float rarity) {}

    private static final int REGION_SIZE = 64;

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

    private final long worldSeed;

    /**
     * Builds the resolver from all loaded biomes.
     *
     * @param biomesById Array of all biomes indexed by OTG biome ID.
     */
    public UndergroundBiomeResolver(IBiome[] biomesById, long worldSeed) {
        this.candidatesBySurfaceBiome = new Int2ObjectOpenHashMap<>();
        this.startOffsetBySurfaceBiome = new Int2ObjectOpenHashMap<>();
        this.worldSeed = worldSeed;

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
                    ubs.getUndergroundBiomeRarity(),
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

                candidates.add(new UndergroundCandidate(ub.otgBiomeId, ub.minY, ub.maxY, ub.priority, ub.rarity));
            }

            // Sort by priority (ascending)
            candidates.sort(Comparator.comparingInt(UndergroundCandidate::priority));

            if (!candidates.isEmpty()) {
                candidatesBySurfaceBiome.put(surfaceId, List.copyOf(candidates));
            }
        }

        OTGLog.info(LogCategory.BIOME_REGISTRY,
                "Underground biome resolver initialized: %d underground biomes, %d surface biomes have candidates",
                undergroundBiomes.size(), candidatesBySurfaceBiome.size());
    }

    /**
     * Resolves the underground biome at the given position.
     *
     * @param surfaceBiomeId OTG biome ID of the surface biome at (x, z)
     * @param worldX         World X coordinate
     * @param worldY         World Y coordinate (NOT noise Y)
     * @param worldZ         World Z coordinate
     * @param estimatedSurfaceY Estimated surface height at (x, z)
     * @return OTG biome ID of the underground biome, or -1 if no underground biome applies
     */
    public int resolve(int surfaceBiomeId, int worldX, int worldY, int worldZ, int estimatedSurfaceY) {
        Integer startOffset = startOffsetBySurfaceBiome.get(surfaceBiomeId);
        if (startOffset == null) return -1;

        int undergroundStart = estimatedSurfaceY - startOffset;
        if (worldY >= undergroundStart) return -1;

        List<UndergroundCandidate> candidates = candidatesBySurfaceBiome.get(surfaceBiomeId);
        if (candidates == null) return -1;

        for (UndergroundCandidate candidate : candidates) {
            if (worldY >= candidate.minY && worldY <= candidate.maxY) {
                if (passesRarityCheck(worldX, worldZ, candidate.otgBiomeId, candidate.rarity)) {
                    return candidate.otgBiomeId;
                }
            }
        }

        return -1;
    }

    /**
     * Deterministic rarity check using region-based hashing.
     * Divides the world into REGION_SIZE x REGION_SIZE blocks (XZ plane).
     * Each region gets a deterministic roll based on position, world seed, and biome ID.
     */
    private boolean passesRarityCheck(int worldX, int worldZ, int biomeId, float rarity) {
        if (rarity >= 100.0f) return true;
        if (rarity <= 0.0f) return false;

        int regionX = Math.floorDiv(worldX, REGION_SIZE);
        int regionZ = Math.floorDiv(worldZ, REGION_SIZE);

        long hash = regionX * 341873128712L + regionZ * 132897987541L
                + worldSeed + biomeId * 6364136223846793005L;
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);

        float roll = (float) ((hash & 0x7FFFFFFFL) % 10000) / 100.0f;
        return roll < rarity;
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
            float rarity,
            float minTemp, float maxTemp,
            float minWet, float maxWet
    ) {}
}
