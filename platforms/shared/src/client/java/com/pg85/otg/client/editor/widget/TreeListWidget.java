package com.pg85.otg.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;
import java.util.function.Consumer;

/**
 * Collapsible tree list widget. Folders can be expanded/collapsed.
 * Files are leaf nodes. Indentation shows hierarchy depth.
 */
public class TreeListWidget {

    private static final int INDENT = 10;
    private static final String EXPANDED = "\u25BC "; // ▼
    private static final String COLLAPSED = "\u25B6 "; // ▶

    private final int x, y, width, height;
    private final int itemHeight;

    private final List<TreeNode> allNodes = new ArrayList<>();
    private List<TreeNode> visibleNodes = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private Consumer<TreeNode> onSelect;

    public record TreeNode(String name, String fullPath, int depth, boolean isFolder, List<TreeNode> children) {
        private static final List<TreeNode> NO_CHILDREN = List.of();

        public static TreeNode folder(String name, String fullPath, int depth) {
            return new TreeNode(name, fullPath, depth, true, new ArrayList<>());
        }

        public static TreeNode file(String name, String fullPath, int depth) {
            return new TreeNode(name, fullPath, depth, false, NO_CHILDREN);
        }
    }

    // Track expanded folders by path
    private final Set<String> expandedPaths = new HashSet<>();

    public TreeListWidget(int x, int y, int width, int height, int itemHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.itemHeight = itemHeight;
    }

    public void setOnSelect(Consumer<TreeNode> onSelect) { this.onSelect = onSelect; }
    public int getSelectedIndex() { return selectedIndex; }

    public TreeNode getSelectedNode() {
        return (selectedIndex >= 0 && selectedIndex < visibleNodes.size())
            ? visibleNodes.get(selectedIndex) : null;
    }

    /**
     * Build tree from a list of relative paths (e.g. "Trees/Oak/BigOak1.bo3").
     * Automatically creates folder nodes for intermediate directories.
     */
    public void buildFromPaths(List<String> relativePaths) {
        allNodes.clear();
        expandedPaths.clear();

        // Build a tree structure from paths
        Map<String, TreeNode> folderMap = new LinkedHashMap<>();
        TreeNode root = TreeNode.folder("", "", -1);
        folderMap.put("", root);

        for (String path : relativePaths) {
            String[] parts = path.replace('\\', '/').split("/");
            StringBuilder currentPath = new StringBuilder();

            TreeNode parent = root;
            for (int i = 0; i < parts.length - 1; i++) {
                if (!currentPath.isEmpty()) currentPath.append("/");
                currentPath.append(parts[i]);
                String folderPath = currentPath.toString();

                TreeNode folder = folderMap.get(folderPath);
                if (folder == null) {
                    folder = TreeNode.folder(parts[i], folderPath, i);
                    folderMap.put(folderPath, folder);
                    parent.children().add(folder);
                }
                parent = folder;
            }

            // Add the file as leaf
            String fileName = parts[parts.length - 1];
            parent.children().add(TreeNode.file(fileName, path, parts.length - 1));
        }

        // Sort children: folders first, then files, both alphabetically
        sortChildren(root);
        allNodes.addAll(root.children());

        rebuildVisible();
    }

