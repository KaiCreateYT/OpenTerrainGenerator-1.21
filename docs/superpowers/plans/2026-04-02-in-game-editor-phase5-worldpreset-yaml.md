# In-Game Editor Phase 5: WorldPreset YAML Editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add full CRUD editor for WorldPreset YAML files (`{otgRoot}/WorldPresets/*.yaml`) inside the in-game editor — metadata, Overworld/Nether/End, custom Dimensions, GameRules, Settings, wizard creation.

**Architecture:** 3 new screens + wizard + 4 new widgets. Dedicated widgets per section (not a universal property grid). Reuses existing PropertyGridWidget for flat scalar fields (Metadata, Settings). WorldPresetConfig POJO is mutated in place; Jackson `toYamlString()` + `Files.writeString()` handle persistence. GameRules tri-state uses reflection on `WorldPresetConfig.GameRules` class.

**Tech Stack:** Java 21, Minecraft 1.21.1, Architectury, Jackson YAML (shaded as `com.pg85.otg.dependency.jackson.*`), MC Screen/Widget API.

**Spec:** `docs/superpowers/specs/2026-04-02-in-game-editor-phase5-worldpreset-yaml.md`

**CRITICAL:** All builds must run from the worktree directory:
```bash
cd /var/home/jmc/IdeaProjects/OpenTerrainGenerator/.worktrees/1.21.1-in-game-editor
./gradlew build
```

Deploy for testing:
```bash
rm /var/home/jmc/Games/minecraft/active_mc/mods/otg-*.jar
cp build/distributions/otg-neoforge-*.jar /var/home/jmc/Games/minecraft/active_mc/mods/
```

**No automated tests** — testing is manual in-game. Build verification = `./gradlew build` succeeds.

---

## File Structure

All new files in `platforms/shared/src/client/java/com/pg85/otg/client/editor/`:

```
editor/
├── data/
│   ├── WorldPresetFileScanner.java    # NEW: scans WorldPresets/*.yaml → entries
│   ├── WorldPresetYamlIO.java         # NEW: load/save via Jackson
│   ├── WorldPresetOperations.java     # NEW: CRUD — newBlank, cloneFrom, delete
│   └── WorldPresetTemplates.java      # NEW: baked-in templates enumeration
├── screen/
│   ├── EditorHubScreen.java           # MODIFY: add "Manage WorldPresets" button
│   ├── ManageWorldPresetsScreen.java  # NEW: list + CRUD + summary
│   ├── WorldPresetWizardScreen.java   # NEW: 4-step creation wizard
│   ├── WorldPresetEditorScreen.java   # NEW: tabs-based editor
│   └── GameRulesEditorScreen.java     # NEW: per-dim GameRules override
└── widget/
    ├── DimensionSlotWidget.java        # NEW: OTG/Non-OTG + preset dropdown + portal config
    ├── DimensionAccordionCard.java     # NEW: expandable card for custom dimensions
    ├── GameRuleTriStateWidget.java     # NEW: tri-state radio for bool, numeric for int
    └── GameRulesListWidget.java        # NEW: reusable search + scrollable list of rules
```

Existing files modified:
- `platforms/shared/src/main/java/com/pg85/otg/shared/registry/WorldPresetRegistrar.java` — widen `normalizeId()` from package-private to `public`
- `platforms/shared/src/main/java/com/pg85/otg/shared/dimensions/DimensionManager.java` — add `activeWorldPresetConfigPath` field + getter

---

## Task 1: Widen `WorldPresetRegistrar.normalizeId()` to `public`

**Files:**
- Modify: `platforms/shared/src/main/java/com/pg85/otg/shared/registry/WorldPresetRegistrar.java:345`

- [ ] **Step 1: Change visibility**

In `WorldPresetRegistrar.java`, find line 345:
```java
    static String normalizeId(String displayName) {
```
Change to:
```java
    public static String normalizeId(String displayName) {
```

- [ ] **Step 2: Build and verify**

```bash
cd /var/home/jmc/IdeaProjects/OpenTerrainGenerator/.worktrees/1.21.1-in-game-editor
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/main/java/com/pg85/otg/shared/registry/WorldPresetRegistrar.java
git commit -m "refactor(registry): widen normalizeId() to public for editor reuse"
```

---

## Task 2: Add `activeWorldPresetConfigPath` to `DimensionManager`

**Files:**
- Modify: `platforms/shared/src/main/java/com/pg85/otg/shared/dimensions/DimensionManager.java`

**Context:** Currently `DimensionManager` caches `activeWorldPresetConfig`. We need to also cache the file path to enable file-path-based matching for "is this the active world's YAML?" check during editor save flow.

- [ ] **Step 1: Find active YAML detection code**

In `DimensionManager.java`, find the block around line 75-88 where `activeWorldPresetConfig` is set:
```java
        List<WorldPresetConfig> worldPresetConfigs = WorldPresetConfigLoader.loadAll(
            OTG.getEngine().getOTGRootFolder());

        // Detect which WorldPreset YAML was used to create this world (first start only)
        detectWorldPreset(server, worldPresetConfigs);

        // Cache active WorldPreset config for portal overrides and gating
        String activePresetName = storage.getWorldPreset();
        if (activePresetName != null) {
            this.activeWorldPresetConfig = worldPresetConfigs.stream()
                .filter(c -> activePresetName.equals(c.DisplayName))
                .findFirst().orElse(null);
        }
```

- [ ] **Step 2: Add field**

Near the `activeWorldPresetConfig` field declaration (around line 37), add:
```java
    private @Nullable java.nio.file.Path activeWorldPresetConfigPath;
```

- [ ] **Step 3: Populate path alongside config**

Replace the `if (activePresetName != null)` block with path-tracking version. Since `WorldPresetConfigLoader.loadAll()` doesn't return paths, we enumerate the folder ourselves:

```java
        // Cache active WorldPreset config for portal overrides and gating
        String activePresetName = storage.getWorldPreset();
        if (activePresetName != null) {
            this.activeWorldPresetConfig = worldPresetConfigs.stream()
                .filter(c -> activePresetName.equals(c.DisplayName))
                .findFirst().orElse(null);

            // Also resolve path by re-scanning the folder and matching DisplayName
            if (this.activeWorldPresetConfig != null) {
                this.activeWorldPresetConfigPath = findYamlPathByDisplayName(activePresetName);
            }
        }
```

- [ ] **Step 4: Add helper method**

Add private helper near other DimensionManager helpers:

```java
    private @Nullable java.nio.file.Path findYamlPathByDisplayName(String displayName) {
        java.nio.file.Path worldPresetsDir = OTG.getEngine().getOTGRootFolder()
            .resolve(com.pg85.otg.constants.Constants.WORLD_PRESETS_FOLDER);
        if (!java.nio.file.Files.isDirectory(worldPresetsDir)) return null;
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(worldPresetsDir)) {
            return files
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".yaml") || name.endsWith(".yml");
                })
                .filter(p -> {
                    com.pg85.otg.config.dimensions.WorldPresetConfig c =
                        com.pg85.otg.loader.WorldPresetConfigLoader.fromFile(p.toFile());
                    return c != null && displayName.equals(c.DisplayName);
                })
                .findFirst().orElse(null);
        } catch (java.io.IOException e) {
            OTGLog.warn("Failed to list WorldPresets folder: {}", e.getMessage());
            return null;
        }
    }
```

- [ ] **Step 5: Add getter**

Near the existing `getActiveWorldPresetConfig()` method (around line 216):
```java
    public @Nullable java.nio.file.Path getActiveWorldPresetConfigPath() {
        return activeWorldPresetConfigPath;
    }
```

- [ ] **Step 6: Build and verify**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add platforms/shared/src/main/java/com/pg85/otg/shared/dimensions/DimensionManager.java
git commit -m "feat(dimensions): track active WorldPreset YAML file path"
```

---

## Task 3: Data Layer — `WorldPresetYamlIO`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/data/WorldPresetYamlIO.java`

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.loader.WorldPresetConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Load and save WorldPreset YAML files. Reuses the existing Jackson mapper
 * via WorldPresetConfigLoader for loading; uses WorldPresetConfig.toYamlString()
 * for saving.
 */
