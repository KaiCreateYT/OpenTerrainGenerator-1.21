package com.pg85.otg;

import com.pg85.otg.config.PluginConfig;
import com.pg85.otg.config.io.FileSettingsReader;
import com.pg85.otg.config.io.FileSettingsWriter;
import com.pg85.otg.config.settings.preset.DimensionPresetInfo;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.customobject.CustomObjectManager;
import com.pg85.otg.customobject.config.CustomObjectResourcesManager;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.interfaces.ILogger;
import com.pg85.otg.interfaces.IModLoadedChecker;
import com.pg85.otg.interfaces.IPluginConfig;
import com.pg85.otg.presets.LocalDimensionPresetLoader;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import lombok.Getter;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Implemented and provided by the platform-specific layer on app start and accessed via OTG.startEngine()/OTG.getEngine(),
 * this class holds any objects and methods used during an app session.
 * 
 * Constructor parameters are platform-specific implementations of wrapper classes, such as a logger, material reader, 
 * preset loader etc. Implement these to provide support for a platform (Forge, Spigot etc).
 *  
 * OTGEngine.onStart() should be called on mod/plugin start, creates all OTG files and folders and registers all presets 
 * and biomes via the platform-specific preset loader provided as a constructor parameter. 
 */
public abstract class OTGEngine
{
	// Classes implemented/provided by the platform-specific layer.
	
	@Getter
    protected final LocalDimensionPresetLoader dimensionPresetLoader;
	@Getter
    protected final ILogger logger;
	@Getter
    private final IModLoadedChecker modLoadedChecker;

	// Common classes
	
	private final Path otgRootFolder;
	@Getter
    private final Path globalObjectsFolder;
	protected PluginConfig pluginConfig;

    // Create manager objects

    @Getter
    private final CustomObjectResourcesManager customObjectResourcesManager = new CustomObjectResourcesManager();
	@Getter
    private CustomObjectManager customObjectManager;
	
	protected OTGEngine(ILogger logger, Path otgRootFolder, IModLoadedChecker modLoadedChecker, LocalDimensionPresetLoader dimensionPresetLoader)
	{
		this.logger = logger;
		this.otgRootFolder = otgRootFolder;
		this.globalObjectsFolder = otgRootFolder.resolve(Constants.GLOBAL_OBJECTS_FOLDER);
		this.dimensionPresetLoader = dimensionPresetLoader;
		this.modLoadedChecker = modLoadedChecker;
	}
	
	// Get jar file that's running OTG, where we will find our default preset
	public abstract File getJarFileFromModLoader();
	
	// Startup / shutdown

	public void onStart()
	{
		// Load plugin config

		File pluginConfigFile = Paths.get(getOTGRootFolder().toString(), Constants.PluginConfigFilename).toFile();
		this.pluginConfig = new PluginConfig(
				FileSettingsReader.read(Constants.PluginConfigFilename, pluginConfigFile),
				pluginConfigFile.toPath()
		);
		this.logger.init(
			this.pluginConfig.getLogLevel().getLevel(),
			this.pluginConfig.getEnabledLogCategories(),
			this.pluginConfig.logPresets()
		);
		FileSettingsWriter.writeToFile(this.pluginConfig.getSettingsAsMap(), pluginConfigFile, this.pluginConfig.getSettingsMode());

		// Detect old folder names and warn users
		Path oldPresetsDir = Paths.get(getOTGRootFolder().toString(), "Presets");
		if (oldPresetsDir.toFile().exists() && !Paths.get(getOTGRootFolder().toString(), Constants.DIMENSION_PRESETS_FOLDER).toFile().exists()) {
			this.logger.error(LogCategory.MAIN, "==============================================");
			this.logger.error(LogCategory.MAIN, "BREAKING CHANGE: 'Presets/' has been renamed to 'DimensionPresets/'");
			this.logger.error(LogCategory.MAIN, "Please rename your Presets folder to DimensionPresets");
			this.logger.error(LogCategory.MAIN, "==============================================");
		}

		Path oldDimConfigsDir = Paths.get(getOTGRootFolder().toString(), "DimensionConfigs");
		if (oldDimConfigsDir.toFile().exists() && !Paths.get(getOTGRootFolder().toString(), Constants.WORLD_PRESETS_FOLDER).toFile().exists()) {
			this.logger.error(LogCategory.MAIN, "==============================================");
			this.logger.error(LogCategory.MAIN, "BREAKING CHANGE: 'DimensionConfigs/' has been renamed to 'WorldPresets/'");
			this.logger.error(LogCategory.MAIN, "Please rename your DimensionConfigs folder to WorldPresets");
			this.logger.error(LogCategory.MAIN, "==============================================");
		}

		// Create OTG folders

		File presetsDir = Paths.get(getOTGRootFolder().toString(), Constants.DIMENSION_PRESETS_FOLDER).toFile();
		if(!presetsDir.exists())
		{
			presetsDir.mkdirs();
		}

		File dimensionConfigsDir = Paths.get(getOTGRootFolder().toString(), Constants.WORLD_PRESETS_FOLDER).toFile();
		if(!dimensionConfigsDir.exists())
		{
			dimensionConfigsDir.mkdirs();
		}

		File globalObjectsDir = this.globalObjectsFolder.toFile();
		if(!globalObjectsDir.exists())
		{
			globalObjectsDir.mkdirs();
		}

		var jarFile = getJarFile();
		if (jarFile == null)
		{
			this.logger.warn(LogCategory.MAIN, "Error getting the jar file. Skipping default preset unpack (copy manually for development).");
		}
		else if (shouldSkipDefaultPresetUnpack(presetsDir, jarFile))
		{
			this.logger.info(LogCategory.MAIN, "Default preset is up-to-date. Skipping unpacking.");
		}
		else
		{
			UnpackDefaultPresetAndExamples(jarFile);
		}

        this.customObjectManager = new CustomObjectManager(
			getPluginConfig().getDeveloperModeEnabled(),
			this.otgRootFolder, 
			getPresetsDirectory(), 
			this.customObjectResourcesManager
		);

		// Load presets

		this.dimensionPresetLoader.loadDimensionPresetsFromDisk();
	}

