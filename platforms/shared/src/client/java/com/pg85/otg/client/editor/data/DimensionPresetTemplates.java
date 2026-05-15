package com.pg85.otg.client.editor.data;

import com.pg85.otg.OTG;
import com.pg85.otg.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lists DimensionPreset templates for the wizard.
 *
 * Templates are sourced from {@code {otgRoot}/DimensionPresets/} — every existing
 * preset directory shows up as a usable template. The first entry is always
 * "Blank Minimal" which points at {@code DefaultPreset} (the only preset
 * guaranteed to exist after {@code OTGEngine.UnpackDefaultPresetAndExamples()}).
 *
 * We don't synthesize a config from scratch because a working preset requires
 * a full 600+ line .ini plus at least one biome — easier to copy a known-good
 * baseline and let the user edit it afterwards.
 */
public final class DimensionPresetTemplates {

    private static final Logger LOG = LoggerFactory.getLogger(DimensionPresetTemplates.class);
    private static final String BLANK_LABEL = "Blank Minimal (from DefaultPreset)";

    public record Template(String label, Path sourceDir) {}

    private DimensionPresetTemplates() {}

    public static List<Template> listTemplates() {
        List<Template> result = new ArrayList<>();
        Path otgRoot = OTG.getEngine().getOTGRootFolder();
        Path presetsDir = otgRoot.resolve(Constants.DIMENSION_PRESETS_FOLDER);

        Path defaultPreset = presetsDir.resolve(Constants.DEFAULT_PRESET_NAME);
        if (Files.isDirectory(defaultPreset)) {
            result.add(new Template(BLANK_LABEL, defaultPreset));
        }

        if (!Files.isDirectory(presetsDir)) {
            return result;
        }

        try (Stream<Path> dirs = Files.list(presetsDir)) {
            dirs.filter(Files::isDirectory)
                .filter(p -> Files.exists(p.resolve(Constants.DIMENSION_PRESET_CONFIG_FILE)))
                .sorted()
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    if (name.equals(Constants.DEFAULT_PRESET_NAME)) return;
                    result.add(new Template(name, p));
                });
        } catch (IOException e) {
            LOG.warn("Failed to list DimensionPreset templates: {}", e.getMessage());
        }

        return result;
    }
}
