package com.pg85.otg.neoforge;

import com.pg85.otg.OTGEngine;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.shared.biome.SharedBiomePlatformAdapter;
import com.pg85.otg.shared.biome.SharedDimensionPresetBiomeLoader;
import com.pg85.otg.shared.materials.SharedMaterials;
import com.pg85.otg.neoforge.util.NeoForgeModLoadedChecker;
import com.pg85.otg.util.OTGLog;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NeoForgeEngine extends OTGEngine {
    protected NeoForgeEngine() {
        super(
                OTGLog.getLogger(),
                FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID),
                new NeoForgeModLoadedChecker(),
                new SharedDimensionPresetBiomeLoader(FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID), new SharedBiomePlatformAdapter())
        );
    }

    @Override
    public void onStart() {
        SharedMaterials.init();
        super.onStart();
    }

    @Override
    protected List<Path> getModBundledResourceRoots() {
        File jar = getJarFileFromModLoader();
        if (jar == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(jar.toPath().toAbsolutePath().normalize());
    }

    @Override
    public File getJarFileFromModLoader() {
        List<File> jarFiles = new ArrayList<>();
        ModList.get().getModContainerById(Constants.MOD_ID_SHORT).ifPresent(modContainer -> {
            var modFile = modContainer.getModInfo().getOwningFile().getFile();
            jarFiles.add(modFile.getFilePath().toFile());
        });
        if (jarFiles.isEmpty()) {
            OTGLog.warn("Could not find mod jar file");
            return null;
        }
        return jarFiles.get(0);
    }
}
