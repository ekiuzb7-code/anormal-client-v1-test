package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A floating tooltip that appears near the cursor after a short hover delay.
 * Managed centrally by the screen — components call {@link #request} when hovered.
 *
 * Supports multi-line text: explicit {@code \n} line breaks are honored, and
 * long lines are word-wrapped to {@link #MAX_TEXT_WIDTH} scaled pixels (the
 * ClickGUI uses this for module hover cards — first line the module name in
 * the bright text color, following lines muted). Single-line callers keep
 * working unchanged.
 *
 * Uses an appear animation (fade + scale handled by RenderUtils shadow falloff).
 *
 * All drawing goes through {@link RenderUtils} which delegates to the
 * standard 1.21.11 yarn-mapped {@link DrawContext#fill} /
 * {@link DrawContext#drawText} / {@link TextRenderer#getWidth} /
 * {@link TextRenderer#fontHeight} APIs.
 */
public class Tooltip {
    private String text;
    private long hoverStartMs;
    private float appearProgress = 0f;
    private static final long DELAY_MS = 380L;
    /** Max rendered width of the wrapped text body (scaled px), padding excluded. */
    private static final int MAX_TEXT_WIDTH = 240;
    /** Vertical gap between wrapped lines. */
    private static final int LINE_GAP = 1;

    public void request(String text) {
        if (text == null || text.isEmpty()) { clear(); return; }
        if (!text.equals(this.text)) {
            this.text = text;
            this.hoverStartMs = System.currentTimeMillis();
        }
    }

    public void clear() {
        this.text = null;
        this.hoverStartMs = 0;
    }

    public void update(long dtMs) {
        float target = (text != null && System.currentTimeMillis() - hoverStartMs >= DELAY_MS) ? 1f : 0f;
        float speed = 1f / 140f;
        appearProgress += (target - appearProgress) * Math.min(1f, speed * dtMs);
        if (appearProgress < 0.001f) appearProgress = 0;
        if (appearProgress > 0.999f) appearProgress = 1;
    }

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, int screenW, int screenH) {
        if (text == null || appearProgress <= 0.01f) return;
        if (System.currentTimeMillis() - hoverStartMs < DELAY_MS) return;

        // Measure (word-wrapped).
        List<String> lines = wrap(tr, text, MAX_TEXT_WIDTH);
        if (lines.isEmpty()) return;
        int textW = 0;
        for (String line : lines) {
            int w = tr.getWidth(line);
            if (w > textW) textW = w;
        }

        int padX = 10, padY = 6;
        int w = textW + padX * 2;
        int h = lines.size() * tr.fontHeight + (lines.size() - 1) * LINE_GAP + padY * 2;

        // Position (prefer right/below cursor, flip if off-screen)
        int x = mouseX + 14;
        int y = mouseY + 16;
        if (x + w > screenW - 4) x = mouseX - w - 14;
        if (y + h > screenH - 4) y = mouseY - h - 16;
        if (x < 4) x = 4;
        if (y < 4) y = 4;

        int alpha = (int) (255 * appearProgress);
        // Aurora glass tooltip: accent glow + glass fill + hairline border.
        int tr_ = LucidTheme.RADIUS_SMALL;
        RenderUtils.drawGlowBorder(ctx, x, y, w, h, tr_, Colors.AURORA_ACCENT, alpha);
        RenderUtils.drawRoundedRect(ctx, x, y, w, h, tr_,
            Colors.withAlpha(Colors.AURORA_GLASS, alpha));
        RenderUtils.drawRoundedRectBorder(ctx, x, y, w, h, tr_,
            Colors.withAlpha(Colors.AURORA_BORDER_STRONG, alpha), 1);
        RenderUtils.drawTopHighlight(ctx, x, y, w, 28 * alpha / 255);

        int lineY = y + padY - 1;
        for (int i = 0; i < lines.size(); i++) {
            // First line = title (bright); everything after = body (muted).
            int color = i == 0
                ? Colors.withAlpha(Colors.AURORA_TEXT, alpha)
                : Colors.withAlpha(Colors.AURORA_TEXT_DIM, alpha);
            RenderUtils.drawText(ctx, tr, lines.get(i), x + padX, lineY, color, false);
            lineY += tr.fontHeight + LINE_GAP;
        }
    }

    /**
     * Word-wrap text to a max pixel width: split on explicit {@code \n}
     * first, then greedily break each paragraph on spaces so no rendered
     * line exceeds {@code maxPx}. A single word longer than the limit is
     * kept whole (never mid-word broken).
     */
    private static List<String> wrap(TextRenderer tr, String text, int maxPx) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                if (word.isEmpty()) continue;
                String candidate = cur.length() == 0 ? word : cur + " " + word;
                if (tr.getWidth(candidate) > maxPx && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(candidate);
                }
            }
            if (cur.length() > 0) out.add(cur.toString());
        }
        return out;
    }
}
