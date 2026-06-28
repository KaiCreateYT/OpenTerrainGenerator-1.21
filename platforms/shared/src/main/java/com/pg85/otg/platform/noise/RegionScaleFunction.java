package com.pg85.otg.platform.noise;

import com.pg85.otg.gen.OTGChunkGenerator;
import com.pg85.otg.interfaces.IUndergroundBiomeMap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Runtime-only density function: returns {@code presetScale * regionMultiplier} for one cave
 * type, where the multiplier comes from the current chunk's underground biome map (bound via
 * {@link OTGChunkGenerator#CURRENT_CAVE_MAP}). Outside any region / when unbound it returns
 * {@code presetScale}, so it behaves exactly like the old constant scale. Never serialized.
 */
public final class RegionScaleFunction implements DensityFunction.SimpleFunction {

    /** Config cap on per-region cave scales (must match UndergroundBiomeSettings range max). */
    public static final double MAX_CAVE_SCALE = 8.0;

    private final int caveType;
    private final double presetScale;

    public RegionScaleFunction(int caveType, double presetScale) {
        this.caveType = caveType;
        this.presetScale = presetScale;
    }

    @Override
    public double compute(FunctionContext ctx) {
        IUndergroundBiomeMap map = OTGChunkGenerator.CURRENT_CAVE_MAP.get();
        if (map == null) return this.presetScale;
        return this.presetScale * map.caveScaleAt(ctx.blockX(), ctx.blockY(), ctx.blockZ(), this.caveType);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(this);
    }

    @Override
    public double minValue() {
        return Math.min(0.0, this.presetScale * MAX_CAVE_SCALE);
    }

    @Override
    public double maxValue() {
        return Math.max(0.0, this.presetScale * MAX_CAVE_SCALE);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        throw new UnsupportedOperationException("RegionScaleFunction is runtime-only and not serializable");
    }
}
