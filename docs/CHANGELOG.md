## Minecraft 1.21.1 — Fabric + NeoForge

### Release: 0.4.0-dev2

**2026-03-05 — In-game editor & preview system (WIP)**

- **Client source sets**: Added `splitEnvironmentSourceSets()` to shared and fabric modules; client-only code compiles separately from server code
- **OTG Editor button**: TitleScreenMixin injects "OTG Editor" button on the title screen, opens empty PreviewScreen
- **OrbitCamera**: Spherical coordinate camera with rotate/zoom/fitTo for 3D terrain preview
- **PreviewWorld**: `BlockAndTintGetter` implementation backed by `PreviewChunk` array — stores blocks + biomes copied from generated chunks, full brightness fake lighting, biome tint support
- **TempServerManager**: Uses MC's native `createFreshLevel` to spin up IntegratedServer with selected OTG preset; auto-cleans temp saves on stop
- **ChunkGenerationManager**: Spiral-order chunk generation from ServerLevel, feeds chunks into PreviewWorld with progress callbacks

**2026-03-03–04 — Portal overrides & GameRule fixes**

- **SharedMaterialData interface**: Now implements IBlockStateMaterial for cross-module material comparison
- **WorldPreset portal overrides**: Portal configuration (frame block, ignition item, color) can be overridden per-dimension in WorldPreset YAMLs
- **Portal gating**: Dimensions can disable portal creation/travel entirely via YAML config
- **Respawn-in-dimension mixin**: Players respawn in the OTG dimension they died in (if configured) instead of always respawning in the overworld
- **GameRules YAML override fix**: Fixed bug where world-level GameRule overrides in YAML weren't being applied
- **Default.yaml**: Added default WorldPreset YAML that ships with the mod

**2026-03-03 — Compatibility & display names**

- **Legacy custom objects**: UseWorld/UseBiome custom objects now handled gracefully instead of crashing
- **FeatureSorter cycle crash**: Deduplicated Registry() features to prevent cycle in MC's FeatureSorter (caused infinite loop during biome feature ordering)
- **Portal ClassCastException**: Fixed crash when portal frame block resolution encounters non-block materials
- **WorldPreset display names**: Dynamic display names via Language mixin — WorldPreset names show localized in the MC world creation GUI instead of raw YAML filenames

**2026-03-02 — Code review fixes**

- Nullable DimensionConfig overrides, error handling improvements
- Various correctness fixes from two rounds of code review

**2026-03-01 — WorldPreset system**

Renamed Preset → DimensionPreset, DimensionConfig → WorldPresetConfig across 188 files, 13 classes.

- **WorldPreset YAML**: New config format that composes a full world from multiple DimensionPresets. Defines which presets go in which dimensions, with per-dimension overrides.
- **WorldPresetRegistrar**: YAML configs registered as Minecraft WorldPresets — appear in the world creation GUI alongside vanilla presets.
- **WorldPresetConfigLoader**: Loads all YAMLs from `WorldPresets/` folder.
- **3-layer GameRules**: DimensionPresetConfig.ini → YAML world-level → YAML per-dimension. Each layer can override individual rules.
- **Folder renames**: `Presets/` → `DimensionPresets/`, `DimensionConfigs/` → `WorldPresets/`, `PresetConfig.ini` → `DimensionPresetConfig.ini`

---

### Release: 0.4.0-dev1

**2026-03-01 — GameRules per-dimension**

Full per-dimension GameRules system:

- **LevelGameRulesMixin**: Intercepts `Level.getGameRules()` to return dimension-specific rules. Works on both Fabric and NeoForge via shared mixin.
- **GameRuleManager**: Static map of dimension → GameRules, populated during dimension creation, cleared on server stop.
- **GameRuleApplier**: Merges GameRules from 3 layers — DimensionPresetConfig.ini (base) → WorldPreset YAML world-level → WorldPreset YAML per-dimension.
- **52 GameRule settings**: 32 original + 20 new 1.21.1 rules (ENDER_PEARLS_VANISH_ON_DEATH, DO_VINES_SPREAD, PLAYERS_SLEEPING_PERCENTAGE, etc.)
- **OTGWorldStorage**: Renamed from DimensionStorage, v2 format persists dimensions + GameRules to `otg_world_data.json`.
- Fixed copy-paste bug where DO_MOB_SPAWNING getter returned DO_MOB_LOOT value.

