package com.pg85.otg.shared.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.config.settings.biome.BiomeStructureTagConfig;
import com.pg85.otg.gen.biome.UndergroundBiomeResolver;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.interfaces.ILayerSampler;
import com.pg85.otg.shared.biome.SharedOTGBiomeProvider;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class MapCommand {

    private static final int MAP_SIZE = 512;
    private static final int BLOCKS_PER_PIXEL = 4;
    private static final int UNDERGROUND_PASSTHROUGH_COLOR = 0;
    private static final Map<String, Integer> STRUCTURE_COLORS = new LinkedHashMap();
    private static final List<String> STRUCTURE_PRIORITY;

    public MapCommand() {}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> otgCommand) {
        otgCommand.then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("map").requires((sourcex) -> {
            return sourcex.hasPermission(2);
        })).executes((ctxx) -> {
            return execute((CommandSourceStack) ctxx.getSource(), "surface");
        })).then(Commands.argument("mode", StringArgumentType.word()).suggests((ctxx, builderx) -> {
            return SharedSuggestionProvider.suggest(new String[]{"surface", "structures", "underground"}, builderx);
        }).executes((ctxx) -> {
            return execute((CommandSourceStack) ctxx.getSource(), StringArgumentType.getString(ctxx, "mode"));
        })));
    }

    private static int execute(CommandSourceStack source, String mode) {
        Entity entity = source.getEntity();

        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        } else {
            ServerLevel serverlevel = source.getLevel();
            ChunkGenerator generator = serverlevel.getChunkSource().getGenerator();
            BiomeSource biomeSource = generator.getBiomeSource();

            if (!(biomeSource instanceof SharedOTGBiomeProvider otgBiomeProvider)) {
                source.sendFailure(Component.literal("The current dimension does not use an OTG biome provider."));
                return 0;
            } else {
                CommandWorldAccessor accessor = OTGCommandRegistrar.getWorldAccessor();

                if (accessor == null) {
                    source.sendFailure(Component.literal("Command world accessor not available."));
                    return 0;
                } else {
                    String presetFolderName = accessor.getPresetFolderName(serverlevel);

                    if (presetFolderName == null) {
                        source.sendFailure(Component.literal("Could not determine OTG preset for this dimension."));
                        return 0;
                    } else {
                        IBiome[] iBiomes = OTG.getEngine().getDimensionPresetLoader().getGlobalIdMapping(presetFolderName);

                        if (iBiomes == null) {
                            source.sendFailure(Component.literal("No biome mapping found for preset '" + presetFolderName + "'."));
                            return 0;
                        } else {
                            int playerX = player.getBlockX();
                            int playerZ = player.getBlockZ();
                            int radius = 1024;
                            int centerX = playerX;
                            int centerZ = playerZ;
                            BufferedImage image = new BufferedImage(512, 512, 1);

                            try {
                                switch (mode) {
                                    case "structures":
                                        renderStructures(image, iBiomes, otgBiomeProvider, centerX, centerZ, radius);
                                        break;
                                    case "underground":
                                        renderUnderground(image, iBiomes, otgBiomeProvider, centerX, centerZ, radius);
                                        break;
                                    default:
                                        renderSurface(image, iBiomes, otgBiomeProvider, centerX, centerZ, radius);
                                }
                            } catch (Exception exception) {
                                source.sendFailure(Component.literal("Error generating map: " + exception.getMessage()));
                                return 0;
                            }

                            Path e = OTG.getEngine().getOTGRootFolder();
                            File file = e.resolve("maps").toFile();

                            file.mkdirs();
                            String timestamp = (new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date());
                            File outputFile = new File(file, "otg_map_" + mode + "_" + timestamp + ".png");

                            for (int attempt = 0; outputFile.exists(); outputFile = new File(file, "otg_map_" + mode + "_" + timestamp + "_" + attempt + ".png")) {
                                ++attempt;
                            }

                            try {
                                ImageIO.write(image, "PNG", outputFile);
                            } catch (IOException ioexception) {
                                source.sendFailure(Component.literal("Failed to write map image: " + ioexception.getMessage()));
                                return 0;
                            }

                            String finalMode = mode;
                            File finalOutputFile = outputFile;
                            source.sendSuccess(() -> {
                                return Component.literal("Saved " + finalMode + " map to " + finalOutputFile.getAbsolutePath());
                            }, true);
                            return 1;
                        }
                    }
                }
            }
        }
    }

    private static BiomeSettings getBiomeSettingsForId(IBiome[] iBiomes, int otgBiomeId) {
        if (otgBiomeId >= 0 && otgBiomeId < iBiomes.length) {
            IBiome biome = iBiomes[otgBiomeId];

            return biome != null ? biome.getBiomeSettings() : null;
        } else {
            return null;
        }
    }

    private static int sampleBiomeId(SharedOTGBiomeProvider otgBiomeProvider, int noiseX, int noiseZ) {
        ILayerSampler sampler = otgBiomeProvider.getSampler();

        return sampler == null ? -1 : sampler.sample(noiseX, noiseZ);
    }

    private static void renderSurface(BufferedImage image, IBiome[] iBiomes, SharedOTGBiomeProvider otgBiomeProvider, int centerX, int centerZ, int radius) {
        for (int px = 0; px < 512; ++px) {
            for (int pz = 0; pz < 512; ++pz) {
                int worldX = centerX - radius + px * 4 + 2;
                int worldZ = centerZ - radius + pz * 4 + 2;
                int noiseX = worldX >> 2;
                int noiseZ = worldZ >> 2;
                int biomeId = sampleBiomeId(otgBiomeProvider, noiseX, noiseZ);

                if (biomeId < 0) {
                    image.setRGB(px, pz, -16759808);
                } else {
                    BiomeSettings settings = getBiomeSettingsForId(iBiomes, biomeId);
                    int color = 17408;

                    if (settings != null) {
                        color = settings.getGenerationSettings().getBiomeMapColor().getColor() & 16777215;
                    }

                    if (color == 0) {
                        color = 17408;
                    }

                    image.setRGB(px, pz, -16777216 | color);
                }
            }
        }

    }

    private static void renderStructures(BufferedImage image, IBiome[] iBiomes, SharedOTGBiomeProvider otgBiomeProvider, int centerX, int centerZ, int radius) {
        for (int px = 0; px < 512; ++px) {
            for (int pz = 0; pz < 512; ++pz) {
                int worldX = centerX - radius + px * 4 + 2;
                int worldZ = centerZ - radius + pz * 4 + 2;
                int noiseX = worldX >> 2;
                int noiseZ = worldZ >> 2;
                int biomeId = sampleBiomeId(otgBiomeProvider, noiseX, noiseZ);
                int color;

                if (biomeId >= 0) {
                    BiomeSettings settings = getBiomeSettingsForId(iBiomes, biomeId);

                    if (settings != null) {
                        color = getStructureColor(settings);
                    } else {
                        color = 0;
                    }
                } else {
                    color = 0;
                }

                image.setRGB(px, pz, -16777216 | color);
            }
        }

    }

    private static int getStructureColor(BiomeSettings settings) {
        BiomeStructureTagConfig tags = settings.getBiomeStructureTagConfig();

        if (tags == null) {
            return settings.getGenerationSettings().getBiomeMapColor().getColor() & 16777215;
        } else {
            for (String structName : MapCommand.STRUCTURE_PRIORITY) {
                if (isStructureEnabled(tags, structName)) {
                    return (Integer) MapCommand.STRUCTURE_COLORS.get(structName);
                }
            }

            return settings.getGenerationSettings().getBiomeMapColor().getColor() & 16777215;
        }
    }

    private static boolean isStructureEnabled(BiomeStructureTagConfig tags, String name) {
        boolean flag;

        switch (name) {
            case "ancientCity":
                flag = tags.isAncientCity();
                break;
            case "woodlandMansion":
                flag = tags.isWoodlandMansion();
                break;
            case "trialChambers":
                flag = tags.isTrialChambers();
                break;
            case "endCity":
                flag = tags.isEndCity();
                break;
            case "oceanMonument":
                flag = tags.isOceanMonument();
                break;
            case "stronghold":
                flag = tags.isStronghold();
                break;
            case "villagePlains":
                flag = tags.isVillagePlains();
                break;
            case "villageDesert":
                flag = tags.isVillageDesert();
                break;
            case "villageSavanna":
                flag = tags.isVillageSavanna();
                break;
            case "villageTaiga":
                flag = tags.isVillageTaiga();
                break;
            case "villageSnowy":
                flag = tags.isVillageSnowy();
                break;
            case "pillagerOutpost":
                flag = tags.isPillagerOutpost();
                break;
            case "desertPyramid":
                flag = tags.isDesertPyramid();
                break;
            case "jungleTemple":
                flag = tags.isJungleTemple();
                break;
            case "swampHut":
                flag = tags.isSwampHut();
                break;
            case "igloo":
                flag = tags.isIgloo();
                break;
            case "shipwreck":
                flag = tags.isShipwreck();
                break;
            case "oceanRuinWarm":
                flag = tags.isOceanRuinWarm();
                break;
            case "oceanRuinCold":
                flag = tags.isOceanRuinCold();
                break;
            case "buriedTreasure":
                flag = tags.isBuriedTreasure();
                break;
            case "mineshaft":
                flag = tags.isMineshaft();
                break;
            case "mineshaftMesa":
                flag = tags.isMineshaftMesa();
                break;
            case "ruinedPortalStandard":
                flag = tags.isRuinedPortalStandard();
                break;
            case "ruinedPortalDesert":
                flag = tags.isRuinedPortalDesert();
                break;
            case "ruinedPortalJungle":
                flag = tags.isRuinedPortalJungle();
                break;
            case "netherFortress":
                flag = tags.isNetherFortress();
                break;
            case "bastionRemnant":
                flag = tags.isBastionRemnant();
                break;
            case "netherFossil":
                flag = tags.isNetherFossil();
                break;
            case "trailRuins":
                flag = tags.isTrailRuins();
                break;
            default:
                flag = false;
        }

        return flag;
    }

    private static void renderUnderground(BufferedImage image, IBiome[] iBiomes, SharedOTGBiomeProvider otgBiomeProvider, int centerX, int centerZ, int radius) {
        int sampleY = -20;
        UndergroundBiomeResolver undergroundResolver = otgBiomeProvider.getUndergroundResolver();
        boolean hasUnderground = undergroundResolver != null && undergroundResolver.hasUndergroundBiomes();

        for (int px = 0; px < 512; ++px) {
            for (int pz = 0; pz < 512; ++pz) {
                int worldX = centerX - radius + px * 4 + 2;
                int worldZ = centerZ - radius + pz * 4 + 2;
                int noiseX = worldX >> 2;
                int noiseZ = worldZ >> 2;
                int surfaceBiomeId = sampleBiomeId(otgBiomeProvider, noiseX, noiseZ);

                if (surfaceBiomeId >= 0 && surfaceBiomeId < iBiomes.length) {
                    int undergroundBiomeId = -1;

                    if (hasUnderground) {
                        undergroundBiomeId = undergroundResolver.resolve(surfaceBiomeId, worldX, sampleY, worldZ, 64);
                    }

                    if (undergroundBiomeId >= 0 && undergroundBiomeId < iBiomes.length && iBiomes[undergroundBiomeId] != null) {
                        IBiome ugBiome = iBiomes[undergroundBiomeId];
                        int color = ugBiome.getBiomeSettings().getGenerationSettings().getBiomeMapColor().getColor() & 16777215;

                        image.setRGB(px, pz, -16777216 | color);
                    } else {
                        image.setRGB(px, pz, -16777216);
                    }
                } else {
                    image.setRGB(px, pz, -16777216);
                }
            }
        }

    }

    static {
        MapCommand.STRUCTURE_COLORS.put("ancientCity", 2236962);
        MapCommand.STRUCTURE_COLORS.put("woodlandMansion", 8912896);
        MapCommand.STRUCTURE_COLORS.put("trialChambers", 13421568);
        MapCommand.STRUCTURE_COLORS.put("endCity", 65416);
        MapCommand.STRUCTURE_COLORS.put("oceanMonument", 17663);
        MapCommand.STRUCTURE_COLORS.put("stronghold", 4473924);
        MapCommand.STRUCTURE_COLORS.put("villagePlains", 16737894);
        MapCommand.STRUCTURE_COLORS.put("villageDesert", 16755268);
        MapCommand.STRUCTURE_COLORS.put("villageSavanna", 6750054);
        MapCommand.STRUCTURE_COLORS.put("villageTaiga", 4491519);
        MapCommand.STRUCTURE_COLORS.put("villageSnowy", 13421823);
        MapCommand.STRUCTURE_COLORS.put("pillagerOutpost", 8947848);
        MapCommand.STRUCTURE_COLORS.put("desertPyramid", 16777028);
        MapCommand.STRUCTURE_COLORS.put("jungleTemple", 4521864);
        MapCommand.STRUCTURE_COLORS.put("swampHut", 8930440);
        MapCommand.STRUCTURE_COLORS.put("igloo", 13434879);
        MapCommand.STRUCTURE_COLORS.put("shipwreck", 8965324);
        MapCommand.STRUCTURE_COLORS.put("oceanRuinWarm", 4491434);
        MapCommand.STRUCTURE_COLORS.put("oceanRuinCold", 4482696);
        MapCommand.STRUCTURE_COLORS.put("buriedTreasure", 16766720);
        MapCommand.STRUCTURE_COLORS.put("mineshaft", 6702114);
        MapCommand.STRUCTURE_COLORS.put("mineshaftMesa", 11171652);
        MapCommand.STRUCTURE_COLORS.put("ruinedPortalStandard", 8913151);
        MapCommand.STRUCTURE_COLORS.put("ruinedPortalDesert", 11158783);
        MapCommand.STRUCTURE_COLORS.put("ruinedPortalJungle", 6728447);
        MapCommand.STRUCTURE_COLORS.put("netherFortress", 16711680);
        MapCommand.STRUCTURE_COLORS.put("bastionRemnant", 4456448);
        MapCommand.STRUCTURE_COLORS.put("netherFossil", 8939076);
        MapCommand.STRUCTURE_COLORS.put("trailRuins", 9127187);
        STRUCTURE_PRIORITY = List.copyOf(MapCommand.STRUCTURE_COLORS.keySet());
    }
}
