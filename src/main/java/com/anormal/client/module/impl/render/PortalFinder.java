package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockESPHelper;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Finds nether portal blocks nearby.
 *
 * <p>Rewritten on the shared budgeted {@link BlockESPHelper}: the old version
 * swept a full sphere in one tick and cleared its list on every sweep, so it
 * both stuttered and blinked. The vertical range is now independent of the
 * horizontal one, so a portal deep underground is still found.</p>
 */
public class PortalFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 48.0, 8.0, 128.0, 4.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 48.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final ColorSetting color = new ColorSetting("Color", "Portal marker color", ColorUtils.rgba(170, 85, 255, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max portal markers", 64.0, 4.0, 256.0, 4.0);

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private final BlockESPHelper helper = new BlockESPHelper();

    public PortalFinder() {
        super("PortalFinder", "Finds nether portal blocks nearby", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(color);
        addSetting(maxShown);
    }

    @Override
    public void onDisable() {
        helper.reset();
    }

    @Override
    public void onTick() {
        int col = color.getValue();
        helper.tick(mc, range.getValue().intValue(), verticalRange.getValue().intValue(),
                scanSpeed.getValue().intValue(), recentreDistance.getValue(),
                state -> BlockESPHelper.pathContains(state, "nether_portal") ? col : -1);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || helper.found().isEmpty()) return;

        int limit = maxShown.getValue().intValue();
        int drawn = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        try {
            for (Map.Entry<BlockPos, Integer> e : helper.found().entrySet()) {
                if (drawn >= limit) break;
                if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;
                if (RenderUtils.projectBlockBounds(e.getKey(), tickDelta) == null) continue;
                drawn++;
                RenderUtils.drawBlockOutline(context, e.getKey(), e.getValue(), tickDelta, 1);
            }
        } catch (Throwable ignored) {
        }
    }
}
