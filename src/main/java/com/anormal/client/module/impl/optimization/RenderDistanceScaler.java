package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Keeps the render distance in step with the frame rate.
 *
 * <p>If FPS falls below the target the view distance is lowered one step at a
 * time; when there is headroom again it is raised back up, up to the maximum.
 * The original value is restored when the module is disabled.</p>
 */
public class RenderDistanceScaler extends Optimizer {

    public final BooleanSetting enabled = new BooleanSetting("Enable", "Master switch", true);
    public final NumberSetting minDistance = new NumberSetting("Min Distance", "Lowest render distance the module may pick", 4.0, 2.0, 32.0, 1.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Highest render distance the module may pick", 16.0, 2.0, 32.0, 1.0);
    public final NumberSetting targetFps = new NumberSetting("Target FPS", "FPS to aim for", 70.0, 20.0, 300.0, 5.0);
    public final NumberSetting hysteresis = new NumberSetting("Hysteresis", "FPS above the target before raising the distance", 15.0, 0.0, 80.0, 5.0);
    public final NumberSetting interval = new NumberSetting("Check Interval", "Ticks between adjustments", 40.0, 5.0, 200.0, 5.0);
    public final NumberSetting step = new NumberSetting("Step", "How many chunks to change at a time", 1.0, 1.0, 4.0, 1.0);

    public final BooleanSetting simulation = new BooleanSetting("Also Simulation Distance", "Scale the simulation distance too", false);
    public final NumberSetting simMin = new NumberSetting("Sim Min", "Lowest simulation distance", 4.0, 2.0, 32.0, 1.0);
    public final NumberSetting simMax = new NumberSetting("Sim Max", "Highest simulation distance", 10.0, 2.0, 32.0, 1.0);

    public final BooleanSetting onlyWhileMoving = new BooleanSetting("Only While Moving", "Skip adjusting when standing still", false);
    public final BooleanSetting restoreOnDisable = new BooleanSetting("Restore On Disable", "Put the original distance back", true);
    public final BooleanSetting notify = new BooleanSetting("Notify", "Show a toast when the distance changes", false);
    public final BooleanSetting showHud = new BooleanSetting("Show HUD", "Show the current distance and FPS", true);

    private int counter = 0;
    private int prevView = -1;
    private int prevSim = -1;

    public RenderDistanceScaler() {
        super("RenderDistanceScaler", "Adjusts the render distance automatically to hold your FPS target");
        addSetting(enabled);
        addSetting(minDistance);
        addSetting(maxDistance);
        addSetting(targetFps);
        addSetting(hysteresis);
        addSetting(interval);
        addSetting(step);
        addSetting(simulation);
        addSetting(simMin);
        addSetting(simMax);
        addSetting(onlyWhileMoving);
        addSetting(restoreOnDisable);
        addSetting(notify);
        addSetting(showHud);
    }

    @Override
    public void onEnable() {
        counter = 0;
        try {
            prevView = mc.options.getViewDistance().getValue();
            prevSim = mc.options.getSimulationDistance().getValue();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() {
        if (restoreOnDisable.isEnabled()) {
            try {
                if (prevView > 0) mc.options.getViewDistance().setValue(prevView);
                if (prevSim > 0) mc.options.getSimulationDistance().setValue(prevSim);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onTick() {
        if (!enabled.isEnabled() || mc.options == null || mc.player == null) return;
        if (onlyWhileMoving.isEnabled()) {
            var v = mc.player.getVelocity();
            if (v.x * v.x + v.z * v.z < 1.0E-4) return;
        }

        if (++counter < interval.getValue().intValue()) return;
        counter = 0;

        try {
            int fps = mc.getCurrentFps();
            int target = targetFps.getValue().intValue();
            int stepSize = step.getValue().intValue();

            int view = mc.options.getViewDistance().getValue();
            int newView = view;

            if (fps < target) {
                newView = Math.max(minDistance.getValue().intValue(), view - stepSize);
            } else if (fps > target + hysteresis.getValue()) {
                newView = Math.min(maxDistance.getValue().intValue(), view + stepSize);
            }

            if (newView != view) {
                mc.options.getViewDistance().setValue(newView);
                if (notify.isEnabled()) {
                    com.anormal.client.util.NotificationManager.info("Render Distance", newView + " chunks");
                }
            }

            if (simulation.isEnabled()) {
                int sim = mc.options.getSimulationDistance().getValue();
                int newSim = sim;
                if (fps < target) newSim = Math.max(simMin.getValue().intValue(), sim - 1);
                else if (fps > target + hysteresis.getValue()) newSim = Math.min(simMax.getValue().intValue(), sim + 1);
                if (newSim != sim) mc.options.getSimulationDistance().setValue(newSim);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRender2D(net.minecraft.client.gui.DrawContext context, float tickDelta) {
        if (!isEnabled() || !showHud.isEnabled() || mc.textRenderer == null) return;
        try {
            int view = mc.options.getViewDistance().getValue();
            String line = "§6Distance §f" + view + " §7| §6FPS §f" + mc.getCurrentFps();
            int w = mc.textRenderer.getWidth(line);
            int x = mc.getWindow().getScaledWidth() / 2 - w / 2;
            int y = mc.getWindow().getScaledHeight() - 40;
            com.anormal.client.util.RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, 0xC00E1016);
            com.anormal.client.util.RenderUtils.drawText(context, mc.textRenderer, line, x, y, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {
        }
    }
}
