package com.pg85.otg.fabric.portals.components;

import com.pg85.otg.constants.Constants;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class OTGComponents implements EntityComponentInitializer {

    public static final ComponentKey<OTGPlayerComponent> OTG_PLAYER = ComponentRegistry.getOrCreate(
            new ResourceLocation(Constants.MOD_ID_SHORT, "otg_player"),
            OTGPlayerComponent.class
    );

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(
                OTG_PLAYER,
                OTGPlayerComponentImpl::new,
                RespawnCopyStrategy.ALWAYS_COPY
        );
    }

    public static OTGPlayerComponent get(Player player) {
        return OTG_PLAYER.get(player);
    }
}
