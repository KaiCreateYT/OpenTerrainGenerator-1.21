package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.loader.WorldPresetConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Load and save WorldPreset YAML files. Reuses the existing Jackson mapper
 * via WorldPresetConfigLoader for loading; uses WorldPresetConfig.toYamlString()
 * for saving.
 */
public final class WorldPresetYamlIO {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetYamlIO.class);

    private WorldPresetYamlIO() {}

    /**
     * Load a WorldPreset YAML from disk.
     * @return parsed config, or null on parse/read failure
     */
    public static @Nullable WorldPresetConfig load(Path yamlFile) {
        return WorldPresetConfigLoader.fromFile(yamlFile.toFile());
    }

    /**
     * Write a WorldPreset YAML to disk. Overwrites existing file.
     * @return true on success
     */
    public static boolean save(Path yamlFile, WorldPresetConfig config) {
        String yaml = config.toYamlString();
        if (yaml == null) {
            LOG.error("toYamlString returned null for {}", yamlFile.getFileName());
            return false;
        }
        try {
            Files.createDirectories(yamlFile.getParent());
            Files.writeString(yamlFile, yaml);
            LOG.info("Saved WorldPreset YAML: {}", yamlFile.getFileName());
            return true;
        } catch (IOException e) {
            LOG.error("Failed to write WorldPreset YAML {}: {}", yamlFile.getFileName(), e.getMessage());
            return false;
        }
    }
}
