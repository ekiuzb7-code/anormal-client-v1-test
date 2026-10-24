package dev.autocc.client.litematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Reads a Litematica ({@code .litematica}) schematic into a flat voxel grid that can be
 * rendered by {@link IsoRenderer}.
 *
 * <p>The file is gzip compressed big endian NBT. The layout (verified against real files and
 * against Litematica's own bytecode) is:
 *
 * <pre>
 * root
 *   Version              int      format version (7 for current files)
 *   SubVersion           int
 *   MinecraftDataVersion int
 *   Metadata             compound Name, Author, Description, RegionCount, TotalBlocks,
 *                                 TotalVolume, EnclosingSize{x,y,z}, TimeCreated, TimeModified
 *   Regions              compound of named region compounds
 *     &lt;name&gt;
 *       Position         compound x,y,z   (region corner, may be negative)
 *       Size             compound x,y,z   (side lengths, may be negative)
 *       BlockStatePalette list of compound { Name: "minecraft:stone", Properties: {...} }
 *       BlockStates      long[]           (or, in very old files, a list of longs)
 *       Entities / TileEntities / PendingBlockTicks / PendingFluidTicks
 * </pre>
 *
 * <p>Two details differ from vanilla's paletted containers and matter for decoding:
 *
 * <ul>
 *   <li>The voxel index is <b>YZX with X fastest</b>:
 *       {@code index = y * width * length + z * width + x}.
 *   <li>The bit packing is Litematica's own and entries may <b>span two longs</b> rather than
 *       being padded to long boundaries. Each entry starts at bit {@code (index * bits) & 63}
 *       of long {@code (index * bits) >> 6} and is read least significant bit first, taking the
 *       remaining bits from the next long when the entry crosses the boundary. The bit width is
 *       {@code max(2, ceilLog2(paletteSize))}, which is also exactly what makes the stored
 *       {@code long[]} length come out as {@code ceil(volume * bits / 64)}.
 * </ul>
 */
public final class LitematicaFile {
    /** Litematica writes format version 7 for all schematics saved since 1.13. */
    public static final int CURRENT_FORMAT_VERSION = 7;

    private static final long NBT_LIMIT_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_GRID_VOLUME = 6_000_000;
    private static final int MAX_GRID_DIMENSION = 8192;

    /** A block identifier together with how many times it occurs, used for the material summary. */
    public static final class Entry {
        public final String id;
        public final int count;

        Entry(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    public final String fileName;
    public final String displayName;
    public final String author;
    public final String description;
    public final int formatVersion;
    public final int formatSubVersion;
    public final int dataVersion;
    public final int declaredRegions;
    public final int declaredBlocks;
    public final int declaredVolume;

    /** Enclosing size from the metadata, falling back to the computed bounding box. */
    public final int sourceWidth;
    public final int sourceHeight;
    public final int sourceLength;

    /** Dimensions of the merged voxel grid. */
    public final int width;
    public final int height;
    public final int length;

    /** Voxel grid, indexed {@code (y * length + z) * width + x}. 0 means empty, else index into {@link #palette}. */
    public final int[] voxels;

    /** Index 0 is a placeholder for empty; every other entry is a block identifier. */
    public final List<String> palette;

    /**
     * Block state properties, index aligned with {@link #palette}. Entry 0 is empty.
     *
     * <p>Two states of the same block (say a north-facing and a south-facing stair) are distinct
     * palette entries here, because auto build has to place the exact state the schematic stores.
     * The preview is unaffected: it renders by block name, and materials are aggregated by name.
     */
    public final List<Map<String, String>> paletteProperties;

    /** Most common blocks, sorted by descending count. */
    public final List<Entry> materials;

    public final int blockCount;
    public final int regionCount;
    public final List<String> warnings;

    /** Origin of the merged grid in schematic space. */
    public final int originX;
    public final int originY;
    public final int originZ;

    private LitematicaFile(Builder builder) {
        this.fileName = builder.fileName;
        this.displayName = builder.displayName;
        this.author = builder.author;
        this.description = builder.description;
        this.formatVersion = builder.formatVersion;
        this.formatSubVersion = builder.formatSubVersion;
        this.dataVersion = builder.dataVersion;
        this.declaredRegions = builder.declaredRegions;
        this.declaredBlocks = builder.declaredBlocks;
        this.declaredVolume = builder.declaredVolume;
        this.sourceWidth = builder.sourceWidth;
        this.sourceHeight = builder.sourceHeight;
        this.sourceLength = builder.sourceLength;
        this.width = builder.width;
        this.height = builder.height;
        this.length = builder.length;
        this.voxels = builder.voxels;
        this.palette = List.copyOf(builder.palette);
        this.paletteProperties = List.copyOf(builder.paletteProperties);
        this.materials = List.copyOf(builder.materials);
        this.blockCount = builder.blockCount;
        this.regionCount = builder.regionCount;
        this.warnings = List.copyOf(builder.warnings);
        this.originX = builder.originX;
        this.originY = builder.originY;
        this.originZ = builder.originZ;
    }

    /** True when at least one block survived decoding. */
    public boolean isEmpty() {
        return this.blockCount == 0;
    }

    /**
     * Number of distinct block identifiers found in the grid. Counted by name rather than by
     * palette entry, since one block can appear in several states.
     */
    public int distinctBlocks() {
        return (int) this.palette.stream().skip(1).distinct().count();
    }

    /** Block state properties of the palette entry at a grid cell, or an empty map. */
    public Map<String, String> propertiesAt(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.height || z >= this.length) {
            return Map.of();
        }
        int value = this.voxels[(y * this.length + z) * this.width + x];
        return value <= 0 || value >= this.paletteProperties.size()
                ? Map.of() : this.paletteProperties.get(value);
    }

    public String identifierAt(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.height || z >= this.length) {
            return "minecraft:air";
        }
        int value = this.voxels[(y * this.length + z) * this.width + x];
        return value <= 0 || value >= this.palette.size() ? "minecraft:air" : this.palette.get(value);
    }

    // ------------------------------------------------------------------
    // Decoding
    // ------------------------------------------------------------------

    public static LitematicaFile read(Path path) throws IOException {
        NbtCompound root = readRoot(path);
        if (!root.contains("Regions")) {
            throw new IOException("Not a Litematica schematic (no Regions tag)");
        }

        Builder builder = new Builder();
        builder.fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();

        NbtCompound metadata = root.getCompoundOrEmpty("Metadata");
        builder.displayName = metadata.getString("Name", "");
        builder.author = metadata.getString("Author", "");
        builder.description = metadata.getString("Description", "");
        builder.declaredRegions = metadata.getInt("RegionCount", 0);
        builder.declaredBlocks = metadata.getInt("TotalBlocks", 0);
        builder.declaredVolume = metadata.getInt("TotalVolume", 0);

        builder.formatVersion = root.getInt("Version", 0);
        builder.formatSubVersion = root.getInt("SubVersion", 0);
        builder.dataVersion = root.getInt("MinecraftDataVersion", 0);

        if (builder.formatVersion > CURRENT_FORMAT_VERSION) {
            builder.warnings.add("Format v" + builder.formatVersion + " is newer than v"
                    + CURRENT_FORMAT_VERSION + " — decoding best effort");
        }

        // --- pass 1: geometry, so we can size the merged grid -------------------
        List<Region> regions = new ArrayList<>();
        for (NbtCompound tag : regionTags(root)) {
            Region region = readGeometry(tag, builder);
            if (region != null) {
                regions.add(region);
            }
        }
        if (regions.isEmpty()) {
            throw new IOException("Schematic has no usable regions");
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Region region : regions) {
            minX = Math.min(minX, region.minX);
            minY = Math.min(minY, region.minY);
            minZ = Math.min(minZ, region.minZ);
            maxX = Math.max(maxX, region.minX + region.width - 1);
            maxY = Math.max(maxY, region.minY + region.height - 1);
            maxZ = Math.max(maxZ, region.minZ + region.length - 1);
        }

        builder.width = maxX - minX + 1;
        builder.height = maxY - minY + 1;
        builder.length = maxZ - minZ + 1;

        NbtCompound enclosing = metadata.getCompoundOrEmpty("EnclosingSize");
        builder.sourceWidth = enclosing.contains("x") ? enclosing.getInt("x", builder.width) : builder.width;
        builder.sourceHeight = enclosing.contains("y") ? enclosing.getInt("y", builder.height) : builder.height;
        builder.sourceLength = enclosing.contains("z") ? enclosing.getInt("z", builder.length) : builder.length;

        long gridVolume = (long) builder.width * builder.height * builder.length;
        if (gridVolume > MAX_GRID_VOLUME
                || builder.width > MAX_GRID_DIMENSION
                || builder.height > MAX_GRID_DIMENSION
                || builder.length > MAX_GRID_DIMENSION) {
            throw new IOException("Schematic is too large to preview ("
                    + builder.width + "×" + builder.height + "×" + builder.length + ")");
        }

        // --- pass 2: voxels -----------------------------------------------------
        int[] grid = new int[(int) gridVolume];
        Map<String, Integer> paletteIndex = new HashMap<>();
        List<String> palette = new ArrayList<>();
        List<Map<String, String>> paletteProperties = new ArrayList<>();
        palette.add("minecraft:air");
        paletteProperties.add(Map.of());
        Map<String, Integer> counts = new HashMap<>();

        int[] decoded = new int[1];
        for (Region region : regions) {
            decodeRegion(region, grid, builder, paletteIndex, palette, paletteProperties, counts,
                    decoded, minX, minY, minZ);
        }
        builder.voxels = grid;
        builder.palette = palette;
        builder.paletteProperties = paletteProperties;
        builder.blockCount = decoded[0];
        builder.regionCount = regions.size();
        builder.originX = minX;
        builder.originY = minY;
        builder.originZ = minZ;

        if (builder.declaredBlocks > 0 && builder.declaredBlocks != decoded[0]) {
            builder.warnings.add("Decoded " + decoded[0] + " blocks, metadata says " + builder.declaredBlocks);
        }

        List<Entry> materials = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            materials.add(new Entry(entry.getKey(), entry.getValue()));
        }
        materials.sort(Comparator.comparingInt((Entry entry) -> entry.count).reversed()
                .thenComparing(entry -> entry.id));
        builder.materials = materials;

        return new LitematicaFile(builder);
    }

    /** Origin of the grid in schematic space; useful for reporting but not needed to render. */
    public int[] gridOrigin() {
        return new int[] {this.originX, this.originY, this.originZ};
    }

    private static NbtCompound readRoot(Path path) throws IOException {
        try {
            return NbtIo.readCompressed(path, NbtSizeTracker.of(NBT_LIMIT_BYTES));
        } catch (IOException gzipFailure) {
            try {
                return NbtIo.read(path);
            } catch (IOException plainFailure) {
                plainFailure.addSuppressed(gzipFailure);
                throw plainFailure;
            }
        }
    }

    private static List<NbtCompound> regionTags(NbtCompound root) {
        List<NbtCompound> tags = new ArrayList<>();
        NbtCompound compound = root.getCompound("Regions").orElse(null);
        if (compound != null) {
            for (String key : new TreeSet<>(compound.getKeys())) {
                tags.add(compound.getCompoundOrEmpty(key));
            }
            return tags;
        }
        NbtList list = root.getList("Regions").orElse(null);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                tags.add(list.getCompoundOrEmpty(i));
            }
        }
        return tags;
    }

    private static Region readGeometry(NbtCompound tag, Builder builder) {
        int[] position = readVector(tag.getCompoundOrEmpty("Position"));
        int[] size = readVector(tag.getCompoundOrEmpty("Size"));

        int width = Math.abs(size[0]);
        int height = Math.abs(size[1]);
        int length = Math.abs(size[2]);
        if (width <= 0 || height <= 0 || length <= 0) {
            builder.warnings.add("Skipped a region with an empty size");
            return null;
        }
        long volume = (long) width * height * length;
        if (volume > MAX_GRID_VOLUME) {
            builder.warnings.add("Skipped a region larger than the preview limit (" + volume + " blocks)");
            return null;
        }

        Region region = new Region();
        region.tag = tag;
        region.width = width;
        region.height = height;
        region.length = length;
        // A negative side length means the region grows towards the negative axis.
        region.minX = position[0] + Math.min(0, size[0] + 1);
        region.minY = position[1] + Math.min(0, size[1] + 1);
        region.minZ = position[2] + Math.min(0, size[2] + 1);
        return region;
    }

    private static void decodeRegion(Region region, int[] grid, Builder builder,
                                     Map<String, Integer> paletteIndex, List<String> palette,
                                     List<Map<String, String>> paletteProperties,
                                     Map<String, Integer> counts, int[] decoded,
                                     int gridMinX, int gridMinY, int gridMinZ) {
        NbtCompound tag = region.tag;
        NbtList paletteTag = tag.getListOrEmpty("BlockStatePalette");
        int paletteSize = paletteTag.size();
        if (paletteSize <= 0) {
            builder.warnings.add("Skipped a region with no block palette");
            return;
        }

        int[] regionIds = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            NbtCompound stateTag = paletteTag.getCompoundOrEmpty(i);
            String name = stateTag.getString("Name", "minecraft:air");
            regionIds[i] = isAir(name) ? 0
                    : paletteId(name, readProperties(stateTag), paletteIndex, palette, paletteProperties);
        }

        long[] storage = readStorage(tag, builder);
        if (storage == null) {
            return;
        }

        int bits = Math.max(2, ceilLog2(paletteSize));
        long expectedLongs = ((long) region.width * region.height * region.length * bits + 63L) / 64L;
        if (storage.length < expectedLongs) {
            builder.warnings.add("Region data is short (" + storage.length + " of " + expectedLongs + " longs)");
        }

        int layer = region.width * region.length;
        for (int y = 0; y < region.height; y++) {
            for (int z = 0; z < region.length; z++) {
                for (int x = 0; x < region.width; x++) {
                    int index = y * layer + z * region.width + x;
                    int value = getPacked(storage, index, bits);
                    if (value < 0 || value >= paletteSize) {
                        continue;
                    }
                    int id = regionIds[value];
                    if (id == 0) {
                        continue;
                    }
                    int gx = region.minX + x - gridMinX;
                    int gy = region.minY + y - gridMinY;
                    int gz = region.minZ + z - gridMinZ;
                    if (gx < 0 || gy < 0 || gz < 0 || gx >= builder.width || gy >= builder.height
                            || gz >= builder.length) {
                        continue;
                    }
                    grid[(gy * builder.length + gz) * builder.width + gx] = id;
                    counts.merge(palette.get(id), 1, Integer::sum);
                    decoded[0]++;
                }
            }
        }
    }

    private static int paletteId(String name, Map<String, String> properties,
                                 Map<String, Integer> index, List<String> palette,
                                 List<Map<String, String>> paletteProperties) {
        String key = stateKey(name, properties);
        Integer existing = index.get(key);
        if (existing != null) {
            return existing;
        }
        int id = palette.size();
        palette.add(name);
        paletteProperties.add(properties.isEmpty() ? Map.of() : Map.copyOf(properties));
        index.put(key, id);
        return id;
    }

    /** Properties from a palette entry's {@code Properties} compound, empty when there are none. */
    private static Map<String, String> readProperties(NbtCompound stateTag) {
        NbtCompound tag = stateTag.getCompound("Properties").orElse(null);
        if (tag == null) {
            return Map.of();
        }
        Map<String, String> properties = new HashMap<>();
        for (String key : tag.getKeys()) {
            String value = tag.getString(key, "");
            if (!value.isEmpty()) {
                properties.put(key, value);
            }
        }
        return properties;
    }

    /** Interning key for a block state: the name plus its properties in a stable order. */
    static String stateKey(String name, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return name;
        }
        List<String> keys = new ArrayList<>(properties.keySet());
        keys.sort(null);
        StringBuilder builder = new StringBuilder(name);
        for (String key : keys) {
            builder.append('|').append(key).append('=').append(properties.get(key));
        }
        return builder.toString();
    }

    private static long[] readStorage(NbtCompound tag, Builder builder) {
        long[] direct = tag.getLongArray("BlockStates").orElse(null);
        if (direct != null) {
            return direct;
        }
        NbtList list = tag.getList("BlockStates").orElse(null);
        if (list == null) {
            builder.warnings.add("Skipped a region with no BlockStates");
            return null;
        }
        long[] values = new long[list.size()];
        for (int i = 0; i < values.length; i++) {
            NbtElement element = (NbtElement) list.get(i);
            values[i] = element == null ? 0L : element.asLong().orElse(0L);
        }
        return values;
    }

    /**
     * Unpacks one palette index out of Litematica's bit array. Entries may span two longs and are
     * stored least significant bit first: {@code ((longs[start] >>> offset) | (longs[end] << (64 - offset))) & mask}.
     */
    static int getPacked(long[] storage, int index, int bits) {
        long bitIndex = (long) index * bits;
        int startArrIndex = (int) (bitIndex >> 6);
        int startOffset = (int) (bitIndex & 0x3FL);
        int endArrIndex = (int) ((((long) index + 1L) * bits - 1L) >> 6);
        if (startArrIndex < 0 || endArrIndex >= storage.length || startArrIndex >= storage.length) {
            return 0;
        }
        long mask = (1L << bits) - 1L;
        if (startArrIndex == endArrIndex) {
            return (int) ((storage[startArrIndex] >>> startOffset) & mask);
        }
        int endOffset = 64 - startOffset;
        return (int) ((storage[startArrIndex] >>> startOffset | storage[endArrIndex] << endOffset) & mask);
    }

    static int ceilLog2(int value) {
        return value <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    /** Litematica stores air as a normal palette entry, so it has to be filtered out explicitly. */
    public static boolean isAir(String name) {
        return "minecraft:air".equals(name)
                || "minecraft:cave_air".equals(name)
                || "minecraft:void_air".equals(name);
    }

    private static int[] readVector(NbtCompound compound) {
        return new int[] {
                compound.getInt("x", 0),
                compound.getInt("y", 0),
                compound.getInt("z", 0)
        };
    }

    private static final class Region {
        NbtCompound tag;
        int width;
        int height;
        int length;
        int minX;
        int minY;
        int minZ;
    }

    private static final class Builder {
        String fileName = "";
        String displayName = "";
        String author = "";
        String description = "";
        int formatVersion;
        int formatSubVersion;
        int dataVersion;
        int declaredRegions;
        int declaredBlocks;
        int declaredVolume;
        int sourceWidth;
        int sourceHeight;
        int sourceLength;
        int width;
        int height;
        int length;
        int[] voxels = new int[0];
        List<String> palette = new ArrayList<>();
        List<Map<String, String>> paletteProperties = new ArrayList<>();
        List<Entry> materials = new ArrayList<>();
        int blockCount;
        int regionCount;
        int originX;
        int originY;
        int originZ;
        final List<String> warnings = new ArrayList<>();
    }
}
