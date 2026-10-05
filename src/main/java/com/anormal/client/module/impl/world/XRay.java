package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XRay extends Module {
    // Core settings
    public final BooleanSetting realXray = new BooleanSetting("Real XRay", "Hide non-selected blocks via mixin (REQUIRED for blocks to disappear)", true);
    public final BooleanSetting fullBright = new BooleanSetting("FullBright", "Full brightness while XRay is active", true);
    public final NumberSetting gamma = new NumberSetting("Gamma", "Fullbright gamma value", 16.0, 1.0, 16.0, 1.0);

    // Background/opacity settings
    public final NumberSetting backgroundOpacity = new NumberSetting("Background Opacity", "Opacity of non-selected blocks (0 = invisible, 255 = normal)", 0, 0, 255, 1);
    public final BooleanSetting exposedOnly = new BooleanSetting("Exposed Only", "Only show blocks exposed to air (cave mode)", false);

    // Markers
    public final BooleanSetting markers = new BooleanSetting("Markers", "Draw boxes on selected blocks through walls", true);
    public final NumberSetting markerRange = new NumberSetting("Marker Range", "Marker scan radius", 24.0, 4.0, 64.0, 1.0);

    // Advanced
    public final BooleanSetting transparentBackground = new BooleanSetting("Transparent Background", "Render non-selected blocks with background opacity", true);

    private final Set<Block> selectedBlocks = new HashSet<>();
    private final List<String> selectedBlockNames = new ArrayList<>();

    private final Set<BlockPos> cache = new HashSet<>();
    private int ticks = 0;
    private double savedGamma = 1.0;

    public XRay() {
        super("XRay", "Real mode hides blocks via mixin, Markers draws boxes through walls", Category.WORLD);
        addSetting(realXray);
        addSetting(fullBright);
        addSetting(gamma);
        addSetting(backgroundOpacity);
        addSetting(exposedOnly);
        addSetting(markers);
        addSetting(markerRange);
        addSetting(transparentBackground);
        selectAllOres();
    }

    public void selectAllOres() {
        selectedBlocks.clear();
        selectedBlockNames.clear();

        // Overworld ores
        addBlock(Blocks.COAL_ORE);
        addBlock(Blocks.DEEPSLATE_COAL_ORE);
        addBlock(Blocks.COPPER_ORE);
        addBlock(Blocks.DEEPSLATE_COPPER_ORE);
        addBlock(Blocks.DIAMOND_ORE);
        addBlock(Blocks.DEEPSLATE_DIAMOND_ORE);
        addBlock(Blocks.EMERALD_ORE);
        addBlock(Blocks.DEEPSLATE_EMERALD_ORE);
        addBlock(Blocks.GOLD_ORE);
        addBlock(Blocks.DEEPSLATE_GOLD_ORE);
        addBlock(Blocks.IRON_ORE);
        addBlock(Blocks.DEEPSLATE_IRON_ORE);
        addBlock(Blocks.LAPIS_ORE);
        addBlock(Blocks.DEEPSLATE_LAPIS_ORE);
        addBlock(Blocks.REDSTONE_ORE);
        addBlock(Blocks.DEEPSLATE_REDSTONE_ORE);
        addBlock(Blocks.NETHER_QUARTZ_ORE);

        // Overworld raw mineral blocks
        addBlock(Blocks.RAW_COPPER_BLOCK);
        addBlock(Blocks.RAW_GOLD_BLOCK);
        addBlock(Blocks.RAW_IRON_BLOCK);

        // Overworld mineral blocks
        addBlock(Blocks.COAL_BLOCK);
        addBlock(Blocks.DIAMOND_BLOCK);
        addBlock(Blocks.EMERALD_BLOCK);
        addBlock(Blocks.GOLD_BLOCK);
        addBlock(Blocks.IRON_BLOCK);
        addBlock(Blocks.LAPIS_BLOCK);
        addBlock(Blocks.REDSTONE_BLOCK);

        // Nether ores
        addBlock(Blocks.ANCIENT_DEBRIS);
        addBlock(Blocks.NETHER_GOLD_ORE);
        addBlock(Blocks.NETHER_QUARTZ_ORE);

        // Nether material blocks
        addBlock(Blocks.NETHERITE_BLOCK);
        addBlock(Blocks.QUARTZ_BLOCK);

        // Storage blocks
        addBlock(Blocks.CHEST);
        addBlock(Blocks.TRAPPED_CHEST);
        addBlock(Blocks.ENDER_CHEST);
        addBlock(Blocks.BARREL);
        addBlock(Blocks.SHULKER_BOX);
        addBlock(Blocks.DISPENSER);
        addBlock(Blocks.DROPPER);
        addBlock(Blocks.HOPPER);

        // Utility blocks
        addBlock(Blocks.SPAWNER);
        addBlock(Blocks.TRIAL_SPAWNER);
        addBlock(Blocks.BEACON);
        addBlock(Blocks.CRAFTING_TABLE);
        addBlock(Blocks.ENCHANTING_TABLE);
        addBlock(Blocks.FURNACE);
        addBlock(Blocks.BLAST_FURNACE);
        addBlock(Blocks.SMOKER);
        addBlock(Blocks.FLOWER_POT);
        addBlock(Blocks.JUKEBOX);
        addBlock(Blocks.LODESTONE);
        addBlock(Blocks.RESPAWN_ANCHOR);
        addBlock(Blocks.END_PORTAL);
        addBlock(Blocks.END_PORTAL_FRAME);
        addBlock(Blocks.NETHER_PORTAL);

        // Anvil variants
        addBlock(Blocks.ANVIL);
        addBlock(Blocks.CHIPPED_ANVIL);
        addBlock(Blocks.DAMAGED_ANVIL);

        // Job blocks
        addBlock(Blocks.BARREL);
        addBlock(Blocks.BLAST_FURNACE);
        addBlock(Blocks.BREWING_STAND);
        addBlock(Blocks.CARTOGRAPHY_TABLE);
        addBlock(Blocks.COMPOSTER);
        addBlock(Blocks.FLETCHING_TABLE);
        addBlock(Blocks.GRINDSTONE);
        addBlock(Blocks.LECTERN);
        addBlock(Blocks.LOOM);
        addBlock(Blocks.SMITHING_TABLE);
        addBlock(Blocks.STONECUTTER);

        // Cauldron variants
        addBlock(Blocks.CAULDRON);
        addBlock(Blocks.LAVA_CAULDRON);
        addBlock(Blocks.WATER_CAULDRON);

        // Command blocks
        addBlock(Blocks.COMMAND_BLOCK);
        addBlock(Blocks.CHAIN_COMMAND_BLOCK);
        addBlock(Blocks.REPEATING_COMMAND_BLOCK);

        // Remaining valuable blocks
        addBlock(Blocks.DRAGON_EGG);
        addBlock(Blocks.TNT);
        addBlock(Blocks.BOOKSHELF);
        addBlock(Blocks.CLAY);

        // Fluids
        addBlock(Blocks.WATER);
        addBlock(Blocks.LAVA);

        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    private void addBlock(Block block) {
        selectedBlocks.add(block);
        selectedBlockNames.add(Registries.BLOCK.getId(block).toString());
    }

    public void clearSelection() {
        selectedBlocks.clear();
        selectedBlockNames.clear();
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public void toggleBlock(Block block) {
        String name = Registries.BLOCK.getId(block).toString();
        if (selectedBlocks.contains(block)) {
            selectedBlocks.remove(block);
            selectedBlockNames.remove(name);
        } else {
            selectedBlocks.add(block);
            selectedBlockNames.add(name);
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

    public List<String> getSelectedBlockNames() {
        return selectedBlockNames;
    }

    public boolean isVisibleBlock(Block block) {
        return selectedBlocks.contains(block);
    }

    public boolean isRealMode() {
        return realXray.isEnabled();
    }

    public boolean markersEnabled() {
        return markers.isEnabled();
    }

    public int getBackgroundOpacity() {
        return backgroundOpacity.getValue().intValue();
    }

    public boolean isExposedOnly() {
        return exposedOnly.isEnabled();
    }

    public boolean isTransparentBackground() {
        return transparentBackground.isEnabled();
    }

    public boolean isFullBright() {
        return fullBright.isEnabled();
    }

    @Override
    public void onEnable() {
        // Save current gamma
        try {
            savedGamma = mc.options.getGamma().getValue();
        } catch (Throwable ignored) {}

        // Apply XRay gamma (like Wurst)
        if (fullBright.isEnabled()) {
            try {
                mc.options.getGamma().setValue(gamma.getValue());
            } catch (Throwable ignored) {}
        }

        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        // Restore gamma
        try {
            mc.options.getGamma().setValue(savedGamma);
        } catch (Throwable ignored) {}

        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onTick() {
        // Apply gamma while enabled (Wurst approach - force gamma)
        if (fullBright.isEnabled()) {
            try {
                mc.options.getGamma().setValue(gamma.getValue());
            } catch (Throwable ignored) {}
        }

        if (!markersEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 12) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = markerRange.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < 512; x++)
                for (int y = -r; y <= r && cache.size() < 512; y++)
                    for (int z = -r; z <= r && cache.size() < 512; z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            BlockState state = mc.world.getBlockState(p);
                            Block block = state.getBlock();
                            if (!selectedBlocks.contains(block)) continue;
                            if (exposedOnly.isEnabled() && !exposed(p)) continue;
                        } catch (Throwable t) {
                            continue;
                        }
                        cache.add(p);
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!markersEnabled() || mc.player == null || cache.isEmpty()) return;
        int col = 0xFFFF8800;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        for (BlockPos p : cache) {
            int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (s == null) continue;
            if (s[0] < -20 || s[0] > sw + 20 || s[1] < -20 || s[1] > sh + 20) continue;
            double dist = Math.sqrt(mc.player.getEyePos().squaredDistanceTo(
                    new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
            int half = Math.max(2, Math.min(6, (int) (60.0 / Math.max(1.0, dist))));
            context.fill(s[0] - half, s[1] - half, s[0] + half, s[1] + half, (col & 0x00FFFFFF) | 0x55000000);
            context.fill(s[0] - half, s[1] - half, s[0] + half, s[1] - half + 1, col);
            context.fill(s[0] - half, s[1] + half - 1, s[0] + half, s[1] + half, col);
            context.fill(s[0] - half, s[1] - half, s[0] - half + 1, s[1] + half, col);
            context.fill(s[0] + half - 1, s[1] - half, s[0] + half, s[1] + half, col);
        }
    }

    private boolean exposed(BlockPos p) {
        try {
            for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
                try {
                    if (mc.world.getBlockState(p.offset(d)).isAir()) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }
}