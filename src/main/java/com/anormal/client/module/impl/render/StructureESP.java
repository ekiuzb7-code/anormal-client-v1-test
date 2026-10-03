package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class StructureESP extends Module {
    public final BooleanSetting village = new BooleanSetting("Village", "Mark villages (bed clusters)", true);
    public final BooleanSetting stronghold = new BooleanSetting("Stronghold", "Mark end portal frames", true);
    public final BooleanSetting ancientCity = new BooleanSetting("Ancient City", "Mark sculk clusters", true);
    public final BooleanSetting mansion = new BooleanSetting("Mansion", "Mark dark oak clusters", false);
    public final BooleanSetting monument = new BooleanSetting("Monument", "Mark prismarine clusters", false);
    public final BooleanSetting bastion = new BooleanSetting("Bastion", "Mark blackstone clusters", false);
    public final BooleanSetting fortress = new BooleanSetting("Fortress", "Mark nether brick clusters", false);
    public final BooleanSetting endCity = new BooleanSetting("End City", "Mark end stone clusters", false);
    public final BooleanSetting desertTemple = new BooleanSetting("Desert Temple", "Mark chiseled sandstone", false);
    public final BooleanSetting jungleTemple = new BooleanSetting("Jungle Temple", "Mark mossy stone clusters", false);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show structure name", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", false);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Scan radius", 64.0, 16.0, 128.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 500.0, 50.0, 2000.0, 50.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 220, 0, 255));

    private static final class Found {
        final String name;
        final BlockPos pos;
        Found(String name, BlockPos pos) {
            this.name = name;
            this.pos = pos;
        }
    }

    private final List<Found> found = new ArrayList<>();
    private int ticks = 0;

    public StructureESP() {
        super("StructureESP", "Marks structures by characteristic blocks", Category.RENDER);
        addSetting(village);
        addSetting(stronghold);
        addSetting(ancientCity);
        addSetting(mansion);
        addSetting(monument);
        addSetting(bastion);
        addSetting(fortress);
        addSetting(endCity);
        addSetting(desertTemple);
        addSetting(jungleTemple);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showCoords);
        addSetting(box);
        addSetting(tracer);
        addSetting(range);
        addSetting(maxDistance);
        addSetting(color);
    }

    private String match(String path) {
        if (path.contains("trial_spawner")) return null;
        if (path.contains("bed") && !path.contains("bedrock")) return "Village";
        if (path.contains("end_portal_frame")) return "Stronghold";
        if (path.contains("sculk")) return "Ancient City";
        if (path.contains("dark_oak_log")) return "Mansion";
        if (path.contains("prismarine")) return "Monument";
        if (path.contains("blackstone") || path.contains("basalt")) return "Bastion";
        if (path.contains("nether_brick") && !path.contains("chiseled")) return "Fortress";
        if (path.contains("end_stone_brick") || path.contains("purpur")) return "End City";
        if (path.contains("chiseled_sandstone") || path.contains("cut_sandstone")) return "Desert Temple";
        if (path.contains("mossy_cobblestone") || path.contains("chiseled_stone")) return "Jungle Temple";
        return null;
    }

    private boolean enabledFor(String name) {
        return switch (name) {
            case "Village" -> village.isEnabled();
            case "Stronghold" -> stronghold.isEnabled();
            case "Ancient City" -> ancientCity.isEnabled();
            case "Mansion" -> mansion.isEnabled();
            case "Monument" -> monument.isEnabled();
            case "Bastion" -> bastion.isEnabled();
            case "Fortress" -> fortress.isEnabled();
            case "End City" -> endCity.isEnabled();
            case "Desert Temple" -> desertTemple.isEnabled();
            case "Jungle Temple" -> jungleTemple.isEnabled();
            default -> false;
        };
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 60) return;
        ticks = 0;
        found.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        double maxD = maxDistance.getValue();
        try {
            java.util.Map<String, List<BlockPos>> hits = new java.util.HashMap<>();
            for (int x = -r; x <= r; x += 4)
                for (int y = -24; y <= 24; y += 4)
                    for (int z = -r; z <= r; z += 4) {
                        if (x * x + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            double dx = p.getX() - origin.getX(), dy = p.getY() - origin.getY(), dz = p.getZ() - origin.getZ();
                            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > maxD) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            String m = match(path);
                            if (m != null && enabledFor(m)) {
                                hits.computeIfAbsent(m, k -> new ArrayList<>()).add(p.toImmutable());
                                if (hits.size() >= 40) break;
                            }
                        } catch (Throwable ignored) {}
                    }
            for (var e : hits.entrySet()) {
                if (e.getValue().size() < 3) continue;
                int ax = 0, ay = 0, az = 0;
                for (BlockPos p : e.getValue()) {
                    ax += p.getX();
                    ay += p.getY();
                    az += p.getZ();
                }
                int n = e.getValue().size();
                found.add(new Found(e.getKey(), new BlockPos(ax / n, ay / n, az / n)));
                if (found.size() >= 8) break;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;
        for (Found f : found) {
            int[] sc = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(f.pos.getX() + 0.5, f.pos.getY() + 1.0, f.pos.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            int col = color.getValue();
            if (box.isEnabled()) {
                context.fill(sc[0] - 6, sc[1] - 6, sc[0] + 6, sc[1] + 6, (col & 0x00FFFFFF) | 0x55000000);
                context.fill(sc[0] - 6, sc[1] - 6, sc[0] + 6, sc[1] - 5, col);
                context.fill(sc[0] - 6, sc[1] + 5, sc[0] + 6, sc[1] + 6, col);
                context.fill(sc[0] - 6, sc[1] - 5, sc[0] - 5, sc[1] + 5, col);
                context.fill(sc[0] + 5, sc[1] - 5, sc[0] + 6, sc[1] + 5, col);
            }
            if (tracer.isEnabled()) {
                int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
                line(context, w / 2, h / 2, sc[0], sc[1], col);
            }
            if (mc.textRenderer != null) {
                StringBuilder label = new StringBuilder();
                if (showName.isEnabled()) label.append("§6").append(f.name);
                if (showDistance.isEnabled() || showCoords.isEnabled()) {
                    double dx = f.pos.getX() - mc.player.getX();
                    double dy = f.pos.getY() - mc.player.getY();
                    double dz = f.pos.getZ() - mc.player.getZ();
                    int d = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (showDistance.isEnabled()) {
                        if (label.length() > 0) label.append(" ");
                        label.append("§f").append(d).append("m");
                    }
                    if (showCoords.isEnabled()) {
                        if (label.length() > 0) label.append(" ");
                        label.append(String.format("§7%d %d %d", f.pos.getX(), f.pos.getY(), f.pos.getZ()));
                    }
                }
                if (label.length() > 0) {
                    String text = label.toString();
                    RenderUtils.drawText(context, mc.textRenderer, text,
                            sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] + 8, 0xFFFFFFFF, true);
                }
            }
        }
    }

    private void line(DrawContext context, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.min(200, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        if (steps == 0) {
            context.fill(x1, y1, x1 + 1, y1 + 1, col);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            context.fill(x, y, x + 1, y + 1, col);
        }
    }
}
