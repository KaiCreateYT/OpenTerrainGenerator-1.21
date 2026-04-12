# In-Game Editor Phase 5: WorldPreset YAML Editor

## Goal

Add full CRUD editor for WorldPreset YAML files (`{otgRoot}/WorldPresets/*.yaml`) inside the in-game editor. User can manage, create, clone, edit and delete WorldPresets — including metadata, Overworld/Nether/End dimension slots, custom Dimensions with portal configs, world-level and per-dimension GameRules, and world settings. Integrates with existing EditorHubScreen.

## Prerequisites

Phase 1-4 complete. Existing infrastructure:
- EditorHubScreen with preset selector
- Reusable widgets: `ScrollableListWidget`, `ScrollablePanel`, `DropdownWidget`, `SearchBoxWidget`, `CategoryTabsWidget`, `PropertyGridWidget`
- `PresetReloader` utility
- `BiomeSelectDialog` pattern for picker dialogs
- `WorldPresetConfig.toYamlString()` already exists (Jackson-backed)
- `WorldPresetConfigLoader.loadAll()` already exists
- Jackson YAML + snakeyaml shaded into platform JARs at `com.pg85.otg.dependency.jackson.*`

## Approach Summary

**Dedicated widgets per section (Approach B)** — new `WorldPresetEditorScreen` with tabs. Some tabs reuse PropertyGridWidget (flat scalar fields like Metadata and Settings); others have dedicated layouts (dimension slots with OTG/Non-OTG toggle, Dimensions accordion, GameRules tri-state list). No changes to existing `PropertyExtractor` — WorldPresetConfig is a Jackson POJO, not a settings-based config.

## Navigation

```
EditorHubScreen
    [Preset: ▶Default◀]  [Manage WorldPresets]  ← new button near preset selector
    [World Settings] [Biome Editor] [Group Settings] [BO Store]
    [Preview World]                                       [Back]

         ↓ "Manage WorldPresets"

ManageWorldPresetsScreen
    List YAMLs + Summary panel + New/Clone/Edit/Delete

         ↓ "New"                    ↓ "Edit"

WorldPresetWizardScreen      WorldPresetEditorScreen
    4-step template + init    7 tabs: Metadata | Overworld | Nether | End |
                              Dimensions | Settings | GameRules
```

Three new screens + wizard + 2 new reusable widgets (for dimension slot and accordion dimension card).

## Screens

### ManageWorldPresetsScreen

Layout:
- **Left** (scrollable): list of WorldPreset YAMLs (sorted by DisplayName). Entries show DisplayName; corrupted entries show "(invalid)" with Edit disabled.
- **Right**: summary panel for selected YAML — DisplayName, Description, Overworld (OTG preset name or Non-OTG type), Nether (preset or vanilla), End, custom Dimensions count.
- **Bottom buttons**: New, Clone, Edit, Delete. Back.

Actions:
- **New** → opens `WorldPresetWizardScreen`
- **Clone** → duplicates selected YAML to `{displayName}_copy.yaml` (counter suffix on collision), opens editor
- **Edit** → opens `WorldPresetEditorScreen` with selected YAML
- **Delete** → confirmation dialog. If matching active world, extra warning: "This WorldPreset is active in your current world. Deleting won't affect it."

### WorldPresetWizardScreen

4-step flow with Back/Next/Finish buttons:

**Step 0: Template**
- Radio options dynamically generated: "Blank" + one option per `.yaml` file in classpath resources `WorldPresets/` directory (Default.yaml, Example 1.yaml, Example 2.yaml, Example 3.yaml, Example 4.yaml shipped by default, others added automatically if present).
- "Blank" = minimal config with only DisplayName + vanilla slots.
- Other options = clone the selected resource YAML as base for the new config.
- Labels show filename stem (e.g. "Default", "Example 1", "Example 4"). Shipped resources enumerated via `WorldPresetTemplates.listResources()`.

**Step 1: Metadata**
- DisplayName (required, non-empty, unique)
- Description (optional)
- ModpackName (optional)
- Inline validation on DisplayName: blocks Next if empty or duplicates existing YAML's DisplayName

