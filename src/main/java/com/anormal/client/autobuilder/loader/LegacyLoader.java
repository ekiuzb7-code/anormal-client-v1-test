package com.anormal.client.autobuilder.loader;

import com.anormal.client.autobuilder.BlockStateParser;
import com.anormal.client.autobuilder.Schematic;
import com.anormal.client.autobuilder.SchematicIO;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Legacy MCEdit / old WorldEdit {@code .schematic} reader.
 *
 * <p>These files store numeric block ids and a metadata byte instead of block state strings.
 * Numeric ids were removed from modern Minecraft, so a lookup table maps the classic ids back
 * to their modern blocks. Metadata that changes the block type (wool colour, wood species,
 * slab type) is applied; metadata that only carries orientation cannot be reconstructed
 * reliably and is reported once in the warnings instead of being guessed.</p>
 */
public final class LegacyLoader implements SchematicIO.Loader {

    private static final String[] IDS = new String[256];

    private static final String[] DYES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    static {
        IDS[1] = "stone";
        IDS[2] = "grass_block";
        IDS[3] = "dirt";
        IDS[4] = "cobblestone";
        IDS[5] = "oak_planks";
        IDS[6] = "oak_sapling";
        IDS[7] = "bedrock";
        IDS[8] = "water";
        IDS[9] = "water";
        IDS[10] = "lava";
        IDS[11] = "lava";
        IDS[12] = "sand";
        IDS[13] = "gravel";
        IDS[14] = "gold_ore";
        IDS[15] = "iron_ore";
        IDS[16] = "coal_ore";
        IDS[17] = "oak_log";
        IDS[18] = "oak_leaves";
        IDS[19] = "sponge";
        IDS[20] = "glass";
        IDS[21] = "lapis_ore";
        IDS[22] = "lapis_block";
        IDS[23] = "dispenser";
        IDS[24] = "sandstone";
        IDS[25] = "note_block";
        IDS[26] = "red_bed";
        IDS[27] = "rail";
        IDS[28] = "detector_rail";
        IDS[29] = "sticky_piston";
        IDS[30] = "cobweb";
        IDS[31] = "short_grass";
        IDS[32] = "dead_bush";
        IDS[33] = "piston";
        IDS[34] = "piston_head";
        IDS[35] = "white_wool";
        IDS[37] = "dandelion";
        IDS[38] = "poppy";
        IDS[39] = "brown_mushroom";
        IDS[40] = "red_mushroom";
        IDS[41] = "gold_block";
        IDS[42] = "iron_block";
        IDS[43] = "smooth_stone_slab";
        IDS[44] = "smooth_stone_slab";
        IDS[45] = "bricks";
        IDS[46] = "tnt";
        IDS[47] = "bookshelf";
        IDS[48] = "mossy_cobblestone";
        IDS[49] = "obsidian";
        IDS[50] = "torch";
        IDS[51] = "fire";
        IDS[52] = "spawner";
        IDS[53] = "oak_stairs";
        IDS[54] = "chest";
        IDS[55] = "redstone_wire";
        IDS[56] = "diamond_ore";
        IDS[57] = "diamond_block";
        IDS[58] = "crafting_table";
        IDS[59] = "wheat";
        IDS[60] = "farmland";
        IDS[61] = "furnace";
        IDS[62] = "furnace";
        IDS[63] = "oak_sign";
        IDS[64] = "oak_door";
        IDS[65] = "ladder";
        IDS[66] = "rail";
        IDS[67] = "stone_stairs";
        IDS[68] = "oak_wall_sign";
        IDS[69] = "lever";
        IDS[70] = "stone_pressure_plate";
        IDS[71] = "iron_door";
        IDS[72] = "oak_pressure_plate";
        IDS[73] = "redstone_ore";
        IDS[74] = "redstone_ore";
        IDS[75] = "redstone_torch";
        IDS[76] = "redstone_wall_torch";
        IDS[77] = "stone_button";
        IDS[78] = "snow";
        IDS[79] = "ice";
        IDS[80] = "snow_block";
        IDS[81] = "cactus";
        IDS[82] = "clay";
        IDS[83] = "sugar_cane";
        IDS[84] = "jukebox";
        IDS[85] = "oak_fence";
        IDS[86] = "pumpkin";
        IDS[87] = "netherrack";
        IDS[88] = "soul_sand";
        IDS[89] = "glowstone";
        IDS[90] = "nether_portal";
        IDS[91] = "jack_o_lantern";
        IDS[92] = "cake";
        IDS[93] = "repeater";
        IDS[94] = "repeater";
        IDS[95] = "white_stained_glass";
        IDS[96] = "oak_trapdoor";
        IDS[97] = "infested_stone";
        IDS[98] = "stone_bricks";
        IDS[99] = "brown_mushroom_block";
        IDS[100] = "red_mushroom_block";
        IDS[101] = "iron_bars";
        IDS[102] = "glass_pane";
        IDS[103] = "melon";
        IDS[104] = "pumpkin_stem";
        IDS[105] = "melon_stem";
        IDS[106] = "vine";
        IDS[107] = "oak_fence_gate";
        IDS[108] = "brick_stairs";
        IDS[109] = "stone_brick_stairs";
        IDS[110] = "mycelium";
        IDS[111] = "lily_pad";
        IDS[112] = "nether_bricks";
        IDS[113] = "nether_brick_fence";
        IDS[114] = "nether_brick_stairs";
        IDS[115] = "nether_wart";
        IDS[116] = "enchanting_table";
        IDS[117] = "brewing_stand";
        IDS[118] = "cauldron";
        IDS[119] = "end_portal";
        IDS[120] = "end_portal_frame";
        IDS[121] = "end_stone";
        IDS[122] = "dragon_egg";
        IDS[123] = "redstone_lamp";
        IDS[124] = "redstone_lamp";
        IDS[125] = "oak_slab";
        IDS[126] = "oak_slab";
        IDS[127] = "cocoa";
        IDS[128] = "sandstone_stairs";
        IDS[129] = "emerald_ore";
        IDS[130] = "ender_chest";
        IDS[131] = "tripwire_hook";
        IDS[132] = "tripwire";
        IDS[133] = "emerald_block";
        IDS[134] = "spruce_stairs";
        IDS[135] = "birch_stairs";
        IDS[136] = "jungle_stairs";
        IDS[137] = "command_block";
        IDS[138] = "beacon";
        IDS[139] = "cobblestone_wall";
        IDS[140] = "flower_pot";
        IDS[141] = "carrots";
        IDS[142] = "potatoes";
        IDS[143] = "oak_button";
        IDS[144] = "skeleton_skull";
        IDS[145] = "anvil";
        IDS[146] = "trapped_chest";
        IDS[147] = "light_weighted_pressure_plate";
        IDS[148] = "heavy_weighted_pressure_plate";
        IDS[149] = "comparator";
        IDS[150] = "comparator";
        IDS[151] = "daylight_detector";
        IDS[152] = "redstone_block";
        IDS[153] = "nether_quartz_ore";
        IDS[154] = "hopper";
        IDS[155] = "quartz_block";
        IDS[156] = "quartz_stairs";
        IDS[157] = "activator_rail";
        IDS[158] = "dropper";
        IDS[159] = "white_terracotta";
        IDS[160] = "white_stained_glass_pane";
        IDS[161] = "oak_leaves";
        IDS[162] = "oak_log";
        IDS[163] = "acacia_stairs";
        IDS[164] = "dark_oak_stairs";
        IDS[165] = "slime_block";
        IDS[166] = "barrier";
        IDS[167] = "iron_trapdoor";
        IDS[168] = "prismarine";
        IDS[169] = "sea_lantern";
        IDS[170] = "hay_block";
        IDS[171] = "white_carpet";
        IDS[172] = "terracotta";
        IDS[173] = "coal_block";
        IDS[174] = "packed_ice";
        IDS[175] = "tall_grass";
        IDS[176] = "white_banner";
        IDS[177] = "white_wall_banner";
        IDS[178] = "red_sandstone";
        IDS[179] = "red_sandstone_stairs";
        IDS[180] = "red_sandstone_slab";
        IDS[181] = "red_sandstone_slab";
        IDS[182] = "spruce_fence_gate";
        IDS[183] = "spruce_fence";
        IDS[184] = "birch_fence";
        IDS[185] = "jungle_fence";
        IDS[186] = "dark_oak_fence";
        IDS[187] = "acacia_fence";
        IDS[188] = "spruce_door";
        IDS[189] = "birch_door";
        IDS[190] = "jungle_door";
        IDS[191] = "acacia_door";
        IDS[192] = "dark_oak_door";
        IDS[193] = "spruce_fence_gate";
        IDS[194] = "birch_fence_gate";
        IDS[195] = "jungle_fence_gate";
        IDS[196] = "acacia_fence_gate";
        IDS[197] = "dark_oak_fence_gate";
        IDS[198] = "end_rod";
        IDS[199] = "chorus_plant";
        IDS[200] = "chorus_flower";
        IDS[201] = "purpur_block";
        IDS[202] = "purpur_pillar";
        IDS[203] = "purpur_stairs";
        IDS[204] = "purpur_double_slab";
        IDS[205] = "purpur_slab";
        IDS[206] = "end_stone_bricks";
        IDS[207] = "beetroots";
        IDS[208] = "dirt_path";
        IDS[209] = "end_gateway";
        IDS[210] = "repeating_command_block";
        IDS[211] = "chain_command_block";
        IDS[212] = "frosted_ice";
        IDS[213] = "magma_block";
        IDS[214] = "nether_wart_block";
        IDS[215] = "red_nether_bricks";
        IDS[216] = "bone_block";
        IDS[217] = "structure_void";
        IDS[218] = "observer";
        IDS[219] = "white_shulker_box";
        IDS[220] = "white_glazed_terracotta";
        IDS[251] = "white_concrete";
        IDS[252] = "white_concrete_powder";
    }

