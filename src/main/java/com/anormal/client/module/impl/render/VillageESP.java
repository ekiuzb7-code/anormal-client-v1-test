package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Marks villages with villager and bed counts.
 *
 * <p>The entity scan for villagers is kept (fast). The bed scan is now
 * budgeted via {@link BlockScanner} with independent vertical range so
 * deep beds are counted too.</p>
 */
public class VillageESP extends Module {
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show VILLAGE label", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", true);
    public final BooleanSetting showVillagers = new BooleanSetting("Show Villager Count", "Show villager count", true);
    public final BooleanSetting showBeds = new BooleanSetting("Show Bed Count", "Show bed count", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Villager search radius", 64.0, 16.0, 160.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 800.0, 50.0, 2000.0, 50.0);
    public final NumberSetting bedScanRange = new NumberSetting("Bed Scan Range", "Bed search radius around village", 32.0, 8.0, 64.0, 4.0);
    public final NumberSetting bedVerticalRange = new NumberSetting("Bed Vertical Range", "How far up/down to count beds", 24.0, 8.0, 80.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(85, 255, 85, 255));

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private final BlockScanner scanner = new BlockScanner();

    private BlockPos villagePos = null;
    private int villagers = 0;
    private int beds = 0;
    private int ticks = 0;
    private int bedScanTicks = 0;
    private int bedCentreX = Integer.MIN_VALUE, bedCentreY, bedCentreZ;

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
        addSetting(bedScanRange);
        addSetting(bedVerticalRange);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        villagePos = null;
        villagers = 0;
        beds = 0;
        scanner.resetCylinder();
        bedCentreX = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 20) return;
        ticks = 0;

        villagePos = null;
        villagers = 0;
        double best = maxDistance.getValue();
        try {
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
        } catch (Throwable ignored) {
        }

        if (showBeds.isEnabled() && villagePos != null) {
            tickBedScan();
        } else {
            beds = 0;
        }
    }

    private void tickBedScan() {
        int r = bedScanRange.getValue().intValue();
        int vr = bedVerticalRange.getValue().intValue();
        BlockPos origin = villagePos;

        if (bedCentreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - bedCentreX) > 4
                || Math.abs(origin.getY() - bedCentreY) > 4
                || Math.abs(origin.getZ() - bedCentreZ) > 4) {
            bedCentreX = origin.getX();
            bedCentreY = origin.getY();
            bedCentreZ = origin.getZ();
            scanner.resetCylinder();
            beds = 0;
        }

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, bedCentreX, bedCentreY, bedCentreZ, r, vr,
                    2000, scratch,
                    state -> !state.isAir(),
                    (pos, state) -> {
                        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
                        if (path.contains("bed") && !path.contains("bedrock")) beds++;
                    });
        } catch (Throwable ignored) {
        }
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
