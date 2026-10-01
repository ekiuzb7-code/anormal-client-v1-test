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

    public ClickGuiScreen() {
        super(Text.literal("Anormal Client GUI"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int guiWidth = Math.min(490, width - 20);
        int guiHeight = Math.min(280, height - 20);
        int guiX = (width - guiWidth) / 2;
        int guiY = (height - guiHeight) / 2;

        Theme activeTheme = ThemeManager.getActiveTheme();

        // Background dark overlay / blur
        renderBackground(context, mouseX, mouseY, delta);

        // Main Window Frame
        ThemeManager.renderWindow(context, guiX, guiY, guiWidth, guiHeight, "Anormal Client");

        // Top Header / Brand
        String titleText = "ANORMAL " + (activeTheme == Theme.VAPE_V4 ? "§6[VAPE V4]" : "§b[GLASSMORPHISM]");
        RenderUtils.drawText(context, textRenderer, titleText, guiX + 10, guiY + 7, 0xFFFFFFFF, true);

        // Search Bar at Top Right
        int searchW = 100;
        int searchX = guiX + guiWidth - searchW - 10;
        int searchY = guiY + 5;
        RenderUtils.fill(context, searchX, searchY, searchX + searchW, searchY + 14, ColorUtils.rgba(10, 10, 10, 200));
        RenderUtils.drawBorder(context, searchX, searchY, searchX + searchW, searchY + 14, 1, searchFocused ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());
        String searchDisplay = searchQuery.isEmpty() ? (searchFocused ? "§f|" : "§7Search...") : (searchFocused ? searchQuery + "§6|" : searchQuery);
        RenderUtils.drawText(context, textRenderer, searchDisplay, searchX + 4, searchY + 3, 0xFFCCCCCC, true);

        // Category Navigation Tabs
        Category[] categories = Category.values();
        int totalTabs = categories.length;
        int gap = 2;
        int tabWidth = (guiWidth - 20 - (totalTabs - 1) * gap) / totalTabs;
        int tabHeight = 18;
        int tabX = guiX + 10;
        int tabY = guiY + 24;

        for (Category category : categories) {
            boolean isSelected = category == currentCategory;
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabHeight;

            int tabBg = isSelected ? ThemeManager.getAccentColor() : (isHovered ? ThemeManager.getCardColor(false, true) : ThemeManager.getCardColor(false, false));
            int textColor = isSelected ? 0xFFFFFFFF : (isHovered ? 0xFFE0E0E0 : ThemeManager.getTextColor(false));

            RenderUtils.fill(context, tabX, tabY, tabX + tabWidth, tabY + tabHeight, tabBg);
            RenderUtils.drawBorder(context, tabX, tabY, tabX + tabWidth, tabY + tabHeight, 1, ThemeManager.getBorderColor());

            String label = category.getIcon() + " " + category.getName();
            int strW = textRenderer.getWidth(label);
            RenderUtils.drawText(context, textRenderer, label, tabX + (tabWidth - strW) / 2, tabY + 5, textColor, true);

            tabX += tabWidth + gap;
        }

        // Modules Panel Container
        int contentX = guiX + 10;
        int contentY = guiY + 45;
        int contentWidth = guiWidth - 20;
        int contentHeight = guiHeight - 52;

        RenderUtils.fill(context, contentX, contentY, contentX + contentWidth, contentY + contentHeight, ColorUtils.rgba(12, 14, 18, 140));
        RenderUtils.drawBorder(context, contentX, contentY, contentX + contentWidth, contentY + contentHeight, 1, ThemeManager.getBorderColor());

        // Render Module Cards
        List<Module> categoryModules = ModuleManager.getModulesByCategory(currentCategory).stream()
                .filter(m -> searchQuery.isEmpty() || m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();

        int modY = contentY + 5 - scrollOffset;

        for (Module module : categoryModules) {
            if (modY + 22 >= contentY && modY <= contentY + contentHeight) {
                renderModuleCard(context, module, contentX + 5, modY, contentWidth - 10, mouseX, mouseY);
            }

            modY += 24;

            if (module.isExpanded()) {
                for (Setting<?> setting : module.getSettings()) {
                    if (modY + 18 >= contentY && modY <= contentY + contentHeight) {
                        renderSetting(context, setting, contentX + 15, modY, contentWidth - 30, mouseX, mouseY);
                    }
                    modY += 20;
                }
            }
        }

        // Handle active slider dragging
        if (draggingSlider != null) {
            updateSliderValue(mouseX, guiX, guiWidth);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderModuleCard(DrawContext context, Module module, int x, int y, int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 21;
        int cardBg = ThemeManager.getCardColor(module.isEnabled(), hovered);

        RenderUtils.fill(context, x, y, x + width, y + 21, cardBg);
        RenderUtils.drawBorder(context, x, y, x + width, y + 21, 1, ThemeManager.getBorderColor());

        // Active indicator strip
        if (module.isEnabled()) {
            RenderUtils.fill(context, x, y, x + 3, y + 21, ThemeManager.getAccentColor());
        }

        // Module Name
        int textColor = module.isEnabled() ? 0xFFFFFFFF : ThemeManager.getTextColor(false);
        RenderUtils.drawText(context, textRenderer, module.getName(), x + 7, y + 6, textColor, true);

        // Keybind badge
        String keyText = "[" + module.getKeybindSetting().getKeyName() + "]";
        int keyWidth = textRenderer.getWidth(keyText);
        RenderUtils.drawText(context, textRenderer, keyText, x + width - keyWidth - 26, y + 6, 0xFF888888, true);

        // Expand settings indicator
        String expandText = module.isExpanded() ? "▼" : "▶";
        RenderUtils.drawText(context, textRenderer, expandText, x + width - 16, y + 6, ThemeManager.getAccentColor(), true);
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
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int guiWidth = Math.min(490, width - 20);
        int guiHeight = Math.min(280, height - 20);
        int guiX = (width - guiWidth) / 2;
        int guiY = (height - guiHeight) / 2;

        // Search Bar click
        int searchW = 100;
        int searchX = guiX + guiWidth - searchW - 10;
        int searchY = guiY + 5;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + 14) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // Tab switching
        Category[] categories = Category.values();
        int totalTabs = categories.length;
        int gap = 2;
        int tabWidth = (guiWidth - 20 - (totalTabs - 1) * gap) / totalTabs;
        int tabHeight = 18;
        int tabX = guiX + 10;
        int tabY = guiY + 24;

        for (Category category : categories) {
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabHeight) {
                currentCategory = category;
                scrollOffset = 0;
                return true;
            }
            tabX += tabWidth + gap;
        }

        // Modules and Settings interaction
        int contentX = guiX + 10;
        int contentY = guiY + 45;
        int contentWidth = guiWidth - 20;
        int contentHeight = guiHeight - 52;

        List<Module> categoryModules = ModuleManager.getModulesByCategory(currentCategory).stream()
                .filter(m -> searchQuery.isEmpty() || m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();

        int modY = contentY + 5 - scrollOffset;

        for (Module module : categoryModules) {
            int cardX = contentX + 5;
            int cardW = contentWidth - 10;

            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= modY && mouseY <= modY + 21) {
                // If right-clicked OR clicked on the expand arrow icon on the right side
                if (button == 1 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || mouseX >= cardX + cardW - 24) {
                    module.setExpanded(!module.isExpanded());
                    return true;
                } else {
                    // Left click on card toggles the module
                    module.toggle();
                    return true;
                }
            }

            modY += 24;

            if (module.isExpanded()) {
                for (Setting<?> setting : module.getSettings()) {
                    int setX = contentX + 15;
                    int setW = contentWidth - 30;

                    if (mouseX >= setX && mouseX <= setX + setW && mouseY >= modY && mouseY <= modY + 18) {
                        if (setting instanceof BooleanSetting bool) {
                            bool.toggle();
                            return true;
                        } else if (setting instanceof ModeSetting mode) {
                            if (button == 1 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) mode.cycleBack();
                            else mode.cycle();
                            return true;
                        } else if (setting instanceof KeybindSetting key) {
                            listeningSetting = key;
                            return true;
                        } else if (setting instanceof NumberSetting num) {
                            draggingSlider = num;
                            updateSliderValue((int) mouseX, guiX, guiWidth);
                            return true;
                        }
                    }
                    modY += 20;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, scrollOffset - (int) (verticalAmount * 18));
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
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
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
            searchQuery += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void updateSliderValue(int mouseX, int guiX, int guiWidth) {
        if (draggingSlider == null) return;
        int contentX = guiX + 10;
        int contentWidth = guiWidth - 20;
        int setW = contentWidth - 30;
        int sliderWidth = 70;
        int sliderX = contentX + 15 + setW - sliderWidth - 6;

        double percent = (double) (mouseX - sliderX) / (double) sliderWidth;
        percent = Math.max(0.0, Math.min(1.0, percent));
        double value = draggingSlider.getMin() + (percent * (draggingSlider.getMax() - draggingSlider.getMin()));
        draggingSlider.setValue(value);
    }
}
