# Resource Queue

The resource queue is the list of resource entries at the end of a biome `.bc` file. Each entry spawns something during chunk decoration — ores, plants, trees, lakes, custom objects, dungeons — and `Registry(...)` entries run vanilla (or datapack) placed features. OTG processes its own entries in the order they appear; `Registry(...)` features run at the vanilla decoration step you declare.

Resource keywords are **case-insensitive** (`Ore`, `ore`, and `ORE` all work). For the rest of the biome configuration, see [Biome Config](biome-config.md).

## Common parameters

Many resources share the same leading/trailing parameters:

- **Frequency** — number of placement attempts made per chunk.
- **Rarity** — percent chance (0–100, decimals allowed) that each attempt succeeds.
- **MinAltitude / MaxAltitude** — absolute world Y bounds (negative values are valid in 1.18+).
- **Source blocks** — the trailing, variadic block list. Its meaning is resource-specific: the blocks an ore may *replace*, the blocks a plant may *grow on*, or the blocks a spring must be *surrounded by*. At least one is required wherever the list is shown.

Blocks accept modern IDs (`minecraft:coal_ore`) or legacy OTG names (`COAL_ORE`). Some resources accept a `PlantType` or `TreeType` enum name instead of a raw block. A few resources (`Ore`, `Tree`) support an optional trailing `,true,MaxSpawn` pair that caps how many placements actually occur per chunk (`0` = no cap).

---

## Ores & Underground Features

Resource queue entries for ore veins, disks, underground structures, and subsurface water features. All altitude values are absolute world Y coordinates (negative values are valid in 1.18+ worlds).

### Ore

Places one or more ellipsoidal blob(s) of a block along a short random line segment, replacing matching source blocks. This is the standard ore vein generator used for coal, iron, diamond, etc.

