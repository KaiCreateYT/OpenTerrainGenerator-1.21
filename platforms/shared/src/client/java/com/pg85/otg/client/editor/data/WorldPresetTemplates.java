package com.pg85.otg.client.editor.data;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Baked-in WorldPreset YAML templates for the wizard Step 0.
 *
 * Templates are sourced from the OTG root folder's WorldPresets/ directory,
 * which is populated on first startup by {@code OTGEngine.UnpackDefaultPresetAndExamples()}
 * from the jar's {@code resources/WorldPresets/} entries. This means the
 * wizard sees the same files users see on disk — no classpath resource lookup needed.
 */
public final class WorldPresetTemplates {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetTemplates.class);

    public record Template(String label, Path sourcePath) {}

    private WorldPresetTemplates() {}

    /** Returns "Blank" as first entry, then all shipped YAMLs from WorldPresets/. */
    public static List<Template> listTemplates() {
        List<Template> result = new ArrayList<>();
        result.add(new Template("Blank", null));

        Path otgRoot = OTG.getEngine().getOTGRootFolder();
        Path worldPresetsDir = otgRoot.resolve(Constants.WORLD_PRESETS_FOLDER);
        if (!Files.isDirectory(worldPresetsDir)) {
            return result;
        }

        try (Stream<Path> files = Files.list(worldPresetsDir)) {
            files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".yaml") || n.endsWith(".yml");
            }).sorted().forEach(p -> {
                String stem = stripExtension(p.getFileName().toString());
                result.add(new Template(stem, p));
            });
        } catch (IOException e) {
            LOG.warn("Failed to list WorldPresets templates: {}", e.getMessage());
        }

        return result;
    }

    /** Creates a config from the given template (null sourcePath = blank). */
    public static WorldPresetConfig create(Template template) {
        if (template.sourcePath() == null) {
            return blank();
        }
        WorldPresetConfig config = WorldPresetYamlIO.load(template.sourcePath());
        if (config == null) {
            LOG.warn("Template source missing or unreadable: {}, falling back to blank",
                template.sourcePath());
            return blank();
        }
        return config;
    }

    public static WorldPresetConfig blank() {
        WorldPresetConfig config = new WorldPresetConfig();
        config.Version = 1;
        config.DisplayName = "";
        return config;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
