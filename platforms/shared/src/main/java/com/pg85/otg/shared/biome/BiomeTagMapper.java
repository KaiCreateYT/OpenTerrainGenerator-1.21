package com.pg85.otg.shared.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps OTG modtag.* selectors to conventional tags (c:*) and vanilla tags (minecraft:is_*).
 */
public class BiomeTagMapper {

    // OTG modtag.* -> c:* or minecraft:is_* mapping
    private static final Map<String, String[]> TAG_MAPPING = new HashMap<>();

    static {
        // Each OTG tag maps to one or more Fabric/vanilla tags (checked with OR logic)
        TAG_MAPPING.put("water", new String[]{"c:aquatic", "minecraft:is_ocean", "minecraft:is_river"});
        TAG_MAPPING.put("mountain", new String[]{"c:mountain", "minecraft:is_mountain"});
        TAG_MAPPING.put("hills", new String[]{"c:extreme_hills", "minecraft:is_hill"});
        TAG_MAPPING.put("plains", new String[]{"c:plains"});
        TAG_MAPPING.put("forest", new String[]{"c:forest", "minecraft:is_forest"});
        TAG_MAPPING.put("lush", new String[]{"c:climate_wet", "c:vegetation_dense", "c:swamp"});
        TAG_MAPPING.put("sandy", new String[]{"c:desert", "c:badlands", "minecraft:is_badlands"});
        TAG_MAPPING.put("jungle", new String[]{"c:jungle", "minecraft:is_jungle"});
        TAG_MAPPING.put("overworld", new String[]{"c:in_overworld", "minecraft:is_overworld"});
        TAG_MAPPING.put("nether", new String[]{"c:in_nether", "minecraft:is_nether"});
        TAG_MAPPING.put("end", new String[]{"c:in_the_end", "minecraft:is_end"});
        TAG_MAPPING.put("taiga", new String[]{"c:taiga", "minecraft:is_taiga"});
        TAG_MAPPING.put("savanna", new String[]{"c:savanna", "minecraft:is_savanna"});
        TAG_MAPPING.put("beach", new String[]{"c:beach", "minecraft:is_beach"});
        TAG_MAPPING.put("snowy", new String[]{"c:snowy", "c:climate_cold"});
    }

    /**
     * Check if a biome has the specified OTG tag.
     *
     * @param biomeRegistry The biome registry
     * @param biomeKey The biome's resource key
     * @param otgTag The OTG tag name (without modtag. prefix), e.g., "water", "mountain"
     * @return true if biome matches any of the mapped tags
     */
    public static boolean biomeHasTag(Registry<Biome> biomeRegistry, ResourceKey<Biome> biomeKey, String otgTag) {
        String key = otgTag.toLowerCase(Locale.ROOT).trim();
        String[] fabricTags = TAG_MAPPING.get(key);

        Optional<Holder.Reference<Biome>> holderOpt = biomeRegistry.get(biomeKey);
        if (holderOpt.isEmpty()) {
            return false;
        }
        Holder.Reference<Biome> holder = holderOpt.get();

        if (fabricTags != null) {
            for (String fabricTag : fabricTags) {
                TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, ResourceLocation.parse(fabricTag));
                if (holder.is(tagKey)) {
                    return true;
                }
            }
            return false;
        }

        // Full registry tag id (e.g. tag.minecraft:is_forest → includeTags entry "minecraft:is_forest")
        if (!key.contains(":")) {
            return false;
        }
        try {
            String path = key.startsWith("#") ? key.substring(1) : key;
            TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, ResourceLocation.parse(path));
            return holder.is(tagKey);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get tag keys for an OTG tag.
     */
    @SuppressWarnings("unchecked")
    public static TagKey<Biome>[] getTagKeys(String otgTag) {
        String[] fabricTags = TAG_MAPPING.get(otgTag.toLowerCase());
        if (fabricTags == null) {
            return new TagKey[0];
        }

        TagKey<Biome>[] keys = new TagKey[fabricTags.length];
        for (int i = 0; i < fabricTags.length; i++) {
            keys[i] = TagKey.create(Registries.BIOME, ResourceLocation.parse(fabricTags[i]));
        }
        return keys;
    }
}
