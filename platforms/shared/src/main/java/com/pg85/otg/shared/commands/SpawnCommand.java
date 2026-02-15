package com.pg85.otg.shared.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pg85.otg.OTG;
import com.pg85.otg.customobject.CustomObject;
import com.pg85.otg.customobject.CustomObjectCollection;
import com.pg85.otg.util.bo3.Rotation;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpawnCommand {

    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (ctx, builder) -> {
        List<String> names = new ArrayList<>(OTG.getEngine().getPresetLoader().getAllPresetFolderNames());
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> OBJECT_SUGGESTIONS = (ctx, builder) -> {
        String presetName;
        try {
            presetName = StringArgumentType.getString(ctx, "preset");
        } catch (IllegalArgumentException e) {
            presetName = OTG.getEngine().getPresetLoader().getDefaultPresetFolderName();
        }

        CustomObjectCollection objects = OTG.getEngine().getCustomObjectManager().getGlobalObjects();
        List<String> objectNames = new ArrayList<>();

        // Add preset-specific objects
        ArrayList<String> presetObjects = objects.getAllBONamesForPreset(presetName, OTG.getEngine().getOTGRootFolder());
        if (presetObjects != null) {
            objectNames.addAll(presetObjects);
        }

        // Add global objects
        ArrayList<String> globalObjects = objects.getGlobalObjectNames(OTG.getEngine().getOTGRootFolder());
        if (globalObjects != null) {
            objectNames.addAll(globalObjects);
        }

        return SharedSuggestionProvider.suggest(objectNames, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> ROTATION_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"NORTH", "SOUTH", "EAST", "WEST"}, builder);

    public static void register(LiteralArgumentBuilder<CommandSourceStack> otgCommand) {
        otgCommand.then(
            Commands.literal("spawn")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("preset", StringArgumentType.string())
                        .suggests(PRESET_SUGGESTIONS)
                        .then(
                            Commands.argument("object", StringArgumentType.string())
                                .suggests(OBJECT_SUGGESTIONS)
                                .executes(ctx -> execute(ctx, "NORTH"))
                                .then(
                                    Commands.argument("rotation", StringArgumentType.word())
                                        .suggests(ROTATION_SUGGESTIONS)
                                        .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "rotation")))
                                )
                        )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, String rotationStr) {
        CommandSourceStack source = ctx.getSource();
        String presetName = StringArgumentType.getString(ctx, "preset");
        String objectName = StringArgumentType.getString(ctx, "object");

        // Validate rotation
        Rotation rotation;
        try {
            rotation = Rotation.valueOf(rotationStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid rotation: " + rotationStr + ". Use NORTH, SOUTH, EAST, or WEST."));
            return 0;
        }

        // Find the custom object
        CustomObject objectToSpawn = OTG.getEngine().getCustomObjectManager().getGlobalObjects().getObjectByName(
            objectName,
            presetName,
            OTG.getEngine().getOTGRootFolder(),
            OTG.getEngine().getCustomObjectManager(),
            OTG.getEngine().getPresetLoader().getMaterialReader(),
            OTG.getEngine().getCustomObjectResourcesManager(),
            OTG.getEngine().getModLoadedChecker()
        );

        if (objectToSpawn == null) {
            source.sendFailure(Component.literal("Could not find object '" + objectName + "' in preset '" + presetName + "' or GlobalObjects."));
            return 0;
        }

        // Require a player for raycast
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        // Raycast from player's eye position (200 block range)
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.x * 200.0, lookVec.y * 200.0, lookVec.z * 200.0);

        BlockHitResult hitResult = player.level().clip(new ClipContext(
            eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        ));

        if (hitResult.getType() == HitResult.Type.MISS) {
            source.sendFailure(Component.literal("No block in range (200 blocks). Look at a block and try again."));
            return 0;
        }

        int x = hitResult.getBlockPos().getX();
        int y = hitResult.getBlockPos().getY() + 1; // On top of hit block
        int z = hitResult.getBlockPos().getZ();

        // Create command-time world region
        ServerLevel level = source.getLevel();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        CommandWorldAccessor accessor = OTGCommandRegistrar.getWorldAccessor();
        if (accessor == null) {
            source.sendFailure(Component.literal("Command world accessor not available."));
            return 0;
        }

        LocalWorldGenRegion region = accessor.createCommandRegion(level, chunkX, chunkZ);
        if (region == null) {
            source.sendFailure(Component.literal("Could not create world region. Is this an OTG world?"));
            return 0;
        }

        // Spawn the object
        try {
            boolean success = objectToSpawn.spawnForced(
                accessor.getStructureCache(level),
                region,
                new Random(),
                rotation,
                x, y, z,
                true // allowReplaceBlocks
            );

            if (success) {
                source.sendSuccess(
                    () -> Component.literal("Spawned " + objectToSpawn.getName() + " at [" + x + ", " + y + ", " + z + "] facing " + rotation.name()),
                    true
                );
                return 1;
            } else {
                source.sendFailure(Component.literal("Failed to spawn " + objectToSpawn.getName() + " at [" + x + ", " + y + ", " + z + "]. Check SpawnLog for details."));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error spawning object: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
