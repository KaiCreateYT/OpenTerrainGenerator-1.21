# INFO.md

This file provides guidance when working with code in this repository.

## Build Commands

```bash
# Build all modules and create distribution JAR
./gradlew build

# Output: build/distributions/otg-fabric-0.2.0.jar

# Clean rebuild (use sparingly - gradle caching is enabled)
./gradlew clean build

# Refresh dependencies if repository issues
./gradlew --refresh-dependencies build

# Publish to local Maven
./gradlew publishToMavenLocal
```

No automated tests exist - testing is done manually in-game.

## Architecture

OpenTerrainGenerator is a multi-platform Minecraft terrain generation mod using Architectury. Both Fabric and NeoForge are active platforms.

### Module Structure

```
common/
├── common-annotation/    # Minimal annotation processing
├── common-util/          # Config, settings, serialization (depends on annotation)
├── common-customobject/  # BO2/BO3/BO4 objects, TreeObject (depends on util)
├── common-generator/     # Carvers: caves, ravines (depends on util)
└── common-core/          # Chunk generation, integrates all above

platforms/
├── fabric/               # Fabric-specific code
├── neoforge/             # NeoForge-specific code
└── shared/               # Common platform code (shared mixins, helpers)
```

Dependency flow: `common-core` → `common-generator` + `common-customobject` + `common-util` → `common-annotation`

### Platform Deduplication Pattern

Most runtime logic lives in `platforms/shared/`. Fabric (~820 LOC, 17 files) and NeoForge (~740 LOC, 16 files) are thin wrappers (median ~50 LOC/file, range 11-101). Two patterns:

1. **Abstract shared base** — `SharedOTGChunkGenerator`, `SharedOTGBiomeProvider`, `SharedDimensionHelper`, `SharedNBTHelper`, `SharedWorldGenRegion` contain all logic; platform subclasses add only CODEC definitions
2. **Composition** — `SharedDimensionPresetBiomeLoader` + `BiomePlatformAdapter` injected via constructor

**Only genuinely platform-specific:** CODEC definitions, mod loader API (`FabricLoader` vs `ModList`), data attachments (Cardinals vs NeoForge Attachments), event bus wiring.

#### Shared module key packages (`platforms/shared/`)

| Package | Contents |
|---------|----------|
| `shared.biome` | `SharedOTGBiomeProvider`, `SharedDimensionPresetBiomeLoader`, `BiomeRegistrar`, `BiomeFactory`, `BiomePlanResolver`, `BiomeTagMapper` |
| `shared.gen` | `SharedOTGChunkGenerator`, `SharedWorldGenRegion`, `SharedShadowChunkGenerator`, `SharedChunkBuffer` |
| `shared.materials` | `SharedMaterials`, `SharedMaterialData`, `SharedMaterialReader`, `SharedMaterialTag` |
| `shared.dimensions` | `SharedDimensionHelper`, `DimensionManager`, `DimensionKeys` |
| `shared.commands` | `OTGCommandRegistrar`, `DimensionCommands`, `ExportCommand`, `SpawnCommand`, `StructureCommand` |
| `shared.portals` | `SharedOTGPortalBlock`, `SharedOTGTeleporter`, portal config/ignition |
| `shared.mixin` | 7 shared mixins: `BiomeDataMixin`, `WorldPresetTagsMixin`, `LevelGameRulesMixin`, `ChunkAccessAccessor`, `MappedRegistryAccessor`, `MinecraftServerAccessor`, `CocoaDecoratorMixin` |
| `shared.gamerules` | `GameRuleManager`, `GameRuleApplier` — per-dimension GameRules via mixin |
| `platform.noise` | `OTGNoiseRouterData` — custom noise router with tunable cave scales |

#### Access wideners
- `otg-shared.accesswidener` — shared module
- `otg.accesswidener` — Fabric
- NeoForge uses ATs, not access wideners at runtime

### Shadow JAR Relocation

Dependencies are relocated to avoid classpath conflicts:
- `com.fasterxml.jackson` → `com.pg85.otg.dependency.jackson`
- `org.yaml.snakeyaml` → `com.pg85.otg.dependency.snakeyaml`

