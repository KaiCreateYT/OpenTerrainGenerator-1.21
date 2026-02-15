package com.pg85.otg.shared.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pg85.otg.OTG;
import com.pg85.otg.constants.settings.structure.CustomStructureType;
import com.pg85.otg.customobject.CustomObject;
import com.pg85.otg.customobject.CustomObjectCollection;
import com.pg85.otg.customobject.bo4.BO4;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.bo3.Rotation;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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

        // Require a player for raycast / position
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();

        CommandWorldAccessor accessor = OTGCommandRegistrar.getWorldAccessor();
        if (accessor == null) {
            source.sendFailure(Component.literal("Command world accessor not available."));
            return 0;
        }

        if (objectToSpawn instanceof BO4 bo4) {
            return executeBo4(source, player, level, accessor, bo4, presetName);
        } else {
            return executeBo3(source, player, level, accessor, objectToSpawn, rotation);
        }
    }

    /**
     * Handles BO4 structure plotting.
     * Branch BO4s (filename matching C[0-9]R[0-9]) are spawned directly at the player's chunk.
     * Full BO4 structures are plotted by searching outward for unpopulated chunks.
     */
    private static int executeBo4(CommandSourceStack source, ServerPlayer player, ServerLevel level,
                                  CommandWorldAccessor accessor, BO4 bo4, String presetName) {
        int playerX = player.getBlockX();
        int playerZ = player.getBlockZ();
        int chunkX = playerX >> 4;
        int chunkZ = playerZ >> 4;

        LocalWorldGenRegion region = accessor.createCommandRegion(level, chunkX, chunkZ);
        if (region == null) {
            source.sendFailure(Component.literal("Could not create world region. Is this an OTG world?"));
            return 0;
        }

        // BO4 structures require CustomStructureType.BO4 in the preset config
        if (region.getPresetConfig().getResourceSettings().getCustomStructureType() != CustomStructureType.BO4) {
            source.sendFailure(Component.literal(
                "Cannot spawn a BO4 structure in a CustomStructureType:BO3 world. " +
                "Use a BO3 instead, or set CustomStructureType:BO4 in the preset config."
            ));
            return 0;
        }

        CustomStructureCache cache = accessor.getStructureCache(level);
        if (cache == null) {
            source.sendFailure(Component.literal("Could not get structure cache. Is this an OTG world?"));
            return 0;
        }

        ChunkCoordinate playerChunk = ChunkCoordinate.fromBlockCoords(playerX, playerZ);

        try {
            // Branch BO4s (filename matches C[0-9]R[0-9]) are individual pieces, not structure starts.
            // Spawn them directly at the player's chunk like a BO3.
            if (bo4.getName().matches(".*C[0-9]([0-9]*)R[0-9]([0-9]*)$")) {
                int x = playerChunk.getBlockX() + bo4.getConfig().getminX();
                int z = playerChunk.getBlockZ() + bo4.getConfig().getminZ();
                int y = region.getHighestBlockAboveYAt(x, z);

                bo4.trySpawnAt(
                    presetName,
                    OTG.getEngine().getOTGRootFolder(),
                    OTG.getEngine().getCustomObjectManager(),
                    OTG.getEngine().getPresetLoader().getMaterialReader(),
                    OTG.getEngine().getCustomObjectResourcesManager(),
                    OTG.getEngine().getModLoadedChecker(),
                    region,
                    new Random(level.getSeed()),
                    Rotation.NORTH,
                    playerChunk,
                    x, y, z,
                    null, // replaceAbove
                    null, // replaceBelow
                    false, // replaceWithBiomeBlocks
                    null, // replaceWithSurfaceBlock
                    null, // replaceWithGroundBlock
                    null, // replaceWithStoneBlock
                    false, // spawnUnderWater
                    region.getCachedBiomeProvider().getBiomeConfig(x, z).getSurfaceSettings().getWaterLevelMax(),
                    false, // isStructureAtSpawn
                    false, // doReplaceAboveBelowOnly
                    false  // doBiomeConfigReplaceBlocks
                );

                source.sendSuccess(
                    () -> Component.literal("Spawned BO4 branch " + bo4.getName() + " at [" + x + ", " + y + ", " + z + "]"),
                    true
                );
                return 1;
            }

            // Full BO4 structure: search outward from the player for unpopulated chunks to plot into
            int maxRadius = 1000;
            source.sendSuccess(
                () -> Component.literal("Plotting BO4 structure " + bo4.getName() + " within " + maxRadius + " chunks of player. This may take a while."),
                false
            );

            for (int cycle = 1; cycle < maxRadius; cycle++) {
                for (int x1 = playerX - cycle; x1 <= playerX + cycle; x1++) {
                    for (int z1 = playerZ - cycle; z1 <= playerZ + cycle; z1++) {
                        // Only check the outer ring of each cycle (spiral outward)
                        if (x1 == playerX - cycle || x1 == playerX + cycle ||
                            z1 == playerZ - cycle || z1 == playerZ + cycle) {

                            ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(
                                playerChunk.getChunkX() + (x1 - playerX),
                                playerChunk.getChunkZ() + (z1 - playerZ)
                            );

                            // Find chunks that haven't been decorated yet so the plotter can claim them.
                            // The plotter also checks internally, but no point spamming it with populated chunks.
                            if (!level.hasChunk(chunkCoord.getChunkX(), chunkCoord.getChunkZ()) ||
                                !level.getChunk(chunkCoord.getChunkX(), chunkCoord.getChunkZ())
                                    .getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {

                                // Create a region centered on the candidate chunk for the plotter
                                LocalWorldGenRegion plotRegion = accessor.createCommandRegion(
                                    level, chunkCoord.getChunkX(), chunkCoord.getChunkZ()
                                );
                                if (plotRegion == null) {
                                    continue;
                                }

                                ChunkCoordinate spawnedAt = cache.plotBo4Structure(
                                    plotRegion,
                                    bo4,
                                    new ArrayList<>(), // targetBiomes (empty = any biome)
                                    chunkCoord,
                                    OTG.getEngine().getOTGRootFolder(),
                                    OTG.getEngine().getCustomObjectManager(),
                                    OTG.getEngine().getPresetLoader().getMaterialReader(),
                                    OTG.getEngine().getCustomObjectResourcesManager(),
                                    OTG.getEngine().getModLoadedChecker(),
                                    false // force (respect height bounds)
                                );

                                if (spawnedAt != null) {
                                    int spawnX = spawnedAt.getBlockX();
                                    int spawnZ = spawnedAt.getBlockZ();
                                    source.sendSuccess(
                                        () -> Component.literal(bo4.getName() + " was plotted at [" + spawnX + ", ~, " + spawnZ + "]. " +
                                            "Use /tp @s " + spawnX + " ~ " + spawnZ + " to teleport there."),
                                        true
                                    );
                                    return 1;
                                }
                            }
                        }
                    }
                }
            }

            source.sendFailure(Component.literal(
                bo4.getName() + " could not be plotted. Possible reasons: world is currently generating chunks, " +
                "no biomes with enough space could be found, or there is an error in the structure's files. " +
                "Enable SpawnLog:true in OTG.ini and check the logs for more information."
            ));
            return 0;

        } catch (Exception e) {
            source.sendFailure(Component.literal("Error spawning BO4: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Handles BO3 (and BO2) spawning via raycast + spawnForced.
     */
    private static int executeBo3(CommandSourceStack source, ServerPlayer player, ServerLevel level,
                                  CommandWorldAccessor accessor, CustomObject objectToSpawn, Rotation rotation) {
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

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        LocalWorldGenRegion region = accessor.createCommandRegion(level, chunkX, chunkZ);
        if (region == null) {
            source.sendFailure(Component.literal("Could not create world region. Is this an OTG world?"));
            return 0;
        }

        try {
            boolean success = objectToSpawn.spawnForced(
                accessor.getStructureCache(level),
                region,
                new Random(level.getSeed()),
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
