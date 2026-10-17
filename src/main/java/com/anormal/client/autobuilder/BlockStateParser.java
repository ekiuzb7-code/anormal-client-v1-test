package com.anormal.client.autobuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * Converts between the textual block state notation used by schematics
 * ({@code minecraft:oak_stairs[facing=east,half=bottom]}) and {@link BlockState}.
 *
 * <p>Written by hand instead of using {@code BlockArgumentParser} so the behaviour stays
 * stable across mappings and so unknown blocks can be reported instead of silently becoming
 * air.</p>
 */
public final class BlockStateParser {

    private BlockStateParser() {
    }

    /** @return the parsed state, or {@code null} when the block id is unknown. */
    public static BlockState parse(String input) {
        if (input == null) return null;
        String text = input.trim();
        if (text.isEmpty()) return null;

        int bracket = text.indexOf('[');
        String idPart = bracket < 0 ? text : text.substring(0, bracket);

        Identifier id = Identifier.tryParse(normalise(idPart));
        if (id == null || !Registries.BLOCK.containsId(id)) return null;

        Block block = Registries.BLOCK.get(id);
        BlockState state = block.getDefaultState();

        if (bracket < 0) return state;

        int end = text.lastIndexOf(']');
        if (end <= bracket) return state;

        String body = text.substring(bracket + 1, end);
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            state = apply(state, key, value);
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState apply(BlockState state, String key, String value) {
        Property<?> property = state.getBlock().getStateManager().getProperty(key);
        if (property == null) return state;
        Optional<?> parsed = ((Property) property).parse(value);
        if (parsed.isEmpty()) return state;
        return state.with((Property) property, (Comparable) parsed.get());
    }

    private static String normalise(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** Serialises a state back into the same notation, properties sorted by name. */
    public static String format(BlockState state) {
        if (state == null) return "minecraft:air";
        StringBuilder sb = new StringBuilder(Registries.BLOCK.getId(state.getBlock()).toString());
        Map<Property<?>, Comparable<?>> entries = state.getEntries();
        if (!entries.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<Property<?>, Comparable<?>> entry : entries.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(entry.getKey().getName()).append('=').append(nameOf(entry.getKey(), entry.getValue()));
            }
            sb.append(']');
        }
        return sb.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String nameOf(Property<?> property, Comparable<?> value) {
        return ((Property) property).name((Comparable) value);
    }

    /** A short, human readable label used by the materials table. */
    public static String shortName(BlockState state) {
        if (state == null) return "air";
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return path.replace('_', ' ');
    }
}
