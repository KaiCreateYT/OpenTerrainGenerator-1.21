package com.pg85.otg.config.settings.biome;

import com.pg85.otg.config.io.SettingsMap;
import com.pg85.otg.config.settingtype.Setting;
import com.pg85.otg.config.settingtype.Settings;
import com.pg85.otg.config.settings.ConfigSection;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class UndergroundBiomeSettings extends ConfigSection {

    private final boolean isUndergroundBiome;
    private final int undergroundMinY;
    private final int undergroundMaxY;
    private final int undergroundPriority;
    private final float minSurfaceTemperature;
    private final float maxSurfaceTemperature;
    private final float minSurfaceWetness;
    private final float maxSurfaceWetness;

    // Surface biome settings for underground biome control
    private final int undergroundBiomeStartOffset;
    private final List<String> allowedUndergroundBiomes;
    private final List<String> disallowedUndergroundBiomes;

    @Override
    public String getSectionName() {
        return "Underground Biome Settings";
    }

    @Override
    public String[] getSectionComment() {
        return new String[]{
            "Settings for 3D underground biome placement.",
            "Set IsUndergroundBiome: true to mark this biome as an underground biome.",
            "Underground biomes appear below surface biomes based on Y-range, priority, and surface biome conditions.",
            "",
            "For surface biomes: use UndergroundBiomeStartOffset, AllowedUndergroundBiomes, DisallowedUndergroundBiomes",
            "to control which underground biomes can appear below this surface biome."
        };
    }

    // === Underground biome identity ===

    public static final Setting<Boolean> IS_UNDERGROUND_BIOME = Settings.booleanSetting(
            "IsUndergroundBiome", false,
            t -> ((UndergroundBiomeSettings) t).isUndergroundBiome(),
            "Set to true to mark this biome as an underground biome.",
            "Underground biomes appear below surface biomes based on conditions defined here."
    );

    public static final Setting<Integer> UNDERGROUND_MIN_Y = Settings.intSetting(
            "UndergroundMinY", -64, -2048, 2048,
            t -> ((UndergroundBiomeSettings) t).getUndergroundMinY(),
            "Minimum Y coordinate where this underground biome can appear."
    );

    public static final Setting<Integer> UNDERGROUND_MAX_Y = Settings.intSetting(
            "UndergroundMaxY", 320, -2048, 2048,
            t -> ((UndergroundBiomeSettings) t).getUndergroundMaxY(),
            "Maximum Y coordinate where this underground biome can appear."
    );

    public static final Setting<Integer> UNDERGROUND_PRIORITY = Settings.intSetting(
            "UndergroundPriority", 10, 0, 1000,
            t -> ((UndergroundBiomeSettings) t).getUndergroundPriority(),
            "Priority for this underground biome. Lower value = higher priority.",
            "When multiple underground biomes match the same position, the one with lowest priority wins."
    );

    // === Conditions based on surface biome ===

    public static final Setting<Float> MIN_SURFACE_TEMPERATURE = Settings.floatSetting(
            "MinSurfaceTemperature", 0.0f, 0.0f, 2.0f,
            t -> ((UndergroundBiomeSettings) t).getMinSurfaceTemperature(),
            "Minimum BiomeTemperature of the surface biome above for this underground biome to appear."
    );

    public static final Setting<Float> MAX_SURFACE_TEMPERATURE = Settings.floatSetting(
            "MaxSurfaceTemperature", 2.0f, 0.0f, 2.0f,
            t -> ((UndergroundBiomeSettings) t).getMaxSurfaceTemperature(),
            "Maximum BiomeTemperature of the surface biome above for this underground biome to appear."
    );

    public static final Setting<Float> MIN_SURFACE_WETNESS = Settings.floatSetting(
            "MinSurfaceWetness", 0.0f, 0.0f, 1.0f,
            t -> ((UndergroundBiomeSettings) t).getMinSurfaceWetness(),
            "Minimum BiomeWetness of the surface biome above for this underground biome to appear."
    );

    public static final Setting<Float> MAX_SURFACE_WETNESS = Settings.floatSetting(
            "MaxSurfaceWetness", 1.0f, 0.0f, 1.0f,
            t -> ((UndergroundBiomeSettings) t).getMaxSurfaceWetness(),
            "Maximum BiomeWetness of the surface biome above for this underground biome to appear."
    );

    // === Surface biome control over underground ===

    public static final Setting<Integer> UNDERGROUND_BIOME_START_OFFSET = Settings.intSetting(
            "UndergroundBiomeStartOffset", 8, 0, 256,
            t -> ((UndergroundBiomeSettings) t).getUndergroundBiomeStartOffset(),
            "How many blocks below the estimated surface height underground biomes start.",
            "Only applies to surface biomes (non-underground). Default: 8."
    );

    public static final Setting<List<String>> ALLOWED_UNDERGROUND_BIOMES = Settings.stringListSetting(
            "AllowedUndergroundBiomes", new String[]{},
            t -> ((UndergroundBiomeSettings) t).getAllowedUndergroundBiomes(),
            "If set, ONLY these underground biomes can appear below this surface biome.",
            "Leave empty to allow all underground biomes that match conditions.",
            "Example: AllowedUndergroundBiomes: LushCaves, DripstoneCaves"
    );

    public static final Setting<List<String>> DISALLOWED_UNDERGROUND_BIOMES = Settings.stringListSetting(
            "DisallowedUndergroundBiomes", new String[]{},
            t -> ((UndergroundBiomeSettings) t).getDisallowedUndergroundBiomes(),
            "Underground biomes listed here will NEVER appear below this surface biome.",
            "Example: DisallowedUndergroundBiomes: DeepDark"
    );

    // === Factory method ===

    public static UndergroundBiomeSettings getUndergroundBiomeSettings(SettingsMap reader) {
        UndergroundBiomeSettingsBuilder builder = UndergroundBiomeSettings.builder();

        builder.isUndergroundBiome(reader.getSetting(IS_UNDERGROUND_BIOME));
        builder.undergroundMinY(reader.getSetting(UNDERGROUND_MIN_Y));
        builder.undergroundMaxY(reader.getSetting(UNDERGROUND_MAX_Y));
        builder.undergroundPriority(reader.getSetting(UNDERGROUND_PRIORITY));
        builder.minSurfaceTemperature(reader.getSetting(MIN_SURFACE_TEMPERATURE));
        builder.maxSurfaceTemperature(reader.getSetting(MAX_SURFACE_TEMPERATURE));
        builder.minSurfaceWetness(reader.getSetting(MIN_SURFACE_WETNESS));
        builder.maxSurfaceWetness(reader.getSetting(MAX_SURFACE_WETNESS));
        builder.undergroundBiomeStartOffset(reader.getSetting(UNDERGROUND_BIOME_START_OFFSET));
        builder.allowedUndergroundBiomes(reader.getSetting(ALLOWED_UNDERGROUND_BIOMES));
        builder.disallowedUndergroundBiomes(reader.getSetting(DISALLOWED_UNDERGROUND_BIOMES));

        return builder.build();
    }
}
