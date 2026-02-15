package com.pg85.otg.shared.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.pg85.otg.shared.dimensions.DimensionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class OTGCommandRegistrar {
    private static volatile CommandWorldAccessor worldAccessor;
    private static volatile DimensionManager dimensionManager;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandWorldAccessor accessor) {
        worldAccessor = accessor;
        var otgCommand = Commands.literal("otg");
        FlushCacheCommand.register(otgCommand);
        SpawnCommand.register(otgCommand);
        StructureCommand.register(otgCommand);
        ExportCommand.register(otgCommand);
        ExportBO4DataCommand.register(otgCommand);
        DimensionCommands.register(otgCommand);
        dispatcher.register(otgCommand);
    }

    public static void setDimensionManager(DimensionManager manager) {
        dimensionManager = manager;
    }

    public static DimensionManager getDimensionManager() {
        return dimensionManager;
    }

    public static CommandWorldAccessor getWorldAccessor() {
        return worldAccessor;
    }
}
