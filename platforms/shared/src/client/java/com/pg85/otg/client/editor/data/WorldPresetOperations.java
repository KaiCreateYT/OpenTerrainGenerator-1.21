package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.shared.registry.WorldPresetRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CRUD for WorldPreset YAML files.
 * File names derived from DisplayName via WorldPresetRegistrar.normalizeId().
 */
public final class WorldPresetOperations {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetOperations.class);

    private WorldPresetOperations() {}

    /**
     * Create a blank WorldPreset YAML with only DisplayName set.
     * @return path to the new file, or null on failure
     */
    public static Path newBlank(Path otgRoot, String displayName) {
        Path target = resolveUniquePath(otgRoot, displayName);
        WorldPresetConfig config = new WorldPresetConfig();
        config.Version = 1;
        config.DisplayName = displayName;
        if (WorldPresetYamlIO.save(target, config)) {
            return target;
        }
        return null;
    }

    /**
     * Clone an existing YAML file, patching the DisplayName.
     * @return path to the cloned file, or null on failure
     */
    public static Path cloneFrom(Path sourceYaml, Path otgRoot, String newDisplayName) {
        WorldPresetConfig sourceConfig = WorldPresetYamlIO.load(sourceYaml);
        if (sourceConfig == null) {
            LOG.error("Cannot clone — source YAML failed to load: {}", sourceYaml);
            return null;
        }
        WorldPresetConfig clone = sourceConfig.clone();
        clone.DisplayName = newDisplayName;
        Path target = resolveUniquePath(otgRoot, newDisplayName);
        if (WorldPresetYamlIO.save(target, clone)) {
            return target;
        }
        return null;
    }

    /**
     * Delete a WorldPreset YAML file.
     * @return true on success
     */
    public static boolean delete(Path yamlFile) {
        try {
            Files.deleteIfExists(yamlFile);
            LOG.info("Deleted WorldPreset YAML: {}", yamlFile.getFileName());
            return true;
        } catch (IOException e) {
            LOG.error("Failed to delete {}: {}", yamlFile.getFileName(), e.getMessage());
            return false;
        }
    }

    /**
     * Resolve a unique path for a new YAML based on the display name.
     * If {normalized}.yaml exists, appends _1, _2, ... until a free name.
     */
    private static Path resolveUniquePath(Path otgRoot, String displayName) {
        String base = WorldPresetRegistrar.normalizeId(displayName);
        if (base == null || base.isEmpty()) base = "worldpreset";
        Path dir = otgRoot.resolve(Constants.WORLD_PRESETS_FOLDER);
        Path candidate = dir.resolve(base + ".yaml");
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(base + "_" + counter + ".yaml");
            counter++;
        }
        return candidate;
    }
}
