package com.pg85.otg.test.engine;

import com.pg85.otg.interfaces.ILogger;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Minimal ILogger implementation for headless testing.
 * Prints to stderr with level and category prefix.
 */
public class TestLogger implements ILogger {

    private LogLevel minLevel = LogLevel.INFO;
    private boolean logConfigs = true;
    private boolean logBiomeRegistry = false;
    private boolean logDecoration = false;
    private boolean logCustomObjects = false;
    private boolean logStructurePlotting = false;
    private boolean logPerformance = false;
    private boolean logMobs = false;
    private String logPresets = "";

    @Override
    public void init(LogLevel level, boolean logCustomObjects, boolean logStructurePlotting,
                     boolean logConfigs, boolean logPerformance, boolean logBiomeRegistry,
                     boolean logDecoration, boolean logMobs, String logPresets) {
        this.minLevel = level;
        this.logCustomObjects = logCustomObjects;
        this.logStructurePlotting = logStructurePlotting;
        this.logConfigs = logConfigs;
        this.logPerformance = logPerformance;
        this.logBiomeRegistry = logBiomeRegistry;
        this.logDecoration = logDecoration;
        this.logMobs = logMobs;
        this.logPresets = logPresets;
    }

    @Override
    public void log(LogLevel level, LogCategory category, String message) {
        if (level.ordinal() < minLevel.ordinal()) {
            return;
        }
        if (!getLogCategoryEnabled(category)) {
            return;
        }
        System.err.println(level.name() + " " + category.name() + " " + message);
    }

    @Override
    public void printStackTrace(LogLevel level, LogCategory category, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log(level, category, sw.toString());
    }

    @Override
    public boolean getLogCategoryEnabled(LogCategory category) {
        return switch (category) {
            case CONFIGS -> logConfigs;
            case BIOME_REGISTRY -> logBiomeRegistry;
            case DECORATION -> logDecoration;
            case CUSTOM_OBJECTS -> logCustomObjects;
            case STRUCTURE_PLOTTING -> logStructurePlotting;
            case PERFORMANCE -> logPerformance;
            case MOBS -> logMobs;
            default -> true;
        };
    }

    @Override
    public boolean canLogForPreset(String presetFolderName) {
        // Empty logPresets means log all presets
        if (logPresets == null || logPresets.isEmpty()) {
            return true;
        }
        return logPresets.contains(presetFolderName);
    }
}
