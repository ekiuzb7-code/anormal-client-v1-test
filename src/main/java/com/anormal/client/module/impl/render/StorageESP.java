package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Highlights containers (chests, barrels, shulkers, ...) through walls.
 *
 * <p><b>Flicker fix:</b> the old scan advanced by 2 blocks on every axis, so the
 * sampled grid shifted with the player's exact position - markers appeared and
 * disappeared while walking. The scan now advances by 1 and is spread over ticks
 * (one horizontal layer per tick), so results are stable and there is no lag
 * spike. Each container type has its own toggle, colour, and can be drawn as a
 * 3D outline and/or a translucent fill.</p>
 */
public class StorageESP extends Module {
    public final BooleanSetting outlineOpen = new BooleanSetting("Outline Open", "Contrasting outline while a container is open", true);

    public final BooleanSetting chests = new BooleanSetting("Chests", "ESP for chests", true);
    public final ColorSetting chestColor = new ColorSetting("Chest Color", "ESP color for chests", ColorUtils.rgba(255, 170, 0, 255));
    public final BooleanSetting trapped = new BooleanSetting("Trapped Chests", "ESP for trapped chests", true);
    public final ColorSetting trappedColor = new ColorSetting("Trapped Color", "ESP color for trapped chests", ColorUtils.rgba(255, 60, 60, 255));
    public final BooleanSetting ender = new BooleanSetting("Ender Chests", "ESP for ender chests", true);
    public final ColorSetting enderColor = new ColorSetting("Ender Color", "ESP color for ender chests", ColorUtils.rgba(180, 80, 255, 255));
    public final BooleanSetting hoppers = new BooleanSetting("Hoppers", "ESP for hoppers", false);
    public final ColorSetting hopperColor = new ColorSetting("Hopper Color", "ESP color for hoppers", ColorUtils.rgba(150, 150, 150, 255));
    public final BooleanSetting furnaces = new BooleanSetting("Furnaces", "ESP for furnaces", false);
    public final ColorSetting furnaceColor = new ColorSetting("Furnace Color", "ESP color for furnaces", ColorUtils.rgba(120, 120, 120, 255));
    public final BooleanSetting dispensers = new BooleanSetting("Dispensers", "ESP for dispensers", false);
    public final ColorSetting dispenserColor = new ColorSetting("Dispenser Color", "ESP color for dispensers", ColorUtils.rgba(80, 160, 255, 255));
    public final BooleanSetting droppers = new BooleanSetting("Droppers", "ESP for droppers", false);
    public final ColorSetting dropperColor = new ColorSetting("Dropper Color", "ESP color for droppers", ColorUtils.rgba(80, 255, 200, 255));
    public final BooleanSetting shulkers = new BooleanSetting("Shulkers", "ESP for shulker boxes", true);
    public final ColorSetting shulkerColor = new ColorSetting("Shulker Color", "ESP color for shulker boxes", ColorUtils.rgba(200, 120, 255, 255));
    public final BooleanSetting barrels = new BooleanSetting("Barrels", "ESP for barrels", true);
    public final ColorSetting barrelColor = new ColorSetting("Barrel Color", "ESP color for barrels", ColorUtils.rgba(160, 110, 60, 255));
    public final BooleanSetting crafters = new BooleanSetting("Crafters", "ESP for crafters", false);
    public final ColorSetting crafterColor = new ColorSetting("Crafter Color", "ESP color for crafters", ColorUtils.rgba(140, 220, 120, 255));

    public final NumberSetting range = new NumberSetting("Range", "Only renders storage within this range", 32.0, 8.0, 96.0, 4.0);

