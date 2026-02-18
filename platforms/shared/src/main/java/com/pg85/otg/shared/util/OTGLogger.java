package com.pg85.otg.shared.util;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.interfaces.ILogger;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import org.apache.logging.log4j.LogManager;

import java.util.EnumSet;
import java.util.Locale;

public class OTGLogger implements ILogger {
	private final org.apache.logging.log4j.Logger logger =
			LogManager.getLogger(Constants.MOD_ID_SHORT.toUpperCase(Locale.ROOT));

	private LogLevel minLevel = LogLevel.INFO;
	private final EnumSet<LogCategory> enabledCategories = EnumSet.of(LogCategory.MAIN);
	private String logPresets = "all";

	@Override
	public void init(LogLevel level, EnumSet<LogCategory> enabledCategories, String logPresets) {
		this.minLevel = level;
		this.enabledCategories.clear();
		this.enabledCategories.add(LogCategory.MAIN);
		this.enabledCategories.addAll(enabledCategories);
		this.logPresets = logPresets;
	}

	@Override
	public boolean isEnabled(LogLevel level, LogCategory category) {
		return level.ordinal() >= minLevel.ordinal() && enabledCategories.contains(category);
	}

	@Override
	public boolean canLogForPreset(String presetFolderName) {
		return "all".equalsIgnoreCase(logPresets) || logPresets.equalsIgnoreCase(presetFolderName);
	}

	@Override
	public void log(LogLevel level, LogCategory category, String message) {
		if (!isEnabled(level, category)) return;

		String taggedMessage = category.getLogTag() + " " + message;
		switch (level) {
			case FATAL -> logger.fatal(taggedMessage);
			case ERROR -> logger.error(taggedMessage);
			case WARN -> logger.warn(taggedMessage);
			case INFO -> logger.info(taggedMessage);
		}
	}
}
