package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.ui.theme.Colors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Tracers — 2D screen-space lines from a screen anchor (the crosshair, or the
 * bottom-center of the screen for "From Top") to every nearby entity + storage
 * container. This is how the well-known premium clients render tracers, and it
 * reuses the EXACT projection path that EntityESP / StorageESP already use and
 * that the user confirmed renders correctly.
 *
 * ── Projection (same as EntityESP / StorageESP) ─────────────────
 *   view  = R_inv * translate(-camPos)              (R = camera quaternion)
 *   proj  = getBasicProjectionMatrix(fovDeg)        (fovDeg from getFov option)
 *   vp    = proj * view
 *   For each target world point P:
 *     v = vp * (P, 1)
 *     if v.w <= 0.05 → behind camera, skip
 *     ndc = v.xyz / v.w
 *     screenX = (ndc.x * 0.5 + 0.5) * screenW
 *     screenY = (0.5 - ndc.y * 0.5) * screenH
 * Then draw a line from the anchor to (screenX, screenY).
 *
 * ── Line drawing (optimized — the fix for the StackOverflowError) ──
 * The earlier 2D version drew each tracer as a series of per-pixel
 * {@link DrawContext#fill} calls along the line's major axis — hundreds of
 * fills per tracer, which flooded the render path and over flowed the stack.
 * This version draws each line as a SINGLE rotated {@code fill}:
 *   matrices.pushMatrix()
 *   matrices.translate(anchorX, anchorY)
 *   matrices.rotate(angleToTarget)
 *   ctx.fill(0, -half, length, +half, color)   // axis-aligned rect, now rotated
 *   matrices.popMatrix()
 * The rectangle is {@code length × thickness} oriented along +X, then rotated
 * to point at the target. One fill per line — the same call count as the ESP
 * box outlines, which render fine.
 *
 * ── Optimization summary ────────────────────────────────────────
 *   - One {@code fill} per line (rotated rect), not hundreds of pixel stamps.
 *   - Hard cap MAX_TARGETS bounds the worst-case fill count so a busy area
 *     can't flood the render path.
 *   - Distance-sort nearest-first so the cap drops the FARTHEST targets.
 *   - Per-color float unpacking done ONCE per color, not per target.
 *   - Single projection matrix built once per frame.
 *
 * ── Interpolation ──────────────────────────────────────────────
 *   P_render = P_prev + (P_curr - P_prev) * t_partial  via getLerpedPos(tickDelta)
 *
 * ── Color coding ────────────────────────────────────────────────
 *   Players       → Player Color
 *   Hostiles      → red
 *   Passives      → green
 *   Storage (BE)  → Storage Color (if Trace Storage is on)
 *
 * Settings:
 *   Range, Players Only, Player Color, Trace Storage, Storage Color, From Top
 */
public class Tracers extends Module {

    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Scan radius (blocks).", 8f, 128f, 64f, 0));
    private final BooleanSetting playersOnly = addSetting(
        new BooleanSetting("Players Only", "Only trace other players (skip mobs).", false));
    private final ColorSetting playerColor = addSetting(
        new ColorSetting("Player Color", "Color for player tracers. Click to cycle.", 0xFF111111));
    private final BooleanSetting traceStorage = addSetting(
        new BooleanSetting("Trace Storage", "Also draw tracers to nearby storage block entities.", true));
    private final ColorSetting storageColor = addSetting(
        new ColorSetting("Storage Color", "Color for storage tracers. Click to cycle.", 0xFF8B5CF6));
    private final BooleanSetting fromTop = addSetting(
        new BooleanSetting("From Top", "Anchor tracers at the BOTTOM-CENTER of the screen instead of the crosshair (lines fan out from the player's feet).", false));

    /** Screen-space line thickness in pixels. Thin, clean tracers. */
    private static final int LINE_THICKNESS = 1;

    /** Hard cap on the number of tracers drawn per frame. Bounds the worst-case
     *  fill count (one per tracer) so a busy area can't flood the render path.
     *  The nearest targets are kept when the cap bites. */
    private static final int MAX_TARGETS = 96;

    // Cached snapshot of entities + storage block entities to render.
    // Re-populated once per tick (20Hz); the render loop only reads these.
    private final List<Entity> cachedEntities = new ArrayList<>();
    private final List<BlockPos> cachedStorage = new ArrayList<>();

    /** Reused scratch list of render candidates {sx, sy, distSq, packedColor}.
     *  Kept as a field to avoid per-frame allocation. */
    private final List<float[]> renderQueue = new ArrayList<>();

    public Tracers() {
        super("Tracers", "Draws lines from the player to nearby entities + storage.",
            Category.RENDER);
    }

    @Override
    protected void onEnable() { cachedEntities.clear(); cachedStorage.clear(); renderQueue.clear(); }
    @Override
    protected void onDisable() { cachedEntities.clear(); cachedStorage.clear(); renderQueue.clear(); }

    // ── Tick: scan + cache targets (20Hz) ────────────────────
    @Override
    public void onTick(MinecraftClient client) {
        cachedEntities.clear();
        cachedStorage.clear();
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        double r = range.get();
        Box searchBox = new Box(
            player.getX() - r, player.getY() - r, player.getZ() - r,
            player.getX() + r, player.getY() + r, player.getZ() + r);

        List<Entity> candidates = world.getOtherEntities(player, searchBox, e -> {
            if (!(e instanceof LivingEntity)) return false;
            if (!e.isAlive()) return false;
            if (playersOnly.isEnabled() && !(e instanceof PlayerEntity)) return false;
            return e.squaredDistanceTo(player.getX(), player.getY(), player.getZ()) <= r * r;
        });
        cachedEntities.addAll(candidates);

        if (traceStorage.isEnabled()) {
            scanStorageBlockEntities(world, player, r);
        }
    }

    private void scanStorageBlockEntities(ClientWorld world, ClientPlayerEntity player, double r) {
        double r2 = r * r;
        // ⚠ ClientWorld.getBlockEntities() returns an EMPTY set in production
        // (it's only populated in dev). We must iterate loaded CHUNKS and ask
        // each chunk for its block entities via getBlockEntities().
        int chunkRadius = (int) Math.ceil(r / 16.0);
        int centerChunkX = player.getBlockPos().getX() >> 4;
        int centerChunkZ = player.getBlockPos().getZ() >> 4;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                WorldChunk chunk = world.getChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    double dx = pos.getX() + 0.5 - player.getX();
                    double dy = pos.getY() + 0.5 - player.getY();
                    double dz = pos.getZ() + 0.5 - player.getZ();
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    if (!isStorageType(be.getType())) continue;
                    cachedStorage.add(pos);
                }
            }
        }
    }

    private boolean isStorageType(BlockEntityType<?> type) {
        return type == BlockEntityType.CHEST
            || type == BlockEntityType.TRAPPED_CHEST
            || type == BlockEntityType.ENDER_CHEST
            || type == BlockEntityType.SHULKER_BOX
            || type == BlockEntityType.BARREL
            || type == BlockEntityType.HOPPER
            || type == BlockEntityType.DISPENSER
            || type == BlockEntityType.DROPPER;
    }

    // ── HUD render: 2D screen-space tracers (called from LucidClient HUD render) ──
    // Reuses the EXACT projection path EntityESP / StorageESP use, which the
    // user has confirmed renders correctly. Project each target to screen
    // coords, then draw a line from a screen anchor to that point as a single
    // rotated fill (one fill call per line — the same primitive the ESP boxes
    // use, so it's guaranteed to render, and cheap enough to never overflow).
    public void draw(DrawContext ctx, float tickDelta) {
        if (!isEnabled()) return;
        if (cachedEntities.isEmpty() && cachedStorage.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.gameRenderer == null) return;
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;
        Vec3d camPos = camera.getCameraPos();

        // ── Projection matrix (same as EntityESP) ───────────────────────
        // getBasicProjectionMatrix takes FOV DEGREES, not tickDelta. Read the
        // player's FOV option (a SimpleOption<Float>) and pass its value.
        float fovDeg = client.options.getFov().getValue();
        Matrix4f projMatrix = client.gameRenderer.getBasicProjectionMatrix(fovDeg);

        // View matrix using the camera's actual orientation quaternion.
        //   V = R_inv * T(-camPos)
        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.identity();
        Quaternionf invRot = new Quaternionf(camera.getRotation()).invert();
        viewMatrix.rotate(invRot);
        viewMatrix.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);

        // VP = P * V (precompute once per frame)
        Matrix4f vpMatrix = new Matrix4f();
        vpMatrix.set(projMatrix);
        vpMatrix.mul(viewMatrix);

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        // Anchor: crosshair (screen center) by default, or bottom-center when
        // "From Top" is on (lines fan out from the player's feet).
        int anchorX = screenW / 2;
        int anchorY = fromTop.isEnabled() ? screenH - 1 : screenH / 2;

        // Precompute packed colors ONCE per color group (with full alpha), so
        // we don't unpack the same color for every target.
        int storageCol = withFullAlpha(storageColor.getColor());
        int playerCol  = withFullAlpha(playerColor.getColor());
        int hostileCol = withFullAlpha(Colors.ERROR);
        int passiveCol = withFullAlpha(Colors.SUCCESS);

        // ── Build the render queue: {screenX, screenY, distSq, packedColor} ──
        renderQueue.clear();

        if (traceStorage.isEnabled()) {
            for (BlockPos pos : cachedStorage) {
                float[] s = project(vpMatrix, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, screenW, screenH, anchorX, anchorY);
                if (s == null) continue;
                renderQueue.add(new float[] { s[0], s[1], s[2], storageCol });
            }
        }

        for (Entity ent : cachedEntities) {
            if (ent.isRemoved() || !ent.isAlive()) continue;
            Vec3d lp = ent.getLerpedPos(tickDelta);
            double toY = lp.y + ent.getHeight() * 0.5; // body centre
            float[] s = project(vpMatrix, lp.x, toY, lp.z, screenW, screenH, anchorX, anchorY);
            if (s == null) continue;

            int color;
            if (ent instanceof PlayerEntity)                 color = playerCol;
            else if (ent instanceof Monster || ent instanceof HostileEntity) color = hostileCol;
            else if (ent instanceof PassiveEntity)            color = passiveCol;
            else                                              color = playerCol;

            renderQueue.add(new float[] { s[0], s[1], s[2], color });
        }

        if (renderQueue.isEmpty()) return;

        // Distance-sort nearest-first (insertion sort on distSq, index 2). N
        // is bounded by MAX_TARGETS and usually tiny, so the no-allocation sort
        // beats a Comparator-based one that would allocate.
        int n = Math.min(MAX_TARGETS, renderQueue.size());
        for (int i = 0; i < renderQueue.size(); i++) {
            float[] worst = renderQueue.get(i);
            int worstIdx = i;
            for (int j = i + 1; j < renderQueue.size(); j++) {
                if (renderQueue.get(j)[2] < worst[2]) {
                    worst = renderQueue.get(j);
                    worstIdx = j;
                }
            }
            if (worstIdx != i) {
                renderQueue.set(worstIdx, renderQueue.get(i));
                renderQueue.set(i, worst);
            }
        }

        // ── Draw each tracer as a single rotated fill ───────────────────
        // For a line from the anchor to (sx,sy):
        //   length = distance from anchor to target
        //   angle  = atan2(dy, dx)   (radians, screen-space: +y is down)
        //   matrices.translate(anchor); matrices.rotate(angle);
        //   ctx.fill(0, -half, length, +half, color)  // rect along +X, rotated
        // One fill per line — the same call count as the ESP box outlines.
        Matrix3x2fStack matrices = ctx.getMatrices();
        // Thin tracer: a single 1px band along the line (was ±1 = 2px).
        for (int i = 0; i < n; i++) {
            float[] e = renderQueue.get(i);
            float sx = e[0];
            float sy = e[1];
            int color = (int) e[3];

            float dx = sx - anchorX;
            float dy = sy - anchorY;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1f) continue; // target is at the anchor — nothing to draw
            float angle = (float) Math.atan2(dy, dx);

            matrices.pushMatrix();
            matrices.translate(anchorX, anchorY);
            matrices.rotate(angle);
            // Axis-aligned rect of (length × 1px) starting at the anchor
            // and extending along +X. After rotation it points at the target.
            ctx.fill(0, 0, (int) length, LINE_THICKNESS, color);
            matrices.popMatrix();
        }
    }

    /**
     * Project a world-space point to screen-space pixel coords using the VP
     * matrix, and return {screenX, screenY, distSqFromAnchor} or {@code null}
     * if the point is behind the camera or outside the near/far plane.
     * distSqFromAnchor is in screen-space pixels (used for nearest-first sort).
     */
    private float[] project(Matrix4f vpMatrix, double wx, double wy, double wz,
                            int screenW, int screenH, int anchorX, int anchorY) {
        Vector4f v = new Vector4f((float) wx, (float) wy, (float) wz, 1f);
        vpMatrix.transform(v);
        if (v.w <= 0.05f) return null; // behind camera
        float ndcX = v.x / v.w;
        float ndcY = v.y / v.w;
        float ndcZ = v.z / v.w;
        if (ndcZ < -1f || ndcZ > 1f) return null; // outside near/far
        float screenX = (ndcX * 0.5f + 0.5f) * screenW;
        float screenY = (0.5f - ndcY * 0.5f) * screenH;

        // Screen-space squared distance from the anchor — used for the
        // nearest-first sort. Closer-to-crosshair tracers render when cap bites.
        float adx = screenX - anchorX;
        float ady = screenY - anchorY;
        float distSq = adx * adx + ady * ady;
        return new float[] { screenX, screenY, distSq };
    }

    /** Guarantee an opaque alpha (255) if the configured color was left at 0. */
    private static int withFullAlpha(int color) {
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return (color & 0x00FFFFFF) | 0xFF000000;
        return color;
    }
}