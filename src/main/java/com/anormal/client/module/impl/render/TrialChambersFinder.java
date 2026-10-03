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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TrialChambersFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Scan radius in blocks", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Blocks", "Spawners+vaults to count as chamber", 3.0, 1.0, 10.0, 1.0);
    public final BooleanSetting spawners = new BooleanSetting("Spawners", "Mark trial spawners", true);
    public final BooleanSetting vaults = new BooleanSetting("Vaults", "Mark vaults", true);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log new chambers in chat", true);
    public final ColorSetting spawnerColor = new ColorSetting("Spawner Color", "Trial spawner color", ColorUtils.rgba(255, 120, 0, 255));
    public final ColorSetting vaultColor = new ColorSetting("Vault Color", "Vault color", ColorUtils.rgba(0, 230, 255, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max chambers marked", 5.0, 1.0, 15.0, 1.0);

    private static final class Chamber {
        final BlockPos pos;
        final int spawnerCount;
        final int vaultCount;
        Chamber(BlockPos pos, int spawnerCount, int vaultCount) {
            this.pos = pos;
            this.spawnerCount = spawnerCount;
            this.vaultCount = vaultCount;
        }
    }

    private final List<Chamber> chambers = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private int ticks = 0;

    public TrialChambersFinder() {
        super("TrialChambersFinder", "Finds trial chambers by spawners and vaults", Category.RENDER);
        addSetting(range);
        addSetting(minBlocks);
        addSetting(spawners);
        addSetting(vaults);
        addSetting(chatLog);
        addSetting(spawnerColor);
        addSetting(vaultColor);
        addSetting(maxShown);
    }

    private int kind(String path) {
        if (path.contains("trial_spawner")) return 1;
        if (path.equals("vault") || path.contains("vault")) return 2;
        return 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 40) return;
        ticks = 0;
        chambers.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        List<BlockPos> spawnerPos = new ArrayList<>();
        List<BlockPos> vaultPos = new ArrayList<>();
        try {
            for (int x = -r; x <= r && spawnerPos.size() + vaultPos.size() < 300; x += 2)
                for (int y = -24; y <= 24 && spawnerPos.size() + vaultPos.size() < 300; y += 2)
                    for (int z = -r; z <= r && spawnerPos.size() + vaultPos.size() < 300; z += 2) {
                        if (x * x + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            int k = kind(path);
                            if (k == 1 && spawners.isEnabled()) spawnerPos.add(p.toImmutable());
                            else if (k == 2 && vaults.isEnabled()) vaultPos.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}

        // Cluster into chambers
        List<BlockPos> all = new ArrayList<>(spawnerPos);
        all.addAll(vaultPos);
        List<BlockPos> remaining = new ArrayList<>(all);
        int shown = 0;
        while (!remaining.isEmpty() && shown < maxShown.getValue().intValue()) {
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
                        if (dx * dx + dy * dy + dz * dz <= 900.0) {
                            cluster.add(p);
                            remaining.remove(i);
                            grew = true;
                            break;
                        }
                    }
                }
            } while (grew);
            if (cluster.size() < minBlocks.getValue().intValue()) continue;
            int sc = 0, vc = 0;
            int ax = 0, ay = 0, az = 0;
            for (BlockPos c : cluster) {
                ax += c.getX();
                ay += c.getY();
                az += c.getZ();
                try {
                    String path = Registries.BLOCK.getId(mc.world.getBlockState(c).getBlock()).getPath();
                    if (kind(path) == 1) sc++;
                    else vc++;
                } catch (Throwable ignored) {}
            }
            BlockPos center = new BlockPos(ax / cluster.size(), ay / cluster.size(), az / cluster.size());
            chambers.add(new Chamber(center, sc, vc));
            shown++;
            String key = center.getX() + "," + center.getY() + "," + center.getZ();
            if (chatLog.isEnabled() && logged.add(key) && mc.inGameHud != null) {
                try {
                    mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                            String.format("§6[Trial Chamber] §f%d spawners %d vaults at %s", sc, vc, key)));
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || chambers.isEmpty()) return;
        for (Chamber c : chambers) {
            int[] sc = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(c.pos.getX() + 0.5, c.pos.getY() + 1.0, c.pos.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            int col = c.spawnerCount >= c.vaultCount ? spawnerColor.getValue() : vaultColor.getValue();
            context.fill(sc[0] - 5, sc[1] - 5, sc[0] + 5, sc[1] + 5, (col & 0x00FFFFFF) | 0x66000000);
            context.fill(sc[0] - 5, sc[1] - 5, sc[0] + 5, sc[1] - 4, col);
            context.fill(sc[0] - 5, sc[1] + 4, sc[0] + 5, sc[1] + 5, col);
            context.fill(sc[0] - 5, sc[1] - 4, sc[0] - 4, sc[1] + 4, col);
            context.fill(sc[0] + 4, sc[1] - 4, sc[0] + 5, sc[1] + 4, col);
            if (mc.textRenderer != null) {
                String label = "§6Trial §f" + c.spawnerCount + "S/" + c.vaultCount + "V";
                double dx = c.pos.getX() - mc.player.getX();
                double dy = c.pos.getY() - mc.player.getY();
                double dz = c.pos.getZ() - mc.player.getZ();
                label += " §7" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
                RenderUtils.drawText(context, mc.textRenderer, label,
                        sc[0] - mc.textRenderer.getWidth(label) / 2, sc[1] + 7, 0xFFFFFFFF, true);
            }
        }
    }
}
