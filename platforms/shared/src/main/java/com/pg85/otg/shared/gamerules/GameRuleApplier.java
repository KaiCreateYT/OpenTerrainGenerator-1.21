package com.pg85.otg.shared.gamerules;

import com.mojang.serialization.Dynamic;
import com.pg85.otg.config.dimensions.DimensionConfig;
import com.pg85.otg.config.settings.preset.GameRuleSettings;
import com.pg85.otg.util.OTGLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates MC GameRules from OTG config (PresetConfig + optional DimensionConfig override).
 */
public final class GameRuleApplier {

    private GameRuleApplier() {}

    /**
     * Creates a new GameRules instance populated from PresetConfig settings
     * with optional DimensionConfig overrides.
     */
    public static GameRules createGameRules(
            GameRuleSettings presetRules,
            @Nullable DimensionConfig.GameRules overrides,
            MinecraftServer server
    ) {
        GameRules rules = new GameRules();

        if (!presetRules.isOverrideGameRules()) {
            OTGLog.info("OverrideGameRules is false — using vanilla defaults");
            return rules;
        }

        applyFromPreset(rules, presetRules, server);

        if (overrides != null) {
            OTGLog.info("Applying DimensionConfig GameRules overrides");
            applyFromDimensionConfig(rules, overrides, server);
        }

        return rules;
    }

