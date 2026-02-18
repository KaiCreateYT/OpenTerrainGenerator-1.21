package com.pg85.otg;

import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;

/**
 * Main entry-point. Used to access OTGEngine.
 * OTGEngine is implemented and provided by the platform-specific
 * layer and holds any objects and methods used during a session.
 * For logging, use {@link OTGLog} directly.
 */
public class OTG
{
	private static OTGEngine Engine;

	private OTG() { }

	// Engine

	public static void startEngine(OTGEngine engine)
	{
		if (Engine != null)
		{
			throw new IllegalStateException("Engine is already set.");
		}

		Engine = engine;
		engine.onStart();
	}

	public static OTGEngine getEngine()
	{
		if (Engine == null)
		{
			throw new IllegalStateException("Engine is not started.");
		}
		return Engine;
	}

	public static void stopEngine()
	{
		Engine.onShutdown();
		Engine = null;
	}

	/** @deprecated Use {@link OTGLog#log(LogLevel, LogCategory, String)} directly */
	@Deprecated
	public static void log(LogLevel logLevel, LogCategory logCategory, String message) {
		OTGLog.log(logLevel, logCategory, message);
	}

	/** @deprecated Use {@link OTGLog#info(String, Object...)} directly */
	@Deprecated
	public static void log(String message) {
		OTGLog.info(message);
	}
}
