package com.pg85.otg.presets;

import java.nio.file.Path;
import java.util.*;

import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.config.biome.BiomeTemplate;
import com.pg85.otg.config.preset.PresetConfig;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.util.biome.OTGBiomeID;
import lombok.Getter;

/**
 * Represents an OTG preset, with all its world and biome configs, stored in /config/OpenTerrainGenerator/Presets/\<PresetName\>/.
 */
public class Preset {
    @Getter
    private final Path presetFolder;
    @Getter
    private final String folderName;
    @Getter
    private final String presetRegistryName;

    // Note: Since we're not using Supplier<>, we need to be careful about any classes fetching
    // and caching our worldconfig/biomeconfigs etc, or they won't update when reloaded from disk.
    // BiomeGen and ChunkGen cache some settings during a session, so they'll only update on world exit/rejoin.
    @Getter
    private PresetConfig presetConfig;

    private final List<BiomeConfig> biomeConfigList;

    private final List<BiomeTemplate> biomeTemplateList;

    private HashMap<OTGBiomeID, BiomeConfig> biomeConfigs = new HashMap<>();
    private HashMap<String, BiomeTemplate> biomeTemplates = new HashMap<>();

    private HashSet<OTGBiomeID> biomeIDS = new HashSet<>();
    @Getter
    private int majorVersion;
    @Getter
    private String author;
    @Getter
    private String description;

    public Preset(Path presetFolder, PresetConfig presetConfig, List<BiomeConfig> biomeConfigList, List<BiomeTemplate> biomeTemplateList) {
        this.presetFolder = presetFolder;
        this.folderName = presetFolder.toFile().getName();
        this.presetRegistryName = presetConfig.getPresetInfo().getRegistryName();
        this.presetConfig = presetConfig;
        this.author = presetConfig.getPresetInfo().getAuthor();
        this.description = presetConfig.getPresetInfo().getDescription();
        this.majorVersion = presetConfig.getPresetInfo().getMajorVersion();
        this.biomeTemplateList = biomeTemplateList;
        this.biomeConfigList = biomeConfigList;

        this.biomeConfigList.forEach(bc -> {
            OTGBiomeID biomeID = bc.getOTGBiomeID();
            biomeIDS.add(biomeID);
            biomeConfigs.put(biomeID, bc);
        });

        this.biomeTemplateList.forEach(bt -> {
            biomeTemplates.put(bt.getConfigName(), bt);
        });
    }

    public void update(Preset preset) {
        this.presetConfig = preset.presetConfig;
        this.biomeConfigs = preset.biomeConfigs;
        this.biomeTemplates = preset.biomeTemplates;
        this.biomeIDS = preset.biomeIDS;
        this.author = preset.author;
        this.description = preset.description;
        this.majorVersion = preset.majorVersion;
    }

    public BiomeSettings getBiomeConfig(String biomeName) {
        OTGBiomeID biomeID = getBiomeID(biomeName);
        return this.biomeConfigs.get(biomeID);
    }

    public OTGBiomeID getBiomeID(String biomeName) {
        for (OTGBiomeID biomeID : this.biomeIDS) {
            if (biomeID.biomeName().equals(biomeName)) {
                return biomeID;
            }
        }
        return null;
    }


    public ArrayList<BiomeConfig> getBiomeConfigList() {
        return new ArrayList<>(this.biomeConfigList);
    }

    public ArrayList<BiomeTemplate> getBiomeTemplateList() {
        return new ArrayList<>(this.biomeTemplateList);
    }

    public ArrayList<String> getAllBiomeNames() {
        return new ArrayList<>(this.biomeConfigs.keySet().stream().map(OTGBiomeID::biomeName).toList());
    }

    @Override
    public String toString() {
        return this.folderName;
    }

    public List<String> getDimensionNames() {
        return getPresetConfig().getDimensionSettings().getDefaultDimensions()
                .stream()
                .map(
                        string -> string.equalsIgnoreCase("this")
                                ? Constants.MOD_ID_SHORT + ':' + getPresetRegistryName()
                                : string)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
    }
}
