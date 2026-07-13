package com.pg85.otg.fabric;

import com.pg85.otg.OTGEngine;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.shared.biome.SharedBiomePlatformAdapter;
import com.pg85.otg.shared.biome.SharedDimensionPresetBiomeLoader;
import com.pg85.otg.shared.materials.SharedMaterials;
import com.pg85.otg.fabric.util.FabricModLoadedChecker;
import com.pg85.otg.util.OTGLog;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FabricEngine extends OTGEngine {
    protected FabricEngine() {
        super(
                OTGLog.getLogger(),
                FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID),
                new FabricModLoadedChecker(),
                new SharedDimensionPresetBiomeLoader(FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID), new SharedBiomePlatformAdapter())
        );
    }

    @Override
    public void onStart() {
        SharedMaterials.init();
        super.onStart();
    }

    /**
     * Fabric exposes every classpath root for the mod (classes, generated sources, merged jars).
     * Bundled presets may live on any of these — not only the first path.
     */
    @Override
    protected List<Path> getModBundledResourceRoots() {
        List<Path> out = new ArrayList<>();
        FabricLoader.getInstance().getModContainer(Constants.MOD_ID_SHORT).ifPresent(modContainer -> {
            for (Path p : modContainer.getOrigin().getPaths()) {
                out.add(p.toAbsolutePath().normalize());
            }
        });
        return out;
    }

    @Override
    public File getJarFileFromModLoader() {
        // get the jar file of the mod from fabric itself
        List<File> jarFiles = new ArrayList<>();
        FabricLoader.getInstance().getModContainer(Constants.MOD_ID_SHORT).ifPresent(modContainer -> {
            for (var path : modContainer.getOrigin().getPaths()) {
                jarFiles.add(path.toFile());
            }
        });
        if (jarFiles.isEmpty()) {
            OTGLog.warn("No mod origin paths for {}; cannot unpack bundled presets from jar.", Constants.MOD_ID_SHORT);
            return null;
        }
        if (jarFiles.size() > 1) {
            OTGLog.warn("Found multiple paths for mod jar, using the first one.");
        }
        return jarFiles.get(0);
    }
}
