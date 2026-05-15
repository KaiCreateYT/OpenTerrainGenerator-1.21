package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.data.DimensionPresetOperations;
import com.pg85.otg.client.editor.data.DimensionPresetTemplates;
import com.pg85.otg.client.editor.data.PresetReloader;
import com.pg85.otg.presets.DimensionPreset;
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
import java.util.Locale;

/**
 * 4-step wizard to create a new DimensionPreset folder.
 *  Step 1: Template (Blank Minimal or copy of an existing preset)
 *  Step 2: Metadata (DisplayName / FolderName / RegistryName / Author / Description)
 *  Step 3: Info — biomes copied from template, editable afterwards
 *  Step 4: Confirm + Finish
 */
public class DimensionPresetWizardScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(DimensionPresetWizardScreen.class);
    private static final String[] STEP_TITLES = {
        "Step 1/4: Choose Template",
        "Step 2/4: Metadata",
        "Step 3/4: Biomes",
        "Step 4/4: Confirm"
    };

    private final Screen parent;
    private final List<DimensionPresetTemplates.Template> templates;

    private int step = 0;
    private int selectedTemplateIdx = 0;

    private String displayName = "";
    private String folderName = "";
    private String registryName = "";
    private String author = "";
    private String description = "";

    private boolean folderNameEdited = false;
    private boolean registryNameEdited = false;

    public DimensionPresetWizardScreen(Screen parent) {
        super(Component.literal("New DimensionPreset"));
        this.parent = parent;
        this.templates = DimensionPresetTemplates.listTemplates();
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
            case 2, 3 -> { /* render-only */ }
        }
    }

    private void buildStep0() {
        if (templates.isEmpty()) return;
        int x = 40;
        int y = 56;
        for (int i = 0; i < templates.size(); i++) {
            final int idx = i;
            boolean selected = idx == selectedTemplateIdx;
            Button b = Button.builder(
                Component.literal((selected ? "(●) " : "(  ) ") + templates.get(idx).label()),
                btn -> { selectedTemplateIdx = idx; rebuildWidgets(); }
            ).bounds(x, y, 320, 20).build();
            addRenderableWidget(b);
            y += 24;
            if (y > height - 60) break;
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
        dn.setResponder(v -> {
            displayName = v;
            if (!folderNameEdited) {
                folderName = DimensionPresetOperations.suggestFolderName(v);
                rebuildWidgets();
            }
            if (!registryNameEdited) {
                registryName = WorldPresetRegistrar.normalizeId(v == null ? "" : v.toLowerCase(Locale.ROOT));
                if (registryName == null) registryName = "";
                rebuildWidgets();
            }
        });
        addRenderableWidget(dn);

        EditBox fn = new EditBox(font, x + lw, y + 24, fw, 18, Component.empty());
        fn.setMaxLength(64);
        fn.setValue(folderName);
        fn.setResponder(v -> {
            folderName = v;
            folderNameEdited = !v.isBlank();
        });
        addRenderableWidget(fn);

        EditBox rn = new EditBox(font, x + lw, y + 48, fw, 18, Component.empty());
        rn.setMaxLength(64);
        rn.setValue(registryName);
        rn.setResponder(v -> {
            registryName = v;
            registryNameEdited = !v.isBlank();
        });
        addRenderableWidget(rn);

        EditBox au = new EditBox(font, x + lw, y + 72, fw, 18, Component.empty());
        au.setMaxLength(64);
        au.setValue(author);
        au.setResponder(v -> author = v);
        addRenderableWidget(au);

        EditBox desc = new EditBox(font, x + lw, y + 96, fw, 18, Component.empty());
        desc.setMaxLength(256);
        desc.setValue(description);
        desc.setResponder(v -> description = v);
        addRenderableWidget(desc);
    }

    private boolean validateStep() {
        if (step == 0) {
            return !templates.isEmpty();
        }
        if (step == 1) {
            if (displayName == null || displayName.isBlank()) return false;
            if (folderName == null || folderName.isBlank()) return false;
            if (isDuplicateFolderName(folderName)) return false;
            if (isDuplicateDisplayName(displayName)) return false;
            return true;
        }
        return true;
    }

    private boolean isDuplicateFolderName(String name) {
        if (name == null || name.isBlank()) return false;
        Path target = OTG.getEngine().getOTGRootFolder()
            .resolve(com.pg85.otg.constants.Constants.DIMENSION_PRESETS_FOLDER)
            .resolve(name);
        return Files.exists(target);
    }

    private boolean isDuplicateDisplayName(String name) {
        if (name == null || name.isBlank()) return false;
        for (DimensionPreset p : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
            String existing = p.getConfig().getPresetInfo().getDisplayName();
            if (existing != null && name.equalsIgnoreCase(existing)) return true;
        }
        return false;
    }

    private void finishWizard() {
        if (templates.isEmpty()) {
            LOG.error("No templates available — DefaultPreset missing?");
            minecraft.setScreen(parent);
            return;
        }
        Path templateDir = templates.get(selectedTemplateIdx).sourceDir();
        Path otgRoot = OTG.getEngine().getOTGRootFolder();

        Path result = DimensionPresetOperations.newFromTemplate(
            otgRoot, templateDir,
            folderName,
            displayName,
            registryName.isBlank() ? folderName.toLowerCase(Locale.ROOT) : registryName,
            author,
            description
        );

        if (result == null) {
            LOG.error("Wizard failed to create DimensionPreset");
            minecraft.setScreen(parent);
            return;
        }

        PresetReloader.reload();

        DimensionPreset created = findPresetByFolderName(result.getFileName().toString());
        if (created != null) {
            minecraft.setScreen(new WorldSettingsScreen(created, 0));
        } else {
            LOG.warn("Created DimensionPreset {} but failed to reload it — returning to parent",
                result.getFileName());
            minecraft.setScreen(parent);
        }
    }

    private DimensionPreset findPresetByFolderName(String folder) {
        for (DimensionPreset p : OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets()) {
            if (folder.equals(p.getFolderName())) return p;
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, STEP_TITLES[step], width / 2, 20, 0xFFFFFF);

        if (step == 0 && templates.isEmpty()) {
            g.drawCenteredString(font,
                "No templates found — DefaultPreset is missing from disk.",
                width / 2, 100, 0xFFFF6666);
        }

        if (step == 1) {
            int x = 40; int y = 56;
            g.drawString(font, "Display Name:",  x, y + 5,  0xFFAAAAAA);
            g.drawString(font, "Folder Name:",   x, y + 29, 0xFFAAAAAA);
            g.drawString(font, "Registry Name:", x, y + 53, 0xFFAAAAAA);
            g.drawString(font, "Author:",        x, y + 77, 0xFFAAAAAA);
            g.drawString(font, "Description:",   x, y + 101, 0xFFAAAAAA);

            int errY = y + 128;
            if (displayName == null || displayName.isBlank()) {
                g.drawString(font, "DisplayName is required", x, errY, 0xFFFF6666);
            } else if (folderName == null || folderName.isBlank()) {
                g.drawString(font, "FolderName is required", x, errY, 0xFFFF6666);
            } else if (isDuplicateFolderName(folderName)) {
                g.drawString(font, "FolderName already exists on disk", x, errY, 0xFFFF6666);
            } else if (isDuplicateDisplayName(displayName)) {
                g.drawString(font, "DisplayName already used by another preset", x, errY, 0xFFFF6666);
            }
        }

        if (step == 2) {
            String tplLabel = templates.isEmpty() ? "(none)" : templates.get(selectedTemplateIdx).label();
            int x = 40; int y = 56;
            g.drawString(font,
                "Biome configs (.bc files) will be copied from the template:",
                x, y, 0xFFCCCCCC);
            y += 14;
            g.drawString(font, "  " + tplLabel, x, y, 0xFFFFFFFF);
            y += 20;
            g.drawString(font, "You can add, remove, and edit biomes after Finish in", x, y, 0xFFAAAAAA);
            y += 12;
            g.drawString(font, "the Biome Editor.", x, y, 0xFFAAAAAA);
        }

        if (step == 3) {
            String tplLabel = templates.isEmpty() ? "(none)" : templates.get(selectedTemplateIdx).label();
            int x = 40; int y = 56;
            g.drawString(font, "Template:      " + tplLabel,       x, y, 0xFFCCCCCC); y += 14;
            g.drawString(font, "DisplayName:   " + displayName,    x, y, 0xFFCCCCCC); y += 14;
            g.drawString(font, "FolderName:    " + folderName,     x, y, 0xFFCCCCCC); y += 14;
            g.drawString(font, "RegistryName:  " + registryName,   x, y, 0xFFCCCCCC); y += 14;
            g.drawString(font, "Author:        " + nullSafe(author),      x, y, 0xFFCCCCCC); y += 14;
            g.drawString(font, "Description:   " + nullSafe(description), x, y, 0xFFCCCCCC); y += 20;
            g.drawString(font, "Folder will be created at:", x, y, 0xFFAAAAAA); y += 12;
            g.drawString(font, "  DimensionPresets/" + folderName + "/", x, y, 0xFFAAAAAA);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
