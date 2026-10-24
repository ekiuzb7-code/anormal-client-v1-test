package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.ModuleManager;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.ui.theme.NativeTheme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Array List — the module list, in the native client's design.
 *
 * <h3>Look</h3>
 * One 12-unit row per enabled module, right-aligned so every row ends at the
 * screen edge and the block has a clean ragged left edge. Each row carries a
 * 1px stepped outline: a left bar, a bridge to the neighbour wherever the
 * widths differ, and a floor under the last row — so the whole block reads as
 * one shape rather than as a stack of boxes.
 *
 * <p>The row colour is the travelling accent shimmer of the native list
 * (accent → light accent, one sine per row, phase-shifted down the list), and
 * the row itself slides out to the right when a module is switched off.
 *
 * <h3>Settings</h3>
 * <ul>
 *   <li><b>Sort</b> — Length (widest name on top, the native ordering) or
 *       Alphabetical.</li>
 *   <li><b>Color Mode</b> — Accent (the native shimmer), Rainbow (animated hue
 *       per row) or Static (Array Color).</li>
 *   <li><b>Array Color</b> — the row colour for Static.</li>
 *   <li><b>Background</b> — the dark row behind each name.</li>
 * </ul>
 */
public class ArraylistModule extends Module {

    // ── Geometry (GUI units, from the native Hud.cpp) ──────────
    private static final int ROW_H = 12;
    private static final int PAD = 3;
    /** Margin from the top of the screen to the first row. */
    private static final float LIST_TOP = 2f;
    /** Margin from the right edge — the default anchor. */
    private static final float LIST_RIGHT = 0f;
    /** Black @ 130/255 — the per-row background of the native list. */
    private static final int LIST_BG = 0x82000000;

    private final EnumSetting sort = addSetting(
        new EnumSetting("Sort", "How the list orders modules. Length = widest name on top.",
            List.of("Length", "Alphabetical"), "Length"));
    private final EnumSetting colorMode = addSetting(
        new EnumSetting("Color Mode", "Accent = the native shimmer. Rainbow = animated hue per row. Static = one fixed color.",
            List.of("Accent", "Rainbow", "Static"), "Accent"));
    private final ColorSetting arrayColor = addSetting(
        new ColorSetting("Array Color", "Row color used when Color Mode is Static. Click to pick.",
            0xFFBDA4BD));
    private final BooleanSetting background = addSetting(
        new BooleanSetting("Background", "Draw the dark row behind each module name.", true));

    /** Per-module animation state: alpha, slide-out offset and current row y. */
    private static final class Entry {
        float alpha;
        float offset;
        float y;
        boolean seen;
    }

    private final Map<Module, Entry> entries = new HashMap<>();
    private long lastNanos;

    public ArraylistModule() {
        super("Array List",
            "Displays all enabled modules on the right side of the screen.",
            Category.RENDER);
    }

    /** One visible row, measured and ready to place. */
    private record Visible(Module module, String text, int width) {}

    /** A row after sorting and placement. */
    private record Placed(Visible item, float x, float y, float alpha) {}

