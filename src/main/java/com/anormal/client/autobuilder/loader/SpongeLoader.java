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
 * Sponge Schematic format, versions 2 and 3.
 *
 * <p>v2 keeps {@code Palette} / {@code BlockData} at the root, v3 nests them inside a
 * {@code Blocks} compound as {@code Palette} / {@code Data}. Both store block indices as
 * unsigned LEB128 varints in the order {@code (y * Length + z) * Width + x}.</p>
 */
public final class SpongeLoader implements SchematicIO.Loader {

    @Override
    public String formatName() {
        return "Sponge";
    }

    @Override
    public boolean matches(NbtCompound root) {
        NbtCompound schem = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;

        if (schem.get("Blocks") instanceof NbtCompound blocks) {
            if (blocks.contains("Data") || blocks.contains("Palette")) return true;
        }
        return schem.contains("BlockData") && schem.contains("Palette");
    }

    @Override
    public Schematic load(String name, NbtCompound root, List<String> warnings)
            throws SchematicIO.SchematicException {

        NbtCompound schem = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
        int version = schem.getInt("Version", 2);

        if (version < 2) {
            warnings.add("Sponge v" + version + " is not supported; only v2 and v3 are read");
        }

        int width = SchematicIO.intOrShort(schem, "Width", 0);
        int height = SchematicIO.intOrShort(schem, "Height", 0);
        int length = SchematicIO.intOrShort(schem, "Length", 0);

        if (width <= 0 || height <= 0 || length <= 0) {
            throw new SchematicIO.SchematicException("Invalid schematic dimensions: "
                    + width + " x " + height + " x " + length);
        }

        NbtCompound paletteTag;
        byte[] data;
        NbtList blockEntities;

        if (version >= 3) {
            NbtCompound blocks = schem.getCompoundOrEmpty("Blocks");
            paletteTag = blocks.getCompoundOrEmpty("Palette");
            data = SchematicIO.byteArray(blocks, "Data");
            blockEntities = blocks.getListOrEmpty("BlockEntities");
        } else {
            paletteTag = schem.getCompoundOrEmpty("Palette");
            data = SchematicIO.byteArray(schem, "BlockData");
            blockEntities = schem.getListOrEmpty("BlockEntities");
        }

        if (data == null || data.length == 0) {
            throw new SchematicIO.SchematicException("Schematic has no block data");
        }
        if (paletteTag.getSize() == 0) {
            throw new SchematicIO.SchematicException("Schematic has an empty palette");
        }

        // ---- palette ------------------------------------------------------
        int maxId = 0;
        for (String key : paletteTag.getKeys()) {
            int id = paletteTag.getInt(key, -1);
            if (id > maxId) maxId = id;
        }

        String[] palette = new String[maxId + 1];
        for (String key : paletteTag.getKeys()) {
            int id = paletteTag.getInt(key, -1);
            if (id >= 0) palette[id] = key;
        }

        BlockState[] states = new BlockState[palette.length];
        Set<String> unknown = new HashSet<>();

        long total = (long) width * height * length;
        if (total > 64_000_000L) {
            throw new SchematicIO.SchematicException("Schematic is too large (" + total + " blocks)");
        }

        Map<BlockPos, BlockState> blocks = new HashMap<>(Math.max(16, (int) Math.min(total, 4_000_000)));
        int x = 0, z = 0, y = 0;
        int index = 0;
        int cursor = 0;

        while (cursor < data.length && index < total) {
            int value = 0;
            int shift = 0;
            while (cursor < data.length) {
                byte b = data[cursor++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) throw new SchematicIO.SchematicException("Malformed block data (bad varint)");
            }

            if (value > 0 && value < palette.length) {
                BlockState state = states[value];
                if (state == null) {
                    String key = palette[value];
                    if (key != null) {
                        state = BlockStateParser.parse(key);
                        if (state == null && unknown.add(key)) {
                            warnings.add("Unknown block: " + key);
                        }
                    }
                    states[value] = state;
                }
                if (state != null && !state.isAir()) {
                    blocks.put(new BlockPos(x, y, z), state);
                }
            } else if (value >= palette.length) {
                if (unknown.add("#" + value)) {
                    warnings.add("Palette index " + value + " is out of range");
                }
            }

            index++;
            if (++x >= width) {
                x = 0;
                if (++z >= length) {
                    z = 0;
                    y++;
                }
            }
        }

        if (index < total) {
            warnings.add("Block data ended early (" + index + " of " + total + " positions read)");
        }

        // ---- block entities ----------------------------------------------
        Map<BlockPos, NbtCompound> entities = new HashMap<>();
        for (int i = 0; i < blockEntities.size(); i++) {
            NbtCompound entry = blockEntities.getCompoundOrEmpty(i);
            int[] pos = SchematicIO.intArray(entry, "Pos");
            if (pos == null || pos.length < 3) continue;
            entities.put(new BlockPos(pos[0], pos[1], pos[2]), entry.copy());
        }

        List<String> notes = new ArrayList<>(warnings);
        return Schematic.of(name, "Sponge v" + version, blocks, entities, notes, 1, palette.length);
    }
}
