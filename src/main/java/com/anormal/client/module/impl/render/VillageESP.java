package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class VillageESP extends Module {
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show VILLAGE label", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", true);
    public final BooleanSetting showVillagers = new BooleanSetting("Show Villager Count", "Show villager count", true);
    public final BooleanSetting showBeds = new BooleanSetting("Show Bed Count", "Show bed count", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Scan radius", 64.0, 16.0, 160.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 800.0, 50.0, 2000.0, 50.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(85, 255, 85, 255));

    private BlockPos villagePos = null;
    private int villagers = 0;
    private int beds = 0;
    private int ticks = 0;

    public VillageESP() {
        super("VillageESP", "Marks villages with villager and bed counts", Category.RENDER);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showCoords);
        addSetting(showVillagers);
        addSetting(showBeds);
        addSetting(box);
        addSetting(tracer);
        addSetting(range);
        addSetting(maxDistance);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 60) return;
        ticks = 0;
        villagePos = null;
        villagers = 0;
        beds = 0;
        try {
            double best = maxDistance.getValue();
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof VillagerEntity && e.isAlive()) {
                    double d = mc.player.distanceTo(e);
                    if (d < best) {
                        best = d;
                        villagePos = e.getBlockPos();
                    }
                    villagers++;
                }
            }
            if (villagePos == null) return;
            int r = range.getValue().intValue();
            BlockPos origin = villagePos;
            for (int x = -r; x <= r; x += 2)
                for (int y = -12; y <= 12; y += 2)
                    for (int z = -r; z <= r; z += 2) {
                        if (x * x + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (path.contains("bed") && !path.contains("bedrock")) beds++;
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || villagePos == null) return;
        int[] sc = com.anormal.client.util.ProjectionUtil.project(
                new Vec3d(villagePos.getX() + 0.5, villagePos.getY() + 2.0, villagePos.getZ() + 0.5), tickDelta);
        if (sc == null) return;
        int col = color.getValue();
        if (box.isEnabled()) {
            context.fill(sc[0] - 8, sc[1] - 8, sc[0] + 8, sc[1] + 8, (col & 0x00FFFFFF) | 0x44000000);
            context.fill(sc[0] - 8, sc[1] - 8, sc[0] + 8, sc[1] - 7, col);
            context.fill(sc[0] - 8, sc[1] + 7, sc[0] + 8, sc[1] + 8, col);
        }
        if (tracer.isEnabled()) {
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            int steps = Math.min(200, Math.max(Math.abs(sc[0] - w / 2), Math.abs(sc[1] - h / 2)));
            for (int i = 0; i <= steps; i++) {
                int x = w / 2 + (sc[0] - w / 2) * i / Math.max(1, steps);
                int y = h / 2 + (sc[1] - h / 2) * i / Math.max(1, steps);
                context.fill(x, y, x + 1, y + 1, col);
            }
        }
        if (mc.textRenderer != null) {
            StringBuilder label = new StringBuilder();
            if (showName.isEnabled()) label.append("§aVILLAGE");
            double dx = villagePos.getX() - mc.player.getX();
            double dy = villagePos.getY() - mc.player.getY();
            double dz = villagePos.getZ() - mc.player.getZ();
            int d = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (showDistance.isEnabled()) {
                if (label.length() > 0) label.append(" ");
                label.append("§f").append(d).append("m");
            }
            if (showCoords.isEnabled()) {
                if (label.length() > 0) label.append(" ");
                label.append(String.format("§7%d %d %d", villagePos.getX(), villagePos.getY(), villagePos.getZ()));
            }
            if (showVillagers.isEnabled()) label.append(" §e👤").append(villagers);
            if (showBeds.isEnabled()) label.append(" §c🛏").append(beds);
            if (label.length() > 0) {
                String text = label.toString();
                RenderUtils.drawText(context, mc.textRenderer, text,
                        sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] + 10, 0xFFFFFFFF, true);
            }
        }
    }
}
