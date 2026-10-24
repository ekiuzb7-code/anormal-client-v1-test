package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.music.AlbumArt;
import com.anormal.client.music.MusicEngine;
import com.anormal.client.music.MusicLibrary;
import com.anormal.client.music.MusicTrack;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A now-playing card on the HUD, with working transport controls.
 *
 * <p>The whole card is drawn inside one matrix push scaled by the {@code Scale} setting, so every
 * offset can stay in a fixed base coordinate system - hit testing converts the mouse back into that
 * space instead of duplicating the layout maths.</p>
 *
 * <p>Clicks are intercepted in the mouse mixin and cancelled, otherwise pressing play would also
 * swing at the block behind the card.</p>
 */
public class MusicPlayer extends Module {

    public final NumberSetting posX = new NumberSetting("Pos X", "Card X position", 12.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Card Y position", 210.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "Card scale", 1.0, 0.6, 2.0, 0.05);
    public final NumberSetting volume = new NumberSetting("Volume", "Playback volume", 0.8, 0.0, 1.0, 0.05);

    public final BooleanSetting showArt = new BooleanSetting("Show Album Art", "Draw the cover image", true);
    public final BooleanSetting showTimes = new BooleanSetting("Show Times", "Draw elapsed / remaining time", true);
    public final BooleanSetting showProgress = new BooleanSetting("Show Progress", "Draw the seek bar", true);
    public final BooleanSetting showVolume = new BooleanSetting("Show Volume", "Draw the volume slider", true);
    public final BooleanSetting background = new BooleanSetting("Background", "Dark rounded card", true);
    public final BooleanSetting glassStyle = new BooleanSetting("Glass Style",
            "Frosted glass panel with a glowing border (matches the Glassmorphism theme)", true);

    public final ColorSetting accent = new ColorSetting("Accent", "Progress and button colour",
            ColorUtils.rgba(90, 169, 255, 255));

    private static final int BASE_W = 268;
    private static final int BASE_H = 92;
    private static final int ART = 64;
    private static final int CONTENT_X = 80;

    private static MusicPlayer instance;

    // Hit boxes in base coordinates, rebuilt every frame.
    private int prevX, playX, nextX, btnY, btnSize;
    private int progX, progY, progW, progH;
    private int volX, volY, volW, volH;
    private int shuffleX, shuffleY, loopX, loopY;

    public MusicPlayer() {
        super("MusicPlayer", "Plays local audio files with a now-playing card on the HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(volume);
        addSetting(showArt);
        addSetting(showTimes);
        addSetting(showProgress);
        addSetting(showVolume);
        addSetting(background);
        addSetting(glassStyle);
        addSetting(accent);
        instance = this;

        MusicEngine.get().setEndListener(() -> MusicLibrary.get().onTrackEnd());
    }

    public static MusicPlayer getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        MusicEngine.get().setVolume(volume.getValue().floatValue());
    }

    @Override
    public void onDisable() {
        // Off means off: leaving the sound running would hide the only way to stop it.
        MusicEngine.get().stop();
    }

    @Override
    public void onTickAlways() {
        MusicEngine.get().setVolume(volume.getValue().floatValue());
    }

    // ------------------------------------------------------------------
    //  HUD
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (mc.player == null) return;

        MusicLibrary lib = MusicLibrary.get();
        MusicTrack track = lib.current();

        gx = posX.getValue().intValue();
        gy = posY.getValue().intValue();
        gs = scale.getValue().floatValue();

        int accentColor = accent.getColor();

        // Layout stays in base units; sx/sy/sw turn them into screen pixels. Drawing through a
        // matrix was the reason the HUD editor measured the card at (0,0): fill() records the
        // coordinates it is given, which are pre-transform, so the frame never followed posX/posY
        // and dragging only ever moved it downwards.
        btnSize = 14;
        btnY = 68;
        int center = CONTENT_X + (BASE_W - 10 - CONTENT_X) / 2;
        prevX = center - 30;
        playX = center - btnSize / 2;
        nextX = center + 16;

        progX = CONTENT_X;
        progY = 58;
        progW = BASE_W - 10 - CONTENT_X;
        progH = 3;

        volX = CONTENT_X;
        volY = 84;
        volW = 64;
        volH = 3;

        shuffleX = BASE_W - 74;
        shuffleY = 10;
        loopX = BASE_W - 40;
        loopY = 10;

        if (background.isEnabled()) {
            if (glassStyle.isEnabled()) {
                // Frosted panel. The colours are fixed here instead of taken from ThemeManager:
                // the default VAPE_V4 theme uses a 96%-opaque background and a black border, so
                // the "glass" panel rendered as a plain dark card with no see-through at all.
                int glassBg = 0x8C10141C;
                int glassBorder = 0x3CFFFFFF;
                int glassGlow = (accentColor & 0xFFFFFF) | 0x2E000000;
                com.anormal.client.util.RenderUtils.drawGlassPanel(ctx,
                        sx(0), sy(0), sx(BASE_W), sy(BASE_H), glassBg, glassBorder, glassGlow);

                // Top sheen: stacked translucent strips fake the light falloff of glass.
                for (int i = 0; i < 16; i++) {
                    int alpha = 22 - i;
                    if (alpha <= 0) break;
                    ctx.fill(sx(1), sy(1 + i), sx(BASE_W - 1), sy(2 + i), (alpha << 24) | 0xFFFFFF);
                }
                // Bottom vignette for depth.
                for (int i = 0; i < 10; i++) {
                    int alpha = 12 - i;
                    if (alpha <= 0) break;
                    ctx.fill(sx(1), sy(BASE_H - 2 - i), sx(BASE_W - 1), sy(BASE_H - 1 - i), alpha << 24);
                }
            } else {
                ctx.fill(sx(0), sy(0), sx(BASE_W), sy(BASE_H), 0xD0141418);
                ctx.fill(sx(0), sy(0), sx(BASE_W), sy(1), 0xFF3A3A42);
                ctx.fill(sx(0), sy(BASE_H - 1), sx(BASE_W), sy(BASE_H), 0xFF3A3A42);
                ctx.fill(sx(0), sy(0), sx(1), sy(BASE_H), 0xFF3A3A42);
                ctx.fill(sx(BASE_W - 1), sy(0), sx(BASE_W), sy(BASE_H), 0xFF3A3A42);
            }
        }

        if (track == null) {
            textS(ctx, Text.literal("No tracks"), CONTENT_X, 22, 0xFFFFFFFF, true);
            textS(ctx, Text.literal("Right-click the module to add songs"), CONTENT_X, 36, 0xFF8A8A96, false);
            return;
        }

        // Album art.
        if (showArt.isEnabled()) {
            Identifier art = AlbumArt.get(track);
            if (art != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, art,
                        sx(8), sy(8), 0f, 0f, sw(ART), sw(ART), ART, ART);
            } else {
                ctx.fill(sx(8), sy(8), sx(8 + ART), sy(8 + ART), 0xFF1F1F25);
                textS(ctx, Text.literal("\u266a"), 8 + ART / 2 - 3, 8 + ART / 2 - 4, accentColor, false);
            }
            ctx.fill(sx(8), sy(8), sx(8 + ART), sy(9), 0x40FFFFFF);
        }

