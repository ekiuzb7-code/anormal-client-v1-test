package com.anormal.client.gui;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.*;
import com.anormal.client.theme.Theme;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ClickGuiScreen extends Screen {
    private Category currentCategory = Category.COMBAT;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private KeybindSetting listeningSetting = null;
    private NumberSetting draggingSlider = null;
    private ColorSetting editingColor = null;
    private int editDrag = 0; // 0 none, 1 R, 2 G, 3 B

    private int scrollOffset = 0;
    private com.anormal.client.setting.ModeSetting expandedMode = null;

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

        // 2. Main Window Frame (input via mouseClicked/mouseReleased events only)

        // 3. Main Window Frame
        ThemeManager.renderWindow(context, guiX, guiY, guiWidth, guiHeight, "Anormal Client");

        // 4. Top Header: ANORMAL wordmark fully ABOVE the divider line.
        try {
            context.drawTexturedQuad(
                    com.anormal.client.module.impl.legit.Watermark.guiLogo(),
                    guiX + 12, guiY + 0, guiX + 108, guiY + 24, 0.0f, 1.0f, 0.0f, 1.0f);
        } catch (Throwable ignored) {
            RenderUtils.drawText(context, textRenderer, "§lANORMAL", guiX + 12, guiY + 6, 0xFFFFFFFF, true);
        }

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

        // Calculate max scroll height (visible settings only)
        int totalContentHeight = 6;
        for (Module m : categoryModules) {
            totalContentHeight += 26;
            if (m.isExpanded()) {
                for (Setting<?> s : m.getSettings()) {
                    if (s.isVisible()) totalContentHeight += 20;
                    if (s == expandedMode && s instanceof com.anormal.client.setting.ModeSetting em) {
                        totalContentHeight += em.getModes().size() * 16;
                    }
                }
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
                    if (!setting.isVisible()) continue;
                    renderSetting(context, setting, contentX + 16, modY, contentWidth - 36, mouseX, mouseY);
                    modY += 20;
                    // Expanded option list under a mode row: click an option to pick it
                    if (setting == expandedMode && setting instanceof com.anormal.client.setting.ModeSetting em) {
                        for (String opt : em.getModes()) {
                            boolean sel = opt.equals(em.getValue());
                            boolean hov = mouseX >= contentX + 26 && mouseX <= contentX + 6 + contentWidth - 26
                                    && mouseY >= modY && mouseY <= modY + 14;
                            RenderUtils.fill(context, contentX + 26, modY, contentX + 6 + contentWidth - 26, modY + 14,
                                    sel ? ThemeManager.getAccentColor() : (hov ? ColorUtils.rgba(35, 40, 55, 220) : ColorUtils.rgba(16, 18, 24, 220)));
                            RenderUtils.drawText(context, textRenderer, (sel ? "● " : "○ ") + opt,
                                    contentX + 32, modY + 3, sel ? 0xFFFFFFFF : 0xFFBBBBBB, true);
                            modY += 16;
                        }
                    }
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

        // Free RGB picker popup (any ColorSetting, right-click the color box)
        renderPicker(context, mouseX);

        super.render(context, mouseX, mouseY, delta);
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
                    if (!setting.isVisible()) continue;
                    int setX = contentX + 16;
                    int setW = contentWidth - 36;

                    if (mouseX >= setX && mouseX <= setX + setW && mouseY >= modY && mouseY <= modY + 18) {
                        if (setting instanceof BooleanSetting bool) {
                            bool.toggle();
                            return;
                        } else if (setting instanceof ModeSetting mode) {
                            // Click toggles the option list (no cycling arrows)
                            expandedMode = (expandedMode == mode) ? null : mode;
                            return;
                        } else if (setting instanceof KeybindSetting key) {
                            listeningSetting = key;
                            return;
                        } else if (setting instanceof NumberSetting num) {
                            draggingSlider = num;
                            updateSliderValue(mouseX, contentX, contentWidth);
                            return;
                        } else if (setting instanceof ColorSetting color) {
                            if (button == 1) {
                                // Right-click: open free RGB picker
                                editingColor = color;
                                editDrag = 0;
                                return;
                            }
                            int[] palette = {
                                ColorUtils.rgba(255, 120, 0, 255),  // Orange
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
                    // Option rows under an expanded mode: pick directly
                    if (setting == expandedMode && setting instanceof ModeSetting em) {
                        for (String opt : em.getModes()) {
                            if (mouseX >= setX + 10 && mouseX <= setX + setW - 10 && mouseY >= modY && mouseY <= modY + 14) {
                                try {
                                    em.setMode(opt);
                                } catch (Throwable ignored) {}
                                expandedMode = null;
                                return;
                            }
                            modY += 16;
                        }
                    }
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
        String expandText = module.isExpanded() ? "-" : "+";
        RenderUtils.drawText(context, textRenderer, expandText, x + width - 14, y + 7, ThemeManager.getAccentColor(), true);
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
            String modeStr = mode.getValue() + (setting == expandedMode ? "  [-]" : "  [+]");
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

    // 1.21.11 input API: Screen dispatches mouseClicked(Click, boolean).
    // Old (double,double,int) overload is kept as plain logic — new overload delegates to it.
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleGuiClick(click.x(), click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleGuiClick(mouseX, mouseY, button);
    }

    private boolean handleGuiClick(double mouseX, double mouseY, int button) {
        // Color picker popup eats all clicks while open
        if (editingColor != null && handlePickerClick((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        // Binding mode: any mouse button becomes the bind — EXCEPT left click (never bindable)
        if (listeningSetting != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                listeningSetting = null; // left click cancels instead of binding
                return true;
            }
            listeningSetting.setValue(KeybindSetting.mouseCode(button));
            listeningSetting = null;
            return true;
        }
        int guiWidth = Math.min(520, width - 20);
        int guiHeight = Math.min(300, height - 20);
        int guiX = (width - guiWidth) / 2;
        int guiY = (height - guiHeight) / 2;

        processClick((int) mouseX, (int) mouseY, button, guiX, guiY, guiWidth, guiHeight);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = null;
        editDrag = 0;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, scrollOffset - (int) (verticalAmount * 22));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int modifiers = input.modifiers();
        // Picker open: ESC closes it first
        if (editingColor != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editingColor = null;
            return true;
        }
        // 1. Keybind listening has top priority: ANY key (incl. mouse handled in mouseClicked) binds here
        if (listeningSetting != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                    || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                listeningSetting.setValue(GLFW.GLFW_KEY_UNKNOWN);
            } else {
                listeningSetting.setValue(keyCode);
            }
            listeningSetting = null;
            return true;
        }

        // 2. Search field: control keys only — printable chars arrive via charTyped
        // (covers ALL keyboard layouts: EN/RU/UZ; manual A-Z mapping broke non-Latin layouts)
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            // Any other key while searching: swallow it so hotkeys don't leak through,
            // printable result comes via charTyped right after.
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (listeningSetting != null) return true; // don't leak typed chars into search while binding
        String s = input.asString();
        if (searchFocused && s.length() == 1) {
            char chr = s.charAt(0);
            if (chr >= 32 && chr <= 126) {
                searchQuery += chr;
                return true;
            }
        }
        return super.charTyped(input);
    }

    private boolean handlePickerClick(int mouseX, int mouseY, int button) {
        int pw = 180, ph = 112;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) return false;
        if (button != 0) {
            // Right-click anywhere on picker closes it
            if (button == 1) editingColor = null;
            return true;
        }
        int trackX = px + 34, trackW = 110;
        int[][] rows = {{py + 38, 1}, {py + 54, 2}, {py + 70, 3}};
        for (int[] row : rows) {
            if (mouseY >= row[0] - 2 && mouseY <= row[0] + 8 && mouseX >= trackX - 4 && mouseX <= trackX + trackW + 4) {
                editDrag = row[1];
                applyPickerDrag(mouseX);
                return true;
            }
        }
        // Rainbow toggle
        if (mouseX >= px + 8 && mouseX <= px + 100 && mouseY >= py + 86 && mouseY <= py + 100) {
            editingColor.setRainbow(!editingColor.isRainbow());
            return true;
        }
        // Close button
        if (mouseX >= px + pw - 30 && mouseX <= px + pw - 6 && mouseY >= py + 4 && mouseY <= py + 16) {
            editingColor = null;
            return true;
        }
        return true;
    }

    private void applyPickerDrag(int mouseX) {
        if (editingColor == null || editDrag < 1 || editDrag > 3) return;
        int pw = 180;
        int px = (width - pw) / 2;
        int trackX = px + 34, trackW = 110;
        double percent = Math.max(0.0, Math.min(1.0, (double) (mouseX - trackX) / trackW));
        int v = (int) Math.round(percent * 255);
        int r = editingColor.getRed(), g = editingColor.getGreen(), b = editingColor.getBlue();
        if (editDrag == 1) r = v;
        else if (editDrag == 2) g = v;
        else b = v;
        editingColor.setRainbow(false);
        editingColor.setValue(ColorUtils.rgba(r, g, b, 255));
    }

    private void renderPicker(DrawContext context, int mouseX) {
        if (editingColor == null) return;
        if (editDrag != 0) applyPickerDrag(mouseX);
        int pw = 180, ph = 112;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        RenderUtils.fill(context, px, py, px + pw, py + ph, ColorUtils.rgba(12, 14, 20, 245));
        RenderUtils.drawBorder(context, px, py, px + pw, py + ph, 1, ThemeManager.getAccentColor());
        RenderUtils.drawText(context, textRenderer, "§eColor Picker", px + 8, py + 5, 0xFFFFFFFF, true);
        RenderUtils.drawText(context, textRenderer, "§8[X]", px + pw - 30, py + 5, 0xFFAAAAAA, true);
        // Preview
        RenderUtils.fill(context, px + 8, py + 18, px + pw - 8, py + 30, editingColor.getValue());
        RenderUtils.drawBorder(context, px + 8, py + 18, px + pw - 8, py + 30, 1, 0xFFFFFFFF);
        String hex = String.format("#%06X", 0xFFFFFF & editingColor.getValue());
        RenderUtils.drawText(context, textRenderer, hex, px + pw - 8 - textRenderer.getWidth(hex), py + 31, 0xFFAAAAAA, true);
        // RGB sliders
        int trackX = px + 34, trackW = 110;
        int[] vals = {editingColor.getRed(), editingColor.getGreen(), editingColor.getBlue()};
        String[] names = {"R", "G", "B"};
        int[] cols = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF};
        for (int i = 0; i < 3; i++) {
            int ry = py + 38 + i * 16;
            RenderUtils.drawText(context, textRenderer, names[i], px + 8, ry - 1, cols[i], true);
            RenderUtils.fill(context, trackX, ry, trackX + trackW, ry + 6, ColorUtils.rgba(30, 30, 35, 255));
            int fill = (int) (trackW * vals[i] / 255.0);
            RenderUtils.fill(context, trackX, ry, trackX + fill, ry + 6, cols[i]);
            String vs = String.valueOf(vals[i]);
            RenderUtils.drawText(context, textRenderer, vs, trackX + trackW + 4, ry - 1, 0xFFAAAAAA, true);
        }
        // Rainbow + hint
        int rbY = py + 88;
        int rbBg = editingColor.isRainbow() ? ThemeManager.getAccentColor() : ColorUtils.rgba(40, 40, 40, 255);
        RenderUtils.fill(context, px + 8, rbY, px + 22, rbY + 10, rbBg);
        RenderUtils.drawBorder(context, px + 8, rbY, px + 22, rbY + 10, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "Rainbow", px + 26, rbY + 1, 0xFFDDDDDD, true);
        RenderUtils.drawText(context, textRenderer, "§8L:slide R:close", px + 8, py + 101, 0xFF777777, true);
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
