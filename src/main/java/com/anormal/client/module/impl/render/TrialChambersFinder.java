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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Marks every block type that makes up a trial chamber with a 3D wireframe
 * outline that renders through walls (and underground).
 *
 * <p><b>Freeze/crash fix.</b> The old version scanned a full sphere slice every
 * single tick - up to 257x257 = 66 000 world lookups per tick at max range -
 * and then projected up to 3000 blocks x 8 corners per frame. That is millions
 * of operations per second and hard-locked the client.</p>
 *
 * <p>The scan is now <b>budgeted</b>: a persistent cursor walks a precomputed
 * offset list and processes at most {@link #SCAN_BUDGET} blocks per tick, so
 * the cost per tick is constant no matter how large the range is. Rendering
 * has a hard per-frame time budget as well, and off-screen blocks are culled
 * before any geometry is emitted.</p>
 */
public class TrialChambersFinder extends Module {
    /** Max world lookups per tick. Constant cost, no spikes. */
    private static final int SCAN_BUDGET = 3000;
    /** Max milliseconds spent drawing markers per frame. */
    private static final long RENDER_BUDGET_NS = 6_000_000L; // 6 ms

    // --- Scan ---
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up/down to scan", 32.0, 8.0, 96.0, 8.0);

    // --- Render ---
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw block outlines through walls", true);
    public final BooleanSetting centerDot = new BooleanSetting("Center Dot", "Also draw a dot in the middle of each block", false);
    public final BooleanSetting distance = new BooleanSetting("Show Distance", "Show the distance next to spawners/vaults", true);
    public final NumberSetting maxRendered = new NumberSetting("Max Rendered", "Max blocks outlined at once", 250.0, 25.0, 1500.0, 25.0);

    // --- Per-type toggles ---
    public final BooleanSetting showSpawners = new BooleanSetting("Trial Spawners", "Mark trial spawners", true);
    public final BooleanSetting showVaults = new BooleanSetting("Vaults", "Mark vaults (incl. ominous)", true);
    public final BooleanSetting showCopper = new BooleanSetting("Copper", "Mark copper / waxed copper blocks", true);
    public final BooleanSetting showTuff = new BooleanSetting("Tuff", "Mark tuff and tuff bricks", true);
    public final BooleanSetting showDeepslate = new BooleanSetting("Deepslate", "Mark deepslate variants", true);
    public final BooleanSetting showStone = new BooleanSetting("Stone Bricks", "Mark stone brick variants", true);
    public final BooleanSetting showWood = new BooleanSetting("Dark Oak", "Mark dark oak blocks", true);
    public final BooleanSetting showMisc = new BooleanSetting("Misc", "Mark pots, crafters, chests, bars, glass, etc.", true);

    // --- Colours ---
    public final ColorSetting spawnerColor = new ColorSetting("Spawner Color", "Trial spawner color", ColorUtils.rgba(255, 120, 0, 255));
    public final ColorSetting vaultColor = new ColorSetting("Vault Color", "Vault color", ColorUtils.rgba(0, 230, 255, 255));
    public final ColorSetting copperColor = new ColorSetting("Copper Color", "Copper color", ColorUtils.rgba(255, 140, 90, 255));
    public final ColorSetting tuffColor = new ColorSetting("Tuff Color", "Tuff color", ColorUtils.rgba(160, 160, 140, 255));
    public final ColorSetting deepslateColor = new ColorSetting("Deepslate Color", "Deepslate color", ColorUtils.rgba(130, 130, 150, 255));
    public final ColorSetting otherColor = new ColorSetting("Other Color", "Stone / wood / misc color", ColorUtils.rgba(120, 220, 120, 255));

    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Announce a new trial chamber once", true);

    // Categories
    private static final int CAT_OTHER = 0;
    private static final int CAT_SPAWNER = 1;
    private static final int CAT_VAULT = 2;
    private static final int CAT_COPPER = 3;
    private static final int CAT_TUFF = 4;
    private static final int CAT_DEEPSLATE = 5;
    private static final int CAT_STONE = 6;
    private static final int CAT_WOOD = 7;
    private static final int CAT_MISC = 8;

    private final Map<Block, Integer> blockCategories = new HashMap<>();
    private final Map<BlockPos, Integer> found = new HashMap<>();
    private final Set<String> logged = new HashSet<>();

    // ---- Budgeted scan state ----
    private int[] offsetsX = new int[0];
    private int[] offsetsZ = new int[0];
    private int cachedRadius = -1;
    private int cursor = 0;
    private int yLayer = 0;
    private int originX = Integer.MIN_VALUE, originZ = Integer.MIN_VALUE;
    private int originY = Integer.MIN_VALUE;

    public TrialChambersFinder() {
        super("TrialChambersFinder", "Highlights every trial chamber block through walls", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(outline);
        addSetting(centerDot);
        addSetting(distance);
        addSetting(maxRendered);
        addSetting(showSpawners);
        addSetting(showVaults);
        addSetting(showCopper);
        addSetting(showTuff);
        addSetting(showDeepslate);
        addSetting(showStone);
        addSetting(showWood);
        addSetting(showMisc);
        addSetting(spawnerColor);
        addSetting(vaultColor);
        addSetting(copperColor);
        addSetting(tuffColor);
        addSetting(deepslateColor);
        addSetting(otherColor);
        addSetting(chatLog);
        buildBlockTable();
    }

    // ------------------------------------------------------------------
    //  Block table
    // ------------------------------------------------------------------

    private void buildBlockTable() {
        register(CAT_SPAWNER, "trial_spawner");
        register(CAT_VAULT, "vault", "ominous_vault");

        // Copper family (regular + waxed), generated from the base ids.
        String[] copper = {
                "copper_block", "exposed_copper", "weathered_copper", "oxidized_copper",
                "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper",
                "cut_copper_stairs", "exposed_cut_copper_stairs", "weathered_cut_copper_stairs", "oxidized_cut_copper_stairs",
                "cut_copper_slab", "exposed_cut_copper_slab", "weathered_cut_copper_slab", "oxidized_cut_copper_slab",
                "chiseled_copper", "exposed_chiseled_copper", "weathered_chiseled_copper", "oxidized_chiseled_copper",
                "copper_grate", "exposed_copper_grate", "weathered_copper_grate", "oxidized_copper_grate",
                "copper_bulb", "exposed_copper_bulb", "weathered_copper_bulb", "oxidized_copper_bulb",
                "copper_door", "exposed_copper_door", "weathered_copper_door", "oxidized_copper_door",
                "copper_trapdoor", "exposed_copper_trapdoor", "weathered_copper_trapdoor", "oxidized_copper_trapdoor"
        };
        for (String id : copper) {
            register(CAT_COPPER, id);
            register(CAT_COPPER, "waxed_" + id);
        }

        register(CAT_TUFF, "tuff", "polished_tuff", "chiseled_tuff",
                "tuff_bricks", "chiseled_tuff_bricks",
                "tuff_brick_stairs", "tuff_brick_slab", "tuff_brick_wall",
                "polished_tuff_stairs", "polished_tuff_slab", "polished_tuff_wall");

        register(CAT_DEEPSLATE, "deepslate", "cobbled_deepslate", "polished_deepslate", "chiseled_deepslate",
                "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "cracked_deepslate_tiles",
                "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall",
                "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall",
                "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall",
                "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall");

        register(CAT_STONE, "stone_bricks", "chiseled_stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks",
                "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall");

        register(CAT_WOOD, "dark_oak_planks", "dark_oak_log", "dark_oak_wood",
                "stripped_dark_oak_log", "stripped_dark_oak_wood",
                "dark_oak_stairs", "dark_oak_slab", "dark_oak_fence", "dark_oak_fence_gate",
                "dark_oak_door", "dark_oak_trapdoor", "dark_oak_sign", "dark_oak_wall_sign",
                "dark_oak_button", "dark_oak_pressure_plate");

        register(CAT_MISC, "decorated_pot", "crafter", "barrel", "chest", "trapped_chest",
                "iron_bars", "chain", "lightning_rod", "glass", "glass_pane",
                "waxed_copper_block", "waxed_exposed_copper", "waxed_weathered_copper", "waxed_oxidized_copper",
                "heavy_core", "lodestone");
    }

    private void register(int category, String... ids) {
        for (String id : ids) {
            try {
                Block b = Registries.BLOCK.get(Identifier.of("minecraft:" + id));
                if (b != null) blockCategories.put(b, category);
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    //  Scanning (fixed work per tick)
    // ------------------------------------------------------------------

    @Override
    public void onDisable() {
        found.clear();
        cursor = 0;
        yLayer = 0;
        originX = Integer.MIN_VALUE;
    }

    /** Precomputes the (x, z) offsets inside the scan circle. */
    private void ensureOffsets(int r) {
        if (r == cachedRadius) return;
        cachedRadius = r;
        int rr = r * r;
        List<int[]> list = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= rr) list.add(new int[]{x, z});
            }
        }
        offsetsX = new int[list.size()];
        offsetsZ = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            offsetsX[i] = list.get(i)[0];
            offsetsZ[i] = list.get(i)[1];
        }
        cursor = 0;
        yLayer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (blockCategories.isEmpty()) buildBlockTable();

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();
        ensureOffsets(r);
        if (offsetsX.length == 0) return;

        // Re-centre when the player moved far enough.
        boolean recentre = originX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - originX) > 16
                || Math.abs(origin.getZ() - originZ) > 16;
        if (recentre) {
            originX = origin.getX();
            originZ = origin.getZ();
            originY = origin.getY();
            prune(origin, r, vr);
        }

        BlockPos.Mutable p = new BlockPos.Mutable();
        int budget = SCAN_BUDGET;

        try {
            while (budget > 0) {
                if (cursor >= offsetsX.length) {
                    cursor = 0;
                    if (++yLayer > 2 * vr) yLayer = 0;
                }
                int y = originY + yLayer - vr;
                int n = Math.min(budget, offsetsX.length - cursor);
                for (int i = 0; i < n; i++, cursor++) {
                    p.set(originX + offsetsX[cursor], y, originZ + offsetsZ[cursor]);
                    if (!mc.world.isChunkLoaded(p)) continue;
                    BlockState state = mc.world.getBlockState(p);
                    Integer cat = blockCategories.get(state.getBlock());
                    if (cat != null) {
                        found.put(p.toImmutable(), cat);
                    } else {
                        found.remove(p);
                    }
                }
                budget -= n;
            }
        } catch (Throwable ignored) {
        }
    }

    private void prune(BlockPos origin, int r, int vr) {
        int rr = r * r;
        found.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            int dy = pos.getY() - origin.getY();
            return dx * dx + dz * dz > rr || Math.abs(dy) > vr + 1;
        });
    }

    private boolean isCategoryEnabled(int cat) {
        return switch (cat) {
            case CAT_SPAWNER -> showSpawners.isEnabled();
            case CAT_VAULT -> showVaults.isEnabled();
            case CAT_COPPER -> showCopper.isEnabled();
            case CAT_TUFF -> showTuff.isEnabled();
            case CAT_DEEPSLATE -> showDeepslate.isEnabled();
            case CAT_STONE -> showStone.isEnabled();
            case CAT_WOOD -> showWood.isEnabled();
            case CAT_MISC -> showMisc.isEnabled();
            default -> true;
        };
    }

    private int colorFor(int cat) {
        return switch (cat) {
            case CAT_SPAWNER -> spawnerColor.getValue();
            case CAT_VAULT -> vaultColor.getValue();
            case CAT_COPPER -> copperColor.getValue();
            case CAT_TUFF -> tuffColor.getValue();
            case CAT_DEEPSLATE -> deepslateColor.getValue();
            default -> otherColor.getValue();
        };
    }

    // ------------------------------------------------------------------
    //  Rendering (hard per-frame time budget)
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;
        int limit = maxRendered.getValue().intValue();
        int drawn = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        BlockPos spawnerFirst = null;

        try {
            for (Map.Entry<BlockPos, Integer> e : found.entrySet()) {
                if (drawn >= limit) break;
                if ((drawn & 15) == 0 && System.nanoTime() > deadline) break;

                int cat = e.getValue();
                if (!isCategoryEnabled(cat)) continue;
                BlockPos pos = e.getKey();

                // Cheap off-screen cull before emitting any geometry.
                int[] bounds = RenderUtils.projectBlockBounds(pos, tickDelta);
                if (bounds == null) continue;

                drawn++;
                if (cat == CAT_SPAWNER && spawnerFirst == null) spawnerFirst = pos;
                drawBlockBox(context, pos, colorFor(cat), tickDelta, bounds);
            }
        } catch (Throwable ignored) {
        }

        if (chatLog.isEnabled() && spawnerFirst != null && mc.inGameHud != null) {
            String key = (spawnerFirst.getX() >> 4) + "," + (spawnerFirst.getZ() >> 4);
            if (logged.add(key)) {
                try {
                    mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                            String.format("§6[Trial Chamber] §ffound near §f%d %d %d",
                                    spawnerFirst.getX(), spawnerFirst.getY(), spawnerFirst.getZ())));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void drawBlockBox(DrawContext context, BlockPos pos, int col, float tickDelta, int[] bounds) {
        if (outline.isEnabled()) {
            RenderUtils.drawBlockOutline(context, pos, col, tickDelta, 1);
        }

        double x = pos.getX(), y = pos.getY(), z = pos.getZ();

        if (centerDot.isEnabled()) {
            int[] mid = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(x + 0.5, y + 0.5, z + 0.5), tickDelta);
            if (mid != null) context.fill(mid[0] - 2, mid[1] - 2, mid[0] + 2, mid[1] + 2, col);
        }

        if (distance.isEnabled() && mc.textRenderer != null) {
            int[] mid = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(x + 0.5, y + 1.2, z + 0.5), tickDelta);
            if (mid != null) {
                String d = (int) Math.sqrt(pos.getSquaredDistance(mc.player.getBlockPos())) + "m";
                RenderUtils.drawText(context, mc.textRenderer, d, mid[0] + 3, mid[1], col, true);
            }
        }
    }
}
