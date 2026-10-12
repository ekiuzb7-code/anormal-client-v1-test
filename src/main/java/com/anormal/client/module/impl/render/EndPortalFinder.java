package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds end portals and (optionally) their frames.
 *
 * <p><b>Flicker fix:</b> the old scan advanced by 2 blocks on every axis, so
 * whether a portal block was sampled depended on the player's exact coordinate
 * parity. Walking one block made markers appear and disappear. The scan now
 * uses a step of 1, and the results are collected into fresh lists and swapped
 * in atomically so markers never vanish mid-scan.</p>
 */
public class EndPortalFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 64.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up/down to scan", 48.0, 8.0, 96.0, 8.0);
    public final BooleanSetting showFrames = new BooleanSetting("Show Frames", "Also mark end portal frames", true);
    public final BooleanSetting showPortal = new BooleanSetting("Show Portal", "Mark the end portal blocks", true);
    public final ColorSetting color = new ColorSetting("Portal Color", "Portal marker color", ColorUtils.rgba(0, 220, 220, 255));
    public final ColorSetting frameColor = new ColorSetting("Frame Color", "Portal frame marker color", ColorUtils.rgba(120, 255, 160, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max portal markers drawn", 8.0, 1.0, 32.0, 1.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Announce a portal the first time it is found", false);

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int MAX_PORTAL_BLOCKS = 64;
    private static final int MAX_FRAME_BLOCKS = 200;

    private List<BlockPos> portals = new ArrayList<>();
    private List<BlockPos> frames = new ArrayList<>();
    private final java.util.Set<String> logged = new java.util.HashSet<>();
    private int ticks = 0;

    public EndPortalFinder() {
        super("EndPortalFinder", "Finds end portal blocks and frames", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(showFrames);
        addSetting(showPortal);
        addSetting(color);
        addSetting(frameColor);
        addSetting(maxShown);
        addSetting(chatLog);
    }

    @Override
    public void onDisable() {
        portals = new ArrayList<>();
        frames = new ArrayList<>();
        ticks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();

        List<BlockPos> newPortals = new ArrayList<>();
        List<BlockPos> newFrames = new ArrayList<>();

        try {
            scan:
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    for (int y = -vr; y <= vr; y++) {
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            BlockState state = mc.world.getBlockState(p);
                            if (state.isOf(Blocks.END_PORTAL)) {
                                newPortals.add(p.toImmutable());
                            } else if (state.isOf(Blocks.END_PORTAL_FRAME)) {
                                newFrames.add(p.toImmutable());
                            }
                        } catch (Throwable ignored) {
                        }
                        if (newPortals.size() >= MAX_PORTAL_BLOCKS && newFrames.size() >= MAX_FRAME_BLOCKS) break scan;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Atomic swap: markers never disappear while a scan is running.
        portals = newPortals;
        frames = newFrames;

        if (chatLog.isEnabled() && !portals.isEmpty() && mc.inGameHud != null) {
            BlockPos first = portals.get(0);
            String key = (first.getX() >> 4) + "," + (first.getZ() >> 4);
            if (logged.add(key)) {
                try {
                    mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                            String.format("§b[EndPortal] §ffound at §f%d %d %d", first.getX(), first.getY(), first.getZ())));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null) return;
        int limit = maxShown.getValue().intValue();

        if (showPortal.isEnabled()) {
            int col = color.getValue();
            int drawn = 0;
            for (BlockPos p : portals) {
                if (drawn++ >= limit) break;
                drawMarker(context, p, tickDelta, col, 5, "P");
            }
        }

        if (showFrames.isEnabled()) {
            int col = frameColor.getValue();
            int drawn = 0;
            for (BlockPos p : frames) {
                if (drawn++ >= MAX_FRAME_BLOCKS) break;
                drawMarker(context, p, tickDelta, col, 3, null);
            }
        }
    }

    private void drawMarker(DrawContext context, BlockPos p, float tickDelta, int col, int half, String tag) {
        int[] sc = com.anormal.client.util.ProjectionUtil.project(
                new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
        if (sc == null) return;
        context.fill(sc[0] - half, sc[1] - half, sc[0] + half, sc[1] + half, (col & 0x00FFFFFF) | 0x66000000);
        RenderUtils.drawBorder(context, sc[0] - half, sc[1] - half, sc[0] + half, sc[1] + half, 1, col);
        if (tag != null && mc.textRenderer != null) {
            String d = (int) Math.sqrt(p.getSquaredDistance(mc.player.getBlockPos())) + "m";
            RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + half + 2, sc[1] - 4, col, true);
        }
    }
}
