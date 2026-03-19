package com.pg85.otg.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {

    @Accessor("singleplayerServer")
    void otg$setSingleplayerServer(@Nullable IntegratedServer server);

    @Accessor("isLocalServer")
    void otg$setIsLocalServer(boolean local);

    @Invoker("updateLevelInEngines")
    void otg$updateLevelInEngines(@Nullable ClientLevel level);
}
