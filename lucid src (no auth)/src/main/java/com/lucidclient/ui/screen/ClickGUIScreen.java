package com.lucidclient.ui.screen;

import com.lucidclient.LucidClient;
import com.lucidclient.config.ClickGuiConfig;
import com.lucidclient.config.ModuleConfig;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.ModuleManager;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.module.setting.KeybindSetting;
import com.lucidclient.module.setting.Setting;
import com.lucidclient.module.setting.StringSetting;
import com.lucidclient.ui.component.Icons;
import com.lucidclient.ui.theme.NativeTheme;
import com.lucidclient.ui.util.RenderUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Lucid ClickGUI — the {@code lucid-native} interface, ported to Fabric.
 *
 * <h3>Design</h3>
 * Dropdown panels in a centred row, one per category plus a Configs panel and a
 * Client panel. No borders on the body, no gradients on controls, no second
 * hue: a panel is one dark slab with a 1px accent hairline, state is said with
 * opacity and with the accent.
 *
 * <pre>
 *   ┌────────────────┐         ╔════════════════╗
 *   │ Combat      ⚔  │  ← 16-high header: label, accent indicator, icon
 *   ├────────────────┤         ║ Aim Assist  [R]║  ← 12-high row, hover slides 3
 *   │ Aim Assist  [R]│         ║  Range: 3.5    ║  ← settings fold inline
 *   │ Anchor Macro   │         ╚════════════════╝
 *   └────────────────┘
 * </pre>
 *
 * <h3>Interaction (identical to the native client)</h3>
 * <ul>
 *   <li><b>L-drag header</b> → move the panel; <b>R-click header</b> → fold it.</li>
 *   <li><b>L-click row</b> → toggle the module; <b>R-click</b> → fold its settings;
 *       <b>M-click</b> → rebind the module key.</li>
 *   <li><b>Wheel</b> → scroll the panel under the pointer (a tall category is a
 *       scrollable viewport, never a panel running off the screen).</li>
 *   <li><b>Right Shift / Esc</b> → close.</li>
 * </ul>
 *
 * The whole menu slides down from above with a per-panel stagger, the way the
 * native client opens.
 */
public class ClickGUIScreen extends LucidScreen {

    // ══════════════════════════════════════════════════════════════
    //  Geometry — Minecraft GUI-scaled units, straight from the native
    //  ClickGui.cpp constants.
    // ══════════════════════════════════════════════════════════════
    private static final float BAR_Y = 12f;
    private static final float PANEL_WIDTH = 140f;
    private static final float MIN_PANEL_WIDTH = 70f;
    private static final float HEADER_H = 16f;
    private static final float ROW_H = 12f;
    private static final float PADDING = 4f;
    private static final float CAT_GAP = 12f;
    private static final float HOVER_SHIFT = 3f;
    private static final float TOGGLE_SIZE = 6f;
    private static final float SLIDER_H = 1.5f;
    private static final float ARROW_SIZE = 6f;

    /** The native face is narrower than Minecraft's; this keeps a 12-unit row
     *  as compact as it is on the other client while staying legible. */
    private static final float TEXT_SCALE = 0.8f;
    /** The native's label size in units, which TEXT_SCALE renders. */
    private static final float TEXT_SIZE = 9f;
    /** The chevron renders at ARROW_SIZE units rather than the label's 9. */
    private static final float ARROW_SCALE = TEXT_SCALE * ARROW_SIZE / TEXT_SIZE;
    /** Font line height at scale 1 — used to centre text in a row. */
    private static final float FONT_H = 9f;

    private static final float SLIDE_SPEED = 14f;
    private static final float STAGGER = 0.12f;
    private static final float HOVER_SPEED = 12f;
    private static final float ENABLE_SPEED = 10f;
    private static final float EXPAND_SPEED = 14f;
    private static final float CHOICE_EXPAND_SPEED = 12f;
    private static final float SCROLL_SPEED = 18f;
    /** Units the module block travels per wheel notch — three rows. */
    private static final float SCROLL_STEP = 36f;

    private static final float PICKER_WIDTH = 105f;
    private static final float PICKER_HEIGHT = 100f;
    private static final float PICKER_PAD = 5f;
    private static final float PICKER_FIELD = 62f;
    private static final float PICKER_STRIP = 6f;

    private static final int MAX_CONFIG_NAME = 24;
    private static final long CLOSE_DEBOUNCE_MS = 250L;

    // ══════════════════════════════════════════════════════════════
    //  Data structures
    // ══════════════════════════════════════════════════════════════

    /** Axis-aligned rectangle in GUI units (floats — layout is sub-pixel). */
    private record Rect(float x, float y, float w, float h) {
        static final Rect EMPTY = new Rect(0f, 0f, 0f, 0f);
        boolean empty() { return w <= 0f || h <= 0f; }
        float right() { return x + w; }
        float bottom() { return y + h; }
        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
        Rect inset(float d) { return new Rect(x + d, y + d, w - 2f * d, h - 2f * d); }
    }

    private static final class SettingRow {
        final Setting<?> setting;
        Rect bounds = Rect.EMPTY;
        Rect control = Rect.EMPTY;
        float hover, knob, expand, picker, hue;
        boolean expanded, listening, pickerOpen;
        SettingRow(Setting<?> setting) {
            this.setting = setting;
            this.knob = ratioOf(setting);
            this.hue = NativeTheme.hueOf(colourOf(setting));
        }
    }

    private static final class ModuleRow {
        final Module module;
        final List<SettingRow> settings = new ArrayList<>();
        Rect head = Rect.EMPTY;
        float hover, enabled, expand;
        boolean expanded, binding;
        ModuleRow(Module module) {
            this.module = module;
            for (Setting<?> s : module.getSettings()) settings.add(new SettingRow(s));
        }
    }

    private static final class Panel {
        final String key;
        final String label;
        /** Null for the two special panels. */
        final Category category;
        final boolean configs;
        final boolean actions;
        final List<ModuleRow> rows = new ArrayList<>();
        float x, y;
        float hover, expand, scroll, scrollTarget, maxScroll;
        boolean expanded = true;
        boolean placed;
        Rect header = Rect.EMPTY;
        Rect outer = Rect.EMPTY;
        Rect bodyClip = Rect.EMPTY;
        Panel(PanelIdentity identity) {
            this.key = identity.key();
            this.label = identity.label();
            this.category = identity.category();
            this.configs = identity.configs();
            this.actions = identity.actions();
        }
    }

    private record PanelIdentity(String key, String label, Category category,
                                 boolean configs, boolean actions) {}

    private static final class ConfigEntry {
        final String name;
        Rect bounds = Rect.EMPTY;
        float hover;
        ConfigEntry(String name) { this.name = name; }
    }

    private static final class ActionRow {
        final String label;
        Rect bounds = Rect.EMPTY;
        float hover;
        ActionRow(String label) { this.label = label; }
    }

    // ══════════════════════════════════════════════════════════════
    //  State
    // ══════════════════════════════════════════════════════════════
    private final LucidClient client;
    private final List<Panel> panels = new ArrayList<>();
    private final List<ConfigEntry> configs = new ArrayList<>();
    private final List<ActionRow> actions = new ArrayList<>();

    private Map<String, ClickGuiConfig.WindowState> savedState;

    private float panelW = PANEL_WIDTH;
    private float slide;
    private float fullHeight;
    private float time;
    private boolean reflowed;
    private long openMs;
    private long lastFrameMs;

    private float mouseX;
    private float mouseY;
    private boolean mouseDown;

    private Panel dragPanel;
    private float dragDX, dragDY;
    private SettingRow activeSlider;
    private SettingRow activePicker;
    private int pickerAxis = -1;
    private ModuleRow bindRow;