**Syntax:** `Ore(Material,Size,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,...][,true,MaxSpawn])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block to place (e.g. `minecraft:coal_ore`). |
| `Size` | Number of blob iterations along the segment; controls both length and maximum blob diameter. Accepted range: 1–128. |
| `Frequency` | Spawn attempts per chunk. Accepted range: 1–100. |
| `Rarity` | Percent probability per attempt that a blob is placed (0–100, decimal accepted). |
| `MinAltitude` | Lowest Y level at which the blob centre may appear. |
| `MaxAltitude` | Highest Y level at which the blob centre may appear. |
| `BlockSource` | One or more block(s) the ore may replace. At least one is required; repeat the argument to add more. |
| `true` | *(Optional)* Literal `true` enables the extended parameter below. Must appear as the second-to-last argument. |
| `MaxSpawn` | *(Optional, requires `true` above)* Hard cap on the number of blobs that actually place blocks per chunk. `0` means no cap. |

```properties
Ore(minecraft:coal_ore,17,20,100.0,0,127,minecraft:stone)
Ore(minecraft:diamond_ore,8,1,100.0,-64,16,minecraft:stone,minecraft:deepslate,true,1)
```

### UnderWaterOre

Places a flat disk of a block around the first solid block beneath standing water, replacing matching source blocks. Designed for sand, gravel, and clay patches on lake and ocean floors.

**Syntax:** `UnderWaterOre(Material,Size,Frequency,Rarity,BlockSource[,BlockSource2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block to place (e.g. `minecraft:clay`). |
| `Size` | Maximum disk radius in blocks. The actual radius per attempt is `rand(Size) + 2`. Accepted range: 1–8. |
| `Frequency` | Spawn attempts per chunk. Accepted range: 1–100. |
| `Rarity` | Percent probability per attempt (0–100, decimal accepted). |
| `BlockSource` | One or more block(s) the disk may replace. At least one is required. |

```properties
UnderWaterOre(minecraft:clay,4,1,100.0,minecraft:dirt,minecraft:clay)
UnderWaterOre(minecraft:gravel,6,1,100.0,minecraft:grass_block,minecraft:dirt,minecraft:sand)
```

### Vein

Generates large-scale ore veins that span multiple chunks. A vein is anchored to a single chunk (via seeded RNG so it is deterministic) and then individual ore clusters are scattered within a spherical volume around that anchor. Distinct from `Ore` in that a single vein can extend across many chunks and is much larger overall.

**Syntax:** `Vein(Material,MinSize,MaxSize,VeinRarity,OreAvgSize,OreFrequency,OreRarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block to place for individual ore clusters within the vein. |
| `MinSize` | Minimum vein radius in blocks. Accepted range: 10–200. |
| `MaxSize` | Maximum vein radius in blocks. Must be ≥ `MinSize`. |
| `VeinRarity` | Percent probability per chunk that a vein is anchored there (decimal). |
| `OreAvgSize` | Average size (blob iterations) of each individual ore cluster placed inside the vein. Accepted range: 1–64. |
| `OreFrequency` | Number of ore cluster attempts per chunk that falls within the vein radius. Accepted range: 1–100. |
| `OreRarity` | Integer percent probability (0–100) per cluster attempt. |
| `MinAltitude` | Lowest Y level at which an ore cluster may appear. |
| `MaxAltitude` | Highest Y level at which an ore cluster may appear. |
| `BlockSource` | One or more block(s) the clusters may replace. At least one is required. |

```properties
Vein(minecraft:ancient_debris,30,60,0.5,5,15,75,-64,16,minecraft:netherrack,minecraft:basalt)
```

### Dungeon

Places a vanilla Minecraft dungeon (a mossy/cobblestone room with a monster spawner and loot chests) at a random Y within the given altitude range. Frequency is always 1 attempt per chunk; control spawn density via `Rarity`.

**Syntax:** `Dungeon(Rarity,MinAltitude,MaxAltitude)`

| Parameter | Meaning |
|-----------|---------|
| `Rarity` | Percent probability per chunk that a dungeon is attempted (0–100, decimal accepted). |
| `MinAltitude` | Lowest Y level the dungeon may be placed at. |
| `MaxAltitude` | Highest Y level the dungeon may be placed at. |

> **Legacy format** (4 arguments): `Dungeon(Frequency,Rarity,MinAltitude,MaxAltitude)` is accepted for backwards compatibility. When 4 arguments are present the first (`Frequency`) is read but ignored; frequency is always 1.

```properties
Dungeon(8.0,0,64)
```

### Fossil

Places a vanilla Minecraft fossil structure (bone block arrangement) at a random Y within the given altitude range. Exactly one attempt is made per chunk.

**Syntax:** `Fossil(Rarity[,MinAltitude,MaxAltitude])`

| Parameter | Meaning |
|-----------|---------|
| `Rarity` | Integer inverse-rarity value for the internal fossil spawn check (minimum 1). |
| `MinAltitude` | *(Optional)* Lowest Y the fossil may be placed at. Defaults to `30`. |
| `MaxAltitude` | *(Optional)* Highest Y the fossil may be placed at. Defaults to `60`. |

```properties
Fossil(64,0,60)
```

### Well

Places a desert-well-style structure built from three configurable blocks: a base/wall material, a slab material for the overhang, and a liquid material for the interior pool. Scans downward from a random Y to find the first matching source block, then builds the structure there.

**Syntax:** `Well(Material,Slab,Water,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block used for the well walls, floor, and roof centre (e.g. `minecraft:sandstone`). |
| `Slab` | Block used for the overhanging slab positions (e.g. `minecraft:sandstone_slab`). |
| `Water` | Block placed as the liquid in the well basin (e.g. `minecraft:water`). |
| `Frequency` | Spawn attempts per chunk. Accepted range: 1–100. |
| `Rarity` | Percent probability per attempt (0–100, decimal accepted). |
| `MinAltitude` | Lowest Y the downward scan may start from. |
| `MaxAltitude` | Highest Y the downward scan may start from. |
| `BlockSource` | One or more block(s) that must be present at the candidate surface for the well to build. At least one is required. |

```properties
Well(minecraft:sandstone,minecraft:sandstone_slab,minecraft:water,1,0.1,60,100,minecraft:sand)
```

### UnderGroundLake

Carves out an ellipsoidal cavity underground and fills it partially with water, producing small underground lakes or air pockets. The cavity size is chosen randomly between `MinSize` and `MaxSize` per attempt.

**Syntax:** `UnderGroundLake(MinSize,MaxSize,Frequency,Rarity,MinAltitude,MaxAltitude)`

| Parameter | Meaning |
|-----------|---------|
| `MinSize` | Minimum lake size (horizontal and vertical radius of the ellipsoid). Accepted range: 1–25. |
| `MaxSize` | Maximum lake size. Must be ≥ `MinSize`. |
| `Frequency` | Spawn attempts per chunk. Accepted range: 1–100. |
| `Rarity` | Percent probability per attempt (0–100, decimal accepted). |
| `MinAltitude` | Lowest Y the lake centre may appear at. Skipped if the chosen Y is at or above the surface. |
| `MaxAltitude` | Highest Y the lake centre may appear at. |

```properties
UnderGroundLake(5,20,1,60.0,0,50)
```

---

## Plants & Ground Cover

Ground-cover resources that place plants, tall grass, reeds, cactus, vines, bamboo, and noise-based surface patches. All require at least one source block at the end of the argument list (except `Vines`, which attaches to any solid block and takes none).

### Plant

Places a single-block or two-block plant (flower, mushroom, fern, etc.) at a random Y within the altitude range. Makes 64 scatter attempts per invocation, each displaced ±8 blocks in XZ. Requires the block directly below the target to be a source block and the target itself to be air.

**Syntax:** `Plant(Material,Frequency,Rarity,MinAltitude,MaxAltitude,Block[,Block2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Named `PlantType` (e.g. `Dandelion`, `Tallgrass`, `Sunflower`) or any block ID. Two-block plants are handled automatically. |
| `Frequency` | Invocations per chunk (1–100). Each invocation picks a random Y, then scatters up to 64 attempts. |
| `Rarity` | Percent chance (0.000001–100.0) that any given invocation proceeds. |
| `MinAltitude` | Minimum Y the random starting height can be chosen from. |
| `MaxAltitude` | Maximum Y the random starting height can be chosen from. |
| `Block` | One or more block IDs the plant may grow on (checked at Y−1). At least one required. |

```properties
Plant(Dandelion,10,100.0,60,120,minecraft:grass_block)
Plant(Sunflower,1,25.0,60,100,minecraft:grass_block,minecraft:dirt)
```

### UnderWaterPlant

Like `Plant`, but the target position must contain water rather than air. Intended for seagrass-type vegetation below the water surface.

**Syntax:** `UnderWaterPlant(Material,Frequency,Rarity,MinAltitude,MaxAltitude,Block[,Block2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | `PlantType` name or block ID to place. |
| `Frequency` | Invocations per chunk (1–100). |
| `Rarity` | Percent chance (0.000001–100.0) per invocation. |
| `MinAltitude` | Minimum Y for the random starting height. |
| `MaxAltitude` | Maximum Y for the random starting height. |
| `Block` | Block(s) the plant grows on (checked at Y−1). At least one required. |

```properties
UnderWaterPlant(SeaGrass,10,100.0,40,62,minecraft:sand,minecraft:gravel)
```

### Grass

Places tall grass or other surface vegetation at the highest solid surface. Supports two placement modes: `Grouped` (one rarity check per chunk, then a cluster) or `NotGrouped` (independent attempts). No altitude clamping — always resolves to the surface Y.

**Syntax:** `Grass(Material,GroupOption,Frequency,Rarity,Block[,Block2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | `PlantType` name or block ID to place. |
| `GroupOption` | `Grouped` — one patch per chunk; or `NotGrouped` — N independent surface attempts. |
| `Frequency` | Placements per chunk (1–500). Patch density in `Grouped`, attempt count in `NotGrouped`. |
| `Rarity` | Percent chance (0.000001–100.0). Gates the whole chunk in `Grouped`, each attempt in `NotGrouped`. |
| `Block` | Block(s) at the surface the grass can grow on. At least one required. |

```properties
Grass(Tallgrass,NotGrouped,30,100.0,minecraft:grass_block)
Grass(Fern,Grouped,4,80.0,minecraft:podzol,minecraft:grass_block)
```

### Reed

Places a 1–2 block tall column (sugar cane style) at the surface, but only if at least one horizontally adjacent block at Y−1 is liquid.

**Syntax:** `Reed(Material,Frequency,Rarity,MinAltitude,MaxAltitude,Block[,Block2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block ID to stack (e.g. `minecraft:sugar_cane`). |
| `Frequency` | Invocations per chunk (1–100). |
| `Rarity` | Percent chance (0.000001–100.0) per invocation. |
| `MinAltitude` | Minimum surface Y to allow placement. |
| `MaxAltitude` | Maximum surface Y to allow placement. |
| `Block` | Block(s) the reed may grow on. At least one required. |

```properties
Reed(minecraft:sugar_cane,10,100.0,60,70,minecraft:grass_block,minecraft:sand,minecraft:dirt)
```

### Cactus

Places a 1–3 block tall cactus column. Makes 10 placement attempts per invocation; all four horizontal neighbours at the base Y must be air (vanilla cactus rule).

**Syntax:** `Cactus(Material,Frequency,Rarity,MinAltitude,MaxAltitude,Block[,Block2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block ID to stack (e.g. `minecraft:cactus`). |
| `Frequency` | Invocations per chunk (1–100). Each invocation makes up to 10 attempts. |
| `Rarity` | Percent chance (0.000001–100.0) per invocation. |
| `MinAltitude` | Minimum Y for the random starting height. |
| `MaxAltitude` | Maximum Y for the random starting height. |
| `Block` | Block(s) the cactus may grow on. At least one required. |

```properties
Cactus(minecraft:cactus,10,25.0,60,80,minecraft:sand,minecraft:red_sand)
```

### Vines

Fills a vertical band with vanilla vine blocks, scanning every Y from `MinAltitude` to `MaxAltitude`. Attaches vines to adjacent solid blocks. Takes no source blocks and no material — hardcoded to `minecraft:vine`.

**Syntax:** `Vines(Frequency,Rarity,MinAltitude,MaxAltitude)`

| Parameter | Meaning |
|-----------|---------|
| `Frequency` | Invocations per chunk (1–100). |
| `Rarity` | Percent chance (0.000001–100.0) per invocation. |
| `MinAltitude` | Lowest Y where vines may attach. |
| `MaxAltitude` | Highest Y where vines may attach. |

```properties
Vines(5,50.0,50,100)
```

### Bamboo

Places a bamboo stalk (5–16 blocks tall) at the highest surface position, optionally with a podzol patch around the base. No altitude clamping — always resolves to surface Y.

**Syntax:** `Bamboo(Frequency,Rarity,PodzolChance,Block[,Block2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Frequency` | Invocations per chunk (1–500). |
| `Rarity` | Percent chance (0.000001–100.0) per invocation. |
| `PodzolChance` | Probability (`0.0`–`1.0`, **not** percent) that a podzol patch is placed around the base. |
| `Block` | Block(s) the bamboo may grow on. At least one required. |

```properties
Bamboo(25,100.0,0.01,minecraft:grass_block,minecraft:dirt,minecraft:podzol)
```

### SurfacePatch

Replaces the top surface block across the chunk with `Material` wherever a two-octave simplex noise value exceeds 0.0, and places `DecorationAbove` on top at patch edges. No frequency/rarity — coverage is controlled by noise (roughly 50% of eligible surface).

**Syntax:** `SurfacePatch(Material,DecorationAbove,MinAltitude,MaxAltitude,Block[,Block2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block to replace the surface with (e.g. `minecraft:coarse_dirt`). |
| `DecorationAbove` | `PlantType` name or block ID placed one block above the surface at patch edges. Use `minecraft:air` to skip. |
| `MinAltitude` | Minimum surface Y for the patch to apply. |
| `MaxAltitude` | Maximum surface Y for the patch to apply. |
| `Block` | Block(s) that can be replaced (the original surface block must be in this set). At least one required. |

```properties
SurfacePatch(minecraft:coarse_dirt,minecraft:dead_bush,60,100,minecraft:grass_block,minecraft:dirt)
SurfacePatch(minecraft:podzol,Fern,58,90,minecraft:grass_block)
```

---

## Trees & Custom Objects

Resource queue entries for placing trees, custom objects, and BO4 structures into a biome.

### Tree

Places vanilla or custom-object trees during chunk decoration. Tries `Frequency` times per chunk; for each attempt it walks the `Object,Chance` pairs left-to-right and picks the first whose roll succeeds.

**Syntax:** `Tree(Frequency,Object,Chance[,Object2,Chance2,...][,SourceBlock,...][,true,MaxSpawn])`

| Parameter | Meaning |
|-----------|---------|
| `Frequency` | Attempts per chunk (1–100). |
| `Object` | Tree type (`TreeType` enum value) or the name of a BO3/BO4 object. For BO objects with height constraints use `ObjectName(MinHeight=Y;MaxHeight=Y)`. |
| `Chance` | Percent chance (1–100) that this object is attempted on a given iteration. |
| `SourceBlock` | *(optional, after all Object/Chance pairs)* One or more block IDs the tree must be placed on. Defaults to the BO's internal list when absent. |
| `true` / `MaxSpawn` | *(optional)* Literal `true` followed by a per-chunk spawn cap (`0` = no cap). |

Valid `TreeType` values: `Tree`, `BigTree`, `Birch`, `TallBirch`, `Forest` *(deprecated alias for Birch)*, `Taiga1`, `Taiga2`, `HugeTaiga1`, `HugeTaiga2`, `Acacia`, `DarkOak`, `JungleTree`, `SwampTree`, `CocoaTree`, `HugeMushroom`, `HugeRedMushroom`, `HugeBrownMushroom`, `GroundBush`, `CrimsonFungi`, `WarpedFungi`, `ChorusPlant`, `Mangrove`, `TallMangrove`, `Cherry`.

```properties
# 10 attempts/chunk: try Taiga2 first (35% chance), fall back to Taiga1 (guaranteed)
Tree(10,Taiga2,35,Taiga1,100)

# Cherry trees on grass_block only, capped at 4 per chunk
Tree(6,Cherry,80,minecraft:grass_block,true,4)

# Custom BO3 with height range
Tree(5,MyCustomTree(MinHeight=60;MaxHeight=120),60)
```

### Sapling

Defines what grows when a player (or bonemeal) triggers a sapling placed in this biome. The `SaplingType` determines which vanilla sapling block triggers this entry.

**Syntax (standard):** `Sapling(SaplingType,Object,Chance[,Object2,Chance2,...])`
**Syntax (custom block):** `Sapling(Custom,BlockID,WideTrunk,Object,Chance[,Object2,Chance2,...])`

| Parameter | Meaning |
|-----------|---------|
| `SaplingType` | Which vanilla sapling triggers growth (see values below). |
| `BlockID` | *(Custom only)* Full block ID of the custom sapling block. |
| `WideTrunk` | *(Custom only)* `true` if the tree requires a 2×2 sapling arrangement. |
| `Object` | BO3/BO4 object name that can grow as a tree. |
| `Chance` | Percent chance (1.0–100.0) this object is attempted. |

Valid `SaplingType` values: `All` *(wildcard)*, `Oak`, `Redwood`, `Birch`, `SmallJungle`, `BigJungle`, `RedMushroom`, `BrownMushroom`, `Acacia`, `DarkOak`, `HugeRedwood`, `Bamboo`, `Custom`.

```properties
Sapling(Oak,MyOakTree,80,MyOakAlt,100)
Sapling(Custom,minecraft:dark_oak_sapling,true,MyDarkOakBO3,100)
```

### CustomObject

Spawns one or more global custom objects (BO2/BO3/BO4) during chunk decoration. Each named object runs its own internal placement logic (frequency, conditions, rotation) defined inside the object file. A blank name falls back to `UseWorld`.

**Syntax:** `CustomObject(ObjectName[,ObjectName2,...])`

| Parameter | Meaning |
|-----------|---------|
| `ObjectName` | Name of a BO2/BO3/BO4 file (without extension) in the preset's `CustomObjects/` folder. Repeat to spawn multiple. |

```properties
CustomObject(UseWorld)
CustomObject(MyRuins,MyTower)
```

### CustomStructure

Marks this biome as a source of BO4 structures. Each named BO4 is the root of a structure with an independent rarity roll. Unlike `CustomObject`, this drives the BO4 branching/expansion system.

**Syntax:** `CustomStructure(ObjectName,Rarity[,ObjectName2,Rarity2,...])`

| Parameter | Meaning |
|-----------|---------|
| `ObjectName` | Name of the root BO4 file (without extension). |
| `Rarity` | Chance per chunk that this structure attempts to start (0.000001–100.0). |

```properties
CustomStructure(VillageStart,5.0,RuinStart,12.5)
```

### Boulder

Places a spherical clump of a single block on the terrain surface. Scans downward to find a valid source block before placing.

**Syntax:** `Boulder(Material,Frequency,Rarity,MinAltitude,MaxAltitude,SourceBlock[,SourceBlock2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block to fill the boulder with (e.g. `minecraft:mossy_cobblestone`). |
| `Frequency` | Placement attempts per chunk (1–5000). |
| `Rarity` | Percent probability each attempt succeeds (0.000001–100.0). |
| `MinAltitude` | Minimum surface Y for placement. |
| `MaxAltitude` | Maximum surface Y for placement. |
| `SourceBlock` | One or more block IDs the block below the surface must match. At least one required. |

```properties
Boulder(minecraft:mossy_cobblestone,1,25.0,40,120,minecraft:grass_block,minecraft:dirt)
```

### AboveWaterRes

Places a block floating just above a liquid surface (e.g. lily pads). Makes 10 scatter attempts per frequency success; only places in air directly above a liquid block.

**Syntax:** `AboveWaterRes(Material,Frequency,Rarity)`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block to place (e.g. `minecraft:lily_pad`). |
| `Frequency` | Placement attempts per chunk (1–100). |
| `Rarity` | Percent probability each attempt proceeds (0.000001–100.0). |

```properties
AboveWaterRes(minecraft:lily_pad,4,100.0)
```

---

## Aquatic & Liquids

Resource-queue entries for liquid springs, small lakes, and underwater vegetation.

### Liquid

Places a liquid spring block. Requires the target cell to have exactly 3 solid source-blocks and 1 air block among its 4 horizontal neighbours — mimics vanilla spring generation.

**Syntax:** `Liquid(Material,Frequency,Rarity,MinAltitude,MaxAltitude,Block[,Block2,...])`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Liquid block to place (e.g. `minecraft:water`, `minecraft:lava`). |
| `Frequency` | Attempts per chunk (1–5000). |
| `Rarity` | Percent chance per attempt (0.0–100.0). |
| `MinAltitude` | Lowest Y the spring may appear at. |
| `MaxAltitude` | Highest Y the spring may appear at. |
| `Block` | One or more blocks the spring must be surrounded by. At least one required. |

```properties
Liquid(minecraft:water,20,100.0,8,128,minecraft:stone)
```

### SmallLake

Carves a small irregular lake bowl and fills it with a liquid. Skips generation if the chunk contains a vanilla structure start (e.g. a village). For polished lakes, custom BO objects are recommended.

**Syntax:** `SmallLake(Material,Frequency,Rarity,MinAltitude,MaxAltitude)`

| Parameter | Meaning |
|-----------|---------|
| `Material` | Block to fill the lake with. |
| `Frequency` | Attempts per chunk (1–100). |
| `Rarity` | Percent chance per attempt (0.0–100.0). |
| `MinAltitude` | Lowest Y the lake centre may be placed at. |
| `MaxAltitude` | Highest Y the lake centre may be placed at. |

```properties
SmallLake(minecraft:water,4,7.0,8,119)
```

### SeaGrass

Places seagrass on the first solid block beneath an open-water column, with a configurable chance for the tall (two-block) variant.

**Syntax:** `SeaGrass(Frequency,Rarity,TallChance)`

| Parameter | Meaning |
|-----------|---------|
| `Frequency` | Attempts per chunk (1–500). |
| `Rarity` | Percent chance per attempt (0.0–100.0). |
| `TallChance` | Probability (`0.0`–`1.0`) that a placement produces tall seagrass instead of short. |

```properties
SeaGrass(80,100.0,0.1)
```

### Kelp

Grows a kelp column upward from the first solid block under water, up to the water surface. Height is sampled in `[MinHeight, MaxHeight)`.

**Syntax:** `Kelp(Frequency,Rarity[,MinHeight,MaxHeight[,Block,...]])`

| Parameter | Meaning |
|-----------|---------|
| `Frequency` | Attempts per chunk (1–500). |
| `Rarity` | Percent chance per attempt (0.0–100.0). |
| `MinHeight` | *(Optional)* Minimum column height (1–100). Default `1`. |
| `MaxHeight` | *(Optional)* Maximum column height (1–200). Default `11`. |
| `Block` | *(Optional)* Floor blocks kelp may root on. Default: dirt, gravel, sand. |

```properties
Kelp(20,100.0,1,11,minecraft:gravel,minecraft:sand)
```

### SeaPickle

Scatters sea pickle clusters on solid blocks beneath water. Each top-level attempt makes `Attempts` additional random offsets within the chunk.

**Syntax:** `SeaPickle(Frequency,Rarity,Attempts)`

| Parameter | Meaning |
|-----------|---------|
| `Frequency` | Attempts per chunk (1–500). |
| `Rarity` | Percent chance per attempt (0.0–100.0). |
| `Attempts` | Secondary scatter attempts per successful top-level attempt (1–256). |

```properties
SeaPickle(1,100.0,20)
```

### CoralMushroom · CoralTree · CoralClaw

Three procedural coral structures, each spawning at the first solid block beneath an open-water column. The coral species is chosen randomly from the available coral block types. They share an identical 2-parameter signature but generate completely different shapes — `CoralMushroom` is a hollow ellipsoid shell, `CoralTree` a branching upward tree, and `CoralClaw` an outward-curving claw.

**Syntax:** `CoralMushroom(Frequency,Rarity)` · `CoralTree(Frequency,Rarity)` · `CoralClaw(Frequency,Rarity)`

| Parameter | Meaning |
|-----------|---------|
| `Frequency` | Attempts per chunk (1–500). |
| `Rarity` | Percent chance per attempt (0.0–100.0). |

```properties
CoralMushroom(2,100.0)
CoralTree(2,100.0)
CoralClaw(2,100.0)
```

---

## Special Terrain, Grouping & Vanilla Features

Resources for terrain sculpting (ice spikes, icebergs, basalt columns), vegetation grouping and scattering, and delegating to vanilla placed features via the registry.

### IceSpike

Places packed-ice spike formations: a basement disc, small spike, or huge spike.

**Syntax:** `IceSpike(BlockName,IceSpikeType,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,...])`

| Parameter | Meaning |
|-----------|---------|
| `BlockName` | The material placed (e.g. `minecraft:packed_ice`). |
| `IceSpikeType` | Shape variant: `Basement`, `HugeSpike`, or `SmallSpike`. |
| `Frequency` | Placement attempts per chunk (1–30). |
| `Rarity` | Percent chance each attempt succeeds (0.000001–100). |
| `MinAltitude` | Lowest Y the feature can start from. |
| `MaxAltitude` | Highest Y the feature can start from. |
| `BlockSource` | Blocks the spike can replace / grow on. At least one required. |

```properties
IceSpike(minecraft:packed_ice,HugeSpike,3,1.66,60,128,minecraft:ice,minecraft:dirt,minecraft:snow_block)
```

### Iceberg

Places an iceberg structure at water level. Arguments form one or more `(BlockName1,BlockName2,Chance)` triplets followed by a final `TotalChance`. The engine rolls `[0, TotalChance)` and selects the variant whose cumulative weight covers the result.

**Syntax:** `Iceberg(BlockName1,BlockName2,Chance[,BlockName1,BlockName2,Chance,...],TotalChance)`

| Parameter | Meaning |
|-----------|---------|
| `BlockName1` | Primary material (bulk of the iceberg). |
| `BlockName2` | Secondary material (inner/top layer). |
| `Chance` | Weight of this variant in the roll (0.000001–100). |
| `TotalChance` | Upper bound of the random roll; controls overall spawn frequency (higher = rarer). |

```properties
Iceberg(minecraft:ice,minecraft:snow_block,70,minecraft:packed_ice,minecraft:blue_ice,30,100)
```

### BasaltColumn

Places clusters of basalt columns, mimicking vanilla basalt delta formations.

**Syntax:** `BasaltColumn(BlockName,Frequency,Rarity,BaseSize,SizeVariance,BaseHeight,HeightVariance,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,...])`

| Parameter | Meaning |
|-----------|---------|
| `BlockName` | Block placed in every column (e.g. `minecraft:basalt`). |
| `Frequency` | Placement attempts per chunk (1–100). |
| `Rarity` | Percent chance each attempt succeeds (0.000001–100). |
| `BaseSize` | Minimum horizontal radius of each cluster (1–5). |
| `SizeVariance` | Random addition to `BaseSize` (0–5). |
| `BaseHeight` | Minimum column height (1–5). |
| `HeightVariance` | Random addition to `BaseHeight` (0–5). |
| `MinAltitude` | Lowest Y the feature can start from. |
| `MaxAltitude` | Highest Y the feature can start from. |
| `BlockSource` | Blocks the column can overwrite (e.g. `minecraft:lava`, `minecraft:netherrack`). |

```properties
BasaltColumn(minecraft:basalt,8,50,2,1,3,2,32,96,minecraft:lava,minecraft:netherrack)
```

### Group · Scatter

Two vegetation placers that share a `PlantType`, a `VerticalMode`, and an `Environment` (material condition). `Group` spawns a spatial cluster of plants around each origin; `Scatter` places single plants one at a time at random positions.

!!! warning "Experimental"
    `Group` and `Scatter` are not emitted by the default config writer and their serialization round-trip is broken: the parser skips argument index 1 (an unused legacy slot) but `toString()` omits it. If you write these by hand, supply a placeholder (e.g. `0`) at position 1 so the remaining parameters land on the correct indices.

**Syntax:** `Group(PlantType,0,VerticalMode,Environment,Frequency,Rarity,MinAltitude,MaxAltitude,HorizontalSpread,VerticalSpread,GroupSize,Variance,BlockSource[,...])`
**Syntax:** `Scatter(PlantType,0,VerticalMode,Environment,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource,BlockSource2,BlockSource3[,...])`

| Parameter | Meaning |
|-----------|---------|
| `PlantType` | The plant to place (`PlantType` name or block ID). |
| `0` | Unused placeholder slot — must be present. |
| `VerticalMode` | `Surface`, `Floor`, `Ceiling`, or `Random` (random Y between Min/MaxAltitude). |
| `Environment` | Material condition: `ALL_MATERIALS`, `SOLID_MATERIALS`, `NON_SOLID_MATERIALS`, `LIQUIDS`, `AIR`, `NONE`. |
| `Frequency` | Attempts per chunk (1–500). |
| `Rarity` | Percent chance (0.000001–100) each attempt proceeds. |
| `MinAltitude` / `MaxAltitude` | Y bounds (used in `Random` mode). |
| `HorizontalSpread` / `VerticalSpread` | *(Group only)* Max member offset from the origin (1–15 / 1–100). |
| `GroupSize` / `Variance` | *(Group only)* Plants per group ± variation. |
| `BlockSource` | Blocks the plant can replace at the placement position (`Scatter` requires at least 3). |

```properties
Group(Tallgrass,0,Surface,ALL_MATERIALS,4,50,60,128,6,3,20,5,minecraft:grass_block,minecraft:dirt)
Scatter(Dandelion,0,Surface,ALL_MATERIALS,8,60,60,128,minecraft:grass_block,minecraft:dirt,minecraft:sand)
```

### Registry

Runs a vanilla (or datapack-registered) placed feature by its resource location at the specified decoration step. This is the primary way to include vanilla features (vegetation, ores, structures) that OTG does not natively replicate.

**Syntax:** `Registry(FeatureResourceLocation,DecorationStep)`

| Parameter | Meaning |
|-----------|---------|
| `FeatureResourceLocation` | Namespaced ID of the placed feature (e.g. `minecraft:lush_caves_vegetation`). |
| `DecorationStep` | *(Optional)* Generation step at which the feature runs. Defaults to `VEGETAL_DECORATION`. |

Valid decoration steps (must match the `GenerationStep.Decoration` enum exactly):

| Value | Typical use |
|-------|------------|
| `RAW_GENERATION` | Terrain-shaping features run before biome decoration. |
| `LAKES` | Lake carving. |
| `LOCAL_MODIFICATIONS` | Modifications local to terrain (e.g. geodes). |
| `UNDERGROUND_STRUCTURES` | Underground structure starts. |
| `SURFACE_STRUCTURES` | Surface structure starts. |
| `STRONGHOLDS` | Stronghold placement. |
| `UNDERGROUND_ORES` | Ore veins and underground deposits. |
| `UNDERGROUND_DECORATION` | Underground decorations (glow lichen, sculk, etc.). |
| `FLUID_SPRINGS` | Water/lava spring generation. |
| `VEGETAL_DECORATION` | Plants, trees, and most surface vegetation **(default)**. |
| `TOP_LAYER_MODIFICATION` | Top-layer passes (snow, ice, freeze). |

```properties
Registry(minecraft:lush_caves_vegetation,VEGETAL_DECORATION)
Registry(minecraft:glow_lichen,UNDERGROUND_DECORATION)
Registry(minecraft:sculk_patch_deep_dark,UNDERGROUND_DECORATION)
```