**2026-02-19 — BO4Config split**

Split monolithic BO4Config.java (1769 LOC) into focused components:

- **BO4BlockStorage** (326 LOC): Block data management and material resolution
- **BO4DataSerializer** (485 LOC): Binary serialization/deserialization
- **BO4ConfigWriter** (292 LOC): INI file writing
- BO4Config reduced to 733 LOC — just config loading and field access

**2026-02-18 — Logger refactor & BO4Config cleanup**

- **Unified logger**: Merged 3 separate logger implementations (Logger, OTGLogger, BasicLogger) into single OTGLogger. Added LogFormatter with `{}` placeholder support and lazy evaluation. ILogger.init() changed from 8-boolean to EnumSet<LogCategory>. ~416 call sites migrated.
- **BO4Config cleanup**: Extracted helpers, added try-with-resources, removed dead code. Preparation for BO4Config split.
- **BLANK material null-guard**: Fixed NPE when SharedWorldGenRegion.setBlock encounters BLANK material data.

**2026-02-17 — Mixin deduplication & more shared extraction**

- Moved 6 shared mixins to `platforms/shared/` (BiomeDataMixin, WorldPresetTagsMixin, LevelGameRulesMixin, ChunkAccessAccessor, MappedRegistryAccessor, MinecraftServerAccessor)
- Extracted SharedNBTHelper (~270 lines deduplicated), SharedOTGBiomeProvider (~160 lines)
- Collapsed BiomePlatformAdapter into shared — both platform implementations were identical
- Deleted dead code: OTGTemplateHandler, ShowWorldPresetsCommand, BiomeSyncWrapper
- Swamp biome flattened to 50/50 water/land ratio

**2026-02-16 — Biome loading redesign**

Decomposed the monolithic SharedLegacyBiomeLoader into clean, testable components:

- **BiomePlanResolver**: Pure logic for resolving biome assignments from preset config. 7 unit tests.
- **BiomePlan**: Immutable data class for biome resolution output.
- **BiomeFactory**: Creates MC Biome objects from OTG biome configs.
- **BiomeRegistrar**: Isolates MC registry mutation.
- **SharedDimensionPresetBiomeLoader**: Orchestrates the pipeline via composition.
- Deleted 430-line BiomeRegistryNames class frozen at 1.16.5 biome names.
- Fixed bit packing validation, ocean temperature index inversion, dynamic biome array sizing.

**2026-02-16 — Platform deduplication & cleanup**

Massive refactoring day — extracted most runtime logic from Fabric/NeoForge into `platforms/shared/`:

- SharedOTGChunkGenerator, SharedWorldGenRegion, SharedLegacyBiomeLoader
- Portal system, material classes (MaterialData, MaterialReader, Materials, MaterialTag, LegacyMaterials)
- ShadowChunkGenerator, ChunkBuffer, Biome, DimensionHelper
- **Deleted legacy Forge platform**: 74 files, ~17k lines of dead code targeting old Forge (not NeoForge). Fabric + NeoForge only going forward.
- Cleaned up dead interfaces, abandoned event hooks, dead EntityCategory enum, unused biomeColorMap, debug code

**Bug fixes**

- **Dynamic world bounds**: Replaced remaining deprecated WORLD_DEPTH/WORLD_HEIGHT with runtime world bounds
- **printStackTrace cleanup**: Replaced all bare `printStackTrace()` calls with structured OTGLog logging
- **NeoForge backports**: 3 bugfixes that were Fabric-only ported to NeoForge, removed duplicate NoiseParamRegistry
- **RuntimeException bombs**: Replaced 4 RuntimeException throws in BO4CustomStructure with OTGLog.error (mod no longer crashes server on recoverable BO4 errors)
- **River generation**: Fixed rivers not generating when RandomRivers=false
- **Structure tag injection disabled**: Temporarily disabled biome→structure tag injection due to bindTags() performance bug on NeoForge (~60s stall)

---

### Release: 0.2.0-dev5

**2026-02-15 (cont.) — FromImage fix, BO connection states**

- **FromImage OOM**: Fixed OutOfMemoryError when using FromImage biome mode — biome feature ordering cycle caused exponential memory growth.
- **BO connection states**: Updated block connection states for glass panes, iron bars, fences, and walls in BO objects. Objects placed in-world now correctly connect to adjacent blocks instead of floating as standalone pillars.

---

### Release: 0.2.0-dev4

