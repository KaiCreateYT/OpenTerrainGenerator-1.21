package com.pg85.otg.presets;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.config.biome.BiomeResourcesManager;
import com.pg85.otg.config.biome.BiomeTemplate;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.loader.BiomeConfigLoader;
import com.pg85.otg.config.preset.PresetConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.gen.biome.layers.BiomeLayerData;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.interfaces.IMaterialReader;
import com.pg85.otg.loader.PresetConfigLoader;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;

/**
 * A base class for a platform-specific preset loader, which loads
 * all presets from disk when the OTG Engine is started at app start,
 * and registers all biomes with their worldconfig/biomeconfig settings.
 */
public abstract class LocalPresetLoader {
    private static final int MAX_INHERITANCE_DEPTH = 15;
    protected final File presetsDir;
    protected final HashMap<String, Preset> presets = new HashMap<>();
    protected final HashMap<String, String> aliasMap = new HashMap<>();

    public LocalPresetLoader(Path otgRootFolder) {
        this.presetsDir = getPresetsDir(otgRootFolder).toFile();
    }

    private static Path getPresetsDir(Path otgRootFolder) {
        return Paths.get(otgRootFolder.toString(), File.separator + Constants.PRESETS_FOLDER);
    }

    public IMaterialReader getMaterialReader() {
        return OTGMaterialReader.get();
    }


    public Preset getPresetByShortNameOrFolderName(String name) {
        // Example: preset is stored as "Biome Bundle v7", but also accepts "Biome Bundle"
        if (aliasMap.containsKey(name)) {
            return this.presets.get(aliasMap.get(name));
        }
        return this.presets.get(name);
    }

    public Preset getPresetByFolderName(String name) {
        return this.presets.get(name);
    }

    public ArrayList<Preset> getAllPresets() {
        return new ArrayList<Preset>(presets.values());
    }

    public Set<String> getAllPresetFolderNames() {
        return presets.keySet();
    }

    public String getDefaultPresetFolderName() {
        return this.presets.keySet().isEmpty() ? Constants.DEFAULT_PRESET_NAME
                : this.presets.containsKey(Constants.DEFAULT_PRESET_NAME)
                ? Constants.DEFAULT_PRESET_NAME
                : (String) this.presets.keySet().toArray()[0];
    }

    public void loadPresetsFromDisk() {
        // Clear existing presets and aliases before reloading (important for developer mode reload)
        this.presets.clear();
        this.aliasMap.clear();

        if (this.presetsDir.exists() && this.presetsDir.isDirectory()) {
            OTGLog.getLogger().log(
                    LogLevel.INFO,
                    LogCategory.CONFIGS,
                    "Loading presets from " + this.presetsDir
            );
            for (File presetDir : Objects.requireNonNull(this.presetsDir.listFiles())) {
                if (presetDir.isDirectory()) {
                    for (File file : Objects.requireNonNull(presetDir.listFiles())) {
                        if (file.getName().equals(Constants.PRESET_CONFIG_FILE) || file.getName().equals(Constants.LEGACY_WORLD_CONFIG_FILE)) {
                            Preset preset = loadPreset(presetDir.toPath());
                            if (this.aliasMap.containsKey(preset.getPresetRegistryName())) {
                                OTGLog.getLogger().log(LogLevel.ERROR,
                                        LogCategory.MAIN,
                                        "Duplicate preset registry name found: " + preset.getPresetRegistryName() + ". Preset " + preset.getFolderName() + " will be ignored."
                                );
                                continue;
                            } else {
                                this.presets.put(preset.getFolderName(), preset);
                                this.aliasMap.put(preset.getPresetRegistryName(), preset.getFolderName());
                            }
                            break;
                        }
                    }
                }
            }
        } else {
            OTGLog.getLogger().log(
                    LogLevel.INFO,
                    LogCategory.CONFIGS,
                    "No presets found in " + this.presetsDir
            );
        }
    }

    public static Preset loadPreset(Path presetDir) {
        PresetConfig presetConfig = PresetConfigLoader.loadPresetConfig(presetDir);
        List<BiomeTemplate> biomeTemplatesImmutable = BiomeConfigLoader.loadBiomeTemplates(presetDir, presetConfig);
        List<BiomeSettings> biomeSettingsImmutable = BiomeConfigLoader.loadBiomeConfigs(presetDir, presetConfig);
        List<BiomeTemplate> biomeTemplates = new ArrayList<>(biomeTemplatesImmutable);
        List<BiomeConfig> biomeConfigs = new ArrayList<>();
        biomeSettingsImmutable.forEach(bs -> {
            if (bs instanceof BiomeTemplate bt) biomeTemplates.add(bt);
            if (bs instanceof BiomeConfig bc) biomeConfigs.add(bc);
        });

        return new Preset(presetDir, presetConfig, biomeConfigs, biomeTemplates);
    }

    public static List<Preset> loadPresetsFromDisk(Path otgRootFolder) {
        Path presetsDir = getPresetsDir(otgRootFolder);
        if (!presetsDir.toFile().exists()) {
            OTGLog.getLogger().log(
                    LogLevel.INFO,
                    LogCategory.CONFIGS,
                    "No presets found in " + presetsDir
            );
            return Collections.emptyList();
        }
        List<Path> presetDirectories = PresetConfigLoader.findPresetDirectories(presetsDir);
        List<Preset> presets = new ArrayList<>();
        for (Path presetDir : presetDirectories) {
            presets.add(loadPreset(presetDir));
        }
        return presets;
    }

    public abstract IBiome[] getGlobalIdMapping(String presetFolderName);

    public abstract Map<String, BiomeLayerData> getPresetGenerationData();

}