    // Configs panel
    private int selectedConfig = -1;
    private String configName = "";
    private boolean editingName;
    private Rect nameField = Rect.EMPTY;
    private Rect loadButton = Rect.EMPTY;
    private Rect saveButton = Rect.EMPTY;
    private Rect deleteButton = Rect.EMPTY;
    private float nameHover, loadHover, saveHover, deleteHover;

    public ClickGUIScreen(LucidClient client) {
        super(Text.literal("Lucid Client"));
        this.client = client;
    }

    // ══════════════════════════════════════════════════════════════
    //  Build
    // ══════════════════════════════════════════════════════════════
    @Override
    protected void buildUI() {
        openMs = System.currentTimeMillis();
        lastFrameMs = 0L;
        panels.clear();
        configs.clear();
        actions.clear();
        reflowed = false;
        slide = 0f;
        bindRow = null;
        activeSlider = null;
        activePicker = null;
        pickerAxis = -1;

        ModuleManager mm = client == null ? null : client.getModuleManager();
        for (Category category : Category.values()) {
            List<Module> modules = mm == null ? List.of() : mm.getByCategory(category);
            if (modules.isEmpty()) continue;
            Panel panel = new Panel(new PanelIdentity(category.name(), category.getDisplayName(),
                category, false, false));
            for (Module module : modules) panel.rows.add(new ModuleRow(module));
            panels.add(panel);
        }
        panels.add(new Panel(new PanelIdentity("Configs", "Configs", null, true, false)));
        panels.add(new Panel(new PanelIdentity("Client", "Client", null, false, true)));

        actions.add(new ActionRow("Reload Config"));
        actions.add(new ActionRow("Save Config"));
        actions.add(new ActionRow("Open Folder"));
        actions.add(new ActionRow("Disable All"));

        refreshConfigs();

        // Restore saved positions, then slot anything still unplaced into the
        // centred row (wrapping to more rows when one line cannot hold every
        // panel) measured with the same fitted width layout() renders at.
        if (savedState == null) savedState = ClickGuiConfig.load();
        if (savedState != null) {
            for (Panel panel : panels) {
                ClickGuiConfig.WindowState state = savedState.get(panel.key);
                if (state == null) continue;
                panel.x = state.x;
                panel.y = state.y;
                panel.expanded = !state.collapsed;
                panel.placed = true;
            }
        }
        placeUnplaced();
    }

    private void placeUnplaced() {
        int unplaced = 0;
        for (Panel panel : panels) if (!panel.placed) unplaced++;
        if (unplaced == 0 || panels.isEmpty()) return;

        float scaledW = this.width > 0 ? this.width : 640f;
        float fitW = fittedWidth(scaledW, panels.size());
        float stride = fitW + CAT_GAP;
        int perRow = panels.size();
        if (stride * panels.size() - CAT_GAP > scaledW - 16f) {
            perRow = Math.max(1, (int) ((scaledW - 16f + CAT_GAP) / stride));
        }
        int inLine = Math.min(perRow, unplaced);
        float lineWidth = stride * (inLine - 1) + fitW;
        float cursorX = (scaledW - lineWidth) * 0.5f;
        float rowY = BAR_Y;
        int column = 0;
        for (Panel panel : panels) {
            if (panel.placed) continue;
            if (column == perRow) {
                column = 0;
                cursorX = (scaledW - lineWidth) * 0.5f;
                rowY += HEADER_H + ROW_H * 3f;
            }
            panel.x = cursorX;
            panel.y = rowY;
            panel.placed = true;
            cursorX += stride;
            column++;
        }
    }

    /** Preferred width, shrunk just enough for the row to fit on screen. */
    private static float fittedWidth(float scaledW, int count) {
        if (count <= 1) return PANEL_WIDTH;
        float fit = (scaledW - 16f - CAT_GAP * (count - 1)) / count;
        return clamp(Math.min(PANEL_WIDTH, fit), MIN_PANEL_WIDTH, PANEL_WIDTH);
    }

    // ══════════════════════════════════════════════════════════════
    //  Persistence
    // ══════════════════════════════════════════════════════════════
    @Override
    public void close() {
        saveLayout();
        super.close();
    }

    private void saveLayout() {
        if (savedState == null) savedState = new HashMap<>();
        for (Panel panel : panels) {
            savedState.put(panel.key, new ClickGuiConfig.WindowState(
                Math.round(panel.x), Math.round(panel.y), !panel.expanded));
        }
        ClickGuiConfig.save(savedState);
    }

    // ══════════════════════════════════════════════════════════════
    //  Maths helpers
    // ══════════════════════════════════════════════════════════════
    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Exponential approach — frame-rate independent, snaps when close. */
    private static float approach(float current, float target, float rate, float dt) {
        float step = 1f - (float) Math.exp(-rate * dt);
        float next = current + (target - current) * step;
        return Math.abs(target - next) < 0.0015f ? target : next;
    }

    private static float easeOutQuint(float t) {
        float inv = 1f - clamp(t, 0f, 1f);
        return 1f - inv * inv * inv * inv * inv;
    }

    private static boolean isChoice(Setting<?> setting) { return setting instanceof EnumSetting; }

    private static int choiceCount(Setting<?> setting) {
        return setting instanceof EnumSetting e ? e.getOptions().size() : 0;
    }

    private static float ratioOf(Setting<?> setting) {
        if (setting instanceof FloatSetting f) return f.getRatio();
        if (setting instanceof IntSetting i) return i.getRatio();
        return 0f;
    }

    private static int colourOf(Setting<?> setting) {
        return setting instanceof ColorSetting c ? c.getColor() : 0xFFFFFFFF;
    }

