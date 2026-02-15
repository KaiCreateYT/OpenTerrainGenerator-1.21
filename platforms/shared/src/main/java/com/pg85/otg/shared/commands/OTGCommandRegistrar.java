package com.pg85.otg.shared.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class OTGCommandRegistrar {
    private static CommandWorldAccessor worldAccessor;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandWorldAccessor accessor) {
        worldAccessor = accessor;
        var otgCommand = Commands.literal("otg");
        FlushCacheCommand.register(otgCommand);
        SpawnCommand.register(otgCommand);
        dispatcher.register(otgCommand);
    }

    public static CommandWorldAccessor getWorldAccessor() {
        return worldAccessor;
    }
}
