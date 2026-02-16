package com.pg85.otg.neoforge.dimensions;

import com.pg85.otg.neoforge.biome.OTGNeoForgeBiomeProvider;
import com.pg85.otg.neoforge.gen.OTGNeoForgeChunkGenerator;
import com.pg85.otg.neoforge.mixin.MappedRegistryAccessor;
import com.pg85.otg.neoforge.mixin.MinecraftServerAccessor;
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

public class NeoForgeDimensionHelper extends SharedDimensionHelper {

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
        OTGNeoForgeChunkGenerator chunkGenerator = new OTGNeoForgeChunkGenerator(
                new OTGNeoForgeBiomeProvider(presetName, seed),
                noiseSettings,
                biomeRegistry
        );
        return new LevelStem(dimTypeHolder, chunkGenerator);
    }
}