**2026-02-15 — Commands, structures, terrain fixes**

- **UndergroundBiomeRarity**: New config setting to control underground biome spawn frequency.

**BO2/3/4 Commands**

- `/otg flushcache` — clear all cached custom objects
- `/otg spawn <object>` — spawn a BO2/BO3/BO4 at player position
- `/otg structure <object>` — start BO4 structure from branch
- `/otg export [template] [-e excludes] [-t tileentities]` — export selection to BO3 (WorldEdit integration)
- `/otg exportbo4data` — export BO4 data files for all custom objects
- Command infrastructure with CommandWorldAccessor for cross-platform world access

**Vanilla structures in OTG biomes**

- Inject OTG biomes into vanilla structure biome tags on both Fabric and NeoForge
- Villages, strongholds, witch huts, etc. now generate in OTG biomes that match the right temperature/category
- BiomeStructureTagConfig stored during biome registration, StructureTagMapper maps biomes to structure tags

**Bug fixes**

- **Village buildings missing**: JigsawStructureData delta parameter was using bounding box maxY instead of ground level delta, causing massively wrong terrain density around structures — villages only generated paths/farmland, no buildings
- **Steep biome borders**: Implemented vanilla weight halving for biome height blending. Neighbors with higher BiomeHeight get blending weight halved, creating softer transitions instead of cliffs
- **BO3 spawn cache**: spawnForced now properly registers objects in structure cache
- **BO4 structure plotting**: Fixed spiral chunk search and plotBo4Structure() cache integration
- **Beard fill (structure terrain)**: Fixed terrain adaptation around structures — ground under buildings was being carved instead of filled
- **Swamp flattening**: Swamp biome terrain now properly flattened

---

### Release: 0.2.0-dev3

**2026-02-13–14 — 3D underground biomes & cave improvements**

- **3D underground biomes**: Full system for assigning different biomes underground based on Y level and cheese cave noise. Biomes like Lush Caves, Dripstone Caves, Deep Dark appear only inside actual cave voids, not in solid rock.
  - UndergroundBiomeResolver with pre-computed lookup tables
  - Surface height estimation to determine underground threshold
  - Underground biomes excluded from 2D surface layer system
  - Example configs included (LushCaves, DripstoneCaves, DeepDark)
- **Surface-relative cave suppression**: Caves now thin out gradually near the surface using quadratic falloff instead of a hard cutoff. Prevents cheese holes in mountain tops while still allowing cave breakthroughs in valleys.
- **NeoForge carver unification**: Both platforms now use identical noise evaluation for cave carving.

**2026-02-12 — Noise cave carving**

- **1.18+ noise caves**: Added configurable noise cave density carving. Caves now use 3D noise sampling instead of just legacy carvers.
- **Thread-safe CustomObjectResource**: Fixed lazy init race condition that caused crashes with C2ME parallel chunk loading.

---

### Release: 0.2.0-dev2

**2026-02-08 — NeoForge platform & compatibility fixes**

- **NeoForge 1.21.1 support**: Multi-loader build from single codebase via Architectury. NeoForge platform with DeferredRegister, Data Attachments (replacing CCA), NeoForge event bus wiring.
- **C2ME compatibility**: Replaced FifoMap with ThreadSafeLRUCache to fix ConcurrentModificationException when C2ME workers access CustomStructureCache concurrently.
- **RegistryLoaderMixin fix**: Added @Local(ordinal=1) to disambiguate List type erasure, guard against client-side registry sync.

---

### Release: 0.2.0-dev1

**2026-02-07 — MC 1.21.1 port**

Full port from 1.20.1 to 1.21.1 (Java 21, Fabric API 0.116.7, Architectury 13.0.8):

- Migrated ResourceLocation constructors to static factory methods (MC 1.21 change)
- Migrated ChunkGenerator/BiomeSource codecs to MapCodec (MC 1.20.5+ change)
- Removed Executor parameter from fillFromNoise (MC 1.21 change)
- Rewrote RegistryLoaderMixin for 1.21.1 RegistryDataLoader changes — now uses @Local from MixinExtras instead of LocalCapture
- Updated WorldPresetTagsMixin for updateRegistryTags() signature change
- Fixed BootstapContext → BootstrapContext typo (MC fixed their own typo in 1.20.5)

---

## Minecraft 1.20.1

### Release: 0.2.0-dev10

**2026-02-06 — Thread-safe chunk generation**