    private static void applyFromPreset(GameRules rules, GameRuleSettings s, MinecraftServer server) {
        // Boolean rules
        rules.getRule(GameRules.RULE_DOFIRETICK).set(s.isDoFireTick(), server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(s.isMobGriefing(), server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(s.isKeepInventory(), server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(s.isDoMobSpawning(), server);
        rules.getRule(GameRules.RULE_DOMOBLOOT).set(s.isDoMobLoot(), server);
        rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(s.isDoTileDrops(), server);
        rules.getRule(GameRules.RULE_DOENTITYDROPS).set(s.isDoEntityDrops(), server);
        rules.getRule(GameRules.RULE_COMMANDBLOCKOUTPUT).set(s.isCommandBlockOutput(), server);
        rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(s.isNaturalRegeneration(), server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(s.isDoDaylightCycle(), server);
        rules.getRule(GameRules.RULE_LOGADMINCOMMANDS).set(s.isLogAdminCommands(), server);
        rules.getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(s.isShowDeathMessages(), server);
        rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(s.isSendCommandFeedback(), server);
        rules.getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(s.isSpectatorsGenerateChunks(), server);
        rules.getRule(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK).set(s.isDisableElytraMovementCheck(), server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(s.isDoWeatherCycle(), server);
        rules.getRule(GameRules.RULE_LIMITED_CRAFTING).set(s.isDoLimitedCrafting(), server);
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(s.isAnnounceAdvancements(), server);
        rules.getRule(GameRules.RULE_DISABLE_RAIDS).set(s.isDisableRaids(), server);
        rules.getRule(GameRules.RULE_DOINSOMNIA).set(s.isDoInsomnia(), server);
        rules.getRule(GameRules.RULE_DROWNING_DAMAGE).set(s.isDrowningDamage(), server);
        rules.getRule(GameRules.RULE_FALL_DAMAGE).set(s.isFallDamage(), server);
        rules.getRule(GameRules.RULE_FIRE_DAMAGE).set(s.isFireDamage(), server);
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(s.isDoPatrolSpawning(), server);
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(s.isDoTraderSpawning(), server);
        rules.getRule(GameRules.RULE_FORGIVE_DEAD_PLAYERS).set(s.isForgiveDeadPlayers(), server);
        rules.getRule(GameRules.RULE_UNIVERSAL_ANGER).set(s.isUniversalAnger(), server);
        // New 1.21.1 boolean rules
        rules.getRule(GameRules.RULE_PROJECTILESCANBREAKBLOCKS).set(s.isProjectilesCanBreakBlocks(), server);
        rules.getRule(GameRules.RULE_REDUCEDDEBUGINFO).set(s.isReducedDebugInfo(), server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(s.isDoImmediateRespawn(), server);
        rules.getRule(GameRules.RULE_FREEZE_DAMAGE).set(s.isFreezeDamage(), server);
        rules.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(s.isDoWardenSpawning(), server);
        rules.getRule(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY).set(s.isBlockExplosionDropDecay(), server);
        rules.getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(s.isMobExplosionDropDecay(), server);
        rules.getRule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY).set(s.isTntExplosionDropDecay(), server);
        rules.getRule(GameRules.RULE_WATER_SOURCE_CONVERSION).set(s.isWaterSourceConversion(), server);
        rules.getRule(GameRules.RULE_LAVA_SOURCE_CONVERSION).set(s.isLavaSourceConversion(), server);
        rules.getRule(GameRules.RULE_GLOBAL_SOUND_EVENTS).set(s.isGlobalSoundEvents(), server);
        rules.getRule(GameRules.RULE_DO_VINES_SPREAD).set(s.isDoVinesSpread(), server);
        rules.getRule(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH).set(s.isEnderPearlsVanishOnDeath(), server);

        // Integer rules
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(s.getRandomTickSpeed(), server);
        rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(s.getSpawnRadius(), server);
        rules.getRule(GameRules.RULE_MAX_ENTITY_CRAMMING).set(s.getMaxEntityCramming(), server);
        rules.getRule(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH).set(s.getMaxCommandChainLength(), server);
        // New 1.21.1 integer rules
        rules.getRule(GameRules.RULE_MAX_COMMAND_FORK_COUNT).set(s.getMaxCommandForkCount(), server);
        rules.getRule(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT).set(s.getCommandModificationBlockLimit(), server);
        rules.getRule(GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY).set(s.getPlayersNetherPortalDefaultDelay(), server);
        rules.getRule(GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY).set(s.getPlayersNetherPortalCreativeDelay(), server);
        rules.getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(s.getPlayersSleepingPercentage(), server);
        rules.getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(s.getSnowAccumulationHeight(), server);
        rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(s.getSpawnChunkRadius(), server);
    }

    private static void applyFromDimensionConfig(GameRules rules, DimensionConfig.GameRules dc, MinecraftServer server) {
        rules.getRule(GameRules.RULE_DOFIRETICK).set(dc.DoFireTick, server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(dc.MobGriefing, server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(dc.KeepInventory, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(dc.DoMobSpawning, server);
        rules.getRule(GameRules.RULE_DOMOBLOOT).set(dc.DoMobLoot, server);
        rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(dc.DoTileDrops, server);
        rules.getRule(GameRules.RULE_DOENTITYDROPS).set(dc.DoEntityDrops, server);
        rules.getRule(GameRules.RULE_COMMANDBLOCKOUTPUT).set(dc.CommandBlockOutput, server);
        rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(dc.NaturalRegeneration, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(dc.DoDaylightCycle, server);
        rules.getRule(GameRules.RULE_LOGADMINCOMMANDS).set(dc.LogAdminCommands, server);
        rules.getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(dc.ShowDeathMessages, server);
        rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(dc.SendCommandFeedback, server);
        rules.getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(dc.SpectatorsGenerateChunks, server);
        rules.getRule(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK).set(dc.DisableElytraMovementCheck, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(dc.DoWeatherCycle, server);
        rules.getRule(GameRules.RULE_LIMITED_CRAFTING).set(dc.DoLimitedCrafting, server);
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(dc.AnnounceAdvancements, server);
        rules.getRule(GameRules.RULE_DISABLE_RAIDS).set(dc.DisableRaids, server);
        rules.getRule(GameRules.RULE_DOINSOMNIA).set(dc.DoInsomnia, server);
        rules.getRule(GameRules.RULE_DROWNING_DAMAGE).set(dc.DrowningDamage, server);
        rules.getRule(GameRules.RULE_FALL_DAMAGE).set(dc.FallDamage, server);
        rules.getRule(GameRules.RULE_FIRE_DAMAGE).set(dc.FireDamage, server);
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(dc.DoPatrolSpawning, server);
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(dc.DoTraderSpawning, server);
        rules.getRule(GameRules.RULE_FORGIVE_DEAD_PLAYERS).set(dc.ForgiveDeadPlayers, server);
        rules.getRule(GameRules.RULE_UNIVERSAL_ANGER).set(dc.UniversalAnger, server);
        // New 1.21.1 rules
        rules.getRule(GameRules.RULE_PROJECTILESCANBREAKBLOCKS).set(dc.ProjectilesCanBreakBlocks, server);
        rules.getRule(GameRules.RULE_REDUCEDDEBUGINFO).set(dc.ReducedDebugInfo, server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(dc.DoImmediateRespawn, server);
        rules.getRule(GameRules.RULE_FREEZE_DAMAGE).set(dc.FreezeDamage, server);
        rules.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(dc.DoWardenSpawning, server);
        rules.getRule(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY).set(dc.BlockExplosionDropDecay, server);
        rules.getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(dc.MobExplosionDropDecay, server);
        rules.getRule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY).set(dc.TntExplosionDropDecay, server);
        rules.getRule(GameRules.RULE_WATER_SOURCE_CONVERSION).set(dc.WaterSourceConversion, server);
        rules.getRule(GameRules.RULE_LAVA_SOURCE_CONVERSION).set(dc.LavaSourceConversion, server);
        rules.getRule(GameRules.RULE_GLOBAL_SOUND_EVENTS).set(dc.GlobalSoundEvents, server);
        rules.getRule(GameRules.RULE_DO_VINES_SPREAD).set(dc.DoVinesSpread, server);
        rules.getRule(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH).set(dc.EnderPearlsVanishOnDeath, server);
        // Integers
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(dc.RandomTickSpeed, server);
        rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(dc.SpawnRadius, server);
        rules.getRule(GameRules.RULE_MAX_ENTITY_CRAMMING).set(dc.MaxEntityCramming, server);
        rules.getRule(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH).set(dc.MaxCommandChainLength, server);
        rules.getRule(GameRules.RULE_MAX_COMMAND_FORK_COUNT).set(dc.MaxCommandForkCount, server);
        rules.getRule(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT).set(dc.CommandModificationBlockLimit, server);
        rules.getRule(GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY).set(dc.PlayersNetherPortalDefaultDelay, server);
        rules.getRule(GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY).set(dc.PlayersNetherPortalCreativeDelay, server);
        rules.getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(dc.PlayersSleepingPercentage, server);
        rules.getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(dc.SnowAccumulationHeight, server);
        rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(dc.SpawnChunkRadius, server);
    }

    /**
     * Serializes a GameRules instance to a map for persistence.
     */
    public static Map<String, Object> toMap(GameRules rules) {
        Map<String, Object> map = new LinkedHashMap<>();
        var tag = rules.createTag();
        for (String key : tag.getAllKeys()) {
            String value = tag.getString(key);
            try {
                map.put(key, Integer.parseInt(value));
            } catch (NumberFormatException e) {
                map.put(key, Boolean.parseBoolean(value));
            }
        }
        return map;
    }

    /**
     * Deserializes a GameRules instance from a persisted map.
     */
    public static GameRules fromMap(Map<String, Object> map) {
        var tag = new CompoundTag();
        for (var entry : map.entrySet()) {
            tag.putString(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new GameRules(new Dynamic<>(NbtOps.INSTANCE, tag));
    }
}