    // ---- Render ----
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw a 3D block outline", true);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Draw a translucent fill", false);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Opacity of the fill", 90.0, 0.0, 255.0, 5.0);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outline", 1.0, 1.0, 4.0, 1.0);
    public final NumberSetting maxRendered = new NumberSetting("Max Rendered", "Max containers drawn at once", 250.0, 20.0, 1000.0, 10.0);

    private final Map<BlockPos, Integer> found = new HashMap<>();
    private int scanLayer = 0;
    private int originX = Integer.MIN_VALUE, originZ = Integer.MIN_VALUE;

    public StorageESP() {
        super("StorageESP", "Renders outlines around chests, barrels, and shulkers", Category.RENDER);
        addSetting(outlineOpen);

        addSetting(chests);
        addSetting(chestColor);
        addSetting(trapped);
        addSetting(trappedColor);
        addSetting(ender);
        addSetting(enderColor);
        addSetting(hoppers);
        addSetting(hopperColor);
        addSetting(furnaces);
        addSetting(furnaceColor);
        addSetting(dispensers);
        addSetting(dispenserColor);
        addSetting(droppers);
        addSetting(dropperColor);
        addSetting(shulkers);
        addSetting(shulkerColor);
        addSetting(barrels);
        addSetting(barrelColor);
        addSetting(crafters);
        addSetting(crafterColor);

        addSetting(range);
        addSetting(outline);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(outlineWidth);
        addSetting(maxRendered);
    }

    @Override
    public void onDisable() {
        found.clear();
        scanLayer = 0;
        originX = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();

        boolean newSweep = scanLayer == 0 || originX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - originX) > 16 || Math.abs(origin.getZ() - originZ) > 16;
        if (newSweep) {
            originX = origin.getX();
            originZ = origin.getZ();
            if (scanLayer == 0) prune(origin, r);
        }

        int yOff = scanLayer - r;
        scanLayer++;
        if (scanLayer > 2 * r) scanLayer = 0;

        BlockPos.Mutable p = new BlockPos.Mutable();
        try {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    p.set(originX + x, origin.getY() + yOff, originZ + z);
                    if (!mc.world.isChunkLoaded(p)) continue;
                    int col = colorFor(mc.world.getBlockState(p).getBlock());
                    if (col == -1) found.remove(p);
                    else found.put(p.toImmutable(), col);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void prune(BlockPos origin, int r) {
        int rr = r * r;
        found.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > r + 1;
        });
    }

    private int colorFor(Block block) {
        if (block == Blocks.TRAPPED_CHEST) return trapped.isEnabled() ? trappedColor.getValue() : -1;
        if (block == Blocks.ENDER_CHEST) return ender.isEnabled() ? enderColor.getValue() : -1;
        if (block == Blocks.CHEST) return chests.isEnabled() ? chestColor.getValue() : -1;
        if (block == Blocks.HOPPER) return hoppers.isEnabled() ? hopperColor.getValue() : -1;
        if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER)
            return furnaces.isEnabled() ? furnaceColor.getValue() : -1;
        if (block == Blocks.DISPENSER) return dispensers.isEnabled() ? dispenserColor.getValue() : -1;
        if (block == Blocks.DROPPER) return droppers.isEnabled() ? dropperColor.getValue() : -1;
        if (isShulker(block)) return shulkers.isEnabled() ? shulkerColor.getValue() : -1;
        if (block == Blocks.BARREL) return barrels.isEnabled() ? barrelColor.getValue() : -1;
        if (block == Blocks.CRAFTER) return crafters.isEnabled() ? crafterColor.getValue() : -1;
        return -1;
    }

    private boolean isShulker(Block block) {
        try {
            String path = net.minecraft.registry.Registries.BLOCK.getId(block).getPath();
            return path.endsWith("shulker_box");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;

        boolean open = false;
        try {
            open = outlineOpen.isEnabled() && mc.currentScreen != null
                    && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
        } catch (Throwable ignored) {}

        int limit = maxRendered.getValue().intValue();
        int drawn = 0;
        int alpha = fillOpacity.getValue().intValue();
        int width = outlineWidth.getValue().intValue();

        try {
            for (Map.Entry<BlockPos, Integer> e : found.entrySet()) {
                if (drawn++ >= limit) break;
                BlockPos p = e.getKey();
                int col = e.getValue();
                if (fill.isEnabled()) RenderUtils.drawBlockFill(context, p, col, tickDelta, alpha);
                if (outline.isEnabled()) RenderUtils.drawBlockOutline(context, p, col, tickDelta, width);
                if (open) {
                    RenderUtils.drawBlockOutline(context, p, 0xFFFFFFFF, tickDelta, width);
                }
            }
        } catch (Throwable ignored) {}
    }
}
