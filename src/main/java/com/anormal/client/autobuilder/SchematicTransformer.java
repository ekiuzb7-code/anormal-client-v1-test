package com.anormal.client.autobuilder;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies horizontal rotation and mirroring to a schematic.
 *
 * <p>The transform is applied to the block positions <em>and</em> to the directional block
 * states, so stairs, logs, observers, pistons, ladders and similar blocks keep pointing the
 * right way after a rotation. The result is a brand new {@link Schematic}; the input is never
 * mutated, which keeps repeated rotations deterministic instead of accumulating error.</p>
 *
 * <p>Order of operations: mirror X, then mirror Z, then rotate clockwise. Rotating a point
 * {@code (x, z)} by 90 degrees uses {@code (x, z) -> (-z, x)}.</p>
 */
public final class SchematicTransformer {

    private SchematicTransformer() {
    }

    public static Schematic apply(Schematic source, int rotationDegrees, boolean mirrorX, boolean mirrorZ) {
        int steps = Math.floorMod(rotationDegrees / 90, 4);
        if (steps == 0 && !mirrorX && !mirrorZ) return source;

        List<String> warnings = new ArrayList<>(source.warnings);
        Set<String> skipped = new HashSet<>();

        Map<BlockPos, BlockState> blocks = new HashMap<>(Math.max(16, source.blocks.size() * 2));

        for (Map.Entry<BlockPos, BlockState> entry : source.blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = transformState(entry.getValue(), steps, mirrorX, mirrorZ, skipped);

            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            if (mirrorX) x = -x;
            if (mirrorZ) z = -z;

            for (int i = 0; i < steps; i++) {
                int nx = -z;
                int nz = x;
                x = nx;
                z = nz;
            }

            blocks.put(new BlockPos(x, y, z), state);
        }

        Map<BlockPos, NbtCompound> entities = new HashMap<>();
        for (Map.Entry<BlockPos, NbtCompound> entry : source.blockEntities.entrySet()) {
            BlockPos pos = entry.getKey();
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            if (mirrorX) x = -x;
            if (mirrorZ) z = -z;

            for (int i = 0; i < steps; i++) {
                int nx = -z;
                int nz = x;
                x = nx;
                z = nz;
            }
            entities.put(new BlockPos(x, y, z), entry.getValue());
        }

        for (String property : skipped) {
            warnings.add("Could not transform block: " + property);
        }

        return Schematic.of(source.name, source.format, blocks, entities, warnings,
                source.regionCount, source.paletteSize);
    }

    /**
     * Uses Minecraft's own {@code BlockState.mirror/rotate}, the same code structure blocks and
     * WorldEdit style tools rely on. That correctly handles stairs (shape), doors (hinge), fences,
     * walls, panes, vines, rails, beds, banners, signs, redstone parts and every modded block that
     * implements the vanilla hooks. The previous hand written version left every block with
     * north/south/east/west/up/down or shape properties unrotated.
     */
    private static BlockState transformState(BlockState state, int steps, boolean mirrorX, boolean mirrorZ,
                                             Set<String> skipped) {
        if (steps == 0 && !mirrorX && !mirrorZ) return state;

        BlockState out = state;
        try {
            if (mirrorX) out = out.mirror(BlockMirror.FRONT_BACK);   // x -> -x
            if (mirrorZ) out = out.mirror(BlockMirror.LEFT_RIGHT);   // z -> -z
            if (steps != 0) out = out.rotate(BlockRotation.values()[steps]); // 1=90, 2=180, 3=270 clockwise
        } catch (Throwable t) {
            skipped.add(state.getBlock().getTranslationKey());
            return state;
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Direction readDirection(BlockState state, Property<?> property) {
        Object value = state.get((Property) property);
        return value instanceof Direction direction ? direction : null;
    }

    /** Properties whose values encode a shape that a 90 degree rotation cannot express. */
    private static boolean isOrientationProperty(Property<?> property) {
        String name = property.getName();
        return name.equals("shape") || name.equals("north") || name.equals("south")
                || name.equals("east") || name.equals("west") || name.equals("up")
                || name.equals("down") || name.equals("orientation")
                || name.equals("rotation") || name.equals("axis");
    }

    private static Direction transformDirection(Direction direction, int steps, boolean mirrorX, boolean mirrorZ) {
        int x = direction.getOffsetX();
        int y = direction.getOffsetY();
        int z = direction.getOffsetZ();

        if (mirrorX) x = -x;
        if (mirrorZ) z = -z;

        for (int i = 0; i < steps; i++) {
            int nx = -z;
            int nz = x;
            x = nx;
            z = nz;
        }

        for (Direction candidate : Direction.values()) {
            if (candidate.getOffsetX() == x && candidate.getOffsetY() == y && candidate.getOffsetZ() == z) {
                return candidate;
            }
        }
        return direction;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState set(BlockState state, Property property, Comparable value) {
        return state.with(property, value);
    }
}