	/**
	 * Gets the jar file that's running OTG
	 * @return the jar file, or null if it couldn't be found
	 */
	private JarFile getJarFile() {
		JarFile jarFile;

		File jarFileLocation = getJarFileFromModLoader();
		if (jarFileLocation == null || !jarFileLocation.exists()) {
			this.logger.warn(LogCategory.MAIN, "Given location for jar file is null or the file does not exist. Location: {}", jarFileLocation);
			return null;
		}

		try {
			jarFile = new JarFile(jarFileLocation);
		} catch (IOException e) {
			this.logger.warn(LogCategory.MAIN, "Could not open root jar file {}. Error: {}", jarFileLocation, e.getMessage());
			return null;
		}

		return jarFile;
	}

	/**
	 * Extracts the default preset and example dimension configuration files
	 * from the provided OTG jar into the local OTG root directory.
	 *
	 * <p>The method iterates over all entries in the given {@link JarFile} and
	 * copies only those located under:
	 * <ul>
	 *   <li>{@code resources/presets/<DEFAULT_PRESET_NAME>/}</li>
	 *   <li>{@code resources/dimension_configs/}</li>
	 * </ul>
	 *
	 * <p>Directory structure is recreated as needed. Existing files are silently
	 * overwritten.
	 *
	 * <p><b>Important:</b>
	 * <ul>
	 *   <li>The {@link JarFile} is always closed by this method (including on failure).</li>
	 *   <li>If {@code jarFile} is {@code null}, a {@link NullPointerException} will occur.</li>
	 *   <li>All I/O errors are caught and logged; no exception is propagated to the caller.</li>
	 *   <li>Path handling assumes a fixed {@code "resources/"} prefix length
	 *       (via {@code substring(10)}), which is fragile and will break if the
	 *       jar layout changes.</li>
	 * </ul>
	 *
	 * @param jarFile the OTG jar file containing default presets and example configs
	 */
	private void UnpackDefaultPresetAndExamples(JarFile jarFile)
	{
		try
		{
			String rootDir = getOTGRootFolder().toString();
			String defaultPresetPath = "resources/" + Constants.DIMENSION_PRESETS_FOLDER + "/" + Constants.DEFAULT_PRESET_NAME + "/";
			String dimensionConfigsPath = "resources/" + Constants.WORLD_PRESETS_FOLDER + "/";
			Enumeration<JarEntry> entries = jarFile.entries();

			while (entries.hasMoreElements())
			{
				JarEntry entry = entries.nextElement();
				if (
					entry.getName().startsWith(dimensionConfigsPath) ||
					entry.getName().startsWith(defaultPresetPath)
				)
				{
					File file = new File(rootDir + File.separator + (entry.getName().substring(10)));

					if (entry.isDirectory())
					{
						file.mkdirs();
					} else {
						file.createNewFile();
						FileOutputStream fos = new FileOutputStream(file);
						byte[] byteArray = new byte[4096];
						int i;
						java.io.InputStream is = jarFile.getInputStream(entry);
						while ((i = is.read(byteArray)) > 0)
						{
							fos.write(byteArray, 0, i);
						}
						is.close();
						fos.close();
					}
				}
			}
		}
		catch (IOException e)
		{
			this.logger.error(LogCategory.MAIN, "Failed to extract jar file", e);
		} finally {
			if(jarFile != null)
			{
				try {
					jarFile.close();
				} catch (IOException e) {
					this.logger.error(LogCategory.MAIN, "Failed to close jar file", e);
				}
			}
		}
	}

