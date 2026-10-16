package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Marks signs with text preview.
 *
 * <p>Migrated to budgeted {@link BlockScanner}: no full-sphere freeze,
 * persistent cache with re-validation (no blink/ghosts), 3-axis recentre
 * and independent vertical range for deep coverage.</p>
 */
public class SignESP extends Module {
    public final ModeSetting textFilter = new ModeSetting("Text Filter", "Sign type filter", "All",
            "All", "Oak", "Spruce", "Birch", "Dark Oak", "Cherry", "Bamboo", "Crimson", "Warped");
    public final BooleanSetting showText = new BooleanSetting("Show Text", "Show sign text (best effort)", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Box outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 32.0, 8.0, 64.0, 4.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 32.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 6000.0, 1000.0, 20000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max signs marked", 20.0, 5.0, 60.0, 5.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(200, 160, 100, 255));

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private final BlockScanner scanner = new BlockScanner();
    private final Map<BlockPos, String> cache = new HashMap<>();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;

    public SignESP() {
        super("SignESP", "Marks signs with text preview", Category.RENDER);
        addSetting(textFilter);
        addSetting(showText);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(maxShown);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        cache.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
    }

    private boolean wanted(String path) {
        if (!path.contains("sign") || path.contains("signal")) return false;
        if (textFilter.is("All")) return true;
        String f = textFilter.getValue().toLowerCase().replace(" ", "_");
        if (f.equals("dark_oak")) return path.contains("dark_oak");
        return path.contains(f);
    }

    private String signText(BlockPos p) {
        try {
            net.minecraft.block.entity.BlockEntity be = mc.world.getBlockEntity(p);
            if (be instanceof SignBlockEntity sign) {
                try {
                    String t = sign.getFrontText().getMessage(0, false).getString();
                    if (t != null && !t.trim().isEmpty()) return t.trim();
                } catch (Throwable ignored) {
                }
                try {
                    String t = sign.getText(true).getMessage(0, false).getString();
                    if (t != null && !t.trim().isEmpty()) return t.trim();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private boolean isWantedSign(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return wanted(path);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();

        if (centreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - centreX) > recentreDistance.getValue()
                || Math.abs(origin.getY() - centreY) > recentreDistance.getValue()
                || Math.abs(origin.getZ() - centreZ) > recentreDistance.getValue()) {
            centreX = origin.getX();
            centreY = origin.getY();
            centreZ = origin.getZ();
            scanner.resetCylinder();
            prune(origin, r, vr);
        }

        cache.entrySet().removeIf(e -> {
            if (!mc.world.isChunkLoaded(e.getKey())) return false;
            return !isWantedSign(mc.world.getBlockState(e.getKey()));
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ, r, vr,
                    scanSpeed.getValue().intValue(), scratch,
                    state -> !state.isAir() && isWantedSign(state),
                    (pos, state) -> {
                        if (cache.size() < maxShown.getValue().intValue()) {
                            cache.put(pos.toImmutable(), "");
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private void prune(BlockPos origin, int range, int verticalRange) {
        int rr = range * range;
        cache.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > verticalRange + 2;
        });
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cache.isEmpty()) return;

        int col = color.getValue();
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        int limit = maxShown.getValue().intValue();
        int drawn = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        for (Map.Entry<BlockPos, String> e : cache.entrySet()) {
            if (drawn >= limit) break;
            if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;

            BlockPos p = e.getKey();
            int[] sc = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            drawn++;

            if (box.isEnabled()) {
                context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] + 3, (col & 0x00FFFFFF) | 0x55000000);
                if (outline.isEnabled()) {
                    context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] - 2, col);
                    context.fill(sc[0] - 3, sc[1] + 2, sc[0] + 3, sc[1] + 3, col);
                }
            } else {
                context.fill(sc[0] - 2, sc[1] - 2, sc[0] + 2, sc[1] + 2, col);
            }
            if (tracer.isEnabled()) {
                int steps = Math.min(200, Math.max(Math.abs(sc[0] - w / 2), Math.abs(sc[1] - h / 2)));
                for (int i = 0; i <= steps; i++) {
                    int x = w / 2 + (sc[0] - w / 2) * i / Math.max(1, steps);
                    int y = h / 2 + (sc[1] - h / 2) * i / Math.max(1, steps);
                    context.fill(x, y, x + 1, y + 1, col);
                }
            }
            if (mc.textRenderer != null) {
                StringBuilder label = new StringBuilder();
                if (showText.isEnabled()) {
                    String t = signText(p);
                    if (!t.isEmpty()) {
                        if (t.length() > 20) t = t.substring(0, 20) + "…";
                        label.append("§e\"").append(t).append("\" ");
                    }
                }
                if (showDistance.isEnabled()) {
                    double dx = p.getX() - mc.player.getX(), dy = p.getY() - mc.player.getY(), dz = p.getZ() - mc.player.getZ();
                    label.append("§7").append((int) Math.sqrt(dx * dx + dy * dy + dz * dz)).append("m");
                }
                if (label.length() > 0) {
                    String text = label.toString();
                    RenderUtils.drawText(context, mc.textRenderer, text, sc[0] + 5, sc[1] - 4, 0xFFFFFFFF, true);
                }
            }
        }
    }
}
