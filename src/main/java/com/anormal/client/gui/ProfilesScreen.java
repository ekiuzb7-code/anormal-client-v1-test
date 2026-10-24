package com.anormal.client.gui;

import com.anormal.client.module.impl.client.Profiles;
import com.anormal.client.profiles.ProfileManager;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Profile browser: list on the left, name field and actions on the right.
 *
 * <p>Saving with a name that already exists overwrites it, which is the "load, tweak, save again"
 * workflow. Delete needs two clicks so a slip does not wipe a profile.</p>
 */
public class ProfilesScreen extends Screen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 272;
    private static final int HEADER_H = 22;
    private static final int PAD = 8;
    private static final int STATUS_H = 16;
    private static final int ROW_H = 24;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT);

    private final Profiles module;
    private final List<String> profiles = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();

    private TextFieldWidget nameField;
    private String selected;
    private int scroll;
    private String status = "Type a name and press Save";
    private boolean statusError;
    private String armedDelete;

    private int panelX;
    private int panelY;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int rightX;
    private int rightW;
    private int fieldX;
    private int fieldY;
    private int buttonTop;
    private int buttonW;
    private int buttonH;
    private int buttonGap;

    public ProfilesScreen(Profiles module) {
        super(Text.literal("Profiles"));
        this.module = module;
    }

    @Override
    protected void init() {
        layout();
        if (nameField == null) {
            nameField = new TextFieldWidget(textRenderer, fieldX, fieldY, rightW, 18, Text.literal("Profile name"));
            nameField.setMaxLength(48);
            nameField.setPlaceholder(Text.literal("Profile name"));
            addDrawableChild(nameField);
        }
        nameField.setX(fieldX);
        nameField.setY(fieldY);
        nameField.setWidth(rightW);
        nameField.setText(selected == null ? "" : selected);
        refresh();
    }

    /**
     * Single source of truth for every rectangle. Render and hit-testing both use these values, so
     * a resize can never leave the buttons drawn somewhere the clicks do not expect.
     */
    private void layout() {
        int w = Math.min(PANEL_W, Math.max(300, width - 16));
        int h = Math.min(PANEL_H, Math.max(200, height - 16));
        panelX = (width - w) / 2;
        panelY = (height - h) / 2;

        int gap = 8;
        int contentTop = panelY + HEADER_H + PAD;
        int contentBottom = panelY + h - STATUS_H - PAD;

        listX = panelX + PAD;
        listY = contentTop;
        listW = Math.max(120, (w - PAD * 3) / 2 - 6);
        listH = Math.max(60, contentBottom - contentTop);

        rightX = listX + listW + gap;
        rightW = panelX + w - PAD - rightX;
        fieldX = rightX;
        fieldY = contentTop;

        buttonTop = fieldY + 24;
        buttonW = Math.max(60, (rightW - 8) / 2);
        buttonH = 18;
        buttonGap = 4;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    private void refresh() {
        profiles.clear();
        profiles.addAll(ProfileManager.list());
        if (selected != null && !profiles.contains(selected)) selected = null;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, profiles.size() - visibleRows())));
    }

    private void save() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = selected == null ? "" : selected;
        String safe = ProfileManager.sanitize(name);
        if (safe.isEmpty()) {
            setStatus("Enter a profile name", true);
            return;
        }
        boolean overwrite = ProfileManager.exists(safe);
        String error = ProfileManager.save(safe);
        if (error != null) {
            setStatus(error, true);
            return;
        }
        selected = safe;
        nameField.setText(safe);
        refresh();
        setStatus(overwrite ? "Overwrote \"" + safe + "\"" : "Saved \"" + safe + "\"", false);
    }

    private void load() {
        String name = currentTarget();
        if (name == null) {
            setStatus("Select a profile first", true);
            return;
        }
        String error = ProfileManager.load(name);
        if (error != null) {
            setStatus(error, true);
            return;
        }
        setStatus("Loaded \"" + name + "\"", false);
    }

    private void delete() {
        String name = currentTarget();
        if (name == null) {
            setStatus("Select a profile first", true);
            return;
        }
        if (!name.equals(armedDelete)) {
            armedDelete = name;
            setStatus("Press Delete again to remove \"" + name + "\"", true);
            return;
        }
        armedDelete = null;
        String error = ProfileManager.delete(name);
        if (error != null) {
            setStatus(error, true);
            return;
        }
        if (name.equals(selected)) {
            selected = null;
            nameField.setText("");
        }
        refresh();
        setStatus("Deleted \"" + name + "\"", false);
    }

    private void openFolder() {
        try {
            Util.getOperatingSystem().open(ProfileManager.directory());
            setStatus("Opened " + ProfileManager.directory().getAbsolutePath(), false);
        } catch (Throwable t) {
            setStatus("Folder: " + ProfileManager.directory().getAbsolutePath(), false);
        }
    }

    /** Selected row, or the typed name when nothing is selected. */
    private String currentTarget() {
        if (selected != null) return selected;
        String typed = ProfileManager.sanitize(nameField.getText());
        return typed.isEmpty() ? null : typed;
    }

    private void setStatus(String text, boolean error) {
        status = text;
        statusError = error;
    }

    private int visibleRows() {
        return Math.max(1, (listH - 2) / ROW_H);
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layout();
        buttons.clear();

        int panelW = rightX + rightW + PAD - panelX;
        int panelH = Math.min(PANEL_H, Math.max(200, height - 16));

        RenderUtils.fill(context, 0, 0, width, height, ColorUtils.rgba(0, 0, 0, 150));
        RenderUtils.fill(context, panelX, panelY, panelX + panelW, panelY + panelH,
                ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, panelX, panelY, panelW, panelH, 1, ThemeManager.getBorderColor());
        RenderUtils.fill(context, panelX, panelY, panelX + panelW, panelY + HEADER_H, ThemeManager.getHeaderColor());
        RenderUtils.drawText(context, textRenderer, "§lPROFILES", panelX + PAD, panelY + 7,
                ThemeManager.getTextColor(true), true);
        String count = profiles.size() + " saved";
        RenderUtils.drawText(context, textRenderer, "§8" + count,
                panelX + panelW - textRenderer.getWidth(count) - PAD, panelY + 7, 0xFF777777, false);

        drawList(context, mouseX, mouseY);

        addButton(rightX, buttonTop, buttonW, buttonH, "Save", this::save, true);
        addButton(rightX + buttonW + buttonGap, buttonTop, buttonW, buttonH, "Load", this::load, false);
        addButton(rightX, buttonTop + buttonH + buttonGap, buttonW, buttonH, "Delete", this::delete, false);
        addButton(rightX + buttonW + buttonGap, buttonTop + buttonH + buttonGap, buttonW, buttonH,
                "Refresh", this::refresh, false);
        addButton(rightX, buttonTop + (buttonH + buttonGap) * 2, rightW, buttonH,
                "Open profiles folder", this::openFolder, false);

        int infoY = buttonTop + (buttonH + buttonGap) * 3 + 6;
        int infoBottom = panelY + panelH - STATUS_H - PAD;
        if (infoY < infoBottom) {
            if (selected != null) {
                RenderUtils.drawText(context, textRenderer, "§7Selected: §f" + selected, rightX, infoY, 0xFFDDDDDD, false);
                String summary = ProfileManager.summary(selected);
                if (!summary.isEmpty() && infoY + 11 < infoBottom) {
                    RenderUtils.drawText(context, textRenderer, "§8" + summary, rightX, infoY + 11, 0xFF888888, false);
                }
                long mod = ProfileManager.modified(selected);
                if (mod > 0 && infoY + 22 < infoBottom) {
                    RenderUtils.drawText(context, textRenderer, "§8Saved " + DATE.format(new Date(mod)),
                            rightX, infoY + 22, 0xFF888888, false);
                }
            } else {
                RenderUtils.drawText(context, textRenderer, "§8Select a profile on the left", rightX, infoY, 0xFF777777, false);
            }
        }

        int hintY = panelY + panelH - STATUS_H - PAD - 20;
        if (hintY > infoY + 34) {
            RenderUtils.drawText(context, textRenderer, "§8Profiles are plain .json files.", rightX, hintY, 0xFF666666, false);
            RenderUtils.drawText(context, textRenderer, "§8Share one by copying it into the folder.", rightX, hintY + 10, 0xFF666666, false);
        }

        int statusColor = statusError ? 0xFFFF7777 : 0xFF77DD88;
        RenderUtils.drawText(context, textRenderer, status, panelX + PAD, panelY + panelH - STATUS_H + 3, statusColor, false);

        super.render(context, mouseX, mouseY, delta);

        for (Button b : buttons) {
            b.draw(context, mouseX, mouseY);
        }
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        RenderUtils.fill(context, listX, listY, listX + listW, listY + listH, ColorUtils.rgba(12, 13, 17, 220));
        RenderUtils.drawBorder(context, listX, listY, listW, listH, 1, ColorUtils.rgba(50, 55, 70, 200));

        if (profiles.isEmpty()) {
            RenderUtils.drawText(context, textRenderer, "§8No profiles yet", listX + 8, listY + 8, 0xFF777777, false);
            return;
        }

        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int index = i + scroll;
            if (index >= profiles.size()) break;
            String name = profiles.get(index);
            int ry = listY + 1 + i * ROW_H;
            boolean hovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2
                    && mouseY >= ry && mouseY <= ry + ROW_H - 2;
            boolean isSelected = name.equals(selected);

            int bg = isSelected ? ColorUtils.rgba(255, 255, 255, 26)
                    : hovered ? ColorUtils.rgba(255, 255, 255, 12) : 0;
            if (bg != 0) RenderUtils.fill(context, listX + 2, ry, listX + listW - 2, ry + ROW_H - 2, bg);
            if (isSelected) {
                RenderUtils.fill(context, listX + 2, ry, listX + 4, ry + ROW_H - 2, ThemeManager.getAccentColor());
            }

            RenderUtils.drawText(context, textRenderer, "§f" + name, listX + 9, ry + 3, 0xFFFFFFFF, false);
            String sub = ProfileManager.summary(name);
            if (!sub.isEmpty()) {
                RenderUtils.drawText(context, textRenderer, "§8" + sub, listX + 9, ry + 12, 0xFF777777, false);
            }
        }

        if (profiles.size() > rows) {
            String hint = (scroll + 1) + "-" + Math.min(profiles.size(), scroll + rows)
                    + " / " + profiles.size() + "  (scroll)";
            RenderUtils.drawText(context, textRenderer, "§8" + hint, listX + 6, listY + listH - 11, 0xFF666666, false);
        }
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button);
    }

    private boolean handleClick(double mouseX, double mouseY, int button) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.hit(mouseX, mouseY)) {
                b.action.run();
                return true;
            }
        }

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int row = (int) ((mouseY - listY - 1) / ROW_H);
            int index = row + scroll;
            if (row >= 0 && index < profiles.size()) {
                selected = profiles.get(index);
                armedDelete = null;
                if (nameField != null) nameField.setText(selected);
                setStatus("Selected \"" + selected + "\"", false);
                return true;
            }
        }

        if (nameField != null) {
            boolean over = nameField.isMouseOver(mouseX, mouseY);
            nameField.setFocused(over);
            if (over) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int max = Math.max(0, profiles.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        return super.keyPressed(input);
    }

    private void closeScreen() {
        if (client != null) client.setScreen(null);
    }

    // ------------------------------------------------------------------
    //  Tiny button
    // ------------------------------------------------------------------

    private void addButton(int x, int y, int w, int h, String label, Runnable action, boolean primary) {
        buttons.add(new Button(x, y, w, h, label, action, primary));
    }

    private final class Button {
        final int x, y, w, h;
        final String label;
        final Runnable action;
        final boolean primary;

        Button(int x, int y, int w, int h, String label, Runnable action, boolean primary) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.action = action;
            this.primary = primary;
        }

        boolean hit(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        void draw(DrawContext context, int mouseX, int mouseY) {
            boolean hovered = hit(mouseX, mouseY);
            int bg = primary
                    ? (hovered ? brighten(ThemeManager.getAccentColor()) : ThemeManager.getAccentColor())
                    : (hovered ? ColorUtils.rgba(52, 56, 68, 255) : ColorUtils.rgba(30, 32, 40, 255));
            RenderUtils.fill(context, x, y, x + w, y + h, bg);
            RenderUtils.drawBorder(context, x, y, w, h, 1,
                    primary ? ThemeManager.getAccentColor() : ColorUtils.rgba(70, 76, 92, 255));
            int color = primary ? 0xFF000000 : 0xFFDDDDDD;
            RenderUtils.drawText(context, textRenderer, label,
                    x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, color, false);
        }

        private int brighten(int color) {
            int a = (color >> 24) & 0xFF;
            int r = Math.min(255, (int) (((color >> 16) & 0xFF) * 1.15));
            int g = Math.min(255, (int) (((color >> 8) & 0xFF) * 1.15));
            int b = Math.min(255, (int) ((color & 0xFF) * 1.15));
            return ColorUtils.rgba(r, g, b, a == 0 ? 255 : a);
        }
    }
}
