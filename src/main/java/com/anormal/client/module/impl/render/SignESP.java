package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class SignESP extends Module {
    public final ModeSetting textFilter = new ModeSetting("Text Filter", "Sign type filter", "All",
            "All", "Oak", "Spruce", "Birch", "Dark Oak", "Cherry", "Bamboo", "Crimson", "Warped");
    public final BooleanSetting showText = new BooleanSetting("Show Text", "Show sign text (best effort)", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Box outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final NumberSetting range = new NumberSetting("Range", "Scan radius", 32.0, 8.0, 64.0, 4.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max signs marked", 20.0, 5.0, 60.0, 5.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(200, 160, 100, 255));

    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;

    public SignESP() {
        super("SignESP", "Marks signs with text preview", Category.RENDER);
        addSetting(textFilter);
        addSetting(showText);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(range);
        addSetting(maxShown);
        addSetting(color);
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
            if (be instanceof net.minecraft.block.entity.SignBlockEntity sign) {
                try {
                    String t = sign.getFrontText().getMessage(0, false).getString();
                    if (t != null && !t.trim().isEmpty()) return t.trim();
                } catch (Throwable ignored) {}
                try {
                    String t = sign.getText(true).getMessage(0, false).getString();
                    if (t != null && !t.trim().isEmpty()) return t.trim();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return "";
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 12) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < maxShown.getValue().intValue(); x++)
                for (int y = -r; y <= r && cache.size() < maxShown.getValue().intValue(); y++)
                    for (int z = -r; z <= r && cache.size() < maxShown.getValue().intValue(); z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (wanted(path)) cache.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cache.isEmpty()) return;
        int col = color.getValue();
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        for (BlockPos p : cache) {
            int[] sc = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
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
