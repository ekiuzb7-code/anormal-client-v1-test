package com.anormal.client.gui;

import com.anormal.client.actionwheel.ActionWheelConfig;
import com.anormal.client.actionwheel.WheelAction;
import com.anormal.client.module.Module;
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

/**
 * In-game visual editor for the Action Wheel.
 * Allows customizing labels, commands, modules, icons, colors, and presets.
 */
public class ActionWheelEditorScreen extends Screen {

    private final Module parentModule;
    private int selectedIndex = 0;
    private int focusedField = 0; // 0 none, 1 label, 2 value

    public ActionWheelEditorScreen(Module parentModule) {
        super(Text.literal("Action Wheel Editor"));
        this.parentModule = parentModule;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int winW = Math.min(500, width - 20);
        int winH = Math.min(290, height - 20);
        int winX = (width - winW) / 2;
        int winY = (height - winH) / 2;

        ThemeManager.renderWindow(context, winX, winY, winW, winH, "Action Wheel Customizer");

        List<WheelAction> actions = ActionWheelConfig.getActions();
        if (selectedIndex >= actions.size()) selectedIndex = Math.max(0, actions.size() - 1);

        // 1. Left List: Slots
        int listX = winX + 10;
        int listY = winY + 28;
        int listW = 180;
        int listH = winH - 65;

        RenderUtils.fill(context, listX, listY, listX + listW, listY + listH, ColorUtils.rgba(14, 16, 22, 180));
        RenderUtils.drawBorder(context, listX, listY, listX + listW, listY + listH, 1, ThemeManager.getBorderColor());

        int rowY = listY + 4;
        for (int i = 0; i < actions.size(); i++) {
            WheelAction act = actions.get(i);
            boolean sel = (i == selectedIndex);
            boolean hov = mouseX >= listX + 4 && mouseX <= listX + listW - 4 && mouseY >= rowY && mouseY <= rowY + 18;

            int rowBg = sel ? ThemeManager.getAccentColor() : (hov ? ColorUtils.rgba(30, 35, 48, 200) : ColorUtils.rgba(20, 24, 32, 150));
            RenderUtils.fill(context, listX + 4, rowY, listX + listW - 4, rowY + 18, rowBg);

            String text = (i + 1) + ". " + act.getIcon() + " " + act.getLabel();
            RenderUtils.drawText(context, textRenderer, text, listX + 8, rowY + 5, sel ? 0xFFFFFFFF : 0xFFDDDDDD, true);

            // Type badge
            String tBadge = act.getType().getDisplay().substring(0, 1);
            RenderUtils.drawText(context, textRenderer, tBadge, listX + listW - 16, rowY + 5, sel ? 0xFFEEEEEE : 0xFF888888, true);

            rowY += 20;
            if (rowY + 18 > listY + listH - 22) break; // limit visible
        }

        // Add / Remove buttons under list
        int btnY = listY + listH - 20;
        int addW = 75, delW = 75;
        boolean addHov = mouseX >= listX + 4 && mouseX <= listX + 4 + addW && mouseY >= btnY && mouseY <= btnY + 16;
        RenderUtils.fill(context, listX + 4, btnY, listX + 4 + addW, btnY + 16, addHov ? ThemeManager.getAccentColor() : ColorUtils.rgba(25, 30, 42, 220));
        RenderUtils.drawBorder(context, listX + 4, btnY, listX + 4 + addW, btnY + 16, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "+ Add Slot", listX + 14, btnY + 4, 0xFFFFFFFF, true);

        boolean delHov = mouseX >= listX + listW - delW - 4 && mouseX <= listX + listW - 4 && mouseY >= btnY && mouseY <= btnY + 16;
        RenderUtils.fill(context, listX + listW - delW - 4, btnY, listX + listW - 4, btnY + 16, delHov ? ColorUtils.rgba(200, 50, 50, 220) : ColorUtils.rgba(35, 20, 20, 220));
        RenderUtils.drawBorder(context, listX + listW - delW - 4, btnY, listX + listW - 4, btnY + 16, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "- Delete", listX + listW - delW + 12, btnY + 4, 0xFFFFFFFF, true);

        // 2. Right Inspector: Edit selected slot
        int inspX = listX + listW + 10;
        int inspY = listY;
        int inspW = winW - listW - 30;
        int inspH = listH;

        RenderUtils.fill(context, inspX, inspY, inspX + inspW, inspY + inspH, ColorUtils.rgba(14, 16, 22, 180));
        RenderUtils.drawBorder(context, inspX, inspY, inspX + inspW, inspY + inspH, 1, ThemeManager.getBorderColor());

        if (selectedIndex >= 0 && selectedIndex < actions.size()) {
            WheelAction current = actions.get(selectedIndex);

            int ey = inspY + 10;

            // Header
            RenderUtils.drawText(context, textRenderer, "§6Slot #" + (selectedIndex + 1) + " Properties", inspX + 10, ey, 0xFFFFFFFF, true);
            ey += 18;

            // Field 1: Label
            RenderUtils.drawText(context, textRenderer, "§7Button Label:", inspX + 10, ey, 0xFFAAAAAA, true);
            ey += 12;
            int boxW = inspW - 20;
            boolean f1 = (focusedField == 1);
            RenderUtils.fill(context, inspX + 10, ey, inspX + 10 + boxW, ey + 18, ColorUtils.rgba(20, 24, 32, 220));
            RenderUtils.drawBorder(context, inspX + 10, ey, inspX + 10 + boxW, ey + 18, 1, f1 ? ThemeManager.getAccentColor() : ColorUtils.rgba(50, 55, 75, 200));
            String lblDisplay = current.getLabel() + (f1 ? "§6|" : "");
            RenderUtils.drawText(context, textRenderer, lblDisplay, inspX + 15, ey + 5, 0xFFFFFFFF, true);
            ey += 24;

            // Field 2: Type
            RenderUtils.drawText(context, textRenderer, "§7Action Type:", inspX + 10, ey, 0xFFAAAAAA, true);
            ey += 12;
            boolean typeHov = mouseX >= inspX + 10 && mouseX <= inspX + 10 + boxW && mouseY >= ey && mouseY <= ey + 18;
            RenderUtils.fill(context, inspX + 10, ey, inspX + 10 + boxW, ey + 18, typeHov ? ColorUtils.rgba(35, 42, 60, 220) : ColorUtils.rgba(20, 24, 32, 220));
            RenderUtils.drawBorder(context, inspX + 10, ey, inspX + 10 + boxW, ey + 18, 1, ThemeManager.getBorderColor());
            String typeStr = "Type: §a" + current.getType().getDisplay() + " §7(Click to change)";
            RenderUtils.drawText(context, textRenderer, typeStr, inspX + 15, ey + 5, 0xFFFFFFFF, true);
            ey += 24;

            // Field 3: Command / Value
            String valPrompt = (current.getType() == WheelAction.Type.COMMAND) ? "Chat Command (e.g. /spawn, /rtp):"
                    : (current.getType() == WheelAction.Type.MODULE ? "Module Name (e.g. XRay, ChunkFinder):" : "Chat Message:");
            RenderUtils.drawText(context, textRenderer, "§7" + valPrompt, inspX + 10, ey, 0xFFAAAAAA, true);
            ey += 12;
            boolean f2 = (focusedField == 2);
            RenderUtils.fill(context, inspX + 10, ey, inspX + 10 + boxW, ey + 18, ColorUtils.rgba(20, 24, 32, 220));
            RenderUtils.drawBorder(context, inspX + 10, ey, inspX + 10 + boxW, ey + 18, 1, f2 ? ThemeManager.getAccentColor() : ColorUtils.rgba(50, 55, 75, 200));
            String valDisplay = current.getValue() + (f2 ? "§6|" : "");
            RenderUtils.drawText(context, textRenderer, valDisplay, inspX + 15, ey + 5, 0xFFFFFFFF, true);
            ey += 24;

            // Field 4: Icon & Color cycle
            boolean iconHov = mouseX >= inspX + 10 && mouseX <= inspX + 80 && mouseY >= ey && mouseY <= ey + 18;
            RenderUtils.fill(context, inspX + 10, ey, inspX + 80, ey + 18, iconHov ? ThemeManager.getAccentColor() : ColorUtils.rgba(20, 24, 32, 220));
            RenderUtils.drawBorder(context, inspX + 10, ey, inspX + 80, ey + 18, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, textRenderer, "Icon: " + current.getIcon(), inspX + 16, ey + 5, 0xFFFFFFFF, true);

            boolean colHov = mouseX >= inspX + 90 && mouseX <= inspX + boxW && mouseY >= ey && mouseY <= ey + 18;
            RenderUtils.fill(context, inspX + 90, ey, inspX + boxW, ey + 18, current.getColor());
            RenderUtils.drawBorder(context, inspX + 90, ey, inspX + boxW, ey + 18, 1, 0xFFFFFFFF);
            RenderUtils.drawText(context, textRenderer, "Color (Click to cycle)", inspX + 100, ey + 5, 0xFF000000, false);
        }

        // 3. Bottom Toolbar: Presets & Apply
        int barY = winY + winH - 28;
        drawPresetBtn(context, winX + 10, barY, 80, "Universal", mouseX, mouseY);
        drawPresetBtn(context, winX + 95, barY, 80, "DonutSMP", mouseX, mouseY);
        drawPresetBtn(context, winX + 180, barY, 80, "Lifesteal", mouseX, mouseY);

        // Save & Return button
        int saveW = 110;
        int saveX = winX + winW - saveW - 10;
        boolean saveHov = mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= barY && mouseY <= barY + 18;
        RenderUtils.fill(context, saveX, barY, saveX + saveW, barY + 18, saveHov ? ThemeManager.getAccentColor() : ColorUtils.rgba(20, 140, 60, 220));
        RenderUtils.drawBorder(context, saveX, barY, saveX + saveW, barY + 18, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "✔ Save & Close", saveX + 15, barY + 5, 0xFFFFFFFF, true);
    }

