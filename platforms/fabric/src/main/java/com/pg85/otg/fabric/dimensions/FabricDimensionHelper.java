package com.pg85.otg.fabric.dimensions;

import com.pg85.otg.fabric.biome.OTGFabricBiomeProvider;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.mixin.MappedRegistryAccessor;
import com.pg85.otg.fabric.mixin.MinecraftServerAccessor;
import com.pg85.otg.shared.dimensions.SharedDimensionHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.Map;
import java.util.concurrent.Executor;

public class FabricDimensionHelper extends SharedDimensionHelper {

    @Override
    protected Map<ResourceKey<Level>, ServerLevel> getLevels(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).getLevels();
    }

    @Override
    protected Executor getExecutor(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).getExecutor();
    }

    @Override
    protected LevelStorageSource.LevelStorageAccess getStorageSource(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).getStorageSource();
    }

    @Override
    protected boolean isRegistryFrozen(MappedRegistry<?> registry) {
        return ((MappedRegistryAccessor) registry).isFrozen();
    }

    @Override
    protected void setRegistryFrozen(MappedRegistry<?> registry, boolean frozen) {
        ((MappedRegistryAccessor) registry).setFrozen(frozen);
    }

    @Override
    protected LevelStem createOTGLevelStem(
            String presetName, long seed,
            Holder<DimensionType> dimTypeHolder,
            Holder<NoiseGeneratorSettings> noiseSettings,
            Registry<Biome> biomeRegistry
    ) {
        OTGFabricChunkGenerator chunkGenerator = new OTGFabricChunkGenerator(
                new OTGFabricBiomeProvider(presetName, seed),
                noiseSettings,
                biomeRegistry
        );
        return new LevelStem(dimTypeHolder, chunkGenerator);
    }
}
