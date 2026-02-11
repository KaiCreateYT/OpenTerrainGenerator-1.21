package com.pg85.otg.platform.noise;

import com.pg85.otg.config.settings.preset.NoiseCaveSettings;
import com.pg85.otg.constants.Constants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Partial reimplementation of NoiseRouterData.overworld with tunable cave scales.
 * Uses vanilla noise keys but applies per-component scaling and offsets from preset.
 */
public class OTGNoiseRouterData {
    private static ResourceLocation rl(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static ResourceLocation noiseRl(String presetName, String path) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID_SHORT, presetName + "/" + path);
    }

    private static Holder<NormalNoise.NoiseParameters> noiseHolder(HolderGetter<NormalNoise.NoiseParameters> noise, String presetName, String path) {
        if (presetName == null) {
            // Use vanilla minecraft: namespace keys (for caveDensityForCarving with randomState.noises)
            return noise.getOrThrow(ResourceKeyUtil.noise(ResourceLocation.withDefaultNamespace(path)));
        }
        return noise.getOrThrow(ResourceKeyUtil.noise(noiseRl(presetName, path)));
    }

    public static NoiseRouter overworld(
            HolderGetter<DensityFunction> df,
            HolderGetter<NormalNoise.NoiseParameters> noise,
            NoiseCaveSettings settings,
            String presetName,
            boolean large,
            boolean amplified
    ) {
        DensityFunction barrier = settings.isAquifersEnabled()
                ? DensityFunctions.noise(noiseHolder(noise, presetName, "aquifer_barrier"), 0.5)
                : DensityFunctions.constant(1.0);
        DensityFunction fluidFlood = settings.isAquifersEnabled()
                ? DensityFunctions.noise(noiseHolder(noise, presetName, "aquifer_floodedness"), 0.67)
                : DensityFunctions.constant(1.0);
        DensityFunction fluidSpread = settings.isAquifersEnabled()
                ? DensityFunctions.noise(noiseHolder(noise, presetName, "aquifer_spread"), 0.7142857142857143)
                : DensityFunctions.constant(1.0);
        DensityFunction lava = settings.isAquifersEnabled()
                ? DensityFunctions.noise(noiseHolder(noise, presetName, "aquifer_lava"))
                : DensityFunctions.constant(1.0);

        DensityFunction shiftX = get(df, rl("shift_x"));
        DensityFunction shiftZ = get(df, rl("shift_z"));

        DensityFunction temperature = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noise.getOrThrow(large ? Noises.TEMPERATURE_LARGE : Noises.TEMPERATURE));
        DensityFunction vegetation = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noise.getOrThrow(large ? Noises.VEGETATION_LARGE : Noises.VEGETATION));

        DensityFunction continents = get(df, rl(large ? "overworld_large_biomes/continents" : "overworld/continents"));
        DensityFunction erosion = get(df, rl(large ? "overworld_large_biomes/erosion" : "overworld/erosion"));
        DensityFunction ridges = get(df, rl("overworld/ridges"));

        DensityFunction factor = get(df, rl(large ? "overworld_large_biomes/factor" : (amplified ? "overworld_amplified/factor" : "overworld/factor")));
        DensityFunction depth = get(df, rl(large ? "overworld_large_biomes/depth" : (amplified ? "overworld_amplified/depth" : "overworld/depth")));
        DensityFunction noiseGradient = noiseGradientDensity(DensityFunctions.cache2d(factor), depth);
        DensityFunction slopedCheese = get(df, rl(large ? "overworld_large_biomes/sloped_cheese" : (amplified ? "overworld_amplified/sloped_cheese" : "overworld/sloped_cheese")));

        DensityFunction y = get(df, rl("y"));
        DensityFunction spaghettiRough = scaleDF(spaghettiRoughnessFunction(noise, presetName), settings.getSpaghetti3dScale());
        DensityFunction spaghetti2d = scaleDF(spaghetti2D(noise, presetName), settings.getSpaghetti2dScale());
        DensityFunction entrances = scaleDF(entrances(spaghettiRough, noise, presetName), settings.getSpaghetti3dScale());
        DensityFunction noodle = scaleDF(noodle(y, noise, presetName), settings.getNoodleScale());
        DensityFunction pillars = scaleDF(pillars(noise, presetName), settings.getPillarScale());

        DensityFunction caveCheese = DensityFunctions.min(slopedCheese, DensityFunctions.mul(DensityFunctions.constant(5.0), entrances));
        DensityFunction underground = underground(noise, presetName, slopedCheese, spaghetti2d, spaghettiRough, entrances, pillars, settings);

        DensityFunction caves = DensityFunctions.rangeChoice(slopedCheese, -1000000.0, 1.5625, caveCheese, underground);
        DensityFunction finalDensity = DensityFunctions.min(postProcess(slideOverworld(amplified, caves)), noodle);

        if (!settings.isCavesEnabled()) {
            finalDensity = DensityFunctions.constant(1.0);
        } else {
            finalDensity = DensityFunctions.add(
                    DensityFunctions.mul(finalDensity, DensityFunctions.constant(settings.getFinalDensityScale())),
                    DensityFunctions.constant(settings.getFinalDensityOffset())
            );
        }

        int minY = settings.getVeinMinY();
        int maxY = settings.getVeinMaxY();
        DensityFunction veinToggle = yLimitedInterpolatable(y, DensityFunctions.noise(noiseHolder(noise, presetName, "ore_veininess"), 1.5, 1.5), minY, maxY, 0);
        DensityFunction veinA = yLimitedInterpolatable(y, DensityFunctions.noise(noiseHolder(noise, presetName, "ore_vein_a"), 4.0, 4.0), minY, maxY, 0).abs();
        DensityFunction veinB = yLimitedInterpolatable(y, DensityFunctions.noise(noiseHolder(noise, presetName, "ore_vein_b"), 4.0, 4.0), minY, maxY, 0).abs();
        DensityFunction veinRidged = DensityFunctions.add(DensityFunctions.constant(-0.08F), DensityFunctions.max(veinA, veinB));
        DensityFunction veinGap = DensityFunctions.noise(noiseHolder(noise, presetName, "ore_gap"));

        if (!settings.isVeinsEnabled()) {
            veinToggle = DensityFunctions.constant(-1.0);
            veinRidged = DensityFunctions.constant(1.0);
            veinGap = DensityFunctions.constant(1.0);
        }

        return new NoiseRouter(
                barrier,
                fluidFlood,
                fluidSpread,
                lava,
                temperature,
                vegetation,
                continents,
                erosion,
                depth,
                ridges,
                slideOverworld(amplified, DensityFunctions.add(noiseGradient, DensityFunctions.constant(-0.703125)).clamp(-64.0, 64.0)),
                finalDensity,
                veinToggle,
                veinRidged,
                veinGap
        );
    }

    private static DensityFunction underground(
            HolderGetter<NormalNoise.NoiseParameters> noise,
            String presetName,
            DensityFunction slopedCheese,
            DensityFunction spaghetti2d,
            DensityFunction spaghettiRough,
            DensityFunction entrances,
            DensityFunction pillars,
            NoiseCaveSettings settings
    ) {
        DensityFunction caveLayer = DensityFunctions.noise(noiseHolder(noise, presetName, "cave_layer"), 8.0);
        DensityFunction layer = DensityFunctions.mul(DensityFunctions.constant(4.0), caveLayer.square());
        DensityFunction caveCheese = DensityFunctions.noise(
                noiseHolder(noise, presetName, "cave_cheese"),
                0.6666666666666666 * settings.getSpaghetti3dScale()
        );
        DensityFunction cheese = DensityFunctions.add(
                DensityFunctions.add(DensityFunctions.constant(0.27), caveCheese).clamp(-1.0, 1.0),
                DensityFunctions.add(DensityFunctions.constant(1.5), DensityFunctions.mul(DensityFunctions.constant(-0.64), slopedCheese)).clamp(0.0, 0.5)
        );
        DensityFunction mix = DensityFunctions.add(layer, cheese);
        DensityFunction min = DensityFunctions.min(DensityFunctions.min(mix, entrances), DensityFunctions.add(spaghetti2d, spaghettiRough));

        DensityFunction pillar = DensityFunctions.rangeChoice(pillars, -1000000.0, 0.03, DensityFunctions.constant(-1000000.0), pillars);
        return DensityFunctions.max(min, pillar);
    }

    public static DensityFunction caveDensity(
            HolderGetter<DensityFunction> df,
            HolderGetter<NormalNoise.NoiseParameters> noise,
            NoiseCaveSettings settings,
            String presetName,
            boolean large,
            boolean amplified
    ) {
        DensityFunction slopedCheese = get(df, rl(large ? "overworld_large_biomes/sloped_cheese" : (amplified ? "overworld_amplified/sloped_cheese" : "overworld/sloped_cheese")));

        DensityFunction y = get(df, rl("y"));
        DensityFunction spaghettiRough = scaleDF(spaghettiRoughnessFunction(noise, presetName), settings.getSpaghetti3dScale());
        DensityFunction spaghetti2d = scaleDF(spaghetti2D(noise, presetName), settings.getSpaghetti2dScale());
        DensityFunction entrances = scaleDF(entrances(spaghettiRough, noise, presetName), settings.getSpaghetti3dScale());
        DensityFunction noodle = scaleDF(noodle(y, noise, presetName), settings.getNoodleScale());
        DensityFunction pillars = scaleDF(pillars(noise, presetName), settings.getPillarScale());

        DensityFunction caveCheese = DensityFunctions.min(slopedCheese, DensityFunctions.mul(DensityFunctions.constant(5.0), entrances));
        DensityFunction underground = underground(noise, presetName, slopedCheese, spaghetti2d, spaghettiRough, entrances, pillars, settings);

        DensityFunction caves = DensityFunctions.rangeChoice(slopedCheese, -1000000.0, 1.5625, caveCheese, underground);
        DensityFunction caveDensity = DensityFunctions.min(caves, noodle);

        if (!settings.isCavesEnabled()) {
            return DensityFunctions.constant(1.0);
        }

        return DensityFunctions.add(
                DensityFunctions.mul(caveDensity, DensityFunctions.constant(settings.getFinalDensityScale())),
                DensityFunctions.constant(settings.getFinalDensityOffset())
        );
    }

    /**
     * Standalone cave density for carving into OTG terrain.
     * Completely independent of vanilla terrain shape — no slopedCheese, no rangeChoice.
     * Each cave type is built directly from noise functions and combined.
     * Result: negative = cave, positive = solid.
     */
    public static DensityFunction caveDensityForCarving(
            HolderGetter<NormalNoise.NoiseParameters> noise,
            NoiseCaveSettings settings,
            String presetName,
            int minY,
            int maxY
    ) {
        if (!settings.isCavesEnabled()) {
            return DensityFunctions.constant(1.0);
        }

        DensityFunction y = DensityFunctions.yClampedGradient(minY, maxY, (double) minY, (double) maxY);

        // === Individual cave components (all terrain-independent) ===
        // Pass null as presetName to use vanilla minecraft: noise keys from randomState.noises
        DensityFunction spaghettiRough = scaleDF(spaghettiRoughnessFunction(noise, null), settings.getSpaghetti3dScale());
        DensityFunction spaghetti2d = scaleDF(spaghetti2D(noise, null), settings.getSpaghetti2dScale());
        DensityFunction entranceFunc = scaleDF(entrances(spaghettiRough, noise, null), settings.getSpaghetti3dScale());
        DensityFunction noodleFunc = scaleDF(noodle(y, noise, null), settings.getNoodleScale());
        DensityFunction pillarsFunc = scaleDF(pillars(noise, null), settings.getPillarScale());

        // === Spaghetti + entrance tunnels (all depths) ===
        DensityFunction spaghettiCaves = DensityFunctions.min(
                DensityFunctions.add(spaghetti2d, spaghettiRough),
                entranceFunc
        );

        // === Cheese caves (large caverns, suppressed near surface) ===
        DensityFunction caveLayer = DensityFunctions.noise(noiseHolder(noise, null, "cave_layer"), 8.0);
        DensityFunction layer = DensityFunctions.mul(DensityFunctions.constant(4.0), caveLayer.square());
        DensityFunction caveCheeseNoise = DensityFunctions.noise(
                noiseHolder(noise, null, "cave_cheese"),
                0.6666666666666666 * settings.getSpaghetti3dScale()
        );
        // Y-based suppression replaces terrain-dependent slopedCheese modulation.
        // Cheese constant 0.35 (vanilla=0.27): slightly smaller cheese caverns.
        // Suppression ramps from Y=10 to Y=50: gradually kills cheese caves near surface.
        DensityFunction cheeseSuppression = DensityFunctions.yClampedGradient(10, 50, 0.0, 1.0);
        DensityFunction cheese = DensityFunctions.add(
                DensityFunctions.add(DensityFunctions.constant(0.35), caveCheeseNoise).clamp(-1.0, 1.0),
                cheeseSuppression
        );
        DensityFunction cheeseCaves = DensityFunctions.add(layer, cheese);

        // === Combine all cave types (min = most aggressive carving wins) ===
        DensityFunction allCaves = DensityFunctions.min(spaghettiCaves, cheeseCaves);
        allCaves = DensityFunctions.min(allCaves, noodleFunc);

        // Pillars: positive values inside pillar areas prevent carving.
        // Threshold 0.15 (vanilla=0.03): only strong pillar signals form columns,
        // reducing pillar count while keeping the most prominent ones.
        DensityFunction pillarMask = DensityFunctions.rangeChoice(
                pillarsFunc, -1000000.0, 0.15,
                DensityFunctions.constant(-1000000.0), pillarsFunc
        );
        allCaves = DensityFunctions.max(allCaves, pillarMask);

        // Suppress caves near bedrock
        DensityFunction bedrockProtection = DensityFunctions.yClampedGradient(minY, minY + 5, 0.2, 0.0);
        allCaves = DensityFunctions.add(allCaves, bedrockProtection);

        // Surface suppression: prevents most caves from breaking through to surface.
        // Starts at Y=50, ramps to +0.5 at Y=80. Gentle enough that strong entrance
        // signals can still occasionally reach surface (desirable for natural entrances).
        DensityFunction surfaceSuppression = DensityFunctions.yClampedGradient(50, 80, 0.0, 0.5);
        allCaves = DensityFunctions.add(allCaves, surfaceSuppression);

        DensityFunction scaled = DensityFunctions.add(
                DensityFunctions.mul(allCaves, DensityFunctions.constant(settings.getFinalDensityScale())),
                DensityFunctions.constant(settings.getFinalDensityOffset())
        );

        return scaled;
    }

    private static DensityFunction spaghettiRoughnessFunction(HolderGetter<NormalNoise.NoiseParameters> noise, String presetName) {
        DensityFunction roughness = DensityFunctions.noise(noiseHolder(noise, presetName, "spaghetti_roughness"));
        DensityFunction modulator = DensityFunctions.mappedNoise(noiseHolder(noise, presetName, "spaghetti_roughness_modulator"), 0.0, -0.1);
        return DensityFunctions.cacheOnce(DensityFunctions.mul(modulator, DensityFunctions.add(roughness.abs(), DensityFunctions.constant(-0.4))));
    }

    private static DensityFunction entrances(
            DensityFunction spaghettiRoughness,
            HolderGetter<NormalNoise.NoiseParameters> noise,
            String presetName
    ) {
        DensityFunction rarity = DensityFunctions.cacheOnce(DensityFunctions.noise(noiseHolder(noise, presetName, "spaghetti_3d_rarity"), 2.0, 1.0));
        DensityFunction thickness = DensityFunctions.mappedNoise(noiseHolder(noise, presetName, "spaghetti_3d_thickness"), -0.065, -0.088);
        DensityFunction spaghetti3d1 = weirdScaledSampler(rarity, noiseHolder(noise, presetName, "spaghetti_3d_1"), "TYPE1");
        DensityFunction spaghetti3d2 = weirdScaledSampler(rarity, noiseHolder(noise, presetName, "spaghetti_3d_2"), "TYPE1");
        DensityFunction spaghetti = DensityFunctions.add(DensityFunctions.max(spaghetti3d1, spaghetti3d2), thickness).clamp(-1.0, 1.0);
        DensityFunction caveEntrance = DensityFunctions.noise(noiseHolder(noise, presetName, "cave_entrance"), 0.75, 0.5);
        DensityFunction entrance = DensityFunctions.add(
                DensityFunctions.add(caveEntrance, DensityFunctions.constant(0.37)), DensityFunctions.yClampedGradient(-10, 30, 0.3, 0.0)
        );
        return DensityFunctions.cacheOnce(DensityFunctions.min(entrance, DensityFunctions.add(spaghettiRoughness, spaghetti)));
    }

    private static DensityFunction noodle(
            DensityFunction y,
            HolderGetter<NormalNoise.NoiseParameters> noise,
            String presetName
    ) {
        DensityFunction noodle = yLimitedInterpolatable(
                y, DensityFunctions.noise(noiseHolder(noise, presetName, "noodle"), 1.0, 1.0), -60, 320, -1
        );
        DensityFunction thickness = yLimitedInterpolatable(
                y, DensityFunctions.mappedNoise(noiseHolder(noise, presetName, "noodle_thickness"), 1.0, 1.0, -0.05, -0.1), -60, 320, 0
        );
        double spacing = 2.6666666666666665;
        DensityFunction ridgeA = yLimitedInterpolatable(
                y, DensityFunctions.noise(noiseHolder(noise, presetName, "noodle_ridge_a"), spacing, spacing), -60, 320, 0
        );
        DensityFunction ridgeB = yLimitedInterpolatable(
                y, DensityFunctions.noise(noiseHolder(noise, presetName, "noodle_ridge_b"), spacing, spacing), -60, 320, 0
        );
        DensityFunction ridge = DensityFunctions.mul(DensityFunctions.constant(1.5), DensityFunctions.max(ridgeA.abs(), ridgeB.abs()));
        return DensityFunctions.rangeChoice(noodle, -1000000.0, 0.0, DensityFunctions.constant(64.0), DensityFunctions.add(thickness, ridge));
    }

    private static DensityFunction pillars(HolderGetter<NormalNoise.NoiseParameters> noise, String presetName) {
        DensityFunction pillar = DensityFunctions.noise(noiseHolder(noise, presetName, "pillar"), 25.0, 0.3);
        DensityFunction rareness = DensityFunctions.mappedNoise(noiseHolder(noise, presetName, "pillar_rareness"), 0.0, -2.0);
        DensityFunction thickness = DensityFunctions.mappedNoise(noiseHolder(noise, presetName, "pillar_thickness"), 0.0, 1.1);
        DensityFunction pillarValue = DensityFunctions.add(DensityFunctions.mul(pillar, DensityFunctions.constant(2.0)), rareness);
        return DensityFunctions.cacheOnce(DensityFunctions.mul(pillarValue, thickness.cube()));
    }

    private static DensityFunction spaghetti2D(HolderGetter<NormalNoise.NoiseParameters> noise, String presetName) {
        DensityFunction modulator = DensityFunctions.noise(noiseHolder(noise, presetName, "spaghetti_2d_modulator"), 2.0, 1.0);
        DensityFunction spaghetti = weirdScaledSampler(modulator, noiseHolder(noise, presetName, "spaghetti_2d"), "TYPE2");
        DensityFunction elevation = DensityFunctions.mappedNoise(
                noiseHolder(noise, presetName, "spaghetti_2d_elevation"), 0.0, (double)Math.floorDiv(-64, 8), 8.0
        );
        DensityFunction thicknessModulator = DensityFunctions.cacheOnce(
                DensityFunctions.mappedNoise(noiseHolder(noise, presetName, "spaghetti_2d_thickness"), 2.0, 1.0, -0.6, -1.3)
        );
        DensityFunction combined = DensityFunctions.add(elevation, DensityFunctions.yClampedGradient(-64, 320, 8.0, -40.0)).abs();
        DensityFunction thickness = DensityFunctions.add(combined, thicknessModulator).cube();
        DensityFunction spaghettiWithThickness = DensityFunctions.add(spaghetti, DensityFunctions.mul(DensityFunctions.constant(0.083), thicknessModulator));
        return DensityFunctions.max(spaghettiWithThickness, thickness).clamp(-1.0, 1.0);
    }

    private static DensityFunction weirdScaledSampler(DensityFunction input, Holder<NormalNoise.NoiseParameters> noise, String mapperName) {
        DensityFunctions.WeirdScaledSampler.RarityValueMapper mapper =
                DensityFunctions.WeirdScaledSampler.RarityValueMapper.valueOf(mapperName);
        return DensityFunctions.weirdScaledSampler(input, noise, mapper);
    }

    private static DensityFunction scaleDF(DensityFunction df, double scale) {
        return scale == 1.0 ? df : DensityFunctions.mul(df, DensityFunctions.constant(scale));
    }

    private static DensityFunction get(HolderGetter<DensityFunction> df, ResourceLocation id) {
        Holder<DensityFunction> holder = df.getOrThrow(ResourceKeyUtil.df(id));
        return new DensityFunctions.HolderHolder(holder);
    }

    private static DensityFunction noiseGradientDensity(DensityFunction d1, DensityFunction d2) {
        DensityFunction mul = DensityFunctions.mul(d2, d1);
        return DensityFunctions.mul(DensityFunctions.constant(4.0), mul.quarterNegative());
    }

    private static DensityFunction yLimitedInterpolatable(DensityFunction y, DensityFunction value, int min, int max, int out) {
        return DensityFunctions.interpolated(
                DensityFunctions.rangeChoice(y, (double) min, (double) (max + 1), value, DensityFunctions.constant((double) out))
        );
    }

    private static DensityFunction postProcess(DensityFunction d) {
        DensityFunction blend = DensityFunctions.blendDensity(d);
        return DensityFunctions.mul(DensityFunctions.interpolated(blend), DensityFunctions.constant(0.64)).squeeze();
    }

    private static DensityFunction slideOverworld(boolean amplified, DensityFunction d) {
        return slide(d, -64, 384, amplified ? 16 : 80, amplified ? 0 : 64, -0.078125, 0, 24, amplified ? 0.4 : 0.1171875);
    }

    private static DensityFunction slide(DensityFunction d, int i, int j, int k, int l, double d1, int m, int n, double d2) {
        DensityFunction t = DensityFunctions.yClampedGradient(i + j - k, i + j - l, 1.0, 0.0);
        DensityFunction lerp = DensityFunctions.lerp(t, d1, d);
        DensityFunction t2 = DensityFunctions.yClampedGradient(i + m, i + n, 0.0, 1.0);
        return DensityFunctions.lerp(t2, d2, lerp);
    }
}
