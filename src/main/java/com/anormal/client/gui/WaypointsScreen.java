package com.anormal.client.gui;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.Waypoints;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Full waypoint manager: every saved waypoint is listed with its name,
 * coordinates, dimension and distance, and can be renamed, deleted, toggled,
 * recoloured or copied to the clipboard straight from the list.
 */
public class WaypointsScreen extends Screen {
    private final Screen parent;

    private static final int ROW_H = 22;
    private int scroll = 0;
    private int maxScroll = 0;

    private int selected = -1;
    private int renameIdx = -1;
    private final StringBuilder renameBuf = new StringBuilder();

    // Row button hit boxes, rebuilt each frame.
    private int btnToggleX, btnRenameX, btnCopyX, btnDeleteX, btnW, btnY, btnH;

    public WaypointsScreen(Screen parent) {
        super(Text.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Waypoints module() {
        return ModuleManager.getModule(Waypoints.class);
    }

    private int panelW() {
        return Math.min(430, width - 20);
    }

    private int panelH() {
        return Math.min(280, height - 20);
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int panelY() {
        return (height - panelH()) / 2;
    }

    private int listX() {
        return panelX() + 8;
    }

    private int listY() {
        return panelY() + 34;
    }

    private int listW() {
        return panelW() - 16;
    }

    private int listH() {
        return panelH() - 42;
    }

    private int addBtnX() {
        return panelX() + panelW() - 78;
    }

    private int addBtnY() {
        return panelY() + 8;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        Waypoints wp = module();
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();

        ThemeManager.renderWindow(context, px, py, pw, ph, "Waypoints");

        List<Waypoints.Waypoint> pts = wp != null ? wp.getPoints() : List.of();

        RenderUtils.drawText(context, textRenderer, "§7" + pts.size() + " waypoint(s)", px + 10, py + 14, 0xFFAAAAAA, true);

        // "+ Add Here" button
        boolean addHover = mouseX >= addBtnX() && mouseX <= addBtnX() + 70
                && mouseY >= addBtnY() && mouseY <= addBtnY() + 16;
        RenderUtils.fill(context, addBtnX(), addBtnY(), addBtnX() + 70, addBtnY() + 16,
                addHover ? ThemeManager.getAccentColor() : ColorUtils.rgba(20, 24, 30, 200));
        RenderUtils.drawBorder(context, addBtnX(), addBtnY(), addBtnX() + 70, addBtnY() + 16, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "+ Add Here", addBtnX() + 6, addBtnY() + 4,
                addHover ? 0xFFFFFFFF : 0xFFDDDDDD, true);

        // List background
        int lx = listX(), ly = listY(), lw = listW(), lh = listH();
        RenderUtils.fill(context, lx, ly, lx + lw, ly + lh, ColorUtils.rgba(10, 12, 16, 160));
        RenderUtils.drawBorder(context, lx, ly, lx + lw, ly + lh, 1, ThemeManager.getBorderColor());

        maxScroll = Math.max(0, pts.size() * ROW_H - lh + 4);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        try {
            context.enableScissor(lx + 1, ly + 1, lx + lw - 1, ly + lh - 1);
        } catch (Throwable ignored) {
        }

        int rowY = ly + 2 - scroll;
        for (int i = 0; i < pts.size(); i++) {
            Waypoints.Waypoint w = pts.get(i);
            drawRow(context, wp, w, i, lx + 2, rowY, lw - 4, mouseX, mouseY);
            rowY += ROW_H;
        }

        try {
            context.disableScissor();
        } catch (Throwable ignored) {
        }

        if (pts.isEmpty()) {
            RenderUtils.drawText(context, textRenderer, "§7No waypoints yet - press \"+ Add Here\"",
                    lx + 10, ly + 10, 0xFF999999, true);
        }

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = lx + lw - 5;
            RenderUtils.fill(context, sbX, ly + 2, sbX + 3, ly + lh - 2, ColorUtils.rgba(20, 20, 25, 150));
            float ratio = (float) lh / (float) (pts.size() * ROW_H);
            int thumbH = Math.max(14, (int) ((lh - 4) * ratio));
            int thumbY = ly + 2 + (int) ((lh - 4 - thumbH) * ((float) scroll / maxScroll));
            RenderUtils.fill(context, sbX, thumbY, sbX + 3, thumbY + thumbH, ThemeManager.getAccentColor());
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRow(DrawContext context, Waypoints wp, Waypoints.Waypoint w, int i,
                         int x, int y, int wdt, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + wdt && mouseY >= y && mouseY <= y + ROW_H - 2;
        boolean isSel = i == selected;

        int bg = isSel ? ColorUtils.rgba(40, 46, 60, 200)
                : (hovered ? ColorUtils.rgba(28, 32, 42, 190) : ColorUtils.rgba(16, 18, 24, 150));
        RenderUtils.fill(context, x, y, x + wdt, y + ROW_H - 2, bg);

        // Colour swatch
        RenderUtils.fill(context, x + 3, y + 4, x + 9, y + ROW_H - 6, w.color);
        RenderUtils.drawBorder(context, x + 3, y + 4, x + 9, y + ROW_H - 6, 1, ColorUtils.rgba(0, 0, 0, 160));

        // Name + coords
        String name = (w.on ? "§f" : "§8") + (i + 1) + ". " + w.name;
        RenderUtils.drawText(context, textRenderer, name, x + 13, y + 3, 0xFFFFFFFF, true);
        String sub = String.format("§7%.0f / %.0f / %.0f  §8%s", w.x, w.y, w.z,
                w.dim == null || w.dim.isEmpty() ? "?" : w.dim);
        RenderUtils.drawText(context, textRenderer, sub, x + 13, y + 12, 0xFF888888, true);

        // Right-aligned buttons
        btnW = 15;
        btnH = 14;
        btnY = y + 2;
        btnDeleteX = x + wdt - btnW - 3;
        btnCopyX = btnDeleteX - btnW - 2;
        btnRenameX = btnCopyX - btnW - 2;
        btnToggleX = btnRenameX - btnW - 2;

        drawRowButton(context, btnToggleX, w.on ? "◉" : "○", w.on, mouseX, mouseY);
        drawRowButton(context, btnRenameX, "✎", false, mouseX, mouseY);
        drawRowButton(context, btnCopyX, "⎘", false, mouseX, mouseY);
        drawRowButton(context, btnDeleteX, "✖", false, mouseX, mouseY);

        // Inline rename editor
        if (renameIdx == i) {
            String edit = renameBuf + "§6|";
            int ex = x + 13;
            RenderUtils.fill(context, ex - 2, y + 1, x + wdt - btnW * 4 - 12, y + ROW_H - 3, ColorUtils.rgba(0, 0, 0, 220));
            RenderUtils.drawBorder(context, ex - 2, y + 1, x + wdt - btnW * 4 - 12, y + ROW_H - 3, 1, ThemeManager.getAccentColor());
            RenderUtils.drawText(context, textRenderer, edit, ex, y + 5, 0xFFFFFFFF, true);
        }
    }

    private void drawRowButton(DrawContext context, int bx, String label, boolean active, int mouseX, int mouseY) {
        boolean hovered = mouseX >= bx && mouseX <= bx + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        RenderUtils.fill(context, bx, btnY, bx + btnW, btnY + btnH,
                hovered ? ThemeManager.getAccentColor() : ColorUtils.rgba(24, 28, 36, 220));
        RenderUtils.drawBorder(context, bx, btnY, bx + btnW, btnY + btnH, 1, ThemeManager.getBorderColor());
        int tw = textRenderer.getWidth(label);
        RenderUtils.drawText(context, textRenderer, label, bx + (btnW - tw) / 2, btnY + 3,
                active ? 0xFF66FF66 : 0xFFDDDDDD, true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button);
    }

    private boolean handleClick(double mouseX, double mouseY, int button) {
        Waypoints wp = module();
        if (wp == null) return false;
        List<Waypoints.Waypoint> pts = wp.getPoints();

        // Add here
        if (mouseX >= addBtnX() && mouseX <= addBtnX() + 70
                && mouseY >= addBtnY() && mouseY <= addBtnY() + 16) {
            wp.addPointHere();
            selected = pts.size() - 1;
            return true;
        }

        int lx = listX(), ly = listY(), lw = listW(), lh = listH();
        if (mouseX < lx || mouseX > lx + lw || mouseY < ly || mouseY > ly + lh) {
            renameIdx = -1;
            return false;
        }

        int rowY = ly + 2 - scroll;
        for (int i = 0; i < pts.size(); i++) {
            if (mouseY >= rowY && mouseY <= rowY + ROW_H - 2) {
                if (button != 0) return true;

                if (mouseX >= btnDeleteX && mouseX <= btnDeleteX + btnW) {
                    wp.removePoint(i);
                    if (selected >= pts.size()) selected = pts.size() - 1;
                    renameIdx = -1;
                    return true;
                }
                if (mouseX >= btnCopyX && mouseX <= btnCopyX + btnW) {
                    try {
                        if (client != null) {
                            client.keyboard.setClipboard(String.format("%.0f %.0f %.0f", pts.get(i).x, pts.get(i).y, pts.get(i).z));
                        }
                    } catch (Throwable ignored) {
                    }
                    return true;
                }
                if (mouseX >= btnRenameX && mouseX <= btnRenameX + btnW) {
                    renameIdx = i;
                    renameBuf.setLength(0);
                    renameBuf.append(pts.get(i).name);
                    selected = i;
                    return true;
                }
                if (mouseX >= btnToggleX && mouseX <= btnToggleX + btnW) {
                    wp.togglePoint(i);
                    return true;
                }
                selected = i;
                return true;
            }
            rowY += ROW_H;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int step = (int) Math.round(verticalAmount * 22.0);
        if (step == 0) step = verticalAmount > 0 ? 1 : -1;
        scroll = Math.max(0, Math.min(maxScroll, scroll - step));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (renameIdx >= 0) {
            Waypoints wp = module();
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (wp != null) wp.renamePoint(renameIdx, renameBuf.toString().trim());
                renameIdx = -1;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                renameIdx = -1;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (renameBuf.length() > 0) renameBuf.setLength(renameBuf.length() - 1);
                return true;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (client != null) client.setScreen(parent);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (renameIdx >= 0) {
            try {
                String s = input.asString();
                if (s.length() == 1) {
                    char c = s.charAt(0);
                    if (c >= 32 && c <= 126 && renameBuf.length() < 20) renameBuf.append(c);
                }
            } catch (Throwable ignored) {
            }
            return true;
        }
        return super.charTyped(input);
    }
}
