package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Marks ores through walls.
 *
 * <p>Every ore has its own on/off toggle, its own <b>outline</b> toggle and its
 * own <b>outline colour</b>, so you can hide the outlines for common ores and
 * keep a bright colour for the rare ones.</p>
 *
 * <p>The markers are drawn as real world-space block outlines, and the
 * projection uses the interpolated render camera - so they stay pinned to
 * their blocks while you walk, run or fly instead of sliding around.</p>
 */
public class OreESP extends Module {

    // ---- Ore table -------------------------------------------------------
    private static final String[][] ORE_IDS = {
            {"diamond_ore", "deepslate_diamond_ore", "diamond_block"},
            {"ancient_debris", "netherite_block"},
            {"gold_ore", "deepslate_gold_ore", "nether_gold_ore", "raw_gold_block", "gold_block"},
            {"iron_ore", "deepslate_iron_ore", "raw_iron_block", "iron_block"},
            {"emerald_ore", "deepslate_emerald_ore", "emerald_block"},
            {"lapis_ore", "deepslate_lapis_ore", "lapis_block"},
            {"redstone_ore", "deepslate_redstone_ore", "redstone_block"},
            {"coal_ore", "deepslate_coal_ore", "coal_block"},
            {"copper_ore", "deepslate_copper_ore", "raw_copper_block", "copper_block"},
            {"nether_quartz_ore", "quartz_block"},
    };
    private static final int N_DIAMOND = 0, N_DEBRIS = 1, N_GOLD = 2, N_IRON = 3, N_EMERALD = 4,
            N_LAPIS = 5, N_REDSTONE = 6, N_COAL = 7, N_COPPER = 8, N_QUARTZ = 9;

    // ---- Per-ore: enabled / outline / colour ----------------------------
    public final BooleanSetting diamond = new BooleanSetting("Diamond", "Mark diamond ores", true);
    public final BooleanSetting diamondOutline = new BooleanSetting("Diamond Outline", "Draw diamond outlines", true);
    public final ColorSetting diamondColor = new ColorSetting("Diamond Color", "Diamond outline color", ColorUtils.rgba(80, 240, 255, 255));

    public final BooleanSetting ancientDebris = new BooleanSetting("Ancient Debris", "Mark ancient debris", true);
    public final BooleanSetting ancientDebrisOutline = new BooleanSetting("Ancient Debris Outline", "Draw debris outlines", true);
    public final ColorSetting ancientDebrisColor = new ColorSetting("Ancient Debris Color", "Debris outline color", ColorUtils.rgba(160, 90, 200, 255));

    public final BooleanSetting gold = new BooleanSetting("Gold", "Mark gold ores", true);
    public final BooleanSetting goldOutline = new BooleanSetting("Gold Outline", "Draw gold outlines", true);
    public final ColorSetting goldColor = new ColorSetting("Gold Color", "Gold outline color", ColorUtils.rgba(255, 215, 0, 255));

    public final BooleanSetting iron = new BooleanSetting("Iron", "Mark iron ores", true);
    public final BooleanSetting ironOutline = new BooleanSetting("Iron Outline", "Draw iron outlines", true);
    public final ColorSetting ironColor = new ColorSetting("Iron Color", "Iron outline color", ColorUtils.rgba(215, 175, 150, 255));

    public final BooleanSetting emerald = new BooleanSetting("Emerald", "Mark emerald ores", true);
    public final BooleanSetting emeraldOutline = new BooleanSetting("Emerald Outline", "Draw emerald outlines", true);
    public final ColorSetting emeraldColor = new ColorSetting("Emerald Color", "Emerald outline color", ColorUtils.rgba(60, 255, 120, 255));

    public final BooleanSetting lapis = new BooleanSetting("Lapis", "Mark lapis ores", true);
    public final BooleanSetting lapisOutline = new BooleanSetting("Lapis Outline", "Draw lapis outlines", true);
    public final ColorSetting lapisColor = new ColorSetting("Lapis Color", "Lapis outline color", ColorUtils.rgba(60, 100, 230, 255));

    public final BooleanSetting redstone = new BooleanSetting("Redstone", "Mark redstone ores", true);
    public final BooleanSetting redstoneOutline = new BooleanSetting("Redstone Outline", "Draw redstone outlines", true);
    public final ColorSetting redstoneColor = new ColorSetting("Redstone Color", "Redstone outline color", ColorUtils.rgba(255, 60, 60, 255));

