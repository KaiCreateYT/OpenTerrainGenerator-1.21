package com.pg85.otg.fabric.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;

public interface WorldSaveCallback {
    Event<WorldSaveCallback> EVENT = EventFactory.createArrayBacked(WorldSaveCallback.class,
            (listeners) -> (serverLevel) -> {
                for (WorldSaveCallback listener : listeners) {
                    listener.onWorldSave(serverLevel);
                }
    });

    void onWorldSave(ServerLevel serverLevel);
}
