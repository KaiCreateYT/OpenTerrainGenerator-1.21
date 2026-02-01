package com.pg85.otg.dimensions;

import com.pg85.otg.config.settings.preset.DimensionSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.OTGLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DimensionDatapack {
    private static final String PACK_MCMETA = """
            {
              "pack": {
                "pack_format": 15,
                "description": "OTG Dynamic Dimensions"
              }
            }
            """;

    private final Path datapackPath;

    public DimensionDatapack(Path worldDatapacksPath) {
        this.datapackPath = worldDatapacksPath.resolve(Constants.MOD_ID_SHORT);
    }

    public void ensurePackMcmeta() throws IOException {
        Path packMcmeta = datapackPath.resolve("pack.mcmeta");
        if (!Files.exists(packMcmeta)) {
            Files.createDirectories(datapackPath);
            Files.writeString(packMcmeta, PACK_MCMETA);
        }
    }

    public void createDimensionFiles(DimensionInfo info, DimensionSettings settings) throws IOException {
        ensurePackMcmeta();

        // Create dimension_type JSON
        Path dimTypePath = datapackPath.resolve("data")
                .resolve(Constants.MOD_ID_SHORT)
                .resolve("dimension_type")
                .resolve(info.getName() + ".json");
        Files.createDirectories(dimTypePath.getParent());
        Files.writeString(dimTypePath, generateDimensionTypeJson(settings));

        // Create dimension JSON
        Path dimPath = datapackPath.resolve("data")
                .resolve(Constants.MOD_ID_SHORT)
                .resolve("dimension")
                .resolve(info.getName() + ".json");
        Files.createDirectories(dimPath.getParent());
        Files.writeString(dimPath, generateDimensionJson(info));

        OTGLog.info("Created datapack files for dimension %s", info.getName());
    }

    public void deleteDimensionFiles(String name) throws IOException {
        Path dimTypePath = datapackPath.resolve("data")
                .resolve(Constants.MOD_ID_SHORT)
                .resolve("dimension_type")
                .resolve(name + ".json");
        Path dimPath = datapackPath.resolve("data")
                .resolve(Constants.MOD_ID_SHORT)
                .resolve("dimension")
                .resolve(name + ".json");

        Files.deleteIfExists(dimTypePath);
        Files.deleteIfExists(dimPath);

        OTGLog.info("Deleted datapack files for dimension %s", name);
    }

    private String generateDimensionTypeJson(DimensionSettings settings) {
        return String.format("""
                {
                  "ultrawarm": %s,
                  "natural": %s,
                  "coordinate_scale": %s,
                  "has_skylight": %s,
                  "has_ceiling": %s,
                  "ambient_light": %s,
                  "piglin_safe": %s,
                  "bed_works": %s,
                  "respawn_anchor_works": %s,
                  "has_raids": %s,
                  "logical_height": %d,
                  "min_y": %d,
                  "height": %d,
                  "infiniburn": "%s",
                  "effects": "%s",
                  "monster_spawn_light_level": %d,
                  "monster_spawn_block_light_limit": %d
                }
                """,
                settings.isUltraWarm(),
                settings.isNatural(),
                settings.getCoordinateScale(),
                settings.isHasSkyLight(),
                settings.isHasCeiling(),
                settings.getAmbientLight(),
                settings.isPiglinSafe(),
                settings.isBedWorks(),
                settings.isRespawnAnchorWorks(),
                settings.isHasRaids(),
                settings.getLogicalHeight(),
                settings.getMinY(),
                settings.getHeight(),
                settings.getInfiniburn(),
                settings.getEffectsLocation().toLowerCase(),
                settings.getMonsterSpawnLightLimit(),
                settings.getMonsterSpawnLightLimit()
        );
    }

    private String generateDimensionJson(DimensionInfo info) {
        return String.format("""
                {
                  "type": "%s:%s",
                  "generator": {
                    "type": "%s:otg",
                    "preset": "%s",
                    "seed": %d
                  }
                }
                """,
                Constants.MOD_ID_SHORT, info.getName(),
                Constants.MOD_ID_SHORT,
                info.getPreset(),
                info.getSeed()
        );
    }
}
