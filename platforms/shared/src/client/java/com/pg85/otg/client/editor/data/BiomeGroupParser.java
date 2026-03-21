package com.pg85.otg.client.editor.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BiomeGroupParser {

    private static final Logger LOG = LoggerFactory.getLogger(BiomeGroupParser.class);

    private BiomeGroupParser() {}

    public static List<BiomeGroupData> parse(List<String> rawLines) {
        List<BiomeGroupData> groups = new ArrayList<>();

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("BiomeGroup(") || !trimmed.endsWith(")")) {
                continue;
            }

            String inner = trimmed.substring("BiomeGroup(".length(), trimmed.length() - 1);
            String[] args = inner.split(",");
            if (args.length < 3) {
                LOG.warn("Malformed BiomeGroup line (too few args): {}", trimmed);
                continue;
            }

            for (int i = 0; i < args.length; i++) {
                args[i] = args[i].trim();
            }

            String name = args[0];
            int generationDepth;
            int rarity;
            try {
                generationDepth = Integer.parseInt(args[1]);
                rarity = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                LOG.warn("Malformed BiomeGroup line (bad depth/rarity): {}", trimmed);
                continue;
            }

            double minTemp = 0.0;
            double maxTemp = 0.0;
            int biomeEndIndex = args.length;

            // Check if last 2 args are doubles (temperature range)
            if (args.length >= 5) {
                try {
                    double lastTwo = Double.parseDouble(args[args.length - 1]);
                    double lastOne = Double.parseDouble(args[args.length - 2]);
                    minTemp = lastOne;
                    maxTemp = lastTwo;
                    biomeEndIndex = args.length - 2;
                } catch (NumberFormatException ignored) {
                    // Not temps, all remaining args are biome names
                }
            }

            List<String> biomes = new ArrayList<>();
            for (int i = 3; i < biomeEndIndex; i++) {
                if (!args[i].isEmpty()) {
                    biomes.add(args[i]);
                }
            }

            groups.add(new BiomeGroupData(name, generationDepth, rarity, biomes, minTemp, maxTemp));
        }

        return groups;
    }
}
