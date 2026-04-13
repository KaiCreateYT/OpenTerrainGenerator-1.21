package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.data.WorldPresetFileScanner;
import com.pg85.otg.client.editor.data.WorldPresetTemplates;
import com.pg85.otg.client.editor.data.WorldPresetYamlIO;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.shared.registry.WorldPresetRegistrar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 4-step wizard to create a new WorldPreset YAML.
 *  Step 0: Template (Blank or shipped YAML)
 *  Step 1: Metadata (DisplayName, Description, ModpackName)
 *  Step 2: Dimensions placeholder (edit in editor after Finish)
 *  Step 3: Confirm + Finish
 */
public class WorldPresetWizardScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetWizardScreen.class);
    private static final String[] STEP_TITLES = {
        "Step 1/4: Choose Template",
        "Step 2/4: Metadata",
        "Step 3/4: Dimensions",
        "Step 4/4: Confirm"
    };

    private final Screen parent;
    private final List<WorldPresetTemplates.Template> templates;

    private int step = 0;
    private int selectedTemplateIdx = 0;

    private String displayName = "";
    private String description = "";
    private String modpackName = "";

    private WorldPresetConfig staged;

    // Cached duplicate-check result — invalidated when displayName changes.
    private String lastCheckedName = null;
    private boolean lastCheckedResult = false;

    public WorldPresetWizardScreen(Screen parent) {
        super(Component.literal("New WorldPreset"));
        this.parent = parent;
        this.templates = WorldPresetTemplates.listTemplates();
    }

    @Override
    protected void init() {
        int cx = width / 2;

        addRenderableWidget(Button.builder(Component.literal("Cancel"),
            b -> minecraft.setScreen(parent)
        ).bounds(10, height - 30, 70, 20).build());

        if (step > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Back"),
                b -> { step--; rebuildWidgets(); }
            ).bounds(cx - 100, height - 30, 90, 20).build());
        }

        if (step < 3) {
            addRenderableWidget(Button.builder(Component.literal("Next >"),
                b -> { if (validateStep()) { step++; rebuildWidgets(); } }
            ).bounds(cx + 10, height - 30, 90, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Finish"),
                b -> finishWizard()
            ).bounds(cx + 10, height - 30, 90, 20).build());
        }

        switch (step) {
            case 0 -> buildStep0();
            case 1 -> buildStep1();
            case 2 -> { /* no widgets — placeholder text only */ }
            case 3 -> { /* summary only — no widgets */ }
        }
    }

    private void buildStep0() {
        int x = 40;
        int y = 56;
        for (int i = 0; i < templates.size(); i++) {
            final int idx = i;
            boolean selected = idx == selectedTemplateIdx;
            Button b = Button.builder(
                Component.literal((selected ? "(●) " : "( ) ") + templates.get(idx).label()),
                btn -> { selectedTemplateIdx = idx; rebuildWidgets(); }
            ).bounds(x, y, 280, 20).build();
            addRenderableWidget(b);
            y += 24;
        }
    }

    private void buildStep1() {
        int x = 40;
        int y = 56;
        int lw = 110;
        int fw = Math.min(260, width - 80 - lw);

        EditBox dn = new EditBox(font, x + lw, y, fw, 18, Component.empty());
        dn.setMaxLength(128);
        dn.setValue(displayName);
        dn.setResponder(v -> displayName = v);
        addRenderableWidget(dn);

        EditBox desc = new EditBox(font, x + lw, y + 24, fw, 18, Component.empty());
        desc.setMaxLength(512);
        desc.setValue(description);
        desc.setResponder(v -> description = v);
        addRenderableWidget(desc);

        EditBox mp = new EditBox(font, x + lw, y + 48, fw, 18, Component.empty());
        mp.setMaxLength(128);
        mp.setValue(modpackName);
        mp.setResponder(v -> modpackName = v);
        addRenderableWidget(mp);
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
        if (name == null) return false;
        // Cache: re-scan only when the name changes (avoid per-frame file I/O).
        if (name.equals(lastCheckedName)) return lastCheckedResult;
        Path root = OTG.getEngine().getOTGRootFolder();
        var entries = WorldPresetFileScanner.scan(root);
        boolean result = false;
        for (var e : entries) {
            if (e.config() != null && name.equalsIgnoreCase(e.config().DisplayName)) {
                result = true;
                break;
            }
        }
        lastCheckedName = name;
        lastCheckedResult = result;
        return result;
    }

    private void finishWizard() {
        if (staged == null) staged = WorldPresetTemplates.blank();
        staged.DisplayName = displayName;
        staged.Description = description == null || description.isBlank() ? null : description;
        staged.ModpackName = modpackName == null || modpackName.isBlank() ? null : modpackName;

        Path root = OTG.getEngine().getOTGRootFolder();
        Path dir = root.resolve(Constants.WORLD_PRESETS_FOLDER);
        String base = WorldPresetRegistrar.normalizeId(displayName);
        if (base == null || base.isEmpty()) base = "worldpreset";
        Path filename = dir.resolve(base + ".yaml");
        int counter = 1;
        while (Files.exists(filename)) {
            filename = dir.resolve(base + "_" + counter + ".yaml");
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

        g.drawCenteredString(font, STEP_TITLES[step], width / 2, 20, 0xFFFFFF);

        if (step == 1) {
            int x = 40; int y = 56;
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
            int x = 40; int y = 56;
            g.drawString(font, "Template:    " + templates.get(selectedTemplateIdx).label(), x, y, 0xFFCCCCCC);
            y += 14;
            g.drawString(font, "DisplayName: " + displayName, x, y, 0xFFCCCCCC);
            y += 14;
            g.drawString(font, "Description: " + (description == null ? "" : description), x, y, 0xFFCCCCCC);
            y += 14;
            g.drawString(font, "Modpack:     " + (modpackName == null ? "" : modpackName), x, y, 0xFFCCCCCC);
            y += 20;
            String base = WorldPresetRegistrar.normalizeId(displayName);
            g.drawString(font, "Filename:    " + (base == null ? "" : base) + ".yaml", x, y, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
