package com.pg85.otg.shared.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pg85.otg.OTG;
import com.pg85.otg.customobject.bo3.BO3;
import com.pg85.otg.customobject.bo4.BO4;
import com.pg85.otg.customobject.structures.CustomStructure;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.customobject.structures.bo3.BO3CustomStructure;
import com.pg85.otg.customobject.structures.bo4.BO4CustomStructure;
import com.pg85.otg.interfaces.IStructuredCustomObject;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.OTGMaterialReader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;

public class StructureCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> otgCommand) {
        otgCommand.then(
            Commands.literal("structure")
                .executes(ctx -> execute(ctx.getSource()))
        );
    }

    private static int execute(CommandSourceStack source) {
        CommandWorldAccessor accessor = OTGCommandRegistrar.getWorldAccessor();
        if (accessor == null) {
            source.sendFailure(Component.literal("Command world accessor not available."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        CustomStructureCache structureCache = accessor.getStructureCache(level);
        if (structureCache == null) {
            source.sendFailure(Component.literal("No OTG structure cache found. Is this an OTG world?"));
            return 0;
        }

        int blockX = (int) source.getPosition().x;
        int blockZ = (int) source.getPosition().z;
        ChunkCoordinate playerChunk = ChunkCoordinate.fromBlockCoords(blockX, blockZ);

        CustomStructure structure = structureCache.getChunkData(playerChunk);
        if (structure == null || structure.start == null) {
            source.sendSuccess(
                () -> Component.literal("No OTG structure found at chunk [" + playerChunk.getChunkX() + ", " + playerChunk.getChunkZ() + "]."),
                false
            );
            return 1;
        }

        Path otgRootFolder = OTG.getEngine().getOTGRootFolder();
        var customObjectManager = OTG.getEngine().getCustomObjectManager();
        var materialReader = OTGMaterialReader.get();
        var resourcesManager = OTG.getEngine().getCustomObjectResourcesManager();
        var modLoadedChecker = OTG.getEngine().getModLoadedChecker();

        IStructuredCustomObject startObject = structure.start.getObject(
            otgRootFolder, customObjectManager, materialReader, resourcesManager, modLoadedChecker
        );

        if (startObject == null) {
            source.sendFailure(Component.literal(
                "Structure found but could not load object '" + structure.start.bo3Name + "'."
            ));
            return 0;
        }

        StringBuilder info = new StringBuilder();

        if (structure instanceof BO4CustomStructure bo4Structure) {
            BO4 bo4 = (BO4) startObject;
            info.append("-- BO4 Info --");
            info.append("\nName: ").append(bo4.getConfig().getName().replace("Start", ""));
            info.append("\nAuthor: ").append(bo4.getConfig().author);
            info.append("\nDescription: ").append(bo4.getConfig().description);

            String branchesInChunk = bo4Structure.getObjectsToSpawnInfo().get(playerChunk);
            if (branchesInChunk != null && !branchesInChunk.isEmpty()) {
                info.append("\n").append(branchesInChunk);
            }
        } else if (structure instanceof BO3CustomStructure) {
            BO3 bo3 = (BO3) startObject;
            info.append("-- BO3 Info --");
            info.append("\nName: ").append(bo3.getConfig().getName().replace("Start", ""));
            info.append("\nAuthor: ").append(bo3.getConfig().author);
            info.append("\nDescription: ").append(bo3.getConfig().description);
        } else {
            info.append("Unknown structure type: ").append(structure.getClass().getSimpleName());
            info.append("\nBO name: ").append(structure.start.bo3Name);
        }

        String result = info.toString();
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
