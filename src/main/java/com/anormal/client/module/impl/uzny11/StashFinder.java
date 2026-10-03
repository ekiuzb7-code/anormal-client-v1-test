package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StashFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Scan radius in blocks", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting minStorages = new NumberSetting("Min Storages", "Containers to count as stash", 3.0, 2.0, 10.0, 1.0);
    public final BooleanSetting logInChat = new BooleanSetting("Log In Chat", "Log new stashes in chat", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final ColorSetting color = new ColorSetting("Color", "Stash marker color", ColorUtils.rgba(255, 220, 0, 255));

    private static final class Stash {
        final BlockPos pos;
        final int count;
        Stash(BlockPos pos, int count) {
            this.pos = pos;
            this.count = count;
        }
    }

    private final List<Stash> stashes = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private int ticks = 0;

    public StashFinder() {
        super("StashFinder", "Finds hidden storage clusters and logs them", Category.UZNY11);
        addSetting(range);
        addSetting(minStorages);
        addSetting(logInChat);
        addSetting(showDistance);
        addSetting(color);
    }

    private boolean isStorage(String path) {
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker")
                || path.contains("hopper") || path.contains("furnace") || path.contains("dispenser")
                || path.contains("dropper");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 40) return;
        ticks = 0;
        stashes.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        List<BlockPos> found = new ArrayList<>();
        try {
            for (int x = -r; x <= r && found.size() < 400; x += 2)
                for (int y = -16; y <= 16 && found.size() < 400; y += 2)
                    for (int z = -r; z <= r && found.size() < 400; z += 2) {
                        if (x * x + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (isStorage(path)) found.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}

        // Cluster nearby containers into stashes
        List<BlockPos> remaining = new ArrayList<>(found);
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.remove(0);
            List<BlockPos> cluster = new ArrayList<>();
            cluster.add(seed);
            boolean grew;
            do {
                grew = false;
                for (int i = remaining.size() - 1; i >= 0; i--) {
                    BlockPos p = remaining.get(i);
                    for (BlockPos c : cluster) {
                        double dx = p.getX() - c.getX(), dy = p.getY() - c.getY(), dz = p.getZ() - c.getZ();
                        if (dx * dx + dy * dy + dz * dz <= 64.0) {
                            cluster.add(p);
                            remaining.remove(i);
                            grew = true;
                            break;
                        }
                    }
                }
            } while (grew);
            if (cluster.size() >= minStorages.getValue().intValue()) {
                int ax = 0, ay = 0, az = 0;
                for (BlockPos c : cluster) {
                    ax += c.getX();
                    ay += c.getY();
                    az += c.getZ();
                }
                BlockPos center = new BlockPos(ax / cluster.size(), ay / cluster.size(), az / cluster.size());
                stashes.add(new Stash(center, cluster.size()));
                String key = center.getX() + "," + center.getY() + "," + center.getZ();
                if (logInChat.isEnabled() && logged.add(key) && mc.inGameHud != null) {
                    try {
                        mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                                "§6[Stash] §f" + cluster.size() + " containers at " + key));
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || stashes.isEmpty()) return;
        int col = color.getValue();
        for (Stash s : stashes) {
            int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x66000000);
            context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] - 3, col);
            context.fill(sc[0] - 4, sc[1] + 3, sc[0] + 4, sc[1] + 4, col);
            context.fill(sc[0] - 4, sc[1] - 3, sc[0] - 3, sc[1] + 3, col);
            context.fill(sc[0] + 3, sc[1] - 3, sc[0] + 4, sc[1] + 3, col);
            if (mc.textRenderer != null) {
                String label = "§6" + s.count + "x" + (showDistance.isEnabled() ? " " + (int) Math.sqrt(mc.player.getBlockPos().getSquaredDistance(s.pos)) + "m" : "");
                RenderUtils.drawText(context, mc.textRenderer, label, sc[0] + 6, sc[1] - 4, 0xFFFFFFFF, true);
            }
        }
    }
}
