package com.pg85.otg.interfaces;

/** Per-chunk lookup of underground biome OTG ids at quart (4-block) resolution. */
public interface IUndergroundBiomeMap {

    /** @return underground biome OTG id at this world position, or -1 if none. */
    int getUndergroundBiomeId(int worldX, int worldY, int worldZ);

    /** @return true if no underground biome occupies this chunk (fast path). */
    boolean isEmpty();
}