        textS(ctx, Text.literal("now playing"), CONTENT_X, 10, 0xFF8A8A96, false);

        // Title scrolls when it does not fit: a long name used to run straight out of the card.
        int textArea = BASE_W - 10 - CONTENT_X;
        marquee(ctx, Text.literal(track.title()).styled(st -> st.withBold(true)),
                CONTENT_X, 22, textArea, 0xFFFFFFFF);

        // Artist is clipped, not scrolled - two moving lines would be unreadable.
        ctx.enableScissor(sx(CONTENT_X), sy(34), sx(CONTENT_X + textArea), sy(46));
        textS(ctx, Text.literal(track.artist()), CONTENT_X, 36, 0xFFB0B0BA, false);
        ctx.disableScissor();

        // Shuffle / loop indicators (clickable).
        boolean shuf = lib.shuffle();
        textS(ctx, Text.literal("SHUF"), shuffleX, shuffleY, shuf ? accentColor : 0xFF5A5A66, false);
        String loopLabel = switch (lib.loop()) {
            case OFF -> "LOOP";
            case ALL -> "ALL";
            case ONE -> "1";
        };
        textS(ctx, Text.literal(loopLabel), loopX, loopY,
                lib.loop() == MusicLibrary.Loop.OFF ? 0xFF5A5A66 : accentColor, false);

