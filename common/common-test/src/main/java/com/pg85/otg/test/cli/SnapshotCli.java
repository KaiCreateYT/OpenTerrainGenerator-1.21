package com.pg85.otg.test.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.test.biome.TestBiome;
import com.pg85.otg.test.biome.TestBiomeProvider;
import com.pg85.otg.test.preset.TestPresetLoader;
import com.pg85.otg.test.snapshot.SnapshotModel;
import com.pg85.otg.test.snapshot.TerrainSnapshotComparator;
import com.pg85.otg.test.snapshot.TerrainSnapshotComparator.ComparisonResult;
import com.pg85.otg.test.snapshot.TerrainSnapshotComparator.SectionDiff;
import com.pg85.otg.test.snapshot.TerrainSnapshotComparator.ValueDiff;
import com.pg85.otg.test.snapshot.TerrainSnapshotGenerator;
import com.pg85.otg.util.gen.OTGWorldInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Command-line interface for terrain snapshot generation and comparison.
 *
 * Commands:
 * - generate --seed <seed> --output <file.json> [--preset <name>] [--otg-root <path>]
 * - compare --baseline <file.json> --current <file.json> [--verbose] [--fail-threshold <percent>]
 * - verify --seed <seed> --baseline <file.json> [--preset <name>] [--otg-root <path>]
 *
 * Exit codes:
 * - 0: Success (identical or within threshold)
 * - 1: Differences found (above threshold)
 * - 2: Error (invalid arguments, file not found, etc.)
 */
public class SnapshotCli {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_DIFFERS = 1;
    private static final int EXIT_ERROR = 2;

    private static final int DEFAULT_MAX_DIFFS_PER_SECTION = 10;
    private static final String DEFAULT_OTG_ROOT = "config/OpenTerrainGenerator";
    private static final String DEFAULT_PRESET = "DefaultPreset";
    private static final String VERSION = "0.2.0";

    // Default chunk region: 17x17 chunks (-8,-8 to 8,8)
    private static final int DEFAULT_FROM_CHUNK = -8;
    private static final int DEFAULT_TO_CHUNK = 8;

    private final ObjectMapper mapper;

    public SnapshotCli() {
        this.mapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(EXIT_ERROR);
        }

        SnapshotCli cli = new SnapshotCli();
        String command = args[0];

        int exitCode;
        switch (command) {
            case "generate":
                exitCode = cli.runGenerate(args);
                break;
            case "compare":
                exitCode = cli.runCompare(args);
                break;
            case "verify":
                exitCode = cli.runVerify(args);
                break;
            case "--help":
            case "-h":
                printUsage();
                exitCode = EXIT_SUCCESS;
                break;
            default:
                System.err.println("Unknown command: " + command);
                printUsage();
                exitCode = EXIT_ERROR;
                break;
        }

