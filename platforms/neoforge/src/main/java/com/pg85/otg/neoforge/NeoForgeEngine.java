package com.pg85.otg.neoforge;

import com.pg85.otg.OTG;
import com.pg85.otg.OTGEngine;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.neoforge.biome.LegacyNeoForgeBiomeLoader;
import com.pg85.otg.neoforge.materials.NeoForgeMaterials;
import com.pg85.otg.neoforge.util.NeoForgeModLoadedChecker;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NeoForgeEngine extends OTGEngine {
    protected NeoForgeEngine() {
        super(
                OTGLog.getLogger(),
                FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID),
                new NeoForgeModLoadedChecker(),
                new LegacyNeoForgeBiomeLoader(FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID))
        );
    }

    @Override
    public void onStart() {
        NeoForgeMaterials.init();
        super.onStart();
    }

    @Override
    public File getJarFileFromModLoader() {
        List<File> jarFiles = new ArrayList<>();
        ModList.get().getModContainerById(Constants.MOD_ID_SHORT).ifPresent(modContainer -> {
            var modFile = modContainer.getModInfo().getOwningFile().getFile();
            jarFiles.add(modFile.getFilePath().toFile());
        });
        if (jarFiles.isEmpty()) {
            OTG.log(LogLevel.WARN, LogCategory.MAIN, "Could not find mod jar file");
            return null;
        }
        return jarFiles.get(0);
    }
}
