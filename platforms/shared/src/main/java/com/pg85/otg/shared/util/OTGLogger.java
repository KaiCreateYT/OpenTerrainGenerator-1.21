package com.pg85.otg.shared.util;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import com.pg85.otg.util.logging.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Locale;

public class OTGLogger extends Logger {
    private final String modId = Constants.MOD_ID_SHORT.toUpperCase(Locale.ROOT);
    private final org.apache.logging.log4j.Logger logger = LogManager.getLogger(modId);
    @Override
    public void log(LogLevel level, LogCategory category, String message)
    {
        if (this.minimumLevel.compareTo(level) < 0)
        {
            return;
        }

        switch (level)
        {
            case FATAL:
                this.logger.fatal("{} {}", category.getLogTag(), message);
                break;
            case ERROR:
                this.logger.error("{} {}", category.getLogTag(), message);
                break;
            case WARN:
                this.logger.warn("{} {}", category.getLogTag(), message);
                break;
            case INFO:
                this.logger.info("{} {}", category.getLogTag(), message);
                break;
            default:
                break;
        }

    }
}
