package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.Set;

public class XRay extends Module {
    public final NumberSetting opacity = new NumberSetting("Opacity", "Opacity of non-ore blocks", 20.0, 0.0, 100.0, 5.0);
    private final Set<Block> selectedBlocks = new HashSet<>();

    public XRay() {
        super("XRay", "Makes non-selected blocks transparent to locate ores and custom blocks", Category.WORLD);
        addSetting(opacity);
        selectAllOres();
    }

    public void selectAllOres() {
        selectedBlocks.clear();
        selectedBlocks.add(Blocks.DIAMOND_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        selectedBlocks.add(Blocks.ANCIENT_DEBRIS);
        selectedBlocks.add(Blocks.GOLD_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_GOLD_ORE);
        selectedBlocks.add(Blocks.NETHER_GOLD_ORE);
        selectedBlocks.add(Blocks.IRON_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_IRON_ORE);
        selectedBlocks.add(Blocks.EMERALD_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_EMERALD_ORE);
        selectedBlocks.add(Blocks.LAPIS_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_LAPIS_ORE);
        selectedBlocks.add(Blocks.REDSTONE_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        selectedBlocks.add(Blocks.CHEST);
        selectedBlocks.add(Blocks.TRAPPED_CHEST);
        selectedBlocks.add(Blocks.BARREL);
        selectedBlocks.add(Blocks.SPAWNER);
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public void clearSelection() {
        selectedBlocks.clear();
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public void toggleBlock(Block block) {
        if (selectedBlocks.contains(block)) {
            selectedBlocks.remove(block);
        } else {
            selectedBlocks.add(block);
        }
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public boolean isBlockSelected(Block block) {
        return selectedBlocks.contains(block);
    }

    public Set<Block> getSelectedBlocks() {
        return selectedBlocks;
    }

    public boolean isVisibleBlock(Block block) {
        return selectedBlocks.contains(block);
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
