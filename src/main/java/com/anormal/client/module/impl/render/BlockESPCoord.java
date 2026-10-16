package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Marks chosen blocks with coords.
 *
 * <p>Migrated to budgeted {@link BlockScanner}: no full-sphere freeze,
 * persistent cache with re-validation (no blink/ghosts), 3-axis recentre
 * and independent vertical range for deep coverage.</p>
 */
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
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 48.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 300.0, 50.0, 1000.0, 25.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max blocks marked", 40.0, 5.0, 150.0, 5.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 170, 0, 255));

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private final BlockScanner scanner = new BlockScanner();
    private final Map<BlockPos, String> cache = new HashMap<>();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;

    public BlockESPCoord() {
        super("BlockESPCoord", "Marks chosen blocks with coords", Category.RENDER);
        addSetting(chest); addSetting(bed); addSetting(door); addSetting(crafting);
        addSetting(furnace); addSetting(enchanting); addSetting(anvil); addSetting(beacon);
        addSetting(spawner); addSetting(shulker);
        addSetting(showName); addSetting(showDistance); addSetting(showCoords);
        addSetting(outline); addSetting(box); addSetting(tracer);
        addSetting(range); addSetting(verticalRange); addSetting(scanSpeed); addSetting(recentreDistance);
        addSetting(maxDistance); addSetting(maxShown); addSetting(color);
    }

    @Override
    public void onDisable() {
        cache.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
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

    private boolean isWanted(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return match(path) != null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();
        double maxD = maxDistance.getValue();
        int maxDsq = (int) (maxD * maxD);

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
            String m = match(Registries.BLOCK.getId(mc.world.getBlockState(e.getKey()).getBlock()).getPath());
            return m == null;
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ, r, vr,
                    scanSpeed.getValue().intValue(), scratch,
                    state -> !state.isAir() && isWanted(state),
                    (pos, state) -> {
                        if (cache.size() >= maxShown.getValue().intValue()) return;
                        int dx = pos.getX() - origin.getX();
                        int dy = pos.getY() - origin.getY();
                        int dz = pos.getZ() - origin.getZ();
                        if (dx * dx + dy * dy + dz * dz > maxDsq) return;
                        String name = match(Registries.BLOCK.getId(state.getBlock()).getPath());
                        if (name != null) cache.put(pos.toImmutable(), name);
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
                    new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            drawn++;

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
                if (showName.isEnabled()) label.append("§6").append(e.getValue());
                if (showDistance.isEnabled()) {
                    double dx = p.getX() - mc.player.getX();
                    double dy = p.getY() - mc.player.getY();
                    double dz = p.getZ() - mc.player.getZ();
                    if (label.length() > 0) label.append(" ");
                    label.append("§f").append((int) Math.sqrt(dx * dx + dy * dy + dz * dz)).append("m");
                }
                if (showCoords.isEnabled()) {
                    if (label.length() > 0) label.append(" ");
                    label.append(String.format("§7X:%d Y:%d Z:%d", p.getX(), p.getY(), p.getZ()));
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
