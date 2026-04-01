# Resource Queue Editing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make resource queue entries (Ore, Tree, CustomObject, Dungeon, etc.) editable in BiomeEditorScreen — add, remove, reorder, and edit individual entries inline.

**Architecture:** ResourceEntry is a mutable data class wrapping one ConfigFunction line. ResourceQueueEditor is a dedicated screen opened from BiomeEditorScreen that shows a scrollable list of entries with inline text editing, add/remove/reorder buttons. ConfigWriter gets a new `saveWithResources()` method that replaces resource queue lines in rawLines.

**Tech Stack:** Java 21, Minecraft 1.21.1, Architectury, MC Screen/GuiGraphics/EditBox API

**CRITICAL:** All builds must run from the worktree:
```bash
cd /var/home/jmc/IdeaProjects/OpenTerrainGenerator/.worktrees/1.21.1-in-game-editor
./gradlew build
```

**No automated tests** — manual in-game testing.

---

## File Structure

```
editor/
├── data/
│   ├── ResourceEntry.java        # NEW: mutable wrapper for one resource queue line
│   └── ConfigWriter.java         # MODIFY: add saveWithResources()
├── screen/
│   ├── ResourceQueueScreen.java  # NEW: full-screen resource queue editor
│   └── BiomeEditorScreen.java    # MODIFY: open ResourceQueueScreen, pass/receive entries
└── widget/                       # No changes — reuses ScrollableListWidget
```

---

## Task 1: ResourceEntry — Data Class

**Files:**
- Create: `editor/data/ResourceEntry.java`

- [ ] **Step 1: Create ResourceEntry**

```java
package com.pg85.otg.client.editor.data;

/**
 * Mutable wrapper for one resource queue line (e.g. "Ore(minecraft:coal_ore,17,20,100.0,0,127,minecraft:stone)").
 * Stores the full raw line as a string — editing is done on the raw text.
 */
public class ResourceEntry {

    private String line;
    private boolean dirty;
    private boolean deleted;

    public ResourceEntry(String line) {
        this.line = line.trim();
    }

    public String getLine() { return line; }
    public boolean isDirty() { return dirty; }
    public boolean isDeleted() { return deleted; }

    public void setLine(String line) {
        if (!this.line.equals(line)) {
            this.line = line.trim();
            this.dirty = true;
        }
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        this.dirty = true;
    }

    public void clearDirty() { this.dirty = false; }

    /**
     * Extract the function name (e.g. "Ore" from "Ore(...)").
     */
    public String getFunctionName() {
        int parenIdx = line.indexOf('(');
        return parenIdx > 0 ? line.substring(0, parenIdx) : line;
    }
}
```

- [ ] **Step 2: Build and verify**
```bash
cd /var/home/jmc/IdeaProjects/OpenTerrainGenerator/.worktrees/1.21.1-in-game-editor && ./gradlew build
```

- [ ] **Step 3: Commit**
```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/data/ResourceEntry.java
git commit -m "feat(editor): add ResourceEntry — mutable wrapper for resource queue lines"
```

---

## Task 2: ConfigWriter.saveWithResources()

**Files:**
- Modify: `editor/data/ConfigWriter.java`

- [ ] **Step 1: Add saveWithResources method**

This method extends the existing `save()` to also handle resource queue line changes. It replaces all resource queue lines in rawLines with the provided entries (in order), skipping deleted ones and inserting new ones.

