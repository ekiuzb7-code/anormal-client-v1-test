package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class XRay extends Module {
    public final NumberSetting opacity = new NumberSetting("Opacity", "Opacity of non-ore blocks", 20.0, 0.0, 100.0, 5.0);
    public final BooleanSetting diamond = new BooleanSetting("Diamond", "Highlights Diamond Ore", true);
    public final BooleanSetting netherite = new BooleanSetting("Ancient Debris", "Highlights Ancient Debris", true);
    public final BooleanSetting gold = new BooleanSetting("Gold", "Highlights Gold Ore", true);
    public final BooleanSetting iron = new BooleanSetting("Iron", "Highlights Iron Ore", true);
    public final BooleanSetting coal = new BooleanSetting("Coal", "Highlights Coal Ore", false);
    public final BooleanSetting emerald = new BooleanSetting("Emerald", "Highlights Emerald Ore", true);
    public final BooleanSetting redstone = new BooleanSetting("Redstone", "Highlights Redstone Ore", false);
    public final BooleanSetting lapis = new BooleanSetting("Lapis", "Highlights Lapis Ore", false);
    public final BooleanSetting chests = new BooleanSetting("Chests", "Highlights Chests & Spawners", true);

    public XRay() {
        super("XRay", "Makes common blocks transparent to easily locate underground ores", Category.WORLD);
        addSetting(opacity);
        addSetting(diamond);
        addSetting(netherite);
        addSetting(gold);
        addSetting(iron);
        addSetting(coal);
        addSetting(emerald);
        addSetting(redstone);
        addSetting(lapis);
        addSetting(chests);
    }

    public boolean isVisibleBlock(Block block) {
        if (diamond.isEnabled() && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
        if (netherite.isEnabled() && block == Blocks.ANCIENT_DEBRIS) return true;
        if (gold.isEnabled() && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE)) return true;
        if (iron.isEnabled() && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)) return true;
        if (coal.isEnabled() && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)) return true;
        if (emerald.isEnabled() && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)) return true;
        if (redstone.isEnabled() && (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)) return true;
        if (lapis.isEnabled() && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)) return true;
        if (chests.isEnabled() && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL || block == Blocks.SPAWNER)) return true;
        return false;
    }

    @Override
    public void onEnable() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }
}
