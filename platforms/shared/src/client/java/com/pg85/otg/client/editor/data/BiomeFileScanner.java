package com.pg85.otg.client.editor.data;

import com.pg85.otg.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class BiomeFileScanner {

    private static final Logger LOG = LoggerFactory.getLogger(BiomeFileScanner.class);

    private static final List<String> BIOME_EXTENSIONS = List.of(
        ".bc", ".biome", ".bc.ini", ".biome.ini"
    );

    public record BiomeEntry(String name, Path path) implements Comparable<BiomeEntry> {
        @Override
        public int compareTo(BiomeEntry other) {
            return this.name.compareToIgnoreCase(other.name);
        }
    }

    public static List<BiomeEntry> scan(Path presetFolder) {
        Path biomesDir = presetFolder.resolve(Constants.BIOMES_FOLDER);
        if (!Files.isDirectory(biomesDir)) {
            biomesDir = presetFolder.resolve(Constants.LEGACY_WORLD_BIOMES_FOLDER);
            if (!Files.isDirectory(biomesDir)) {
                LOG.warn("No biomes directory found in {}", presetFolder);
                return List.of();
            }
        }

        List<BiomeEntry> entries = new ArrayList<>();
        try (var walk = Files.walk(biomesDir)) {
            walk.filter(Files::isRegularFile)
                .filter(BiomeFileScanner::isBiomeFile)
                .forEach(path -> {
                    String name = toBiomeName(path);
                    entries.add(new BiomeEntry(name, path));
                });
        } catch (IOException e) {
            LOG.error("Failed to scan biomes directory: {}", biomesDir, e);
        }

        Collections.sort(entries);
        LOG.info("Found {} biome files in {}", entries.size(), biomesDir);
        return entries;
    }

    public static Path getBiomesDirectory(Path presetFolder) {
        Path biomesDir = presetFolder.resolve(Constants.BIOMES_FOLDER);
        if (Files.isDirectory(biomesDir)) return biomesDir;
        Path legacy = presetFolder.resolve(Constants.LEGACY_WORLD_BIOMES_FOLDER);
        if (Files.isDirectory(legacy)) return legacy;
        return biomesDir;
    }

    private static boolean isBiomeFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals("biomeconfig.ini")) return true;
        return BIOME_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static String toBiomeName(Path path) {
        String fileName = path.getFileName().toString();
        for (String ext : List.of(".bc.ini", ".biome.ini", ".biome", ".bc")) {
            if (fileName.toLowerCase(Locale.ROOT).endsWith(ext)) {
                return fileName.substring(0, fileName.length() - ext.length());
            }
        }
        if (fileName.equalsIgnoreCase("BiomeConfig.ini")) {
            return path.getParent().getFileName().toString();
        }
        return fileName;
    }
}