**Step 2: Dimensions**
- Overworld: OTG/Non-OTG radio
  - OTG → DropdownWidget listing all DimensionPresets
  - Non-OTG → DropdownWidget with built-in types (`normal`, `flat`, `amplified`, `large_biomes`) plus a "Custom…" entry at the end. Selecting "Custom…" opens a text-input dialog for `modid:name` values. Currently-set custom value displays as the dropdown label when it doesn't match built-ins.
- Nether: "Use vanilla" checkbox + DimensionPreset dropdown (disabled when vanilla)
- End: same as Nether

Custom Dimensions skipped in wizard — user adds them later in editor.

**Step 3: Confirm**
- Summary of all entered data
- Filename preview: `{normalized(displayName)}.yaml`
- **Finish** → creates WorldPresetConfig, writes YAML, closes wizard, opens WorldPresetEditorScreen with new config
- **Back** returns to Step 2

### WorldPresetEditorScreen

Header: title showing DisplayName, dirty indicator.
7 tabs (CategoryTabsWidget): `Metadata | Overworld | Nether | End | Dimensions | Settings | GameRules`.
Bottom: Save, Revert, Back.

Back with unsaved changes: confirmation dialog.
Revert: restores working copy from `original.clone()`.

#### Tab: Metadata
PropertyGridWidget with flat scalar fields:
- DisplayName (string, required — highlight if empty/duplicate)
- Description (string)
- ModpackName (string)

Uses `PropertyGridMode.PRESET_EDITOR` (no Override/Merge/OPV flags).

Version field not displayed in UI — it's a serialization marker, always `1` in Phase 5, not user-editable.

#### Tab: Overworld
Uses `DimensionSlotWidget` (new) for OTG/Non-OTG toggle + preset dropdown + portal config fields. Overworld variant shows Non-OTG option.

Layout:
- Type: radio [OTG Preset] / [Non-OTG World Type]
- OTG: DropdownWidget with all DimensionPresets
- Non-OTG: DropdownWidget with built-in types (`normal`, `flat`, `amplified`, `large_biomes`) + "Custom…" entry that opens a text-input dialog for `modid:name`. Custom value shown as dropdown label. Additional: NonOTGGeneratorSettings text field (string, for flat world settings JSON).
- Seed (long)
- Portal config: PortalBlocks, PortalColor, PortalMob, PortalIgnitionSource (text fields), RespawnInDimension (checkbox)

#### Tab: Nether / Tab: End
Same widget as Overworld, but:
- No Non-OTG option (only OTG preset or vanilla)
- "Use vanilla" checkbox at top — when checked, PresetFolderName = null, dropdown disabled

#### Tab: Dimensions
Scrollable list of `DimensionAccordionCard` widgets (new), one per custom dimension in `config.Dimensions[]`.
Each card:
- Header: `▼/▶ {PresetFolderName}` + `[- Remove]` button
- Expanded body:
  - PresetFolderName (DropdownWidget, required)
  - Seed (long)
  - Portal config (same fields as Overworld)
  - `[Edit GameRules Override]` button → opens `GameRulesEditorScreen` in per-dim mode

`[+ Add Dimension]` button at bottom appends a new blank OTGDimension entry.

Add/Remove updates `config.Dimensions[]`. No reordering in Phase 5.

#### Tab: Settings
PropertyGridWidget with 2 checkboxes: `GenerateStructures`, `BonusChest`.

#### Tab: GameRules
Inline within WorldPresetEditorScreen (world-level GameRules):
- SearchBoxWidget at top
- Scrollable list of `GameRuleTriStateWidget` — one per field in `WorldPresetConfig.GameRules` (50 rules: 42 Boolean + 8 Integer). Fields discovered via reflection over `WorldPresetConfig.GameRules.class.getDeclaredFields()` — ensures no manual list maintenance when new fields are added.
- Each rule row: [name] [tri-state control]
  - Booleans: 3-way radio (default / true / false)
  - Integers: 2-way (default / numeric input)