    private void sortChildren(TreeNode node) {
        if (!node.isFolder() || node.children().isEmpty()) return;
        node.children().sort((a, b) -> {
            if (a.isFolder() != b.isFolder()) return a.isFolder() ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        for (TreeNode child : node.children()) sortChildren(child);
    }

    /**
     * Filter tree by search text. Matches file names (not folders).
     * Expands folders that contain matching files.
     */
    public void filter(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            rebuildVisible();
            return;
        }

        String lower = searchText.toLowerCase();
        visibleNodes = new ArrayList<>();
        for (TreeNode node : allNodes) {
            collectMatching(node, lower, visibleNodes);
        }
        scrollOffset = 0;
        selectedIndex = -1;
    }

    private boolean collectMatching(TreeNode node, String filter, List<TreeNode> out) {
        if (node.isFolder()) {
            List<TreeNode> matchingChildren = new ArrayList<>();
            boolean hasMatch = false;
            for (TreeNode child : node.children()) {
                int before = matchingChildren.size();
                if (collectMatching(child, filter, matchingChildren)) {
                    hasMatch = true;
                }
            }
            if (hasMatch) {
                out.add(node);
                out.addAll(matchingChildren);
            }
            return hasMatch;
        } else {
            if (node.name().toLowerCase().contains(filter)) {
                out.add(node);
                return true;
            }
            return false;
        }
    }

    private void rebuildVisible() {
        visibleNodes = new ArrayList<>();
        for (TreeNode node : allNodes) {
            addVisible(node);
        }
        scrollOffset = 0;
    }

    private void addVisible(TreeNode node) {
        visibleNodes.add(node);
        if (node.isFolder() && expandedPaths.contains(node.fullPath())) {
            for (TreeNode child : node.children()) {
                addVisible(child);
            }
        }
    }

    public int getTotalFileCount() {
        return countFiles(allNodes);
    }

    private int countFiles(List<TreeNode> nodes) {
        int count = 0;
        for (TreeNode n : nodes) {
            if (n.isFolder()) count += countFiles(n.children());
            else count++;
        }
        return count;
    }

    // --- Rendering ---

    public void render(GuiGraphics graphics) {
        if (visibleNodes.isEmpty()) return;
        Font font = Minecraft.getInstance().font;

        graphics.fill(x, y, x + width, y + height, 0xFF1E1E1E);

        int maxVisible = height / itemHeight;
        int maxScroll = Math.max(0, visibleNodes.size() - maxVisible);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        graphics.enableScissor(x, y, x + width, y + height);
        for (int i = 0; i < maxVisible && (i + scrollOffset) < visibleNodes.size(); i++) {
            int idx = i + scrollOffset;
            TreeNode node = visibleNodes.get(idx);
            int iy = y + i * itemHeight;

            // Selection highlight
            if (idx == selectedIndex) {
                graphics.fill(x, iy, x + width, iy + itemHeight, 0xFF2A3A4A);
                graphics.fill(x, iy, x + 3, iy + itemHeight, 0xFF4A8AFF);
            }

            int indent = x + 4 + node.depth() * INDENT;
            int textColor = idx == selectedIndex ? 0xFFFFFFFF : 0xFFAAAAAA;

            if (node.isFolder()) {
                boolean expanded = expandedPaths.contains(node.fullPath());
                String prefix = expanded ? EXPANDED : COLLAPSED;
                int folderColor = idx == selectedIndex ? 0xFFFFCC44 : 0xFFCCAA33;
                graphics.drawString(font, prefix + node.name(), indent, iy + (itemHeight - 8) / 2, folderColor);
            } else {
                graphics.drawString(font, node.name(), indent, iy + (itemHeight - 8) / 2, textColor);
            }
        }
        graphics.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;

        int clicked = (int) ((mouseY - y) / itemHeight) + scrollOffset;
        if (clicked < 0 || clicked >= visibleNodes.size()) return false;

        TreeNode node = visibleNodes.get(clicked);

        if (node.isFolder()) {
            // Toggle expand/collapse
            if (expandedPaths.contains(node.fullPath())) {
                expandedPaths.remove(node.fullPath());
            } else {
                expandedPaths.add(node.fullPath());
            }
            rebuildVisible();
            // Keep selection stable
            if (selectedIndex >= 0 && selectedIndex < visibleNodes.size()) {
                // Selection may have shifted — don't update
            }
            return true;
        } else {
            selectedIndex = clicked;
            if (onSelect != null) onSelect.accept(node);
            return true;
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        int maxVisible = height / itemHeight;
        int maxScroll = Math.max(0, visibleNodes.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
        return true;
    }
}
