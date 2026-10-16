package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Stops rendering entities that are already hidden by fog.
 *
 * <p>Vanilla still submits every entity to the renderer even when it sits
 * beyond the fog wall, where it is invisible anyway. Cancelling those submissions
 * costs nothing visually and saves a lot of work in crowded areas.</p>
 */
public class FogCull extends Optimizer {

    public final BooleanSetting cullEntities = new BooleanSetting("Cull Entities", "Skip entities beyond the fog", true);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Entities further away than this are culled", 48.0, 8.0, 256.0, 4.0);
    public final BooleanSetting useFogDistance = new BooleanSetting("Use Fog Distance", "Derive the limit from the current render distance", true);
    public final NumberSetting fogFactor = new NumberSetting("Fog Factor", "Fraction of the render distance used as the limit", 0.85, 0.3, 1.0, 0.05);
    public final BooleanSetting cullBlockEntities = new BooleanSetting("Cull Block Entities", "Skip chests, signs and banners that far away", true);
    public final BooleanSetting cullNametags = new BooleanSetting("Cull Nametags", "Skip nametags beyond the limit", true);
    public final BooleanSetting ignorePlayers = new BooleanSetting("Ignore Players", "Never cull other players", true);
    public final BooleanSetting ignoreBosses = new BooleanSetting("Ignore Bosses", "Never cull bosses", true);
    public final BooleanSetting ignoreVehicles = new BooleanSetting("Ignore Vehicles", "Never cull boats, minecarts and horses you ride", false);
    public final BooleanSetting ignoreOwned = new BooleanSetting("Ignore Tamed", "Never cull tamed animals", false);
    public final BooleanSetting showCulled = new BooleanSetting("Show Culled", "Show how many entities were culled", false);

    private int culled;

    public FogCull() {
        super("FogCull", "Stops rendering entities that fog already hides");
        addSetting(cullEntities);
        addSetting(maxDistance);
        addSetting(useFogDistance);
        addSetting(fogFactor);
        addSetting(cullBlockEntities);
        addSetting(cullNametags);
        addSetting(ignorePlayers);
        addSetting(ignoreBosses);
        addSetting(ignoreVehicles);
        addSetting(ignoreOwned);
        addSetting(showCulled);
    }

    @Override
    public void onEnable() {
        culled = 0;
    }

    /** Effective cull distance in blocks. */
    public double limit() {
        if (useFogDistance.isEnabled()) {
            try {
                int view = mc.options.getViewDistance().getValue();
                return view * 16.0 * fogFactor.getValue();
            } catch (Throwable ignored) {
            }
        }
        return maxDistance.getValue();
    }

    /** @return true when this entity should not be submitted for rendering. */
    public static boolean shouldCull(Entity entity) {
        try {
            FogCull m = com.anormal.client.module.ModuleManager.getModule(FogCull.class);
            if (m == null || !m.isEnabled() || !m.cullEntities.isEnabled()) return false;
            return m.check(entity);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean check(Entity e) {
        if (e == null || mc.player == null) return false;
        if (e == mc.player) return false;

        if (ignorePlayers.isEnabled() && e instanceof PlayerEntity) return false;
        if (ignoreBosses.isEnabled() && (e instanceof EnderDragonEntity || e instanceof WitherEntity)) return false;
        if (ignoreVehicles.isEnabled() && e.hasPassengers()) return false;
        if (ignoreOwned.isEnabled()) {
            try {
                if (e instanceof net.minecraft.entity.passive.TameableEntity tame && tame.isTamed()) return false;
            } catch (Throwable ignored) {
            }
        }

        double limit = limit();
        boolean cull = mc.player.squaredDistanceTo(e) > limit * limit;
        if (cull) culled++;
        return cull;
    }

    public int getCulled() {
        return culled;
    }
}
