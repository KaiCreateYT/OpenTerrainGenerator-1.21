package com.pg85.otg.shared.mixin;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.shared.portals.WorldPresetPortalResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRespawnMixin {

    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void otg$respawnInOTGDimension(boolean isKeepingAllPlayerData,
            DimensionTransition.PostDimensionTransition postTransition,
            CallbackInfoReturnable<DimensionTransition> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;

        // Only override if player has no explicit respawn point (no bed/anchor)
        if (self.getRespawnPosition() != null) return;

        ServerLevel currentLevel = self.serverLevel();
        if (!(currentLevel.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen)) return;

        // Only override if YAML explicitly enables RespawnInDimension for this dimension
        WorldPresetConfig activeWorldPreset = WorldPresetPortalResolver.getActiveWorldPreset();
        if (activeWorldPreset == null) return; // no YAML = vanilla behavior

        String presetFolder = gen.getPreset() != null ? gen.getPreset().getFolderName() : null;
        WorldPresetConfig.OTGDimension dimEntry = WorldPresetPortalResolver.findDimensionEntry(activeWorldPreset, presetFolder);
        if (dimEntry == null || !Boolean.TRUE.equals(dimEntry.RespawnInDimension)) return; // must be explicitly true

        // Respawn in the same OTG dimension at its spawn point
        cir.setReturnValue(DimensionTransition.missingRespawnBlock(currentLevel, self, postTransition));
    }
}
