package com.pg85.otg.client.preview;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.widget.Viewport3DRenderer;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PreviewScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewScreen.class);
    private static final int PANEL_WIDTH = 150;

    // Persisted across screen recreations via static
    private static long seed = 12345L;
    private static int radiusChunks = 4;
    private static ChunkStatus chunkStatus = ChunkStatus.FULL;
    private static String selectedPresetDisplay = "";
    private static String boObjectName = "";
    // DimensionPreset folder name from the selected WorldPreset's Overworld
    private static String dimensionPresetName = "";

    private EditBox seedInput;
    private EditBox boInput;
    private int viewportX, viewportY, viewportW, viewportH;

    // Available WorldPreset entries (populated in init)
    private record PresetEntry(String displayName, String resourceId, String overworldPresetFolder) {}
    private List<PresetEntry> presetEntries = new ArrayList<>();
    private int presetIndex = 0;

    private final Screen parentScreen;
    private final String initialPresetFolder;

    public PreviewScreen() {
        this(null, null);
    }

    public PreviewScreen(Screen parent, String presetFolderName) {
        super(Component.literal("OTG Editor — Preview"));
        this.parentScreen = parent;
        this.initialPresetFolder = presetFolderName;
    }

    @Override
    protected void init() {
        // Load available DimensionPresets from engine.
        // Each DimensionPreset is registered as MC WorldPreset at otg:<registryName>
        presetEntries = new ArrayList<>();
        try {
            var engine = OTG.getEngine();
            if (engine != null) {
                for (DimensionPreset preset : engine.getDimensionPresetLoader().getAllDimensionPresets()) {
                    String displayName = preset.getConfig().getPresetInfo().getDisplayName();
                    if (displayName == null || displayName.isBlank()) displayName = preset.getFolderName();
                    // Must match OTGRegistryHelper.registerWorldPresets() registration key
                    String resourceId = Constants.MOD_ID_SHORT + ":" + preset.getRegistryName().toLowerCase(Locale.ROOT);
                    presetEntries.add(new PresetEntry(displayName, resourceId, preset.getFolderName()));
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to load DimensionPresets for preview", e);
        }

        if (presetEntries.isEmpty()) {
            presetEntries.add(new PresetEntry("Vanilla", "", ""));
        }

        // Restore preset selection — prefer initialPresetFolder if provided
        presetIndex = 0;
        if (initialPresetFolder != null && !initialPresetFolder.isEmpty()) {
            for (int i = 0; i < presetEntries.size(); i++) {
                if (presetEntries.get(i).overworldPresetFolder.equals(initialPresetFolder)) {
                    presetIndex = i;
                    break;
                }
            }
        } else {
            for (int i = 0; i < presetEntries.size(); i++) {
                if (presetEntries.get(i).displayName.equals(selectedPresetDisplay)) {
                    presetIndex = i;
                    break;
                }
            }
        }
        selectedPresetDisplay = presetEntries.get(presetIndex).displayName;
        dimensionPresetName = presetEntries.get(presetIndex).overworldPresetFolder;

        int panelX = 10;
        int y = 35;

        // --- TERRAIN SECTION ---

        // Preset selector button — cycles through available WorldPresets
        addRenderableWidget(Button.builder(
            Component.literal("Preset: " + selectedPresetDisplay),
            btn -> {
                presetIndex = (presetIndex + 1) % presetEntries.size();
                PresetEntry entry = presetEntries.get(presetIndex);
                selectedPresetDisplay = entry.displayName;
                dimensionPresetName = entry.overworldPresetFolder;
                rebuildWidgets();
            }
        ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
        y += 24;

        // Seed input
        seedInput = new EditBox(font, panelX, y, PANEL_WIDTH - 20, 20, Component.literal("Seed"));
        seedInput.setValue(String.valueOf(seed));
        seedInput.setResponder(s -> {
            try { seed = Long.parseLong(s); } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(seedInput);
        y += 24;

        // Size buttons — selected one is highlighted with brackets
        int btnX = panelX;
        for (int size : new int[]{4, 8, 16, 32}) {
            final int r = size / 2;
            boolean selected = radiusChunks == r;
            String label = selected ? "[" + size + "]" : size + "x" + size;
            addRenderableWidget(Button.builder(
                Component.literal(label),
                btn -> { radiusChunks = r; rebuildWidgets(); }
            ).bounds(btnX, y, 30, 20).build());
            btnX += 33;
        }
        y += 24;

        // Generation level buttons — selected one is highlighted
        record GenLevel(String label, ChunkStatus status) {}
        for (var level : new GenLevel[]{
                new GenLevel("Surface", ChunkStatus.SURFACE),
                new GenLevel("Caves", ChunkStatus.CARVERS),
                new GenLevel("Full", ChunkStatus.FULL)
        }) {
            boolean selected = chunkStatus == level.status;
            String label = selected ? "> " + level.label : "  " + level.label;
            addRenderableWidget(Button.builder(
                Component.literal(label),
                btn -> { chunkStatus = level.status; rebuildWidgets(); }
            ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
            y += 22;
        }
        y += 4;

        // Generate button
        addRenderableWidget(Button.builder(
            Component.literal("Generate Preview"),
            btn -> {
                if (PreviewState.getPhase() == PreviewState.Phase.IDLE
                        || PreviewState.getPhase() == PreviewState.Phase.DONE) {
                    PreviewState.startGeneration(
                        presetEntries.get(presetIndex).resourceId,
                        seed, radiusChunks, chunkStatus);
                }
            }
        ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
        y += 28;

        // --- BO SECTION ---

        // BO object name input
        boInput = new EditBox(font, panelX, y, PANEL_WIDTH - 20, 20, Component.literal("BO Name"));
        boInput.setValue(boObjectName);
        boInput.setResponder(s -> boObjectName = s);
        boInput.setHint(Component.literal("BO3/BO4 name"));
        addRenderableWidget(boInput);
        y += 24;

        // Load BO button
        addRenderableWidget(Button.builder(
            Component.literal("Load BO"),
            btn -> {
                if (!boObjectName.isEmpty() &&
                    (PreviewState.getPhase() == PreviewState.Phase.IDLE
                        || PreviewState.getPhase() == PreviewState.Phase.DONE)) {
                    PreviewState.loadBO(boObjectName, dimensionPresetName);
                    rebuildWidgets();
                }
            }
        ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
        y += 28;

        // --- CONTROLS ---

        // Clear button (only when we have data)
        if (PreviewState.getPhase() == PreviewState.Phase.DONE) {
            addRenderableWidget(Button.builder(
                Component.literal("Clear"),
                btn -> {
                    PreviewState.reset();
                    rebuildWidgets();
                }
            ).bounds(panelX, y, PANEL_WIDTH - 20, 20).build());
            y += 28;
        }

        // Back button
        addRenderableWidget(Button.builder(
            Component.literal("Back"),
            btn -> onClose()
        ).bounds(panelX, height - 30, PANEL_WIDTH - 20, 20).build());

        // Viewport bounds
        viewportX = PANEL_WIDTH;
        viewportY = 0;
        viewportW = width - PANEL_WIDTH;
        viewportH = height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Opaque background to hide spectator world rendering behind the screen
        graphics.fill(0, 0, width, height, 0xFF000000);
        // Widgets on top of black background
        super.render(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);

        // Status text
        graphics.drawString(font, PreviewState.getStatusText(), 10, height - 50, 0xAAAAAA);

        // Selected params
        graphics.drawString(font, radiusChunks * 2 + "x" + radiusChunks * 2 + " chunks", 10, 22, 0x888888);

        // 3D viewport
        if (PreviewState.getPhase() == PreviewState.Phase.DONE && !PreviewState.getRenderer().isEmpty()) {
            renderViewport(graphics, partialTick);
        } else {
            // Empty viewport placeholder
            graphics.fill(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH, 0xFF1A1A1A);
            if (PreviewState.getPhase() == PreviewState.Phase.IDLE) {
                graphics.drawCenteredString(font, "Click 'Generate Preview' or 'Load BO' to start",
                    viewportX + viewportW / 2, viewportY + viewportH / 2, 0x666666);
            } else if (PreviewState.getPhase() != PreviewState.Phase.DONE) {
                String progress = PreviewState.getStatusText();
                int centerX = viewportX + viewportW / 2;
                int centerY = viewportY + viewportH / 2;
                graphics.drawCenteredString(font, progress, centerX, centerY - 10, 0x888888);

                if (PreviewState.getPhase() == PreviewState.Phase.GENERATING_CHUNKS) {
                    int barW = Math.min(viewportW - 40, 200);
                    int barH = 6;
                    int barX = centerX - barW / 2;
                    int barY = centerY + 6;
                    float pct = PreviewState.getProgress();
                    graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
                    graphics.fill(barX, barY, barX + (int)(barW * pct), barY + barH, 0xFF44AA44);
                }
            }
        }
    }

    private void renderViewport(GuiGraphics graphics, float partialTick) {
        Viewport3DRenderer.render(graphics, viewportX, viewportY, viewportW, viewportH,
            PreviewState.getCamera(), PreviewState.getRenderer());
    }

    // --- Input handling ---

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                 double deltaX, double deltaY) {
        if (isInViewport(mouseX, mouseY)) {
            PreviewState.getCamera().rotate((float) (-deltaX * 0.01), (float) (deltaY * 0.01));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  double deltaH, double deltaV) {
        if (isInViewport(mouseX, mouseY)) {
            PreviewState.getCamera().zoom((float) (deltaV * 120));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    private boolean isInViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX < viewportX + viewportW
            && mouseY >= viewportY && mouseY < viewportY + viewportH;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Don't allow ESC to close during generation — use Back button or wait
        PreviewState.Phase ph = PreviewState.getPhase();
        return ph == PreviewState.Phase.IDLE || ph == PreviewState.Phase.DONE;
    }

    @Override
    public void onClose() {
        // Set return destination before close
        PreviewState.setReturnScreen(parentScreen);
        // Don't disconnect here — mc.disconnect() causes black screen when called
        // from any screen event context. Instead, request close via tick-delayed
        // mechanism: tick 1 closes PreviewScreen, tick 2+ disconnects cleanly.
        PreviewState.requestClose();
    }
}
