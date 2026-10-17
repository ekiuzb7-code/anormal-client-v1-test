package com.anormal.client.autobuilder;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
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
            warnings.add("Orientation not transformed for property '" + property + "'");
        }

        return Schematic.of(source.name, source.format, blocks, entities, warnings,
                source.regionCount, source.paletteSize);
    }

    private static BlockState transformState(BlockState state, int steps, boolean mirrorX, boolean mirrorZ,
                                             Set<String> skipped) {
        if (steps == 0 && !mirrorX && !mirrorZ) return state;

        BlockState out = state;

        for (Property<?> property : state.getProperties()) {
            if (property.getType() == Direction.class) {
                Direction direction = readDirection(state, property);
                if (direction == null) continue;
                Direction moved = transformDirection(direction, steps, mirrorX, mirrorZ);
                out = set(out, property, moved);
            } else if (property == Properties.AXIS) {
                Direction.Axis axis = state.get(Properties.AXIS);
                if (steps % 2 == 1) {
                    axis = switch (axis) {
                        case X -> Direction.Axis.Z;
                        case Z -> Direction.Axis.X;
                        default -> axis;
                    };
                }
                out = set(out, Properties.AXIS, axis);
            } else if (property == Properties.ROTATION) {
                int rotation = state.get(Properties.ROTATION);
                if (mirrorX) rotation = (16 - rotation) & 15;
                if (mirrorZ) rotation = (8 - rotation) & 15;
                rotation = (rotation + steps * 4) & 15;
                out = set(out, Properties.ROTATION, rotation);
            } else if (isOrientationProperty(property)) {
                skipped.add(property.getName());
            }
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
