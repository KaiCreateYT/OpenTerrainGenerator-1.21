package com.pg85.otg.neoforge.util;

import com.pg85.otg.interfaces.IModLoadedChecker;
import net.neoforged.fml.ModList;

public class NeoForgeModLoadedChecker implements IModLoadedChecker {
    @Override
    public boolean isModLoaded(String mod) {
        return ModList.get().isLoaded(mod);
    }
}
