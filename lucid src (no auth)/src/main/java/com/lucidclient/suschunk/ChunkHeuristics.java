package com.lucidclient.suschunk;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

/**
 * ChunkHeuristics — section-scanning suspicion heuristics for SusChunkESP.
 *
 * ── What this does ─────────────────────────────────────────────────
 * The packet-cache layer ({@link SusChunkCache}) catches block-entity
 * leaks + event-driven leaks (sounds, block updates, pistons, particles,
 * entity spawns). This class adds the classic base-hunter signals that
 * require reading the actual block-state palette of a chunk's sections —
 * the signals that Wurst's BaseFinder, Meteor addons, and Krypton's Sus
 * Chunk Finder all use:
 *
 *   1. Unnatural block palette — blocks that don't generate naturally
 *      (obsidian, planks, glass, crafting tables, bookshelves, enchanting
 *      tables, furnaces, hoppers, beacons, etc.). A cluster of these
 *      underground = player base.
 *   2. Air-pocket anomalies — {@code air} pockets where terrain generation
 *      wouldn't put them (rectangular rooms, flat ceilings/floors,
 *      straight tunnels). Anti-xray obfuscates ore but struggles to fully
 *      hide excavated rooms.
 *   3. Light sources — torches, lanterns, glowstone deep in stone. Players
 *      light their bases; natural generation rarely places light sources
 *      underground.
 *   4. Heightmap inconsistencies — the chunk's stored heightmap (highest
 *      non-air block per column) not matching the actual block layout.
 *      This leaks surface edits even when the surface looks untouched, and
 *      can reveal underground ceilings when the heightmap says "y=X" but a
 *      non-air block exists above X.
 *
 * ── Performance ────────────────────────────────────────────────────
 * Iterating every block in every section of every loaded chunk (4096
 * blocks × ~24 sections × ~200 chunks) every scan tick would be O(millions)
 * and tank FPS. Instead, this class SAMPLES: it walks each section's
 * blocks on a sparse stride (every 2nd block in each axis = 512 samples
 * per section), and only flags chunks where the sample clears a
 * threshold. The scan is also only called on the staggered 10-tick cadence
 * from SusChunkESP's tick, and only for chunks not already flagged by the
 * cheaper block-entity / event heuristics.
 *
 * ── Anti-xray interaction ──────────────────────────────────────────
 * Anti-xray (Paper Engine Mode 2) rewrites stone-area block states to fake
 * stone, so the "unnatural block" signal is weaker in obfuscated zones.
 * But the air-pocket + heightmap signals still leak (anti-xray doesn't
 * bother obfuscating air correctly, and heightmaps are computed
 * server-side from the real data). This class therefore weights air +
 * heightmap signals higher than unnatural-block signals when anti-xray is
 * likely present (heuristic: chunks below Y=0).
 *
 * ── Cave false-positive suppression ───────────────────────────────
 * Air alone is NOT a player signal — 1.18+ generates huge cave systems
 * full of air below Y=0, so counting every air block would flag every
 * natural cave as a "base". The air-pocket heuristic therefore only
 * counts air that is CORROBORATED: an air sample counts only if there's
 * an unnatural block or a non-natural light source within a small
 * neighborhood (±2 blocks) of it. A bare natural cave (air surrounded by
 * stone, no player-built blocks, no torches) contributes zero air-pocket
 * points — exactly the behavior we want. This makes air a *modifier* that
 * amplifies the unnatural-block signal (a player-carved room lined with
 * chests/torches) rather than a standalone trigger (a cave).
 */
public final class ChunkHeuristics {

    private ChunkHeuristics() {} // static helpers only

    /** Result of a single chunk's heuristic scan. */
    public static final class ScanResult {
        /** Weighted score from all four heuristics combined. */
        public float score;
        /** Unnatural-block count (sampled — multiply by sample ratio for true count). */
        public int unnaturalBlocks;
        /** Corroborated air-pocket count (sampled). Only air samples within
         *  ±2 blocks of an unnatural block or non-natural light source are
         *  counted — bare natural caves contribute zero, killing the most
         *  common false positive. */
        public int airPockets;
        /** Light-source count (sampled). */
        public int lightSources;
        /** Heightmap mismatch count (per-column, full 16×16). */
        public int heightmapMismatches;
        /** True if any block entity below Y=0 was found (the deepslate-base signature). */
        public boolean hasBelowY0BlockEntity;
        /** True if a mob spawner block entity is present. */
        public boolean hasSpawner;
    }

