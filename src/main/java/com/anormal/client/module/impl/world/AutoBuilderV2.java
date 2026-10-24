package com.anormal.client.module.impl.world;

import com.anormal.client.autobuilder2.build.BuildEngine;
import com.anormal.client.autobuilder2.gui.AutoBuilderV2Screen;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * AutoBuilder V2 - a straight port of the standalone "Auto cc" builder.
 *
 * <p>Unlike {@link AutoBuilder}, this engine does not only place blocks: it walks the player to
 * every cell with its own path planner, selects the right hotbar item, mines whatever is in the
 * way, and performs the state clicks redstone needs (repeater delay, comparator mode, lever,
 * note block, ...). The whole build is driven from the mc tick, so it keeps running while
 * the menu is closed.</p>
 *
 * <p>All logic lives in {@code com.anormal.client.autobuilder2}: {@code build.BuildEngine} is the
 * builder itself, {@code litematic.LitematicaFile} reads {@code .litematic} files and
 * {@code gui.AutoBuilderV2Screen} is the interface.</p>
 */
public class AutoBuilderV2 extends Module {

    public final BooleanSetting showProgress = new BooleanSetting("Show Progress",
            "Draw build progress in the corner while a build is running", true);

    public final BooleanSetting openOnEnable = new BooleanSetting("Open On Enable",
            "Open the builder screen when the module is switched on", true);

    public final BooleanSetting showBox = new BooleanSetting("Show Box",
            "Draw the build volume in the world", true);

    public AutoBuilderV2() {
        super("AutoBuilderV2", "Walks to and builds .litematic schematics block by block", Category.WORLD);
        addSetting(showProgress);
        addSetting(openOnEnable);
        addSetting(showBox);
    }

    @Override
    public void onEnable() {
        if (openOnEnable.isEnabled() && mc.currentScreen == null) {
            mc.setScreen(new AutoBuilderV2Screen(Text.literal("AutoBuilder V2")));
        }
    }

    @Override
    public void onDisable() {
        try {
            if (BuildEngine.get().isActive()) BuildEngine.get().stop("Module disabled");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onTick() {
        try {
            BuildEngine.get().tick(mc);
        } catch (Throwable ignored) {
        }
        // The isometric preview decodes and uploads its texture between frames; without this call
        // it would never leave the "decoding" state.
        try {
            if (mc.currentScreen instanceof AutoBuilderV2Screen screen) screen.tickPreview();
        } catch (Throwable ignored) {
        }
    }

    /**
     * The keybind only opens and closes the interface - it never switches the module off, so
     * pressing it a second time does not cancel a running build.
     */
    @Override
    public boolean onKeyPressed() {
        if (mc.currentScreen instanceof AutoBuilderV2Screen) {
            mc.setScreen(null);
            return true;
        }
        if (!isEnabled()) {
            setEnabled(true);
            return true;
        }
        mc.setScreen(new AutoBuilderV2Screen(Text.literal("AutoBuilder V2")));
        return true;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null || mc.getWindow() == null) return;

        BuildEngine engine;
        try {
            engine = BuildEngine.get();
        } catch (Throwable t) {
            return;
        }
        if (engine.state() == BuildEngine.State.IDLE) return;

        if (showProgress.isEnabled()) {
            try {
                String line = "§6AutoBuilder §7| §f" + engine.progress();
                int w = mc.getWindow().getScaledWidth();
                int tw = mc.textRenderer.getWidth(line);
                context.drawText(mc.textRenderer, line, w - tw - 6, 6, 0xFFFFFFFF, true);
            } catch (Throwable ignored) {
            }
        }

        // World preview: the build volume is drawn as a projected box, so the schematic is visible
        // in the world even while the builder screen is closed.
        if (showBox.isEnabled()) drawBounds(context, engine, tickDelta);
    }

    /** Eight corners of the plan, projected and joined, exactly like the other ESP boxes. */
    private void drawBounds(DrawContext context, BuildEngine engine, float tickDelta) {
        int[] b;
        try {
            b = engine.bounds();
        } catch (Throwable t) {
            return;
        }
        if (b == null || b.length < 6) return;

        double x0 = b[0], y0 = b[1], z0 = b[2];
        double x1 = b[3] + 1.0, y1 = b[4] + 1.0, z1 = b[5] + 1.0;

        Vec3d[] corners = {
                new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0), new Vec3d(x1, y0, z1), new Vec3d(x0, y0, z1),
                new Vec3d(x0, y1, z0), new Vec3d(x1, y1, z0), new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1)
        };
        int[][] p = new int[8][];
        for (int i = 0; i < 8; i++) {
            try {
                p[i] = ProjectionUtil.project(corners[i], tickDelta);
            } catch (Throwable t) {
                return;
            }
        }

        int colour = ThemeManager.getAccentColor();
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            int[] a = p[e[0]];
            int[] c = p[e[1]];
            if (a == null || c == null) continue;
            RenderUtils.drawLine(context, a[0], a[1], c[0], c[1], colour, 1.0f);
        }
    }
}
