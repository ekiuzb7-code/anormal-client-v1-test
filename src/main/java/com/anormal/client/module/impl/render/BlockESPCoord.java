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

public class BlockESPCoord extends Module {
    public final BooleanSetting chest = new BooleanSetting("Chest", "Mark chests", true);
    public final BooleanSetting bed = new BooleanSetting("Bed", "Mark beds", true);
    public final BooleanSetting door = new BooleanSetting("Door", "Mark doors", false);
    public final BooleanSetting crafting = new BooleanSetting("Crafting Table", "Mark crafting tables", false);
    public final BooleanSetting furnace = new BooleanSetting("Furnace", "Mark furnaces", false);
    public final BooleanSetting enchanting = new BooleanSetting("Enchanting", "Mark enchanting tables", false);
    public final BooleanSetting anvil = new BooleanSetting("Anvil", "Mark anvils", false);
    public final BooleanSetting beacon = new BooleanSetting("Beacon", "Mark beacons", true);
    public final BooleanSetting spawner = new BooleanSetting("Spawner", "Mark spawners", true);
    public final BooleanSetting shulker = new BooleanSetting("Shulker", "Mark shulkers", false);
    public final BooleanSetting showName = new BooleanSetting("Block Name", "Show block name", true);
    public final BooleanSetting showDistance = new BooleanSetting("Distance", "Show distance", true);
    public final BooleanSetting showCoords = new BooleanSetting("Coordinates", "Show coordinates", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Box outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Scan radius", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 300.0, 50.0, 1000.0, 25.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max blocks marked", 40.0, 5.0, 150.0, 5.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 170, 0, 255));

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

    public BlockESPCoord() {
        super("BlockESPCoord", "Marks chosen blocks with coords", Category.RENDER);
        addSetting(chest);
        addSetting(bed);
        addSetting(door);
        addSetting(crafting);
        addSetting(furnace);
        addSetting(enchanting);
        addSetting(anvil);
        addSetting(beacon);
        addSetting(spawner);
        addSetting(shulker);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showCoords);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(range);
        addSetting(maxDistance);
        addSetting(maxShown);
        addSetting(color);
    }

    private String match(String path) {
        if (path.contains("chest") && chest.isEnabled()) return "Chest";
        if (path.contains("bed") && !path.contains("bedrock") && bed.isEnabled()) return "Bed";
        if ((path.contains("_door") || path.equals("door")) && door.isEnabled()) return "Door";
        if (path.contains("crafting_table") && crafting.isEnabled()) return "Crafting";
        if (path.contains("furnace") && furnace.isEnabled()) return "Furnace";
        if (path.contains("enchanting_table") && enchanting.isEnabled()) return "Enchanting";
        if (path.contains("anvil") && anvil.isEnabled()) return "Anvil";
        if (path.equals("beacon") && beacon.isEnabled()) return "Beacon";
        if (path.contains("spawner") && spawner.isEnabled()) return "Spawner";
        if (path.contains("shulker") && shulker.isEnabled()) return "Shulker";
        return null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 12) return;
        ticks = 0;
        found.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        double maxD = maxDistance.getValue();
        try {
            for (int x = -r; x <= r && found.size() < maxShown.getValue().intValue(); x++)
                for (int y = -r; y <= r && found.size() < maxShown.getValue().intValue(); y++)
                    for (int z = -r; z <= r && found.size() < maxShown.getValue().intValue(); z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            double dx = p.getX() - origin.getX(), dy = p.getY() - origin.getY(), dz = p.getZ() - origin.getZ();
                            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > maxD) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            String m = match(path);
                            if (m != null) found.add(new Found(m, p.toImmutable()));
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;
        int col = color.getValue();
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        for (Found f : found) {
            int[] sc = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(f.pos.getX() + 0.5, f.pos.getY() + 0.5, f.pos.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            if (box.isEnabled()) {
                context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x55000000);
                if (outline.isEnabled()) {
                    context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] - 3, col);
                    context.fill(sc[0] - 4, sc[1] + 3, sc[0] + 4, sc[1] + 4, col);
                    context.fill(sc[0] - 4, sc[1] - 3, sc[0] - 3, sc[1] + 3, col);
                    context.fill(sc[0] + 3, sc[1] - 3, sc[0] + 4, sc[1] + 3, col);
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
                if (showName.isEnabled()) label.append("§6").append(f.name);
                if (showDistance.isEnabled()) {
                    double dx = f.pos.getX() - mc.player.getX();
                    double dy = f.pos.getY() - mc.player.getY();
                    double dz = f.pos.getZ() - mc.player.getZ();
                    if (label.length() > 0) label.append(" ");
                    label.append("§f").append((int) Math.sqrt(dx * dx + dy * dy + dz * dz)).append("m");
                }
                if (showCoords.isEnabled()) {
                    if (label.length() > 0) label.append(" ");
                    label.append(String.format("§7X:%d Y:%d Z:%d", f.pos.getX(), f.pos.getY(), f.pos.getZ()));
                }
                if (label.length() > 0) {
                    String text = label.toString();
                    RenderUtils.drawText(context, mc.textRenderer, text,
                            sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] + 6, 0xFFFFFFFF, true);
                }
            }
        }
    }
}
