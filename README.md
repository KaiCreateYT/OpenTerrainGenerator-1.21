##  OpenTerrainGenerator by Team OTG

OpenTerrainGenerator for MC 1.20.x is under development, alpha builds are available in the dev-releases channel of the OTG Discord.



### Team OTG
* <a href="https://github.com/PG85">PG85</a>
* MCPitman
* <a href="https://github.com/authvin">Authvin</a>
* <a href="https://github.com/Coll1234567">Josh</a>
* <a href="https://github.com/SuperCoder7979">SuperCoder79</a>
* <a href="https://github.com/SXRWahrheit">Wahrheit</a>

We're always looking for people to contribute or collaborate with. For OTG 1.20, we have switched to developing on Fabric first, with plans to port to other platforms. If you'd like to contribute, collaborate or become part of Team OTG, join us on the OTG Discord!

## Installation / building

- As with Forge mods, clone the repo, then run `/gradlew genEclipseRuns`, then `/gradlew eclipse` (for Eclipse IDE).
- To create release jars in the `build/distributions` folder, update the build version in build.gradle, then run `/gradlew`.
- If you're having problems, make a sacrifice to the gradle gods and/or run `/gradlew clean` and then `/gradlew --refresh-dependencies`.

### IntelliJ Building Instructions

- All you have to do is Open IntelliJ and import the project folder, make sure you Trust the gradle project, and IntelliJ will do the rest :)
- Follow the same instructions that you do for Eclipse if you want to build -- do note that IntelliJ has a gradle GUI that you can use once you've imported the project. (Should be on the right of the code.)

## Commands

### Dimension Management

OTG supports creating custom dimensions from any preset:

| Command | Permission | Description |
|---------|------------|-------------|
| `/otg dimension create <preset>` | OP (level 2) | Creates a new dimension using the specified preset. Requires server restart. |
| `/otg dimension delete <name>` | OP (level 2) | Removes a dimension (keeps world data). |
| `/otg dimension delete <name> --purge --confirm` | OP (level 2) | Removes a dimension and deletes all world data permanently. |
| `/otg dimension list` | All players | Lists all OTG dimensions. |
| `/otg dimension info <name>` | All players | Shows dimension details (preset, seed, creation date). |

### Teleportation

| Command | Permission | Description |
|---------|------------|-------------|
| `/otg tp <dimension>` | All players | Teleports to the specified dimension. Automatically finds safe spawn location. |

Supported dimension names for `/otg tp`:
- `overworld`, `the_nether`, `the_end` - vanilla dimensions
- Any OTG dimension name (e.g., `void`, `biome_bundle`)

## Links
* [CurseForge](https://minecraft.curseforge.com/projects/open-terrain-generator)
* [Wiki](http://openterraingen.wikia.com/wiki/Open_Terrain_Generator_Wiki)
* [Discord](https://discord.com/invite/UXzdVTH)
* [Installation](https://openterraingen.fandom.com/wiki/Installing_OTG) for Spigot and Forge

## Original developers

OpenTerrainGenerator is a fork of Terrain Control, which is the successor to <a href="http://www.minecraftforum.net/topic/313991-phoenixterrainmod/">PhoenixTerrainMod</a>, which was based on <a href="http://www.minecraftforum.net/topic/71565-biomemod/">BiomeTerrainMod</a>. 

* Buycruss       - BiomeTerrainMod
* R-T-B          - PhoenixTerrainMod
* <a href="http://dev.bukkit.org/profiles/Khoorn/">Khoorn - TerrainControl</a> (known as <a href="https://github.com/Wickth">Wickth</a> on GitHub)
* <a href="https://github.com/oloflarsson">Oloflarsson/Cayorion - TerrainControl</a>
* <a href="https://github.com/Timethor">Timethor - TerrainControl</a>
* <a href="https://github.com/rutgerkok">Rutgerkok - TerrainControl</a>
* <a href="https://github.com/bloodmc">BloodMC - TerrainControl</a>
