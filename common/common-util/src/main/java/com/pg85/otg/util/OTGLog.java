package com.pg85.otg.util;

import com.pg85.otg.interfaces.ILogger;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;

public final class OTGLog {
    private static ILogger logger = new BasicLogger();
    public static void setLogger(ILogger logger) {
        OTGLog.logger = logger;
    }
    public static ILogger getLogger() {
        return OTGLog.logger;
    }
    
    public static void log(LogLevel level, LogCategory category, String message) {
        logger.log(level, category, message);
    }
    
    public static void printStackTrace(LogLevel marker, LogCategory category, Exception e) {
        logger.printStackTrace(marker, category, e);
    }
    
    public static boolean canLogForPreset(String presetFolderName) {
        return logger.canLogForPreset(presetFolderName);
    }

    public static void info(LogCategory category, String message, Object... objects) {
        log(LogLevel.INFO, category, String.format(message, objects));
    }

    public static void warn(LogCategory category, String message, Object... objects) {
        log(LogLevel.WARN, category, String.format(message, objects));
    }

    public static void error(LogCategory category, String message, Object... objects) {
        log(LogLevel.ERROR, category, String.format(message, objects));
    }

    public static void fatal(LogCategory category, String message, Object... objects) {
        log(LogLevel.FATAL, category, String.format(message, objects));
    }

    public static void info(String message, Object... objects) {
        info(LogCategory.MAIN, message, objects);
    }

    public static void warn(String message, Object... objects) {
        warn(LogCategory.MAIN, message, objects);
    }

    public static void error(String message, Object... objects) {
        error(LogCategory.MAIN, message, objects);
    }

    public static void fatal(String message, Object... objects) {
        fatal(LogCategory.MAIN, message, objects);
    }

    public static boolean getLogCategoryEnabled(LogCategory logCategory) {
        return logger.getLogCategoryEnabled(logCategory);
    }

    // Not for use by platform code; it's a fallback for when code is executed and the engine isn't running
    // like when generating YAML schema from settings
    private static class BasicLogger implements ILogger
    {
        private LogLevel level = LogLevel.INFO;
        private boolean logCustomObjects;
        private boolean logStructurePlotting;
        private boolean logConfigs = true;
        private boolean logPerformance;
        private boolean logBiomeRegistry;
        private boolean logDecoration;
        private boolean logMobs;

        @Override
        public void init(LogLevel level, boolean logCustomObjects, boolean logStructurePlotting, boolean logConfigs, boolean logPerformance, boolean logBiomeRegistry, boolean logDecoration, boolean logMobs, String logPresets) {
            this.level = level;
            this.logCustomObjects = logCustomObjects;
            this.logStructurePlotting = logStructurePlotting;
            this.logConfigs = logConfigs;
            this.logPerformance = logPerformance;
            this.logBiomeRegistry = logBiomeRegistry;
            this.logDecoration = logDecoration;
            this.logMobs = logMobs;
        }

        @Override
        public boolean getLogCategoryEnabled(LogCategory category) {
            switch (category) {
                case MAIN -> {
                    return true;
                }
                case CUSTOM_OBJECTS -> {
                    return logCustomObjects;
                }
                case STRUCTURE_PLOTTING -> {
                    return logStructurePlotting;
                }
                case CONFIGS -> {
                    return logConfigs;
                }
                case BIOME_REGISTRY -> {
                    return logBiomeRegistry;
                }
                case DECORATION -> {
                    return logDecoration;
                }
                case PERFORMANCE -> {
                    return logPerformance;
                }
                case MOBS -> {
                    return logMobs;
                }
            }
            return false;
        }

        @Override
        public void log(LogLevel level, LogCategory category, String message) {
            if (level.ordinal() >= this.level.ordinal()) {
                if (level.ordinal() >= LogLevel.WARN.ordinal()) {
                    System.err.println(level.name() + " " + category.name() + " " + message);
                } else {
                    System.out.println(level.name() + " " + category.name() + " " + message);
                }
            }
        }

        @Override
        public void printStackTrace(LogLevel marker, LogCategory category, Exception e) {
            if (marker.ordinal() >= this.level.ordinal()) {
                e.printStackTrace(System.err);
            }
        }

        @Override
        public boolean canLogForPreset(String presetFolderName) {
            return true;
        }
    }
}
