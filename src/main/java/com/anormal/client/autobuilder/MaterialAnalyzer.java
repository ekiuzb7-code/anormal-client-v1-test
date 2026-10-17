package com.anormal.client.autobuilder;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out how many of each item a schematic needs, how much of that is already standing in
 * the world, and how much the player is carrying.
 *
 * <p>Counting is per <em>item</em>, not per coordinate: a double slab block consumes two slab
 * items, and two different block states that share one inventory item are merged into a single
 * row.</p>
 */
public final class MaterialAnalyzer {

    private MaterialAnalyzer() {
    }

    public static final class Row {
        public final Item item;
        public final String label;

        public int required;
        public int satisfied;
        public int hotbar;
        public int inventory;

        Row(Item item, String label) {
            this.item = item;
            this.label = label;
        }

        public int remaining() {
            return Math.max(0, required - satisfied);
        }

        public int missing() {
            return Math.max(0, remaining() - hotbar - inventory);
        }

        public boolean isComplete() {
            return missing() == 0;
        }
    }

    public static final class Result {
        public final List<Row> rows = new ArrayList<>();
        public int totalRequired;
        public int totalSatisfied;
        public int totalRemaining;
        public int totalMissing;
        public int unresolved;
        public int blockCount;
        public boolean worldAvailable;
    }

    /** Item stacks consumed by one block of this state. */
    public static int itemsPerBlock(BlockState state) {
        try {
            if (state.contains(Properties.SLAB_TYPE)
                    && state.get(Properties.SLAB_TYPE) == SlabType.DOUBLE) {
                return 2;
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

    public static Result analyze(Schematic schematic, BlockPos origin) {
        Result result = new Result();
        if (schematic == null) return result;

        MinecraftClient mc = MinecraftClient.getInstance();
        result.worldAvailable = mc.world != null && origin != null;
        result.blockCount = schematic.blockCount();

        Map<Item, Row> rows = new HashMap<>();

        for (Map.Entry<BlockPos, BlockState> entry : schematic.blocks.entrySet()) {
            BlockState desired = entry.getValue();
            int per = itemsPerBlock(desired);

            boolean alreadyThere = false;
            if (result.worldAvailable) {
                try {
                    BlockPos worldPos = origin.add(entry.getKey());
                    alreadyThere = mc.world.getBlockState(worldPos).equals(desired);
                } catch (Throwable ignored) {
                }
            }

            Item item;
            try {
                item = desired.getBlock().asItem();
            } catch (Throwable t) {
                item = Items.AIR;
            }

            if (item == Items.AIR) {
                result.unresolved += per;
                continue;
            }

            Row row = rows.computeIfAbsent(item, key -> new Row(key, key.getName().getString()));
            row.required += per;
            if (alreadyThere) row.satisfied += per;
        }

        // ---- what the player is carrying ---------------------------------
        if (mc.player != null) {
            PlayerInventory inventory = mc.player.getInventory();
            int hotbarSize = Math.max(1, inventory.getHotbarSize());
            int size = Math.min(36, inventory.size());
            for (int slot = 0; slot < size; slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                Row row = rows.get(stack.getItem());
                if (row == null) continue;
                if (slot < hotbarSize) row.hotbar += stack.getCount();
                else row.inventory += stack.getCount();
            }
        }

        result.rows.addAll(rows.values());
        result.rows.sort(Comparator
                .comparingInt((Row r) -> r.missing() > 0 ? 0 : 1)
                .thenComparing(Comparator.comparingInt((Row r) -> r.required).reversed())
                .thenComparing(r -> r.label));

        for (Row row : result.rows) {
            result.totalRequired += row.required;
            result.totalSatisfied += row.satisfied;
            result.totalRemaining += row.remaining();
            result.totalMissing += row.missing();
        }

        return result;
    }
}
