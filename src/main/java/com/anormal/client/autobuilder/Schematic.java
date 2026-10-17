package com.anormal.client.autobuilder;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Format independent schematic representation.
 *
 * <p>All block positions are stored relative to the schematic's minimum corner, so they are
 * always inside {@code [0, sizeX) x [0, sizeY) x [0, sizeZ)}. Every loader normalises its
 * input through {@link #of} so the rest of the pipeline never has to care where the original
 * file placed its origin.</p>
 *
 * <p>Only non-air blocks are stored. A 100x100x100 build therefore costs one map entry per
 * actually occupied position instead of a million.</p>
 */
public final class Schematic {

    public final String name;
    public final String format;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;

    /** Relative position to block state. Never contains air. */
    public final Map<BlockPos, BlockState> blocks;
    /** Relative position to block entity NBT, for the positions that carry one. */
    public final Map<BlockPos, NbtCompound> blockEntities;

    public final List<String> warnings;
    public final int regionCount;
    public final int paletteSize;

    private Schematic(String name, String format, int sizeX, int sizeY, int sizeZ,
                      Map<BlockPos, BlockState> blocks, Map<BlockPos, NbtCompound> blockEntities,
                      List<String> warnings, int regionCount, int paletteSize) {
        this.name = name;
        this.format = format;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = blocks;
        this.blockEntities = blockEntities;
        this.warnings = warnings;
        this.regionCount = regionCount;
        this.paletteSize = paletteSize;
    }

    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    public int blockCount() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /**
     * Normalises raw relative coordinates so the minimum corner becomes {@code (0,0,0)} and
     * derives the dimensions from the actual block extents.
     */
    public static Schematic of(String name, String format,
                               Map<BlockPos, BlockState> rawBlocks,
                               Map<BlockPos, NbtCompound> rawBlockEntities,
                               List<String> warnings, int regionCount, int paletteSize) {

        List<String> notes = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);

        if (rawBlocks == null || rawBlocks.isEmpty()) {
            return new Schematic(name, format, 0, 0, 0,
                    Collections.emptyMap(), Collections.emptyMap(), notes, regionCount, paletteSize);
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : rawBlocks.keySet()) {
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        int dx = -minX, dy = -minY, dz = -minZ;

        Map<BlockPos, BlockState> shifted = new HashMap<>(Math.max(16, rawBlocks.size() * 2));
        for (Map.Entry<BlockPos, BlockState> entry : rawBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            shifted.put(new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz), entry.getValue());
        }

        Map<BlockPos, NbtCompound> shiftedEntities = new HashMap<>();
        if (rawBlockEntities != null) {
            for (Map.Entry<BlockPos, NbtCompound> entry : rawBlockEntities.entrySet()) {
                BlockPos pos = entry.getKey();
                shiftedEntities.put(new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz), entry.getValue());
            }
        }

        return new Schematic(name, format,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1,
                shifted, shiftedEntities, notes, regionCount, paletteSize);
    }

    /** Immutable view used by the transformer and the preview renderer. */
    public Schematic with(String newFormat,
                          Map<BlockPos, BlockState> newBlocks,
                          Map<BlockPos, NbtCompound> newBlockEntities,
                          int newSizeX, int newSizeY, int newSizeZ,
                          List<String> newWarnings) {
        return new Schematic(name, newFormat, newSizeX, newSizeY, newSizeZ,
                newBlocks, newBlockEntities,
                newWarnings == null ? new ArrayList<>() : newWarnings,
                regionCount, paletteSize);
    }

    public String dimensions() {
        return sizeX + " x " + sizeY + " x " + sizeZ;
    }
}