- **Caffeine caching**: Replaced all FifoMap/LinkedHashMap caches with Caffeine-backed ThreadSafeLRUCache. Fixes ConcurrentModificationException with C2ME.
- **Synchronous fillFromNoise**: Removed ShadowChunkGenerator worker threads. Vanilla's ForkJoinPool already parallelizes chunk generation — same approach C2ME uses. Shadow gen kept only for BO4 objects.
- **C2ME compatibility**: OTG now works alongside C2ME for multiplayer chunk generation scaling.

**2026-02-04 — Performance optimization (benchmark-driven)**

Built a headless terrain snapshot testing system with benchmark command for measuring generation throughput without running Minecraft.

Optimizations applied (with before/after measurements):

- **Flatten GRAD array**: Converted 2D int[][] to flat int[] in SimplexNoiseSampler for better CPU cache locality.
- **BiomeSettings cache**: Cache per-chunk instead of per-block — reduces getBiomeSettings() calls from 98,304 to 256 per chunk (384x reduction).
- **Noise buffer reuse**: Pre-allocate double[][][] via ThreadLocal instead of 4KB allocation per chunk.
- **Bit shifts in ChunkCoordinate**: Replace `* 16` with `<< 4`, use Math.floorDiv for region coords. (Credit: Meldexun/OTG)
- **LRU cache**: Replaced FifoMap with LRUCache that evicts least-recently-used instead of oldest entries. Better hit rates for temporal locality patterns. (Credit: Meldexun/OTG)
- **O(log n) biome selection**: TreeMap.higherEntry() instead of linear iteration. (Credit: Meldexun/OTG)
- **SurfaceSettings cache**: Avoid 3 redundant method calls per block in populateNoise inner loop.
- **Hoist MutableBoolean**: Move allocation outside carver loops — eliminates 400-900 allocations per carve call.
- **Eliminate ThreadLocal boxing**: Replace ThreadLocal<Integer>/ThreadLocal<Double> with single ThreadLocal holding primitive fields.

**2026-02-03 — Portals, CI/CD, mob spawning**

- **Nether-style portals**: Implemented portal system for OTG dimensions — build a portal frame, light it, teleport between dimensions. Portal frame blocks and linking logic are configurable per preset.
- **Portal refactors**: Extracted PortalConfigResolver, PortalConfigLookup, DimensionKeys, DimensionNameUtils to shared modules for future NeoForge reuse.
- **CI/CD**: GitHub Actions workflow with auto-release on push. Builds both platforms, uploads artifacts.
- **Mob spawning**: Implemented mob spawning during chunk generation on Fabric (was previously missing).

**2026-02-02 — Dynamic dimensions system**

- **Runtime dimension creation**: Full system for creating OTG dimensions at runtime via datapack generation. Core types, platform interfaces, Fabric dimension manager, safe spawn logic, teleport command.
- **Dimension JSON handling**: Streamlined dimension type and noise settings JSON generation.

**2026-02-01 — Terrain tuning & deepslate**

- **Terrain constant tuning**: Adjusted BiomeHeight, BiomeVolatility, and noise parameters for 1.18+ terrain that no longer looks like a jagged mess.
- **VolatilityWeight fix**: Increased VolatilityWeight values from 0-1 range to ~48 range — the low values caused horrible jagged terrain.
- **DEEPSLATE material**: Added deepslate to the material registry for proper resource generation below Y=0.

**2026-01-24 — Terrain smoothing**

- **SmoothRadius increase**: Bumped smoothing radius for better terrain transitions between biomes.
- **Biome tag mapping**: Added FabricBiomeTagMapper to map OTG biomes to vanilla structure tags, enabling villages/strongholds/etc. in OTG biomes.

**2026-01-23 — World height & crash fixes**

- **Dynamic world height**: Replaced hardcoded WORLD_DEPTH/WORLD_HEIGHT constants with runtime MinY/MaxY from the world. Fixes terrain generation on custom-height worlds (1.18+ changed overworld to Y -64..319).
- **Crash safeguards**: Added null-checks and bounds validation throughout chunk generation to prevent empty chunks and NPEs during terrain population.
- **Tree spawning materials**: Added MaterialSet support so tree objects can validate block placement against a set of allowed materials.
- **OTG dimension type**: Expanded PresetSettings with dimension type fields (fixed time, respawn anchor, etc.) for custom OTG dimensions.