    /**
     * The "unnatural block" set — blocks that don't generate naturally in
     * vanilla terrain and are strong player-activity signals. This is the
     * Wurst BaseFinder / Krypton approach: a curated whitelist of
     * high-value telltale blocks rather than a fragile "natural blocks"
     * tag list (which would miss mod-added stone variants).
     *
     * Anything in this set → +1 unnatural point. Anything NOT in the set
     * and NOT in {@link #NATURAL_BLOCKS} → +0.5 (weakly suspicious — could
     * be a mod block or a less-common generated block).
     */
    private static final Set<Block> UNNATURAL_BLOCKS = new HashSet<>();
    static {
        // Storage + workstations.
        UNNATURAL_BLOCKS.add(Blocks.CHEST);
        UNNATURAL_BLOCKS.add(Blocks.TRAPPED_CHEST);
        UNNATURAL_BLOCKS.add(Blocks.ENDER_CHEST);
        UNNATURAL_BLOCKS.add(Blocks.SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.WHITE_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.ORANGE_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.MAGENTA_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_BLUE_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.YELLOW_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.LIME_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.PINK_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.GRAY_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_GRAY_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.CYAN_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.PURPLE_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.BLUE_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.BROWN_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.GREEN_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.RED_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.BLACK_SHULKER_BOX);
        UNNATURAL_BLOCKS.add(Blocks.BARREL);
        UNNATURAL_BLOCKS.add(Blocks.HOPPER);
        UNNATURAL_BLOCKS.add(Blocks.DISPENSER);
        UNNATURAL_BLOCKS.add(Blocks.DROPPER);
        UNNATURAL_BLOCKS.add(Blocks.FURNACE);
        UNNATURAL_BLOCKS.add(Blocks.BLAST_FURNACE);
        UNNATURAL_BLOCKS.add(Blocks.SMOKER);
        UNNATURAL_BLOCKS.add(Blocks.BREWING_STAND);
        UNNATURAL_BLOCKS.add(Blocks.CRAFTING_TABLE);
        UNNATURAL_BLOCKS.add(Blocks.SMITHING_TABLE);
        UNNATURAL_BLOCKS.add(Blocks.FLETCHING_TABLE);
        UNNATURAL_BLOCKS.add(Blocks.LOOM);
        UNNATURAL_BLOCKS.add(Blocks.CARTOGRAPHY_TABLE);
        UNNATURAL_BLOCKS.add(Blocks.GRINDSTONE);
        UNNATURAL_BLOCKS.add(Blocks.STONECUTTER);
        UNNATURAL_BLOCKS.add(Blocks.ANVIL);
        UNNATURAL_BLOCKS.add(Blocks.CHIPPED_ANVIL);
        UNNATURAL_BLOCKS.add(Blocks.DAMAGED_ANVIL);
        UNNATURAL_BLOCKS.add(Blocks.ENCHANTING_TABLE);
        UNNATURAL_BLOCKS.add(Blocks.BOOKSHELF);
        UNNATURAL_BLOCKS.add(Blocks.LECTERN);
        UNNATURAL_BLOCKS.add(Blocks.NOTE_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.JUKEBOX);
        UNNATURAL_BLOCKS.add(Blocks.BEACON);
        UNNATURAL_BLOCKS.add(Blocks.CONDUIT);
        UNNATURAL_BLOCKS.add(Blocks.BELL);
        UNNATURAL_BLOCKS.add(Blocks.LODESTONE);
        UNNATURAL_BLOCKS.add(Blocks.RESPAWN_ANCHOR);
        // Building materials players use but generation rarely places.
        UNNATURAL_BLOCKS.add(Blocks.OBSIDIAN);
        UNNATURAL_BLOCKS.add(Blocks.CRYING_OBSIDIAN);
        UNNATURAL_BLOCKS.add(Blocks.NETHERITE_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.DIAMOND_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.EMERALD_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.GOLD_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.IRON_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.REDSTONE_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.LAPIS_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.COAL_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.HAY_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.SLIME_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.HONEY_BLOCK);
        // Glass (players build with it; generation only places it in
        // desert villages / end cities).
        UNNATURAL_BLOCKS.add(Blocks.GLASS);
        UNNATURAL_BLOCKS.add(Blocks.TINTED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.WHITE_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.ORANGE_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.MAGENTA_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_BLUE_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.YELLOW_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.LIME_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.PINK_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.GRAY_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_GRAY_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.CYAN_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.PURPLE_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.BLUE_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.BROWN_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.GREEN_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.RED_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.BLACK_STAINED_GLASS);
        UNNATURAL_BLOCKS.add(Blocks.GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.WHITE_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.ORANGE_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.MAGENTA_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.YELLOW_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.LIME_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.PINK_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.GRAY_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.CYAN_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.PURPLE_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.BLUE_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.BROWN_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.GREEN_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.RED_STAINED_GLASS_PANE);
        UNNATURAL_BLOCKS.add(Blocks.BLACK_STAINED_GLASS_PANE);
        // Planks + wood building blocks.
        UNNATURAL_BLOCKS.add(Blocks.OAK_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.SPRUCE_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.BIRCH_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.JUNGLE_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.ACACIA_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.DARK_OAK_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.MANGROVE_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.CHERRY_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.BAMBOO_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.CRIMSON_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.WARPED_PLANKS);
        UNNATURAL_BLOCKS.add(Blocks.BRICKS);
        UNNATURAL_BLOCKS.add(Blocks.STONE_BRICKS);
        UNNATURAL_BLOCKS.add(Blocks.MOSSY_STONE_BRICKS);
        UNNATURAL_BLOCKS.add(Blocks.CRACKED_STONE_BRICKS);
        UNNATURAL_BLOCKS.add(Blocks.CHISELED_STONE_BRICKS);
        UNNATURAL_BLOCKS.add(Blocks.DEEPSLATE_BRICKS);
        UNNATURAL_BLOCKS.add(Blocks.POLISHED_DEEPSLATE);
        UNNATURAL_BLOCKS.add(Blocks.SMOOTH_STONE);
        UNNATURAL_BLOCKS.add(Blocks.STONE_BRICK_STAIRS);
        UNNATURAL_BLOCKS.add(Blocks.OAK_STAIRS);
        UNNATURAL_BLOCKS.add(Blocks.SPRUCE_STAIRS);
        UNNATURAL_BLOCKS.add(Blocks.BRICK_STAIRS);
        UNNATURAL_BLOCKS.add(Blocks.NETHER_BRICKS);
        UNNATURAL_BLOCKS.add(Blocks.RED_NETHER_BRICKS);
        // NOTE: Sandstone variants (SANDSTONE, RED_SANDSTONE, CUT_SANDSTONE,
        // SMOOTH_SANDSTONE) were removed from UNNATURAL_BLOCKS and moved to
        // NATURAL_BLOCKS — they generate naturally in desert biomes (desert
        // wells, desert temples, desert villages), so flagging them caused
        // widespread desert-biome false positives. This weakens detection of
        // player-built sandstone structures, but eliminates the desert FP.
        UNNATURAL_BLOCKS.add(Blocks.QUARTZ_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.QUARTZ_BRICKS);
        UNNATURAL_BLOCKS.add(Blocks.QUARTZ_PILLAR);
        UNNATURAL_BLOCKS.add(Blocks.CHISELED_QUARTZ_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.SMOOTH_QUARTZ);
        UNNATURAL_BLOCKS.add(Blocks.PURPUR_BLOCK);
        UNNATURAL_BLOCKS.add(Blocks.PURPUR_PILLAR);
        UNNATURAL_BLOCKS.add(Blocks.END_STONE_BRICKS);
        // NOTE: Smooth basalt, basalt, and polished basalt were removed from
        // UNNATURAL_BLOCKS and moved to NATURAL_BLOCKS — smooth basalt
        // generates naturally as the OUTER SHELL of every amethyst geode
        // (1.18+), and basalt + polished basalt generate naturally in the
        // Nether's basalt deltas + soul sand valley biomes. Flagging them
        // as "unnatural" produced widespread amethyst-geode + basalt-deltas
        // false positives — entire geode clusters were flagged as bases.
        // Polished basalt (only player-crafted via stonecutter) is RARELY
        // used for building and isn't a strong enough player signal to
        // justify the geode/deltas FP cost, so it's natural too.
        UNNATURAL_BLOCKS.add(Blocks.WHITE_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.ORANGE_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.MAGENTA_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_BLUE_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.YELLOW_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.LIME_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.PINK_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.GRAY_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_GRAY_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.CYAN_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.PURPLE_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.BLUE_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.BROWN_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.GREEN_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.RED_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.BLACK_CONCRETE);
        UNNATURAL_BLOCKS.add(Blocks.WHITE_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.ORANGE_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.MAGENTA_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_BLUE_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.YELLOW_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.LIME_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.PINK_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.GRAY_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_GRAY_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.CYAN_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.PURPLE_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.BLUE_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.BROWN_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.GREEN_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.RED_WOOL);
        UNNATURAL_BLOCKS.add(Blocks.BLACK_WOOL);
        // Bed (player bed = base).
        UNNATURAL_BLOCKS.add(Blocks.WHITE_BED);
        UNNATURAL_BLOCKS.add(Blocks.ORANGE_BED);
        UNNATURAL_BLOCKS.add(Blocks.MAGENTA_BED);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_BLUE_BED);
        UNNATURAL_BLOCKS.add(Blocks.YELLOW_BED);
        UNNATURAL_BLOCKS.add(Blocks.LIME_BED);
        UNNATURAL_BLOCKS.add(Blocks.PINK_BED);
        UNNATURAL_BLOCKS.add(Blocks.GRAY_BED);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_GRAY_BED);
        UNNATURAL_BLOCKS.add(Blocks.CYAN_BED);
        UNNATURAL_BLOCKS.add(Blocks.PURPLE_BED);
        UNNATURAL_BLOCKS.add(Blocks.BLUE_BED);
        UNNATURAL_BLOCKS.add(Blocks.BROWN_BED);
        UNNATURAL_BLOCKS.add(Blocks.GREEN_BED);
        UNNATURAL_BLOCKS.add(Blocks.RED_BED);
        UNNATURAL_BLOCKS.add(Blocks.BLACK_BED);
        // Spawner (already covered by the spawner-flag heuristic, but the
        // palette scan also catches it for the section-based score).
        UNNATURAL_BLOCKS.add(Blocks.SPAWNER);
        // Redstone (redstone wire, repeater, comparator, piston, observer,
        // daylight detector, lever, button, pressure plate — the
        // "automated machine" telltales).
        UNNATURAL_BLOCKS.add(Blocks.REDSTONE_WIRE);
        UNNATURAL_BLOCKS.add(Blocks.REDSTONE_TORCH);
        UNNATURAL_BLOCKS.add(Blocks.REDSTONE_WALL_TORCH);
        UNNATURAL_BLOCKS.add(Blocks.REPEATER);
        UNNATURAL_BLOCKS.add(Blocks.COMPARATOR);
        UNNATURAL_BLOCKS.add(Blocks.PISTON);
        UNNATURAL_BLOCKS.add(Blocks.STICKY_PISTON);
        UNNATURAL_BLOCKS.add(Blocks.PISTON_HEAD);
        UNNATURAL_BLOCKS.add(Blocks.MOVING_PISTON);
        UNNATURAL_BLOCKS.add(Blocks.OBSERVER);
        UNNATURAL_BLOCKS.add(Blocks.DROPPER);
        UNNATURAL_BLOCKS.add(Blocks.DAYLIGHT_DETECTOR);
        UNNATURAL_BLOCKS.add(Blocks.LEVER);
        UNNATURAL_BLOCKS.add(Blocks.STONE_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.OAK_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.SPRUCE_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.BIRCH_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.JUNGLE_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.ACACIA_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.DARK_OAK_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.MANGROVE_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.CHERRY_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.BAMBOO_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.CRIMSON_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.WARPED_BUTTON);
        UNNATURAL_BLOCKS.add(Blocks.STONE_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.OAK_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.SPRUCE_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.BIRCH_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.JUNGLE_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.ACACIA_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.DARK_OAK_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.MANGROVE_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.CHERRY_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.BAMBOO_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.CRIMSON_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.WARPED_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);
        UNNATURAL_BLOCKS.add(Blocks.TRIPWIRE_HOOK);
        UNNATURAL_BLOCKS.add(Blocks.TRIPWIRE);
        UNNATURAL_BLOCKS.add(Blocks.TNT);
        UNNATURAL_BLOCKS.add(Blocks.TARGET);
        // Doors / trapdoors / fences / gates (player-built structures).
        UNNATURAL_BLOCKS.add(Blocks.OAK_DOOR);
        UNNATURAL_BLOCKS.add(Blocks.IRON_DOOR);
        UNNATURAL_BLOCKS.add(Blocks.OAK_TRAPDOOR);
        UNNATURAL_BLOCKS.add(Blocks.IRON_TRAPDOOR);
        UNNATURAL_BLOCKS.add(Blocks.OAK_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.SPRUCE_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.BIRCH_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.JUNGLE_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.ACACIA_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.DARK_OAK_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.MANGROVE_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.CHERRY_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.BAMBOO_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.CRIMSON_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.WARPED_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.NETHER_BRICK_FENCE);
        UNNATURAL_BLOCKS.add(Blocks.OAK_FENCE_GATE);
        UNNATURAL_BLOCKS.add(Blocks.SPRUCE_FENCE_GATE);
        UNNATURAL_BLOCKS.add(Blocks.IRON_BARS);
    }

    /**
     * The "natural blocks" set — blocks that vanilla generation places
     * routinely and that should NOT trigger suspicion even if not in the
     * {@link #UNNATURAL_BLOCKS} set. This catches the "anything not in
     * unnatural list" false-positives (e.g. granite, diorite, andesite,
     * calcite, dripstone, etc. are generated but not "unnatural").
     */
    private static final Set<Block> NATURAL_BLOCKS = new HashSet<>();
    static {
        // Stone family.
        NATURAL_BLOCKS.add(Blocks.STONE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE);
        NATURAL_BLOCKS.add(Blocks.GRANITE);
        NATURAL_BLOCKS.add(Blocks.POLISHED_GRANITE);
        NATURAL_BLOCKS.add(Blocks.DIORITE);
        NATURAL_BLOCKS.add(Blocks.POLISHED_DIORITE);
        NATURAL_BLOCKS.add(Blocks.ANDESITE);
        NATURAL_BLOCKS.add(Blocks.POLISHED_ANDESITE);
        NATURAL_BLOCKS.add(Blocks.TUFF);
        NATURAL_BLOCKS.add(Blocks.CALCITE);
        NATURAL_BLOCKS.add(Blocks.DRIPSTONE_BLOCK);
        NATURAL_BLOCKS.add(Blocks.COBBLESTONE);
        NATURAL_BLOCKS.add(Blocks.MOSSY_COBBLESTONE);
        NATURAL_BLOCKS.add(Blocks.DIRT);
        NATURAL_BLOCKS.add(Blocks.COARSE_DIRT);
        NATURAL_BLOCKS.add(Blocks.ROOTED_DIRT);
        NATURAL_BLOCKS.add(Blocks.GRASS_BLOCK);
        NATURAL_BLOCKS.add(Blocks.DIRT_PATH);
        NATURAL_BLOCKS.add(Blocks.MYCELIUM);
        NATURAL_BLOCKS.add(Blocks.PODZOL);
        NATURAL_BLOCKS.add(Blocks.SAND);
        NATURAL_BLOCKS.add(Blocks.RED_SAND);
        NATURAL_BLOCKS.add(Blocks.GRAVEL);
        NATURAL_BLOCKS.add(Blocks.CLAY);
        NATURAL_BLOCKS.add(Blocks.SNOW_BLOCK);
        NATURAL_BLOCKS.add(Blocks.PACKED_ICE);
        NATURAL_BLOCKS.add(Blocks.BLUE_ICE);
        NATURAL_BLOCKS.add(Blocks.ICE);
        NATURAL_BLOCKS.add(Blocks.FROSTED_ICE);
        NATURAL_BLOCKS.add(Blocks.WATER);
        NATURAL_BLOCKS.add(Blocks.LAVA);
        NATURAL_BLOCKS.add(Blocks.BEDROCK);
        NATURAL_BLOCKS.add(Blocks.OAK_LOG);
        NATURAL_BLOCKS.add(Blocks.SPRUCE_LOG);
        NATURAL_BLOCKS.add(Blocks.BIRCH_LOG);
        NATURAL_BLOCKS.add(Blocks.JUNGLE_LOG);
        NATURAL_BLOCKS.add(Blocks.ACACIA_LOG);
        NATURAL_BLOCKS.add(Blocks.DARK_OAK_LOG);
        NATURAL_BLOCKS.add(Blocks.MANGROVE_LOG);
        NATURAL_BLOCKS.add(Blocks.CHERRY_LOG);
        NATURAL_BLOCKS.add(Blocks.BAMBOO_BLOCK);
        NATURAL_BLOCKS.add(Blocks.STRIPPED_OAK_LOG);
        NATURAL_BLOCKS.add(Blocks.STRIPPED_SPRUCE_LOG);
        NATURAL_BLOCKS.add(Blocks.STRIPPED_BIRCH_LOG);
        NATURAL_BLOCKS.add(Blocks.STRIPPED_JUNGLE_LOG);
        NATURAL_BLOCKS.add(Blocks.STRIPPED_ACACIA_LOG);
        NATURAL_BLOCKS.add(Blocks.STRIPPED_DARK_OAK_LOG);
        NATURAL_BLOCKS.add(Blocks.CRIMSON_STEM);
        NATURAL_BLOCKS.add(Blocks.WARPED_STEM);
        NATURAL_BLOCKS.add(Blocks.OAK_LEAVES);
        NATURAL_BLOCKS.add(Blocks.SPRUCE_LEAVES);
        NATURAL_BLOCKS.add(Blocks.BIRCH_LEAVES);
        NATURAL_BLOCKS.add(Blocks.JUNGLE_LEAVES);
        NATURAL_BLOCKS.add(Blocks.ACACIA_LEAVES);
        NATURAL_BLOCKS.add(Blocks.DARK_OAK_LEAVES);
        NATURAL_BLOCKS.add(Blocks.MANGROVE_LEAVES);
        NATURAL_BLOCKS.add(Blocks.CHERRY_LEAVES);
        NATURAL_BLOCKS.add(Blocks.AZALEA_LEAVES);
        NATURAL_BLOCKS.add(Blocks.FLOWERING_AZALEA_LEAVES);
        NATURAL_BLOCKS.add(Blocks.NETHER_WART_BLOCK);
        NATURAL_BLOCKS.add(Blocks.WARPED_WART_BLOCK);
        // Ores (natural).
        NATURAL_BLOCKS.add(Blocks.COAL_ORE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE_COAL_ORE);
        NATURAL_BLOCKS.add(Blocks.IRON_ORE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE_IRON_ORE);
        NATURAL_BLOCKS.add(Blocks.COPPER_ORE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE_COPPER_ORE);
        NATURAL_BLOCKS.add(Blocks.GOLD_ORE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE_GOLD_ORE);
        NATURAL_BLOCKS.add(Blocks.REDSTONE_ORE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        NATURAL_BLOCKS.add(Blocks.EMERALD_ORE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE_EMERALD_ORE);
        NATURAL_BLOCKS.add(Blocks.LAPIS_ORE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE_LAPIS_ORE);
        NATURAL_BLOCKS.add(Blocks.DIAMOND_ORE);
        NATURAL_BLOCKS.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        NATURAL_BLOCKS.add(Blocks.NETHER_GOLD_ORE);
        NATURAL_BLOCKS.add(Blocks.NETHER_QUARTZ_ORE);
        NATURAL_BLOCKS.add(Blocks.ANCIENT_DEBRIS);
        // Nether / end natural.
        NATURAL_BLOCKS.add(Blocks.NETHERRACK);
        NATURAL_BLOCKS.add(Blocks.WARPED_NYLIUM);
        NATURAL_BLOCKS.add(Blocks.CRIMSON_NYLIUM);
        NATURAL_BLOCKS.add(Blocks.SOUL_SAND);
        NATURAL_BLOCKS.add(Blocks.SOUL_SOIL);
        NATURAL_BLOCKS.add(Blocks.END_STONE);
        NATURAL_BLOCKS.add(Blocks.GLOWSTONE);
        // Basalt family — generates naturally in the Nether's basalt deltas
        // biome AND as the OUTER SHELL of every amethyst geode (1.18+).
        // Moved here from UNNATURAL_BLOCKS to eliminate the amethyst-geode +
        // basalt-deltas false positives. Smooth basalt = geode shell;
        // basalt = basalt deltas / soul sand valley; polished basalt is
        // player-crafted but rarely-used and not worth the FP cost.
        NATURAL_BLOCKS.add(Blocks.BASALT);
        NATURAL_BLOCKS.add(Blocks.SMOOTH_BASALT);
        NATURAL_BLOCKS.add(Blocks.POLISHED_BASALT);
        // NOTE: OBSIDIAN is intentionally NOT in NATURAL_BLOCKS. It's in
        // UNNATURAL_BLOCKS (checked first at line 623), so a NATURAL entry
        // would be dead code. Obsidian is a strong player signal even though
        // it generates in lava+water encounters and nether portals.
        // Caves / vegetation.
        NATURAL_BLOCKS.add(Blocks.MOSS_BLOCK);
        NATURAL_BLOCKS.add(Blocks.SPORE_BLOSSOM);
        NATURAL_BLOCKS.add(Blocks.BIG_DRIPLEAF);
        NATURAL_BLOCKS.add(Blocks.BIG_DRIPLEAF_STEM);
        NATURAL_BLOCKS.add(Blocks.SMALL_DRIPLEAF);
        NATURAL_BLOCKS.add(Blocks.AZALEA);
        NATURAL_BLOCKS.add(Blocks.FLOWERING_AZALEA);
        NATURAL_BLOCKS.add(Blocks.MOSS_CARPET);
        NATURAL_BLOCKS.add(Blocks.HANGING_ROOTS);
        NATURAL_BLOCKS.add(Blocks.SHORT_GRASS);
        NATURAL_BLOCKS.add(Blocks.TALL_GRASS);
        NATURAL_BLOCKS.add(Blocks.FERN);
        NATURAL_BLOCKS.add(Blocks.LARGE_FERN);
        NATURAL_BLOCKS.add(Blocks.DEAD_BUSH);
        NATURAL_BLOCKS.add(Blocks.VINE);
        NATURAL_BLOCKS.add(Blocks.CAVE_VINES);
        NATURAL_BLOCKS.add(Blocks.CAVE_VINES_PLANT);
        NATURAL_BLOCKS.add(Blocks.SUGAR_CANE);
        NATURAL_BLOCKS.add(Blocks.BAMBOO);
        NATURAL_BLOCKS.add(Blocks.CACTUS);
        NATURAL_BLOCKS.add(Blocks.PUMPKIN);
        NATURAL_BLOCKS.add(Blocks.MELON);
        NATURAL_BLOCKS.add(Blocks.SEAGRASS);
        NATURAL_BLOCKS.add(Blocks.KELP);
        NATURAL_BLOCKS.add(Blocks.KELP_PLANT);
        NATURAL_BLOCKS.add(Blocks.LILY_PAD);
        NATURAL_BLOCKS.add(Blocks.COCOA);
        NATURAL_BLOCKS.add(Blocks.SNOW);
        NATURAL_BLOCKS.add(Blocks.COBWEB);
        // Sandstone family — generates naturally in desert biomes (desert
        // wells, desert temples, desert villages). Moved here from
        // UNNATURAL_BLOCKS to eliminate widespread desert-biome false
        // positives. Includes stairs/slabs/walls (desert village structures).
        NATURAL_BLOCKS.add(Blocks.SANDSTONE);
        NATURAL_BLOCKS.add(Blocks.RED_SANDSTONE);
        NATURAL_BLOCKS.add(Blocks.CUT_SANDSTONE);
        NATURAL_BLOCKS.add(Blocks.SMOOTH_SANDSTONE);
        NATURAL_BLOCKS.add(Blocks.CUT_RED_SANDSTONE);
        NATURAL_BLOCKS.add(Blocks.SMOOTH_RED_SANDSTONE);
        NATURAL_BLOCKS.add(Blocks.CHISELED_SANDSTONE);
        NATURAL_BLOCKS.add(Blocks.CHISELED_RED_SANDSTONE);
        NATURAL_BLOCKS.add(Blocks.SANDSTONE_STAIRS);
        NATURAL_BLOCKS.add(Blocks.RED_SANDSTONE_STAIRS);
        NATURAL_BLOCKS.add(Blocks.SANDSTONE_SLAB);
        NATURAL_BLOCKS.add(Blocks.RED_SANDSTONE_SLAB);
        NATURAL_BLOCKS.add(Blocks.SANDSTONE_WALL);
        NATURAL_BLOCKS.add(Blocks.RED_SANDSTONE_WALL);
        // Archaeology blocks — generate naturally in 1.20+ (desert wells,
        // ocean ruins, trail ruins).
        NATURAL_BLOCKS.add(Blocks.SUSPICIOUS_SAND);
        NATURAL_BLOCKS.add(Blocks.SUSPICIOUS_GRAVEL);
        // Amethyst geode family — generates naturally in 1.18+ geodes.
        NATURAL_BLOCKS.add(Blocks.AMETHYST_BLOCK);
        NATURAL_BLOCKS.add(Blocks.BUDDING_AMETHYST);
        NATURAL_BLOCKS.add(Blocks.SMALL_AMETHYST_BUD);
        NATURAL_BLOCKS.add(Blocks.MEDIUM_AMETHYST_BUD);
        NATURAL_BLOCKS.add(Blocks.LARGE_AMETHYST_BUD);
        NATURAL_BLOCKS.add(Blocks.AMETHYST_CLUSTER);
        // Dripstone caves — pointed dripstone generates naturally.
        NATURAL_BLOCKS.add(Blocks.POINTED_DRIPSTONE);
        // Mangrove swamp generation.
        NATURAL_BLOCKS.add(Blocks.MUD);
        NATURAL_BLOCKS.add(Blocks.PACKED_MUD);
        NATURAL_BLOCKS.add(Blocks.MUDDY_MANGROVE_ROOTS);
        NATURAL_BLOCKS.add(Blocks.MANGROVE_ROOTS);
        // Deep dark biome generation.
        NATURAL_BLOCKS.add(Blocks.SCULK);
        NATURAL_BLOCKS.add(Blocks.SCULK_VEIN);
        NATURAL_BLOCKS.add(Blocks.SCULK_CATALYST);
        NATURAL_BLOCKS.add(Blocks.SCULK_SENSOR);
        NATURAL_BLOCKS.add(Blocks.SCULK_SHRIEKER);
        // Raw ore blocks — generate in large ore veins since 1.18.
        NATURAL_BLOCKS.add(Blocks.RAW_IRON_BLOCK);
        NATURAL_BLOCKS.add(Blocks.RAW_COPPER_BLOCK);
        // Mountain biome generation.
        NATURAL_BLOCKS.add(Blocks.POWDER_SNOW);
        // Nether fossils + underwater ravine generation.
        NATURAL_BLOCKS.add(Blocks.MAGMA_BLOCK);
        NATURAL_BLOCKS.add(Blocks.BONE_BLOCK);
        // Warm ocean generation.
        NATURAL_BLOCKS.add(Blocks.SEA_PICKLE);
        // Note: OBSIDIAN is in UNNATURAL_BLOCKS only (checked first) — it's a
        // strong player signal even though it generates in lava+water
        // encounters and nether portals. Basalt (incl. smooth + polished)
        // is in NATURAL_BLOCKS now (geode shells / basalt deltas).
    }

    /**
     * The "natural light sources" — blocks that generate with luminance
     * naturally and shouldn't be flagged as a base-lighting signal. The
     * remaining light sources (torches, lanterns, end rods, furnaces, etc.)
     * flag.
     */
    private static final Set<Block> NATURAL_LIGHTS = new HashSet<>();
    static {
        NATURAL_LIGHTS.add(Blocks.GLOWSTONE);
        NATURAL_LIGHTS.add(Blocks.LAVA);
        NATURAL_LIGHTS.add(Blocks.SHROOMLIGHT);
        NATURAL_LIGHTS.add(Blocks.GLOW_LICHEN);
        NATURAL_LIGHTS.add(Blocks.CAVE_VINES);
        NATURAL_LIGHTS.add(Blocks.CAVE_VINES_PLANT);
        NATURAL_LIGHTS.add(Blocks.SPORE_BLOSSOM);
        NATURAL_LIGHTS.add(Blocks.SMALL_AMETHYST_BUD);
        NATURAL_LIGHTS.add(Blocks.MEDIUM_AMETHYST_BUD);
        NATURAL_LIGHTS.add(Blocks.LARGE_AMETHYST_BUD);
        NATURAL_LIGHTS.add(Blocks.BUDDING_AMETHYST);
        // Amethyst clusters emit light level 5 and generate naturally in geodes.
        NATURAL_LIGHTS.add(Blocks.AMETHYST_CLUSTER);
        NATURAL_LIGHTS.add(Blocks.JACK_O_LANTERN); // rare natural, but possible in villages
        NATURAL_LIGHTS.add(Blocks.END_ROD);
        NATURAL_LIGHTS.add(Blocks.FROGSPAWN);
        // Sculk family — sculk sensors and shriekers emit light naturally
        // (light level 1) in the deep dark biome.
        NATURAL_LIGHTS.add(Blocks.SCULK_SENSOR);
        NATURAL_LIGHTS.add(Blocks.SCULK_SHRIEKER);
        // Magma blocks emit light level 3 and generate naturally in the Nether
        // and underwater ravines.
        NATURAL_LIGHTS.add(Blocks.MAGMA_BLOCK);
        // Sea pickles emit light when waterlogged (light level 6 per pickle,
        // up to 15 with 4) and generate naturally in warm ocean biomes.
        NATURAL_LIGHTS.add(Blocks.SEA_PICKLE);
        // Froglights — technically mob drops but generate organically in
        // ancient cities and are commonly found in natural structures.
        NATURAL_LIGHTS.add(Blocks.PEARLESCENT_FROGLIGHT);
        NATURAL_LIGHTS.add(Blocks.VERDANT_FROGLIGHT);
        NATURAL_LIGHTS.add(Blocks.OCHRE_FROGLIGHT);
    }

    /**
     * The sampling stride — sample every Nth block along each axis within
     * a section. 2 = 8 samples per 16-block axis = 512 samples per section
     * (12.5% sample rate). This keeps the scan cheap while still catching
     * any non-trivial cluster of unnatural blocks.
     */
    private static final int SAMPLE_STRIDE = 2;

    /**
     * @return the shared set of player-placed / unnatural blocks (the same
     *         set used by {@link #scanChunk}). Exposed so other modules (e.g.
     *         HoleESP / Tunnel ESP) can corroborate air-pocket geometry by
     *         checking whether the surrounding shell contains unnatural
     *         blocks, without duplicating this list.
     */
    public static Set<Block> getUnnaturalBlocks() { return UNNATURAL_BLOCKS; }

    /**
     * @return the shared set of natural light-emitting blocks (the same
     *         set used by {@link #scanChunk}). Exposed so other modules can
     *         distinguish player-placed light sources (torches, lanterns)
     *         from natural ones (lava, glowstone, cave vines).
     */
    public static Set<Block> getNaturalLights() { return NATURAL_LIGHTS; }

    /**
     * Scan a chunk's sections for the four heuristic signals and return a
     * weighted score. Called from SusChunkESP's tick on the staggered 10-
     * tick cadence, only for chunks not already flagged by the cheaper
     * cache-based heuristics.
     *
     * @param chunk the loaded {@link WorldChunk} to scan
     * @return a {@link ScanResult} with the counts + weighted score
     */
    public static ScanResult scanChunk(WorldChunk chunk) {
        ScanResult r = new ScanResult();
        if (chunk == null) return r;

        // Block-entity signals (cheap — read the map, no section iteration).
        chunk.getBlockEntities().forEach((pos, be) -> {
            BlockEntityType<?> t = be.getType();
            if (t == BlockEntityType.MOB_SPAWNER) r.hasSpawner = true;
            if (SusChunkCache.isContainer(t)) {
                if (pos.getY() < 0) r.hasBelowY0BlockEntity = true;
            }
        });

        // Section-based palette scan.
        ChunkSection[] sections = chunk.getSectionArray();
        // The chunk's bottom Y (in blocks) — sections are indexed from 0
        // starting at world.getMinY(). We need the world height context to
        // know each section's absolute Y range; the chunk doesn't expose
        // it directly, so we use chunk.getBottomY() (yarn name for the
        // field that holds the world's min Y).
        int bottomY = chunk.getBottomY();

        // ── Two-pass scan to suppress cave false-positives ──────────
        // Air alone is not a player signal (1.18+ generates huge cave
        // systems below Y=0). We only count air that's CORROBORATED by a
        // nearby unnatural block or non-natural light source. So:
        //   Pass 1: record every sample position that is an unnatural
        //           block or a non-natural light source, and tally those.
        //   Pass 2: for each air sample below Y=0, check whether any
        //           pass-1 position is within ±2 blocks (Chebyshev). Only
        //           then count it as an air-pocket.
        // A bare cave (air walled by stone, no torches/chests) gets zero
        // air-pocket points. A player-carved room (air next to chests +
        // torches) gets points for both the air and the unnatural blocks.
        //
        // ── Surface FP suppression (sand villages, amethyst geodes,
        //    ocean monuments, surface temples) ──────────────────────
        // The section scan is limited to BELOW Y=0 (deepslate range).
        // Surface generation routinely places "unnatural-looking" blocks
        // in villages (oak planks, hay bales, chests, bells, lanterns),
        // desert temples (chests, TNT, pressure plates), ocean monuments
        // (sea lanterns), and amethyst geodes (smooth basalt shell — now
        // in NATURAL_BLOCKS). Limiting the scan to below Y=0 focuses
        // detection on underground bases — the actual stash-hunter use
        // case — while killing all surface natural-feature false
        // positives. Surface player bases are still detectable via the
        // block-entity (container density) + event-driven heuristics.
        //
        // Pass 1: tally unnatural blocks + light sources AND record the
        // sample coords of each player signal. We store (lx, absY, lz) —
        // chunk-local x/z (0..15 step 2) plus the absolute world Y — so the
        // Chebyshev check in pass 2 can cross section boundaries.
        java.util.List<int[]> playerSignals = new java.util.ArrayList<>();
        for (int s = 0; s < sections.length; s++) {
            ChunkSection section = sections[s];
            if (section == null || section.isEmpty()) continue;
            int sectionYBase = bottomY + s * 16;
            // Skip surface sections (Y >= 0) — surface FP suppression.
            // Only the deepslate range contributes to the section-scan
            // signal; surface structures are read by the block-entity +
            // event-driven heuristics instead.
            if (sectionYBase >= 0) continue;
            // Sample every SAMPLE_STRIDE blocks in each axis.
            for (int lx = 0; lx < 16; lx += SAMPLE_STRIDE) {
                for (int ly = 0; ly < 16; ly += SAMPLE_STRIDE) {
                    for (int lz = 0; lz < 16; lz += SAMPLE_STRIDE) {
                        BlockState state = section.getBlockState(lx, ly, lz);
                        if (state.isAir()) continue;
                        Block block = state.getBlock();
                        // 1. Unnatural block palette.
                        boolean unnatural = UNNATURAL_BLOCKS.contains(block);
                        if (unnatural) {
                            r.unnaturalBlocks++;
                        }
                        // 3. Non-natural light source (torch / lantern /
                        // glowstone underground).
                        boolean playerLight = state.getLuminance() > 0
                            && !NATURAL_LIGHTS.contains(block);
                        if (playerLight) {
                            r.lightSources++;
                        }
                        // Record the position for pass-2 corroboration if
                        // it's a player signal.
                        if (unnatural || playerLight) {
                            playerSignals.add(new int[]{lx, sectionYBase + ly, lz});
                        }
                    }
                }
            }
        }

        // Pass 2: count CORROBORATED air pockets. An air sample below Y=0
        // counts only if there's a player signal within ±2 blocks in each
        // axis (Chebyshev distance ≤ 2). This is the cave-FP killer —
        // walled-by-stone air never has a nearby unnatural block, so a
        // bare natural cave contributes ZERO air-pocket points. Only
        // player-carved rooms (air next to chests + torches) score here.
        if (!playerSignals.isEmpty()) {
            for (int s = 0; s < sections.length; s++) {
                ChunkSection section = sections[s];
                if (section == null || section.isEmpty()) continue;
                int sectionYBase = bottomY + s * 16;
                // Skip surface sections — air-pocket corroboration only
                // runs below Y=0 (perf + correctness: no surface air).
                if (sectionYBase >= 0) continue;
                for (int lx = 0; lx < 16; lx += SAMPLE_STRIDE) {
                    for (int ly = 0; ly < 16; ly += SAMPLE_STRIDE) {
                        for (int lz = 0; lz < 16; lz += SAMPLE_STRIDE) {
                            BlockState state = section.getBlockState(lx, ly, lz);
                            if (!state.isAir()) continue;
                            int absY = sectionYBase + ly;
                            // (No need to re-check absY >= 0 — the
                            // sectionYBase >= 0 skip above already
                            // guarantees absY < 0 for every air sample
                            // reached here.)
                            // Chebyshev ≤ 2 against any player signal.
                            for (int[] sig : playerSignals) {
                                if (Math.abs(sig[0] - lx) <= 2
                                    && Math.abs(sig[1] - absY) <= 2
                                    && Math.abs(sig[2] - lz) <= 2) {
                                    r.airPockets++;
                                    break; // one corroboration is enough
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Heightmap inconsistency: compare the stored WORLD_SURFACE
        // heightmap (highest non-air block Y per column) against the
        // actual highest non-air block we found in each column via the
        // section scan. A mismatch — when CORROBORATED by a nearby player
        // signal — can leak surface edits / underground ceilings even when
        // the obfuscated block states hide them.
        //
        // UNCORROBORATED heightmap mismatches are NOT a player signal:
        // natural multi-layer terrain (desert temples, badlands, amethyst
        // geode roofs, overhangs, snow drift, lava-lakes, ice spike
        // pillars, savanna plateaus) routinely produces columns whose
        // stored WORLD_SURFACE heightmap doesn't match the single highest
        // non-air block found by a top-down scan — even when no player has
        // ever touched the chunk. Counting those mismatches standalone
        // flagged every natural sand/amethyst-geode/overhang chunk as a
        // "base" (the user-reported false-positive). So — same pattern as
        // the air-pocket check — only count a mismatch when there's an
        // unnatural block or non-natural light source within ±2 blocks
        // (Chebyshev). Bare natural terrain (sand beside sandstone, geode
        // beside smooth basalt) gets zero heightmap-mismatch points; a
        // player-edited column next to a chest/torch/planks scores.
        //
        // Also guard the three real failure modes that produced the
        // mountains of false mismatches in pristine chunks:
        //   • Heightmap.get() returns -1 for columns whose heightmap
        //     hasn't been populated yet — skip (do NOT count as mismatch).
        //   • A heightmap entry above the world's top Y (overflow sentinel
        //     some servers send for untouched columns) — skip.
        //   • Tolerance: a difference of <= 2 blocks is normal block-update
        //     lag / snow-layer drift / leaf-decay noise — only count > 2.
        //   • Skip the upper-half of the world (Y >= 0): the "leaks
        //     underground ceilings" signal is only meaningful below Y=0
        //     where anti-xray is active; surface-terrain height-map noise
        //     above Y=0 isn't a base signal.
        try {
            Heightmap hm = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE);
            // Reuse playerSignals from pass 1 — only chessmatch-mismatch
            // columns near a signal count. If pass 1 found no player
            // signals at all, the loop below is a no-op (we skip the
            // entire scan — a chunk with zero chests/torches/unnatural
            // blocks can't be a "base" regardless of its heightmap).
            if (!playerSignals.isEmpty()) {
                outer:
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int storedY = hm.get(lx, lz);
                        // Skip unpopulated / sentinel heightmap entries.
                        if (storedY < bottomY) continue;
                        // Find the actual highest non-air block in this
                        // column by scanning sections top-down, but only
                        // below Y=0 (the deepslate base-hunting range).
                        int actualY = bottomY - 1;
                        for (int s = sections.length - 1; s >= 0; s--) {
                            ChunkSection section = sections[s];
                            if (section == null || section.isEmpty()) continue;
                            int sectionYBase = bottomY + s * 16;
                            // Skip purely-surface sections (Y >= 0) — their
                            // heightmap is noisy natural terrain, not a
                            // player signal.
                            if (sectionYBase >= 0) continue;
                            for (int ly = 15; ly >= 0; ly--) {
                                BlockState st = section.getBlockState(lx, ly, lz);
                                if (!st.isAir()) {
                                    actualY = sectionYBase + ly;
                                    break;
                                }
                            }
                            if (actualY >= sectionYBase) break;
                        }
                        if (actualY < bottomY) continue; // no non-air below Y=0
                        int expected = actualY + 1; // heightmap stores "first air" Y
                        // Tolerance: <= 2 blocks is block-update lag / snow drift.
                        if (Math.abs(storedY - expected) <= 2) continue;
                        // Chebyshev ≤ 2 corroboration against any player
                        // signal recorded in pass 1 (unnatural block or
                        // non-natural light). Only count mismatches near
                        // real player activity — kills the sand / amethyst
                        // / overhang false positives where the surrounding
                        // blocks are all natural.
                        for (int[] sig : playerSignals) {
                            if (Math.abs(sig[0] - lx) <= 2
                                && Math.abs(sig[2] - lz) <= 2
                                && Math.abs(sig[1] - actualY) <= 4) {
                                r.heightmapMismatches++;
                                continue outer;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Heightmap may not be populated yet for freshly-loaded chunks.
        }

        // Weighted score. Air pockets are now a CORROBORATING signal (only
        // counted near unnatural blocks / light), so they weigh LOW — they
        // amplify the unnatural-block signal rather than standing alone.
        // Unnatural blocks + light sources are the primary player signals.
        // Heightmap mismatches are anti-xray-resistant but rare, so they
        // weigh moderate. The block-entity signals (spawner, below-Y=0) are
        // handled separately by SusChunkESP's existing flags — here we just
        // add the section-scan contribution.
        r.score = r.unnaturalBlocks * 1.0f
            + r.airPockets * 0.5f
            + r.lightSources * 2.0f
            + r.heightmapMismatches * 1.0f;
        return r;
    }
}