    /** Native number formatting: fixed decimals, trailing zeros trimmed. */
    private static String formatNumber(Setting<?> setting) {
        String value;
        if (setting instanceof FloatSetting f) {
            value = String.format(Locale.ROOT, "%." + f.getDecimals() + "f", f.get());
        } else if (setting instanceof IntSetting i) {
            value = String.valueOf(i.get());
        } else {
            return "";
        }
        if (value.indexOf('.') < 0) return value;
        while (value.endsWith("0")) value = value.substring(0, value.length() - 1);
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private boolean interactive() { return !isClosing(); }

    // ══════════════════════════════════════════════════════════════
    //  Clock, layout
    // ══════════════════════════════════════════════════════════════
    private void frame() {
        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0.016f
            : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        time += dt;

        slide = approach(slide, isClosing() ? 0f : 1f, SLIDE_SPEED, dt);
        if (isClosing() && slide < 0.02f) {
            slide = 0f;
            close();
            return;
        }
        layout(dt);
    }

    private void layout(float dt) {
        float scaledW = this.width;
        float scaledH = this.height;

        if (!panels.isEmpty()) {
            float clampMaxX = Math.max(scaledW - 40f, -panelW + 40f);
            float clampMaxY = Math.max(scaledH - 20f, 0f);
            for (Panel panel : panels) {
                panel.x = clamp(panel.x, -panelW + 40f, clampMaxX);
                panel.y = clamp(panel.y, 0f, clampMaxY);
            }
            if (!reflowed) {
                reflowed = true;
                reflow(scaledW);
            }
            panelW = fittedWidth(scaledW, panels.size());
        }

        fullHeight = 0f;
        for (Panel panel : panels) {
            panel.expand = approach(panel.expand, panel.expanded ? 1f : 0f, EXPAND_SPEED, dt);
            fullHeight = Math.max(fullHeight, HEADER_H + panelContentHeight(panel) + 1f);
        }

        float maxX = Math.max(scaledW - 40f, -panelW + 40f);
        float maxY = Math.max(scaledH - 20f, 0f);
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            panel.x = clamp(panel.x, -panelW + 40f, maxX);
            panel.y = clamp(panel.y, 0f, maxY);

            // Stagger cap: beyond start >= 1 the divisor collapses and the last
            // panels in a long menu simply pop.
            float start = Math.min(i * STAGGER, 0.75f);
            float local = clamp((slide - start) / Math.max(1f - start, 0.001f), 0f, 1f);
            float yOffset = (local - 1f) * fullHeight;

            // The module block is capped to what fits under the header, so a
            // category whose settings run past the bottom of the screen becomes
            // a scrollable viewport instead of a panel off the display.
            float contentH = panelContentHeight(panel);
            float bodyTop = panel.y + yOffset + HEADER_H;
            float availableH = Math.max(ROW_H * 2f, scaledH - bodyTop - 6f);
            panel.maxScroll = Math.max(0f, contentH - availableH);
            panel.scrollTarget = clamp(panel.scrollTarget, 0f, panel.maxScroll);
            panel.scroll = clamp(approach(panel.scroll, panel.scrollTarget, SCROLL_SPEED, dt),
                0f, panel.maxScroll);
            float visibleH = Math.min(contentH, availableH);
            float totalH = HEADER_H + visibleH + 1f;
            panel.header = new Rect(panel.x, panel.y + yOffset, panelW, HEADER_H);
            panel.outer = new Rect(panel.x, panel.y + yOffset, panelW, totalH);
            panel.bodyClip = new Rect(panel.outer.x + 1f, bodyTop,
                Math.max(0f, panel.outer.w - 2f), visibleH);

            panel.hover = approach(panel.hover,
                interactive() && panel.header.contains(mouseX, mouseY) ? 1f : 0f, HOVER_SPEED, dt);

            if (panel.configs) {
                layoutConfigs(panel, yOffset, dt);
                continue;
            }
            if (panel.actions) {
                float rowTop = panel.y + yOffset + HEADER_H - panel.scroll;
                for (ActionRow action : actions) {
                    action.bounds = panel.expand > 0.02f
                        ? new Rect(panel.outer.x, rowTop, panel.outer.w, ROW_H) : Rect.EMPTY;
                    action.hover = approach(action.hover,
                        interactive() && !action.bounds.empty()
                            && action.bounds.contains(mouseX, mouseY) ? 1f : 0f, HOVER_SPEED, dt);
                    rowTop += ROW_H * panel.expand;
                }
                continue;
            }

            float rowTop = panel.y + yOffset + HEADER_H - panel.scroll;
            for (ModuleRow row : panel.rows) {
                boolean hovered = interactive() && panel.expand > 0.3f
                    && new Rect(panel.outer.x, rowTop, panel.outer.w, ROW_H)
                        .contains(mouseX, mouseY);
                row.hover = approach(row.hover, hovered ? 1f : 0f, HOVER_SPEED, dt);
                row.enabled = approach(row.enabled, row.module.isEnabled() ? 1f : 0f, ENABLE_SPEED, dt);
                row.expand = approach(row.expand, row.expanded ? 1f : 0f, EXPAND_SPEED, dt);

                row.head = panel.expand > 0.02f
                    ? new Rect(panel.outer.x, rowTop, panel.outer.w, ROW_H) : Rect.EMPTY;

                float settingTop = rowTop + ROW_H;
                for (SettingRow setting : row.settings) {
                    float h = ROW_H + (isChoice(setting.setting)
                        ? choiceCount(setting.setting) * ROW_H * setting.expand : 0f);
                    boolean settingHovered = interactive() && row.expand > 0.3f
                        && panel.expand > 0.3f
                        && new Rect(panel.outer.x, settingTop, panel.outer.w, h)
                            .contains(mouseX, mouseY);
                    setting.hover = approach(setting.hover, settingHovered ? 1f : 0f, HOVER_SPEED, dt);
                    setting.expand = approach(setting.expand, setting.expanded ? 1f : 0f,
                        CHOICE_EXPAND_SPEED, dt);
                    setting.picker = approach(setting.picker, setting.pickerOpen ? 1f : 0f, 16f, dt);
                    if (setting.setting instanceof FloatSetting || setting.setting instanceof IntSetting) {
                        setting.knob = approach(setting.knob, ratioOf(setting.setting), 20f, dt);
                    }
                    setting.bounds = (row.expand > 0.02f && panel.expand > 0.02f)
                        ? new Rect(panel.outer.x, settingTop, panel.outer.w, h) : Rect.EMPTY;
                    settingTop += h * row.expand;
                }
                rowTop += (ROW_H + settingsHeight(row)) * panel.expand;
            }
        }

        if (activeSlider != null && mouseDown && !activeSlider.control.empty()) {
            Rect track = activeSlider.control;
            setRatio(activeSlider.setting, (mouseX - track.x) / track.w);
        }
        if (activePicker != null && mouseDown && !activePicker.control.empty()) {
            dragPicker(activePicker);
        }
    }

    /** One-time overlap repair: re-flows every panel into the centred grid. */
    private void reflow(float scaledW) {
        int overlapping = 0;
        for (int i = 0; i < panels.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (Math.abs(panels.get(i).x - panels.get(j).x) < panelW
                    && Math.abs(panels.get(i).y - panels.get(j).y) < HEADER_H) {
                    overlapping++;
                }
            }
        }
        if (overlapping == 0) return;

        float width = scaledW > 0f ? scaledW : 640f;
        float fitW = fittedWidth(width, panels.size());
        float stride = fitW + CAT_GAP;
        int perRow = panels.size();
        if (stride * panels.size() - CAT_GAP > width - 16f) {
            perRow = Math.max(1, (int) ((width - 16f + CAT_GAP) / stride));
        }
        // Row pitch must clear the EXPANDED panel, computed from the module
        // tree rather than the animated height, which is still zero up here.
        float pitch = HEADER_H + ROW_H * 3f;
        float widest = 0f;
        for (Panel panel : panels) widest = Math.max(widest, fullContentHeight(panel) + 1f);
        pitch = Math.max(pitch, widest + 4f);

