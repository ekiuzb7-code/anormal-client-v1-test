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
 * Litematica {@code .litematic} reader.
 *
 * <p>Each region stores its own palette and a bit-packed {@code BlockStates} long array.
 * Unlike the vanilla structure format the values are packed contiguously across longs, so the
 * bit stream is read directly instead of using padded per-long slots.</p>
 *
 * <p>A region {@code Size} may be negative, which means the region extends in the negative
 * direction from {@code Position}; the base corner is adjusted accordingly.</p>
 */
public final class LitematicaLoader implements SchematicIO.Loader {

    private static final long MAX_BLOCKS = 64_000_000L;

    @Override
    public String formatName() {
        return "Litematica";
    }

    @Override
    public boolean matches(NbtCompound root) {
        if (!root.contains("Regions")) return false;
        return root.get("Regions") instanceof NbtCompound regions && regions.getSize() > 0;
    }

    @Override
    public Schematic load(String name, NbtCompound root, List<String> warnings)
            throws SchematicIO.SchematicException {

        NbtCompound regions = root.getCompoundOrEmpty("Regions");
        if (regions.getSize() == 0) {
            throw new SchematicIO.SchematicException("Litematic contains no regions");
        }

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, NbtCompound> entities = new HashMap<>();
        Set<String> unknown = new HashSet<>();
        int totalPalette = 0;
        int regionCount = 0;

        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompoundOrEmpty(regionName);
            int[] position = vec(region, "Position");
            int[] size = vec(region, "Size");

            if (position == null || size == null) {
                warnings.add("Region '" + regionName + "' is missing Position/Size and was skipped");
                continue;
            }

            int sx = size[0], sy = size[1], sz = size[2];
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            if (ax == 0 || ay == 0 || az == 0) {
                warnings.add("Region '" + regionName + "' has an empty size and was skipped");
                continue;
            }

            long total = (long) ax * ay * az;
            if (total > MAX_BLOCKS) {
                throw new SchematicIO.SchematicException(
                        "Region '" + regionName + "' is too large (" + total + " blocks)");
            }

            int baseX = sx >= 0 ? position[0] : position[0] + sx + 1;
            int baseY = sy >= 0 ? position[1] : position[1] + sy + 1;
            int baseZ = sz >= 0 ? position[2] : position[2] + sz + 1;

            long[] data = SchematicIO.longArray(region, "BlockStates");
            if (data == null || data.length == 0) {
                warnings.add("Region '" + regionName + "' has no block data and was skipped");
                continue;
            }

            NbtList paletteTag = region.getListOrEmpty("BlockStatePalette");
            if (paletteTag.size() == 0) {
                warnings.add("Region '" + regionName + "' has an empty palette and was skipped");
                continue;
            }

            BlockState[] palette = new BlockState[paletteTag.size()];
            totalPalette += palette.length;

            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
            long mask = (1L << bits) - 1L;

            long required = (total * bits + 63L) / 64L;
            if (data.length < required) {
                warnings.add("Region '" + regionName + "' is truncated ("
                        + data.length + " of " + required + " longs)");
            }

            int index = 0;
            for (int y = 0; y < ay; y++) {
                for (int z = 0; z < az; z++) {
                    for (int x = 0; x < ax; x++) {
                        if (index >= total) break;

                        long startBit = (long) index * bits;
                        int startLong = (int) (startBit >>> 6);
                        int offset = (int) (startBit & 63L);
                        long value;

                        if (startLong >= data.length) {
                            value = 0;
                        } else if (offset + bits <= 64) {
                            value = (data[startLong] >>> offset) & mask;
                        } else if (startLong + 1 < data.length) {
                            value = ((data[startLong] >>> offset)
                                    | (data[startLong + 1] << (64 - offset))) & mask;
                        } else {
                            value = (data[startLong] >>> offset) & mask;
                        }

                        int id = (int) value;
                        if (id > 0 && id < palette.length) {
                            BlockState state = palette[id];
                            if (state == null) {
                                state = stateOf(paletteTag.getCompoundOrEmpty(id), unknown, warnings);
                                palette[id] = state;
                            }
                            if (state != null && !state.isAir()) {
                                blocks.put(new BlockPos(baseX + x, baseY + y, baseZ + z), state);
                            }
                        }

                        index++;
                    }
                }
            }

            // Block entities are stored relative to the region origin.
            NbtList tileEntities = region.getListOrEmpty("TileEntities");
            for (int i = 0; i < tileEntities.size(); i++) {
                NbtCompound entry = tileEntities.getCompoundOrEmpty(i);
                int[] pos = vec(entry, "Pos");
                if (pos == null) continue;
                entities.put(new BlockPos(baseX + pos[0], baseY + pos[1], baseZ + pos[2]), entry.copy());
            }

            regionCount++;
        }

        if (regionCount == 0) {
            throw new SchematicIO.SchematicException("No readable regions found in the litematic");
        }
        if (regionCount > 1) {
            warnings.add("Combined " + regionCount + " regions into one schematic");
        }

        return Schematic.of(name, "Litematica", blocks, entities,
                new ArrayList<>(warnings), regionCount, totalPalette);
    }

    private static BlockState stateOf(NbtCompound entry, Set<String> unknown, List<String> warnings) {
        String blockName = entry.getString("Name", "minecraft:air");
        if (blockName.isEmpty()) return null;

        NbtCompound properties = entry.getCompoundOrEmpty("Properties");
        String key = blockName;

        if (properties.getSize() > 0) {
            StringBuilder sb = new StringBuilder(blockName).append('[');
            boolean first = true;
            for (String name : properties.getKeys()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(name).append('=').append(properties.getString(name, ""));
            }
            sb.append(']');
            key = sb.toString();
        }

        BlockState state = BlockStateParser.parse(key);
        if (state == null) {
            state = BlockStateParser.parse(blockName);
            if (state == null && unknown.add(blockName)) {
                warnings.add("Unknown block: " + blockName);
            } else if (state != null && unknown.add(key)) {
                warnings.add("Unsupported block state, using defaults: " + key);
            }
        }
        return state;
    }

    /** Reads a vector that may be stored either as a compound or as an int array. */
    private static int[] vec(NbtCompound tag, String key) {
        if (tag.get(key) instanceof NbtCompound compound) {
            return new int[]{
                    compound.getInt("x", 0),
                    compound.getInt("y", 0),
                    compound.getInt("z", 0)
            };
        }
        int[] array = SchematicIO.intArray(tag, key);
        if (array != null && array.length >= 3) {
            return new int[]{array[0], array[1], array[2]};
        }
        return null;
    }
}
