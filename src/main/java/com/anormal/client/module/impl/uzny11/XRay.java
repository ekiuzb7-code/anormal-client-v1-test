package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XRay extends Module {
    public final BooleanSetting realXray = new BooleanSetting("Real XRay", "Hide non-selected blocks via mixin", true);
    public final BooleanSetting markers = new BooleanSetting("Markers", "Draw boxes on selected blocks", true);
    public final NumberSetting opacity = new NumberSetting("Opacity", "Opacity of hidden blocks", 0.0, 0.0, 100.0, 5.0);
    public final BooleanSetting onlyExposed = new BooleanSetting("Only Exposed", "Only show exposed blocks", false);
    public final NumberSetting markerRange = new NumberSetting("Marker Range", "Marker scan radius", 24.0, 4.0, 48.0, 1.0);
    public final NumberSetting gamma = new NumberSetting("Gamma", "Fullbright gamma", 16.0, 1.0, 16.0, 1.0);

    private final Set<Block> selectedBlocks = new HashSet<>();
    private final List<BlockPos> cache = new java.util.ArrayList<>();
    private int ticks = 0;
    private double savedGamma = 1.0;

    public XRay() {
        super("XRay", "Highlights blocks through walls", Category.UZNY11);
        addSetting(realXray);
        addSetting(markers);
        addSetting(opacity);
        addSetting(onlyExposed);
        addSetting(markerRange);
        addSetting(gamma);
        selectAllOres();
    }

    private void selectAllOres() {
        selectedBlocks.clear();
        addBlock(Blocks.DIAMOND_ORE);
        addBlock(Blocks.DEEPSLATE_DIAMOND_ORE);
        addBlock(Blocks.ANCIENT_DEBRIS);
        addBlock(Blocks.GOLD_ORE);
        addBlock(Blocks.DEEPSLATE_GOLD_ORE);
        addBlock(Blocks.IRON_ORE);
        addBlock(Blocks.DEEPSLATE_IRON_ORE);
        addBlock(Blocks.EMERALD_ORE);
        addBlock(Blocks.DEEPSLATE_EMERALD_ORE);
        addBlock(Blocks.LAPIS_ORE);
        addBlock(Blocks.DEEPSLATE_LAPIS_ORE);
        addBlock(Blocks.REDSTONE_ORE);
        addBlock(Blocks.DEEPSLATE_REDSTONE_ORE);
        addBlock(Blocks.COAL_ORE);
        addBlock(Blocks.DEEPSLATE_COAL_ORE);
        addBlock(Blocks.COPPER_ORE);
        addBlock(Blocks.DEEPSLATE_COPPER_ORE);
        addBlock(Blocks.NETHER_GOLD_ORE);
        addBlock(Blocks.NETHER_QUARTZ_ORE);

        // Storage
        addBlock(Blocks.CHEST);
        addBlock(Blocks.TRAPPED_CHEST);
        addBlock(Blocks.ENDER_CHEST);
        addBlock(Blocks.BARREL);
        addBlock(Blocks.SHULKER_BOX);

        // Spawners
        addBlock(Blocks.SPAWNER);
        addBlock(Blocks.TRIAL_SPAWNER);

        // Fluids
        addBlock(Blocks.WATER);
        addBlock(Blocks.LAVA);

        // Valuable
        addBlock(Blocks.DIAMOND_BLOCK);
        addBlock(Blocks.EMERALD_BLOCK);
        addBlock(Blocks.GOLD_BLOCK);
        addBlock(Blocks.IRON_BLOCK);
        addBlock(Blocks.COAL_BLOCK);
        addBlock(Blocks.REDSTONE_BLOCK);
        addBlock(Blocks.LAPIS_BLOCK);
    }

    private void addBlock(Block block) {
        selectedBlocks.add(block);
    }

    public boolean isVisibleBlock(Block block) {
        return selectedBlocks.contains(block);
    }

    public boolean isRealMode() {
        return realXray.isEnabled();
    }

    @Override
    public void onEnable() {
        savedGamma = mc.options.getGamma().getValue();
        if (realXray.isEnabled()) mc.options.getGamma().setValue(gamma.getValue());
    }

    @Override
    public void onDisable() {
        mc.options.getGamma().setValue(savedGamma);
    }

    @Override
    public void onTick() {
        // XRay logic
    }
}