        long dur = MusicEngine.get().durationMs();
        long pos = MusicEngine.get().positionMs();
        float progress = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;

        if (showProgress.isEnabled()) {
            ctx.fill(sx(progX), sy(progY), sx(progX + progW), sy(progY + progH), 0xFF2A2A32);
            int filled = (int) (progW * progress);
            ctx.fill(sx(progX), sy(progY), sx(progX + filled), sy(progY + progH), accentColor);
            int knobX = progX + filled;
            // Soft glow under the filled part so the bar reads as "lit" on glass.
            ctx.fill(sx(progX), sy(progY + progH), sx(knobX), sy(progY + progH + 2),
                    (accentColor & 0x00FFFFFF) | 0x40000000);
            ctx.fill(sx(knobX - 1), sy(progY - 2), sx(knobX + 1), sy(progY + progH + 2), 0xFFFFFFFF);
        }

        // Transport.
        drawPrev(ctx, prevX, btnY, accentColor);
        if (MusicEngine.get().isPlaying()) drawPause(ctx, playX, btnY, accentColor);
        else drawPlay(ctx, playX, btnY, accentColor);
        drawNext(ctx, nextX, btnY, accentColor);

        if (showTimes.isEnabled()) {
            textS(ctx, Text.literal(fmt(pos)), CONTENT_X, btnY + 3, 0xFFB0B0BA, false);
            String right = "-" + fmt(Math.max(0, dur - pos));
            textS(ctx, Text.literal(right), BASE_W - 10 - mc.textRenderer.getWidth(right),
                    btnY + 3, 0xFFB0B0BA, false);
        }