- "default" = null in YAML (don't override vanilla)

The separate `GameRulesEditorScreen` is used only for per-dimension GameRules override (opened from DimensionAccordionCard's "Edit GameRules Override" button), not for world-level GameRules.

### GameRulesEditorScreen

Reusable for world-level GameRules tab and per-dim GameRules override from DimensionAccordionCard.

Constructor:
```java
GameRulesEditorScreen(WorldPresetConfig.GameRules rules, Screen parent, Consumer<WorldPresetConfig.GameRules> onSave, String title)
```

Back with unsaved changes → confirmation dialog.
Save → callback writes rules into caller's config object.

## Data Layer

### WorldPresetFileScanner

```java
public record WorldPresetEntry(String displayName, Path path, WorldPresetConfig config, boolean valid) {}

public static List<WorldPresetEntry> scan(Path otgRoot);
```

Scans `{otgRoot}/WorldPresets/*.yaml|.yml`. Returns entries sorted by `displayName` (case-insensitive). Corrupted YAMLs get `valid=false` and `config=null`.

### WorldPresetYamlIO

```java
public static WorldPresetConfig load(Path yamlFile);
public static boolean save(Path yamlFile, WorldPresetConfig config);
```

`load()` uses Jackson's existing mapper (`WorldPresetConfigLoader.fromFile()`). `save()` calls `config.toYamlString()` and `Files.writeString()`. Returns true on success.

### WorldPresetOperations

```java
public static Path newBlank(Path otgRoot, String displayName);
public static Path cloneFrom(Path sourceYaml, String newDisplayName);
public static boolean delete(Path yamlFile);
```

All operations use `normalize(displayName)` for filename. Phase 5 widens `WorldPresetRegistrar.normalizeId()` from package-private to `public` so it can be reused by the editor code (one-line change). Collision handled by suffix counter (`_1`, `_2`, ...).

### WorldPresetTemplates

Three baked-in templates for wizard Step 0:
- `blank()` → minimal config (DisplayName only, vanilla everything)
- `fromResource("Default.yaml")` → load from classpath resources
- `fromResource("Example 4.yaml")` → load from classpath resources

If resource missing, fallback to `blank()` with warning.

## Data Flow

```
Load:    Jackson → WorldPresetConfig
         WorldPresetConfig original = loaded        ← for Revert/cancel
         WorldPresetConfig working  = loaded.clone() ← edited in UI
Dirty:   boolean dirty flag, set by widget callbacks on any field change
         (checked lazily via !working.toYamlString().equals(original.toYamlString())
         only when user clicks Back — cheaper than re-serializing per keystroke)
Save:    Files.writeString(path, working.toYamlString())
         original = working.clone()
         dirty = false
         PresetReloader.reload()                     ← refresh OTG caches
         WorldPresetConfigLoader.loadAll() cached in DimensionManager if needed
```

All screens hold state across `rebuildWidgets()` — reload-from-disk only on first `init()`, guarded by `if (working == null)`.

## Widgets

### DimensionSlotWidget (new)

Props: `OTGDimension dim`, `boolean allowNonOTG` (true for Overworld only), `boolean allowVanilla` (true for Nether/End), `List<String> otgPresets`.

Renders OTG/Non-OTG radio (if allowed), dropdown for preset/type, seed + portal config fields. Mutates `dim` fields on user input.

### DimensionAccordionCard (new)

Props: `OTGDimension dim`, `Runnable onRemove`, `Runnable onOpenGameRules`, `List<String> otgPresets`.

Expandable card. Collapsed: header with PresetFolderName + Remove button. Expanded: inline DimensionSlotWidget (vanilla=false, nonOTG=false) + Edit GameRules button.

### GameRuleTriStateWidget (new)

Props: `String ruleName`, `PropertyType type (BOOLEAN or INT)`, `Object currentValue` (Boolean/Integer/null), `Consumer<Object> onChange`.

Renders:
- Boolean: `[● default  ○ true  ○ false]` — 3 clickable radios
- Integer: `[● default  ○ [  123  ]]` — radio + numeric EditBox (EditBox disabled when default)

### GameRulesListWidget (new)

Reusable container for the full scrollable list of `GameRuleTriStateWidget` rows + SearchBoxWidget. Used by BOTH the world-level GameRules tab in WorldPresetEditorScreen AND the per-dimension GameRulesEditorScreen — extracts the common "search + 50 rule rows" pattern to a single widget.

Props: `WorldPresetConfig.GameRules rules` (mutated in place).

Uses reflection on `WorldPresetConfig.GameRules.class` to enumerate fields → infer type from field (Boolean/Integer) → render appropriate `GameRuleTriStateWidget`. On change, reflection-writes back to the bound GameRules instance.

## Error Handling

- **Empty DisplayName** — wizard: block Next with inline error. Editor: warning status "YAML not registered in Create World GUI (requires DisplayName)", Save still allowed.
- **Duplicate DisplayName** — wizard: block Next with inline error. Editor: warning status, Save allowed (users may create templates-in-progress).
- **Invalid OTGDimension (PresetFolderName null)** — warning icon in accordion card header, save allowed (entry ignored by registrar). When adding new dimension via `[+ Add Dimension]`, editor auto-picks the first available DimensionPreset from the dropdown to avoid the invalid state from the start. User can change it afterward.
- **Dangling PresetFolderName reference** — if a YAML references a DimensionPreset that no longer exists on disk (e.g. user deleted it), the dropdown shows the missing name as plain text (greyed out) with warning icon. Save preserves the reference (user may restore the preset later).
- **Invalid portal fields** — no editor validation (runtime check at portal placement).
- **Delete active world's WorldPreset** — confirmation dialog with extra warning about active world.
- **Corrupted YAML** — scanner logs warning, entry shown as "(invalid)" with Edit/Clone disabled (Delete still allowed).
- **File write error** — error dialog "Failed to save WorldPreset: {cause}". Dirty state preserved.
- **Missing baked-in resource template** — fallback to blank, log warning.

## Save Flow & Hot-Reload

1. User clicks Save in WorldPresetEditorScreen.
2. `WorldPresetYamlIO.save(path, working)` writes YAML.
3. `original = working.clone()` resets dirty state.
4. `PresetReloader.reload()` refreshes OTG engine's DimensionPreset cache. (WorldPreset YAML cache refresh happens via `WorldPresetConfigLoader.loadAll()` being called again inside `OTGRegistryHelper.loadOTGPresets()` next time MC loads registries.)
5. Status message: "Saved. Changes visible in Create World GUI next time you open it."
6. If YAML matches active world (by file path comparison: `Files.isSameFile(savedYamlPath, DimensionManager.getActiveWorldPresetConfigPath())`), hot-update in-flight GameRules:
   - For each loaded server level, call `GameRuleApplier.createGameRules(preset, worldLevelOverrides, dimOverrides, server)` to compute fresh rules
   - Apply resulting `GameRules` to each `Level` via `GameRuleManager` static map (replaces per-dim entries)
   - LevelStems and custom dimensions cannot be hot-updated (frozen in registry)

**No MC restart required** for Create World GUI updates. Because `RegistryDataLoader.load()` fires per-world-creation (our RegistryLoaderMixin refires), new/modified YAMLs appear in "Create World" GUI as soon as user reopens the dialog.

**Caveat for active world**: LevelStems / custom dimensions / portals are frozen in loaded world's registry. Changes to those slots only affect new worlds created after save. Status message documents this limitation when applicable: "Saved. New LevelStem changes (custom dimensions, portals) require restart. GameRules updated in-place."

**Active world detection**: Based on file path comparison. `DimensionManager` tracks `activeWorldPresetConfigPath` (added in Phase 5) — set during world load when YAML is detected. Delete confirmation and hot-update both use this path for matching. YAMLs with duplicate DisplayNames but different files are distinguished correctly.

## What's NOT in Phase 5

- Hot-reload for aktywnego świata's LevelStems / custom dimensions (requires unfreezing MC registry — not feasible)
- Export/Import YAMLs from arbitrary filesystem paths (only `{otgRoot}/WorldPresets/` scanned)
- Full in-game preview of all dimensions in a WorldPreset (would require multi-dim temp world creation)
- Modpack mode (`isModpackConfig`) — runtime flag, not serialized to YAML
- Version field editing — managed by editor, fixed at 1
- Reordering custom Dimensions list
