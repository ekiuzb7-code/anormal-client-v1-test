package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds end portals and (optionally) their frames.
 *
 * <p><b>Sliding fix:</b> markers are now projected against the interpolated
 * render camera, so they stay pinned to their blocks while you walk, run or
 * fly. Previously the projection used the tick-quantised player yaw/pitch, so
 * the markers drifted behind the world as soon as you moved.</p>
 *
 * <p><b>Spike fix:</b> the old scan was a full 3D sweep of
 * {@code (2*64+1)^2 * (2*48+1)} = ~1.6 million blocks in a single tick, which
 * froze the game. It is now budgeted and incremental.</p>
 */
public class EndPortalFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 64.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up/down to scan", 48.0, 8.0, 96.0, 8.0);
    public final BooleanSetting showFrames = new BooleanSetting("Show Frames", "Also mark end portal frames", true);
    public final BooleanSetting showPortal = new BooleanSetting("Show Portal", "Mark the end portal blocks", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw 3D block outlines", true);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outlines", 1.0, 1.0, 4.0, 1.0);
    public final ColorSetting color = new ColorSetting("Portal Color", "Portal marker color", ColorUtils.rgba(0, 220, 220, 255));
    public final ColorSetting frameColor = new ColorSetting("Frame Color", "Portal frame marker color", ColorUtils.rgba(120, 255, 160, 255));
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show the distance on portal markers", true);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Announce a portal the first time it is found", false);

    private static final int TYPE_PORTAL = 0;
    private static final int TYPE_FRAME = 1;
    private static final int SCAN_BUDGET = 2500;
    private static final long RENDER_BUDGET_NS = 4_000_000L;

    private final Map<BlockPos, Integer> found = new HashMap<>();
    private final BlockScanner scanner = new BlockScanner();
    private final Set<String> logged = new HashSet<>();
    private int scanOriginX = Integer.MIN_VALUE, scanOriginY, scanOriginZ;

    public EndPortalFinder() {
        super("EndPortalFinder", "Finds end portal blocks and frames", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(showFrames);
        addSetting(showPortal);
        addSetting(outline);
        addSetting(outlineWidth);
        addSetting(color);
        addSetting(frameColor);
        addSetting(showDistance);
        addSetting(chatLog);
    }

    @Override
    public void onDisable() {
        found.clear();
        scanner.reset();
        scanOriginX = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();

        if (scanOriginX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - scanOriginX) > 16
                || Math.abs(origin.getZ() - scanOriginZ) > 16) {
            scanOriginX = origin.getX();
            scanOriginY = origin.getY();
            scanOriginZ = origin.getZ();
            prune(origin, r, vr);
        }

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scan(mc.world, scanOriginX, scanOriginY, scanOriginZ, r, vr, SCAN_BUDGET,
                    scratch,
                    state -> state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.END_PORTAL_FRAME),
                    (pos, state) -> found.put(pos.toImmutable(),
                            state.isOf(Blocks.END_PORTAL) ? TYPE_PORTAL : TYPE_FRAME));
        } catch (Throwable ignored) {
        }

        if (chatLog.isEnabled() && mc.inGameHud != null) {
            for (Map.Entry<BlockPos, Integer> e : found.entrySet()) {
                if (e.getValue() != TYPE_PORTAL) continue;
                BlockPos first = e.getKey();
                String key = (first.getX() >> 4) + "," + (first.getZ() >> 4);
                if (logged.add(key)) {
                    try {
                        mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                                String.format("§b[EndPortal] §ffound at §f%d %d %d",
                                        first.getX(), first.getY(), first.getZ())));
                    } catch (Throwable ignored) {
                    }
                }
                break;
            }
        }
    }

    private void prune(BlockPos origin, int r, int vr) {
        int rr = r * r;
        found.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            int dy = pos.getY() - origin.getY();
            return dx * dx + dz * dz > rr || Math.abs(dy) > vr + 1;
        });
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;

        int width = outlineWidth.getValue().intValue();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;
        int drawn = 0;

        try {
            for (Map.Entry<BlockPos, Integer> e : found.entrySet()) {
                if ((drawn & 15) == 0 && System.nanoTime() > deadline) break;

                int type = e.getValue();
                if (type == TYPE_PORTAL && !showPortal.isEnabled()) continue;
                if (type == TYPE_FRAME && !showFrames.isEnabled()) continue;

                BlockPos p = e.getKey();
                int col = type == TYPE_PORTAL ? color.getValue() : frameColor.getValue();

                if (RenderUtils.projectBlockBounds(p, tickDelta) == null) continue;
                drawn++;

                if (outline.isEnabled()) {
                    RenderUtils.drawBlockOutline(context, p, col, tickDelta, width);
                } else {
                    int[] sc = ProjectionUtil.project(
                            new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
                    if (sc != null) {
                        RenderUtils.fill(context, sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] + 3, col);
                    }
                }

                if (showDistance.isEnabled() && type == TYPE_PORTAL && mc.textRenderer != null) {
                    int[] sc = ProjectionUtil.project(
                            new Vec3d(p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5), tickDelta);
                    if (sc != null) {
                        String d = (int) Math.sqrt(p.getSquaredDistance(mc.player.getBlockPos())) + "m";
                        RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + 3, sc[1] - 4, col, true);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
