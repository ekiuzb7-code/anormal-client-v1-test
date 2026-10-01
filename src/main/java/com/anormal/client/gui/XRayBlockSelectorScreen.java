package com.anormal.client.gui;

import com.anormal.client.module.impl.world.XRay;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class XRayBlockSelectorScreen extends Screen {
    private final Screen parent;
    private final XRay xray;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private int scrollOffset = 0;
    private final List<Block> allBlocks = new ArrayList<>();
    private List<Block> filteredBlocks = new ArrayList<>();

    public XRayBlockSelectorScreen(Screen parent, XRay xray) {
        super(Text.literal("XRay Block Selector"));
        this.parent = parent;
        this.xray = xray;
    }

    @Override
    protected void init() {
        allBlocks.clear();
        for (Block b : Registries.BLOCK) {
            allBlocks.add(b);
        }
        updateFilter();
    }

    private void updateFilter() {
        if (searchQuery.isEmpty()) {
            filteredBlocks = new ArrayList<>(allBlocks);
        } else {
            String query = searchQuery.toLowerCase();
            filteredBlocks = allBlocks.stream()
                    .filter(b -> Registries.BLOCK.getId(b).getPath().toLowerCase().contains(query) ||
                            b.getName().getString().toLowerCase().contains(query))
                    .toList();
        }
        scrollOffset = 0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int panelW = Math.min(560, width - 40);
        int panelH = Math.min(360, height - 40);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        ThemeManager.renderWindow(context, panelX, panelY, panelW, panelH, "XRay Block Selector");

        // Top Header
        String title = "§6XRAY BLOCK SELECTOR §7(" + xray.getSelectedBlocks().size() + " Selected)";
        RenderUtils.drawText(context, textRenderer, title, panelX + 12, panelY + 8, 0xFFFFFFFF, true);

        // Search Bar
        int searchW = 140;
        int searchX = panelX + panelW - searchW - 12;
        int searchY = panelY + 6;
        RenderUtils.fill(context, searchX, searchY, searchX + searchW, searchY + 14, ColorUtils.rgba(10, 10, 10, 200));
        RenderUtils.drawBorder(context, searchX, searchY, searchX + searchW, searchY + 14, 1, searchFocused ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());
        String searchDisplay = searchQuery.isEmpty() ? (searchFocused ? "§f|" : "§7Search block...") : (searchFocused ? searchQuery + "§6|" : searchQuery);
        RenderUtils.drawText(context, textRenderer, searchDisplay, searchX + 5, searchY + 3, 0xFFCCCCCC, true);

        // Action Buttons at top
        int btnW = 90;
        int btnH = 14;
        int btn1X = panelX + 12;
        int btn1Y = panelY + 26;
        boolean b1Hov = mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH;
        RenderUtils.fill(context, btn1X, btn1Y, btn1X + btnW, btn1Y + btnH, b1Hov ? ThemeManager.getAccentColor() : ColorUtils.rgba(25, 30, 40, 200));
        RenderUtils.drawBorder(context, btn1X, btn1Y, btn1X + btnW, btn1Y + btnH, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "Select All Ores", btn1X + 8, btn1Y + 3, 0xFFFFFFFF, true);

        int btn2X = btn1X + btnW + 6;
        boolean b2Hov = mouseX >= btn2X && mouseX <= btn2X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH;
        RenderUtils.fill(context, btn2X, btn1Y, btn2X + btnW, btn1Y + btnH, b2Hov ? ThemeManager.getAccentColor() : ColorUtils.rgba(25, 30, 40, 200));
        RenderUtils.drawBorder(context, btn2X, btn1Y, btn2X + btnW, btn1Y + btnH, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "Clear Selection", btn2X + 8, btn1Y + 3, 0xFFFFFFFF, true);

        // Content Area for Block List
        int contentX = panelX + 8;
        int contentY = panelY + 44;
        int contentW = panelW - 16;
        int contentH = panelH - 52;

        RenderUtils.fill(context, contentX, contentY, contentX + contentW, contentY + contentH, ColorUtils.rgba(12, 14, 18, 180));
        RenderUtils.drawBorder(context, contentX, contentY, contentX + contentW, contentY + contentH, 1, ThemeManager.getBorderColor());

        int cols = 2;
        int itemW = (contentW - 16) / cols;
        int itemH = 22;
        int totalRows = (int) Math.ceil((double) filteredBlocks.size() / cols);
        int totalHeight = totalRows * (itemH + 2) + 6;
        int maxScroll = Math.max(0, totalHeight - contentH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        try {
            context.enableScissor(contentX + 1, contentY + 1, contentX + contentW - 1, contentY + contentH - 1);
        } catch (Throwable ignored) {}

        for (int i = 0; i < filteredBlocks.size(); i++) {
            Block block = filteredBlocks.get(i);
            int row = i / cols;
            int col = i % cols;

            int ix = contentX + 6 + col * (itemW + 4);
            int iy = contentY + 6 + row * (itemH + 2) - scrollOffset;

            if (iy + itemH < contentY || iy > contentY + contentH) continue;

            boolean selected = xray.isBlockSelected(block);
            boolean hov = mouseX >= ix && mouseX <= ix + itemW && mouseY >= iy && mouseY <= iy + itemH;

            int itemBg = selected ? ThemeManager.getAccentColor() : (hov ? ColorUtils.rgba(30, 35, 45, 200) : ColorUtils.rgba(18, 20, 26, 160));
            RenderUtils.fill(context, ix, iy, ix + itemW, iy + itemH, itemBg);
            RenderUtils.drawBorder(context, ix, iy, ix + itemW, iy + itemH, 1, selected ? ThemeManager.getAccentColor() : ColorUtils.rgba(40, 45, 60, 100));

            // Block icon
            try {
                ItemStack itemStack = new ItemStack(block.asItem());
                if (!itemStack.isEmpty()) {
                    context.drawItem(itemStack, ix + 3, iy + 3);
                }
            } catch (Throwable ignored) {}

            String name = block.getName().getString();
            if (name.length() > 22) name = name.substring(0, 20) + "..";
            int textColor = selected ? 0xFFFFFFFF : 0xFFDDDDDD;
            RenderUtils.drawText(context, textRenderer, name, ix + 24, iy + 7, textColor, true);

            String check = selected ? "§a[ON]" : "§7[OFF]";
            int cw = textRenderer.getWidth(check);
            RenderUtils.drawText(context, textRenderer, check, ix + itemW - cw - 6, iy + 7, 0xFFFFFFFF, true);
        }

        try {
            context.disableScissor();
        } catch (Throwable ignored) {}

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = Math.min(560, width - 40);
        int panelH = Math.min(360, height - 40);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        // Search bar
        int searchW = 140;
        int searchX = panelX + panelW - searchW - 12;
        int searchY = panelY + 6;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + 14) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // Action Buttons
        int btnW = 90;
        int btnH = 14;
        int btn1X = panelX + 12;
        int btn1Y = panelY + 26;
        if (mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH) {
            xray.selectAllOres();
            return true;
        }

        int btn2X = btn1X + btnW + 6;
        if (mouseX >= btn2X && mouseX <= btn2X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH) {
            xray.clearSelection();
            return true;
        }

        // Content Area Click
        int contentX = panelX + 8;
        int contentY = panelY + 44;
        int contentW = panelW - 16;
        int contentH = panelH - 52;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            int cols = 2;
            int itemW = (contentW - 16) / cols;
            int itemH = 22;

            for (int i = 0; i < filteredBlocks.size(); i++) {
                int row = i / cols;
                int col = i % cols;

                int ix = contentX + 6 + col * (itemW + 4);
                int iy = contentY + 6 + row * (itemH + 2) - scrollOffset;

                if (mouseX >= ix && mouseX <= ix + itemW && mouseY >= iy && mouseY <= iy + itemH) {
                    Block block = filteredBlocks.get(i);
                    xray.toggleBlock(block);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, scrollOffset - (int) (verticalAmount * 24));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    updateFilter();
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                searchQuery += " ";
                updateFilter();
                return true;
            } else if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
                boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
                char c = (char) ((shift ? 'A' : 'a') + (keyCode - GLFW.GLFW_KEY_A));
                searchQuery += c;
                updateFilter();
                return true;
            } else if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                char c = (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
                searchQuery += c;
                updateFilter();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_MINUS) {
                searchQuery += "_";
                updateFilter();
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (client != null) {
                client.setScreen(parent);
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
