package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Scans {otgRoot}/WorldPresets/ for *.yaml / *.yml files and loads each one.
 * Corrupted YAMLs are surfaced as entries with valid=false and config=null.
 */
public final class WorldPresetFileScanner {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetFileScanner.class);

    private WorldPresetFileScanner() {}

    public record WorldPresetEntry(
        String displayName,
        Path path,
        @Nullable WorldPresetConfig config,
        boolean valid
    ) {}

    /**
     * Scan otgRoot/WorldPresets/ and load every YAML file.
     * @return entries sorted by displayName (case-insensitive)
     */
    public static List<WorldPresetEntry> scan(Path otgRoot) {
        List<WorldPresetEntry> entries = new ArrayList<>();
        Path worldPresetsDir = otgRoot.resolve(Constants.WORLD_PRESETS_FOLDER);
        if (!Files.isDirectory(worldPresetsDir)) {
            LOG.info("No WorldPresets folder at {}", worldPresetsDir);
            return entries;
        }

        try (Stream<Path> files = Files.list(worldPresetsDir)) {
            files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".yaml") || n.endsWith(".yml");
            }).forEach(p -> entries.add(toEntry(p)));
        } catch (IOException e) {
            LOG.error("Failed to scan WorldPresets folder: {}", e.getMessage());
        }

        entries.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return entries;
    }

    private static WorldPresetEntry toEntry(Path yamlFile) {
        WorldPresetConfig config = WorldPresetYamlIO.load(yamlFile);
        if (config == null) {
            return new WorldPresetEntry("(invalid) " + yamlFile.getFileName(), yamlFile, null, false);
        }
        String displayName = (config.DisplayName == null || config.DisplayName.isBlank())
            ? "(no name) " + yamlFile.getFileName()
            : config.DisplayName;
        return new WorldPresetEntry(displayName, yamlFile, config, true);
    }
}
