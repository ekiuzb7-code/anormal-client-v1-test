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
    private Setting<?> listeningSetting = null;
    private NumberSetting draggingSlider = null;

    // Window layout
    private int guiX = 40;
    private int guiY = 30;
    private int guiWidth = 540;
    private int guiHeight = 320;
    private int scrollOffset = 0;

    public ClickGuiScreen() {
        super(Text.literal("Anormal Client GUI"));
    }

    @Override
    protected void init() {
        super.init();
        guiX = Math.max(10, (width - guiWidth) / 2);
        guiY = Math.max(10, (height - guiHeight) / 2);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Theme activeTheme = ThemeManager.getActiveTheme();

        // Background dark overlay / blur
        renderBackground(context, mouseX, mouseY, delta);

        // Main Window Frame
        ThemeManager.renderWindow(context, guiX, guiY, guiWidth, guiHeight, "Anormal Client");

        // Top Header / Brand
        String titleText = "ANORMAL " + (activeTheme == Theme.VAPE_V4 ? "§6[VAPE V4]" : "§b[GLASSMORPHISM]");
        context.drawTextWithShadow(textRenderer, titleText, guiX + 10, guiY + 7, 0xFFFFFFFF);

        // Category Navigation Tabs
        Category[] categories = Category.values();
        int totalTabs = categories.length;
        int gap = 3;
        int tabWidth = (guiWidth - 20 - (totalTabs - 1) * gap) / totalTabs;
        int tabHeight = 18;
        int tabX = guiX + 10;
        int tabY = guiY + 26;

        for (Category category : categories) {
            boolean isSelected = category == currentCategory;
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabHeight;

            int tabBg = isSelected ? ThemeManager.getAccentColor() : (isHovered ? ThemeManager.getCardColor(false, true) : ThemeManager.getCardColor(false, false));
            int textColor = isSelected ? 0xFFFFFFFF : (isHovered ? 0xFFE0E0E0 : ThemeManager.getTextColor(false));

            RenderUtils.fill(context, tabX, tabY, tabX + tabWidth, tabY + tabHeight, tabBg);
            RenderUtils.drawBorder(context, tabX, tabY, tabX + tabWidth, tabY + tabHeight, 1, ThemeManager.getBorderColor());

            String label = category.getIcon() + " " + category.getName();
            int strW = textRenderer.getWidth(label);
            context.drawTextWithShadow(textRenderer, label, tabX + (tabWidth - strW) / 2, tabY + 5, textColor);

            tabX += tabWidth + gap;
        }

        // Search Bar at Top Right
        int searchX = guiX + guiWidth - 140;
        int searchY = guiY + 5;
        RenderUtils.fill(context, searchX, searchY, searchX + 130, searchY + 14, ColorUtils.rgba(10, 10, 10, 180));
        RenderUtils.drawBorder(context, searchX, searchY, searchX + 130, searchY + 14, 1, ThemeManager.getBorderColor());
        String searchDisplay = searchQuery.isEmpty() ? "§7Search..." : searchQuery;
        context.drawTextWithShadow(textRenderer, searchDisplay, searchX + 4, searchY + 3, 0xFFCCCCCC);

        // Modules Panel Container
        int contentX = guiX + 10;
        int contentY = guiY + 48;
        int contentWidth = guiWidth - 20;
        int contentHeight = guiHeight - 58;

        RenderUtils.fill(context, contentX, contentY, contentX + contentWidth, contentY + contentHeight, ColorUtils.rgba(12, 14, 18, 120));
        RenderUtils.drawBorder(context, contentX, contentY, contentX + contentWidth, contentY + contentHeight, 1, ThemeManager.getBorderColor());

        // Render Module Cards
        List<Module> categoryModules = ModuleManager.getModulesByCategory(currentCategory).stream()
                .filter(m -> searchQuery.isEmpty() || m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();

        int modY = contentY + 6 - scrollOffset;

        for (Module module : categoryModules) {
            if (modY + 24 >= contentY && modY <= contentY + contentHeight) {
                renderModuleCard(context, module, contentX + 6, modY, contentWidth - 12, mouseX, mouseY);
            }

            modY += 26;

            if (module.isExpanded()) {
                for (Setting<?> setting : module.getSettings()) {
                    if (modY + 18 >= contentY && modY <= contentY + contentHeight) {
                        renderSetting(context, setting, contentX + 16, modY, contentWidth - 32, mouseX, mouseY);
                    }
                    modY += 20;
                }
            }
        }

        // Handle active slider dragging
        if (draggingSlider != null) {
            updateSliderValue(mouseX);
        }

        super.render(context, mouseX, mouseY, delta);
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
        context.drawTextWithShadow(textRenderer, module.getName(), x + 8, y + 7, textColor);

        // Keybind badge
        String keyText = "[" + module.getKeybindSetting().getKeyName() + "]";
        int keyWidth = textRenderer.getWidth(keyText);
        context.drawTextWithShadow(textRenderer, keyText, x + width - keyWidth - 30, y + 7, 0xFF888888);

        // Expand settings indicator
        String expandText = module.isExpanded() ? "▼" : "▶";
        context.drawTextWithShadow(textRenderer, expandText, x + width - 18, y + 7, ThemeManager.getAccentColor());
    }

    private void renderSetting(DrawContext context, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY) {
        RenderUtils.fill(context, x, y, x + width, y + 18, ColorUtils.rgba(18, 20, 26, 200));
        RenderUtils.drawBorder(context, x, y, x + width, y + 18, 1, ColorUtils.rgba(40, 45, 60, 100));

        context.drawTextWithShadow(textRenderer, setting.getName(), x + 6, y + 5, 0xFFDDDDDD);

        if (setting instanceof BooleanSetting bool) {
            int toggleX = x + width - 20;
            int toggleY = y + 4;
            int toggleBg = bool.isEnabled() ? ThemeManager.getAccentColor() : ColorUtils.rgba(40, 40, 40, 255);
            RenderUtils.fill(context, toggleX, toggleY, toggleX + 14, toggleY + 10, toggleBg);
            RenderUtils.drawBorder(context, toggleX, toggleY, toggleX + 14, toggleY + 10, 1, ThemeManager.getBorderColor());
            if (bool.isEnabled()) {
                context.drawTextWithShadow(textRenderer, "✓", toggleX + 3, toggleY + 1, 0xFFFFFFFF);
            }
        } else if (setting instanceof NumberSetting num) {
            int sliderWidth = 80;
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
            context.drawTextWithShadow(textRenderer, valStr, sliderX - textRenderer.getWidth(valStr) - 4, y + 5, 0xFFAAAAAA);
        } else if (setting instanceof ModeSetting mode) {
            String modeStr = "< " + mode.getValue() + " >";
            int mw = textRenderer.getWidth(modeStr);
            context.drawTextWithShadow(textRenderer, modeStr, x + width - mw - 6, y + 5, ThemeManager.getAccentColor());
        } else if (setting instanceof KeybindSetting key) {
            String keyStr = (listeningSetting == key) ? "§e[...]" : "[" + key.getKeyName() + "]";
            int kw = textRenderer.getWidth(keyStr);
            context.drawTextWithShadow(textRenderer, keyStr, x + width - kw - 6, y + 5, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Tab switching
        Category[] categories = Category.values();
        int totalTabs = categories.length;
        int gap = 3;
        int tabWidth = (guiWidth - 20 - (totalTabs - 1) * gap) / totalTabs;
        int tabHeight = 18;
        int tabX = guiX + 10;
        int tabY = guiY + 26;

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
        int contentY = guiY + 48;
        int contentWidth = guiWidth - 20;
        int contentHeight = guiHeight - 58;

        List<Module> categoryModules = ModuleManager.getModulesByCategory(currentCategory).stream()
                .filter(m -> searchQuery.isEmpty() || m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();

        int modY = contentY + 6 - scrollOffset;

        for (Module module : categoryModules) {
            int cardX = contentX + 6;
            int cardW = contentWidth - 12;

            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= modY && mouseY <= modY + 22) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    module.toggle();
                    return true;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    module.setExpanded(!module.isExpanded());
                    return true;
                }
            }

            modY += 26;

            if (module.isExpanded()) {
                for (Setting<?> setting : module.getSettings()) {
                    int setX = contentX + 16;
                    int setW = contentWidth - 32;

                    if (mouseX >= setX && mouseX <= setX + setW && mouseY >= modY && mouseY <= modY + 18) {
                        if (setting instanceof BooleanSetting bool) {
                            bool.toggle();
                            return true;
                        } else if (setting instanceof ModeSetting mode) {
                            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) mode.cycle();
                            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) mode.cycleBack();
                            return true;
                        } else if (setting instanceof KeybindSetting key) {
                            listeningSetting = key;
                            return true;
                        } else if (setting instanceof NumberSetting num) {
                            draggingSlider = num;
                            updateSliderValue((int) mouseX);
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
        scrollOffset = Math.max(0, scrollOffset - (int) (verticalAmount * 16));
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

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (listeningSetting == null && chr >= 32 && chr <= 126) {
            searchQuery += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void updateSliderValue(int mouseX) {
        if (draggingSlider == null) return;
        int contentX = guiX + 10;
        int contentWidth = guiWidth - 20;
        int setW = contentWidth - 32;
        int sliderWidth = 80;
        int sliderX = contentX + 16 + setW - sliderWidth - 6;

        double percent = (double) (mouseX - sliderX) / (double) sliderWidth;
        percent = Math.max(0.0, Math.min(1.0, percent));
        double value = draggingSlider.getMin() + (percent * (draggingSlider.getMax() - draggingSlider.getMin()));
        draggingSlider.setValue(value);
    }
}