```java
/**
 * Save properties AND resource queue entries to a .bc file.
 * Resource queue lines (containing "(") in rawLines are replaced wholesale
 * with the entries list, preserving non-resource lines as-is.
 */
public static boolean saveWithResources(Path iniPath, List<String> rawLines,
                                         List<PropertyValue> properties,
                                         List<ResourceEntry> resources) {
    // First, apply property changes to rawLines (same logic as save())
    Map<String, String> changes = new HashMap<>();
    for (PropertyValue pv : properties) {
        if (pv.isDirty()) {
            changes.put(pv.getDefinition().name(), pv.getValue());
        }
    }

    List<String> output = new ArrayList<>(rawLines.size());
    boolean resourcesInserted = false;
    boolean seenResource = false;

    for (String line : rawLines) {
        String trimmed = line.trim();

        // Resource queue line — skip (will be replaced)
        if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("(")
                && !trimmed.contains(":") || (trimmed.contains("(") && trimmed.indexOf('(') < trimmed.indexOf(':'))) {
            // Heuristic: line starts with FunctionName( — it's a resource
            if (!trimmed.startsWith("#") && trimmed.matches("^[A-Za-z].*\\(.*\\)$")) {
                if (!seenResource) seenResource = true;
                if (!resourcesInserted) {
                    // Insert all non-deleted resources at position of first resource line
                    for (ResourceEntry re : resources) {
                        if (!re.isDeleted()) {
                            output.add(re.getLine());
                        }
                    }
                    resourcesInserted = true;
                }
                continue; // skip original resource line
            }
        }

        // Property line — apply changes
        if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("<")
                && !trimmed.contains("(") && trimmed.contains(":")) {
            int colonIdx = trimmed.indexOf(':');
            String key = trimmed.substring(0, colonIdx).trim();

            if (changes.containsKey(key)) {
                String indent = line.substring(0, line.indexOf(trimmed));
                output.add(indent + key + ": " + changes.get(key));
                changes.remove(key);
                continue;
            }
        }

        output.add(line);
    }

    // If no resource lines existed in file, append at end
    if (!resourcesInserted && resources != null && !resources.isEmpty()) {
        output.add("");
        for (ResourceEntry re : resources) {
            if (!re.isDeleted()) {
                output.add(re.getLine());
            }
        }
    }

    // Append any new property changes
    if (!changes.isEmpty()) {
        output.add("");
        output.add("# Added by OTG Editor");
        for (var entry : changes.entrySet()) {
            output.add(entry.getKey() + ": " + entry.getValue());
        }
    }

    try {
        Files.write(iniPath, output);
        LOG.info("Saved properties + {} resources to {}",
            resources.stream().filter(r -> !r.isDeleted()).count(), iniPath.getFileName());
        return true;
    } catch (IOException e) {
        LOG.error("Failed to write config file: {}", iniPath, e);
        return false;
    }
}
```

- [ ] **Step 2: Build and verify**
```bash
cd /var/home/jmc/IdeaProjects/OpenTerrainGenerator/.worktrees/1.21.1-in-game-editor && ./gradlew build
```

- [ ] **Step 3: Commit**
```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/data/ConfigWriter.java
git commit -m "feat(editor): add ConfigWriter.saveWithResources() for resource queue editing"
```

---

## Task 3: ResourceQueueScreen — Full-Screen Editor

**Files:**
- Create: `editor/screen/ResourceQueueScreen.java`

- [ ] **Step 1: Create ResourceQueueScreen**

A dedicated screen for editing resource queue entries. Shows a scrollable list of entries with:
- Each entry as an EditBox (full line editable as text)
- Function name prefix label (Ore, Tree, etc.) in color
- Delete button per entry
- Add button (appends blank entry)
- Move Up/Down buttons for reordering
- Done button (returns to BiomeEditorScreen with modified entries)

