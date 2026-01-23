package com.pg85.otg.config.settings.biome;

import com.pg85.otg.config.io.SettingsMap;
import com.pg85.otg.config.settingtype.DoubleArraySetting;
import com.pg85.otg.config.settingtype.Setting;
import com.pg85.otg.config.settingtype.Settings;
import com.pg85.otg.config.settings.ConfigSection;
import com.pg85.otg.config.settings.preset.TerrainSettings;
import com.pg85.otg.constants.Constants;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class BiomeTerrainSettings extends ConfigSection {
    private final TerrainSettings parent;
    private final float biomeHeight;
    private final float biomeVolatility;
    private final int smoothRadius;
    private final int CHCSmoothRadius;
    private final double maxAverageHeight;
    private final double maxAverageDepth;
    private final double volatility1;
    private final double volatility2;
    private final double volatilityWeight1;
    private final double volatilityWeight2;
    private final boolean disableBiomeHeight;
    private final double[] customHeightControl;
    @Override
    public String getSectionName() {
        return "Biome Terrain Settings";
    }

    public static final Setting<Integer> SMOOTH_RADIUS = Settings.intSetting(
            "SmoothRadius", 2, 0, 32,
            t -> ((BiomeTerrainSettings) t).getSmoothRadius(),
            "Smooth radius between biomes. Must be between 0 and 32, inclusive. The resulting",
            "smooth radius seems to be  (thisSmoothRadius + 1 + smoothRadiusOfBiomeOnOtherSide) * 4 .",
            "So if two biomes next to each other have both a smooth radius of 2, the",
            "resulting smooth area will be (2 + 1 + 2) * 4 = 20 blocks wide."
    );
    public static final Setting<Integer> CUSTOM_HEIGHT_CONTROL_SMOOTH_RADIUS = Settings.intSetting(
            "CustomHeightControlSmoothRadius", 2, 0, 32,
            t -> ((BiomeTerrainSettings) t).getCHCSmoothRadius(),
            "Works the same way as SmoothRadius but only acts on CustomHeightControl.",
            "Must be between 0 and 32, inclusive.",
            "Does nothing if Custom Height Control smoothing is not enabled in the world config."
    );
    public static final Setting<Double> VOLATILITY_1 = Settings.doubleSetting(
            "Volatility1", 0, -10000, 1000,
            t -> ((BiomeTerrainSettings) t).getVolatility1(),
            "Another type of noise. This noise is independent from biomes.",
            "The larger the values the more chaotic/volatile landscape generation becomes.",
            "Setting the values to negative will have the opposite effect and make landscape generation calmer/gentler."
    );
    public static final Setting<Double> VOLATILITY_2 = Settings.doubleSetting(
            "Volatility2", 0, -10000, 1000,
            t -> ((BiomeTerrainSettings) t).getVolatility2()
    );
    public static final Setting<Double> VOLATILITY_WEIGHT_1 = Settings.doubleSetting(
            "VolatilityWeight1", 0.5, -1000, 1000,
            t -> ((BiomeTerrainSettings) t).getVolatilityWeight1(),
            "Adjust the weight of the corresponding volatility settings.",
            "This allows you to change how prevalent you want either of the volatility settings to be in the terrain."
    );
    public static final Setting<Double> VOLATILITY_WEIGHT_2 = Settings.doubleSetting(
            "VolatilityWeight2", 0.45, -1000, 1000,
            t -> ((BiomeTerrainSettings) t).getVolatilityWeight2()
    );
    public static final Setting<Double> MAX_AVERAGE_HEIGHT = Settings.doubleSetting(
            "MaxAverageHeight", 0, -1000, 1000,
            t -> ((BiomeTerrainSettings) t).getMaxAverageHeight(),
            "If this value is greater than 0, then it will affect how much, on average,",
            "the terrain will rise before leveling off when it begins to increase in elevation.",
            "If the value is less than 0, then it will cause the terrain to either increase to a lower height",
            "before leveling out or decrease in height if the value is a large enough negative."
    );
    public static final Setting<Double> MAX_AVERAGE_DEPTH = Settings.doubleSetting(
            "MaxAverageDepth", 0, -1000, 1000,
            t -> ((BiomeTerrainSettings) t).getMaxAverageDepth(),
            "If this value is greater than 0, then it will affect how much, on average,",
            "the terrain (usually at the ottom of the ocean) will fall before leveling off when it begins to decrease in elevation. ",
            "If the value is less than 0, then it will cause the terrain to either fall to a lesser depth",
            "before leveling out or increase in height if the value is a large enough negative."
    );
    public static final Setting<Float> BIOME_HEIGHT = Settings.floatSetting(
            "BiomeHeight", 0.1f, -10, 10,
            t -> ((BiomeTerrainSettings) t).getBiomeHeight(),
            "BiomeHeight defines how much height will be added during terrain generation",
            "Must be between -10.0 and 10.0",
            "Value 0.0 is equivalent to half of map height with all other settings at defaults."
    );
    public static final Setting<Float> BIOME_VOLATILITY = Settings.floatSetting(
            "BiomeVolatility", 0.3f, -1000, 1000,
            t -> ((BiomeTerrainSettings) t).getBiomeVolatility(),
            "Biome volatility."
    );
    public static final Setting<Boolean> DISABLE_BIOME_HEIGHT = Settings.booleanSetting(
            "DisableBiomeHeight", false,
            t -> ((BiomeTerrainSettings) t).isDisableBiomeHeight(),
            "Disable all noises except Volatility1 and Volatility2. Also disable default block chance from height."
    );
    public static final Setting<double[]> CUSTOM_HEIGHT_CONTROL = new DoubleArraySetting(
            "CustomHeightControl",
            t -> ((BiomeTerrainSettings) t).getCustomHeightControl(),
            "List of custom height factors, 17 double entries, each controls about 7",
            "blocks height, starting at the bottom of the world. Positive entry - larger chance of spawn blocks, negative - smaller",
            "Values which affect your configuration may be found only experimentally. Values may be very big, like ~3000.0 depends from height",
            "Example:",
            "  CustomHeightControl:0.0,-2500.0,0.0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0",
            "Makes empty layer above bedrock layer. "
    );


    public static BiomeTerrainSettings getBiomeTerrainSettings(SettingsMap reader, TerrainSettings parent, int worldHeight) {
        BiomeTerrainSettingsBuilder builder = BiomeTerrainSettings.builder();

        builder.parent(parent);
        builder.biomeHeight(reader.getSetting(BIOME_HEIGHT));
        builder.biomeVolatility(reader.getSetting(BIOME_VOLATILITY));
        builder.smoothRadius(reader.getSetting(SMOOTH_RADIUS));
        builder.CHCSmoothRadius(reader.getSetting(CUSTOM_HEIGHT_CONTROL_SMOOTH_RADIUS));
        builder.maxAverageHeight(reader.getSetting(MAX_AVERAGE_HEIGHT));
        builder.maxAverageDepth(reader.getSetting(MAX_AVERAGE_DEPTH));
        builder.volatility1(reader.getSetting(VOLATILITY_1));
        builder.volatility2(reader.getSetting(VOLATILITY_2));
        builder.volatilityWeight1(reader.getSetting(VOLATILITY_WEIGHT_1));
        builder.volatilityWeight2(reader.getSetting(VOLATILITY_WEIGHT_2));
        int configVersion = reader.getVersion();
        if (configVersion < 2) {
            // In older configs, the values were stored as negative values and then converted
            builder.volatility1(builder.volatility1 < 0.00D ? 1.0D / Math.abs(builder.volatility1) : builder.volatility1 + 1.0D);
            builder.volatility2(builder.volatility2 < 0.00D ? 1.0D / Math.abs(builder.volatility2) : builder.volatility2 + 1.0D);
            builder.volatilityWeight1((builder.volatilityWeight1 - 0.5D) * 24.0D);
            builder.volatilityWeight2((builder.volatilityWeight2 - 0.5D) * 24.0D);
        }

        builder.disableBiomeHeight(reader.getSetting(DISABLE_BIOME_HEIGHT));
        builder.customHeightControl(builder.readHeightSettings(reader, worldHeight));

        return builder.fixSettings().build();
    }

    public static class BiomeTerrainSettingsBuilder {
        protected double[] readHeightSettings(SettingsMap settings, int worldHeight)
        {
            double[] heightMatrix = new double[worldHeight / Constants.PIECE_Y_SIZE + 1];
            double[] keys = settings.getSetting(CUSTOM_HEIGHT_CONTROL);
            for (int i = 0; i < heightMatrix.length && i < keys.length; i++)
            {
                heightMatrix[i] = keys[i];
            }
            return heightMatrix;
        }
        public BiomeTerrainSettingsBuilder fixSettings() {
            checkVolatility1();
            checkVolatility2();
            return this;
        }
        private void checkVolatility1() {
            if (this.volatility1 < 0.0D) {
                this.volatility1 = 1.0D / (Math.abs(this.volatility1) + 1.0D);
            }
        }
        private void checkVolatility2() {
            if (this.volatility2 < 0.0D) {
                this.volatility2 = 1.0D / (Math.abs(this.volatility2) + 1.0D);
            }
        }
    }

}