package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Worth Display — shows each item's DonutSMP "worth" in its hover tooltip
 * (inventory screens, hotbar, chests, etc.), client-side only.
 *
 * <h3>What "worth" means here</h3>
 * The server's own pricing: the fixed per-item /sell base price at the default
 * 1.0x multiplier, plus the fixed Money Shop prices for End/combat items.
 * Player-market (Auction House / Discord) prices fluctuate, so they are not
 * baked in — the tooltip shows the same stable number the server uses.
 * Spawners are shown in Shards (1,500 from the Shard Shop, the price the
 * wiki publishes).
 *
 * <h3>Customising values</h3>
 * Overrides live in {@code config/lucidclient/donut_worth.json} (part of the
 * normal Minecraft config dir), e.g.:
 * <pre>
 * {
 *   "minecraft:diamond": 1200,
 *   "minecraft:netherite_ingot": 7000000,
 *   "spawner_shards": 1500
 * }
 * </pre>
 * Any entry replaces the bundled default for that item id. Add items the
 * bundled table doesn't know and they'll start showing tooltips too.
 *
 * <h3>Server scope</h3>
 * DonutSMP-only (matches the FakePay/NameProtect gate): the tooltip suffix only
 * renders when the current server name/address contains "donut".
 */
public class WorthDisplay extends Module {

    // ── Static state read by the ItemStackMixin tooltip hook. A static flag
    //    lets the mixin no-op cheaply when the module is off.
    private static volatile boolean active = false;

    // ── Bundled worth table: confirmed DonutSMP prices (wiki + Money Shop).
    //    /sell base price per item at 1.0x multiplier.
    private static final Map<String, Long> WORTH = new HashMap<>();
    private static long spawnerShards = 1500L;
    private static volatile boolean worthLoaded = false;

    public WorthDisplay() {
        super("Worth Display",
            "Shows each item's DonutSMP worth in its hover tooltip (client-side).",
            Category.DONUT);
    }

    static {
        // /sell base prices at 1.0x.
        WORTH.put("minecraft:oak_log", 300L);
        WORTH.put("minecraft:sand", 100L);
        WORTH.put("minecraft:diamond", 1_200L);
        WORTH.put("minecraft:nether_gold_ore", 1_200L);
        WORTH.put("minecraft:leather", 10_000L);
        WORTH.put("minecraft:bamboo_block", 500L);
        WORTH.put("minecraft:dried_kelp_block", 550L);
        WORTH.put("minecraft:ancient_debris", 1_700_000L);
        // Money Shop fixed prices.
        WORTH.put("minecraft:ender_chest", 2_500L);
        WORTH.put("minecraft:ender_pearl", 75L);
        WORTH.put("minecraft:end_stone", 8L);
        WORTH.put("minecraft:dragon_breath", 1_000L);
        WORTH.put("minecraft:end_rod", 100L);
        WORTH.put("minecraft:chorus_fruit", 108L);
        WORTH.put("minecraft:popped_chorus_fruit", 28L);
        WORTH.put("minecraft:shulker_shell", 350L);
        WORTH.put("minecraft:shulker_box", 800L);
        WORTH.put("minecraft:totem_of_undying", 1_500L);
        WORTH.put("minecraft:end_crystal", 350L);
        WORTH.put("minecraft:respawn_anchor", 1_000L);
        WORTH.put("minecraft:glowstone", 100L);
        WORTH.put("minecraft:experience_bottle", 75L);
        WORTH.put("minecraft:obsidian", 100L);
    }

    @Override
    protected void onEnable() {
        active = true;
        worthLoaded = false;  // pick up any config edits on the next hover
    }

    @Override
    protected void onDisable() {
        active = false;
    }

    public static boolean isActive() { return active; }

    /** True when the current server looks like DonutSMP (same gate as FakePay). */
    public static boolean isOnDonutSMP() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.getCurrentServerEntry() == null) return false;
            ServerInfo info = c.getCurrentServerEntry();
            String addr = info.address != null ? info.address.toLowerCase(Locale.ROOT) : "";
            String name = info.name != null ? info.name.toLowerCase(Locale.ROOT) : "";
            return addr.contains("donut") || name.contains("donut");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Append DonutSMP worth line(s) to an item stack's tooltip lines. Called
     * from the {@code getTooltip} tail-inject mixin. No-op off-server or when
     * the item has no known worth.
     */
    public static void appendWorthLines(List<Text> lines, ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) return;
            ensureWorthLoaded();
            if (stack.getItem() == Items.SPAWNER) {
                lines.add(Text.literal("Worth: " + fmt(spawnerShards) + " Shards (Shard Shop)")
                    .formatted(Formatting.GOLD));
                return;
            }
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id == null) return;
            Long w = WORTH.get(id.toString());
            if (w == null || w <= 0L) return;
            MutableText line = Text.literal("Worth: $" + fmt(w)).formatted(Formatting.GOLD);
            int count = stack.getCount();
            if (count > 1) {
                line.append(Text.literal(" ($" + fmt(w * count) + " / stack)")
                    .formatted(Formatting.YELLOW));
            }
            lines.add(line);
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid WorthDisplay appendWorthLines threw", t);
        }
    }

    /** Comma-grouped number formatting: 1234567 → "1,234,567". */
    private static String fmt(long v) {
        return String.format("%,d", v);
    }

    /**
     * Merge {@code config/lucidclient/donut_worth.json} overrides into the
     * bundled table once. Any key not in the file keeps its default.
     */
    private static void ensureWorthLoaded() {
        if (worthLoaded) return;
        worthLoaded = true;
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.runDirectory == null) return;
            Path cfg = c.runDirectory.toPath().resolve("config").resolve("lucidclient")
                .resolve("donut_worth.json");
            if (!Files.exists(cfg)) return;
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseReader(
                Files.newBufferedReader(cfg)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                try {
                    String key = e.getKey();
                    long v = e.getValue().getAsLong();
                    if (key.equals("spawner_shards")) {
                        if (v > 0) spawnerShards = v;
                    } else {
                        WORTH.put(key, v);
                    }
                } catch (Throwable ignored) {
                    // Skip malformed entries (non-numeric value, etc.).
                }
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.warn("Lucid WorthDisplay could not load donut_worth.json", t);
        }
    }
}