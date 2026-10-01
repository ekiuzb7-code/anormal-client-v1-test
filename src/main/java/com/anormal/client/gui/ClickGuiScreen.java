package com.anormal.client.gui;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.*;
import com.anormal.client.theme.Theme;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ClickGuiScreen extends Screen {
    private Category currentCategory = Category.COMBAT;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private Setting<?> listeningSetting = null;
    private NumberSetting draggingSlider = null;

    private int scrollOffset = 0;
    private boolean leftWasDown = false;
    private boolean rightWasDown = false;

    public ClickGuiScreen() {
        super(Text.literal("Anormal Client GUI"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int guiWidth = Math.min(520, width - 20);
        int guiHeight = Math.min(300, height - 20);
        int guiX = (width - guiWidth) / 2;
        int guiY = (height - guiHeight) / 2;

        Theme activeTheme = ThemeManager.getActiveTheme();

        // 1. Background dark tint
        renderBackground(context, mouseX, mouseY, delta);

        // 2. Direct GLFW mouse click handling for 100% responsiveness
        handleDirectMouseInput(mouseX, mouseY, guiX, guiY, guiWidth, guiHeight);

        // 3. Main Window Frame
        ThemeManager.renderWindow(context, guiX, guiY, guiWidth, guiHeight, "Anormal Client");

        // 4. Top Header
        String titleText = "ANORMAL " + (activeTheme == Theme.VAPE_V4 ? "§6[VAPE V4]" : "§b[GLASSMORPHISM]");
        RenderUtils.drawText(context, textRenderer, titleText, guiX + 12, guiY + 8, 0xFFFFFFFF, true);

        // Search Bar at Top Right
        int searchW = 110;
        int searchX = guiX + guiWidth - searchW - 12;
        int searchY = guiY + 6;
        RenderUtils.fill(context, searchX, searchY, searchX + searchW, searchY + 14, ColorUtils.rgba(10, 10, 10, 200));
        RenderUtils.drawBorder(context, searchX, searchY, searchX + searchW, searchY + 14, 1, searchFocused ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());
        String searchDisplay = searchQuery.isEmpty() ? (searchFocused ? "§f|" : "§7Search...") : (searchFocused ? searchQuery + "§6|" : searchQuery);
        RenderUtils.drawText(context, textRenderer, searchDisplay, searchX + 5, searchY + 3, 0xFFCCCCCC, true);

        // Edit HUD Button
        int hudBtnW = 64;
        int hudBtnX = searchX - hudBtnW - 6;
        int hudBtnY = guiY + 6;
        boolean hudHovered = mouseX >= hudBtnX && mouseX <= hudBtnX + hudBtnW && mouseY >= hudBtnY && mouseY <= hudBtnY + 14;
        RenderUtils.fill(context, hudBtnX, hudBtnY, hudBtnX + hudBtnW, hudBtnY + 14, hudHovered ? ThemeManager.getAccentColor() : ColorUtils.rgba(20, 24, 30, 200));
        RenderUtils.drawBorder(context, hudBtnX, hudBtnY, hudBtnX + hudBtnW, hudBtnY + 14, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "⚙ Edit HUD", hudBtnX + 5, hudBtnY + 3, hudHovered ? 0xFFFFFFFF : 0xFFDDDDDD, true);

        // 5. Left Sidebar (Category Navigation Tabs)
        int sidebarWidth = 110;
        int sidebarX = guiX + 8;
        int sidebarY = guiY + 26;
        int sidebarHeight = guiHeight - 34;

        RenderUtils.fill(context, sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, ColorUtils.rgba(14, 16, 20, 160));
        RenderUtils.drawBorder(context, sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 1, ThemeManager.getBorderColor());

        Category[] categories = Category.values();
        int tabY = sidebarY + 4;
        int tabHeight = 24;

        for (Category category : categories) {
            boolean isSelected = category == currentCategory;
            boolean isHovered = mouseX >= sidebarX + 4 && mouseX <= sidebarX + sidebarWidth - 4 && mouseY >= tabY && mouseY <= tabY + tabHeight;

            int tabBg = isSelected ? ThemeManager.getAccentColor() : (isHovered ? ThemeManager.getCardColor(false, true) : ColorUtils.rgba(20, 22, 28, 120));
            int textColor = isSelected ? 0xFFFFFFFF : (isHovered ? 0xFFE0E0E0 : ThemeManager.getTextColor(false));

            RenderUtils.fill(context, sidebarX + 4, tabY, sidebarX + sidebarWidth - 4, tabY + tabHeight, tabBg);
            RenderUtils.drawBorder(context, sidebarX + 4, tabY, sidebarX + sidebarWidth - 4, tabY + tabHeight, 1, isSelected ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());

            String label = category.getIcon() + " " + category.getName();
            RenderUtils.drawText(context, textRenderer, label, sidebarX + 10, tabY + 8, textColor, true);

            tabY += tabHeight + 4;
        }

        // 6. Right Main Panel (Scrollable Module List)
        int contentX = sidebarX + sidebarWidth + 6;
        int contentY = guiY + 26;
        int contentWidth = guiWidth - sidebarWidth - 22;
        int contentHeight = guiHeight - 34;

        RenderUtils.fill(context, contentX, contentY, contentX + contentWidth, contentY + contentHeight, ColorUtils.rgba(12, 14, 18, 140));
        RenderUtils.drawBorder(context, contentX, contentY, contentX + contentWidth, contentY + contentHeight, 1, ThemeManager.getBorderColor());

        List<Module> categoryModules = ModuleManager.getModulesByCategory(currentCategory).stream()
                .filter(m -> searchQuery.isEmpty() || m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();

        // Calculate max scroll height
        int totalContentHeight = 6;
        for (Module m : categoryModules) {
            totalContentHeight += 26;
            if (m.isExpanded()) {
                totalContentHeight += m.getSettings().size() * 20;
            }
        }
        int maxScroll = Math.max(0, totalContentHeight - contentHeight + 10);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Enable Scissor to prevent any module from overflowing borders
        try {
            context.enableScissor(contentX + 1, contentY + 1, contentX + contentWidth - 1, contentY + contentHeight - 1);
        } catch (Throwable ignored) {}

        int modY = contentY + 6 - scrollOffset;

        for (Module module : categoryModules) {
            renderModuleCard(context, module, contentX + 6, modY, contentWidth - 16, mouseX, mouseY);
            modY += 26;

            if (module.isExpanded()) {
                for (Setting<?> setting : module.getSettings()) {
                    renderSetting(context, setting, contentX + 16, modY, contentWidth - 36, mouseX, mouseY);
                    modY += 20;
                }
                if (module instanceof com.anormal.client.module.impl.world.XRay) {
                    int btnX = contentX + 16;
                    int btnW = contentWidth - 36;
                    boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= modY && mouseY <= modY + 18;
                    RenderUtils.fill(context, btnX, modY, btnX + btnW, modY + 18, hov ? ThemeManager.getAccentColor() : ColorUtils.rgba(20, 24, 32, 220));
                    RenderUtils.drawBorder(context, btnX, modY, btnX + btnW, modY + 18, 1, ThemeManager.getBorderColor());
                    RenderUtils.drawText(context, textRenderer, "🔍 Select Blocks...", btnX + 8, modY + 5, 0xFFFFFFFF, true);
                    modY += 22;
                }
            }
        }

        // Disable Scissor after rendering modules
        try {
            context.disableScissor();
        } catch (Throwable ignored) {}

        // Scrollbar track & thumb
        if (maxScroll > 0) {
            int scrollbarX = contentX + contentWidth - 6;
            int scrollbarY = contentY + 2;
            int scrollbarH = contentHeight - 4;
            RenderUtils.fill(context, scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarH, ColorUtils.rgba(20, 20, 25, 150));

            float thumbRatio = (float) contentHeight / (float) totalContentHeight;
            int thumbH = Math.max(16, (int) (scrollbarH * thumbRatio));
            float scrollRatio = (float) scrollOffset / (float) maxScroll;
            int thumbY = scrollbarY + (int) ((scrollbarH - thumbH) * scrollRatio);

            RenderUtils.fill(context, scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, ThemeManager.getAccentColor());
        }

        // Handle active slider dragging
        if (draggingSlider != null) {
            updateSliderValue(mouseX, contentX, contentWidth);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void handleDirectMouseInput(int mouseX, int mouseY, int guiX, int guiY, int guiWidth, int guiHeight) {
        if (client == null || client.getWindow() == null) return;
        long window = client.getWindow().getHandle();
        if (window == 0) return;

        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS;

        if (leftDown && !leftWasDown) {
            processClick(mouseX, mouseY, 0, guiX, guiY, guiWidth, guiHeight);
        }
        if (rightDown && !rightWasDown) {
            processClick(mouseX, mouseY, 1, guiX, guiY, guiWidth, guiHeight);
        }

        if (!leftDown) {
            draggingSlider = null;
        }

        leftWasDown = leftDown;
        rightWasDown = rightDown;
    }

    private void processClick(int mouseX, int mouseY, int button, int guiX, int guiY, int guiWidth, int guiHeight) {
        // 1. Edit HUD Button Click
        int searchW = 110;
        int searchX = guiX + guiWidth - searchW - 12;
        int searchY = guiY + 6;
        int hudBtnW = 64;
        int hudBtnX = searchX - hudBtnW - 6;
        int hudBtnY = guiY + 6;
        if (mouseX >= hudBtnX && mouseX <= hudBtnX + hudBtnW && mouseY >= hudBtnY && mouseY <= hudBtnY + 14) {
            if (client != null) {
                client.setScreen(new HudEditorScreen(this));
            }
            return;
        }

        // 2. Search Bar Click
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + 14) {
            searchFocused = true;
            return;
        } else {
            searchFocused = false;
        }

        // 2. Category Sidebar Tabs
        int sidebarWidth = 110;
        int sidebarX = guiX + 8;
        int sidebarY = guiY + 26;

        Category[] categories = Category.values();
        int tabY = sidebarY + 4;
        int tabHeight = 24;

        for (Category category : categories) {
            if (mouseX >= sidebarX + 4 && mouseX <= sidebarX + sidebarWidth - 4 && mouseY >= tabY && mouseY <= tabY + tabHeight) {
                currentCategory = category;
                scrollOffset = 0;
                return;
            }
            tabY += tabHeight + 4;
        }

        // 3. Module Cards & Settings Interaction
        int contentX = sidebarX + sidebarWidth + 6;
        int contentY = guiY + 26;
        int contentWidth = guiWidth - sidebarWidth - 22;
        int contentHeight = guiHeight - 34;

        // Ensure clicks are within content box
        if (mouseX < contentX || mouseX > contentX + contentWidth || mouseY < contentY || mouseY > contentY + contentHeight) {
            return;
        }

        List<Module> categoryModules = ModuleManager.getModulesByCategory(currentCategory).stream()
                .filter(m -> searchQuery.isEmpty() || m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();

        int modY = contentY + 6 - scrollOffset;

        for (Module module : categoryModules) {
            int cardX = contentX + 6;
            int cardW = contentWidth - 16;

            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= modY && mouseY <= modY + 22) {
                if (button == 1 || mouseX >= cardX + cardW - 24) {
                    // Right-click or clicking expand arrow toggles settings
                    module.setExpanded(!module.isExpanded());
                } else {
                    // Left-click toggles module ON/OFF
                    module.toggle();
                }
                return;
            }

            modY += 26;

            if (module.isExpanded()) {
                for (Setting<?> setting : module.getSettings()) {
                    int setX = contentX + 16;
                    int setW = contentWidth - 36;

                    if (mouseX >= setX && mouseX <= setX + setW && mouseY >= modY && mouseY <= modY + 18) {
                        if (setting instanceof BooleanSetting bool) {
                            bool.toggle();
                            return;
                        } else if (setting instanceof ModeSetting mode) {
                            if (button == 1) mode.cycleBack();
                            else mode.cycle();
                            return;
                        } else if (setting instanceof KeybindSetting key) {
                            listeningSetting = key;
                            return;
                        } else if (setting instanceof NumberSetting num) {
                            draggingSlider = num;
                            updateSliderValue(mouseX, contentX, contentWidth);
                            return;
                        } else if (setting instanceof ColorSetting color) {
                            int[] palette = {
                                ColorUtils.rgba(255, 120, 0, 255),  // Vape Orange
                                ColorUtils.rgba(0, 230, 255, 255),  // Neon Cyan
                                ColorUtils.rgba(255, 50, 50, 255),   // Crimson Red
                                ColorUtils.rgba(0, 255, 127, 255),   // Emerald Green
                                ColorUtils.rgba(180, 50, 255, 255),  // Royal Purple
                                ColorUtils.rgba(255, 220, 0, 255),   // Gold Yellow
                                ColorUtils.rgba(255, 255, 255, 255)  // Pure White
                            };
                            int cur = color.getValue();
                            int nextIdx = 0;
                            for (int i = 0; i < palette.length; i++) {
                                if (palette[i] == cur) {
                                    nextIdx = (i + 1) % palette.length;
                                    break;
                                }
                            }
                            color.setValue(palette[nextIdx]);
                            return;
                        }
                    }
                    modY += 20;
                }
                if (module instanceof com.anormal.client.module.impl.world.XRay xrayModule) {
                    int btnX = contentX + 16;
                    int btnW = contentWidth - 36;
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= modY && mouseY <= modY + 18) {
                        if (client != null) {
                            client.setScreen(new XRayBlockSelectorScreen(this, xrayModule));
                        }
                        return;
                    }
                    modY += 22;
                }
            }
        }
    }

    private void renderModuleCard(DrawContext context, Module module, int x, int y, int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 22;
        int cardBg = ThemeManager.getCardColor(module.isEnabled(), hovered);

        RenderUtils.fill(context, x, y, x + width, y + 22, cardBg);
        RenderUtils.drawBorder(context, x, y, x + width, y + 22, 1, ThemeManager.getBorderColor());

        // Active indicator strip
        if (module.isEnabled()) {
            RenderUtils.fill(context, x, y, x + 3, y + 22, ThemeManager.getAccentColor());
        }

        // Module Name
        int textColor = module.isEnabled() ? 0xFFFFFFFF : ThemeManager.getTextColor(false);
        RenderUtils.drawText(context, textRenderer, module.getName(), x + 8, y + 7, textColor, true);

        // Keybind badge
        String keyText = "[" + module.getKeybindSetting().getKeyName() + "]";
        int keyWidth = textRenderer.getWidth(keyText);
        RenderUtils.drawText(context, textRenderer, keyText, x + width - keyWidth - 26, y + 7, 0xFF888888, true);

        // Expand settings indicator
        String expandText = module.isExpanded() ? "▼" : "▶";
        RenderUtils.drawText(context, textRenderer, expandText, x + width - 16, y + 7, ThemeManager.getAccentColor(), true);
    }

    private void renderSetting(DrawContext context, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY) {
        RenderUtils.fill(context, x, y, x + width, y + 18, ColorUtils.rgba(18, 20, 26, 220));
        RenderUtils.drawBorder(context, x, y, x + width, y + 18, 1, ColorUtils.rgba(40, 45, 60, 100));

        RenderUtils.drawText(context, textRenderer, setting.getName(), x + 6, y + 5, 0xFFDDDDDD, true);

        if (setting instanceof BooleanSetting bool) {
            int toggleX = x + width - 20;
            int toggleY = y + 4;
            int toggleBg = bool.isEnabled() ? ThemeManager.getAccentColor() : ColorUtils.rgba(40, 40, 40, 255);
            RenderUtils.fill(context, toggleX, toggleY, toggleX + 14, toggleY + 10, toggleBg);
            RenderUtils.drawBorder(context, toggleX, toggleY, toggleX + 14, toggleY + 10, 1, ThemeManager.getBorderColor());
            if (bool.isEnabled()) {
                RenderUtils.drawText(context, textRenderer, "✓", toggleX + 3, toggleY + 1, 0xFFFFFFFF, true);
            }
        } else if (setting instanceof NumberSetting num) {
            int sliderWidth = 70;
            int sliderX = x + width - sliderWidth - 6;
            int sliderY = y + 6;
            int sliderHeight = 6;

            // Track background
            RenderUtils.fill(context, sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, ColorUtils.rgba(30, 30, 35, 255));

            // Filled progress
            double percent = (num.getValue() - num.getMin()) / (num.getMax() - num.getMin());
            int fillWidth = (int) (sliderWidth * Math.max(0, Math.min(1, percent)));
            RenderUtils.fill(context, sliderX, sliderY, sliderX + fillWidth, sliderY + sliderHeight, ThemeManager.getAccentColor());

            // Value label
            String valStr = String.format("%.1f", num.getValue());
            RenderUtils.drawText(context, textRenderer, valStr, sliderX - textRenderer.getWidth(valStr) - 4, y + 5, 0xFFAAAAAA, true);
        } else if (setting instanceof ModeSetting mode) {
            String modeStr = "< " + mode.getValue() + " >";
            int mw = textRenderer.getWidth(modeStr);
            RenderUtils.drawText(context, textRenderer, modeStr, x + width - mw - 6, y + 5, ThemeManager.getAccentColor(), true);
        } else if (setting instanceof KeybindSetting key) {
            String keyStr = (listeningSetting == key) ? "§e[...]" : "[" + key.getKeyName() + "]";
            int kw = textRenderer.getWidth(keyStr);
            RenderUtils.drawText(context, textRenderer, keyStr, x + width - kw - 6, y + 5, 0xFF888888, true);
        } else if (setting instanceof ColorSetting color) {
            int boxX = x + width - 36;
            int boxY = y + 4;
            RenderUtils.fill(context, boxX, boxY, boxX + 30, boxY + 10, color.getValue());
            RenderUtils.drawBorder(context, boxX, boxY, boxX + 30, boxY + 10, 1, 0xFFFFFFFF);
            String colLabel = color.isRainbow() ? "§dRAINBOW" : String.format("#%06X", (0xFFFFFF & color.getValue()));
            RenderUtils.drawText(context, textRenderer, colLabel, boxX - textRenderer.getWidth(colLabel) - 4, y + 5, 0xFFAAAAAA, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int guiWidth = Math.min(520, width - 20);
        int guiHeight = Math.min(300, height - 20);
        int guiX = (width - guiWidth) / 2;
        int guiY = (height - guiHeight) / 2;

        processClick((int) mouseX, (int) mouseY, button, guiX, guiY, guiWidth, guiHeight);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, scrollOffset - (int) (verticalAmount * 22));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningSetting instanceof KeybindSetting keySetting) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                keySetting.setValue(GLFW.GLFW_KEY_UNKNOWN);
            } else {
                keySetting.setValue(keyCode);
            }
            listeningSetting = null;
            return true;
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                searchQuery += " ";
                return true;
            } else if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
                boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
                char c = (char) ((shift ? 'A' : 'a') + (keyCode - GLFW.GLFW_KEY_A));
                searchQuery += c;
                return true;
            } else if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                char c = (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
                searchQuery += c;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_MINUS) {
                searchQuery += "-";
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_PERIOD) {
                searchQuery += ".";
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && chr >= 32 && chr <= 126) {
            if (!searchQuery.endsWith(String.valueOf(chr))) {
                searchQuery += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void updateSliderValue(int mouseX, int contentX, int contentWidth) {
        if (draggingSlider == null) return;
        int setW = contentWidth - 36;
        int sliderWidth = 70;
        int sliderX = contentX + 16 + setW - sliderWidth - 6;

        double percent = (double) (mouseX - sliderX) / (double) sliderWidth;
        percent = Math.max(0.0, Math.min(1.0, percent));
        double value = draggingSlider.getMin() + (percent * (draggingSlider.getMax() - draggingSlider.getMin()));
        draggingSlider.setValue(value);
    }
}
