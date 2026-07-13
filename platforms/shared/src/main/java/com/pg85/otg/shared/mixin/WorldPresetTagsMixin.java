package com.pg85.otg.shared.mixin;

import com.pg85.otg.util.OTGLog;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;

/**
 * WorldPresetTagsMixin is disabled in 1.21.5 because updateRegistryTags() was removed.
 * Tag binding now happens in OTGRegistryHelper.loadOTGPresets().
 */
@Mixin(ReloadableServerResources.class)
public class WorldPresetTagsMixin {
    // Mixin disabled - tags are bound in OTGRegistryHelper
}
