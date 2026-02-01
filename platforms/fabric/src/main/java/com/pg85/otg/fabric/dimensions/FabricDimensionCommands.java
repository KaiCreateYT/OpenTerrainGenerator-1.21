package com.pg85.otg.fabric.dimensions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pg85.otg.OTG;
import com.pg85.otg.dimensions.DimensionInfo;
import com.pg85.otg.presets.Preset;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class FabricDimensionCommands {

    private static FabricDimensionManager manager;

    public static void setManager(FabricDimensionManager mgr) {
        manager = mgr;
    }

    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (context, builder) -> {
        List<String> presets = OTG.getEngine().getPresetLoader().getAllPresets().stream()
                .map(Preset::getFolderName)
                .toList();
        return SharedSuggestionProvider.suggest(presets, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> DIMENSION_SUGGESTIONS = (context, builder) -> {
        if (manager == null) {
            return builder.buildFuture();
        }
        List<String> dimensions = manager.listDimensions().stream()
                .map(DimensionInfo::getName)
                .toList();
        return SharedSuggestionProvider.suggest(dimensions, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("otg")
                .then(Commands.literal("dimension")
                    .then(Commands.literal("create")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.string())
                            .suggests(PRESET_SUGGESTIONS)
                            .executes(FabricDimensionCommands::createDimension)))
                    .then(Commands.literal("delete")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("dimension", StringArgumentType.string())
                            .suggests(DIMENSION_SUGGESTIONS)
                            .executes(ctx -> deleteDimension(ctx, false, false))
                            .then(Commands.literal("--purge")
                                .executes(ctx -> deleteDimension(ctx, true, false))
                                .then(Commands.literal("--confirm")
                                    .executes(ctx -> deleteDimension(ctx, true, true))))))
                    .then(Commands.literal("list")
                        .executes(FabricDimensionCommands::listDimensions))
                    .then(Commands.literal("info")
                        .then(Commands.argument("dimension", StringArgumentType.string())
                            .suggests(DIMENSION_SUGGESTIONS)
                            .executes(FabricDimensionCommands::dimensionInfo))))
                .then(Commands.literal("tp")
                    .then(Commands.argument("dimension", StringArgumentType.string())
                        .suggests(DIMENSION_SUGGESTIONS)
                        .executes(FabricDimensionCommands::teleport)))
        );
    }

    private static int createDimension(CommandContext<CommandSourceStack> ctx) {
        if (manager == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension manager not initialized"));
            return 0;
        }

        String presetName = StringArgumentType.getString(ctx, "preset");

        var result = manager.createDimension(presetName);

        if (result.success()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Created dimension otg:" + result.info().getName() + " (seed: " + result.info().getSeed() + ")\n" +
                    "Server restart required for full activation."
            ), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(result.error()));
            return 0;
        }
    }

    private static int deleteDimension(CommandContext<CommandSourceStack> ctx, boolean purge, boolean confirmed) {
        if (manager == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension manager not initialized"));
            return 0;
        }

        String dimensionName = StringArgumentType.getString(ctx, "dimension");

        var result = manager.deleteDimension(dimensionName, purge, confirmed);

        if (result.needsConfirmation()) {
            ctx.getSource().sendFailure(Component.literal(
                    "WARNING: This will permanently delete all world data for otg:" + result.dimensionName() + "\n" +
                    "Type '/otg dimension delete " + result.dimensionName() + " --purge --confirm' to proceed"
            ));
            return 0;
        }

        if (result.success()) {
            String message = "Deleted dimension otg:" + result.dimensionName();
            if (result.playersRelocated() > 0) {
                message = "Teleported " + result.playersRelocated() + " players to overworld\n" + message;
            }
            if (result.purged()) {
                message += "\nPurged world data";
            }
            final String finalMessage = message;
            ctx.getSource().sendSuccess(() -> Component.literal(finalMessage), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(result.error()));
            return 0;
        }
    }

    private static int listDimensions(CommandContext<CommandSourceStack> ctx) {
        if (manager == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension manager not initialized"));
            return 0;
        }

        List<DimensionInfo> dimensions = manager.listDimensions();

        if (dimensions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No OTG dimensions created"), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("OTG Dimensions (" + dimensions.size() + "):\n");
        for (DimensionInfo dim : dimensions) {
            sb.append("  - otg:").append(dim.getName())
              .append(" (").append(dim.getPreset()).append(")\n");
        }

        final String message = sb.toString().trim();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int dimensionInfo(CommandContext<CommandSourceStack> ctx) {
        if (manager == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension manager not initialized"));
            return 0;
        }

        String dimensionName = StringArgumentType.getString(ctx, "dimension");

        var infoOpt = manager.getDimensionInfo(dimensionName);

        if (infoOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown dimension '" + dimensionName + "'. Use /otg dimension list"
            ));
            return 0;
        }

        DimensionInfo info = infoOpt.get();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        String message = String.format("""
                Dimension: otg:%s
                  Preset: %s
                  Seed: %d
                  Created: %s""",
                info.getName(),
                info.getPreset(),
                info.getSeed(),
                sdf.format(new Date(info.getCreated()))
        );

        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) {
        if (manager == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension manager not initialized"));
            return 0;
        }

        String dimensionName = StringArgumentType.getString(ctx, "dimension");

        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        var infoOpt = manager.getDimensionInfo(dimensionName);
        if (infoOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown dimension '" + dimensionName + "'. Use /otg dimension list"
            ));
            return 0;
        }

        manager.teleportPlayer(player, dimensionName);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Teleported to otg:" + infoOpt.get().getName()
        ), false);
        return 1;
    }
}