        System.exit(exitCode);
    }

    private static void printUsage() {
        System.out.println("Usage: SnapshotCli <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  generate --seed <seed> --output <file.json> [--preset <name>] [--otg-root <path>]");
        System.out.println("      Generate a terrain snapshot for the given seed");
        System.out.println();
        System.out.println("  compare --baseline <file.json> --current <file.json> [--verbose] [--fail-threshold <percent>]");
        System.out.println("      Compare two snapshot files");
        System.out.println();
        System.out.println("  verify --seed <seed> --baseline <file.json> [--preset <name>] [--otg-root <path>] [--verbose] [--fail-threshold <percent>]");
        System.out.println("      Generate snapshot and compare against baseline");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --seed <n>            World seed (default: 12345)");
        System.out.println("  --preset <name>       Preset name (default: DefaultPreset)");
        System.out.println("  --otg-root <path>     OTG config directory (default: config/OpenTerrainGenerator)");
        System.out.println("  --verbose             Show all differences (default: max 10 per section)");
        System.out.println("  --fail-threshold <n>  Fail only if difference exceeds n% (default: 0)");
    }

    /**
     * Runs the generate command.
     */
    public int runGenerate(String[] args) {
        long seed = 12345;
        String output = null;
        String presetName = DEFAULT_PRESET;
        String otgRoot = DEFAULT_OTG_ROOT;

        // Parse arguments
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--seed":
                    if (i + 1 < args.length) {
                        try {
                            seed = Long.parseLong(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid seed value: " + args[i]);
                            return EXIT_ERROR;
                        }
                    }
                    break;
                case "--output":
                    if (i + 1 < args.length) {
                        output = args[++i];
                    }
                    break;
                case "--preset":
                    if (i + 1 < args.length) {
                        presetName = args[++i];
                    }
                    break;
                case "--otg-root":
                    if (i + 1 < args.length) {
                        otgRoot = args[++i];
                    }
                    break;
            }
        }

        if (output == null) {
            System.err.println("Error: --output is required");
            return EXIT_ERROR;
        }

        System.out.println("Generating snapshot...");
        System.out.println("  Seed: " + seed);
        System.out.println("  Preset: " + presetName);
        System.out.println("  OTG Root: " + otgRoot);

        try {
            SnapshotModel snapshot = generateSnapshot(seed, presetName, otgRoot);

            // Write to file
            File outputFile = new File(output);
            outputFile.getParentFile().mkdirs();
            mapper.writeValue(outputFile, snapshot);

            System.out.println("Snapshot written to: " + output);
            System.out.println("  Region: chunks (" + DEFAULT_FROM_CHUNK + "," + DEFAULT_FROM_CHUNK +
                             ") to (" + DEFAULT_TO_CHUNK + "," + DEFAULT_TO_CHUNK + ")");
            System.out.println("  Heightmap entries: " + snapshot.heightmap.data.size());
            System.out.println("  Slices: " + snapshot.slices.size());

            return EXIT_SUCCESS;
        } catch (Exception e) {
            System.err.println("Error generating snapshot: " + e.getMessage());
            e.printStackTrace();
            return EXIT_ERROR;
        }
    }

    /**
     * Runs the compare command.
     */
    public int runCompare(String[] args) {
        String baselinePath = null;
        String currentPath = null;
        boolean verbose = false;
        double failThreshold = 0.0;

        // Parse arguments
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--baseline":
                    if (i + 1 < args.length) {
                        baselinePath = args[++i];
                    }
                    break;
                case "--current":
                    if (i + 1 < args.length) {
                        currentPath = args[++i];
                    }
                    break;
                case "--verbose":
                    verbose = true;
                    break;
                case "--fail-threshold":
                    if (i + 1 < args.length) {
                        try {
                            failThreshold = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid threshold value: " + args[i]);
                            return EXIT_ERROR;
                        }
                    }
                    break;
            }
        }

        if (baselinePath == null || currentPath == null) {
            System.err.println("Error: --baseline and --current are required");
            return EXIT_ERROR;
        }

        // Load snapshots
        SnapshotModel baseline;
        SnapshotModel current;

        try {
            baseline = loadSnapshot(baselinePath);
        } catch (IOException e) {
            System.err.println("Error loading baseline: " + e.getMessage());
            return EXIT_ERROR;
        }

        try {
            current = loadSnapshot(currentPath);
        } catch (IOException e) {
            System.err.println("Error loading current: " + e.getMessage());
            return EXIT_ERROR;
        }

        return compareSnapshots(baseline, current, verbose, failThreshold);
    }

    /**
     * Runs the verify command - generates snapshot and compares against baseline.
     */
    public int runVerify(String[] args) {
        long seed = 12345;
        String baselinePath = null;
        String presetName = DEFAULT_PRESET;
        String otgRoot = DEFAULT_OTG_ROOT;
        boolean verbose = false;
        double failThreshold = 0.0;

        // Parse arguments
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--seed":
                    if (i + 1 < args.length) {
                        try {
                            seed = Long.parseLong(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid seed value: " + args[i]);
                            return EXIT_ERROR;
                        }
                    }
                    break;
                case "--baseline":
                    if (i + 1 < args.length) {
                        baselinePath = args[++i];
                    }
                    break;
                case "--preset":
                    if (i + 1 < args.length) {
                        presetName = args[++i];
                    }
                    break;
                case "--otg-root":
                    if (i + 1 < args.length) {
                        otgRoot = args[++i];
                    }
                    break;
                case "--verbose":
                    verbose = true;
                    break;
                case "--fail-threshold":
                    if (i + 1 < args.length) {
                        try {
                            failThreshold = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid threshold value: " + args[i]);
                            return EXIT_ERROR;
                        }
                    }
                    break;
            }
        }

        if (baselinePath == null) {
            System.err.println("Error: --baseline is required");
            return EXIT_ERROR;
        }

        System.out.println("Verifying terrain generation...");
        System.out.println("  Seed: " + seed);
        System.out.println("  Preset: " + presetName);
        System.out.println("  Baseline: " + baselinePath);

        // Load baseline
        SnapshotModel baseline;
        try {
            baseline = loadSnapshot(baselinePath);
        } catch (IOException e) {
            System.err.println("Error loading baseline: " + e.getMessage());
            return EXIT_ERROR;
        }

        // Generate current snapshot
        SnapshotModel current;
        try {
            current = generateSnapshot(seed, presetName, otgRoot);
        } catch (Exception e) {
            System.err.println("Error generating snapshot: " + e.getMessage());
            e.printStackTrace();
            return EXIT_ERROR;
        }

        System.out.println("Comparing...");
        return compareSnapshots(baseline, current, verbose, failThreshold);
    }

    /**
     * Generates a terrain snapshot for the given parameters.
     */
    private SnapshotModel generateSnapshot(long seed, String presetName, String otgRoot) throws Exception {
        Path otgRootPath = Paths.get(otgRoot);

        // Initialize headless mode with OTG engine
        TestPresetLoader.initHeadless(otgRootPath);

        // Load the specified preset
        Preset preset = TestPresetLoader.loadPreset(otgRootPath, presetName);
        if (preset == null) {
            // List available presets
            List<Preset> available = TestPresetLoader.loadPresets(otgRootPath);
            StringBuilder sb = new StringBuilder("Preset not found: " + presetName);
            if (!available.isEmpty()) {
                sb.append("\nAvailable presets:");
                for (Preset p : available) {
                    sb.append("\n  - ").append(p.getFolderName());
                }
            } else {
                sb.append("\nNo presets found in: ").append(otgRootPath.resolve("Presets"));
            }
            throw new IllegalArgumentException(sb.toString());
        }

        System.out.println("  Loaded preset: " + preset.getFolderName());

        // Get world info from preset
        OTGWorldInfo worldInfo = preset.getPresetConfig().getWorldInfo();
        System.out.println("  World height: " + worldInfo.minY() + " to " + worldInfo.maxY());

        // Build IBiome array from preset's BiomeConfigs
        List<BiomeConfig> biomeConfigs = preset.getBiomeConfigList();
        System.out.println("  Biomes: " + biomeConfigs.size());

        // Assign OTG biome IDs (LocalPresetLoader doesn't do this - only platform loaders do)
        // ID 0 is reserved for ocean, start from 1
        int currentId = 1;
        for (BiomeConfig bc : biomeConfigs) {
            bc.setOTGBiomeId(currentId++);
        }
        int maxBiomeId = currentId - 1;

        // Create IBiome array indexed by biome ID
        IBiome[] biomes = new IBiome[maxBiomeId + 1];
        int[] availableBiomeIds = new int[biomeConfigs.size()];
        int idx = 0;
        for (BiomeConfig bc : biomeConfigs) {
            int id = bc.getOTGBiomeID().id();
            float temperature = bc.getVisualSettings().getBiomeTemperature();
            biomes[id] = new TestBiome(bc, temperature);
            availableBiomeIds[idx++] = id;
        }

        // Create biome provider with seed-based selection
        TestBiomeProvider biomeProvider = new TestBiomeProvider(seed, availableBiomeIds);

        // Create generator and generate snapshot
        TerrainSnapshotGenerator generator = new TerrainSnapshotGenerator(
                preset,
                biomeProvider,
                biomes,
                worldInfo,
                seed,
                VERSION
        );

        return generator.generateSnapshot(
                DEFAULT_FROM_CHUNK, DEFAULT_FROM_CHUNK,
                DEFAULT_TO_CHUNK, DEFAULT_TO_CHUNK
        );
    }

    /**
     * Compares two snapshots and prints results.
     */
    private int compareSnapshots(SnapshotModel baseline, SnapshotModel current,
                                  boolean verbose, double failThreshold) {
        TerrainSnapshotComparator comparator = new TerrainSnapshotComparator();
        ComparisonResult result = comparator.compare(baseline, current);

        long seed = baseline.metadata != null ? baseline.metadata.seed : 0;

        if (result.identical) {
            System.out.println("Snapshots identical (seed: " + seed + ", " + result.totalValues + " values compared)");
            return EXIT_SUCCESS;
        }

        // Snapshots differ
        System.out.println("Snapshots differ!");
        System.out.println();

        int maxDiffsPerSection = verbose ? Integer.MAX_VALUE : DEFAULT_MAX_DIFFS_PER_SECTION;

        for (SectionDiff section : result.sections.values()) {
            if (section.differences == 0) {
                continue;
            }

            System.out.printf("%s differences (%d of %d): %.1f%%%n",
                    section.sectionName,
                    section.differences,
                    section.totalValues,
                    section.getDifferencePercentage());

            List<ValueDiff> diffs = section.diffs;
            int showCount = Math.min(diffs.size(), maxDiffsPerSection);

            for (int i = 0; i < showCount; i++) {
                ValueDiff diff = diffs.get(i);
                System.out.printf("  [%s]: %s -> %s%n", diff.coord, diff.oldValue, diff.newValue);
            }

            int remaining = diffs.size() - showCount;
            if (remaining > 0) {
                System.out.printf("  ... (%d more)%n", remaining);
            }

            System.out.println();
        }

        System.out.printf("Total: %d differences across %d values (%.1f%%)%n",
                result.totalDifferences,
                result.totalValues,
                result.getDifferencePercentage());

        // Check threshold
        if (result.getDifferencePercentage() <= failThreshold) {
            System.out.printf("Within threshold (%.1f%% <= %.1f%%)%n",
                    result.getDifferencePercentage(), failThreshold);
            return EXIT_SUCCESS;
        }

        return EXIT_DIFFERS;
    }

    /**
     * Loads a snapshot from a JSON file.
     */
    private SnapshotModel loadSnapshot(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }
        return mapper.readValue(file, SnapshotModel.class);
    }
}
