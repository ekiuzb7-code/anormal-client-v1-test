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
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds end portal frames marking strongholds.
 *
 * <p>Migrated to the shared budgeted {@link BlockESPHelper}: no more 80-tick
 * freeze, no blink, independent vertical range for deep coverage.</p>
 */
public class StrongholdFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 64.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 48.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log new frames in chat", true);
    public final ColorSetting color = new ColorSetting("Color", "Frame marker color", ColorUtils.rgba(120, 220, 60, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max frame markers drawn", 8.0, 1.0, 32.0, 1.0);

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private final BlockESPHelper helper = new BlockESPHelper();
    private final Set<String> logged = new HashSet<>();
    private int prevSize = 0;

    public StrongholdFinder() {
        super("StrongholdFinder", "Finds end portal frames marking strongholds", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(chatLog);
        addSetting(color);
        addSetting(maxShown);
    }

    @Override
    public void onDisable() {
        helper.reset();
        logged.clear();
        prevSize = 0;
    }

    @Override
    public void onTick() {
        int col = color.getValue();
        helper.tick(mc, range.getValue().intValue(), verticalRange.getValue().intValue(),
                scanSpeed.getValue().intValue(), recentreDistance.getValue(),
                state -> BlockESPHelper.pathEndsWith(state, "end_portal_frame") ? col : -1);

        if (chatLog.isEnabled() && mc.inGameHud != null && helper.found().size() > prevSize) {
            for (BlockPos p : helper.found().keySet()) {
                String key = p.getX() + "," + p.getY() + "," + p.getZ();
                if (logged.add(key)) {
                    try {
                        mc.inGameHud.getChatHud().addMessage(Text.literal("§a[Stronghold] §fframe at " + key));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        prevSize = helper.found().size();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || helper.found().isEmpty()) return;

        int col = color.getValue();
        int limit = maxShown.getValue().intValue();
        int drawn = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        for (Map.Entry<BlockPos, Integer> e : helper.found().entrySet()) {
            if (drawn >= limit) break;
            if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;

            BlockPos p = e.getKey();
            int[] sc = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            drawn++;

            context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x66000000);
            RenderUtils.drawBorder(context, sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, 1, col);
            if (mc.textRenderer != null) {
                String d = (int) Math.sqrt(p.getSquaredDistance(mc.player.getBlockPos())) + "m";
                RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + 6, sc[1] - 4, col, true);
            }
        }
    }
}