    public final BooleanSetting coal = new BooleanSetting("Coal", "Mark coal ores", true);
    public final BooleanSetting coalOutline = new BooleanSetting("Coal Outline", "Draw coal outlines", true);
    public final ColorSetting coalColor = new ColorSetting("Coal Color", "Coal outline color", ColorUtils.rgba(120, 120, 120, 255));

    public final BooleanSetting copper = new BooleanSetting("Copper", "Mark copper ores", false);
    public final BooleanSetting copperOutline = new BooleanSetting("Copper Outline", "Draw copper outlines", true);
    public final ColorSetting copperColor = new ColorSetting("Copper Color", "Copper outline color", ColorUtils.rgba(255, 140, 90, 255));

    public final BooleanSetting quartz = new BooleanSetting("Quartz", "Mark nether quartz ores", true);
    public final BooleanSetting quartzOutline = new BooleanSetting("Quartz Outline", "Draw quartz outlines", true);
    public final ColorSetting quartzColor = new ColorSetting("Quartz Color", "Quartz outline color", ColorUtils.rgba(240, 240, 240, 255));

    // ---- Global render settings -----------------------------------------
    public final BooleanSetting outline = new BooleanSetting("Outline", "Master switch for block outlines", true);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outlines", 1.0, 1.0, 4.0, 1.0);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Draw a translucent fill", false);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Opacity of the fill", 70.0, 0.0, 255.0, 5.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show the distance next to each ore", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Draw a line from the crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius", 32.0, 8.0, 96.0, 4.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to scan - raise this to find ores deep below you", 32.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the scan restarts", 8.0, 4.0, 32.0, 2.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max ores marked", 300.0, 10.0, 1000.0, 25.0);

    // ---- Scan state ------------------------------------------------------
    private final Map<BlockPos, Integer> found = new HashMap<>();
    private final Map<String, Integer> pathToOre = new HashMap<>();
    private final BlockScanner scanner = new BlockScanner();
    private int scanOriginX = Integer.MIN_VALUE, scanOriginY, scanOriginZ;

    private static final int SCAN_BUDGET = 3000;
    private static final long RENDER_BUDGET_NS = 10_000_000L; // 10 ms

    public OreESP() {
        super("OreESP", "Marks ores with per-ore outlines, toggles and colours", Category.RENDER);

        addSetting(diamond); addSetting(diamondOutline); addSetting(diamondColor);
        addSetting(ancientDebris); addSetting(ancientDebrisOutline); addSetting(ancientDebrisColor);
        addSetting(gold); addSetting(goldOutline); addSetting(goldColor);
        addSetting(iron); addSetting(ironOutline); addSetting(ironColor);
        addSetting(emerald); addSetting(emeraldOutline); addSetting(emeraldColor);
        addSetting(lapis); addSetting(lapisOutline); addSetting(lapisColor);
        addSetting(redstone); addSetting(redstoneOutline); addSetting(redstoneColor);
        addSetting(coal); addSetting(coalOutline); addSetting(coalColor);
        addSetting(copper); addSetting(copperOutline); addSetting(copperColor);
        addSetting(quartz); addSetting(quartzOutline); addSetting(quartzColor);

        addSetting(outline);
        addSetting(outlineWidth);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(showDistance);
        addSetting(tracer);
        addSetting(range);
        addSetting(maxShown);

        for (int i = 0; i < ORE_IDS.length; i++) {
            for (String id : ORE_IDS[i]) {
                pathToOre.put(id, i);
            }
        }
    }

    private BooleanSetting enabledFor(int ore) {
        return switch (ore) {
            case N_DIAMOND -> diamond;
            case N_DEBRIS -> ancientDebris;
            case N_GOLD -> gold;
            case N_IRON -> iron;
            case N_EMERALD -> emerald;
            case N_LAPIS -> lapis;
            case N_REDSTONE -> redstone;
            case N_COAL -> coal;
            case N_COPPER -> copper;
            default -> quartz;
        };
    }

    private BooleanSetting outlineFor(int ore) {
        return switch (ore) {
            case N_DIAMOND -> diamondOutline;
            case N_DEBRIS -> ancientDebrisOutline;
            case N_GOLD -> goldOutline;
            case N_IRON -> ironOutline;
            case N_EMERALD -> emeraldOutline;
            case N_LAPIS -> lapisOutline;
            case N_REDSTONE -> redstoneOutline;
            case N_COAL -> coalOutline;
            case N_COPPER -> copperOutline;
            default -> quartzOutline;
        };
    }

    private ColorSetting colorFor(int ore) {
        return switch (ore) {
            case N_DIAMOND -> diamondColor;
            case N_DEBRIS -> ancientDebrisColor;
            case N_GOLD -> goldColor;
            case N_IRON -> ironColor;
            case N_EMERALD -> emeraldColor;
            case N_LAPIS -> lapisColor;
            case N_REDSTONE -> redstoneColor;
            case N_COAL -> coalColor;
            case N_COPPER -> copperColor;
            default -> quartzColor;
        };
    }

    @Override
    public void onDisable() {
        found.clear();
        scanner.reset();
        scanOriginX = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();

        // 1. Purge disabled or destroyed ores from found set
        found.entrySet().removeIf(e -> {
            if (!enabledFor(e.getValue()).isEnabled()) return true;
            if (mc.world.isChunkLoaded(e.getKey())) {
                BlockState state = mc.world.getBlockState(e.getKey());
                Integer ore = pathToOre.get(Registries.BLOCK.getId(state.getBlock()).getPath());
                return ore == null || !ore.equals(e.getValue());
            }
            return false;
        });

        if (scanOriginX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - scanOriginX) > recentreDistance.getValue()
                || Math.abs(origin.getY() - scanOriginY) > recentreDistance.getValue()
                || Math.abs(origin.getZ() - scanOriginZ) > recentreDistance.getValue()) {
            scanOriginX = origin.getX();
            scanOriginY = origin.getY();
            scanOriginZ = origin.getZ();
            scanner.reset();
            prune(origin, r);
        }

        int limit = maxShown.getValue().intValue();
        BlockPos.Mutable scratch = new BlockPos.Mutable();

        try {
            scanner.scanCylinder(mc.world, scanOriginX, scanOriginY, scanOriginZ, r,
                    verticalRange.getValue().intValue(), scanSpeed.getValue().intValue(),
                    scratch, state -> {
                        // FAST FILTER: only test if it is an ore AND that ore is currently enabled!
                        Integer ore = pathToOre.get(Registries.BLOCK.getId(state.getBlock()).getPath());
                        return ore != null && enabledFor(ore).isEnabled();
                    },
                    (pos, state) -> {
                        if (found.size() >= limit) return;
                        Integer ore = pathToOre.get(Registries.BLOCK.getId(state.getBlock()).getPath());
                        if (ore != null && enabledFor(ore).isEnabled()) {
                            found.put(pos.toImmutable(), ore);
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private void prune(BlockPos origin, int r) {
        int rr = (r + 4) * (r + 4);
        found.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dy = pos.getY() - origin.getY();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dy * dy + dz * dz > rr;
        });
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;

        int width = outlineWidth.getValue().intValue();
        int alpha = fillOpacity.getValue().intValue();
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;
        int checked = 0;
        double maxDistSq = Math.pow(range.getValue() + 8.0, 2);

        try {
            for (Map.Entry<BlockPos, Integer> e : found.entrySet()) {
                if ((++checked & 31) == 0 && System.nanoTime() > deadline) break;

                int ore = e.getValue();
                if (!enabledFor(ore).isEnabled()) continue;

                BlockPos p = e.getKey();

                // Fast distance check to avoid running projections on far blocks
                double dx = p.getX() + 0.5 - mc.player.getX();
                double dy = p.getY() + 0.5 - mc.player.getY();
                double dz = p.getZ() + 0.5 - mc.player.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > maxDistSq) continue;

                int col = colorFor(ore).getValue();

                int[] bounds = RenderUtils.projectBlockBounds(p, tickDelta);
                if (bounds == null) continue;

                if (fill.isEnabled()) {
                    RenderUtils.drawBlockFill(context, p, col, tickDelta, alpha);
                }
                if (outline.isEnabled() && outlineFor(ore).isEnabled()) {
                    RenderUtils.drawBlockOutline(context, p, col, tickDelta, width);
                }

                int[] sc = ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
                if (sc == null) continue;

                if (tracer.isEnabled()) {
                    RenderUtils.drawLine(context, w / 2, h / 2, sc[0], sc[1], col, 1.0f);
                }
                if (showDistance.isEnabled() && mc.textRenderer != null) {
                    String d = (int) Math.sqrt(distSq) + "m";
                    RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + 5, sc[1] - 4, 0xFFAAAAAA, true);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
