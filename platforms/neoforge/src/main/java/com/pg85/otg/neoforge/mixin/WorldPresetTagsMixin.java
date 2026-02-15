package com.pg85.otg.neoforge.mixin;

import com.pg85.otg.config.settings.biome.BiomeStructureTagConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.neoforge.biome.LegacyNeoForgeBiomeLoader;
import com.pg85.otg.neoforge.mixin.util.RegistryUtil;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.biome.StructureTagMapper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(ReloadableServerResources.class)
public class WorldPresetTagsMixin {
    @Inject(method = "updateRegistryTags()V", at = @At("RETURN"))
    private void addOurOwnTags(CallbackInfo ci) {
        ReloadableServerResources self = (ReloadableServerResources) (Object) this;
        RegistryAccess registryAccess = self.fullRegistries().get();
        OTGLog.getLogger().info("Adding OTG presets to the world preset tags");
        var presets = registryAccess.registryOrThrow(Registries.WORLD_PRESET);
        // get all the tags from the presets registry
        var tags = presets.getTags();
        HashMap<TagKey<WorldPreset>, List<Holder<WorldPreset>>> collected = tags.collect(
                HashMap::new,
                (tagMap, pair) -> tagMap.put(pair.getFirst(), new ArrayList<>(pair.getSecond().stream().toList())),
                HashMap::putAll);
        // add our own presets to the tags
        presets.keySet().forEach(id -> {
            // check it's in the OTG name space
            if (!id.getNamespace().equalsIgnoreCase(Constants.MOD_ID_SHORT)) {
                return;
            }
            // can't use direct holders, need reference
            Holder.Reference<WorldPreset> holder = getAsReference(presets, id).orElse(null);
            if (holder == null) {
                OTGLog.getLogger().error("Preset %s does not exist!", id.toString());
                return;
            }
            OTGLog.getLogger().info("Adding preset %s to the tags", id.toString());
            collected.computeIfAbsent(WorldPresetTags.NORMAL, tag -> new ArrayList<>())
                    .add(holder);
        });

        presets.bindTags(collected);

        addBiomesToStructureTags(registryAccess);
    }

    private void addBiomesToStructureTags(RegistryAccess registryAccess) {
        Map<ResourceKey<Biome>, BiomeStructureTagConfig> structureConfigs =
                LegacyNeoForgeBiomeLoader.getStructureTagConfigs();

        if (structureConfigs.isEmpty()) return;

        var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        // Collect ALL existing biome tags into mutable map
        // bindTags() replaces everything, so we MUST preserve existing tags
        var tags = biomeRegistry.getTags();
        HashMap<TagKey<Biome>, List<Holder<Biome>>> biomeTagMap = tags.collect(
                HashMap::new,
                (tagMap, pair) -> tagMap.put(pair.getFirst(), new ArrayList<>(pair.getSecond().stream().toList())),
                HashMap::putAll
        );

        int addedCount = 0;
        for (var entry : structureConfigs.entrySet()) {
            ResourceKey<Biome> biomeKey = entry.getKey();
            BiomeStructureTagConfig config = entry.getValue();

            Optional<Holder.Reference<Biome>> holderOpt = biomeRegistry.getHolder(biomeKey);
            if (holderOpt.isEmpty()) {
                OTGLog.getLogger().warn("Could not find holder for biome {} when injecting structure tags", biomeKey.location());
                continue;
            }
            Holder.Reference<Biome> holder = holderOpt.get();

            List<String> structureTags = StructureTagMapper.getStructureTags(config);
            for (String tagPath : structureTags) {
                TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, ResourceLocation.parse(tagPath));
                biomeTagMap.computeIfAbsent(tagKey, k -> new ArrayList<>()).add(holder);
                addedCount++;
            }
        }

        biomeRegistry.bindTags(biomeTagMap);
        OTGLog.getLogger().info("Injected {} structure tag entries for {} OTG biomes",
                addedCount, structureConfigs.size());
    }

    private static <T> Optional<Holder.Reference<T>> getAsReference(Registry<T> registry, ResourceLocation key) {
        return registry.getOptional(key)
                .flatMap(registry::getResourceKey)
                .flatMap(registry::getHolder);
    }
}