    /** Called from LucidClient's HUD render pass (screen-space overlay). */
    public void draw(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        if (tr == null) return;

        LucidClient lucid = LucidClient.getInstance();
        ModuleManager mgr = lucid != null ? lucid.getModuleManager() : null;
        if (mgr == null) return;

        // Frame-rate independent animation, from the frame clock rather than
        // the tick clock — the list has to glide between ticks.
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        float blend = 1f - (float) Math.exp(-14f * dt);

        int screenW = client.getWindow().getScaledWidth();
        float rightEdge = screenW - LIST_RIGHT;

        boolean alphabetical = "Alphabetical".equals(sort.get());
        boolean rainbow = "Rainbow".equals(colorMode.get());
        boolean staticMode = "Static".equals(colorMode.get());
        int staticColour = arrayColor.getColor();
        boolean drawBackground = background.isEnabled();

        // ── Gather ─────────────────────────────────────────────
        for (Entry entry : entries.values()) entry.seen = false;

        List<Visible> visible = new ArrayList<>();
        for (Module module : mgr.getAll()) {
            // The client's own chrome has nothing to say in its own list.
            if (module == this) continue;
            Entry entry = entries.computeIfAbsent(module, key -> new Entry());
            if (!module.isEnabled() && entry.alpha < 0.004f) continue;
            entry.seen = true;
            visible.add(new Visible(module, module.getName(), tr.getWidth(module.getName())));
        }

        // Widest first gives the block a clean diagonal edge instead of a
        // ragged one, which is the whole point of an array list.
        if (alphabetical) {
            visible.sort(Comparator.comparing(Visible::text, String.CASE_INSENSITIVE_ORDER));
        } else {
            visible.sort(Comparator.comparingInt(Visible::width).reversed());
        }

        // ── Place ──────────────────────────────────────────────
        List<Placed> placed = new ArrayList<>();
        float y = LIST_TOP;
        float time = (System.currentTimeMillis() % 600_000L) / 1000f;
        for (Visible item : visible) {
            Entry entry = entries.get(item.module());
            if (entry == null) continue;
            float targetAlpha = item.module().isEnabled() ? 1f : 0f;
            float targetOffset = item.module().isEnabled() ? 0f : 1f;
            entry.alpha += (targetAlpha - entry.alpha) * blend;
            entry.offset += (targetOffset - entry.offset) * blend;
            entry.y = entry.y <= 0f ? y : entry.y + (y - entry.y) * blend;
            if (Math.abs(y - entry.y) < 0.5f) entry.y = y;

            if (entry.alpha > 0.004f) {
                // Switched off: the row leaves to the right, out of the edge.
                float slide = easeOutQuint(clamp01(entry.offset)) * (item.width() + 8f);
                placed.add(new Placed(item,
                    Math.round(rightEdge + slide - item.width() - PAD),
                    Math.round(entry.y), entry.alpha));
            }
            y += ROW_H;
        }

        // ── Render ─────────────────────────────────────────────
        int accent = NativeTheme.accent();
        int light = NativeTheme.mix(accent, 0xFFFFFFFF, 0.35f);

        for (int i = 0; i < placed.size(); i++) {
            Placed row = placed.get(i);
            float alpha = row.alpha();
            float rowX = row.x();
            float rowY = row.y();

            float fade = 0.5f + 0.5f * (float) Math.sin(time * 2.2f - i * 0.55f);
            int colour;
            if (staticMode) {
                colour = staticColour;
            } else if (rainbow) {
                float hue = ((System.currentTimeMillis() % 20000L) / 20000f + i * 0.07f) % 1f;
                colour = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.72f, 1f) & 0x00FFFFFF);
            } else {
                colour = NativeTheme.mix(accent, light, fade);
            }
            int fill = NativeTheme.opacity(colour, alpha);

            if (drawBackground) {
                ctx.fill(Math.round(rowX - PAD), Math.round(rowY), Math.round(rightEdge),
                    Math.round(rowY + ROW_H), NativeTheme.opacity(LIST_BG, alpha));
            }

            ctx.drawText(tr, row.item().text(), Math.round(rowX),
                Math.round(rowY) + (ROW_H - tr.fontHeight) / 2 + 1, fill, true);

            // Stepped outline: a left bar per row, bridged to the neighbour
            // wherever the widths differ, and a floor under the last row.
            ctx.fill(Math.round(rowX - PAD - 1f), Math.round(rowY), Math.round(rowX - PAD),
                Math.round(rowY + ROW_H), fill);

            if (i > 0) {
                float prevRight = placed.get(i - 1).x() - PAD;
                if (rowX - PAD > prevRight + 0.5f) {
                    ctx.fill(Math.round(prevRight - 1f), Math.round(rowY - 1f),
                        Math.round(rowX - PAD), Math.round(rowY), fill);
                }
            }
            if (i + 1 < placed.size()) {
                float nextRight = placed.get(i + 1).x() - PAD;
                if (rowX - PAD > nextRight + 0.5f) {
                    ctx.fill(Math.round(nextRight - 1f), Math.round(rowY + ROW_H),
                        Math.round(rowX - PAD), Math.round(rowY + ROW_H + 1f), fill);
                }
            } else {
                ctx.fill(Math.round(rowX - PAD - 1f), Math.round(rowY + ROW_H),
                    Math.round(rightEdge), Math.round(rowY + ROW_H + 1f), fill);
            }
        }

        entries.values().removeIf(entry -> !entry.seen && entry.alpha < 0.004f);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    private static float easeOutQuint(float t) {
        float inv = 1f - clamp01(t);
        return 1f - inv * inv * inv * inv * inv;
    }
}