        int inLine = Math.min(perRow, panels.size());
        float lineWidth = stride * (inLine - 1) + fitW;
        float cursorX = (width - lineWidth) * 0.5f;
        float rowY = BAR_Y;
        int column = 0;
        for (Panel panel : panels) {
            if (column == perRow) {
                column = 0;
                cursorX = (width - lineWidth) * 0.5f;
                rowY += pitch;
            }
            panel.x = cursorX;
            panel.y = rowY;
            panel.placed = true;
            cursorX += stride;
            column++;
        }
        panelW = fitW;
    }

    // ══════════════════════════════════════════════════════════════
    //  Content heights
    // ══════════════════════════════════════════════════════════════
    private float settingsHeight(ModuleRow row) {
        float height = 0f;
        for (SettingRow setting : row.settings) {
            height += ROW_H;
            if (isChoice(setting.setting)) {
                height += choiceCount(setting.setting) * ROW_H * setting.expand;
            }
        }
        return height * row.expand;
    }

    private float panelContentHeight(Panel panel) {
        if (panel.configs) return configsHeight() * panel.expand;
        if (panel.actions) return actionsHeight() * panel.expand;
        float height = 0f;
        for (ModuleRow row : panel.rows) height += ROW_H + settingsHeight(row);
        return height * panel.expand;
    }

    /** Fully-expanded content height — used by the overlap repair's row pitch. */
    private float fullContentHeight(Panel panel) {
        if (panel.configs) return (configs.size() + 4) * ROW_H;
        if (panel.actions) return actions.size() * ROW_H;
        float height = 0f;
        for (ModuleRow row : panel.rows) {
            height += ROW_H;
            for (SettingRow setting : row.settings) {
                height += ROW_H;
                if (isChoice(setting.setting)) {
                    height += choiceCount(setting.setting) * ROW_H;
                }
            }
        }
        return height;
    }

    private float configsHeight() { return (configs.size() + 4) * ROW_H; }

    private float actionsHeight() { return actions.size() * ROW_H; }

    // ══════════════════════════════════════════════════════════════
    //  Render
    // ══════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frame();
        if (slide <= 0.004f) return;

        float alpha = clamp(slide, 0f, 1f);

        // Dimmed scene behind the panels, fading with the slide. The blur post
        // effect is registered from drawBackground(), which vanilla calls
        // before this method.
        ctx.fill(0, 0, this.width, this.height, NativeTheme.opacity(NativeTheme.SCRIM, alpha));

        for (Panel panel : panels) renderPanel(ctx, panel);

        // Colour pickers live above every panel, unclipped.
        for (Panel panel : panels) {
            for (ModuleRow row : panel.rows) {
                for (SettingRow setting : row.settings) {
                    if (setting.picker > 0.004f) renderPicker(ctx, setting);
                }
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext ctx, float progress) {
        // DrawContext.applyBlur() bypasses the "Menu Background Blurriness"
        // option guard, so the world behind the menu is always frosted.
        ctx.applyBlur();
    }

    private void renderPanel(DrawContext ctx, Panel panel) {
        float alpha = clamp(slide, 0f, 1f);
        int accent = NativeTheme.accent();

        // Accent hairline around the whole panel, then the dark body inset by
        // one unit. A hairline round a slab this large would otherwise draw the
        // eye to the edge instead of to what is written inside it.
        fill(ctx, panel.outer, NativeTheme.opacity(accent, alpha));
        fill(ctx, panel.outer.inset(1f),
            NativeTheme.opacity(NativeTheme.darker(NativeTheme.MODULE_BG, 2), alpha));

        if (panel.expand > 0.005f) {
            clipOn(ctx, panel.bodyClip);
            float rowTop = panel.outer.y + HEADER_H - panel.scroll;
            if (panel.configs) {
                renderConfigs(ctx, panel, rowTop);
            } else if (panel.actions) {
                renderActions(ctx, panel, rowTop);
            } else {
                for (ModuleRow row : panel.rows) {
                    renderModuleRow(ctx, panel, row, rowTop);
                    rowTop += (ROW_H + settingsHeight(row)) * panel.expand;
                }
            }
            ctx.disableScissor();
        }

        // Header over the module block: base, hover, then the active step.
        int header = NativeTheme.mix(
            NativeTheme.mix(NativeTheme.BASE, NativeTheme.HOVER_BG, panel.hover),
            NativeTheme.ACTIVE_BG, panel.expand);
        fill(ctx, new Rect(panel.outer.x + 1.5f, panel.outer.y + 1.5f,
            panel.outer.w - 3f, HEADER_H - 2.5f), NativeTheme.opacity(header, alpha));

        // Indicator line growing from the centre, quadratic ease-out.
        if (panel.expand > 0.005f) {
            float ease = 1f - (1f - panel.expand) * (1f - panel.expand);
            float half = (panel.outer.w * 0.5f - 1.5f) * ease;
            float mid = panel.outer.x + panel.outer.w * 0.5f;
            fill(ctx, new Rect(mid - half, panel.outer.y + 1.5f, half * 2f, 0.5f),
                NativeTheme.opacity(accent, alpha));
        }

        int label = NativeTheme.mix(
            NativeTheme.mix(NativeTheme.TEXT_DIM, NativeTheme.TEXT_MID, panel.hover),
            NativeTheme.TEXT_BRIGHT, panel.expand);
        text(ctx, panel.label, panel.outer.x + PADDING,
            textY(panel.outer.y, HEADER_H, TEXT_SCALE), TEXT_SCALE,
            NativeTheme.opacity(label, alpha));

        // Icon, right-aligned in the header (the native client draws the same
        // asset slot there; here it is the category's own glyph).
        int iconSize = 8;
        int iconX = Math.round(panel.outer.right() - PADDING - iconSize - 1f);
        int iconY = Math.round(panel.outer.y + (HEADER_H - iconSize) * 0.5f);
        int iconColor = NativeTheme.opacity(accent, alpha);
        if (panel.configs) {
            Icons.drawGear(ctx, iconX, iconY, iconSize, iconColor);
        } else if (panel.actions) {
            Icons.drawGrid(ctx, iconX, iconY, iconSize, iconColor);
        } else if (panel.category != null) {
            Icons.drawCategoryIcon(ctx, panel.category.getIconName(), iconX, iconY, iconSize, iconColor);
        }
    }

    private void renderModuleRow(DrawContext ctx, Panel panel, ModuleRow row, float rowTop) {
        if (row.head.empty()) return;
        float alpha = clamp(slide, 0f, 1f);
        int accent = NativeTheme.accent();
        Rect head = new Rect(row.head.x + 1.5f, row.head.y, row.head.w - 3f, ROW_H);

        int disabled = NativeTheme.mix(NativeTheme.MODULE_BG, NativeTheme.MODULE_HOVER, row.hover);
        fill(ctx, head, NativeTheme.opacity(
            NativeTheme.mix(disabled, NativeTheme.darker(accent, 2), row.enabled), alpha));

        int textColour = NativeTheme.mix(
            NativeTheme.mix(NativeTheme.TEXT_DIM, NativeTheme.TEXT_MID, row.hover),
            NativeTheme.TEXT_ON, row.enabled);
        float drawX = head.x + PADDING + HOVER_SHIFT * row.hover;
        float size = TEXT_SCALE;
        float ty = textY(head.y, ROW_H, size);

        String name = fit(name(row), head.w - PADDING * 2f - ARROW_SIZE * 2f, size);
        text(ctx, name, drawX, ty, size, NativeTheme.opacity(textColour, alpha));
        float nameEnd = drawX + textWidth(name, size);

        int bind = row.module.getKeybind();
        if (row.binding) {
            text(ctx, "[...]", nameEnd + 4f, ty, size, NativeTheme.opacity(accent, alpha));
        } else if (bind > 0) {
            text(ctx, "[" + KeybindSetting.getKeyName(bind) + "]", nameEnd + 4f, ty, size,
                NativeTheme.opacity(NativeTheme.BIND_IDLE, alpha));
        }

        boolean hasSettings = !row.settings.isEmpty();
        if (hasSettings) {
            int idle = NativeTheme.mix(NativeTheme.ITEM_IDLE, NativeTheme.TEXT_MID, row.hover);
            int arrow = NativeTheme.mix(NativeTheme.mix(idle, accent, row.expand),
                NativeTheme.TEXT_ON, row.enabled);
            float arrowX = head.right() - PADDING - ARROW_SIZE + 1f;
            float arrowY = textY(head.y, ROW_H, ARROW_SCALE);
            float arrowScale = ARROW_SCALE;
            // No rotated text, so the chevron cross-fades from left to down
            // across the expand animation instead of rotating.
            text(ctx, "<", arrowX, arrowY, arrowScale,
                NativeTheme.opacity(arrow, alpha * (1f - row.expand)));
            text(ctx, "v", arrowX, arrowY, arrowScale,
                NativeTheme.opacity(arrow, alpha * row.expand));
        }

        if (row.expand <= 0.005f) return;
        float settingTop = rowTop + ROW_H;
        for (SettingRow setting : row.settings) {
            renderSetting(ctx, row, setting, settingTop);
            settingTop += ROW_H + (isChoice(setting.setting)
                ? choiceCount(setting.setting) * ROW_H * setting.expand : 0f);
        }
    }

    private void renderSetting(DrawContext ctx, ModuleRow row, SettingRow setting, float settingTop) {
        if (setting.bounds.empty()) return;
        float alpha = clamp(slide, 0f, 1f);
        int accent = NativeTheme.accent();
        Setting<?> value = setting.setting;

        float x = setting.bounds.x + 1.5f;
        float w = setting.bounds.w - 3f;
        fill(ctx, new Rect(x, setting.bounds.y, w, ROW_H),
            NativeTheme.opacity(NativeTheme.SETTING_BG, alpha));

        float size = TEXT_SCALE;
        float drawX = x + PADDING + HOVER_SHIFT * setting.hover;
        float ty = textY(setting.bounds.y, ROW_H, size);
        int textColour = NativeTheme.mix(NativeTheme.TEXT_DIM, NativeTheme.TEXT_MID, setting.hover);
        float right = setting.bounds.right() - 1.5f - PADDING;

        if (value instanceof BooleanSetting bool) {
            text(ctx, value.getName(), drawX, ty, size, NativeTheme.opacity(textColour, alpha));
            Rect frame = new Rect(right - TOGGLE_SIZE - 1f,
                setting.bounds.y + (ROW_H - TOGGLE_SIZE) * 0.5f - 1f,
                TOGGLE_SIZE + 2f, TOGGLE_SIZE + 2f);
            setting.control = frame.inset(1f);
            fill(ctx, frame, NativeTheme.opacity(NativeTheme.OUTLINE_DARK, alpha));
            if (bool.isEnabled()) fill(ctx, setting.control, NativeTheme.opacity(accent, alpha));
        } else if (value instanceof FloatSetting || value instanceof IntSetting) {
            text(ctx, value.getName() + ": " + formatNumber(value), drawX, ty, size,
                NativeTheme.opacity(textColour, alpha));
            Rect track = new Rect(x, setting.bounds.y + (ROW_H - SLIDER_H), w, SLIDER_H);
            setting.control = track;
            fill(ctx, track, NativeTheme.opacity(NativeTheme.TRACK_BG, alpha));
            float filled = w * clamp(setting.knob, 0f, 1f);
            if (filled > 0.5f) {
                RenderUtils.drawGradientHorizontal(ctx, Math.round(track.x), Math.round(track.y),
                    Math.max(1, Math.round(filled)), Math.max(1, Math.round(track.h)),
                    NativeTheme.opacity(accent, alpha),
                    NativeTheme.opacity(NativeTheme.darker(accent, 1), alpha));
            }
        } else if (value instanceof EnumSetting) {
            text(ctx, value.getName(), drawX, ty, size, NativeTheme.opacity(textColour, alpha));
            textRight(ctx, String.valueOf(value.get()), right, ty, size,
                NativeTheme.opacity(textColour, alpha));
            setting.control = setting.bounds;
            if (setting.expand > 0.01f) renderChoiceItems(ctx, setting);
        } else if (value instanceof KeybindSetting key) {
            text(ctx, value.getName(), drawX, ty, size, NativeTheme.opacity(textColour, alpha));
            String label = setting.listening ? "[...]" : "[" + key.getKeyName() + "]";
            textRight(ctx, label, right, ty, size,
                NativeTheme.opacity(setting.listening ? accent : textColour, alpha));
            setting.control = setting.bounds;
        } else if (value instanceof ColorSetting colour) {
            text(ctx, value.getName(), drawX, ty, size, NativeTheme.opacity(textColour, alpha));
            setting.control = new Rect(right - TOGGLE_SIZE,
                setting.bounds.y + (ROW_H - TOGGLE_SIZE) * 0.5f, TOGGLE_SIZE, TOGGLE_SIZE);
            fill(ctx, new Rect(setting.control.x - 1f, setting.control.y - 1f,
                TOGGLE_SIZE + 2f, TOGGLE_SIZE + 2f),
                NativeTheme.opacity(NativeTheme.OUTLINE_DARK, alpha));
            fill(ctx, setting.control,
                NativeTheme.opacity(colour.getColor(), alpha));
        } else if (value instanceof StringSetting text2) {
            text(ctx, value.getName(), drawX, ty, size, NativeTheme.opacity(textColour, alpha));
            boolean listening = text2.isFocused();
            String shown = text2.isEmpty() && !listening ? "..." : String.valueOf(text2.get());
            float start = textRight(ctx, shown, right, ty, size,
                NativeTheme.opacity(listening ? accent : NativeTheme.BIND_IDLE, alpha));
            if (listening) {
                float blink = 0.5f + 0.5f * (float) Math.sin(time * 6f);
                fill(ctx, new Rect(start + textWidth(shown, size) + 1f, ty, 0.8f, FONT_H * size),
                    NativeTheme.opacity(accent, alpha * blink));
            }
            setting.control = setting.bounds;
        }
    }

    private void renderChoiceItems(DrawContext ctx, SettingRow setting) {
        float alpha = clamp(slide, 0f, 1f) * clamp(setting.expand, 0f, 1f);
        if (alpha <= 0.01f) return;
        EnumSetting value = (EnumSetting) setting.setting;
        int count = value.getOptions().size();
        float x = setting.bounds.x + 1.5f;
        float w = setting.bounds.w - 3f;
        float size = TEXT_SCALE;

        fill(ctx, new Rect(x, setting.bounds.y + ROW_H, w, count * ROW_H * setting.expand),
            NativeTheme.opacity(NativeTheme.SETTING_DEEP, alpha));

        float revealed = count * ROW_H * setting.expand;
        for (int i = 0; i < count; i++) {
            float itemY = setting.bounds.y + ROW_H + i * ROW_H;
            if (itemY + ROW_H > setting.bounds.y + ROW_H + revealed + 0.5f) break;
            boolean selected = i == value.getSelectedIndex();
            boolean hovered = mouseY >= itemY && mouseY <= itemY + ROW_H
                && mouseX >= setting.bounds.x && mouseX <= setting.bounds.right();
            int colour = selected ? NativeTheme.accent()
                : NativeTheme.mix(NativeTheme.ITEM_IDLE, NativeTheme.TEXT_MID, hovered ? 1f : 0f);
            text(ctx, value.getOptions().get(i),
                x + PADDING + (hovered ? HOVER_SHIFT : 0f),
                textY(itemY, ROW_H, size), size, NativeTheme.opacity(colour, alpha));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Configs panel
    // ══════════════════════════════════════════════════════════════
    private void layoutConfigs(Panel panel, float yOffset, float dt) {
        float rowTop = panel.y + yOffset + HEADER_H - panel.scroll;
        for (ConfigEntry entry : configs) {
            entry.bounds = panel.expand > 0.02f
                ? new Rect(panel.outer.x, rowTop, panel.outer.w, ROW_H) : Rect.EMPTY;
            entry.hover = approach(entry.hover,
                interactive() && !entry.bounds.empty() && entry.bounds.contains(mouseX, mouseY)
                    ? 1f : 0f, HOVER_SPEED, dt);
            rowTop += ROW_H * panel.expand;
        }
        nameField = panel.expand > 0.02f
            ? new Rect(panel.outer.x, rowTop, panel.outer.w, ROW_H) : Rect.EMPTY;
        rowTop += ROW_H * panel.expand;
        loadButton = panel.expand > 0.02f
            ? new Rect(panel.outer.x, rowTop, panel.outer.w, ROW_H) : Rect.EMPTY;
        rowTop += ROW_H * panel.expand;
        saveButton = panel.expand > 0.02f
            ? new Rect(panel.outer.x, rowTop, panel.outer.w, ROW_H) : Rect.EMPTY;
        rowTop += ROW_H * panel.expand;
        deleteButton = panel.expand > 0.02f
            ? new Rect(panel.outer.x, rowTop, panel.outer.w, ROW_H) : Rect.EMPTY;

        nameHover = approach(nameHover, hovered(nameField) ? 1f : 0f, HOVER_SPEED, dt);
        loadHover = approach(loadHover, hovered(loadButton) ? 1f : 0f, HOVER_SPEED, dt);
        saveHover = approach(saveHover, hovered(saveButton) ? 1f : 0f, HOVER_SPEED, dt);
        deleteHover = approach(deleteHover, hovered(deleteButton) ? 1f : 0f, HOVER_SPEED, dt);
    }

    private boolean hovered(Rect rect) {
        return interactive() && !rect.empty() && rect.contains(mouseX, mouseY);
    }

    private void renderConfigs(DrawContext ctx, Panel panel, float rowTop) {
        float alpha = clamp(slide, 0f, 1f);
        int accent = NativeTheme.accent();
        float size = TEXT_SCALE;

        for (int i = 0; i < configs.size(); i++) {
            ConfigEntry entry = configs.get(i);
            if (entry.bounds.empty()) continue;
            Rect row = new Rect(entry.bounds.x + 1.5f, entry.bounds.y, entry.bounds.w - 3f, ROW_H);
            fill(ctx, row, NativeTheme.opacity(NativeTheme.SETTING_BG, alpha));
            int colour = i == selectedConfig ? accent
                : NativeTheme.mix(NativeTheme.TEXT_DIM, NativeTheme.TEXT_MID, entry.hover);
            text(ctx, entry.name, row.x + PADDING + HOVER_SHIFT * entry.hover,
                textY(row.y, ROW_H, size), size, NativeTheme.opacity(colour, alpha));
        }

        if (!nameField.empty()) {
            Rect row = new Rect(nameField.x + 1.5f, nameField.y, nameField.w - 3f, ROW_H);
            fill(ctx, row, NativeTheme.opacity(NativeTheme.SETTING_DEEP, alpha));
            boolean empty = configName.isEmpty();
            String shown = empty ? "Config name..." : configName;
            int colour = empty ? NativeTheme.ITEM_IDLE
                : NativeTheme.mix(NativeTheme.TEXT_DIM, NativeTheme.TEXT_MID, nameHover);
            float start = row.x + PADDING;
            text(ctx, shown, start, textY(row.y, ROW_H, size), size,
                NativeTheme.opacity(colour, alpha));
            if (editingName) {
                float blink = 0.5f + 0.5f * (float) Math.sin(time * 6f);
                fill(ctx, new Rect(start + textWidth(shown, size) + 1.5f,
                    textY(row.y, ROW_H, size), 1f, FONT_H * size),
                    NativeTheme.opacity(accent, alpha * blink));
            }
        }

        renderPanelButton(ctx, loadButton, "Load", loadHover, alpha);
        renderPanelButton(ctx, saveButton, "Save", saveHover, alpha);
        renderPanelButton(ctx, deleteButton, "Delete", deleteHover, alpha);
    }

    private void renderPanelButton(DrawContext ctx, Rect bounds, String label, float hover,
                                   float alpha) {
        if (bounds.empty()) return;
        Rect row = new Rect(bounds.x + 1.5f, bounds.y, bounds.w - 3f, ROW_H);
        fill(ctx, row, NativeTheme.opacity(
            NativeTheme.mix(NativeTheme.SETTING_BG, NativeTheme.MODULE_HOVER, hover), alpha));
        text(ctx, label, row.x + PADDING, textY(row.y, ROW_H, TEXT_SCALE), TEXT_SCALE,
            NativeTheme.opacity(NativeTheme.mix(NativeTheme.TEXT_DIM, NativeTheme.TEXT_ON, hover),
                alpha));
    }

    // ══════════════════════════════════════════════════════════════
    //  Client panel
    // ══════════════════════════════════════════════════════════════
    private void renderActions(DrawContext ctx, Panel panel, float rowTop) {
        float alpha = clamp(slide, 0f, 1f);
        float size = TEXT_SCALE;
        for (int i = 0; i < actions.size(); i++) {
            ActionRow action = actions.get(i);
            if (action.bounds.empty()) continue;
            Rect row = new Rect(action.bounds.x + 1.5f, action.bounds.y,
                action.bounds.w - 3f, ROW_H);
            fill(ctx, row, NativeTheme.opacity(
                NativeTheme.mix(NativeTheme.SETTING_BG, NativeTheme.MODULE_HOVER, action.hover),
                alpha));
            // The way out is the one row that is not like the others.
            int colour = i + 1 == actions.size()
                ? NativeTheme.mix(NativeTheme.TEXT_DIM, NativeTheme.NEGATIVE, action.hover)
                : NativeTheme.mix(NativeTheme.TEXT_DIM, NativeTheme.TEXT_ON, action.hover);
            text(ctx, action.label, row.x + PADDING, textY(row.y, ROW_H, size), size,
                NativeTheme.opacity(colour, alpha));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Colour picker
    // ══════════════════════════════════════════════════════════════
    private Rect pickerPopup(SettingRow setting) {
        return new Rect(setting.control.right() + 8f, setting.control.y - 3f,
            PICKER_WIDTH, PICKER_HEIGHT);
    }

    private Rect pickerField(Rect popup) {
        return new Rect(popup.x + PICKER_PAD, popup.y + PICKER_PAD,
            popup.w - PICKER_PAD * 2f, PICKER_FIELD);
    }

    private Rect pickerStrip(Rect field) {
        return new Rect(field.x, field.bottom() + PICKER_PAD, field.w, PICKER_STRIP);
    }

    private void renderPicker(DrawContext ctx, SettingRow setting) {
        float alpha = clamp(slide, 0f, 1f) * easeOutQuint(clamp(setting.picker, 0f, 1f));
        if (alpha <= 0.004f || setting.control.empty()) return;

        int colour = colourOf(setting.setting);
        Rect popup = pickerPopup(setting);
        RenderUtils.drawSoftShadow(ctx, Math.round(popup.x), Math.round(popup.y),
            Math.round(popup.w), Math.round(popup.h), 9, 5, 40);
        RenderUtils.drawRoundedRect(ctx, Math.round(popup.x), Math.round(popup.y),
            Math.round(popup.w), Math.round(popup.h), 9,
            NativeTheme.opacity(NativeTheme.HEADER, alpha));

        Rect field = pickerField(popup);
        RenderUtils.drawGradientHorizontal(ctx, Math.round(field.x), Math.round(field.y),
            Math.round(field.w), Math.round(field.h),
            NativeTheme.withAlpha(0x00FFFFFF, Math.round(255f * alpha)),
            NativeTheme.hsv(setting.hue, 1f, 1f, Math.round(255f * alpha)));
        RenderUtils.drawGradientVertical(ctx, Math.round(field.x), Math.round(field.y),
            Math.round(field.w), Math.round(field.h),
            NativeTheme.withAlpha(0x00000000, 0), NativeTheme.withAlpha(0x000000, Math.round(255f * alpha)));

        float saturation = NativeTheme.saturationOf(colour);
        float value = NativeTheme.valueOf(colour);
        RenderUtils.drawRoundedRectBorder(ctx,
            Math.round(field.x + field.w * saturation - 3.2f),
            Math.round(field.y + field.h * (1f - value) - 3.2f), 7, 7, 3,
            NativeTheme.withAlpha(0xFFFFFFFF, Math.round(255f * alpha)), 1);

        Rect strip = pickerStrip(field);
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            int x0 = Math.round(strip.x + strip.w * i / steps);
            int x1 = Math.round(strip.x + strip.w * (i + 1) / steps);
            if (x1 <= x0) continue;
            int band = NativeTheme.hsv(i / (float) steps, 1f, 1f, Math.round(255f * alpha));
            ctx.fill(x0, Math.round(strip.y), x1, Math.round(strip.bottom()), band);
        }
        float markerX = strip.x + strip.w * clamp(setting.hue, 0f, 1f);
        ctx.fill(Math.round(markerX), Math.round(strip.y - 1f),
            Math.round(markerX) + 1, Math.round(strip.bottom()) + 1,
            NativeTheme.withAlpha(0xFFFFFFFF, Math.round(255f * alpha)));
    }

    private void dragPicker(SettingRow setting) {
        Rect popup = pickerPopup(setting);
        Rect field = pickerField(popup);
        int current = colourOf(setting.setting);
        if (pickerAxis == 0) {
            float saturation = clamp((mouseX - field.x) / Math.max(field.w, 1f), 0f, 1f);
            float value = 1f - clamp((mouseY - field.y) / Math.max(field.h, 1f), 0f, 1f);
            writeColour(setting, setting.hue, saturation, value, NativeTheme.alphaOf(current));
        } else if (pickerAxis == 1) {
            Rect strip = pickerStrip(field);
            setting.hue = clamp((mouseX - strip.x) / Math.max(strip.w, 1f), 0f, 1f);
            float max = Math.max(NativeTheme.red(current),
                Math.max(NativeTheme.green(current), NativeTheme.blue(current))) / 255f;
            float saturation = max <= 0f ? 1f : NativeTheme.saturationOf(current);
            writeColour(setting, setting.hue, saturation, max <= 0f ? 1f : max,
                NativeTheme.alphaOf(current));
        }
    }

    private void writeColour(SettingRow setting, float hue, float saturation, float value,
                             int alpha) {
        if (setting.setting instanceof ColorSetting colour) {
            colour.setColor(NativeTheme.hsv(hue, saturation, value, alpha));
        }
    }

    private void setRatio(Setting<?> setting, float ratio) {
        float r = clamp(ratio, 0f, 1f);
        if (setting instanceof FloatSetting f) f.setRatio(r);
        else if (setting instanceof IntSetting i) i.setRatio(r);
    }

    // ══════════════════════════════════════════════════════════════
    //  Draw helpers
    // ══════════════════════════════════════════════════════════════
    private static void fill(DrawContext ctx, Rect rect, int colour) {
        if (rect.empty()) return;
        int x0 = Math.round(rect.x);
        int y0 = Math.round(rect.y);
        int x1 = Math.round(rect.x + rect.w);
        int y1 = Math.round(rect.y + rect.h);
        if (x1 <= x0 || y1 <= y0) return;
        ctx.fill(x0, y0, x1, y1, colour);
    }

    private static void clipOn(DrawContext ctx, Rect rect) {
        ctx.enableScissor(Math.round(rect.x), Math.round(rect.y),
            Math.round(rect.x + rect.w), Math.round(rect.y + rect.h));
    }

    private float textWidth(String value, float scale) {
        return this.textRenderer.getWidth(value) * scale;
    }

    /** Top of a row's text box, so the label sits optically centred. */
    private static float textY(float rowTop, float rowH, float scale) {
        return rowTop + (rowH - FONT_H * scale) * 0.5f;
    }

    private void text(DrawContext ctx, String value, float x, float y, float scale, int colour) {
        if (value == null || value.isEmpty()) return;
        if (scale == 1f) {
            ctx.drawText(this.textRenderer, value, Math.round(x), Math.round(y), colour, false);
            return;
        }
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(this.textRenderer, value, Math.round(x / scale), Math.round(y / scale),
            colour, false);
        ctx.getMatrices().popMatrix();
    }

    /** Right-aligned text; returns the x it started at. */
    private float textRight(DrawContext ctx, String value, float right, float y, float scale,
                            int colour) {
        float start = right - textWidth(value, scale);
        text(ctx, value, start, y, scale, colour);
        return start;
    }

    /** Truncate to fit, the way a fixed-width dropdown has to. */
    private String fit(String value, float maxUnits, float scale) {
        if (value == null) return "";
        if (textWidth(value, scale) <= maxUnits) return value;
        String out = value;
        while (out.length() > 1 && textWidth(out + "..", scale) > maxUnits) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }

    private static String name(ModuleRow row) { return row.module.getName(); }

    // ══════════════════════════════════════════════════════════════
    //  Input
    // ══════════════════════════════════════════════════════════════
    @Override
    public boolean mouseClicked(Click click, boolean repeat) {
        mouseX = (float) click.x();
        mouseY = (float) click.y();
        if (isClosing()) return true;
        mouseDown = click.button() == 0;
        return pressAt(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(Click click) {
        mouseDown = false;
        dragPanel = null;
        activeSlider = null;
        activePicker = null;
        pickerAxis = -1;
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        mouseX = (float) click.x();
        mouseY = (float) click.y();
        if (dragPanel != null) {
            dragPanel.x = clamp(mouseX - dragDX, -panelW + 40f,
                Math.max(this.width - 40f, -panelW + 40f));
            dragPanel.y = clamp(mouseY - dragDY, 0f, Math.max(this.height - 20f, 0f));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        mouseX = (float) mx;
        mouseY = (float) my;
        // The wheel scrolls whichever panel the pointer is over. Panels with
        // nothing to scroll swallow it silently rather than letting it fall
        // through to the game.
        for (Panel panel : panels) {
            if (!panel.outer.contains(mx, my) || panel.maxScroll <= 0f) continue;
            panel.scrollTarget = clamp(panel.scrollTarget - (float) vertical * SCROLL_STEP,
                0f, panel.maxScroll);
            break;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int code = keyInput.key();

        StringSetting focused = findFocusedStringSetting();
        if (focused != null) {
            if (code == GLFW.GLFW_KEY_BACKSPACE) focused.backspace();
            else if (code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_ENTER) {
                focused.setFocused(false);
            }
            return true;
        }

        KeybindSetting waiting = findWaitingKeybindSetting();
        if (waiting != null) {
            if (code == GLFW.GLFW_KEY_ESCAPE) waiting.cancelWaiting();
            else waiting.setKey(code);
            return true;
        }

        if (editingName) {
            if (code == GLFW.GLFW_KEY_ESCAPE) {
                editingName = false;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (!configName.isEmpty()) configName = configName.substring(0, configName.length() - 1);
            } else if (code == GLFW.GLFW_KEY_ENTER && !configName.isEmpty()) {
                saveNamed(configName);
                editingName = false;
            }
            return true;
        }

        SettingRow listening = keyListener();
        if (listening != null) {
            if (listening.setting instanceof KeybindSetting setting) {
                // 0 is the client's "unbound" — Escape clears rather than binds.
                setting.setKey(code == GLFW.GLFW_KEY_ESCAPE ? 0 : code);
            }
            listening.listening = false;
            return true;
        }

        if (bindRow != null) {
            if (code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_BACKSPACE) {
                bindRow.module.setKeybind(-1);
            } else {
                bindRow.module.setKeybind(code);
            }
            bindRow.binding = false;
            bindRow = null;
            return true;
        }

        if (code == GLFW.GLFW_KEY_ESCAPE) {
            if (pickerOpenAnywhere()) closePickers();
            else requestClose();
            return true;
        }
        if (code == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            // Debounce: the press that opened the menu would otherwise slam it
            // shut again as GLFW autorepeats while the key is still held.
            if (System.currentTimeMillis() - openMs > CLOSE_DEBOUNCE_MS) requestClose();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        char chr = (char) charInput.codepoint();
        if (editingName) {
            if (validConfigChar(chr) && configName.length() < MAX_CONFIG_NAME) {
                configName = configName + chr;
            }
            return true;
        }
        return super.charTyped(charInput);
    }

    // ══════════════════════════════════════════════════════════════
    //  Pressing
    // ══════════════════════════════════════════════════════════════
    private boolean pressAt(double x, double y, int button) {
        stopListening();

        // An open picker sits above everything, including its own panel.
        for (Panel panel : panels) {
            for (ModuleRow row : panel.rows) {
                for (SettingRow setting : row.settings) {
                    if (!setting.pickerOpen || setting.picker < 0.5f
                        || setting.control.empty()) continue;
                    Rect popup = pickerPopup(setting);
                    if (!popup.contains(x, y)) continue;
                    Rect field = pickerField(popup);
                    activePicker = setting;
                    pickerAxis = y < field.bottom() + PICKER_PAD * 0.5f ? 0 : 1;
                    return true;
                }
            }
        }

        if (!contains(x, y)) {
            editingName = false;
            return false;
        }

        for (Panel panel : panels) {
            if (!panel.header.contains(x, y)) continue;
            if (button == 1) {
                panel.expanded = !panel.expanded;
            } else if (button == 0) {
                dragPanel = panel;
                dragDX = (float) x - panel.x;
                dragDY = (float) y - panel.y;
            }
            return true;
        }

        for (Panel panel : panels) {
            if (panel.configs) {
                if (panel.expand > 0.05f && pressConfigs(panel, x, y, button)) return true;
                continue;
            }
            if (panel.actions) {
                if (panel.expand > 0.05f && pressActions(panel, x, y, button)) return true;
                continue;
            }
            if (!panel.expanded || panel.expand < 0.05f) continue;
            // Only the visible viewport is clickable: a row scrolled out of
            // sight must not answer clicks aimed at whatever is drawn over it.
            if (!panel.bodyClip.contains(x, y)) continue;

            for (ModuleRow row : panel.rows) {
                if (!row.head.empty() && row.head.contains(x, y)) {
                    if (button == 0) {
                        row.module.toggle();
                    } else if (button == 1) {
                        row.expanded = !row.expanded;
                    } else if (button == 2) {
                        if (bindRow != null) bindRow.binding = false;
                        bindRow = row;
                        row.binding = true;
                    }
                    return true;
                }
                if (!row.expanded || row.expand < 0.05f) continue;
                for (SettingRow setting : row.settings) {
                    if (setting.bounds.empty() || !setting.bounds.contains(x, y)) continue;
                    Setting<?> value = setting.setting;
                    if (value instanceof BooleanSetting bool) {
                        if (button == 0) bool.toggle();
                    } else if (value instanceof FloatSetting || value instanceof IntSetting) {
                        if (button == 0) {
                            activeSlider = setting;
                            if (!setting.control.empty()) {
                                setRatio(value, (float) (x - setting.control.x) / setting.control.w);
                            }
                        }
                    } else if (value instanceof EnumSetting options) {
                        float localY = (float) y - setting.bounds.y;
                        if (localY < ROW_H) {
                            if (button == 0 || button == 1) setting.expanded = !setting.expanded;
                        } else if (setting.expand > 0.3f) {
                            int item = (int) ((localY - ROW_H) / ROW_H);
                            if (item >= 0 && item < options.getOptions().size()) {
                                options.setSelectedIndex(item);
                            }
                        }
                    } else if (value instanceof KeybindSetting) {
                        if (button == 0) setting.listening = true;
                    } else if (value instanceof StringSetting text) {
                        if (button == 0) text.setFocused(true);
                    } else if (value instanceof ColorSetting colour) {
                        if (button == 0 || button == 1) {
                            boolean wasOpen = setting.pickerOpen;
                            closePickers();
                            setting.pickerOpen = !wasOpen;
                            if (setting.pickerOpen) setting.hue = NativeTheme.hueOf(colour.getColor());
                        }
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private boolean contains(double x, double y) {
        for (Panel panel : panels) {
            if (panel.outer.contains(x, y)) return true;
        }
        return false;
    }

    private void stopListening() {
        for (Panel panel : panels) {
            for (ModuleRow row : panel.rows) {
                for (SettingRow setting : row.settings) {
                    if (setting.listening) setting.listening = false;
                }
            }
        }
    }

    private SettingRow keyListener() {
        for (Panel panel : panels) {
            for (ModuleRow row : panel.rows) {
                for (SettingRow setting : row.settings) {
                    if (setting.listening) return setting;
                }
            }
        }
        return null;
    }

    private boolean pickerOpenAnywhere() {
        for (Panel panel : panels) {
            for (ModuleRow row : panel.rows) {
                for (SettingRow setting : row.settings) {
                    if (setting.pickerOpen) return true;
                }
            }
        }
        return false;
    }

    private void closePickers() {
        for (Panel panel : panels) {
            for (ModuleRow row : panel.rows) {
                for (SettingRow setting : row.settings) setting.pickerOpen = false;
            }
        }
        activePicker = null;
        pickerAxis = -1;
    }

    // ══════════════════════════════════════════════════════════════
    //  Configs
    // ══════════════════════════════════════════════════════════════
    private void refreshConfigs() {
        configs.clear();
        for (String name : ModuleConfig.listNamed()) configs.add(new ConfigEntry(name));
        if (selectedConfig >= configs.size()) selectedConfig = -1;
    }

    private boolean pressConfigs(Panel panel, double x, double y, int button) {
        if (button != 0 || !panel.bodyClip.contains(x, y)) return false;
        for (int i = 0; i < configs.size(); i++) {
            ConfigEntry entry = configs.get(i);
            if (!entry.bounds.empty() && entry.bounds.contains(x, y)) {
                selectedConfig = i;
                configName = entry.name;
                editingName = false;
                return true;
            }
        }
        if (hovered(nameField)) {
            editingName = true;
            return true;
        }
        if (hovered(loadButton)) {
            editingName = false;
            if (selectedConfig >= 0 && selectedConfig < configs.size()) {
                loadNamed(configs.get(selectedConfig).name);
            }
            return true;
        }
        if (hovered(saveButton)) {
            editingName = false;
            if (!configName.isEmpty()) saveNamed(configName);
            return true;
        }
        if (hovered(deleteButton)) {
            editingName = false;
            if (selectedConfig >= 0 && selectedConfig < configs.size()) {
                deleteNamed(configs.get(selectedConfig).name);
            }
            return true;
        }
        return false;
    }

    private void saveNamed(String name) {
        ModuleManager mm = manager();
        if (mm == null) return;
        if (ModuleConfig.saveNamed(mm, name)) {
            LucidClient.LOGGER.info("Config saved: {}", name);
        } else {
            LucidClient.LOGGER.warn("Config could not be saved: {}", name);
        }
        refreshConfigs();
    }

    private void loadNamed(String name) {
        ModuleManager mm = manager();
        if (mm == null) return;
        if (ModuleConfig.loadNamed(mm, name)) {
            LucidClient.LOGGER.info("Config loaded: {}", name);
        } else {
            LucidClient.LOGGER.warn("Config could not be loaded: {}", name);
        }
    }

    private void deleteNamed(String name) {
        if (ModuleConfig.deleteNamed(name)) {
            LucidClient.LOGGER.info("Config deleted: {}", name);
        } else {
            LucidClient.LOGGER.warn("Config could not be deleted: {}", name);
        }
        refreshConfigs();
    }

    private static boolean validConfigChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
            || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    // ══════════════════════════════════════════════════════════════
    //  Client actions
    // ══════════════════════════════════════════════════════════════
    private boolean pressActions(Panel panel, double x, double y, int button) {
        if (button != 0 || !panel.bodyClip.contains(x, y)) return false;
        for (int i = 0; i < actions.size(); i++) {
            ActionRow action = actions.get(i);
            if (action.bounds.empty() || !action.bounds.contains(x, y)) continue;
            runAction(i);
            return true;
        }
        return false;
    }

    private void runAction(int index) {
        ModuleManager mm = manager();
        switch (index) {
            case 0 -> {
                if (mm == null) return;
                ModuleConfig.load(mm);
                ModuleConfig.applyPendingEnabled(mm);
                LucidClient.LOGGER.info("Config reloaded from disk.");
            }
            case 1 -> {
                if (mm == null) return;
                ModuleConfig.save(mm);
                LucidClient.LOGGER.info("Config written to disk.");
            }
            case 2 -> {
                try {
                    java.nio.file.Files.createDirectories(ModuleConfig.configsDir());
                    Util.getOperatingSystem().open(ModuleConfig.configsDir());
                } catch (Throwable t) {
                    LucidClient.LOGGER.warn("Could not open the config folder: {}", t.toString());
                }
            }
            case 3 -> {
                if (mm == null) return;
                for (Module module : mm.getAll()) {
                    if (module.isEnabled()) module.setEnabled(false);
                }
                LucidClient.LOGGER.info("All modules disabled.");
            }
            default -> { }
        }
    }

    private ModuleManager manager() {
        return client == null ? null : client.getModuleManager();
    }
}