```java
package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.data.ResourceEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ResourceQueueScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int MARGIN = 10;

    private final Screen parent;
    private final List<ResourceEntry> entries;
    private final Consumer<List<ResourceEntry>> onSave;

    private int scrollOffset = 0;
    private List<EditBox> entryEditBoxes = new ArrayList<>();

    public ResourceQueueScreen(Screen parent, List<ResourceEntry> entries, Consumer<List<ResourceEntry>> onSave) {
        super(Component.literal("Resource Queue Editor"));
        this.parent = parent;
        this.entries = new ArrayList<>(entries); // work on a copy
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        entryEditBoxes.clear();

        int listY = 30;
        int listH = height - 70;
        int maxVisible = listH / ROW_HEIGHT;
        int editW = width - 100; // leave room for buttons

        // Clamp scroll
        int maxScroll = Math.max(0, entries.size() - maxVisible);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // Create EditBoxes for visible entries
        for (int i = 0; i < maxVisible && (i + scrollOffset) < entries.size(); i++) {
            int idx = i + scrollOffset;
            ResourceEntry entry = entries.get(idx);
            if (entry.isDeleted()) continue;

            int ey = listY + i * ROW_HEIGHT;

            EditBox eb = new EditBox(font, MARGIN, ey + 1, editW, ROW_HEIGHT - 3, Component.empty());
            eb.setMaxLength(500);
            eb.setValue(entry.getLine());
            final int entryIdx = idx;
            eb.setResponder(val -> entries.get(entryIdx).setLine(val));
            addRenderableWidget(eb);
            entryEditBoxes.add(eb);

            // Delete button
            addRenderableWidget(Button.builder(Component.literal("X"), btn -> {
                entries.get(entryIdx).setDeleted(true);
                rebuildWidgets();
            }).bounds(width - 85, ey, 18, ROW_HEIGHT - 3).build());

            // Move up
            if (idx > 0) {
                addRenderableWidget(Button.builder(Component.literal("\u25B2"), btn -> {
                    swap(entryIdx, entryIdx - 1);
                    rebuildWidgets();
                }).bounds(width - 63, ey, 18, ROW_HEIGHT - 3).build());
            }

            // Move down
            if (idx < entries.size() - 1) {
                addRenderableWidget(Button.builder(Component.literal("\u25BC"), btn -> {
                    swap(entryIdx, entryIdx + 1);
                    rebuildWidgets();
                }).bounds(width - 42, ey, 18, ROW_HEIGHT - 3).build());
            }
        }

        // Bottom buttons
        int btnY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Add Entry"), btn -> {
            entries.add(new ResourceEntry("CustomObject()"));
            rebuildWidgets();
        }).bounds(MARGIN, btnY, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            // Remove deleted entries, pass back to parent
            List<ResourceEntry> result = new ArrayList<>();
            for (ResourceEntry e : entries) {
                if (!e.isDeleted()) result.add(e);
            }
            onSave.accept(result);
            minecraft.setScreen(parent);
        }).bounds(width - 60, btnY, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            minecraft.setScreen(parent);
        }).bounds(width - 120, btnY, 50, 20).build());
    }

    private void swap(int a, int b) {
        ResourceEntry tmp = entries.get(a);
        entries.set(a, entries.get(b));
        entries.set(b, tmp);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
        graphics.drawString(font, entries.stream().filter(e -> !e.isDeleted()).count() + " entries", MARGIN, 20, 0xFF888888);

        // Function name labels (colored, left of EditBoxes)
        int listY = 30;
        int maxVisible = (height - 70) / ROW_HEIGHT;
        int visIdx = 0;
        for (int i = scrollOffset; i < entries.size() && visIdx < maxVisible; i++) {
            if (entries.get(i).isDeleted()) continue;
            int ey = listY + visIdx * ROW_HEIGHT;
            String funcName = entries.get(i).getFunctionName();
            int color = getFunctionColor(funcName);
            // Draw colored dot before EditBox
            graphics.fill(MARGIN - 6, ey + 4, MARGIN - 2, ey + ROW_HEIGHT - 6, color);
            visIdx++;
        }
    }

    private int getFunctionColor(String funcName) {
        return switch (funcName) {
            case "Ore", "UnderWaterOre" -> 0xFF8888CC;
            case "CustomObject" -> 0xFF88CC88;
            case "CustomStructure" -> 0xFFCC8888;
            case "Tree" -> 0xFF44AA44;
            case "Dungeon" -> 0xFFAAAA44;
            case "Grass", "Plant" -> 0xFF66CC66;
            case "SmallLake", "UnderGroundLake" -> 0xFF4488CC;
            default -> 0xFF888888;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        int maxVisible = (height - 70) / ROW_HEIGHT;
        int maxScroll = Math.max(0, (int) entries.stream().filter(e -> !e.isDeleted()).count() - maxVisible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) deltaV));
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
```

- [ ] **Step 2: Build and verify**
```bash
cd /var/home/jmc/IdeaProjects/OpenTerrainGenerator/.worktrees/1.21.1-in-game-editor && ./gradlew build
```

- [ ] **Step 3: Commit**
```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/ResourceQueueScreen.java
git commit -m "feat(editor): add ResourceQueueScreen — full-screen resource queue editor"
```

---

## Task 4: BiomeEditorScreen — Wire Up Resource Queue Editing

**Files:**
- Modify: `editor/screen/BiomeEditorScreen.java`

- [ ] **Step 1: Convert resourceQueueLines to ResourceEntry list**

Change field type from `List<String>` to `List<ResourceEntry>`:
```java
// Change:
private List<String> resourceQueueLines = List.of();
// To:
private List<ResourceEntry> resourceEntries = List.of();
```