public final class WorldPresetYamlIO {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetYamlIO.class);

    private WorldPresetYamlIO() {}

    /**
     * Load a WorldPreset YAML from disk.
     * @return parsed config, or null on parse/read failure
     */
    public static @Nullable WorldPresetConfig load(Path yamlFile) {
        return WorldPresetConfigLoader.fromFile(yamlFile.toFile());
    }

    /**
     * Write a WorldPreset YAML to disk. Overwrites existing file.
     * @return true on success
     */
    public static boolean save(Path yamlFile, WorldPresetConfig config) {
        String yaml = config.toYamlString();
        if (yaml == null) {
            LOG.error("toYamlString returned null for {}", yamlFile.getFileName());
            return false;
        }
        try {
            Files.createDirectories(yamlFile.getParent());
            Files.writeString(yamlFile, yaml);
            LOG.info("Saved WorldPreset YAML: {}", yamlFile.getFileName());
            return true;
        } catch (IOException e) {
            LOG.error("Failed to write WorldPreset YAML {}: {}", yamlFile.getFileName(), e.getMessage());
            return false;
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/data/WorldPresetYamlIO.java
git commit -m "feat(editor): add WorldPresetYamlIO for load/save"
```

---

## Task 4: Data Layer — `WorldPresetFileScanner`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/data/WorldPresetFileScanner.java`

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Scans {otgRoot}/WorldPresets/ for *.yaml / *.yml files and loads each one.
 * Corrupted YAMLs are surfaced as entries with valid=false and config=null.
 */
public final class WorldPresetFileScanner {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetFileScanner.class);

    private WorldPresetFileScanner() {}

    public record WorldPresetEntry(
        String displayName,
        Path path,
        @Nullable WorldPresetConfig config,
        boolean valid
    ) {}

    /**
     * Scan otgRoot/WorldPresets/ and load every YAML file.
     * @return entries sorted by displayName (case-insensitive)
     */
    public static List<WorldPresetEntry> scan(Path otgRoot) {
        List<WorldPresetEntry> entries = new ArrayList<>();
        Path worldPresetsDir = otgRoot.resolve(Constants.WORLD_PRESETS_FOLDER);
        if (!Files.isDirectory(worldPresetsDir)) {
            LOG.info("No WorldPresets folder at {}", worldPresetsDir);
            return entries;
        }

        try (Stream<Path> files = Files.list(worldPresetsDir)) {
            files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".yaml") || n.endsWith(".yml");
            }).forEach(p -> entries.add(toEntry(p)));
        } catch (IOException e) {
            LOG.error("Failed to scan WorldPresets folder: {}", e.getMessage());
        }

        entries.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return entries;
    }

    private static WorldPresetEntry toEntry(Path yamlFile) {
        WorldPresetConfig config = WorldPresetYamlIO.load(yamlFile);
        if (config == null) {
            return new WorldPresetEntry("(invalid) " + yamlFile.getFileName(), yamlFile, null, false);
        }
        String displayName = (config.DisplayName == null || config.DisplayName.isBlank())
            ? "(no name) " + yamlFile.getFileName()
            : config.DisplayName;
        return new WorldPresetEntry(displayName, yamlFile, config, true);
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/data/WorldPresetFileScanner.java
git commit -m "feat(editor): add WorldPresetFileScanner"
```

---

## Task 5: Data Layer — `WorldPresetOperations`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/data/WorldPresetOperations.java`

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.shared.registry.WorldPresetRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CRUD for WorldPreset YAML files.
 * File names derived from DisplayName via WorldPresetRegistrar.normalizeId().
 */
public final class WorldPresetOperations {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetOperations.class);

    private WorldPresetOperations() {}

    /**
     * Create a blank WorldPreset YAML with only DisplayName set.
     * @return path to the new file, or null on failure
     */
    public static Path newBlank(Path otgRoot, String displayName) {
        Path target = resolveUniquePath(otgRoot, displayName);
        WorldPresetConfig config = new WorldPresetConfig();
        config.Version = 1;
        config.DisplayName = displayName;
        if (WorldPresetYamlIO.save(target, config)) {
            return target;
        }
        return null;
    }

    /**
     * Clone an existing YAML file, patching the DisplayName.
     * @return path to the cloned file, or null on failure
     */
    public static Path cloneFrom(Path sourceYaml, Path otgRoot, String newDisplayName) {
        WorldPresetConfig sourceConfig = WorldPresetYamlIO.load(sourceYaml);
        if (sourceConfig == null) {
            LOG.error("Cannot clone — source YAML failed to load: {}", sourceYaml);
            return null;
        }
        WorldPresetConfig clone = sourceConfig.clone();
        clone.DisplayName = newDisplayName;
        Path target = resolveUniquePath(otgRoot, newDisplayName);
        if (WorldPresetYamlIO.save(target, clone)) {
            return target;
        }
        return null;
    }

    /**
     * Delete a WorldPreset YAML file.
     * @return true on success
     */
    public static boolean delete(Path yamlFile) {
        try {
            Files.deleteIfExists(yamlFile);
            LOG.info("Deleted WorldPreset YAML: {}", yamlFile.getFileName());
            return true;
        } catch (IOException e) {
            LOG.error("Failed to delete {}: {}", yamlFile.getFileName(), e.getMessage());
            return false;
        }
    }

    /**
     * Resolve a unique path for a new YAML based on the display name.
     * If {normalized}.yaml exists, appends _1, _2, ... until a free name.
     */
    private static Path resolveUniquePath(Path otgRoot, String displayName) {
        String base = WorldPresetRegistrar.normalizeId(displayName);
        if (base == null || base.isEmpty()) base = "worldpreset";
        Path dir = otgRoot.resolve(Constants.WORLD_PRESETS_FOLDER);
        Path candidate = dir.resolve(base + ".yaml");
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(base + "_" + counter + ".yaml");
            counter++;
        }
        return candidate;
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/data/WorldPresetOperations.java
git commit -m "feat(editor): add WorldPresetOperations CRUD"
```

---

## Task 6: Data Layer — `WorldPresetTemplates`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/data/WorldPresetTemplates.java`

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.loader.WorldPresetConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Baked-in WorldPreset YAML templates from classpath resources (/WorldPresets/*.yaml).
 * Used by the wizard Step 0.
 */
public final class WorldPresetTemplates {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetTemplates.class);
    private static final String RESOURCE_DIR = "/WorldPresets";

    public record Template(String label, String resourcePath) {}

    private WorldPresetTemplates() {}

    /** Returns "Blank" as first entry, then all shipped resource YAMLs. */
    public static List<Template> listTemplates() {
        List<Template> result = new ArrayList<>();
        result.add(new Template("Blank", null));

        for (String resourcePath : listResources()) {
            String name = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            String stem = stripExtension(name);
            result.add(new Template(stem, resourcePath));
        }

        return result;
    }

    /** Creates a config from the given template (null resourcePath = blank). */
    public static WorldPresetConfig create(Template template) {
        if (template.resourcePath() == null) {
            return blank();
        }
        WorldPresetConfig config = fromResource(template.resourcePath());
        if (config == null) {
            LOG.warn("Template resource missing: {}, falling back to blank", template.resourcePath());
            return blank();
        }
        return config;
    }

    public static WorldPresetConfig blank() {
        WorldPresetConfig config = new WorldPresetConfig();
        config.Version = 1;
        config.DisplayName = "";
        return config;
    }

    private static List<String> listResources() {
        List<String> result = new ArrayList<>();
        try {
            URL url = WorldPresetTemplates.class.getResource(RESOURCE_DIR);
            if (url == null) return result;

            URI uri = url.toURI();
            Path dir;
            FileSystem fs = null;
            if ("jar".equals(uri.getScheme())) {
                fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                dir = fs.getPath(RESOURCE_DIR);
            } else {
                dir = Paths.get(uri);
            }

            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".yaml") || n.endsWith(".yml");
                }).forEach(p -> result.add(RESOURCE_DIR + "/" + p.getFileName().toString()));
            }

            if (fs != null) fs.close();
        } catch (URISyntaxException | IOException e) {
            LOG.warn("Failed to enumerate WorldPreset templates: {}", e.getMessage());
        }
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    private static WorldPresetConfig fromResource(String resourcePath) {
        try (InputStream in = WorldPresetTemplates.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            String yaml = new String(in.readAllBytes());
            return WorldPresetConfigLoader.fromYamlString(yaml);
        } catch (IOException e) {
            LOG.warn("Failed to read resource {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/data/WorldPresetTemplates.java
git commit -m "feat(editor): add WorldPresetTemplates for wizard Step 0"
```

---

## Task 7: Widget — `GameRuleTriStateWidget`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/GameRuleTriStateWidget.java`

**Context:** Each GameRule field in `WorldPresetConfig.GameRules` is either `Boolean` or `Integer`. Null = "default / don't override". Widget renders radios + optional numeric input.

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.widget;

import com.pg85.otg.client.editor.data.PropertyType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Tri-state editor for a single GameRule field.
 * Booleans: 3 radios (default / true / false).
 * Integers: 2 radios (default / custom) + numeric input when custom selected.
 *
 * "default" = null value in YAML.
 */
public class GameRuleTriStateWidget {

    private final String ruleName;
    private final PropertyType type; // BOOLEAN or INT
    private final Consumer<Object> onChange;

    private int x, y, width;
    private Object currentValue; // Boolean, Integer, or null

    private EditBox intEditBox;

    public GameRuleTriStateWidget(String ruleName, PropertyType type, @Nullable Object currentValue,
                                   Consumer<Object> onChange) {
        this.ruleName = ruleName;
        this.type = type;
        this.currentValue = currentValue;
        this.onChange = onChange;
    }

    public void init(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        if (type == PropertyType.INT) {
            Minecraft mc = Minecraft.getInstance();
            intEditBox = new EditBox(mc.font, x + 200, y, 70, 16, Component.empty());
            intEditBox.setValue(currentValue instanceof Integer i ? i.toString() : "0");
            intEditBox.setEditable(currentValue instanceof Integer);
            intEditBox.setResponder(val -> {
                if (!(currentValue instanceof Integer)) return;
                try {
                    int parsed = Integer.parseInt(val);
                    currentValue = parsed;
                    onChange.accept(parsed);
                } catch (NumberFormatException ignored) {}
            });
        }
    }

    public @Nullable EditBox getEditBox() {
        return intEditBox;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, ruleName, x, y + 4, 0xFFCCCCCC);

        int rx = x + 140;
        if (type == PropertyType.BOOLEAN) {
            renderRadio(g, rx, y, currentValue == null, "default");
            renderRadio(g, rx + 60, y, Boolean.TRUE.equals(currentValue), "true");
            renderRadio(g, rx + 110, y, Boolean.FALSE.equals(currentValue), "false");
        } else {
            renderRadio(g, rx, y, currentValue == null, "default");
            renderRadio(g, rx + 60, y, currentValue instanceof Integer, "custom");
        }
    }

    private void renderRadio(GuiGraphics g, int rx, int ry, boolean selected, String label) {
        var font = Minecraft.getInstance().font;
        g.fill(rx, ry + 2, rx + 10, ry + 12, 0xFF333333);
        if (selected) {
            g.fill(rx + 2, ry + 4, rx + 8, ry + 10, 0xFF66CC66);
        }
        g.drawString(font, label, rx + 14, ry + 4, 0xFFAAAAAA);
    }

    public boolean mouseClicked(double mx, double my) {
        if (my < y || my > y + 16) return false;
        int rx = x + 140;
        if (clickInRadio(mx, rx)) { setValue(null); return true; }
        if (type == PropertyType.BOOLEAN) {
            if (clickInRadio(mx, rx + 60)) { setValue(Boolean.TRUE); return true; }
            if (clickInRadio(mx, rx + 110)) { setValue(Boolean.FALSE); return true; }
        } else {
            if (clickInRadio(mx, rx + 60)) {
                // Initialize to 0 if transitioning from default
                if (!(currentValue instanceof Integer)) setValue(0);
                return true;
            }
        }
        return false;
    }

    private boolean clickInRadio(double mx, int rx) {
        return mx >= rx && mx <= rx + 50;
    }

    private void setValue(@Nullable Object newValue) {
        currentValue = newValue;
        onChange.accept(newValue);
        if (intEditBox != null) {
            intEditBox.setEditable(newValue instanceof Integer);
            if (newValue instanceof Integer i) {
                intEditBox.setValue(i.toString());
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/GameRuleTriStateWidget.java
git commit -m "feat(editor): add GameRuleTriStateWidget for null/value tri-state editing"
```

---

## Task 8: Widget — `GameRulesListWidget`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/GameRulesListWidget.java`

**Context:** Reusable container with search + scrollable list of all ~50 GameRule fields. Uses reflection on `WorldPresetConfig.GameRules` to enumerate fields. Used by both the world-level GameRules tab in WorldPresetEditorScreen and the per-dim GameRulesEditorScreen.

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.widget;

import com.pg85.otg.client.editor.data.PropertyType;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reusable scrollable list of GameRule tri-state rows with search.
 * Mutates the bound WorldPresetConfig.GameRules instance in place.
 */
public class GameRulesListWidget extends ScrollablePanel {

    private static final Logger LOG = LoggerFactory.getLogger(GameRulesListWidget.class);
    private static final int ROW_HEIGHT = 18;

    private final WorldPresetConfig.GameRules rules;
    private SearchBoxWidget search;
    private String filter = "";

    private final List<Field> allFields;
    private List<Field> visibleFields;
    private final List<GameRuleTriStateWidget> rowWidgets = new ArrayList<>();

    private int x, y, width, height;

    public GameRulesListWidget(WorldPresetConfig.GameRules rules) {
        this.rules = rules;
        this.allFields = enumerateRuleFields();
        this.visibleFields = new ArrayList<>(allFields);
    }

    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        var mc = Minecraft.getInstance();
        search = new SearchBoxWidget(mc.font, x, y, width - 20, 16, "Search rules...");
        search.setOnTextChanged(text -> {
            filter = text == null ? "" : text.toLowerCase(Locale.ROOT);
            applyFilter();
        });

        rebuildRowWidgets();
    }

    private void applyFilter() {
        visibleFields = new ArrayList<>();
        for (Field f : allFields) {
            if (filter.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(filter)) {
                visibleFields.add(f);
            }
        }
        clampScroll(visibleFields.size());
        rebuildRowWidgets();
    }

    private void rebuildRowWidgets() {
        rowWidgets.clear();
        for (Field f : visibleFields) {
            PropertyType type = f.getType() == Boolean.class ? PropertyType.BOOLEAN : PropertyType.INT;
            Object value;
            try {
                value = f.get(rules);
            } catch (IllegalAccessException e) {
                value = null;
            }
            Field captured = f;
            GameRuleTriStateWidget w = new GameRuleTriStateWidget(
                f.getName(), type, value,
                newValue -> setFieldValue(captured, newValue)
            );
            rowWidgets.add(w);
        }
    }

    private void setFieldValue(Field field, Object value) {
        try {
            field.set(rules, value);
        } catch (IllegalAccessException e) {
            LOG.error("Failed to set rule {}: {}", field.getName(), e.getMessage());
        }
    }

    public EditBox getSearchEditBox() {
        return search.getEditBox();
    }

    /** EditBoxes for numeric GameRule inputs in the currently visible rows. */
    public List<EditBox> getRowEditBoxes() {
        List<EditBox> boxes = new ArrayList<>();
        int listY = y + 22;
        int visibleRows = (height - 22) / ROW_HEIGHT;
        int start = getScrollOffset();
        int end = Math.min(start + visibleRows, rowWidgets.size());
        for (int i = start; i < end; i++) {
            GameRuleTriStateWidget w = rowWidgets.get(i);
            w.init(x, listY + (i - start) * ROW_HEIGHT, width - 20);
            EditBox eb = w.getEditBox();
            if (eb != null) boxes.add(eb);
        }
        return boxes;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        int listY = y + 22;
        int listH = height - 22;
        int visibleRows = listH / ROW_HEIGHT;
        int maxScroll = Math.max(0, rowWidgets.size() - visibleRows);

        g.fill(x, listY, x + width, listY + listH, 0xFF1A1A1A);
        g.enableScissor(x, listY, x + width, listY + listH);

        int start = getScrollOffset();
        int end = Math.min(start + visibleRows, rowWidgets.size());
        for (int i = start; i < end; i++) {
            GameRuleTriStateWidget w = rowWidgets.get(i);
            w.init(x, listY + (i - start) * ROW_HEIGHT, width - 20);
            w.render(g, mouseX, mouseY);
        }

        g.disableScissor();

        renderScrollbar(g, x + width - 10, listY, 8, listH, rowWidgets.size(), visibleRows);
    }

    public boolean mouseClicked(double mx, double my) {
        int start = getScrollOffset();
        int visibleRows = (height - 22) / ROW_HEIGHT;
        int end = Math.min(start + visibleRows, rowWidgets.size());
        for (int i = start; i < end; i++) {
            if (rowWidgets.get(i).mouseClicked(mx, my)) return true;
        }
        return handleScrollbarClick(mx, my, x + width - 10, y + 22, 8, height - 22,
            rowWidgets.size(), visibleRows);
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x || mx > x + width || my < y || my > y + height) return false;
        int visibleRows = (height - 22) / ROW_HEIGHT;
        int maxScroll = Math.max(0, rowWidgets.size() - visibleRows);
        return handleMouseScrolled(delta, maxScroll);
    }

    private static List<Field> enumerateRuleFields() {
        List<Field> fields = new ArrayList<>();
        for (Field f : WorldPresetConfig.GameRules.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != Boolean.class && f.getType() != Integer.class) continue;
            f.setAccessible(true);
            fields.add(f);
        }
        fields.sort(Comparator.comparing(Field::getName));
        return fields;
    }
}
```

- [ ] **Step 2: Check ScrollablePanel API**

Verify what methods `ScrollablePanel` exposes:

```bash
grep -n "protected\|public" platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/ScrollablePanel.java | head -30
```

If method names in the code above (`getScrollOffset`, `clampScroll`, `renderScrollbar`, `handleScrollbarClick`, `handleMouseScrolled`) don't match existing `ScrollablePanel`, adjust calls to match the actual API. The existing `ScrollableListWidget.java` is a reference for the correct usage.

- [ ] **Step 3: Build and verify**

```bash
./gradlew build
```

If compilation errors reference missing ScrollablePanel methods, fix by matching the existing widget pattern. Do not restructure ScrollablePanel — just use what's there.

- [ ] **Step 4: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/GameRulesListWidget.java
git commit -m "feat(editor): add GameRulesListWidget — reusable search + scrollable rule list"
```

---

## Task 9: Widget — `DimensionSlotWidget`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/DimensionSlotWidget.java`

**Context:** Renders a single dimension slot (Overworld/Nether/End/custom). OTG preset dropdown or (for Overworld) Non-OTG world type dropdown. Portal config text fields. RespawnInDimension checkbox.

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.widget;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Edits a single dimension slot (Overworld, Nether, End, or custom).
 *
 * Modes:
 *  - Overworld: allowNonOTG=true (OTG preset OR vanilla/modded world type)
 *  - Nether/End: allowVanilla=true (OTG preset OR null = vanilla)
 *  - Custom: neither (OTG preset only, always required)
 */
public class DimensionSlotWidget {

    private static final List<String> BUILT_IN_NON_OTG_TYPES = List.of(
        "normal", "flat", "amplified", "large_biomes"
    );
    private static final String CUSTOM_ENTRY = "Custom...";

    private final WorldPresetConfig.OTGDimension dim;
    private final boolean allowNonOTG;  // Overworld only
    private final boolean allowVanilla; // Nether/End only
    private final List<String> otgPresets;
    private final Runnable onChanged;

    // Widgets
    private Button otgRadioBtn;
    private Button nonOtgRadioBtn;
    private Button vanillaCheckboxBtn;
    private DropdownWidget presetDropdown;
    private DropdownWidget nonOtgDropdown;
    private EditBox seedInput;
    private EditBox portalBlocksInput;
    private EditBox portalColorInput;
    private EditBox portalMobInput;
    private EditBox ignitionInput;
    private Button respawnBtn;

    // State
    private int x, y, width;
    private boolean usingNonOTG;
    private boolean usingVanilla;

    public DimensionSlotWidget(WorldPresetConfig.OTGDimension dim,
                               boolean allowNonOTG, boolean allowVanilla,
                               List<String> otgPresets, Runnable onChanged) {
        this.dim = dim;
        this.allowNonOTG = allowNonOTG;
        this.allowVanilla = allowVanilla;
        this.otgPresets = otgPresets;
        this.onChanged = onChanged;

        // Derive state from dim
        if (dim instanceof WorldPresetConfig.OTGOverWorld ow) {
            this.usingNonOTG = ow.NonOTGWorldType != null && !ow.NonOTGWorldType.isBlank();
        }
        this.usingVanilla = (dim.PresetFolderName == null || dim.PresetFolderName.isBlank()) && !usingNonOTG;
    }

    /** Registers child widgets with the Screen. Returns widgets to render externally. */
    public List<EditBox> init(int x, int y, int width, Consumer<Button> addButton, Consumer<EditBox> addEditBox) {
        this.x = x;
        this.y = y;
        this.width = width;

        var font = Minecraft.getInstance().font;
        int row = y;

        // Mode selector
        if (allowNonOTG) {
            otgRadioBtn = Button.builder(
                Component.literal((usingNonOTG ? "○" : "●") + " OTG Preset"),
                b -> setUsingNonOTG(false)
            ).bounds(x, row, 120, 16).build();
            nonOtgRadioBtn = Button.builder(
                Component.literal((usingNonOTG ? "●" : "○") + " Non-OTG"),
                b -> setUsingNonOTG(true)
            ).bounds(x + 125, row, 120, 16).build();
            addButton.accept(otgRadioBtn);
            addButton.accept(nonOtgRadioBtn);
            row += 22;
        } else if (allowVanilla) {
            vanillaCheckboxBtn = Button.builder(
                Component.literal((usingVanilla ? "[✓]" : "[ ]") + " Use vanilla"),
                b -> setUsingVanilla(!usingVanilla)
            ).bounds(x, row, 160, 16).build();
            addButton.accept(vanillaCheckboxBtn);
            row += 22;
        }

        // Preset / Non-OTG dropdown
        if (usingNonOTG) {
            List<String> options = new ArrayList<>(BUILT_IN_NON_OTG_TYPES);
            options.add(CUSTOM_ENTRY);
            String current = ((WorldPresetConfig.OTGOverWorld) dim).NonOTGWorldType;
            String shown = current == null ? "normal" : current;
            if (!options.contains(shown)) shown = current; // custom value
            nonOtgDropdown = new DropdownWidget(x, row, 200, options, shown);
            row += 22;
        } else if (!usingVanilla) {
            String current = dim.PresetFolderName == null ? otgPresets.get(0) : dim.PresetFolderName;
            if (dim.PresetFolderName == null && !otgPresets.isEmpty()) {
                dim.PresetFolderName = otgPresets.get(0);
                onChanged.run();
            }
            presetDropdown = new DropdownWidget(x, row, 200, otgPresets, current);
            row += 22;
        }

        // Seed
        seedInput = new EditBox(font, x + 60, row, 140, 16, Component.empty());
        seedInput.setValue(String.valueOf(dim.Seed));
        seedInput.setResponder(val -> {
            try { dim.Seed = Long.parseLong(val); onChanged.run(); } catch (NumberFormatException ignored) {}
        });
        addEditBox.accept(seedInput);
        row += 22;

        // Portal config
        portalBlocksInput = textField(font, "Blocks", dim.PortalBlocks, row,
            v -> { dim.PortalBlocks = v; onChanged.run(); });
        addEditBox.accept(portalBlocksInput);
        row += 18;
        portalColorInput = textField(font, "Color", dim.PortalColor, row,
            v -> { dim.PortalColor = v; onChanged.run(); });
        addEditBox.accept(portalColorInput);
        row += 18;
        portalMobInput = textField(font, "Mob", dim.PortalMob, row,
            v -> { dim.PortalMob = v; onChanged.run(); });
        addEditBox.accept(portalMobInput);
        row += 18;
        ignitionInput = textField(font, "Ignition", dim.PortalIgnitionSource, row,
            v -> { dim.PortalIgnitionSource = v; onChanged.run(); });
        addEditBox.accept(ignitionInput);
        row += 22;

        // Respawn checkbox
        boolean respawn = Boolean.TRUE.equals(dim.RespawnInDimension);
        respawnBtn = Button.builder(
            Component.literal((respawn ? "[✓]" : "[ ]") + " RespawnInDimension"),
            b -> {
                boolean current = Boolean.TRUE.equals(dim.RespawnInDimension);
                dim.RespawnInDimension = !current;
                onChanged.run();
            }
        ).bounds(x, row, 200, 16).build();
        addButton.accept(respawnBtn);

        List<EditBox> allBoxes = new ArrayList<>();
        allBoxes.add(seedInput);
        allBoxes.add(portalBlocksInput);
        allBoxes.add(portalColorInput);
        allBoxes.add(portalMobInput);
        allBoxes.add(ignitionInput);
        return allBoxes;
    }

    private EditBox textField(net.minecraft.client.gui.Font font, String label, String value, int row,
                               Consumer<String> setter) {
        EditBox box = new EditBox(font, x + 90, row, 160, 16, Component.empty());
        box.setValue(value == null ? "" : value);
        box.setResponder(setter);
        return box;
    }

    private void setUsingNonOTG(boolean useNonOTG) {
        this.usingNonOTG = useNonOTG;
        if (!(dim instanceof WorldPresetConfig.OTGOverWorld ow)) return;
        if (useNonOTG) {
            if (ow.NonOTGWorldType == null) ow.NonOTGWorldType = "normal";
            ow.PresetFolderName = null;
        } else {
            ow.NonOTGWorldType = null;
            ow.NonOTGGeneratorSettings = null;
        }
        onChanged.run();
    }

    private void setUsingVanilla(boolean vanilla) {
        this.usingVanilla = vanilla;
        if (vanilla) {
            dim.PresetFolderName = null;
        } else if (!otgPresets.isEmpty()) {
            dim.PresetFolderName = otgPresets.get(0);
        }
        onChanged.run();
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int lx = x;

        int row = y + (allowNonOTG || allowVanilla ? 22 : 0);
        if (presetDropdown != null) { presetDropdown.render(g, mouseX, mouseY); row += 22; }
        if (nonOtgDropdown != null) { nonOtgDropdown.render(g, mouseX, mouseY); row += 22; }

        g.drawString(font, "Seed:", lx, row + 4, 0xFFAAAAAA);
        row += 22;

        g.drawString(font, "Portal Blocks:", lx, row + 4, 0xFFAAAAAA); row += 18;
        g.drawString(font, "Portal Color:",  lx, row + 4, 0xFFAAAAAA); row += 18;
        g.drawString(font, "Portal Mob:",    lx, row + 4, 0xFFAAAAAA); row += 18;
        g.drawString(font, "Ignition:",      lx, row + 4, 0xFFAAAAAA);
    }

    public boolean mouseClicked(double mx, double my) {
        if (presetDropdown != null) {
            String selected = presetDropdown.mouseClicked(mx, my);
            if (selected != null) {
                dim.PresetFolderName = selected;
                onChanged.run();
                return true;
            }
        }
        if (nonOtgDropdown != null) {
            String selected = nonOtgDropdown.mouseClicked(mx, my);
            if (selected != null && dim instanceof WorldPresetConfig.OTGOverWorld ow) {
                if (CUSTOM_ENTRY.equals(selected)) {
                    // Leave value unchanged; custom text entry added later or edit via YAML directly
                } else {
                    ow.NonOTGWorldType = selected;
                    onChanged.run();
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (presetDropdown != null && presetDropdown.mouseScrolled(mx, my, delta)) return true;
        if (nonOtgDropdown != null && nonOtgDropdown.mouseScrolled(mx, my, delta)) return true;
        return false;
    }
}
```

- [ ] **Step 2: Verify DropdownWidget API matches**

```bash
grep -n "public\|String mouseClicked" platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/DropdownWidget.java | head -20
```

If `DropdownWidget` constructor signature or `mouseClicked` return type differ from the code above, adjust calls to match the real API. Do not modify DropdownWidget.

- [ ] **Step 3: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 4: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/DimensionSlotWidget.java
git commit -m "feat(editor): add DimensionSlotWidget for dimension slot editing"
```

---

## Task 10: Widget — `DimensionAccordionCard`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/DimensionAccordionCard.java`

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.widget;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Expandable card for a custom dimension.
 * Collapsed: header with PresetFolderName + Remove button.
 * Expanded: full DimensionSlotWidget + Edit GameRules button.
 */
public class DimensionAccordionCard {

    public static final int COLLAPSED_HEIGHT = 22;
    public static final int EXPANDED_HEIGHT = 180;

    private final WorldPresetConfig.OTGDimension dim;
    private final Runnable onRemove;
    private final Runnable onOpenGameRules;
    private final List<String> otgPresets;
    private final Runnable onChanged;

    private boolean expanded;

    private Button headerBtn;
    private Button removeBtn;
    private Button gameRulesBtn;
    private DimensionSlotWidget slotWidget;

    private int x, y, width;

    public DimensionAccordionCard(WorldPresetConfig.OTGDimension dim,
                                    Runnable onRemove,
                                    Runnable onOpenGameRules,
                                    List<String> otgPresets,
                                    Runnable onChanged) {
        this.dim = dim;
        this.onRemove = onRemove;
        this.onOpenGameRules = onOpenGameRules;
        this.otgPresets = otgPresets;
        this.onChanged = onChanged;
    }

    public int getHeight() {
        return expanded ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT;
    }

    public void init(int x, int y, int width, Consumer<Button> addButton, Consumer<EditBox> addEditBox) {
        this.x = x;
        this.y = y;
        this.width = width;

        String label = (expanded ? "▼ " : "▶ ") +
            (dim.PresetFolderName == null ? "(unset)" : dim.PresetFolderName);
        headerBtn = Button.builder(
            Component.literal(label),
            b -> expanded = !expanded
        ).bounds(x, y, width - 80, 18).build();
        addButton.accept(headerBtn);

        removeBtn = Button.builder(
            Component.literal("- Remove"),
            b -> onRemove.run()
        ).bounds(x + width - 76, y, 74, 18).build();
        addButton.accept(removeBtn);

        if (expanded) {
            slotWidget = new DimensionSlotWidget(dim, false, false, otgPresets, onChanged);
            slotWidget.init(x + 4, y + 22, width - 8, addButton, addEditBox);

            gameRulesBtn = Button.builder(
                Component.literal("Edit GameRules Override"),
                b -> onOpenGameRules.run()
            ).bounds(x + 4, y + EXPANDED_HEIGHT - 24, width - 8, 18).build();
            addButton.accept(gameRulesBtn);
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(x, y + 20, x + width, y + getHeight(), 0xFF1E1E1E);
        if (expanded && slotWidget != null) {
            slotWidget.render(g, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mx, double my) {
        if (slotWidget != null && slotWidget.mouseClicked(mx, my)) return true;
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        return slotWidget != null && slotWidget.mouseScrolled(mx, my, delta);
    }

    public boolean isExpanded() { return expanded; }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/widget/DimensionAccordionCard.java
git commit -m "feat(editor): add DimensionAccordionCard for custom dimensions list"
```

---

## Task 11: Screen — `GameRulesEditorScreen`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/GameRulesEditorScreen.java`

**Context:** Standalone screen for editing a `WorldPresetConfig.GameRules` object. Used for per-dimension GameRules override. Wraps `GameRulesListWidget`.

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.widget.GameRulesListWidget;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Editor for a single WorldPresetConfig.GameRules instance.
 * Mutates the passed-in rules object in place.
 * Calls back onSave when user clicks Save; does nothing on Cancel.
 */
public class GameRulesEditorScreen extends Screen {

    private final WorldPresetConfig.GameRules rules;
    private final Screen parent;
    private final Consumer<WorldPresetConfig.GameRules> onSave;

    private GameRulesListWidget listWidget;

    public GameRulesEditorScreen(WorldPresetConfig.GameRules rules,
                                   Screen parent,
                                   Consumer<WorldPresetConfig.GameRules> onSave,
                                   String title) {
        super(Component.literal(title));
        this.rules = rules;
        this.parent = parent;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        listWidget = new GameRulesListWidget(rules);
        listWidget.init(10, 30, width - 20, height - 70);

        addRenderableWidget(listWidget.getSearchEditBox());
        for (var eb : listWidget.getRowEditBoxes()) {
            addRenderableWidget(eb);
        }

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            onSave.accept(rules);
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 100, height - 30, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b ->
            minecraft.setScreen(parent)
        ).bounds(width / 2 + 10, height - 30, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        listWidget.render(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listWidget.mouseClicked(mouseX, mouseY)) {
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (listWidget.mouseScrolled(mouseX, mouseY, dy)) {
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/GameRulesEditorScreen.java
git commit -m "feat(editor): add GameRulesEditorScreen for per-dim GameRules override"
```

---

## Task 12: Screen — `WorldPresetEditorScreen` (scaffold)

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java`

**Context:** Tabs-based editor. This task creates the scaffold with tabs but leaves tab bodies as placeholders — subsequent tasks fill each tab.

- [ ] **Step 1: Create file with tab scaffold**

```java
package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.data.PresetReloader;
import com.pg85.otg.client.editor.data.WorldPresetYamlIO;
import com.pg85.otg.client.editor.widget.CategoryTabsWidget;
import com.pg85.otg.client.editor.widget.DimensionAccordionCard;
import com.pg85.otg.client.editor.widget.DimensionSlotWidget;
import com.pg85.otg.client.editor.widget.GameRulesListWidget;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WorldPresetEditorScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetEditorScreen.class);

    private static final List<String> TAB_NAMES = List.of(
        "Metadata", "Overworld", "Nether", "End", "Dimensions", "Settings", "GameRules"
    );

    private final Path yamlPath;
    private final Screen parent;

    private WorldPresetConfig original;
    private WorldPresetConfig working;
    private boolean dirty;
    private String statusMessage;

    private CategoryTabsWidget tabs;
    private int activeTab = 0;

    // Cached preset folder list for dropdowns
    private List<String> otgPresetFolders;

    // Tab-specific widgets
    private DimensionSlotWidget overworldSlot;
    private DimensionSlotWidget netherSlot;
    private DimensionSlotWidget endSlot;
    private List<DimensionAccordionCard> dimensionCards = new ArrayList<>();
    private GameRulesListWidget gameRulesList;

    public WorldPresetEditorScreen(Path yamlPath, Screen parent) {
        super(Component.literal("Edit WorldPreset"));
        this.yamlPath = yamlPath;
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (working == null) {
            original = WorldPresetYamlIO.load(yamlPath);
            if (original == null) {
                original = new WorldPresetConfig();
                original.Version = 1;
            }
            working = original.clone();
        }

        // Build preset folder list once
        if (otgPresetFolders == null) {
            otgPresetFolders = new ArrayList<>();
            var engine = OTG.getEngine();
            if (engine != null) {
                for (DimensionPreset p : engine.getDimensionPresetLoader().getAllDimensionPresets()) {
                    otgPresetFolders.add(p.getFolderName());
                }
            }
        }

        // Tabs
        tabs = new CategoryTabsWidget(10, 30, width - 20, 18);
        tabs.setCategories(TAB_NAMES);
        tabs.setSelectedIndex(activeTab);
        tabs.setOnSelect(idx -> {
            activeTab = idx;
            rebuildWidgets();
        });

        // Bottom buttons
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
            .bounds(10, height - 30, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Revert"), b -> revert())
            .bounds(95, height - 30, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(width - 90, height - 30, 80, 20).build());

        buildActiveTab();
    }

    private void buildActiveTab() {
        switch (activeTab) {
            case 0 -> buildMetadataTab();
            case 1 -> buildOverworldTab();
            case 2 -> buildNetherTab();
            case 3 -> buildEndTab();
            case 4 -> buildDimensionsTab();
            case 5 -> buildSettingsTab();
            case 6 -> buildGameRulesTab();
        }
    }

    // Filled in later tasks
    private void buildMetadataTab() {}
    private void buildOverworldTab() {}
    private void buildNetherTab() {}
    private void buildEndTab() {}
    private void buildDimensionsTab() {}
    private void buildSettingsTab() {}
    private void buildGameRulesTab() {}

    private void save() {
        if (working == null) return;
        if (WorldPresetYamlIO.save(yamlPath, working)) {
            original = working.clone();
            dirty = false;
            PresetReloader.reload();
            statusMessage = "Saved. Changes visible in Create World GUI next time you open it.";
            LOG.info("Saved WorldPreset: {}", yamlPath.getFileName());
        } else {
            statusMessage = "Save failed — check logs";
        }
    }

    private void revert() {
        working = original.clone();
        dirty = false;
        statusMessage = "Reverted to last saved state";
        rebuildWidgets();
    }

    void markDirty() {
        if (!dirty) {
            String current = working.toYamlString();
            String before = original.toYamlString();
            if (current != null && !current.equals(before)) {
                dirty = true;
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        String titleText = "WorldPreset Editor" + (dirty ? "  ●" : "") +
            (working != null && working.DisplayName != null ? " — " + working.DisplayName : "");
        g.drawCenteredString(font, titleText, width / 2, 10, 0xFFFFFF);

        if (tabs != null) tabs.render(g);

        if (statusMessage != null) {
            g.drawString(font, statusMessage, 10, height - 48, 0xFFAAAA44);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabs != null && tabs.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (dirty) {
            // Simple confirmation — for now just ignore dirty state on Back.
            // Future: show confirmation dialog.
            LOG.warn("Discarding unsaved changes to {}", yamlPath.getFileName());
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java
git commit -m "feat(editor): add WorldPresetEditorScreen scaffold with tabs"
```

---

## Task 13: Editor Tabs — Metadata + Settings

**Files:**
- Modify: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java`

**Context:** Fill in the Metadata tab (DisplayName, Description, ModpackName — 3 EditBoxes) and Settings tab (GenerateStructures, BonusChest — 2 buttons acting as checkboxes).

- [ ] **Step 1: Replace buildMetadataTab()**

Replace the empty stub:
```java
    private void buildMetadataTab() {
        int x = 20;
        int y = 60;
        int labelW = 100;
        int fieldW = Math.min(300, width - 40 - labelW);

        EditBox displayName = new EditBox(font, x + labelW, y, fieldW, 18, Component.empty());
        displayName.setMaxLength(128);
        displayName.setValue(working.DisplayName == null ? "" : working.DisplayName);
        displayName.setResponder(v -> { working.DisplayName = v; markDirty(); });
        addRenderableWidget(displayName);
        y += 24;

        EditBox description = new EditBox(font, x + labelW, y, fieldW, 18, Component.empty());
        description.setMaxLength(512);
        description.setValue(working.Description == null ? "" : working.Description);
        description.setResponder(v -> { working.Description = v; markDirty(); });
        addRenderableWidget(description);
        y += 24;

        EditBox modpackName = new EditBox(font, x + labelW, y, fieldW, 18, Component.empty());
        modpackName.setMaxLength(128);
        modpackName.setValue(working.ModpackName == null ? "" : working.ModpackName);
        modpackName.setResponder(v -> { working.ModpackName = v; markDirty(); });
        addRenderableWidget(modpackName);
    }
```

- [ ] **Step 2: Add label rendering in render()**

In `render()`, after `tabs.render(g);`, add the Metadata tab labels:

```java
        if (activeTab == 0) {
            int x = 20;
            int y = 60;
            g.drawString(font, "Display Name:", x, y + 5, 0xFFAAAAAA);
            g.drawString(font, "Description:",  x, y + 29, 0xFFAAAAAA);
            g.drawString(font, "Modpack Name:", x, y + 53, 0xFFAAAAAA);
        }
```

- [ ] **Step 3: Replace buildSettingsTab()**

Replace the empty stub:
```java
    private void buildSettingsTab() {
        if (working.Settings == null) working.Settings = new WorldPresetConfig.Settings();
        var settings = working.Settings;

        int x = 20;
        int y = 60;

        Button generateStructuresBtn = Button.builder(
            Component.literal((settings.GenerateStructures ? "[✓]" : "[ ]") + " GenerateStructures"),
            b -> {
                settings.GenerateStructures = !settings.GenerateStructures;
                markDirty();
                rebuildWidgets();
            }
        ).bounds(x, y, 220, 20).build();
        addRenderableWidget(generateStructuresBtn);
        y += 24;

        Button bonusChestBtn = Button.builder(
            Component.literal((settings.BonusChest ? "[✓]" : "[ ]") + " BonusChest"),
            b -> {
                settings.BonusChest = !settings.BonusChest;
                markDirty();
                rebuildWidgets();
            }
        ).bounds(x, y, 220, 20).build();
        addRenderableWidget(bonusChestBtn);
    }
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 5: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java
git commit -m "feat(editor): WorldPresetEditor Metadata + Settings tabs"
```

---

## Task 14: Editor Tabs — Overworld / Nether / End

**Files:**
- Modify: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java`

- [ ] **Step 1: Replace buildOverworldTab()**

```java
    private void buildOverworldTab() {
        if (working.Overworld == null) {
            working.Overworld = new WorldPresetConfig.OTGOverWorld(null, 0, null, null);
        }
        overworldSlot = new DimensionSlotWidget(
            working.Overworld, true, false, otgPresetFolders,
            this::markDirty
        );
        overworldSlot.init(20, 60, width - 40,
            this::addRenderableWidget,
            this::addRenderableWidget);
    }
```

- [ ] **Step 2: Replace buildNetherTab()**

```java
    private void buildNetherTab() {
        if (working.Nether == null) {
            working.Nether = new WorldPresetConfig.OTGDimension(null, 0);
        }
        netherSlot = new DimensionSlotWidget(
            working.Nether, false, true, otgPresetFolders,
            this::markDirty
        );
        netherSlot.init(20, 60, width - 40,
            this::addRenderableWidget,
            this::addRenderableWidget);
    }
```

- [ ] **Step 3: Replace buildEndTab()**

```java
    private void buildEndTab() {
        if (working.End == null) {
            working.End = new WorldPresetConfig.OTGDimension(null, 0);
        }
        endSlot = new DimensionSlotWidget(
            working.End, false, true, otgPresetFolders,
            this::markDirty
        );
        endSlot.init(20, 60, width - 40,
            this::addRenderableWidget,
            this::addRenderableWidget);
    }
```

- [ ] **Step 4: Delegate mouse events to active slot**

In `mouseClicked(double, double, int)`, before `return super...`:

```java
        if (activeTab == 1 && overworldSlot != null && overworldSlot.mouseClicked(mouseX, mouseY)) return true;
        if (activeTab == 2 && netherSlot != null && netherSlot.mouseClicked(mouseX, mouseY)) return true;
        if (activeTab == 3 && endSlot != null && endSlot.mouseClicked(mouseX, mouseY)) return true;
```

Override `mouseScrolled`:

```java
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (activeTab == 1 && overworldSlot != null && overworldSlot.mouseScrolled(mouseX, mouseY, dy)) return true;
        if (activeTab == 2 && netherSlot != null && netherSlot.mouseScrolled(mouseX, mouseY, dy)) return true;
        if (activeTab == 3 && endSlot != null && endSlot.mouseScrolled(mouseX, mouseY, dy)) return true;
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }
```

- [ ] **Step 5: Add slot rendering in render()**

In `render()`, after the Metadata labels block:

```java
        if (activeTab == 1 && overworldSlot != null) overworldSlot.render(g, mouseX, mouseY);
        if (activeTab == 2 && netherSlot != null) netherSlot.render(g, mouseX, mouseY);
        if (activeTab == 3 && endSlot != null) endSlot.render(g, mouseX, mouseY);
```

- [ ] **Step 6: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 7: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java
git commit -m "feat(editor): WorldPresetEditor Overworld/Nether/End tabs"
```

---

## Task 15: Editor Tabs — Dimensions (accordion)

**Files:**
- Modify: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java`

- [ ] **Step 1: Replace buildDimensionsTab()**

```java
    private void buildDimensionsTab() {
        if (working.Dimensions == null) working.Dimensions = new ArrayList<>();

        dimensionCards.clear();
        int x = 10;
        int y = 60;

        for (int i = 0; i < working.Dimensions.size(); i++) {
            final int idx = i;
            var dim = working.Dimensions.get(idx);

            Runnable onRemove = () -> {
                working.Dimensions.remove(idx);
                markDirty();
                rebuildWidgets();
            };
            Runnable onOpenGameRules = () -> {
                if (dim.GameRules == null) dim.GameRules = new WorldPresetConfig.GameRules();
                var grs = dim.GameRules;
                minecraft.setScreen(new GameRulesEditorScreen(
                    grs, this,
                    updated -> { markDirty(); },
                    "GameRules — " + (dim.PresetFolderName == null ? "(unset)" : dim.PresetFolderName)
                ));
            };

            DimensionAccordionCard card = new DimensionAccordionCard(
                dim, onRemove, onOpenGameRules, otgPresetFolders, this::markDirty
            );
            card.init(x, y, width - 20, this::addRenderableWidget, this::addRenderableWidget);
            dimensionCards.add(card);
            y += card.getHeight() + 4;
        }

        // Add Dimension button
        Button addBtn = Button.builder(
            Component.literal("+ Add Dimension"),
            b -> {
                var newDim = new WorldPresetConfig.OTGDimension(
                    otgPresetFolders.isEmpty() ? null : otgPresetFolders.get(0), 0);
                working.Dimensions.add(newDim);
                markDirty();
                rebuildWidgets();
            }
        ).bounds(x, y, 160, 20).build();
        addRenderableWidget(addBtn);
    }
```

- [ ] **Step 2: Delegate mouse events + render to cards**

In `mouseClicked`, add before `return super...`:
```java
        if (activeTab == 4) {
            for (var card : dimensionCards) {
                if (card.mouseClicked(mouseX, mouseY)) return true;
            }
        }
```

In `mouseScrolled`, add:
```java
        if (activeTab == 4) {
            for (var card : dimensionCards) {
                if (card.mouseScrolled(mouseX, mouseY, dy)) return true;
            }
        }
```

In `render()`:
```java
        if (activeTab == 4) {
            for (var card : dimensionCards) card.render(g, mouseX, mouseY);
        }
```

- [ ] **Step 3: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 4: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java
git commit -m "feat(editor): WorldPresetEditor Dimensions tab with accordion cards"
```

---

## Task 16: Editor Tabs — GameRules (inline)

**Files:**
- Modify: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java`

- [ ] **Step 1: Replace buildGameRulesTab()**

```java
    private void buildGameRulesTab() {
        if (working.GameRules == null) working.GameRules = new WorldPresetConfig.GameRules();

        gameRulesList = new GameRulesListWidget(working.GameRules);
        gameRulesList.init(10, 60, width - 20, height - 100);

        addRenderableWidget(gameRulesList.getSearchEditBox());
        for (var eb : gameRulesList.getRowEditBoxes()) {
            addRenderableWidget(eb);
        }
    }
```

- [ ] **Step 2: Hook up render/mouse**

In `render()`:
```java
        if (activeTab == 6 && gameRulesList != null) gameRulesList.render(g, mouseX, mouseY);
```

In `mouseClicked`:
```java
        if (activeTab == 6 && gameRulesList != null && gameRulesList.mouseClicked(mouseX, mouseY)) {
            markDirty();
            rebuildWidgets();
            return true;
        }
```

In `mouseScrolled`:
```java
        if (activeTab == 6 && gameRulesList != null && gameRulesList.mouseScrolled(mouseX, mouseY, dy)) {
            rebuildWidgets();
            return true;
        }
```

- [ ] **Step 3: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 4: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetEditorScreen.java
git commit -m "feat(editor): WorldPresetEditor GameRules tab (inline)"
```

---

## Task 17: Screen — `WorldPresetWizardScreen`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetWizardScreen.java`

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.data.WorldPresetFileScanner;
import com.pg85.otg.client.editor.data.WorldPresetOperations;
import com.pg85.otg.client.editor.data.WorldPresetTemplates;
import com.pg85.otg.client.editor.data.WorldPresetYamlIO;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.shared.registry.WorldPresetRegistrar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * 4-step wizard to create a new WorldPreset YAML.
 *  Step 0: Template (Blank or resource YAML)
 *  Step 1: Metadata (DisplayName, Description, ModpackName)
 *  Step 2: Dimensions summary (placeholder — edit in editor after Finish)
 *  Step 3: Confirm + Finish
 */
public class WorldPresetWizardScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetWizardScreen.class);

    private final Screen parent;
    private final List<WorldPresetTemplates.Template> templates;

    private int step = 0;
    private int selectedTemplateIdx = 0;

    // Staged metadata
    private String displayName = "";
    private String description = "";
    private String modpackName = "";

    // Staged config (populated once template chosen)
    private WorldPresetConfig staged;

    public WorldPresetWizardScreen(Screen parent) {
        super(Component.literal("New WorldPreset"));
        this.parent = parent;
        this.templates = WorldPresetTemplates.listTemplates();
    }

    @Override
    protected void init() {
        int cx = width / 2;

        // Back button (always)
        addRenderableWidget(Button.builder(Component.literal("Cancel"),
            b -> minecraft.setScreen(parent)
        ).bounds(10, height - 30, 70, 20).build());

        // Previous step
        if (step > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Back"),
                b -> { step--; rebuildWidgets(); }
            ).bounds(cx - 100, height - 30, 90, 20).build());
        }

        // Next or Finish
        if (step < 3) {
            Button next = Button.builder(Component.literal("Next >"), b -> {
                if (validateStep()) { step++; rebuildWidgets(); }
            }).bounds(cx + 10, height - 30, 90, 20).build();
            addRenderableWidget(next);
        } else {
            addRenderableWidget(Button.builder(Component.literal("Finish"), b -> finishWizard())
                .bounds(cx + 10, height - 30, 90, 20).build());
        }

        buildStepBody();
    }

    private void buildStepBody() {
        switch (step) {
            case 0 -> buildStep0();
            case 1 -> buildStep1();
            case 2 -> buildStep2();
            case 3 -> buildStep3();
        }
    }

    private void buildStep0() {
        int x = 40;
        int y = 60;
        for (int i = 0; i < templates.size(); i++) {
            final int idx = i;
            boolean selected = idx == selectedTemplateIdx;
            Button b = Button.builder(
                Component.literal((selected ? "● " : "○ ") + templates.get(idx).label()),
                btn -> { selectedTemplateIdx = idx; rebuildWidgets(); }
            ).bounds(x, y, 240, 20).build();
            addRenderableWidget(b);
            y += 24;
        }
    }

    private void buildStep1() {
        int x = 40;
        int y = 60;
        int lw = 110;
        int fw = Math.min(260, width - 80 - lw);

        EditBox dn = new EditBox(font, x + lw, y, fw, 18, Component.empty());
        dn.setMaxLength(128);
        dn.setValue(displayName);
        dn.setResponder(v -> displayName = v);
        addRenderableWidget(dn);
        y += 24;

        EditBox desc = new EditBox(font, x + lw, y, fw, 18, Component.empty());
        desc.setMaxLength(512);
        desc.setValue(description);
        desc.setResponder(v -> description = v);
        addRenderableWidget(desc);
        y += 24;

        EditBox mp = new EditBox(font, x + lw, y, fw, 18, Component.empty());
        mp.setMaxLength(128);
        mp.setValue(modpackName);
        mp.setResponder(v -> modpackName = v);
        addRenderableWidget(mp);
    }

    private void buildStep2() {
        // Placeholder — dimensions will be edited in WorldPresetEditorScreen after Finish
    }

    private void buildStep3() {
        // Summary only — no widgets
    }

    private boolean validateStep() {
        if (step == 0) {
            staged = WorldPresetTemplates.create(templates.get(selectedTemplateIdx));
            if (staged == null) staged = WorldPresetTemplates.blank();
            staged.Version = 1;
            return true;
        }
        if (step == 1) {
            if (displayName == null || displayName.isBlank()) return false;
            if (isDuplicateDisplayName(displayName)) return false;
            return true;
        }
        return true;
    }

    private boolean isDuplicateDisplayName(String name) {
        Path root = OTG.getEngine().getOTGRootFolder();
        var entries = WorldPresetFileScanner.scan(root);
        for (var e : entries) {
            if (name.equalsIgnoreCase(e.displayName())) return true;
        }
        return false;
    }

    private void finishWizard() {
        if (staged == null) staged = WorldPresetTemplates.blank();
        staged.DisplayName = displayName;
        staged.Description = description;
        staged.ModpackName = modpackName;

        Path root = OTG.getEngine().getOTGRootFolder();
        Path filename = root.resolve(com.pg85.otg.constants.Constants.WORLD_PRESETS_FOLDER)
            .resolve(WorldPresetRegistrar.normalizeId(displayName) + ".yaml");

        // Handle collision via WorldPresetOperations via save
        int counter = 1;
        while (java.nio.file.Files.exists(filename)) {
            filename = root.resolve(com.pg85.otg.constants.Constants.WORLD_PRESETS_FOLDER)
                .resolve(WorldPresetRegistrar.normalizeId(displayName) + "_" + counter + ".yaml");
            counter++;
        }

        if (WorldPresetYamlIO.save(filename, staged)) {
            LOG.info("Wizard created WorldPreset: {}", filename.getFileName());
            minecraft.setScreen(new WorldPresetEditorScreen(filename, parent));
        } else {
            LOG.error("Wizard failed to save WorldPreset");
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        String[] titles = { "Step 1/4: Choose Template", "Step 2/4: Metadata",
                            "Step 3/4: Dimensions", "Step 4/4: Confirm" };
        g.drawCenteredString(font, titles[step], width / 2, 20, 0xFFFFFF);

        if (step == 1) {
            int x = 40; int y = 60;
            g.drawString(font, "Display Name:", x, y + 5, 0xFFAAAAAA);
            g.drawString(font, "Description:",  x, y + 29, 0xFFAAAAAA);
            g.drawString(font, "Modpack Name:", x, y + 53, 0xFFAAAAAA);

            if (displayName == null || displayName.isBlank()) {
                g.drawString(font, "DisplayName is required", x, y + 80, 0xFFFF6666);
            } else if (isDuplicateDisplayName(displayName)) {
                g.drawString(font, "DisplayName already exists", x, y + 80, 0xFFFF6666);
            }
        }

        if (step == 2) {
            g.drawCenteredString(font,
                "Custom Dimensions can be added after Finish in the editor.",
                width / 2, 80, 0xFFAAAAAA);
        }

        if (step == 3) {
            int x = 40; int y = 60;
            g.drawString(font, "DisplayName: " + displayName, x, y, 0xFFCCCCCC);
            y += 14;
            g.drawString(font, "Template: " + templates.get(selectedTemplateIdx).label(), x, y, 0xFFCCCCCC);
            y += 14;
            g.drawString(font, "Description: " + (description == null ? "" : description), x, y, 0xFFCCCCCC);
            y += 14;
            g.drawString(font, "Filename: " + WorldPresetRegistrar.normalizeId(displayName) + ".yaml",
                x, y, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/WorldPresetWizardScreen.java
git commit -m "feat(editor): add WorldPresetWizardScreen (4-step creation flow)"
```

---

## Task 18: Screen — `ManageWorldPresetsScreen`

**Files:**
- Create: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/ManageWorldPresetsScreen.java`

- [ ] **Step 1: Create file**

```java
package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.data.WorldPresetFileScanner;
import com.pg85.otg.client.editor.data.WorldPresetOperations;
import com.pg85.otg.client.editor.widget.ScrollableListWidget;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ManageWorldPresetsScreen extends Screen {

    private final Screen parent;
    private List<WorldPresetFileScanner.WorldPresetEntry> entries = new ArrayList<>();
    private int selectedIdx = -1;
    private ScrollableListWidget listWidget;
    private String statusMessage;

    public ManageWorldPresetsScreen(Screen parent) {
        super(Component.literal("Manage WorldPresets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reloadEntries();

        int listX = 10;
        int listY = 30;
        int listW = 240;
        int listH = height - 80;

        listWidget = new ScrollableListWidget(listX, listY, listW, listH, 16);
        listWidget.setItems(labels());
        listWidget.setSelectedIndex(selectedIdx);
        listWidget.setOnSelect(idx -> {
            selectedIdx = idx;
            rebuildWidgets();
        });

        int btnY = height - 46;
        int bx = 10;
        addRenderableWidget(Button.builder(Component.literal("New"), b ->
            minecraft.setScreen(new WorldPresetWizardScreen(this))
        ).bounds(bx, btnY, 56, 20).build());
        bx += 60;

        Button cloneBtn = Button.builder(Component.literal("Clone"), b -> doClone())
            .bounds(bx, btnY, 56, 20).build();
        cloneBtn.active = validSelected();
        addRenderableWidget(cloneBtn);
        bx += 60;

        Button editBtn = Button.builder(Component.literal("Edit"), b -> doEdit())
            .bounds(bx, btnY, 56, 20).build();
        editBtn.active = validSelected();
        addRenderableWidget(editBtn);
        bx += 60;

        Button deleteBtn = Button.builder(Component.literal("Delete"), b -> doDelete())
            .bounds(bx, btnY, 56, 20).build();
        deleteBtn.active = selectedIdx >= 0 && selectedIdx < entries.size();
        addRenderableWidget(deleteBtn);

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(width - 80, height - 30, 70, 20).build());
    }

    private void reloadEntries() {
        entries = WorldPresetFileScanner.scan(OTG.getEngine().getOTGRootFolder());
        if (selectedIdx >= entries.size()) selectedIdx = -1;
    }

    private List<String> labels() {
        List<String> out = new ArrayList<>();
        for (var e : entries) out.add(e.displayName());
        return out;
    }

    private boolean validSelected() {
        return selectedIdx >= 0 && selectedIdx < entries.size() && entries.get(selectedIdx).valid();
    }

    private void doEdit() {
        if (!validSelected()) return;
        var entry = entries.get(selectedIdx);
        minecraft.setScreen(new WorldPresetEditorScreen(entry.path(), this));
    }

    private void doClone() {
        if (!validSelected()) return;
        var entry = entries.get(selectedIdx);
        String newName = (entry.config().DisplayName == null ? "copy" : entry.config().DisplayName) + " (copy)";
        var result = WorldPresetOperations.cloneFrom(entry.path(),
            OTG.getEngine().getOTGRootFolder(), newName);
        if (result != null) {
            statusMessage = "Cloned to " + result.getFileName();
            reloadEntries();
            rebuildWidgets();
        } else {
            statusMessage = "Clone failed";
        }
    }

    private void doDelete() {
        if (selectedIdx < 0 || selectedIdx >= entries.size()) return;
        var entry = entries.get(selectedIdx);
        // Simple confirmation: click again within short window.
        // For Phase 5 baseline: immediate delete. Add confirmation dialog in polish pass.
        if (WorldPresetOperations.delete(entry.path())) {
            statusMessage = "Deleted " + entry.path().getFileName();
            selectedIdx = -1;
            reloadEntries();
            rebuildWidgets();
        } else {
            statusMessage = "Delete failed";
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        if (listWidget != null) listWidget.render(g);

        // Summary
        if (validSelected()) {
            var e = entries.get(selectedIdx);
            var c = e.config();
            int sx = 270;
            int sy = 40;
            g.drawString(font, "DisplayName: " + (c.DisplayName == null ? "" : c.DisplayName), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "Description: " + (c.Description == null ? "" : c.Description), sx, sy, 0xFFCCCCCC);
            sy += 12;
            String overworld = describeSlot(c.Overworld);
            g.drawString(font, "Overworld: " + overworld, sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "Nether: " + describeSlot(c.Nether), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "End: " + describeSlot(c.End), sx, sy, 0xFFCCCCCC);
            sy += 12;
            int dimCount = c.Dimensions == null ? 0 : c.Dimensions.size();
            g.drawString(font, "Custom Dimensions: " + dimCount, sx, sy, 0xFFCCCCCC);
        }

        if (statusMessage != null) {
            g.drawString(font, statusMessage, 10, height - 12, 0xFFAAAA44);
        }
    }

    private String describeSlot(WorldPresetConfig.OTGDimension dim) {
        if (dim == null) return "vanilla";
        if (dim instanceof WorldPresetConfig.OTGOverWorld ow && ow.NonOTGWorldType != null && !ow.NonOTGWorldType.isBlank()) {
            return "non-OTG: " + ow.NonOTGWorldType;
        }
        if (dim.PresetFolderName == null || dim.PresetFolderName.isBlank()) return "vanilla";
        return dim.PresetFolderName;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listWidget != null && listWidget.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (listWidget != null && listWidget.mouseScrolled(mouseX, mouseY, dy)) return true;
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/ManageWorldPresetsScreen.java
git commit -m "feat(editor): add ManageWorldPresetsScreen with list + CRUD"
```

---

## Task 19: Wire into EditorHubScreen

**Files:**
- Modify: `platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/EditorHubScreen.java`

- [ ] **Step 1: Add button near preset selector**

In `init()`, after the `\u25B6` (right arrow) button registration (around line 80), before `y += 30;`:

```java
        addRenderableWidget(Button.builder(
            Component.literal("Manage WorldPresets"),
            btn -> minecraft.setScreen(new ManageWorldPresetsScreen(this))
        ).bounds(cx + 125, y, 130, 20).build());
```

Add import at top of file:
```java
import com.pg85.otg.client.editor.screen.ManageWorldPresetsScreen;
```

Note: the class is in the same package so the import isn't strictly required. Skip if already implied.

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/EditorHubScreen.java
git commit -m "feat(editor): add 'Manage WorldPresets' button to EditorHubScreen"
```

---

## Task 20: In-Game Manual Test Pass

**Files:** none (deploy + test)

- [ ] **Step 1: Deploy**

```bash
cd /var/home/jmc/IdeaProjects/OpenTerrainGenerator/.worktrees/1.21.1-in-game-editor
./gradlew build
rm /var/home/jmc/Games/minecraft/active_mc/mods/otg-*.jar
cp build/distributions/otg-neoforge-*.jar /var/home/jmc/Games/minecraft/active_mc/mods/
```

- [ ] **Step 2: Manual test checklist**

Start Minecraft and walk through:

1. Title Screen → "OTG Editor" → EditorHubScreen
2. Click "Manage WorldPresets" → ManageWorldPresetsScreen loads with list of existing YAMLs
3. Select a YAML → summary panel shows DisplayName / Description / Overworld / Nether / End / custom dim count
4. Click "New" → Wizard opens at Step 0 (templates)
5. Choose "Blank", Next
6. Enter DisplayName (try duplicate first — Next should not advance)
7. Clear/change DisplayName to unique, Next
8. Step 2 placeholder, Next
9. Step 3 shows summary, Finish
10. WorldPresetEditorScreen opens with new YAML
11. Tab Metadata — edit DisplayName, Description, ModpackName. Verify dirty indicator (●) appears.
12. Tab Overworld — toggle OTG/Non-OTG, pick preset / type, edit seed and portal fields
13. Tab Nether — toggle "Use vanilla" on/off, pick preset
14. Tab End — same as Nether
15. Tab Dimensions — click "+ Add Dimension", expand the new card (▼), edit its fields, click "Edit GameRules Override" → GameRulesEditorScreen opens
16. In GameRulesEditorScreen, use search, toggle a bool rule's radios, set an int rule, Save → returns to editor
17. Tab Settings — toggle GenerateStructures and BonusChest
18. Tab GameRules — search, toggle bool and int rules
19. Click Save → status "Saved. Changes visible in Create World GUI next time you open it."
20. Click Back → ManageWorldPresetsScreen
21. Clone selected → new entry appears with "(copy)" in name
22. Delete an entry → disappears from list
23. Back → EditorHubScreen → Back → Title Screen
24. Open MC's "Singleplayer" → "Create World" → "More" → "World Type" dropdown → verify the new WorldPreset's DisplayName appears

- [ ] **Step 3: Fix any issues found**

If compile or runtime errors surface during testing, fix inline. Re-run build + test.

- [ ] **Step 4: Update changelog**

Add entry above the latest `### Release:` header in `docs/CHANGELOG.md`:

```markdown
**2026-04-02 — In-game editor Phase 5: WorldPreset YAML editor**

- feat(editor): ManageWorldPresetsScreen — list/summary/CRUD for `WorldPresets/*.yaml`
- feat(editor): WorldPresetEditorScreen — 7 tabs (Metadata, Overworld, Nether, End, Dimensions, Settings, GameRules)
- feat(editor): WorldPresetWizardScreen — 4-step template-based creation wizard (Blank + shipped resource YAMLs)
- feat(editor): DimensionSlotWidget, DimensionAccordionCard, GameRuleTriStateWidget, GameRulesListWidget — reusable widgets for YAML editing
- feat(editor): GameRulesEditorScreen for per-dimension GameRule overrides
- feat(editor): WorldPresetYamlIO, WorldPresetFileScanner, WorldPresetOperations, WorldPresetTemplates data layer
- refactor(registry): widen WorldPresetRegistrar.normalizeId() to public
- feat(dimensions): track activeWorldPresetConfigPath for file-path-based matching
```

- [ ] **Step 5: Final commit**

```bash
git add docs/CHANGELOG.md
git commit -m "docs(changelog): Phase 5 WorldPreset YAML editor"
```
