package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.data.DimensionPresetOperations;
import com.pg85.otg.client.editor.data.PresetReloader;
import com.pg85.otg.client.editor.widget.ScrollableListWidget;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists all DimensionPresets with summary + CRUD buttons.
 *
 * Differs from {@link ManageWorldPresetsScreen} in two ways:
 *  - "Edit" opens {@link WorldSettingsScreen} (preset edit happens there, not in a dedicated screen)
 *  - DefaultPreset is delete-disabled (it's the baseline shipped with the jar)
 *  - Delete warns if any WorldPreset YAML references the preset by PresetFolderName
 */
public class ManageDimensionPresetsScreen extends Screen {

    private final Screen parent;
    private List<DimensionPreset> presets = new ArrayList<>();
    private int selectedIdx = -1;
    private ScrollableListWidget listWidget;
    private String statusMessage;

    public ManageDimensionPresetsScreen(Screen parent) {
        super(Component.literal("Manage DimensionPresets"));
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
            minecraft.setScreen(new DimensionPresetWizardScreen(this))
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
        deleteBtn.active = canDelete();
        addRenderableWidget(deleteBtn);

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(width - 80, height - 30, 70, 20).build());
    }

    private void reloadEntries() {
        presets = new ArrayList<>(OTG.getEngine().getDimensionPresetLoader().getAllDimensionPresets());
        if (selectedIdx >= presets.size()) selectedIdx = -1;
    }

    private List<String> labels() {
        List<String> out = new ArrayList<>();
        for (var p : presets) {
            String display = p.getConfig().getPresetInfo().getDisplayName();
            if (display == null || display.isBlank()) display = p.getFolderName();
            out.add(display);
        }
        return out;
    }

    private boolean validSelected() {
        return selectedIdx >= 0 && selectedIdx < presets.size();
    }

    private boolean canDelete() {
        if (!validSelected()) return false;
        return !Constants.DEFAULT_PRESET_NAME.equals(presets.get(selectedIdx).getFolderName());
    }

    private void doEdit() {
        if (!validSelected()) return;
        minecraft.setScreen(new WorldSettingsScreen(presets.get(selectedIdx), 0));
    }

    private void doClone() {
        if (!validSelected()) return;
        var preset = presets.get(selectedIdx);
        String origDisplay = preset.getConfig().getPresetInfo().getDisplayName();
        if (origDisplay == null || origDisplay.isBlank()) origDisplay = preset.getFolderName();
        String newDisplay = origDisplay + " (copy)";

        Path source = preset.getFolder();
        Path otgRoot = OTG.getEngine().getOTGRootFolder();
        Path result = DimensionPresetOperations.cloneFrom(source, otgRoot, newDisplay);
        if (result != null) {
            statusMessage = "Cloned to " + result.getFileName();
            PresetReloader.reload();
            reloadEntries();
            rebuildWidgets();
        } else {
            statusMessage = "Clone failed";
        }
    }

    private void doDelete() {
        if (!canDelete()) return;
        var preset = presets.get(selectedIdx);
        String folderName = preset.getFolderName();

        Path otgRoot = OTG.getEngine().getOTGRootFolder();
        List<Path> referencingYamls = DimensionPresetOperations.findWorldPresetsReferencing(otgRoot, folderName);

        Component message;
        if (!referencingYamls.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(referencingYamls.size())
                .append(" WorldPreset YAML(s) reference this preset and will break if deleted:\n");
            for (int i = 0; i < Math.min(3, referencingYamls.size()); i++) {
                sb.append("- ").append(referencingYamls.get(i).getFileName()).append("\n");
            }
            if (referencingYamls.size() > 3) {
                sb.append("...and ").append(referencingYamls.size() - 3).append(" more\n");
            }
            sb.append("\nDelete ").append(folderName).append(" anyway?");
            message = Component.literal(sb.toString());
        } else {
            message = Component.literal("Delete DimensionPreset \"" + folderName + "\"? This removes the entire folder.");
        }

        minecraft.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    if (DimensionPresetOperations.delete(preset.getFolder())) {
                        statusMessage = "Deleted " + folderName;
                        selectedIdx = -1;
                        PresetReloader.reload();
                    } else {
                        statusMessage = "Delete failed";
                    }
                }
                minecraft.setScreen(this);
            },
            Component.literal("Delete DimensionPreset"),
            message,
            Component.literal("Delete"),
            Component.literal("Cancel")
        ));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        if (listWidget != null) listWidget.render(g);

        if (validSelected()) {
            var preset = presets.get(selectedIdx);
            var info = preset.getConfig().getPresetInfo();
            int sx = 270;
            int sy = 40;
            g.drawString(font, "Folder:       " + preset.getFolderName(), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "DisplayName:  " + nullSafe(info.getDisplayName()), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "RegistryName: " + nullSafe(info.getRegistryName()), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "Author:       " + nullSafe(info.getAuthor()), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "Description:  " + nullSafe(info.getDescription()), sx, sy, 0xFFCCCCCC);
            sy += 12;
            var biomeList = preset.getBiomeConfigList();
            int biomeCount = biomeList == null ? 0 : biomeList.size();
            g.drawString(font, "Biomes:       " + biomeCount, sx, sy, 0xFFCCCCCC);
            sy += 16;
            if (Constants.DEFAULT_PRESET_NAME.equals(preset.getFolderName())) {
                g.drawString(font, "(DefaultPreset — cannot be deleted)", sx, sy, 0xFFAAAA44);
            }
        }

        if (statusMessage != null) {
            g.drawString(font, statusMessage, 10, height - 12, 0xFFAAAA44);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
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
