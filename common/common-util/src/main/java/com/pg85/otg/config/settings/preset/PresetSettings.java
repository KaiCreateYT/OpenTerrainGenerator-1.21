package com.pg85.otg.config.settings.preset;

import com.pg85.otg.config.ConfigFile;
import com.pg85.otg.util.gen.OTGWorldInfo;
import lombok.Getter;

/**
 * PresetConfig.ini classes
 * <p>
 * PresetSettings defines anything that's used/exposed between projects.
 * PresetConfig contains only fields/methods used for io/serialisation/instantiation.
 * <p>
 * PresetConfig should be used only in common-core and platform-specific layers, when reading/writing settings on app start.
 * PresetSettings should be used wherever settings are used in code.
 */
@Getter
public abstract class PresetSettings implements ConfigFile {
	/* This class is a container for all settings at the preset level
	* In order to add a new setting, there are four steps:
	* 1. Add a field to one of the classes below
	* 2. Add a public static setting to the class, including a getter and a description
	* 3. Add the setting to the builder of the class
	* 4. Add the setting to the write method of the class
	* 4a. If the class has no write method yet, add it to the PresetWriter directly in the appropriate section
	 */
	protected GameRuleSettings gameRuleSettings;
	protected StructureSettings structureSettings;
	protected CarverSettings carverSettings;
	protected SpawnSettings spawnSettings;
	protected PortalSettings portalSettings;
	protected DimensionSettings dimensionSettings;
	protected GenerationSettings generationSettings;
    protected TerrainSettings terrainSettings;
    protected NoiseCaveSettings noiseCaveSettings;
	protected ImageSettings imageSettings;
	protected VisualSettings visualSettings;
	protected PresetInfo presetInfo;
	protected ResourceSettings resourceSettings;
	protected BlockSettings blockSettings;
	private final String configName;
	private OTGWorldInfo otgWorldInfo = null;

    protected PresetSettings(String configName) {
        this.configName = configName;
    }

	@Override
	public String getConfigName() {
		return configName;
	}

	public OTGWorldInfo getWorldInfo() {
		if (otgWorldInfo == null) {
            switch (dimensionSettings.getDimensionType()) {
                case NETHER, END -> otgWorldInfo = new OTGWorldInfo(0, 127);
                case OVERWORLD, OTG -> otgWorldInfo = new OTGWorldInfo(-64, 319);
                default -> {
					int maxY = dimensionSettings.getMinY() + dimensionSettings.getHeight() - 1;
					return otgWorldInfo = new OTGWorldInfo(dimensionSettings.getMinY(), maxY);
				}
            }
		}
		return otgWorldInfo;
	}
}
