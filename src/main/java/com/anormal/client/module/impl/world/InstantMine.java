package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;

/**
 * Makes blocks break instantly.
 *
 * <p>Two cooperating hooks, both in {@code ClientPlayerInteractionManagerMixin}
 * and {@code BlockHardnessMixin}:</p>
 * <ul>
 *   <li><b>Instant</b> - {@code AbstractBlockState.getHardness} returns 0 for a
 *       matching block, so a single click destroys it.</li>
 *   <li><b>Fast</b> - the vanilla breaking cooldown is reset to 0 after every
 *       breaking tick, which removes the 5-tick delay between blocks.</li>
 * </ul>
 */
public class InstantMine extends Module {

    public final ModeSetting mode = new ModeSetting("Mode", "Which trick to use", "Both",
            "Instant", "Fast", "Both");
    public final ModeSetting filter = new ModeSetting("Block Filter", "Which blocks are affected", "All",
            "All", "Ores", "Valuables", "Stone", "Wood", "Soft");

    public final BooleanSetting ignoreBedrock = new BooleanSetting("Ignore Unbreakable", "Never touch bedrock, barriers, portals and command blocks", true);
    public final BooleanSetting ignoreObsidian = new BooleanSetting("Ignore Obsidian", "Never touch obsidian, crying obsidian and ancient debris", true);
    public final BooleanSetting ignoreFluids = new BooleanSetting("Ignore Fluids", "Never touch water or lava", true);
    public final BooleanSetting onlyExposed = new BooleanSetting("Only Exposed", "Only affect blocks touching air", false);
    public final BooleanSetting requireTool = new BooleanSetting("Require Tool", "Only affect blocks your held tool can actually mine", false);

    public final BooleanSetting swingHand = new BooleanSetting("Swing Hand", "Swing your arm while mining", true);
    public final BooleanSetting autoTool = new BooleanSetting("Auto Tool", "Switch to the fastest tool before mining", false);
    public final BooleanSetting ignoreCreative = new BooleanSetting("Ignore In Creative", "Do nothing in creative mode", true);

    public InstantMine() {
        super("InstantMine", "Breaks blocks instantly by removing hardness and the breaking cooldown", Category.WORLD);
        addSetting(mode);
        addSetting(filter);
        addSetting(ignoreBedrock);
        addSetting(ignoreObsidian);
        addSetting(ignoreFluids);
        addSetting(onlyExposed);
        addSetting(requireTool);
        addSetting(swingHand);
        addSetting(autoTool);
        addSetting(ignoreCreative);
    }

    public boolean isInstant() {
        return isEnabled() && (mode.is("Instant") || mode.is("Both"));
    }

    public boolean isFast() {
        return isEnabled() && (mode.is("Fast") || mode.is("Both"));
    }

    /** @return true when this block should have its hardness forced to zero. */
    public boolean shouldInstantBreak(BlockState state) {
        if (!isEnabled()) return false;
        if (state == null || state.isAir()) return false;

        if (ignoreCreative.isEnabled() && mc.player != null && mc.player.getAbilities().creativeMode) return false;

        if (ignoreFluids.isEnabled()) {
            try {
                if (!state.getFluidState().isEmpty()) return false;
            } catch (Throwable ignored) {
            }
        }

        String path;
        try {
            path = Registries.BLOCK.getId(state.getBlock()).getPath();
        } catch (Throwable t) {
            return false;
        }

        if (ignoreBedrock.isEnabled() && (path.equals("bedrock") || path.contains("barrier")
                || path.contains("command_block") || path.contains("structure_block")
                || path.contains("end_portal") || path.contains("portal_frame")
                || path.contains("jigsaw") || path.contains("light"))) {
            return false;
        }

        if (ignoreObsidian.isEnabled() && (path.contains("obsidian") || path.contains("ancient_debris"))) {
            return false;
        }

        if (onlyExposed.isEnabled() && !isExposed(state)) return false;

        if (requireTool.isEnabled() && mc.player != null) {
            try {
                if (!mc.player.getMainHandStack().isSuitableFor(state)) return false;
            } catch (Throwable ignored) {
            }
        }

        return matchesFilter(path);
    }

    private boolean matchesFilter(String path) {
        switch (filter.getValue()) {
            case "Ores":
                return path.contains("_ore") || path.contains("ancient_debris") || path.contains("raw_");
            case "Valuables":
                return path.contains("diamond") || path.contains("emerald") || path.contains("gold")
                        || path.contains("netherite") || path.contains("ancient_debris") || path.contains("beacon");
            case "Stone":
                return path.contains("stone") || path.contains("deepslate") || path.contains("andesite")
                        || path.contains("diorite") || path.contains("granite") || path.contains("tuff")
                        || path.contains("calcite") || path.contains("basalt") || path.contains("blackstone")
                        || path.contains("netherrack") || path.contains("end_stone") || path.contains("obsidian");
            case "Wood":
                return path.contains("log") || path.contains("planks") || path.contains("wood")
                        || path.contains("stem") || path.contains("hyphae");
            case "Soft":
                return path.contains("dirt") || path.contains("sand") || path.contains("gravel")
                        || path.contains("clay") || path.contains("soul_soil") || path.contains("grass_block")
                        || path.contains("snow") || path.contains("leaves");
            default:
                return true;
        }
    }

    private boolean isExposed(BlockState state) {
        try {
            var pos = mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr
                    ? bhr.getBlockPos() : null;
            if (pos == null) return true;
            for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
                if (mc.world.getBlockState(pos.offset(d)).isAir()) return true;
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }
}
