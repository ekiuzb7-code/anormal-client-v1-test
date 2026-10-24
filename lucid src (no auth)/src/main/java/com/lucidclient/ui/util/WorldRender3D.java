package com.lucidclient.ui.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * World-space (3D) rendering helpers for Lucid Client.
 *
 * Draws wireframe geometry that renders THROUGH blocks (depth test disabled),
 * so ESP boxes are always visible regardless of terrain between the camera
 * and the target — like an X-ray ESP.
 *
 * ── Depth test disabled ──────────────────────────────────────────
 * Vanilla {@code RenderLayers.LINES} is depth-tested, so it gets hidden
 * behind terrain. To disable depth we build a CUSTOM {@link RenderLayer}
 * backed by a CUSTOM {@link RenderPipeline} based on vanilla's
 * {@code RENDERTYPE_LINES_SNIPPET} (same shaders / blend / vertex format)
 * but with {@link DepthTestFunction#NO_DEPTH_TEST}. This requires an access
 * widener (see {@code lucidclient.accesswidener}) because
 * {@code RenderLayer.of} and {@code RenderPipelines.RENDERTYPE_LINES_SNIPPET}
 * are package-private. The 1.21.11 GPU render-pass backend re-derives GL
 * depth state from the bound pipeline on every bind, so toggling
 * {@code GlStateManager} would be clobbered — only a no-depth pipeline works.
 *
 * ── Coordinate space ──────────────────────────────────────────────
 * At {@code WorldRenderEvents.END_MAIN} the MatrixStack carries the camera
 * rotation but NOT the camera translation (vanilla applies {@code -camPos}
 * per-object). So {@link #begin} pushes + translates by {@code -camPos} and
 * {@link #end} pops. All boxes/lines passed in are WORLD coordinates.
 *
 * ── Black-screen guard ─────────────────────────────────────────────
 * When the camera is inside or very close to a flagged chunk, one or more
 * of the box's 8 corners can land behind the near plane, which makes the
 * projected line span the entire screen and flash it black. We skip drawing
 * a box when the camera is within the box's expanded AABB — the player is
 * standing IN the sus chunk and doesn't need an ESP box around themselves.
 */
public final class WorldRender3D {
    private WorldRender3D() {}

    private static final float LINE_WIDTH = 2.5f;

    /**
     * Custom LINES render layer with DEPTH TEST DISABLED, based on vanilla's
     * lines snippet (same shaders / blend / vertex format). Built once at
     * class-load; the access widener exposes {@code RenderLayer.of} and the
     * {@code RENDERTYPE_LINES_SNIPPET} field.
     */
    public static final RenderLayer LINES_NO_DEPTH = RenderLayer.of(
        "lucidclient:lines_no_depth",
        RenderSetup.builder(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                .withLocation("pipeline/lucidclient_lines_no_depth")
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)        // don't occlude ourselves
                .build()
        ).build()
    );

    /**
     * Custom POSITION_COLOR layer drawn as TRIANGLE_STRIP (a filled band/donut),
     * depth-test disabled, no cull, no depth write, translucent blend — based on
     * vanilla's {@code POSITION_COLOR_SNIPPET} (core/position_color shaders, which
     * already wire POSITION_COLOR format + TRANSLUCENT blend). We OVERRIDE the
     * snippet's default QUADS draw mode with TRIANGLE_STRIP and disable both depth
     * test and face culling (rings are visible from above and below). Requires the
     * access widener's {@code POSITION_COLOR_SNIPPET} field exposure.
     *
     * <p>Used by Jump Circles to render an expanding ring <b>band</b> (with a
     * gradient fading edge — inner vertex bright, outer vertex alpha 0 — which
     * OpenGL interpolates automatically for the premium soft-edge look). A filled
     * band is far more visible from third-person (F5) than a thin 2.5px line.
     *
     * <p>Vertices need only {@code .vertex(entry, x,y,z).color(r,g,b,a)} — no
     * normal/texture/lineWidth (the POSITION_COLOR format has just those two
     * elements). Minecraft flushes the {@link VertexConsumerProvider.Immediate}
     * from {@code WorldRenderContext.consumers()} after END_MAIN automatically.
     */
    public static final RenderLayer TRI_STRIP_NO_DEPTH = RenderLayer.of(
        "lucidclient:tri_strip_no_depth",
        RenderSetup.builder(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation("pipeline/lucidclient_tri_strip_no_depth")
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)              // rings are double-sided
                .withVertexFormat(VertexFormats.POSITION_COLOR,
                                  VertexFormat.DrawMode.TRIANGLE_STRIP)
                .build()
        ).build()
    );

    /**
     * Custom POSITION_COLOR layer drawn as QUADS (a solid filled box), depth-
     * test disabled, no cull, no depth write, translucent blend — the filled
     * sibling of {@link #LINES_NO_DEPTH}. Built on vanilla's
     * {@code POSITION_COLOR_SNIPPET} (core/position_color shaders, POSITION_COLOR
     * format, TRANSLUCENT blend) but with {@link VertexFormat.DrawMode#QUADS}
     * draw mode (every 4 vertices = one quad), no depth test, and face culling
     * disabled so the box is visible from above, below, and through terrain.
     *
     * <p>Used by SusChunkESP to render a <b>flat horizontal filled box</b>
     * covering each flagged 16&#xd7;16 chunk — a translucent red slab that
     * reads as a chunk highlight instead of a tall wireframe column. The
     * fill is kept translucent (alpha capped in {@link #drawFilledBox}) so it
     * covers the chunk without hiding the terrain/base underneath it.
     *
     * <p>Requires the access widener's {@code POSITION_COLOR_SNIPPET} field
     * exposure (same as {@link #TRI_STRIP_NO_DEPTH}).
     */
    public static final RenderLayer QUADS_NO_DEPTH = RenderLayer.of(
        "lucidclient:quads_no_depth",
        RenderSetup.builder(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation("pipeline/lucidclient_quads_no_depth")
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)              // box is double-sided
                .withVertexFormat(VertexFormats.POSITION_COLOR,
                                  VertexFormat.DrawMode.QUADS)
                .build()
        ).build()
    );

    private static MatrixStack matrices;
    private static Vec3d cachedCamPos;

    /** Push + translate the stack to camera-relative space. Pair with {@link #end}. */
    public static void begin(MatrixStack stack, Camera camera) {
        matrices = stack;
        cachedCamPos = camera.getCameraPos();
        stack.push();
        stack.translate(-cachedCamPos.x, -cachedCamPos.y, -cachedCamPos.z);
    }

    /** Pop the stack to restore the caller's matrix state. */
    public static void end(MatrixStack stack) {
        stack.pop();
        matrices = null;
        cachedCamPos = null;
    }

    /**
     * The current camera-relative {@link MatrixStack.Entry} (the result of
     * {@code matrices.peek()}), to transform world-space vertices through when
     * emitting them on a custom {@link RenderLayer} (e.g. a TRIANGLE_STRIP band).
     * Only valid between {@link #begin} and {@link #end}.
     */
    public static MatrixStack.Entry peekEntry() {
        return matrices == null ? null : matrices.peek();
    }

    /**
     * Draw a wireframe box (12 edges) at WORLD coordinates. Renders through
     * blocks (depth test disabled). Skipped when the camera is inside the
     * box's expanded AABB to avoid the black-screen flash from a corner
     * landing behind the near plane.
     */
    public static void drawBox(VertexConsumerProvider consumers, Box box, int color) {
        if (matrices == null || consumers == null || box == null) return;
        // Camera-inside-box guard: if the camera is inside (or within 2 blocks
        // of) the box, one or more corners land behind the near plane and the
        // projected line spans the screen → black flash. Skip in that case;
        // the player is standing in the chunk and doesn't need a self-box.
        if (cachedCamPos != null && box.expand(2.0).contains(cachedCamPos)) return;

        VertexConsumer vc = consumers.getBuffer(LINES_NO_DEPTH);
        VoxelShape shape = VoxelShapes.cuboid(box);
        VertexRendering.drawOutline(matrices, vc, shape, 0.0, 0.0, 0.0, color, LINE_WIDTH);
    }

    /**
     * Draw a FILLED box (6 solid faces) at WORLD coordinates on the custom
     * no-depth QUADS layer. Renders through blocks (depth test disabled) and
     * is double-sided (cull disabled), so the box is visible from any angle —
     * even behind terrain. Used by SusChunkESP's flat chunk overlay.
     *
     * <p>The fill is kept translucent: the color's alpha byte is capped to
     * {@code 0x55} (~33%) so the overlay <b>covers</b> the chunk with its hue
     * (e.g. red) without becoming an opaque sheet that hides the terrain /
     * base inside it. The {@code color} setting therefore controls the hue,
     * not the opacity. (An opaque fill would render as a solid wall of colour
     * through every hill, which is useless for a "find the sus chunk" tool —
     * you need to see where the chunk is, not have it blotted out.)
     *
     * <p>Skipped when the camera is inside the box's expanded AABB (a filled
     * quad with a corner landing behind the near plane would otherwise flash
     * the screen with a giant colour sweep).
     *
     * @param color  ARGB int — RGB are used as the hue; the alpha byte is
     *               capped to a translucent level (see above)
     */
    public static void drawFilledBox(VertexConsumerProvider consumers, Box box, int color) {
        if (matrices == null || consumers == null || box == null) return;
        // Camera-inside-box guard: same rationale as drawBox — when a filled
        // face's corner is projected from behind the near plane it sweeps a
        // giant colour quad across the screen. Skip in that case; the player is
        // standing in the flagged chunk and doesn't need a fill underfoot.
        if (cachedCamPos != null && box.expand(2.0).contains(cachedCamPos)) return;

        // RGB from the colour setting; alpha capped to a translucent level so
        // the fill highlights the chunk rather than occluding it.
        float rf = ((color >> 16) & 0xFF) / 255f;
        float gf = ((color >> 8)  & 0xFF) / 255f;
        float bf = ( color        & 0xFF) / 255f;
        float af = Math.min(((color >> 24) & 0xFF), 0x55) / 255f;
        if (af <= 0f) af = 0x55 / 255f;

        VertexConsumer vc = consumers.getBuffer(QUADS_NO_DEPTH);
        MatrixStack.Entry entry = matrices.peek();
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
        double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;

        // 6 faces × 4 vertices, emitted as QUADS (every 4 verts = one quad).
        // Cull is disabled, so winding doesn't matter — each face is just its
        // 4 corners in order.
        // –x face
        face(vc, entry, rf, gf, bf, af,
             x0, y0, z0,  x0, y1, z0,  x0, y1, z1,  x0, y0, z1);
        // +x face
        face(vc, entry, rf, gf, bf, af,
             x1, y0, z0,  x1, y0, z1,  x1, y1, z1,  x1, y1, z0);
        // –z face
        face(vc, entry, rf, gf, bf, af,
             x0, y0, z0,  x1, y0, z0,  x1, y1, z0,  x0, y1, z0);
        // +z face
        face(vc, entry, rf, gf, bf, af,
             x0, y0, z1,  x0, y1, z1,  x1, y1, z1,  x1, y0, z1);
        // bottom (–y) face
        face(vc, entry, rf, gf, bf, af,
             x0, y0, z0,  x0, y0, z1,  x1, y0, z1,  x1, y0, z0);
        // top (+y) face
        face(vc, entry, rf, gf, bf, af,
             x0, y1, z0,  x1, y1, z0,  x1, y1, z1,  x0, y1, z1);
    }

    /** Emit one filled quad (4 vertices) on the current QUADS_NO_DEPTH buffer. */
    private static void face(VertexConsumer vc, MatrixStack.Entry entry,
                             float r, float g, float b, float a,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz) {
        vc.vertex(entry, (float) ax, (float) ay, (float) az).color(r, g, b, a);
        vc.vertex(entry, (float) bx, (float) by, (float) bz).color(r, g, b, a);
        vc.vertex(entry, (float) cx, (float) cy, (float) cz).color(r, g, b, a);
        vc.vertex(entry, (float) dx, (float) dy, (float) dz).color(r, g, b, a);
    }

    /**
     * Draw a FILLED box (6 solid faces) at WORLD coordinates using the
     * color's alpha byte EXACTLY (no translucent cap). Same no-depth,
     * double-sided, through-walls behaviour and camera-inside guard as
     * {@link #drawFilledBox}, for ESPs where the user controls the opacity
     * directly (e.g. StorageESP's Argon-style Alpha setting).
     *
     * @param color  ARGB int — RGB are the hue; the alpha byte is the fill
     *               opacity (0 = invisible, 255 = solid)
     */
    public static void drawFilledBoxExactAlpha(VertexConsumerProvider consumers, Box box, int color) {
        if (matrices == null || consumers == null || box == null) return;
        if (cachedCamPos != null && box.expand(2.0).contains(cachedCamPos)) return;

        float rf = ((color >> 16) & 0xFF) / 255f;
        float gf = ((color >> 8)  & 0xFF) / 255f;
        float bf = ( color        & 0xFF) / 255f;
        float af = ((color >> 24) & 0xFF) / 255f;
        if (af <= 0f) return;

        VertexConsumer vc = consumers.getBuffer(QUADS_NO_DEPTH);
        MatrixStack.Entry entry = matrices.peek();
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
        double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;

        face(vc, entry, rf, gf, bf, af,
             x0, y0, z0,  x0, y1, z0,  x0, y1, z1,  x0, y0, z1);
        face(vc, entry, rf, gf, bf, af,
             x1, y0, z0,  x1, y0, z1,  x1, y1, z1,  x1, y1, z0);
        face(vc, entry, rf, gf, bf, af,
             x0, y0, z0,  x1, y0, z0,  x1, y1, z0,  x0, y1, z0);
        face(vc, entry, rf, gf, bf, af,
             x0, y0, z1,  x0, y1, z1,  x1, y1, z1,  x1, y0, z1);
        face(vc, entry, rf, gf, bf, af,
             x0, y0, z0,  x0, y0, z1,  x1, y0, z1,  x1, y0, z0);
        face(vc, entry, rf, gf, bf, af,
             x0, y1, z0,  x1, y1, z0,  x1, y1, z1,  x0, y1, z1);
    }

    /**
     * Draw a single line segment between two WORLD-space points as a real
     * two-vertex line on the no-depth LINES layer (NOT a degenerate box — a
     * zero-thickness box outline produced invalid vertices and crashed the
     * game with exit code -805306369).
     *
     * The LINES_NO_DEPTH pipeline uses the POSITION_COLOR_NORMAL_LINE_WIDTH
     * vertex format, so each vertex needs position + color + normal + lineWidth.
     * Uses the default {@link #LINE_WIDTH} thickness.
     */
    public static void drawLine(VertexConsumerProvider consumers, Vec3d from, Vec3d to, int color) {
        drawLine(consumers, from, to, color, LINE_WIDTH);
    }

    /**
     * Draw a single line segment between two WORLD-space points as a real
     * two-vertex line on the no-depth LINES layer, with a caller-specified
     * line width. Used by Jump Circles to vary thickness per-ring / per-glow
     * layer and to animate the ring thinning over its lifetime.
     *
     * The LINES_NO_DEPTH pipeline uses the POSITION_COLOR_NORMAL_LINE_WIDTH
     * vertex format, so each vertex needs position + color + normal + lineWidth.
     */
    public static void drawLine(VertexConsumerProvider consumers, Vec3d from, Vec3d to,
                                int color, float lineWidth) {
        if (matrices == null || consumers == null) return;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) a = 255;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = (color         & 0xFF) / 255f;
        // color(float,float,float,float) multiplies each channel by 255 itself,
        // so pass alpha as a 0..1 float (not the raw 0..255 int) — otherwise alpha
        // gets scaled to ~0.4% and the line renders at near-zero opacity.
        float af = a / 255f;

        // Normal = line direction (unit), required by the LINES shader.
        float nx = (float) (to.x - from.x);
        float ny = (float) (to.y - from.y);
        float nz = (float) (to.z - from.z);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0f) { nx /= len; ny /= len; nz /= len; }
        else { nx = 0f; ny = 1f; nz = 0f; } // degenerate guard

        VertexConsumer vc = consumers.getBuffer(LINES_NO_DEPTH);
        MatrixStack.Entry entry = matrices.peek();
        // Two vertices = one line segment. Each vertex needs the full
        // POSITION_COLOR_NORMAL_LINE_WIDTH element set in the right order.
        vc.vertex(entry, (float) from.x, (float) from.y, (float) from.z)
          .color(r, g, b, af)
          .normal(entry, nx, ny, nz)
          .lineWidth(lineWidth);
        vc.vertex(entry, (float) to.x,   (float) to.y,   (float) to.z)
          .color(r, g, b, af)
          .normal(entry, nx, ny, nz)
          .lineWidth(lineWidth);
    }

    /**
     * Draw a tracer from the camera to a WORLD-space target.
     */
    public static void drawTracer(VertexConsumerProvider consumers, Camera camera, Vec3d toWorld, int color) {
        if (matrices == null || consumers == null || camera == null) return;
        Vec3d from = camera.getCameraPos();
        drawLine(consumers, from, toWorld, color);
    }
}