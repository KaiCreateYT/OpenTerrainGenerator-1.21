package com.pg85.otg.fabric.util;

import com.pg85.otg.fabric.gen.FabricWorldGenRegion;
import com.pg85.otg.shared.util.SharedNBTHelper;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.nbt.NamedBinaryTag;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FabricNBTHelper extends SharedNBTHelper {

    @Override
    public NamedBinaryTag getNBTFromLocation(LocalWorldGenRegion world, int x, int y, int z) {
        BlockEntity blockEntity = ((FabricWorldGenRegion) world).getBlockEntity(new BlockPos(x, y, z));

        if (blockEntity == null) {
            return null;
        }

        CompoundTag nbt = blockEntity.saveCustomOnly(blockEntity.getLevel().registryAccess());
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");

        return getNBTFromNMSTagCompound(null, nbt);
    }
}
