package dev.zaza.client.ui;

import dev.zaza.client.ZazaClient;
import dev.zaza.client.config.ConfigStore;
import dev.zaza.client.input.Keybinds;
import dev.zaza.client.module.Category;
import dev.zaza.client.module.Module;
import dev.zaza.client.module.impl.CombatModule;
import dev.zaza.client.setting.BoolSetting;
import dev.zaza.client.setting.EnumSetting;
import dev.zaza.client.setting.MultiSetting;
import dev.zaza.client.setting.Setting;
import dev.zaza.client.setting.SliderSetting;
import dev.zaza.client.render.Draw;
import dev.zaza.client.render.GlassPane;
import dev.zaza.client.render.Opacity;
import dev.zaza.client.render.Scissor;
import dev.zaza.client.render.Text;
import dev.zaza.client.render.Transform;
import dev.zaza.client.render.state.QuadState;
import dev.zaza.client.render.state.Submit;
import dev.zaza.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ZazaGui extends Screen {
    private static final int TAB_WORLD = 3;
    private static final int TAB_CONFIG = 4;
    private static final float TITLE_SIZE = 7.2f;
    private static final float TAB_SIZE = 6.2f;
    private static final float HEADER_SIZE = 6.4f;
    private static final float ROW_SIZE = 6.0f;
    private static final float SMALL_SIZE = 5.6f;
    private static final int CLOSE_COLLAPSE_MILLIS = 300;
    private static final int CLOSE_DISSOLVE_MILLIS = 240;

    private final Transition reveal = new Transition(0f, 300, 200,
            Transition.Easing.EASE_OUT_EXPO, Transition.Easing.EASE_IN_CUBIC);
    private final Transition underlineSlide = new Transition(1f, 260, Transition.Easing.EASE_OUT_EXPO);
    private final Transition cursorGlow = new Transition(0f, 160, Transition.Easing.EASE_OUT_CUBIC);
    private boolean underlineReady;
    private float underlineFromRelX;
    private float underlineFromWidth;
    private float underlineToRelX;
    private float underlineToWidth;
    private float underlineCurrentRelX;
    private float underlineCurrentWidth;
    private final List<Rect> tabAreas = new ArrayList<>();
    private Rect settingsToggleRect;
    private final Transition[] tabHover = new Transition[5];
    private final Map<String, Transition> toggles = new HashMap<>();
    private final Map<String, Transition> hovers = new HashMap<>();
    private final SliderBar volumeSlider = new SliderBar(0, 100, ZazaSettings::volume, ZazaSettings::setVolume);
    private final SegmentedControl soundControl = new SegmentedControl(
            new String[] {"glass", "click", "synth"}, ZazaSettings::soundIndex, index -> {
                ZazaSettings.setSound(index);
                UiSound.play(ZazaSettings.sound(), "tap");
            });
    private final SegmentedControl closeControl = new SegmentedControl(
            new String[] {"collapse", "dissolve"}, () -> ZazaSettings.closeAnimation().ordinal(),
            index -> ZazaSettings.setCloseAnimation(ZazaSettings.animations()[index]));

    private ZazaLayout layout;
    private int selectedTab = TAB_WORLD;
    private final Map<Category, Module> selection = new HashMap<>();
    private boolean closing;
    private boolean draggingPanel;
    private double grabX;
    private double grabY;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragSlider = -1;
    private int hoverThemeCard = -1;
    private boolean resetHover;
    private final List<Rect> hitRects = new ArrayList<>();
    private final List<SettingClick> settingClicks = new ArrayList<>();
    private SliderSetting dragSliderSetting;
    private float dragSliderX;
    private float dragSliderWidth;

    private record SettingClick(Rect rect, int dragId, Runnable action) {}

    private record Rect(float x, float y, float width, float height) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public ZazaGui() {
        super(Component.literal("zaza.hook"));
        for (int i = 0; i < tabHover.length; i++) tabHover[i] = new Transition(0f, 140);
        reveal.set(1f);
        UiSound.play(ZazaSettings.sound(), "open");
    }

    private Module selectedModule() {
        Category category = tabCategory();
        if (category == null) return null;
        Module selected = selection.get(category);
        List<Module> modules = ZazaClient.MODULES.byCategory(category);
        if (modules.isEmpty()) return null;
        if (selected == null || !modules.contains(selected)) {
            selected = modules.get(0);
            selection.put(category, selected);
        }
        return selected;
    }

    private Category tabCategory() {
        return switch (selectedTab) {
            case 0 -> Category.COMBAT;
            case 1 -> Category.VISUALS;
            case 2 -> Category.LOCAL;
            case TAB_WORLD -> Category.WORLD;
            default -> null;
        };
    }

    @Override
    protected void init() {
        layout = ZazaLayout.of(width, height, dragOffsetX, dragOffsetY, 1f);
    }

    @Override
    public void tick() {
        if (closing && reveal.value() <= 0f) {
            super.onClose();
        }
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        boolean collapse = ZazaSettings.closeAnimation() == ZazaSettings.CloseAnimation.COLLAPSE;
        reveal.set(0f, collapse ? CLOSE_COLLAPSE_MILLIS : CLOSE_DISSOLVE_MILLIS);
        UiSound.play(ZazaSettings.sound(), "close");
        ConfigStore.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Theme.update();
        ThemeAdjust.update();
        float shown = Math.clamp(reveal.value(), 0f, 1f);
        if (shown <= 0f) return;
        int dim = (int) (0x5A * shown);
        if (dim > 0) Draw.rect(graphics, 0, 0, width, height, 0, (dim << 24));
        float centerX = layout.x() + layout.width() * 0.5f;
        float centerY = layout.y() + layout.height() * 0.5f;
        if (closing && ZazaSettings.closeAnimation() == ZazaSettings.CloseAnimation.COLLAPSE) {
            drawCollapse(graphics, shown, mouseX, mouseY);
            return;
        }
        Transform.popIn(graphics, centerX, centerY, shown, 0.965f, () -> drawPanel(graphics, mouseX, mouseY));
        drawCursorGlow(graphics, mouseX, mouseY, shown);
    }

    /**
     * Collapse close: the whole panel squashes downward onto its base edge
     * while fading — reads like a sheet of glass settling out of existence.
     */
    private void drawCollapse(GuiGraphicsExtractor graphics, float shown, int mouseX, int mouseY) {
        float t = Math.clamp(shown, 0f, 1f);
        float eased = t * t;
        float squash = 0.25f + 0.75f * eased;
        float baseY = layout.y() + layout.height();
        // Anchor the squash to the panel's base edge: scale about the line y = baseY.
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(0f, baseY);
            graphics.pose().scale(1f, squash);
            graphics.pose().translate(0f, -baseY);
            Opacity.with(t, () -> drawPanel(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE));
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * Soft accent glow that trails the cursor while the GUI is open — faded
     * ring plus a small bright core, shrinking over non-interactive areas.
     */
    private void drawCursorGlow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float shown) {
        boolean overPanel = mouseX >= layout.x() && mouseX < layout.right()
                && mouseY >= layout.y() && mouseY < layout.bottom();
        boolean overTab = tabAt(mouseX, mouseY) != null;
        float target = overPanel ? (overTab ? 1f : 0.45f) : 0.22f;
        cursorGlow.set(target);
        float glow = Math.clamp(cursorGlow.value(), 0f, 1f) * shown;
        if (glow <= 0.02f) return;
        float size = (overTab ? 40f : 32f) * layout.scale();
        float core = size * 0.42f;
        float x = mouseX - size / 2f;
        float y = mouseY - size / 2f;
        int accent = Theme.ACCENT;
        Draw.rect(graphics, x, y, size, size, size / 2f, Colors.withAlpha(accent, 0.10f * glow));
        Draw.rect(graphics, mouseX - size * 0.32f, mouseY - size * 0.32f, size * 0.64f, size * 0.64f,
                size * 0.32f, Colors.withAlpha(accent, 0.16f * glow));
        Draw.border(graphics, x, y, size, size, size / 2f, Colors.withAlpha(accent, 0.55f * glow));
        Draw.rect(graphics, mouseX - core / 2f, mouseY - core / 2f, core, core, core / 2f,
                Colors.withAlpha(Colors.lighten(accent, 0.5f), 0.5f * glow));
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = layout.x();
        int y = layout.y();
        int w = layout.width();
        int h = layout.height();
        drawBackground(graphics, x, y, w, h);
        drawHeader(graphics, x, y, w, mouseX, mouseY);
        Scissor.clip(new ScreenRectangle(x + 1, layout.contentY(), w - 2, layout.contentHeight()), () -> {
            if (selectedTab == TAB_CONFIG) drawConfigPage(graphics, mouseX, mouseY);
            else drawModulePage(graphics, mouseX, mouseY);
        });
        drawStatusBar(graphics, x, y, w, h);
        Draw.bordered(graphics, x, y, w, h, ZazaLayout.RADIUS * layout.scale(), 0, Theme.BORDER);
    }

    private void drawBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        float radius = ZazaLayout.RADIUS * layout.scale();
        // Frosted-glass panel over the world, tinted by the active theme.
        GlassPane.submit(graphics, x, y, w, h, radius, Colors.withAlpha(Theme.MAIN, 0.88f));
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
        int barHeight = layout.tabBarHeight();
        float radius = ZazaLayout.RADIUS * layout.scale();
        Draw.rect(graphics, x, y, w, barHeight, radius, Colors.withAlpha(Theme.HEADER, 0.92f));
        Draw.rect(graphics, x, y + barHeight - layout.atLeastOne(1), w, layout.atLeastOne(1), 0, Theme.BORDER);
        float scale = layout.scale();
        float pad = layout.padding();
        Draw.rect(graphics, x + pad, y + barHeight / 2f - layout.scaled(4), layout.atLeastOne(1.6f),
                layout.scaled(8), layout.atLeastOne(0.8f), Theme.ACCENT);
        float baseline = Text.centeredBaseline("zaza.hook", TITLE_SIZE * scale, y + barHeight / 2f);
        Text.draw(graphics, List.of(new Text.Span("zaza.hook ", Theme.TEXT),
                        new Text.Span("[TRIDENT]", Theme.MUTED)),
                x + pad + layout.scaled(4), baseline, TITLE_SIZE * scale);
        drawTabs(graphics, x, y, w, barHeight, mouseX, mouseY);
    }

    private static final String[] TAB_LABELS = {"combat", "visuals", "local", "world", "configurações"};

    /** X where the tab strip begins: past the drawn title, never under it. */
    private float tabsStart(float x, float scale) {
        float titleX = x + layout.padding() + layout.scaled(4);
        float titleWidth = Text.width("zaza.hook ", TITLE_SIZE * scale)
                + Text.width("[TRIDENT]", TITLE_SIZE * scale);
        return titleX + titleWidth + layout.scaled(10);
    }

    /** Tab hit/draw geometry, shared by rendering and clicking. */
    private Rect tabRect(int index, float x, float w, int barHeight) {
        float scale = layout.scale();
        float tabY = y_for(barHeight);
        float tabHeight = barHeight - layout.scaled(8);
        float start = tabsStart(x, scale);
        float textWidth = Text.width(TAB_LABELS[TAB_CONFIG], TAB_SIZE * scale);
        float pillWidth = textWidth + layout.scaled(14);
        float pillX = x + w - layout.padding() - layout.scaled(2) - pillWidth;
        if (index < TAB_CONFIG) {
            float moduleEnd = Math.min(x + w * 0.72f, pillX - layout.scaled(10));
            float span = Math.max(layout.scaled(20), (moduleEnd - start) / TAB_CONFIG);
            return new Rect(start + span * index, tabY, span, tabHeight);
        }
        return new Rect(pillX, tabY, pillWidth, tabHeight);
    }

    private float y_for(int barHeight) {
        return layout.y() + layout.scaled(4);
    }

    private Rect underlineRect(int index, float x, float w, int barHeight) {
        Rect tab = tabRect(index, x, w, barHeight);
        float scale = layout.scale();
        float textWidth = Text.width(TAB_LABELS[index], TAB_SIZE * scale);
        float underlineWidth = Math.min(tab.width() - layout.scaled(6), textWidth + layout.scaled(4));
        float underlineX = tab.x() + (tab.width() - underlineWidth) / 2f;
        float underlineY = layout.y() + barHeight - layout.scaled(2.6f);
        return new Rect(underlineX, underlineY, underlineWidth, Math.max(1, layout.scaled(1.5f)));
    }

    private void selectTab(int index) {
        if (index == selectedTab) return;
        Rect current = new Rect(layout.x() + underlineCurrentRelX, 0, underlineCurrentWidth, 0);
        underlineFromRelX = current.x() - layout.x();
        underlineFromWidth = current.width();
        Rect target = underlineRect(index, layout.x(), layout.width(), layout.tabBarHeight());
        underlineToRelX = target.x() - layout.x();
        underlineToWidth = target.width();
        underlineSlide.snap(0f);
        underlineSlide.set(1f);
        selectedTab = index;
        UiSound.play(ZazaSettings.sound(), "switch");
    }

    private Integer tabAt(double mx, double my) {
        if (my < layout.y() || my >= layout.contentY()) return null;
        if (mx < layout.x() || mx >= layout.right()) return null;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            Rect area = tabRect(i, layout.x(), layout.width(), layout.tabBarHeight());
            if (area.contains(mx, my)) return i;
        }
        return null;
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int x, int y, int w, int barHeight, int mouseX, int mouseY) {
        float scale = layout.scale();
        float tabY = y + layout.scaled(4);
        float tabHeight = barHeight - layout.scaled(8);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            Rect tab = tabRect(i, x, w, barHeight);
            boolean hovered = tab.contains(mouseX, mouseY);
            tabHover[i].set(hovered || selectedTab == i ? 1f : 0f);
            float hover = Math.clamp(tabHover[i].value(), 0f, 1f);
            if (i == TAB_CONFIG) {
                Draw.bordered(graphics, tab.x(), tab.y() + layout.scaled(0.5f), tab.width(),
                        tabHeight - layout.scaled(1), tabHeight * 0.42f, 0,
                        Colors.withAlpha(selectedTab == TAB_CONFIG ? Theme.ACCENT
                                : Colors.lerp(Theme.BORDER, ThemeAdjust.tabPill, hover), 0.88f));
            } else if (hover > 0.01f && selectedTab != i) {
                float pillWidth = Text.width(TAB_LABELS[i], TAB_SIZE * scale) + layout.scaled(10);
                Draw.rect(graphics, tab.x() + (tab.width() - pillWidth) / 2f, tabY, pillWidth, tabHeight,
                        tabHeight * 0.4f, Colors.withAlpha(ThemeAdjust.tabPill, 0x50 * hover));
            }
            int color = selectedTab == i ? Theme.TEXT : Colors.lerp(Theme.MUTED, Theme.TEXT, hover);
            Text.drawCenteredX(graphics, TAB_LABELS[i], tab.x() + tab.width() / 2f, tabY + tabHeight / 2f,
                    TAB_SIZE * scale, color);
        }
        Rect configTab = tabRect(TAB_CONFIG, x, w, barHeight);
        for (int i = 0; i <= TAB_CONFIG; i++) {
            Rect tab = tabRect(Math.min(i, TAB_CONFIG - 1), x, w, barHeight);
            float dividerX = (i == TAB_CONFIG ? configTab.x() - layout.scaled(5)
                    : (i == 0 ? tab.x() - layout.scaled(5) : tab.x())) - layout.atLeastOne(0.3f);
            Draw.rect(graphics, dividerX, tabY + tabHeight * 0.22f, layout.atLeastOne(0.6f),
                    tabHeight * 0.56f, 0, Theme.BORDER);
        }
        Rect target = underlineRect(selectedTab, x, w, barHeight);
        if (!underlineReady) {
            underlineReady = true;
            underlineFromRelX = target.x() - x;
            underlineFromWidth = target.width();
            underlineToRelX = underlineFromRelX;
            underlineToWidth = underlineFromWidth;
            underlineSlide.snap(1f);
        }
        float t = Math.clamp(underlineSlide.value(), 0f, 1f);
        underlineCurrentRelX = underlineFromRelX + (underlineToRelX - underlineFromRelX) * t;
        underlineCurrentWidth = underlineFromWidth + (underlineToWidth - underlineFromWidth) * t;
        Draw.rect(graphics, x + underlineCurrentRelX, target.y(), underlineCurrentWidth, target.height(),
                target.height() / 2f, Theme.ACCENT);
    }

    private void drawModulePage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        settingsToggleRect = null;
        drawSidePanel(graphics, layout.leftPanelX(), "modules", true, mouseX, mouseY);
        drawSidePanel(graphics, layout.rightPanelX(), "settings", false, mouseX, mouseY);
    }

    private void drawSidePanel(GuiGraphicsExtractor graphics, int x, String title, boolean modules,
                               int mouseX, int mouseY) {
        float scale = layout.scale();
        int y = layout.panelY();
        int w = layout.panelWidth();
        int h = layout.panelHeight();
        Draw.rect(graphics, x, y, w, h, layout.scaled(4), Colors.withAlpha(Theme.PLATE, 0.96f));
        Draw.border(graphics, x, y, w, h, layout.scaled(4), Theme.BORDER);
        float pad = layout.scaled(6);
        float headerHeight = layout.scaled(13);
        Draw.rect(graphics, x + pad, y + pad, layout.atLeastOne(1.4f), layout.scaled(7),
                layout.atLeastOne(0.7f), Theme.ACCENT);
        Text.drawCentered(graphics, title, x + pad + layout.scaled(4),
                y + pad + layout.scaled(3.5f), HEADER_SIZE * scale, Theme.TEXT);
        Draw.rect(graphics, x + 1, y + headerHeight, w - 2, layout.atLeastOne(1), 0, Theme.BORDER);
        Scissor.clip(new ScreenRectangle(x + 1, (int) (y + headerHeight), w - 2,
                (int) (h - headerHeight - 1)), () -> {
            if (modules) drawModuleRows(graphics, x, y + headerHeight, w, h - headerHeight, mouseX, mouseY);
            else drawSettingsBody(graphics, x, y + headerHeight, w, h - headerHeight, mouseX, mouseY);
        });
    }

    private void drawModuleRows(GuiGraphicsExtractor graphics, int x, float y, int w, float h,
                                int mouseX, int mouseY) {
        Category category = tabCategory();
        if (category == null) return;
        List<Module> modules = ZazaClient.MODULES.byCategory(category);
        float scale = layout.scale();
        if (modules.isEmpty()) {
            drawEmptyState(graphics, x, y, w, h);
            return;
        }
        Module selected = selectedModule();
        float rowHeight = layout.scaled(12);
        float pad = layout.scaled(4);
        float cy = y + pad;
        for (Module module : modules) {
            boolean hovered = mouseX >= x + pad && mouseX < x + w - pad
                    && mouseY >= cy && mouseY < cy + rowHeight;
            Transition hover = hovers.computeIfAbsent("row:" + module.id(), k -> new Transition(0f, 130));
            hover.set(hovered ? 1f : 0f);
            float hoverValue = Math.clamp(hover.value(), 0f, 1f);
            if (hoverValue > 0.01f) {
                Draw.rect(graphics, x + pad, cy, w - pad * 2f, rowHeight, layout.scaled(3),
                        Colors.withAlpha(0xFFFFFFFF, 0x08 + 0x14 * hoverValue));
            }
            if (module == selected) {
                Draw.rect(graphics, x + pad, cy, layout.atLeastOne(1.2f), rowHeight, layout.atLeastOne(0.6f),
                        Colors.withAlpha(Theme.ACCENT, 0.55f));
            }
            Text.drawCentered(graphics, module.name(), x + pad + layout.scaled(5),
                    cy + rowHeight / 2f, ROW_SIZE * scale,
                    module.enabled() ? Theme.TEXT : Colors.lerp(Theme.MUTED, Theme.TEXT, hoverValue));
            drawToggle(graphics, x + w - pad - layout.scaled(18), cy + rowHeight / 2f, module, scale);
            cy += rowHeight + layout.scaled(2);
        }
    }

    private void drawEmptyState(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
        float scale = layout.scale();
        float centerY = y + h / 2f;
        Text.drawCenteredX(graphics, "no modules available", x + w / 2f, centerY - layout.scaled(5),
                ROW_SIZE * scale, Theme.TEXT);
        Text.drawCenteredX(graphics, "this panel is intentionally empty", x + w / 2f, centerY + layout.scaled(6),
                SMALL_SIZE * scale, Theme.MUTED);
    }

    private void drawToggle(GuiGraphicsExtractor graphics, float x, float centerY, Module module, float scale) {
        float width = layout.scaled(14);
        float height = layout.scaled(7);
        float y = centerY - height / 2f;
        Transition thumb = toggles.computeIfAbsent(module.id(), k -> new Transition(module.enabled() ? 1f : 0f,
                170, 170, Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_OUT_CUBIC));
        thumb.set(module.enabled() ? 1f : 0f);
        float on = Math.clamp(thumb.value(), 0f, 1f);
        Draw.rect(graphics, x, y, width, height, height / 2f, Colors.lerp(Theme.CONTROL, Theme.ACCENT, on));
        float inset = height * 0.18f;
        float knob = height - inset * 2f;
        float travel = width - inset * 2f - knob;
        float stretch = 1f + 0.22f * thumb.flight();
        float knobWidth = knob * stretch;
        float knobX = x + inset + travel * on - (knobWidth - knob) * 0.5f;
        Draw.rect(graphics, Math.clamp(knobX, x + inset, x + width - inset - knobWidth),
                centerY - knob / 2f, knobWidth, knob, knob / 2f,
                Colors.lerp(Colors.lighten(Theme.CONTROL, 0.45f), Theme.ON_ACCENT, on));
    }

    private void drawSettingsBody(GuiGraphicsExtractor graphics, int x, float y, int w, float h,
                                  int mouseX, int mouseY) {
        Module module = selectedModule();
        float scale = layout.scale();
        if (module == null) {
            drawEmptyState(graphics, x, y, w, h);
            return;
        }
        float pad = layout.scaled(6);
        float cy = y + pad;
        Text.draw(graphics, module.name(), x + pad, cy + layout.scaled(4), 7.2f * scale, Theme.TEXT);
        cy += layout.scaled(11);
        String chip = module.category().label;
        float chipWidth = Text.width(chip, SMALL_SIZE * scale) + layout.scaled(8);
        Draw.rect(graphics, x + pad, cy, chipWidth, layout.scaled(9), layout.scaled(4.5f),
                Colors.withAlpha(Theme.ACCENT, 0.19f));
        Text.drawCentered(graphics, chip, x + pad + layout.scaled(4), cy + layout.scaled(4.5f),
                SMALL_SIZE * scale, Theme.TEXT);
        cy += layout.scaled(16);
        Draw.rect(graphics, x + 1, cy, w - 2, layout.atLeastOne(1), 0, Theme.BORDER);
        cy += layout.scaled(6);
        Text.drawCentered(graphics, "enabled", x + pad, cy + layout.scaled(5), ROW_SIZE * scale, Theme.TEXT);
        float toggleX = x + w - pad - layout.scaled(18);
        settingsToggleRect = new Rect(toggleX - layout.scaled(2), cy, layout.scaled(22), layout.scaled(11));
        drawToggle(graphics, toggleX, cy + layout.scaled(5), module, scale);
        cy += layout.scaled(16);
        drawModuleSettings(graphics, module, x, w, cy, mouseX, mouseY);
    }

    /** Editable rows for modules that expose settings (currently the aim assist). */
    private void drawModuleSettings(GuiGraphicsExtractor graphics, Module module, float x, float w, float cy,
                                    int mouseX, int mouseY) {
        settingClicks.clear();
        if (!(module instanceof CombatModule combat)) {
            Text.draw(graphics, "no additional settings", x + layout.scaled(6), cy + layout.scaled(3),
                    SMALL_SIZE * layout.scale(), Theme.DIM);
            return;
        }
        float pad = layout.scaled(6);
        float rowHeight = layout.scaled(13);
        for (Setting<?> setting : combat.settings()) {
            if (!setting.isVisible()) continue;
            if (setting instanceof MultiSetting multi) {
                cy = drawMultiRow(graphics, multi, x, w, cy, mouseX, mouseY);
                continue;
            }
            float scale = layout.scale();
            Text.draw(graphics, setting.name(), x + pad,
                    Text.centeredBaseline(setting.name(), ROW_SIZE * scale, cy + rowHeight / 2f),
                    ROW_SIZE * scale, Theme.TEXT);
            float right = x + w - pad;
            if (setting instanceof SliderSetting slider) {
                float track = layout.scaled(64);
                float trackX = right - track - Text.width(slider.format(), SMALL_SIZE * scale) - layout.scaled(8);
                drawSettingSlider(graphics, slider, trackX, cy + rowHeight / 2f, track);
                settingClicks.add(new SettingClick(
                        new Rect(trackX - layout.scaled(3), cy, track + layout.scaled(6), rowHeight), 1,
                        () -> {
                            dragSliderSetting = slider;
                            dragSliderX = trackX;
                            dragSliderWidth = track;
                            slider.set(sliderValue(slider, trackX, track, mouseX));
                        }));
            } else if (setting instanceof BoolSetting bool) {
                drawSettingToggle(graphics, right - layout.scaled(14), cy + rowHeight / 2f, bool.get(),
                        "set:" + setting.id());
                settingClicks.add(new SettingClick(
                        new Rect(right - layout.scaled(16), cy, layout.scaled(18), rowHeight), -1, bool::toggle));
            } else if (setting instanceof EnumSetting option) {
                String value = option.selected();
                float valueWidth = Text.width(value, SMALL_SIZE * scale);
                float controlX = right - valueWidth - layout.scaled(16);
                Draw.rect(graphics, controlX, cy + rowHeight / 2f - layout.scaled(4.5f),
                        valueWidth + layout.scaled(16), layout.scaled(9), layout.scaled(4.5f),
                        Colors.withAlpha(Theme.ACCENT, 0.19f));
                Text.drawCentered(graphics, "<", controlX + layout.scaled(3), cy + rowHeight / 2f,
                        SMALL_SIZE * scale, Theme.MUTED);
                Text.drawCentered(graphics, value, controlX + layout.scaled(8), cy + rowHeight / 2f,
                        SMALL_SIZE * scale, Theme.TEXT);
                Text.drawCentered(graphics, ">", controlX + layout.scaled(8) + valueWidth + layout.scaled(2),
                        cy + rowHeight / 2f, SMALL_SIZE * scale, Theme.MUTED);
                settingClicks.add(new SettingClick(
                        new Rect(controlX, cy, valueWidth + layout.scaled(16), rowHeight), -1, option::cycle));
            }
            cy += rowHeight + layout.scaled(2);
        }
    }

    private float drawMultiRow(GuiGraphicsExtractor graphics, MultiSetting multi, float x, float w, float cy,
                               int mouseX, int mouseY) {
        float scale = layout.scale();
        float pad = layout.scaled(6);
        Text.draw(graphics, multi.name(), x + pad, cy + layout.scaled(4), ROW_SIZE * scale, Theme.TEXT);
        cy += layout.scaled(9);
        float chipX = x + pad;
        for (int option = 0; option < multi.options().length; option++) {
            if (!multi.isOptionVisible(option)) continue;
            String label = multi.options()[option];
            float chipWidth = Text.width(label, SMALL_SIZE * scale) + layout.scaled(8);
            if (chipX + chipWidth > x + w - pad) {
                chipX = x + pad;
                cy += layout.scaled(11);
            }
            boolean on = multi.has(option);
            boolean hovered = mouseX >= chipX && mouseX < chipX + chipWidth
                    && mouseY >= cy && mouseY < cy + layout.scaled(9);
            Draw.rect(graphics, chipX, cy, chipWidth, layout.scaled(9), layout.scaled(4.5f),
                    on ? Colors.withAlpha(Theme.ACCENT, hovered ? 0.38f : 0.28f)
                            : Colors.withAlpha(Theme.CONTROL, hovered ? 0.34f : 0.22f));
            Draw.border(graphics, chipX, cy, chipWidth, layout.scaled(9), layout.scaled(4.5f),
                    on ? Theme.ACCENT : Theme.BORDER);
            Text.drawCenteredX(graphics, label, chipX + chipWidth / 2f, cy + layout.scaled(4.5f),
                    SMALL_SIZE * scale, on ? Theme.TEXT : Theme.MUTED);
            int chosen = option;
            settingClicks.add(new SettingClick(new Rect(chipX, cy, chipWidth, layout.scaled(9)), -1,
                    () -> multi.toggle(chosen)));
            chipX += chipWidth + layout.scaled(4);
        }
        return cy + layout.scaled(14);
    }

    private void drawSettingSlider(GuiGraphicsExtractor graphics, SliderSetting slider, float x, float centerY,
                                   float trackWidth) {
        float scale = layout.scale();
        float trackH = Math.max(1f, 2f * scale);
        float trackY = centerY - trackH / 2f;
        float percent = (slider.get() - slider.min()) / (float) (slider.max() - slider.min());
        Draw.rect(graphics, x, trackY, trackWidth, trackH, trackH / 2f, Theme.TRACK);
        Draw.rect(graphics, x, trackY, Math.max(trackH, trackWidth * percent), trackH, trackH / 2f, Theme.ACCENT);
        float knob = 4.5f * scale;
        Draw.rect(graphics, x + trackWidth * percent - knob / 2f, centerY - knob / 2f, knob, knob, knob / 2f,
                Colors.lighten(Theme.ACCENT, 0.25f));
        Text.drawCentered(graphics, slider.format(), x + trackWidth + layout.scaled(4), centerY,
                SMALL_SIZE * scale, Theme.MUTED);
    }

    private void drawSettingToggle(GuiGraphicsExtractor graphics, float x, float centerY, boolean on, String key) {
        float width = layout.scaled(14);
        float height = layout.scaled(7);
        float y = centerY - height / 2f;
        Transition thumb = toggles.computeIfAbsent(key, k -> new Transition(on ? 1f : 0f,
                170, 170, Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_OUT_CUBIC));
        thumb.set(on ? 1f : 0f);
        float state = Math.clamp(thumb.value(), 0f, 1f);
        Draw.rect(graphics, x, y, width, height, height / 2f, Colors.lerp(Theme.CONTROL, Theme.ACCENT, state));
        float inset = height * 0.18f;
        float knob = height - inset * 2f;
        float travel = width - inset * 2f - knob;
        float knobWidth = knob * (1f + 0.22f * thumb.flight());
        float knobX = x + inset + travel * state - (knobWidth - knob) * 0.5f;
        Draw.rect(graphics, Math.clamp(knobX, x + inset, x + width - inset - knobWidth),
                centerY - knob / 2f, knobWidth, knob, knob / 2f,
                Colors.lerp(Colors.lighten(Theme.CONTROL, 0.45f), Theme.ON_ACCENT, state));
    }

    private static int sliderValue(SliderSetting slider, float trackX, float trackWidth, double px) {
        float fraction = Mth.clamp((float) ((px - trackX) / trackWidth), 0f, 1f);
        return (int) Math.round(slider.min() + fraction * (slider.max() - slider.min()));
    }

    private void drawStatusBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        Draw.rect(graphics, x, layout.contentBottom(), w, layout.statusBarHeight(), 0,
                Colors.withAlpha(Theme.HEADER, 0.9f));
        Draw.rect(graphics, x, layout.contentBottom(), w, layout.atLeastOne(1), 0, Theme.BORDER);
        long enabled = ZazaClient.MODULES.all().stream().filter(Module::enabled).count();
        String status = enabled == 0 ? "no modules loaded" : enabled + " modules enabled";
        Text.drawCentered(graphics, status, x + layout.padding(),
                layout.contentBottom() + layout.statusBarHeight() / 2f,
                SMALL_SIZE * layout.scale(), Theme.MUTED);
    }

    private void drawConfigPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        float scale = layout.scale();
        int x = layout.leftPanelX();
        int y = layout.panelY();
        int w = layout.width() - layout.padding() * 2;
        int h = layout.panelHeight();
        Draw.rect(graphics, x, y, w, h, layout.scaled(4), Colors.withAlpha(Theme.PLATE, 0.96f));
        Draw.border(graphics, x, y, w, h, layout.scaled(4), Theme.BORDER);
        float pad = layout.scaled(7);
        float cx = x + pad;
        float cy = y + pad;
        hitRects.clear();
        drawToolbar(graphics, cx, cy, w - pad * 2f, mouseX, mouseY);
        cy += layout.scaled(24);
        Draw.rect(graphics, cx, cy, layout.atLeastOne(1.4f), layout.scaled(7), layout.atLeastOne(0.7f), Theme.ACCENT);
        Text.draw(graphics, "complete color themes", cx + layout.scaled(4), cy + layout.scaled(3.5f),
                HEADER_SIZE * scale, Theme.TEXT);
        cy += layout.scaled(12);
        hoverThemeCard = -1;
        List<Theme.Definition> definitions = Theme.definitions();
        float cardWidth = (w - pad * 2f - layout.scaled(7)) / 2f;
        float cardHeight = layout.scaled(24);
        for (int i = 0; i < definitions.size(); i++) {
            int column = i % 2;
            int row = i / 2;
            float cardX = cx + column * (cardWidth + layout.scaled(7));
            float cardY = cy + row * (cardHeight + layout.scaled(6));
            drawThemeCard(graphics, definitions.get(i), i, cardX, cardY, cardWidth, cardHeight, mouseX, mouseY);
            hitRects.add(new Rect(cardX, cardY, cardWidth, cardHeight));
        }
        cy += 3 * (cardHeight + layout.scaled(6)) + layout.scaled(4);
        Draw.rect(graphics, cx, cy, layout.atLeastOne(1.4f), layout.scaled(7), layout.atLeastOne(0.7f), Theme.ACCENT);
        Text.draw(graphics, "the selected theme recolors this entire ui", cx + layout.scaled(4),
                cy + layout.scaled(3.5f), SMALL_SIZE * scale, Theme.MUTED);
    }

    private void drawToolbar(GuiGraphicsExtractor graphics, float x, float y, float width, int mouseX, int mouseY) {
        float scale = layout.scale();
        float rowHeight = layout.scaled(15);
        Draw.rect(graphics, x, y, width, rowHeight, layout.scaled(5), Colors.withAlpha(Theme.ROW, 0.85f));
        Draw.border(graphics, x, y, width, rowHeight, layout.scaled(5), Theme.BORDER);
        float cy = y + rowHeight / 2f;
        float cursor = x + layout.scaled(7);
        drawPowerIcon(graphics, cursor + layout.scaled(3.5f), cy, layout.scaled(7), Theme.ACCENT);
        cursor += layout.scaled(10);
        Text.drawCentered(graphics, "ui themes", cursor, cy, SMALL_SIZE * scale, Theme.TEXT);
        cursor += Text.width("ui themes", SMALL_SIZE * scale) + layout.scaled(8);
        Text.drawCentered(graphics, "sound", cursor, cy, SMALL_SIZE * scale, Theme.MUTED);
        cursor += Text.width("sound", SMALL_SIZE * scale) + layout.scaled(5);
        soundControl.place(cursor, cy - layout.scaled(5.5f), layout.scaled(11), scale);
        soundControl.draw(graphics, mouseX, mouseY);
        cursor += soundControl.width(scale) + layout.scaled(8);
        Text.drawCentered(graphics, "volume", cursor, cy, SMALL_SIZE * scale, Theme.MUTED);
        cursor += Text.width("volume", SMALL_SIZE * scale) + layout.scaled(5);
        volumeSlider.place(cursor, cy - layout.scaled(5), layout.scaled(10), scale);
        volumeSlider.trackWidth(layout.scaled(34));
        volumeSlider.draw(graphics, null, false);
        cursor += layout.scaled(34);
        Text.drawCentered(graphics, ZazaSettings.volume() + "%", cursor + layout.scaled(4), cy, SMALL_SIZE * scale, Theme.MUTED);
        float dividerX = x + width * 0.66f;
        Draw.rect(graphics, dividerX, y + layout.scaled(3), layout.atLeastOne(0.6f), rowHeight - layout.scaled(6),
                0, Theme.BORDER);
        cursor = dividerX + layout.scaled(8);
        Text.drawCentered(graphics, "close", cursor, cy, SMALL_SIZE * scale, Theme.MUTED);
        cursor += Text.width("close", SMALL_SIZE * scale) + layout.scaled(5);
        closeControl.place(cursor, cy - layout.scaled(5.5f), layout.scaled(11), scale);
        closeControl.draw(graphics, mouseX, mouseY);
        float resetWidth = Text.width("reset", SMALL_SIZE * scale);
        float resetX = x + width - layout.scaled(7) - resetWidth - layout.scaled(12);
        resetHover = mouseX >= resetX - layout.scaled(12) && mouseX < resetX + resetWidth + layout.scaled(2)
                && mouseY >= cy - layout.scaled(8) && mouseY < cy + layout.scaled(8);
        drawResetIcon(graphics, resetX, cy, layout.scaled(7), resetHover ? Theme.TEXT : Theme.MUTED);
        Text.drawCentered(graphics, "reset", resetX + layout.scaled(10), cy, SMALL_SIZE * scale,
                resetHover ? Theme.TEXT : Theme.DIM);
    }

    private void drawPowerIcon(GuiGraphicsExtractor graphics, float centerX, float centerY, float size, int color) {
        Draw.rect(graphics, centerX - size / 2f, centerY - size / 2f, size, size, size / 2f, color);
        float inner = size * 0.52f;
        Draw.rect(graphics, centerX - inner / 2f, centerY - inner / 2f, inner, inner, inner / 2f, Theme.ROW);
        Draw.rect(graphics, centerX - layout.atLeastOne(0.7f), centerY - size * 0.78f,
                Math.max(1, layout.scaled(1.4f)), size * 0.55f, layout.atLeastOne(0.7f), color);
    }

    private void drawResetIcon(GuiGraphicsExtractor graphics, float centerX, float centerY, float size, int color) {
        Draw.rect(graphics, centerX - size / 2f, centerY - size / 2f, size, size, size / 2f, color);
        float inner = size * 0.5f;
        Draw.rect(graphics, centerX - inner / 2f, centerY - inner / 2f, inner, inner, inner / 2f, Theme.ROW);
        Submit.submit(graphics, new QuadState(new Matrix3x2f(graphics.pose()),
                centerX + size * 0.12f, centerY - size * 0.85f,
                centerX + size * 0.62f, centerY - size * 0.52f,
                centerX + size * 0.12f, centerY - size * 0.18f,
                centerX + size * 0.12f, centerY - size * 0.85f, color));
    }

    private void drawThemeCard(GuiGraphicsExtractor graphics, Theme.Definition definition, int index,
                               float x, float y, float w, float h, int mouseX, int mouseY) {
        float scale = layout.scale();
        boolean selected = Theme.selected() == index;
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (hovered) hoverThemeCard = index;
        int border = selected ? Theme.ACCENT
                : Colors.lerp(Theme.BORDER, Colors.withAlpha(definition.accent(), 0.55f), hovered ? 1f : 0f);
        Draw.rect(graphics, x, y, w, h, layout.scaled(4),
                Colors.withAlpha(definition.slot(Theme.Slot.CARD), hovered && !selected ? 1f : 0.91f));
        Draw.border(graphics, x, y, w, h, layout.scaled(4), border);
        if (selected) {
            Draw.border(graphics, x, y, w, h, layout.scaled(4), Colors.withAlpha(Theme.ACCENT, 0.31f));
        }
        float swatch = layout.scaled(13);
        float swatchY = y + (h - swatch) / 2f;
        CubeSwatch.draw(graphics, x + layout.scaled(6), swatchY, swatch, definition.accent());
        Text.drawCentered(graphics, definition.label(), x + layout.scaled(6) + swatch + layout.scaled(6),
                y + h / 2f, ROW_SIZE * scale,
                selected ? Theme.TEXT : Colors.lerp(Theme.MUTED, Theme.TEXT, hovered ? 1f : 0f));
        if (selected) {
            Draw.icon(graphics, 0xe5ca, x + w - layout.scaled(12), y + h / 2f - layout.scaled(3.5f),
                    layout.scaled(7), Theme.ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        double mx = event.x();
        double my = event.y();
        if (layout.inTitleBar(mx, my)) {
            Integer tab = tabAt(mx, my);
            if (tab != null) {
                selectTab(tab);
                return true;
            }
            draggingPanel = true;
            grabX = mx - layout.x();
            grabY = my - layout.y();
            return true;
        }
        if (selectedTab == TAB_CONFIG && layout.inPanel(mx, my)) {
            if (soundControl.click(mx, my) || closeControl.click(mx, my)) {
                UiSound.play(ZazaSettings.sound(), "switch");
                return true;
            }
            if (volumeSlider.click(mx, my)) {
                dragSlider = 0;
                return true;
            }
            if (hoverThemeCard >= 0 && hoverThemeCard != Theme.selected()) {
                ZazaSettings.applyTheme(hoverThemeCard);
                UiSound.play(ZazaSettings.sound(), "switch");
                return true;
            }
            if (resetHover) {
                ZazaSettings.applyTheme(0);
                UiSound.play(ZazaSettings.sound(), "close");
                return true;
            }
            return true;
        }
        if (layout.inPanel(mx, my)) {
            // Toggles win over row selection.
            Module toggled = toggleAt(mx, my);
            if (toggled != null) {
                toggled.toggle();
                UiSound.play(ZazaSettings.sound(), "tap");
                return true;
            }
            Rect settingsToggle = settingsToggleRect;
            if (settingsToggle != null && settingsToggle.contains(mx, my)) {
                Module module = selectedModule();
                if (module != null) {
                    module.toggle();
                    UiSound.play(ZazaSettings.sound(), "tap");
                }
                return true;
            }
            for (SettingClick click : settingClicks) {
                if (click.rect().contains(mx, my)) {
                    click.action().run();
                    dragSlider = click.dragId();
                    UiSound.play(ZazaSettings.sound(), "tap");
                    return true;
                }
            }
            Module clicked = moduleAt(mx, my);
            if (clicked != null) {
                selection.put(tabCategory(), clicked);
                UiSound.play(ZazaSettings.sound(), "switch");
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Module moduleAt(double mx, double my) {
        Category category = tabCategory();
        if (category == null) return null;
        List<Module> modules = ZazaClient.MODULES.byCategory(category);
        if (modules.isEmpty()) return null;
        float x = layout.leftPanelX();
        float rowTop = layout.panelY() + layout.scaled(13);
        float rowHeight = layout.scaled(12);
        float pad = layout.scaled(4);
        float cy = rowTop + pad;
        for (Module module : modules) {
            if (mx >= x + pad && mx < x + layout.panelWidth() - pad
                    && my >= cy && my < cy + rowHeight) return module;
            cy += rowHeight + layout.scaled(2);
        }
        return null;
    }

    private Module toggleAt(double mx, double my) {
        float x = layout.leftPanelX() + layout.panelWidth() - layout.scaled(4) - layout.scaled(18);
        List<Module> modules = ZazaClient.MODULES.byCategory(tabCategory());
        float rowHeight = layout.scaled(12);
        float pad = layout.scaled(4);
        float cy = layout.panelY() + layout.scaled(13) + pad;
        for (Module module : modules) {
            if (mx >= x - layout.scaled(2) && mx < x + layout.scaled(18) + layout.scaled(2)
                    && my >= cy - layout.scaled(2) && my < cy + rowHeight + layout.scaled(2)) {
                return module;
            }
            cy += rowHeight + layout.scaled(2);
        }
        return null;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (closing) return true;
        if (draggingPanel) {
            dragOffsetX = (int) Math.round(event.x() - grabX - (width - layout.width()) / 2.0);
            dragOffsetY = (int) Math.round(event.y() - grabY - (height - layout.height()) / 2.0);
            layout = ZazaLayout.of(width, height, dragOffsetX, dragOffsetY, 1f);
            return true;
        }
        if (dragSlider == 0) volumeSlider.drag(event.x());
        if (dragSlider == 1 && dragSliderSetting != null) {
            dragSliderSetting.set(sliderValue(dragSliderSetting, dragSliderX, dragSliderWidth, event.x()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPanel = false;
        dragSlider = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) return true;
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (Keybinds.OPEN_GUI.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

}
