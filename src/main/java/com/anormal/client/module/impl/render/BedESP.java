package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockESPHelper;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Highlights bed blocks through walls.
 *
 * <p>The old scan swept a full sphere of radius {@code Range} in a single tick
 * (about 463 000 lookups at r=48) and cleared the marker list every 20 ticks,
 * so markers both froze the client and blinked. It now uses the shared
 * budgeted {@link BlockESPHelper}, with an independent vertical range so beds
 * deep below you are still found.</p>
 */
public class BedESP extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 48.0, 8.0, 128.0, 4.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 48.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max bed markers drawn", 200.0, 10.0, 800.0, 10.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 80, 80, 255));

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private final BlockESPHelper helper = new BlockESPHelper();

    public BedESP() {
        super("BedESP", "Highlights bed blocks through walls", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(maxShown);
        addSetting(color);
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
                state -> BlockESPHelper.pathEndsWith(state, "_bed") ? col : -1);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || helper.found().isEmpty()) return;

        int width = 1;
        int limit = maxShown.getValue().intValue();
        int drawn = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        try {
            for (Map.Entry<BlockPos, Integer> e : helper.found().entrySet()) {
                if (drawn >= limit) break;
                if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;
                if (RenderUtils.projectBlockBounds(e.getKey(), tickDelta) == null) continue;
                drawn++;
                RenderUtils.drawBlockOutline(context, e.getKey(), e.getValue(), tickDelta, width);
            }
        } catch (Throwable ignored) {
        }
    }
}