	/**
	 * Checks if the default preset exists and is up-to-date.
	 * @return true if unpacking should be skipped (preset exists and is current or newer), false otherwise
	 */
	private boolean shouldSkipDefaultPresetUnpack(File presetsDir, JarFile jarFile)
	{
		File presetDir = new File(presetsDir.getPath() + File.separator + Constants.DEFAULT_PRESET_NAME);
		if (!presetDir.exists())
		{
			return false;
		}

		File presetConfigFile = new File(presetDir, Constants.DIMENSION_PRESET_CONFIG_FILE);
		if (!presetConfigFile.exists())
		{
			return false;
		}

		try (BufferedReader existingConfigReader = new BufferedReader(new FileReader(presetConfigFile)))
		{
			int existingMajorVer = parseMajorVersion(existingConfigReader);
			int existingMinorVer = parseMinorVersion(existingConfigReader);

			int bundledMajorVer = 0;
			int bundledMinorVer = 0;

			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements())
			{
				JarEntry jarEntry = entries.nextElement();
				if (jarEntry.getName().contains(Constants.DEFAULT_PRESET_NAME + "/" + Constants.DIMENSION_PRESET_CONFIG_FILE))
				{
					try (BufferedReader jarConfigReader = new BufferedReader(new InputStreamReader(jarFile.getInputStream(jarEntry))))
					{
						bundledMajorVer = parseMajorVersion(jarConfigReader);
						bundledMinorVer = parseMinorVersion(jarConfigReader);
					}
					break;
				}
			}

			// Skip if existing version is same or newer
			return (bundledMajorVer < existingMajorVer) ||
				   (bundledMajorVer == existingMajorVer && bundledMinorVer <= existingMinorVer);
		}
		catch (IOException e)
		{
			this.logger.warn(LogCategory.MAIN, "Error reading default preset config file {}. Error: {}", presetConfigFile, e.getMessage());
			return false;
		}
	}
	
	private int parseMajorVersion(BufferedReader reader) throws IOException
	{
		return parseVersion(reader, DimensionPresetInfo.MAJOR_VERSION.getName());
	}
	
	private int parseMinorVersion(BufferedReader reader) throws IOException
	{
		return parseVersion(reader, DimensionPresetInfo.MINOR_VERSION.getName());
	}
	
	private int parseVersion(BufferedReader reader, String name) throws IOException
	{
		int version = -1;
		String line;
		// Filter out the line with Version in it
		while ((line = reader.readLine()) != null)
		{
			if (line.contains(name))
			{
				break;
			}
		}
		if (line != null)
		{
			String v = line.split(":")[1];
			v = v.trim();
			version = Integer.parseInt(v);
		}
		return version;
	}

	public void onShutdown()
	{
		// Shutdown all loaders
		this.customObjectManager.shutdown();
	}

    // OTG Configs
	
	public IPluginConfig getPluginConfig()
	{
		return this.pluginConfig;
	}

	// OTG dirs

	public Path getOTGRootFolder()
	{
		return this.otgRootFolder;
	}

    public Path getPresetsDirectory()
	{
		return Paths.get(this.getOTGRootFolder().toString(), Constants.DIMENSION_PRESETS_FOLDER);
	}

	// Logging

    // Builders/Factories
	
	public CustomStructureCache createCustomStructureCache(String presetFolderName, Path worldSavepath, long worldSeed, boolean isBo4Enabled)
	{
		// TODO: ModLoadedChecker
		return new CustomStructureCache(
			presetFolderName, 
			worldSavepath, 
			worldSeed, 
			isBo4Enabled, 
			getOTGRootFolder(),
			getCustomObjectManager(),
			OTGMaterialReader.get(),
			getCustomObjectResourcesManager(), 
			null
		);
	}
}
