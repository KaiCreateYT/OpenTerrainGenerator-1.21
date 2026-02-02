# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

OpenTerrainGenerator is a multi-platform Minecraft terrain generation mod using Architectury. Currently only Fabric is active (Forge disabled in `gradle.properties`).

### Module Structure

```
common/
├── common-annotation/    # Minimal annotation processing
├── common-util/          # Config, settings, serialization (depends on annotation)
├── common-customobject/  # BO2/BO3/BO4 objects, TreeObject (depends on util)
├── common-generator/     # Carvers: caves, ravines (depends on util)
└── common-core/          # Chunk generation, integrates all above

platforms/
├── fabric/               # Active - Fabric-specific code
├── shared/               # Common platform code
└── forge/                # Disabled
```

Dependency flow: `common-core` → `common-generator` + `common-customobject` + `common-util` → `common-annotation`

### Shadow JAR Relocation

Dependencies are relocated to avoid classpath conflicts:
- `com.fasterxml.jackson` → `com.pg85.otg.dependency.jackson`
- `org.yaml.snakeyaml` → `com.pg85.otg.dependency.snakeyaml`

### Key Files

| Purpose | Location |
|---------|----------|
| Chunk generation | `common/common-core/.../gen/OTGChunkGenerator.java` |
| Tree spawning | `common/common-customobject/.../customobject/TreeObject.java` |
| Cave/Ravine carvers | `common/common-generator/.../gen/carver/` |
| Fabric materials mapping | `platforms/fabric/.../fabric/materials/FabricMaterials.java` |
| World height constants | `common/common-util/.../constants/Constants.java` |
| Dimension settings | `common/common-util/.../config/settings/preset/DimensionSettings.java` |

### Configuration Files

- **PresetConfig.ini**: World generation settings (height, caves, biome distribution)
- **\*.bc files**: Biome configurations in `resources/Presets/DefaultPreset/Biomes/`
- **DimensionConfigs/\*.yaml**: Dimension setup examples

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

## Reference

See `OTG_1.20.1_UPGRADE_SUMMARY.md` for complete changelog of 1.20.1 migration fixes including tree validation, carver crashes, and noise generation updates.