### Key Files

| Purpose | Location |
|---------|----------|
| Chunk generation (shared) | `platforms/shared/.../gen/SharedOTGChunkGenerator.java` |
| Chunk generation (common) | `common/common-core/.../gen/OTGChunkGenerator.java` |
| Biome provider | `platforms/shared/.../biome/SharedOTGBiomeProvider.java` |
| Biome loading | `platforms/shared/.../biome/SharedDimensionPresetBiomeLoader.java` |
| Tree spawning | `common/common-customobject/.../customobject/TreeObject.java` |
| Cave/Ravine carvers | `common/common-generator/.../gen/carver/` |
| Materials mapping | `platforms/shared/.../materials/SharedMaterials.java` |
| World height constants | `common/common-util/.../constants/Constants.java` |
| Dimension settings | `common/common-util/.../config/settings/preset/DimensionSettings.java` |
| GameRule manager | `platforms/shared/.../gamerules/GameRuleManager.java` |
| GameRule applier | `platforms/shared/.../gamerules/GameRuleApplier.java` |
| World storage | `common/common-core/.../dimensions/OTGWorldStorage.java` |
| Noise router (caves) | `platforms/shared/.../noise/OTGNoiseRouterData.java` |

### Configuration Files

- **DimensionPresetConfig.ini**: Per-dimension generation settings (height, caves, biome distribution, GameRules)
- **\*.bc files**: Biome configurations in `resources/DimensionPresets/DefaultPreset/Biomes/`
- **WorldPresets/\*.yaml**: World preset configs — compose full worlds from multiple DimensionPresets with per-dimension GameRules

### Naming Conventions

- **DimensionPreset** (formerly "Preset"): defines terrain generation for a single dimension. Class: `DimensionPreset`, config: `DimensionPresetConfig.ini`, folder: `DimensionPresets/`
- **WorldPreset**: YAML file composing a full world from multiple DimensionPresets. Class: `WorldPresetConfig`, folder: `WorldPresets/`
- **GameRules hierarchy**: `DimensionPresetConfig.ini` (base) → WorldPreset YAML world-level (override) → WorldPreset YAML per-dimension (override)

## MC 1.20.1 Specifics

### VolatilityWeight Values (IMPORTANT!)
**DO NOT "fix" VolatilityWeight1/VolatilityWeight2 values to 0-1 range!**

- Biome Bundle uses values ~48 (e.g. 48.0, 46.8) = **CORRECT**, smooth terrain
- DefaultPreset uses values 0-1 (e.g. 0.5, 0.45) = **WRONG**, jagged ugly terrain

Although the code in `sampleNoise()` compares `delta` (0-1) with `volatilityWeight`, higher values produce better visual results. DefaultPreset is an example of HOW NOT TO DO IT - its terrain is terribly jagged precisely because of low volatilityWeight values.

### World Height (1.18+)
- Overworld: MinY=-64, MaxY=319, Height=384
- Default constants in `Constants.DEFAULT_WORLD_INFO = new OTGWorldInfo(-64, 319)`

### Block Name Changes
- `Blocks.GRASS` = short grass plant (NOT soil)
- `Blocks.GRASS_BLOCK` = grass soil block (use this for terrain)
- Biome "Extreme Hills" renamed to "Mountains"

### Carver Y-Coordinates
Carvers handle negative Y by using `y - otgWorldInfo.minY()` as BitSet index.

### Anti-Floating Terrain
`OTGChunkGenerator` applies quadratic penalty for terrain above expected surface height to prevent floating islands.

## Changelog

**After every feature, bug fix, or version bump, update `docs/CHANGELOG.md`.**

- Add entry under the current version/date section
- For version bumps, add a new `── Release: x.y.z ──` separator
- Keep entries concise but technical — what changed and why
- Group related changes under a single date heading

## Reference

See `OTG_1.20.1_UPGRADE_SUMMARY.md` for complete changelog of 1.20.1 migration fixes including tree validation, carver crashes, and noise generation updates.