In `loadBiome()`, convert strings to ResourceEntry:
```java
// Change:
resourceQueueLines = result.resourceQueueLines();
// To:
resourceEntries = result.resourceQueueLines().stream()
    .map(ResourceEntry::new)
    .collect(java.util.stream.Collectors.toList());
```

- [ ] **Step 2: Replace read-only display with "Edit Resources" button**

In `init()`, replace the "Browse BO3" button area or add alongside. Add an "Edit Resources (N)" button:
```java
addRenderableWidget(Button.builder(
    Component.literal("Resources (" + resourceEntries.stream().filter(e -> !e.isDeleted()).count() + ")"),
    btn -> {
        if (!resourceEntries.isEmpty() || selectedBiomeIndex >= 0) {
            minecraft.setScreen(new ResourceQueueScreen(this, resourceEntries, updated -> {
                resourceEntries = updated;
                // Mark as needing save
            }));
        }
    }
).bounds(LEFT_PANEL_WIDTH + 200, btnBarY, 90, 20).build());
```

- [ ] **Step 3: Update save() to include resources**

Change the save call from `ConfigWriter.save()` to `ConfigWriter.saveWithResources()`:
```java
// Change:
boolean success = ConfigWriter.save(currentBiomePath, rawLines, toWrite);
// To:
boolean success = ConfigWriter.saveWithResources(currentBiomePath, rawLines, toWrite, resourceEntries);
```

- [ ] **Step 4: Remove old read-only resource queue display from render()**

Remove the block in `render()` that draws `resourceQueueLines` as text (lines with `"Resources ("` header, scissor, scroll). Replace with a simpler status line:
```java
// In render(), replace the resource queue read-only block with:
if (!resourceEntries.isEmpty() && selectedBiomeIndex >= 0) {
    long count = resourceEntries.stream().filter(e -> !e.isDeleted()).count();
    graphics.drawString(font, "Resources: " + count + " entries",
        LEFT_PANEL_WIDTH + 4, height - 88, 0xFF888888);
}
```

Also remove the `resourceQueueScroll` field and the scroll handling for resources in `mouseScrolled()`.

- [ ] **Step 5: Increase property grid height back**

The grid was shrunk to `height - 120` to make room for read-only resource display. Restore to `height - 70`:
```java
// Change:
int gridH = height - 120;
// To:
int gridH = height - 70;
```

- [ ] **Step 6: Build and verify**
```bash
cd /var/home/jmc/IdeaProjects/OpenTerrainGenerator/.worktrees/1.21.1-in-game-editor && ./gradlew build
```

- [ ] **Step 7: Deploy and test**
```bash
rm /var/home/jmc/Games/minecraft/active_mc/mods/otg-*.jar
cp build/distributions/otg-neoforge-*.jar /var/home/jmc/Games/minecraft/active_mc/mods/
```

Test flow:
1. Biome Editor → select biome → click "Resources (N)"
2. ResourceQueueScreen opens with all entries as editable text fields
3. Edit an Ore line — change frequency
4. Click X to delete an entry
5. Click ▲/▼ to reorder
6. Click "Add Entry" — new blank CustomObject() added
7. Click Done → return to BiomeEditorScreen
8. Save → verify .bc file has modified resource queue
9. Reopen biome → verify changes persist

- [ ] **Step 8: Commit**
```bash
git add platforms/shared/src/client/java/com/pg85/otg/client/editor/screen/BiomeEditorScreen.java
git commit -m "feat(editor): wire ResourceQueueScreen into BiomeEditorScreen save flow"
```

---

## Task 5: Changelog + Final

- [ ] **Step 1: Update changelog**

Add to `docs/CHANGELOG.md` under 0.5.0-dev1:
```markdown
**2026-04-02 — Resource queue editing**

- **ResourceQueueScreen**: Full-screen editor for resource queue entries (Ore, Tree, CustomObject, etc.) — inline text editing, add/delete/reorder
- **ResourceEntry**: Mutable wrapper for ConfigFunction lines with dirty/deleted tracking
- **ConfigWriter.saveWithResources()**: Saves properties + resource queue entries to .bc files, replacing original resource lines in-place
- **BiomeEditorScreen**: "Resources (N)" button opens ResourceQueueScreen, save writes modified resources
```

- [ ] **Step 2: Commit**
```bash
git add docs/CHANGELOG.md
git commit -m "docs: add resource queue editing changelog entry"
```
