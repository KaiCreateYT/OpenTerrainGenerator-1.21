package com.pg85.otg.shared.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pg85.otg.OTG;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class FlushCacheCommand {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> otgCommand) {
        otgCommand.then(
            Commands.literal("flushcache")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    OTG.getEngine().getCustomObjectManager().reloadCustomObjectFiles();
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("OTG custom object cache flushed. Objects will reload on next use."),
                        true
                    );
                    return 1;
                })
        );
    }
}
