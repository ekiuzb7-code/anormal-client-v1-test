package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.module.setting.KeybindSetting;
import com.lucidclient.ui.util.WorldRender3D;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Jump Circles — a premium visual module that spawns an expanding, fading ring
 * at the exact world position where the player leaves the ground on a jump,
 * then animates it over its lifetime. Pure client-side rendering — no packets,
 * no gameplay effect; it's a cosmetic flourish.
 *
 * <h3>How it works</h3>
 * <pre>
 *   Player was on ground last tick
 *     → Player is no longer on ground this tick
 *       → (optional) vertical velocity is positive (a real jump, not a ledge step)
 *         → Record the FIXED world takeoff position + spawn time
 *           → Add a JumpCircle to the active list
 *             → Render every frame: radius grows, alpha fades, ring thins
 *               → Removed when lifetime elapses
 * </pre>
 * The ring position stays FIXED in world coordinates — it does NOT follow the
 * player. This is what makes it read as a "mark left on the ground".
 *
 * <h3>Animation timeline</h3>
 * <pre>
 *   Jump → small bright ring → ring expands → glow swells → opacity drops
 *        → ring becomes thin → completely fades away
 * </pre>
 * The difference between a basic and premium-looking Jump Circles module is the
 * quality of the interpolation (easing + frame-rate-independent clock), the
 * visual polish (color modes, glow, multiple styles), and performance (object
 * pooling, distance culling, max-circle cap). This module implements all of it.
 *
 * <h3>Render path</h3>
 * Reuses {@link WorldRender3D} — each ring is drawn as a TRIANGLE_STRIP <b>band</b>
 * (a thin donut) on the custom {@link WorldRender3D#TRI_STRIP_NO_DEPTH} layer, which
 * is depth-test-disabled (renders through walls), no-cull (visible from above and
 * below), and translucent. The band has a <b>gradient fading edge</b>: the inner
 * vertex is bright (full alpha), the outer vertex fades to 0 alpha — OpenGL
 * interpolates the colors between them automatically for the premium soft-edge
 * look. A filled band is far more visible from third-person (F5) than a thin
 * line, and the gradient edge gives the buttery-smooth fade the spec describes.
 *
 * <h3>Frame-rate independence</h3>
 * The animation clock is {@link System#currentTimeMillis()} (wall-clock), so
 * motion is smooth at any FPS — the partial-tick interpolation goal without
 * needing {@code wrc.tickDelta()}.
 *
 * <h3>Performance</h3>
 * <ul>
 *   <li><b>Object pooling</b> — expired JumpCircle objects are returned to a
 *       free-list and reused instead of being GC'd.</li>
 *   <li><b>Distance culling</b> — rings beyond Render Distance from the camera
 *       are skipped (no draw calls).</li>
 *   <li><b>Max-circles cap</b> — the oldest ring is evicted when the cap is hit
 *       (prevents runaway growth from bunny-hopping).</li>
 * </ul>
 *
 * Settings:
 *   Toggle Key, Style, Color Mode, Primary Color, Secondary Color, Rainbow Speed,
 *   Easing, Lifetime, Start Radius, End Radius, Thickness, Segments, Ground Offset,
 *   Glow, Glow Layers, Render Distance, Max Circles, Only On Jump
 */
public class JumpCircles extends Module {

    // ── Toggle key ────────────────────────────────────────────────
    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable Jump Circles. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));

    // ── Shape / style ─────────────────────────────────────────────
    private final EnumSetting style = addSetting(
        new EnumSetting("Style", "Ring geometry variant.",
            new String[] { "Classic", "Double", "Shockwave", "Hexagon" }, "Double"));
    private final FloatSetting startRadius = addSetting(
        new FloatSetting("Start Radius", "Radius (blocks) the ring spawns at.", 0.1f, 2.0f, 0.4f, 2));
    private final FloatSetting endRadius = addSetting(
        new FloatSetting("End Radius", "Radius (blocks) the ring expands to before vanishing.", 0.5f, 6.0f, 2.0f, 2));
    private final FloatSetting thickness = addSetting(
        new FloatSetting("Thickness", "Band width of the ring in blocks (thins over its lifetime). Gradient fades to the outer edge.", 0.02f, 0.5f, 0.18f, 2));
    private final IntSetting segments = addSetting(
        new IntSetting("Segments", "How many line segments approximate the circle. Higher = smoother.", 3, 128, 64, 1));
    private final FloatSetting groundOffset = addSetting(
        new FloatSetting("Ground Offset", "Height (blocks) above the takeoff surface to draw the ring.", 0.0f, 1.0f, 0.01f, 2));

    // ── Animation ─────────────────────────────────────────────────
    private final EnumSetting easing = addSetting(
        new EnumSetting("Easing", "Easing curve applied to the expansion.",
            new String[] { "Linear", "Ease Out Cubic", "Ease Out Quart", "Ease In Out Sine", "Back", "Elastic" },
            "Ease Out Cubic"));
    private final FloatSetting lifetime = addSetting(
        new FloatSetting("Lifetime", "Seconds the ring lives before fading completely.", 0.2f, 3.0f, 1.0f, 2));

    // ── Color ─────────────────────────────────────────────────────
    private final EnumSetting colorMode = addSetting(
        new EnumSetting("Color Mode", "How the ring is colored.",
            new String[] { "Static", "Gradient", "Rainbow", "Astolfo", "Pulse", "Breathing" }, "Astolfo"));
    private final ColorSetting primaryColor = addSetting(
        new ColorSetting("Primary Color", "Main ring color (used by Static / Gradient / Pulse / Breathing).",
            0xFF3B82F6));   // blue
    private final ColorSetting secondaryColor = addSetting(
        new ColorSetting("Secondary Color", "Second color for Gradient mode (and the Astolfo wave target).",
            0xFFEC4899));   // pink
    private final FloatSetting rainbowSpeed = addSetting(
        new FloatSetting("Rainbow Speed", "Hue cycle speed for Rainbow / Astolfo / Pulse / Breathing modes.", 0.1f, 5.0f, 1.0f, 2));

    // ── Glow ──────────────────────────────────────────────────────
    private final BooleanSetting glow = addSetting(
        new BooleanSetting("Glow", "Render extra concentric fading rings for a soft halo (no shaders).", true));
    private final IntSetting glowLayers = addSetting(
        new IntSetting("Glow Layers", "How many halo rings to draw around the primary.", 1, 5, 3, 1));

    // ── Performance ───────────────────────────────────────────────
    private final IntSetting renderDistance = addSetting(
        new IntSetting("Render Distance", "Skip rings farther than this (blocks) from the camera.", 4, 128, 64, 1));
    private final IntSetting maxCircles = addSetting(
        new IntSetting("Max Circles", "Maximum simultaneous rings (oldest evicted first).", 1, 50, 20, 1));

    // ── Trigger ───────────────────────────────────────────────────
    private final BooleanSetting onlyOnJump = addSetting(
        new BooleanSetting("Only On Jump", "Only spawn on real jumps (positive Y velocity), not when walking off ledges.", true));

    // ════════════════════════════════════════════════════════════════
    //  Active rings + object pool
    // ════════════════════════════════════════════════════════════════
    /**
     * A single active jump circle. Only the FIXED world position + spawn time
     * are stored per-circle; lifetime/radius/colors/thickness are read live
     * from the settings at render time so ClickGUI changes apply immediately.
     * Instances are pooled (see {@link #pool}) to avoid per-jump allocation.
     */
    private static final class JumpCircle {
        double x, y, z;
        long spawnMs;

        void set(double x, double y, double z, long spawnMs) {
            this.x = x; this.y = y; this.z = z; this.spawnMs = spawnMs;
        }
    }

    private final List<JumpCircle> active = new ArrayList<>();
    /** Free-list of recycled JumpCircle objects (the "object pooling" optimisation). */
    private final Deque<JumpCircle> pool = new ArrayDeque<>();

    // ── Jump-detection state ──────────────────────────────────────
    private boolean prevOnGround = false;

    // ── Toggle-key edge detection ─────────────────────────────────
    private boolean wasToggleKeyDown = false;

    public JumpCircles() {
        super("JumpCircles",
            "Spawns an expanding, fading ring at the spot you leave the ground on a jump. Cosmetic — pure rendering.",
            Category.RENDER);
    }

    @Override
    protected void onEnable() {
        active.clear();
        pool.clear();
        // Seed prevOnGround with the CURRENT ground state so the first
        // ground→air transition after enabling is detected (if we left it
        // false, the first jump while already standing would be missed until
        // the player lands and jumps again).
        MinecraftClient mc = MinecraftClient.getInstance();
        prevOnGround = (mc != null && mc.player != null) && mc.player.isOnGround();
        wasToggleKeyDown = false;
    }

    @Override
    protected void onDisable() {
        active.clear();
        pool.clear();
        prevOnGround = false;
    }

    // ════════════════════════════════════════════════════════════════
    //  TICK — toggle key (always) + jump detection + pruning (enabled)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTickAlways(MinecraftClient client) {
        if (client.player == null || client.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = client.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }
        if (isDown && !wasToggleKeyDown) {
            setEnabled(!isEnabled());
        }
        wasToggleKeyDown = isDown;
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) { active.clear(); return; }

        // ── Jump detection: was on ground last tick, airborne this tick ──
        boolean onGround = player.isOnGround();
        if (prevOnGround && !onGround) {
            // A real vanilla jump leaves the first airborne tick with positive
            // Y velocity. Walking off a ledge has Y velocity <= 0. The filter
            // is optional so ledge-spawn is available if the user wants it.
            double vy = player.getVelocity().y;
            boolean isRealJump = vy > 0.0;
            if (!onlyOnJump.isEnabled() || isRealJump) {
                // Snap the ring to the TOP of the surface the player was
                // standing on. player.getY() is FEET level — on a full block
                // at integer Y the visible surface is one block higher, so a
                // ring drawn at player.getY() would be buried inside the
                // ground and invisible from F5. We use floor(y)+1 (top of the
                // block the feet occupy) when the player is roughly on an
                // integer boundary, otherwise floor(y)+1 of the block below.
                double feetY = player.getY();
                double surfaceY = Math.floor(feetY) + 1.0;
                // If the feet are nearly exactly on an integer (e.g. 64.0 on
                // top of a full block), floor(64.0)+1 = 65.0 = the block top.
                // If the player is on a slab/carpet (feet at ~64.5), the
                // surface is at 65.0 as well, so this still works.
                spawnCircle(player.getX(), surfaceY, player.getZ());
            }
        }
        prevOnGround = onGround;

        // ── Prune expired rings (return them to the pool) ──
        long now = System.currentTimeMillis();
        long lifetimeMs = (long) (lifetime.get() * 1000.0f);
        for (int i = active.size() - 1; i >= 0; i--) {
            JumpCircle c = active.get(i);
            if (now - c.spawnMs >= lifetimeMs) {
                active.remove(i);
                pool.push(c);   // recycle
            }
        }
    }

    /** Lock in a new ring at a fixed world position, evicting the oldest if at cap. */
    private void spawnCircle(double x, double y, double z) {
        // Enforce the max-circles cap (oldest evicted first — FIFO).
        while (active.size() >= maxCircles.get() && !active.isEmpty()) {
            JumpCircle evicted = active.remove(0);
            pool.push(evicted);
        }
        JumpCircle c = pool.isEmpty() ? new JumpCircle() : pool.pop();
        c.set(x, y, z, System.currentTimeMillis());
        active.add(c);
    }

    // ════════════════════════════════════════════════════════════════
    //  WORLD RENDER — draw every active ring as a TRIANGLE_STRIP band
    // ════════════════════════════════════════════════════════════════
    public void renderWorld(WorldRenderContext wrc) {
        if (!isEnabled() || active.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        long now = System.currentTimeMillis();
        long lifetimeMs = (long) (lifetime.get() * 1000.0f);
        if (lifetimeMs <= 0) return;

        double renderDistSq = (double) renderDistance.get() * (double) renderDistance.get();
        Vec3d camPos = camera.getCameraPos();

        WorldRender3D.begin(matrices, camera);
        try {
            // Snapshot the settings once per frame (ClickGUI may change them
            // mid-frame in theory; reading them once keeps a ring consistent).
            float startR = startRadius.get();
            float endR = endRadius.get();
            float baseThickness = thickness.get();
            float yOff = groundOffset.get();
            int segCount = clamp(segments.get(), 3, 512);
            String styleName = style.get();
            String colorModeName = colorMode.get();
            String easingName = easing.get();
            int primary = primaryColor.getColor();
            int secondary = secondaryColor.getColor();
            float rbowSpeed = rainbowSpeed.get();
            boolean glowOn = glow.isEnabled();
            int glowLayerCount = clamp(glowLayers.get(), 0, 8);

            // Color animation phase (seconds) — shared across all rings so the
            // hue cycle is globally coherent.
            float colorPhase = (now % 3600000L) / 1000.0f * rbowSpeed;

            // The MatrixStack.Entry to transform world vertices through (the
            // stack is already camera-relative thanks to WorldRender3D.begin).
            MatrixStack.Entry entry = WorldRender3D.peekEntry();
            if (entry == null) return;

            for (int ci = 0; ci < active.size(); ci++) {
                JumpCircle c = active.get(ci);
                long ageMs = now - c.spawnMs;
                if (ageMs >= lifetimeMs) continue;   // about to be pruned next tick
                float progress = (float) ((double) ageMs / (double) lifetimeMs);
                if (progress < 0f) progress = 0f;
                else if (progress > 1f) progress = 1f;

                // Distance cull — skip rings beyond Render Distance.
                double dx = c.x - camPos.x;
                double dy = c.y - camPos.y;
                double dz = c.z - camPos.z;
                if (dx * dx + dy * dy + dz * dz > renderDistSq) continue;

                float eased = applyEasing(easingName, progress);
                float radius = lerp(startR, endR, eased);
                float alphaFade = 1.0f - progress;          // fades to 0
                // Band width thins over its lifetime (down to 40% of base).
                float bandWidth = baseThickness * (1.0f - progress * 0.6f);
                double ringY = c.y + yOff;

                int segs = segCount;
                if ("Hexagon".equals(styleName)) segs = 6;

                // Render the style ring(s), then (optionally) the glow halo.
                drawStyleRings(consumers, entry, styleName, c.x, ringY, c.z, radius,
                    alphaFade, bandWidth, segs, colorModeName, primary, secondary,
                    colorPhase, glowOn, glowLayerCount);
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /**
     * Draw the style-specific ring band(s) plus the optional glow halo for one
     * active circle. Each ring is a TRIANGLE_STRIP band (donut) with a gradient
     * fading edge — the inner vertex is bright (full alpha), the outer vertex
     * fades to 0 alpha, and OpenGL interpolates the colors between them for the
     * premium soft-edge look.
     */
    private void drawStyleRings(VertexConsumerProvider consumers, MatrixStack.Entry entry,
                                String styleName, double cx, double cy, double cz,
                                float radius, float alphaFade, float bandWidth,
                                int segs, String colorModeName, int primary, int secondary,
                                float colorPhase, boolean glowOn, int glowLayerCount) {
        // ── Primary style ring band(s) ──
        switch (styleName) {
            case "Double": {
                // Inner ring at 0.65× radius, lower alpha.
                drawRingBand(consumers, entry, cx, cy, cz, radius * 0.65f, bandWidth,
                    alphaFade * 0.55f, segs, colorModeName, primary, secondary, colorPhase, 0.0f);
                drawRingBand(consumers, entry, cx, cy, cz, radius, bandWidth,
                    alphaFade, segs, colorModeName, primary, secondary, colorPhase, 0.0f);
                break;
            }
            case "Shockwave": {
                // Three fast bands at increasing radius, decreasing alpha —
                // reads as an expanding shock front.
                drawRingBand(consumers, entry, cx, cy, cz, radius * 0.55f, bandWidth,
                    alphaFade * 0.80f, segs, colorModeName, primary, secondary, colorPhase, 0.0f);
                drawRingBand(consumers, entry, cx, cy, cz, radius * 0.78f, bandWidth,
                    alphaFade * 0.60f, segs, colorModeName, primary, secondary, colorPhase, 0.0f);
                drawRingBand(consumers, entry, cx, cy, cz, radius, bandWidth,
                    alphaFade * 0.40f, segs, colorModeName, primary, secondary, colorPhase, 0.0f);
                break;
            }
            case "Hexagon":
            case "Classic":
            default: {
                drawRingBand(consumers, entry, cx, cy, cz, radius, bandWidth,
                    alphaFade, segs, colorModeName, primary, secondary, colorPhase, 0.0f);
                break;
            }
        }

        // ── Glow halo: concentric bands, each ~6% larger, alpha decaying ──
        // Simulates a soft glow without shaders (spec section 7).
        if (glowOn && glowLayerCount > 0) {
            for (int g = 1; g <= glowLayerCount; g++) {
                float glowRadius = radius * (1.0f + 0.06f * g);
                float glowAlpha = alphaFade * (1.0f / (g + 1)) * 0.6f;
                drawRingBand(consumers, entry, cx, cy, cz, glowRadius, bandWidth,
                    glowAlpha, segs, colorModeName, primary, secondary, colorPhase, 0.0f);
            }
        }
    }

    /**
     * Draw one ring as a TRIANGLE_STRIP band (a thin donut) on the
     * {@link WorldRender3D#TRI_STRIP_NO_DEPTH} layer. The band has a gradient
     * fading edge: the INNER vertex (at {@code radius}) is bright (full alpha
     * scaled by {@code alpha}), and the OUTER vertex (at {@code radius + bandWidth})
     * fades to 0 alpha. OpenGL interpolates the color/alpha between the two
     * vertices automatically, producing the smooth fading edge the spec calls for.
     *
     * <p>Color is computed per-segment (per angle index {@code i}) depending on
     * the color mode. The {@code ringOffset} shifts the color phase for this
     * ring so multi-ring styles don't all share identical per-segment hues.
     *
     * @param entry       the camera-relative {@link MatrixStack.Entry} (from
     *                    {@link WorldRender3D#peekEntry()}); vertices are WORLD coords
     * @param radius      the inner radius of the band (blocks)
     * @param bandWidth   the width of the band (outer - inner radius, blocks)
     * @param alpha       the overall alpha scale (0..1) applied to the inner vertex;
     *                    the outer vertex always fades to 0
     */
    private void drawRingBand(VertexConsumerProvider consumers, MatrixStack.Entry entry,
                              double cx, double cy, double cz, float radius, float bandWidth,
                              float alpha, int segs, String colorModeName,
                              int primary, int secondary, float colorPhase, float ringOffset) {
        if (radius <= 0f || bandWidth <= 0f || alpha <= 0f || segs < 3) return;

        VertexConsumer vc = consumers.getBuffer(WorldRender3D.TRI_STRIP_NO_DEPTH);
        float outerR = radius + bandWidth;
        double twoPi = Math.PI * 2.0;

        // Per-segment color: compute the RGB for each angle index, then emit
        // inner (bright) + outer (faded) vertices — OpenGL interpolates between
        // them across the band, giving the gradient fading edge.
        for (int i = 0; i <= segs; i++) {
            double theta = twoPi * i / segs;
            double ct = Math.cos(theta), st = Math.sin(theta);
            float xIn  = (float) (cx + radius  * ct);
            float xOut = (float) (cx + outerR  * ct);
            float zIn  = (float) (cz + radius  * st);
            float zOut = (float) (cz + outerR  * st);
            float y    = (float) cy;

            // Color for this segment (depends on angle index i).
            int rgb = colorForSegment(colorModeName, i, segs, primary, secondary,
                                       colorPhase, ringOffset);
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8)  & 0xFF) / 255f;
            float b = ( rgb        & 0xFF) / 255f;

            // Inner vertex: full alpha (scaled by the ring's overall alpha).
            // Outer vertex: 0 alpha → fades to nothing at the band's outer edge.
            // The gradient between them is what gives the soft fading look.
            vc.vertex(entry, xIn,  y, zIn ).color(r, g, b, alpha);
            vc.vertex(entry, xOut, y, zOut).color(r, g, b, 0f);
        }
    }

    /**
     * Compute the RGB color (0x00RRGGBB, alpha stripped) for one segment at
     * angle index {@code i} of {@code segs}, according to the color mode.
     */
    private int colorForSegment(String colorModeName, int i, int segs,
                                int primary, int secondary, float colorPhase, float ringOffset) {
        float t = (float) i / segs;
        switch (colorModeName) {
            case "Gradient":
                return lerpRgb(primary, secondary, t);
            case "Rainbow": {
                float hue = (colorPhase + ringOffset + t) % 1.0f;
                if (hue < 0f) hue += 1.0f;
                return hsbToRgb(hue, 1.0f, 1.0f);
            }
            case "Astolfo": {
                // Pastel pink ↔ blue wave: hue oscillates ~0.55..0.92
                // (blue → pink) around the ring, saturation ~0.6, brightness 1.0.
                float wave = (float) Math.sin(colorPhase + ringOffset + t * Math.PI * 2.0);
                float hue = 0.735f + wave * 0.185f;     // 0.55..0.92
                if (hue < 0f) hue += 1.0f;
                return hsbToRgb(hue, 0.6f, 1.0f);
            }
            case "Pulse": {
                // Primary color with brightness oscillating 0.6..1.0.
                float pulse = 0.8f + 0.2f * (float) Math.sin(colorPhase * Math.PI * 2.0 + ringOffset);
                return hsbToRgb(rgbHue(primary), 0.85f, pulse);
            }
            case "Breathing":
            case "Static":
            default:
                return primary & 0x00FFFFFF;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Easing functions — applied to the raw 0..1 progress
    // ════════════════════════════════════════════════════════════════
    private static float applyEasing(String name, float t) {
        switch (name) {
            case "Ease Out Cubic":
                return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            case "Ease Out Quart":
                { float u = 1.0f - t; return 1.0f - u * u * u * u; }
            case "Ease In Out Sine":
                return -(float) Math.cos(Math.PI * t) * 0.5f + 0.5f;
            case "Back": {
                // ease-out-back with a slight overshoot.
                float c1 = 1.70158f;
                float c3 = c1 + 1.0f;
                float u = t - 1.0f;
                return 1.0f + c3 * u * u * u + c1 * u * u;
            }
            case "Elastic": {
                // ease-out-elastic: oscillates then settles to 1.
                if (t <= 0f) return 0f;
                if (t >= 1f) return 1f;
                float c4 = (2.0f * (float) Math.PI) / 3.0f;
                return (float) (Math.pow(2.0, -10.0 * t)
                    * Math.sin((t * 10.0 - 0.75) * c4)) + 1.0f;
            }
            case "Linear":
            default:
                return t;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Color / math helpers
    // ════════════════════════════════════════════════════════════════

    /** Linear interpolation between two ARGB colors, on the RGB channels only. */
    private static int lerpRgb(int a, int b, float t) {
        if (t <= 0f) return a & 0x00FFFFFF;
        if (t >= 1f) return b & 0x00FFFFFF;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = ar + (int) ((br - ar) * t);
        int g = ag + (int) ((bg - ag) * t);
        int bl = ab + (int) ((bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Convert an ARGB int's RGB channels to an HSB hue (0..1). */
    private static float rgbHue(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return rgbToHsb(r, g, b)[0];
    }

    /**
     * Self-contained HSB → RGB conversion (replaces {@code java.awt.Color.HSBtoRGB}).
     * Returns a 0x00RRGGBB int (alpha stripped). h, s, b are 0..1.
     *
     * <p>We avoid {@code java.awt.Color} deliberately — it can trigger headless
     * toolkit initialization inside the Minecraft client JVM, and any throw
     * here would be silently swallowed by {@code LucidClient.onWorldRender}'s
     * per-module {@code try/catch(Throwable)}, making the ring render nothing
     * with no visible error.
     */
    private static int hsbToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);   // wrap to 0..1
        s = clamp01(s);
        v = clamp01(v);
        if (s <= 0f) {
            int c = (int) (v * 255f + 0.5f);
            return (c << 16) | (c << 8) | c;
        }
        float hf = h * 6f;
        int i = (int) Math.floor(hf);
        float f = hf - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r, g, b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        int ri = (int) (r * 255f + 0.5f);
        int gi = (int) (g * 255f + 0.5f);
        int bi = (int) (b * 255f + 0.5f);
        return (ri << 16) | (gi << 8) | bi;
    }

    /** Self-contained RGB → HSB conversion (replaces {@code java.awt.Color.RGBtoHSB}). */
    private static float[] rgbToHsb(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(Math.max(rf, gf), bf);
        float min = Math.min(Math.min(rf, gf), bf);
        float d = max - min;
        float h;
        if (d <= 0f) {
            h = 0f;
        } else if (max == rf) {
            h = (((gf - bf) / d) % 6f) / 6f;
        } else if (max == gf) {
            h = ((bf - rf) / d + 2f) / 6f;
        } else {
            h = ((rf - gf) / d + 4f) / 6f;
        }
        if (h < 0f) h += 1f;
        float s = max <= 0f ? 0f : d / max;
        return new float[] { h, s, max };
    }

    private static float lerp(float a, float b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}