    @Override
    public String formatName() {
        return "Legacy";
    }

    @Override
    public boolean matches(NbtCompound root) {
        if (!root.contains("Width") || !root.contains("Blocks")) return false;
        return root.get("Blocks") instanceof net.minecraft.nbt.NbtByteArray;
    }

    @Override
    public Schematic load(String name, NbtCompound root, List<String> warnings)
            throws SchematicIO.SchematicException {

        int width = SchematicIO.intOrShort(root, "Width", 0);
        int height = SchematicIO.intOrShort(root, "Height", 0);
        int length = SchematicIO.intOrShort(root, "Length", 0);

        if (width <= 0 || height <= 0 || length <= 0) {
            throw new SchematicIO.SchematicException("Invalid schematic dimensions");
        }

        byte[] blocks = SchematicIO.byteArray(root, "Blocks");
        if (blocks == null) throw new SchematicIO.SchematicException("Schematic has no block data");

        long total = (long) width * height * length;
        if (blocks.length < total) {
            throw new SchematicIO.SchematicException("Block array is shorter than the declared size");
        }

        byte[] data = SchematicIO.byteArray(root, "Data");
        byte[] add = SchematicIO.byteArray(root, "AddBlocks");

        Map<BlockPos, BlockState> map = new HashMap<>();
        Set<Integer> unknown = new HashSet<>();
        boolean orientationLost = false;

        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++, index++) {
                    int id = blocks[index] & 0xFF;
                    if (add != null && index / 2 < add.length) {
                        int packed = add[index / 2] & 0xFF;
                        int high = (index & 1) == 0 ? (packed & 0x0F) : (packed >> 4) & 0x0F;
                        id |= high << 8;
                    }
                    if (id == 0) continue;

                    int meta = data != null && index < data.length ? data[index] & 0x0F : 0;
                    String key = resolve(id, meta);
                    if (key == null) {
                        if (unknown.add(id)) warnings.add("Unsupported legacy block id " + id);
                        continue;
                    }

                    BlockState state = BlockStateParser.parse("minecraft:" + key);
                    if (state == null) {
                        if (unknown.add(id)) warnings.add("Unknown block: " + key);
                        continue;
                    }
                    if (!state.isAir()) {
                        map.put(new BlockPos(x, y, z), state);
                        if (meta != 0 && !orientationLost) orientationLost = true;
                    }
                }
            }
        }

        if (orientationLost) {
            warnings.add("Legacy orientation metadata (stairs, logs, rails) is not restored");
        }

        Map<BlockPos, NbtCompound> entities = new HashMap<>();
        NbtList tiles = root.getListOrEmpty("TileEntities");
        for (int i = 0; i < tiles.size(); i++) {
            NbtCompound entry = tiles.getCompoundOrEmpty(i);
            int[] pos = SchematicIO.intArray(entry, "Pos");
            if (pos == null || pos.length < 3) continue;
            entities.put(new BlockPos(pos[0], pos[1], pos[2]), entry.copy());
        }

        return Schematic.of(name, "Legacy", map, entities,
                new ArrayList<>(warnings), 1, 256);
    }

    private static String resolve(int id, int meta) {
        // Colour / species variants driven by the metadata nibble.
        switch (id) {
            case 5 -> {
                String[] wood = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
                return (meta < wood.length ? wood[meta] : "oak") + "_planks";
            }
            case 17, 162 -> {
                String[] wood = id == 17
                        ? new String[]{"oak", "spruce", "birch", "jungle"}
                        : new String[]{"acacia", "dark_oak"};
                String species = wood[Math.min(wood.length - 1, meta & 3)];
                String axis = switch ((meta >> 2) & 3) {
                    case 1 -> "x";
                    case 2 -> "z";
                    default -> "y";
                };
                return species + "_log[axis=" + axis + "]";
            }
            case 18, 161 -> {
                String[] wood = id == 18
                        ? new String[]{"oak", "spruce", "birch", "jungle"}
                        : new String[]{"acacia", "dark_oak"};
                return wood[Math.min(wood.length - 1, meta & 3)] + "_leaves";
            }
            case 35 -> {
                return DYES[Math.min(15, meta)] + "_wool";
            }
            case 95 -> {
                return DYES[Math.min(15, meta)] + "_stained_glass";
            }
            case 160 -> {
                return DYES[Math.min(15, meta)] + "_stained_glass_pane";
            }
            case 159 -> {
                return DYES[Math.min(15, meta)] + "_terracotta";
            }
            case 171 -> {
                return DYES[Math.min(15, meta)] + "_carpet";
            }
            case 251 -> {
                return DYES[Math.min(15, meta)] + "_concrete";
            }
            case 252 -> {
                return DYES[Math.min(15, meta)] + "_concrete_powder";
            }
            case 24 -> {
                return switch (meta) {
                    case 1 -> "chiseled_sandstone";
                    case 2 -> "cut_sandstone";
                    default -> "sandstone";
                };
            }
            case 98 -> {
                return switch (meta) {
                    case 1 -> "mossy_stone_bricks";
                    case 2 -> "cracked_stone_bricks";
                    case 3 -> "chiseled_stone_bricks";
                    default -> "stone_bricks";
                };
            }
            case 155 -> {
                return switch (meta) {
                    case 1 -> "chiseled_quartz_block";
                    case 2 -> "quartz_pillar";
                    default -> "quartz_block";
                };
            }
            case 43, 44, 125, 126, 180, 181, 182, 204, 205 -> {
                // Slabs: odd metadata means "top" in modern terms.
                String base = IDS[id];
                if (base == null) return null;
                return (meta & 8) != 0 ? base + "[type=top]" : base;
            }
            default -> {
                return IDS[id];
            }
        }
    }
}
