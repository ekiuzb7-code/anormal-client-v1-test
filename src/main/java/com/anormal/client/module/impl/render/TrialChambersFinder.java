package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds trial chambers by scanning for trial spawners and vaults and clustering
 * them into chambers.
 *
 * <p><b>Fixes:</b> the old scan advanced by 2 blocks on every axis, so it only
 * inspected 1/8 of the area and missed most spawners/vaults (which sit on
 * arbitrary coordinates). It also matched blocks by building an
 * {@code Identifier} string for every single block, which was extremely slow.
 * This version scans every block and compares cached {@link Block} instances
 * directly.</p>
 */
public class TrialChambersFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up/down to scan", 32.0, 8.0, 96.0, 8.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Blocks", "Spawners + vaults to count as a chamber", 2.0, 1.0, 10.0, 1.0);
    public final BooleanSetting spawners = new BooleanSetting("Spawners", "Mark trial spawners", true);
    public final BooleanSetting vaults = new BooleanSetting("Vaults", "Mark vaults", true);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log new chambers in chat", true);
    public final ColorSetting spawnerColor = new ColorSetting("Spawner Color", "Trial spawner color", ColorUtils.rgba(255, 120, 0, 255));
    public final ColorSetting vaultColor = new ColorSetting("Vault Color", "Vault color", ColorUtils.rgba(0, 230, 255, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max chambers marked", 5.0, 1.0, 15.0, 1.0);

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int MAX_BLOCKS_PER_SCAN = 600;
    private static final double CLUSTER_RADIUS_SQ = 400.0; // 20 blocks

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

    // Cached block references (resolved once, avoids per-block Identifier lookups).
    private Block trialSpawnerBlock;
    private Block vaultBlock;
    private Block ominousVaultBlock;

    private final List<Chamber> chambers = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private int ticks = 0;

    public TrialChambersFinder() {
        super("TrialChambersFinder", "Finds trial chambers by spawners and vaults", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(minBlocks);
        addSetting(spawners);
        addSetting(vaults);
        addSetting(chatLog);
        addSetting(spawnerColor);
        addSetting(vaultColor);
        addSetting(maxShown);
        resolveBlocks();
    }

    private void resolveBlocks() {
        trialSpawnerBlock = resolve("minecraft:trial_spawner", Blocks.TRIAL_SPAWNER);
        vaultBlock = resolve("minecraft:vault", Blocks.VAULT);
        ominousVaultBlock = resolve("minecraft:ominous_vault", null);
    }

    private Block resolve(String id, Block fallback) {
        try {
            Block b = Registries.BLOCK.get(Identifier.of(id));
            return b != null ? b : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    /** 1 = trial spawner, 2 = vault, 0 = neither. */
    private int kind(Block block) {
        if (block == null) return 0;
        if (block == trialSpawnerBlock) return 1;
        if (block == vaultBlock) return 2;
        if (ominousVaultBlock != null && block == ominousVaultBlock) return 2;
        return 0;
    }

    @Override
    public void onDisable() {
        chambers.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        // Re-resolve lazily (registries are definitely loaded by now).
        if (trialSpawnerBlock == null) resolveBlocks();

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();

        List<BlockPos> spawnerPos = new ArrayList<>();
        List<BlockPos> vaultPos = new ArrayList<>();

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
                            int k = kind(state.getBlock());
                            if (k == 1) {
                                if (spawners.isEnabled()) spawnerPos.add(p.toImmutable());
                            } else if (k == 2) {
                                if (vaults.isEnabled()) vaultPos.add(p.toImmutable());
                            }
                        } catch (Throwable ignored) {
                        }
                        if (spawnerPos.size() + vaultPos.size() >= MAX_BLOCKS_PER_SCAN) break scan;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        clusterChambers(spawnerPos, vaultPos);
    }

    private void clusterChambers(List<BlockPos> spawnerPos, List<BlockPos> vaultPos) {
        chambers.clear();

        List<BlockPos> remaining = new ArrayList<>(spawnerPos);
        remaining.addAll(vaultPos);

        int shown = 0;
        int max = maxShown.getValue().intValue();

        while (!remaining.isEmpty() && shown < max) {
            BlockPos seed = remaining.remove(0);
            List<BlockPos> cluster = new ArrayList<>();
            cluster.add(seed);

            boolean grew;
            do {
                grew = false;
                for (int i = remaining.size() - 1; i >= 0; i--) {
                    BlockPos p = remaining.get(i);
                    for (BlockPos c : cluster) {
                        double dx = p.getX() - c.getX();
                        double dy = p.getY() - c.getY();
                        double dz = p.getZ() - c.getZ();
                        if (dx * dx + dy * dy + dz * dz <= CLUSTER_RADIUS_SQ) {
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
                    int k = kind(mc.world.getBlockState(c).getBlock());
                    if (k == 1) sc++;
                    else vc++;
                } catch (Throwable ignored) {
                }
            }

            BlockPos center = new BlockPos(ax / cluster.size(), ay / cluster.size(), az / cluster.size());
            chambers.add(new Chamber(center, sc, vc));
            shown++;

            String key = center.getX() + "," + center.getY() + "," + center.getZ();
            if (chatLog.isEnabled() && logged.add(key) && mc.inGameHud != null) {
                try {
                    mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                            String.format("§6[Trial Chamber] §f%d spawners, %d vaults at %s", sc, vc, key)));
                } catch (Throwable ignored) {
                }
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
