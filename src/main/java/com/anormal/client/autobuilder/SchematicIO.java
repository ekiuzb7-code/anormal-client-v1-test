package com.anormal.client.autobuilder;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entry point for reading schematic files.
 *
 * <p>Formats are detected from the file <em>structure</em> first and only fall back to the
 * extension when the structure is ambiguous, so a renamed file still loads correctly.</p>
 */
public final class SchematicIO {

    /** Refuse absurd files outright instead of exhausting memory. */
    public static final long MAX_FILE_BYTES = 96L * 1024 * 1024;
    private static final long MAX_NBT_BYTES = 256L * 1024 * 1024;

    private SchematicIO() {
    }

    public interface Loader {
        String formatName();

        /** Structural detection, never based on the file name. */
        boolean matches(NbtCompound root);

        Schematic load(String name, NbtCompound root, List<String> warnings) throws SchematicException;
    }

    public static final class SchematicException extends Exception {
        public SchematicException(String message) {
            super(message);
        }
    }

    private static final List<Loader> LOADERS = List.of(
            new com.anormal.client.autobuilder.loader.LitematicaLoader(),
            new com.anormal.client.autobuilder.loader.SpongeLoader(),
            new com.anormal.client.autobuilder.loader.LegacyLoader()
    );

    public static Schematic load(Path file) throws SchematicException {
        if (file == null) throw new SchematicException("No file selected");
        if (!Files.isRegularFile(file)) throw new SchematicException("File not found: " + file.getFileName());

        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new SchematicException("Cannot read file: " + e.getMessage());
        }
        if (size > MAX_FILE_BYTES) {
            throw new SchematicException("File is too large (" + (size / 1024 / 1024) + " MB, limit "
                    + (MAX_FILE_BYTES / 1024 / 1024) + " MB)");
        }

        NbtCompound root = readNbt(file);
        String name = stripExtension(file.getFileName().toString());

        for (Loader loader : LOADERS) {
            boolean matches;
            try {
                matches = loader.matches(root);
            } catch (Throwable t) {
                matches = false;
            }
            if (!matches) continue;

            List<String> warnings = new ArrayList<>();
            Schematic schematic = loader.load(name, root, warnings);
            if (schematic == null) continue;
            if (schematic.isEmpty() && warnings.isEmpty()) {
                throw new SchematicException("The schematic contains no blocks");
            }
            return schematic;
        }

        throw new SchematicException("Unsupported schematic format (expected .schem, .schematic or .litematic)");
    }

    private static NbtCompound readNbt(Path file) throws SchematicException {
        try {
            return NbtIo.readCompressed(file, NbtSizeTracker.of(MAX_NBT_BYTES));
        } catch (Throwable compressedFailure) {
            try {
                return NbtIo.read(file);
            } catch (Throwable rawFailure) {
                throw new SchematicException("Corrupted or unreadable schematic: " + rawFailure.getMessage());
            }
        }
    }

    public static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    // ------------------------------------------------------------------
    //  Small NBT helpers shared by the loaders
    // ------------------------------------------------------------------

    /** Reads an integer that may be stored as either INT or SHORT. */
    public static int intOrShort(NbtCompound tag, String key, int fallback) {
        Optional<Integer> asInt = tag.getInt(key);
        if (asInt.isPresent()) return asInt.get();
        Optional<Short> asShort = tag.getShort(key);
        if (asShort.isPresent()) return asShort.get() & 0xFFFF;
        return fallback;
    }

    public static byte[] byteArray(NbtCompound tag, String key) {
        return tag.getByteArray(key).orElse(null);
    }

    public static long[] longArray(NbtCompound tag, String key) {
        return tag.getLongArray(key).orElse(null);
    }

    public static int[] intArray(NbtCompound tag, String key) {
        return tag.getIntArray(key).orElse(null);
    }

    public static NbtList list(NbtCompound tag, String key) {
        return tag.getListOrEmpty(key);
    }

    public static boolean hasList(NbtCompound tag, String key) {
        return tag.get(key) instanceof NbtList;
    }
}