        if (showVolume.isEnabled()) {
            float v = volume.getValue().floatValue();
            ctx.fill(sx(volX), sy(volY), sx(volX + volW), sy(volY + volH), 0xFF2A2A32);
            ctx.fill(sx(volX), sy(volY), sx(volX + (int) (volW * v)), sy(volY + volH), 0xFF6A6A76);
            textS(ctx, Text.literal("VOL"), volX + volW + 6, volY - 3, 0xFF6A6A76, false);
        }
    }

    // ------------------------------------------------------------------
    //  Scaling helpers - base units in, screen pixels out
    // ------------------------------------------------------------------

    private int gx, gy;
    private float gs = 1.0f;

    private int sx(int base) {
        return gx + Math.round(base * gs);
    }

    private int sy(int base) {
        return gy + Math.round(base * gs);
    }

    private int sw(int base) {
        return Math.max(1, Math.round(base * gs));
    }

    /**
     * Draws text clipped to {@code maxWidth}; scrolls it back and forth when it does not fit.
     *
     * <p>Ping-pong rather than a loop, so the start of the name is always readable within a couple
     * of seconds instead of waiting for a full pass.</p>
     */
    private void marquee(DrawContext ctx, Text text, int baseX, int baseY, int maxWidth, int color) {
        int textWidth = mc.textRenderer.getWidth(text);
        int offset = 0;

        if (textWidth > maxWidth) {
            long now = System.currentTimeMillis();
            int span = textWidth - maxWidth + 16;
            long cycle = 6000L;
            double phase = (now % cycle) / (double) cycle;
            double triangle = phase < 0.5 ? phase * 2.0 : (1.0 - phase) * 2.0;
            offset = (int) Math.round(span * triangle);
        }

        ctx.enableScissor(sx(baseX), sy(baseY - 1), sx(baseX + maxWidth), sy(baseY + 10));
        textS(ctx, text, baseX - offset, baseY, color, true);
        ctx.disableScissor();
    }

    /** Draws text at a base position, scaled with the card (text is not measured, so the matrix
     *  here cannot disturb the HUD editor's bounds). */
    private void textS(DrawContext ctx, Text text, int baseX, int baseY, int color, boolean shadow) {
        if (gs == 1.0f) {
            ctx.drawText(mc.textRenderer, text, sx(baseX), sy(baseY), color, shadow);
            return;
        }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(sx(baseX), sy(baseY));
        m.scale(gs, gs);
        ctx.drawText(mc.textRenderer, text, 0, 0, color, shadow);
        m.popMatrix();
    }

    private void drawPlay(DrawContext ctx, int bx, int by, int color) {
        int h = btnSize;
        for (int i = 0; i < h; i++) {
            int half = (int) ((h / 2.0) * (i / (double) h));
            ctx.fill(sx(bx + i), sy(by + h / 2 - half), sx(bx + i + 1), sy(by + h / 2 + half), color);
        }
    }

    private void drawPause(DrawContext ctx, int bx, int by, int color) {
        int h = btnSize;
        ctx.fill(sx(bx + 2), sy(by), sx(bx + 5), sy(by + h), color);
        ctx.fill(sx(bx + 8), sy(by), sx(bx + 11), sy(by + h), color);
    }

    private void drawPrev(DrawContext ctx, int bx, int by, int color) {
        int h = btnSize;
        ctx.fill(sx(bx + 1), sy(by), sx(bx + 3), sy(by + h), color);
        for (int i = 0; i < h; i++) {
            int half = (int) ((h / 2.0) * (i / (double) h));
            ctx.fill(sx(bx + h - i), sy(by + h / 2 - half),
                    sx(bx + h - i + 1), sy(by + h / 2 + half), color);
        }
    }

    private void drawNext(DrawContext ctx, int bx, int by, int color) {
        int h = btnSize;
        for (int i = 0; i < h; i++) {
            int half = (int) ((h / 2.0) * (i / (double) h));
            ctx.fill(sx(bx + i), sy(by + h / 2 - half), sx(bx + i + 1), sy(by + h / 2 + half), color);
        }
        ctx.fill(sx(bx + h - 2), sy(by), sx(bx + h), sy(by + h), color);
    }

    private static String fmt(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    /**
     * Handles a click on the card.
     *
     * @return true when the click was consumed (the caller cancels the vanilla click)
     */
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (mc.player == null || !isEnabled()) return false;

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        float s = scale.getValue().floatValue();

        // Convert the cursor into base units - the card is drawn scaled, so the raw pixel hit
        // boxes would drift further apart the larger the scale gets.
        double bx = (mouseX - x) / s;
        double by = (mouseY - y) / s;

        if (bx < 0 || bx > BASE_W || by < 0 || by > BASE_H) return false;

        MusicLibrary lib = MusicLibrary.get();

        if (hit(bx, by, prevX, btnY, btnSize, btnSize)) {
            lib.previous();
            return true;
        }
        if (hit(bx, by, playX, btnY, btnSize, btnSize)) {
            if (MusicEngine.get().isPlaying() || MusicEngine.get().isPaused()) {
                MusicEngine.get().togglePause();
            } else {
                lib.playCurrentOrFirst();
            }
            return true;
        }
        if (hit(bx, by, nextX, btnY, btnSize, btnSize)) {
            lib.next(true);
            return true;
        }
        if (showProgress.isEnabled() && hit(bx, by, progX, progY - 3, progW, progH + 6)) {
            long dur = MusicEngine.get().durationMs();
            if (dur > 0) {
                double ratio = Math.max(0, Math.min(1, (bx - progX) / (double) progW));
                MusicEngine.get().seek((long) (dur * ratio));
            }
            return true;
        }
        if (showVolume.isEnabled() && hit(bx, by, volX, volY - 3, volW, volH + 6)) {
            double ratio = Math.max(0, Math.min(1, (bx - volX) / (double) volW));
            volume.setValue(ratio);
            MusicEngine.get().setVolume((float) ratio);
            return true;
        }
        if (hit(bx, by, shuffleX, shuffleY - 2, 30, 10)) {
            lib.toggleShuffle();
            return true;
        }
        if (hit(bx, by, loopX, loopY - 2, 30, 10)) {
            lib.cycleLoop();
            return true;
        }

        // Clicking the card body does nothing, but still swallows the click so the player does
        // not swing at whatever is behind the HUD.
        return true;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
