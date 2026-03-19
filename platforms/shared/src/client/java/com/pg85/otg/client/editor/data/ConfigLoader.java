package com.pg85.otg.client.editor.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    public record LoadResult(
        List<PropertyValue> properties,
        List<String> rawLines
    ) {}

    public static LoadResult load(Path iniPath, Map<String, PropertyDefinition> definitions) {
        List<String> lines;
        try {
            lines = Files.readAllLines(iniPath);
        } catch (IOException e) {
            LOG.error("Failed to read config file: {}", iniPath, e);
            return null;
        }

        Map<String, PropertyValue> propertyMap = new LinkedHashMap<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("<")) continue;
            if (trimmed.contains("(")) continue;

            int colonIdx = trimmed.indexOf(':');
            if (colonIdx < 0) continue;

            String key = trimmed.substring(0, colonIdx).trim();
            String value = trimmed.substring(colonIdx + 1).trim();

            PropertyDefinition def = definitions.get(key);
            if (def == null) {
                def = new PropertyDefinition(key, PropertyType.STRING, PropertyCategory.UNCATEGORIZED,
                    value, null, null, null, null);
            }

            propertyMap.put(key, new PropertyValue(def, value));
        }

        for (var entry : definitions.entrySet()) {
            if (!propertyMap.containsKey(entry.getKey())) {
                PropertyDefinition def = entry.getValue();
                propertyMap.put(def.name(), new PropertyValue(def, def.defaultValue()));
            }
        }

        LOG.info("Loaded {} properties from {}", propertyMap.size(), iniPath.getFileName());
        return new LoadResult(new ArrayList<>(propertyMap.values()), lines);
    }
}