    private void drawPresetBtn(DrawContext context, int x, int y, int w, String text, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + 18;
        RenderUtils.fill(context, x, y, x + w, y + 18, hov ? ThemeManager.getAccentColor() : ColorUtils.rgba(20, 24, 32, 220));
        RenderUtils.drawBorder(context, x, y, x + w, y + 18, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, text, x + (w - textRenderer.getWidth(text)) / 2, y + 5, 0xFFDDDDDD, true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleMouseClick((int) click.x(), (int) click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleMouseClick((int) mouseX, (int) mouseY, button);
    }

    private boolean handleMouseClick(int mouseX, int mouseY, int button) {
        int winW = Math.min(500, width - 20);
        int winH = Math.min(290, height - 20);
        int winX = (width - winW) / 2;
        int winY = (height - winH) / 2;

        List<WheelAction> actions = ActionWheelConfig.getActions();

        int listX = winX + 10;
        int listY = winY + 28;
        int listW = 180;
        int listH = winH - 65;

        // Click slots list
        int rowY = listY + 4;
        for (int i = 0; i < actions.size(); i++) {
            if (mouseX >= listX + 4 && mouseX <= listX + listW - 4 && mouseY >= rowY && mouseY <= rowY + 18) {
                selectedIndex = i;
                focusedField = 0;
                return true;
            }
            rowY += 20;
            if (rowY + 18 > listY + listH - 22) break;
        }

        // Add Slot
        int btnY = listY + listH - 20;
        int addW = 75, delW = 75;
        if (mouseX >= listX + 4 && mouseX <= listX + 4 + addW && mouseY >= btnY && mouseY <= btnY + 16) {
            actions.add(new WheelAction("New Slot", WheelAction.Type.COMMAND, "/help", "⭐", ColorUtils.rgba(0, 200, 255, 255)));
            selectedIndex = actions.size() - 1;
            ActionWheelConfig.save();
            return true;
        }

        // Delete Slot
        if (mouseX >= listX + listW - delW - 4 && mouseX <= listX + listW - 4 && mouseY >= btnY && mouseY <= btnY + 16) {
            if (actions.size() > 1 && selectedIndex >= 0 && selectedIndex < actions.size()) {
                actions.remove(selectedIndex);
                if (selectedIndex >= actions.size()) selectedIndex = actions.size() - 1;
                ActionWheelConfig.save();
            }
            return true;
        }

        // Right Inspector clicks
        int inspX = listX + listW + 10;
        int inspY = listY;
        int inspW = winW - listW - 30;
        int boxW = inspW - 20;

        if (selectedIndex >= 0 && selectedIndex < actions.size()) {
            WheelAction current = actions.get(selectedIndex);
            int ey = inspY + 28;

            // Click Field 1: Label
            ey += 12;
            if (mouseX >= inspX + 10 && mouseX <= inspX + 10 + boxW && mouseY >= ey && mouseY <= ey + 18) {
                focusedField = 1;
                return true;
            }
            ey += 24;

            // Click Field 2: Type
            ey += 12;
            if (mouseX >= inspX + 10 && mouseX <= inspX + 10 + boxW && mouseY >= ey && mouseY <= ey + 18) {
                WheelAction.Type[] types = WheelAction.Type.values();
                int next = (current.getType().ordinal() + 1) % types.length;
                current.setType(types[next]);
                ActionWheelConfig.save();
                return true;
            }
            ey += 24;

            // Click Field 3: Value
            ey += 12;
            if (mouseX >= inspX + 10 && mouseX <= inspX + 10 + boxW && mouseY >= ey && mouseY <= ey + 18) {
                focusedField = 2;
                return true;
            }
            ey += 24;

            // Click Icon cycle
            if (mouseX >= inspX + 10 && mouseX <= inspX + 80 && mouseY >= ey && mouseY <= ey + 18) {
                String[] icons = {"⚡", "🏠", "🏰", "🔍", "💎", "👁", "💰", "⚔", "🛡", "🔮", "📦", "💵", "⭐", "📢"};
                int curIdx = 0;
                for (int i = 0; i < icons.length; i++) {
                    if (icons[i].equals(current.getIcon())) curIdx = i;
                }
                current.setIcon(icons[(curIdx + 1) % icons.length]);
                ActionWheelConfig.save();
                return true;
            }

            // Click Color cycle
            if (mouseX >= inspX + 90 && mouseX <= inspX + boxW && mouseY >= ey && mouseY <= ey + 18) {
                int[] cols = {
                        ColorUtils.rgba(0, 230, 255, 255),
                        ColorUtils.rgba(255, 215, 0, 255),
                        ColorUtils.rgba(80, 240, 120, 255),
                        ColorUtils.rgba(180, 70, 255, 255),
                        ColorUtils.rgba(255, 80, 220, 255),
                        ColorUtils.rgba(255, 140, 0, 255),
                        ColorUtils.rgba(255, 60, 60, 255)
                };
                int curIdx = 0;
                for (int i = 0; i < cols.length; i++) {
                    if (cols[i] == current.getColor()) curIdx = i;
                }
                current.setColor(cols[(curIdx + 1) % cols.length]);
                ActionWheelConfig.save();
                return true;
            }
        }

        // Presets & Save
        int barY = winY + winH - 28;
        if (mouseY >= barY && mouseY <= barY + 18) {
            if (mouseX >= winX + 10 && mouseX <= winX + 90) {
                ActionWheelConfig.loadUniversalPreset();
                ActionWheelConfig.save();
                return true;
            }
            if (mouseX >= winX + 95 && mouseX <= winX + 175) {
                ActionWheelConfig.loadDonutSMPPreset();
                ActionWheelConfig.save();
                return true;
            }
            if (mouseX >= winX + 180 && mouseX <= winX + 260) {
                ActionWheelConfig.loadLifestealPreset();
                ActionWheelConfig.save();
                return true;
            }
            int saveW = 110;
            int saveX = winX + winW - saveW - 10;
            if (mouseX >= saveX && mouseX <= saveX + saveW) {
                ActionWheelConfig.save();
                close();
                return true;
            }
        }

        focusedField = 0;
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        List<WheelAction> actions = ActionWheelConfig.getActions();
        if (selectedIndex >= 0 && selectedIndex < actions.size()) {
            WheelAction current = actions.get(selectedIndex);

            if (focusedField == 1) { // Editing Label
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    String s = current.getLabel();
                    if (!s.isEmpty()) current.setLabel(s.substring(0, s.length() - 1));
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    focusedField = 0;
                    ActionWheelConfig.save();
                    return true;
                }
            } else if (focusedField == 2) { // Editing Value
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    String s = current.getValue();
                    if (!s.isEmpty()) current.setValue(s.substring(0, s.length() - 1));
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    focusedField = 0;
                    ActionWheelConfig.save();
                    return true;
                }
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ActionWheelConfig.save();
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String s = input.asString();
        List<WheelAction> actions = ActionWheelConfig.getActions();
        if (selectedIndex >= 0 && selectedIndex < actions.size() && s.length() == 1) {
            WheelAction current = actions.get(selectedIndex);
            char c = s.charAt(0);
            if (c >= 32 && c <= 126) {
                if (focusedField == 1) {
                    current.setLabel(current.getLabel() + c);
                    return true;
                } else if (focusedField == 2) {
                    current.setValue(current.getValue() + c);
                    return true;
                }
            }
        }
        return super.charTyped(input);
    }
}
