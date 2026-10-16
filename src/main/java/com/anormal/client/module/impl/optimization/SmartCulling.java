package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Skips entities that cannot possibly be seen.
 *
 * <p>Unlike {@link FogCull} (which uses distance) this module reasons about the
 * camera: an entity behind you, or hidden behind a solid wall, contributes
 * nothing to the frame. Both checks are conservative, so nothing pops in and
 * out of view while you look at it.</p>
 */
public class SmartCulling extends Optimizer {

    public final BooleanSetting enabled = new BooleanSetting("Smart Culling", "Master switch", true);
    public final BooleanSetting frustum = new BooleanSetting("Behind Camera", "Skip entities outside the view frustum", true);
    public final NumberSetting fovMargin = new NumberSetting("FOV Margin", "Extra degrees added to the frustum test", 20.0, 0.0, 90.0, 5.0);
    public final BooleanSetting occlusion = new BooleanSetting("Behind Walls", "Skip entities with no line of sight", false);
    public final NumberSetting occlusionDistance = new NumberSetting("Occlusion Distance", "Only test walls beyond this distance", 16.0, 4.0, 96.0, 4.0);
    public final BooleanSetting skipNametags = new BooleanSetting("Skip Nametags", "Skip nametags for culled entities", true);
    public final BooleanSetting skipShadows = new BooleanSetting("Skip Shadows", "Skip shadows for culled entities", true);
    public final BooleanSetting skipAnimations = new BooleanSetting("Skip Animations", "Skip animation updates for culled entities", true);
    public final NumberSetting updateInterval = new NumberSetting("Update Interval", "Ticks between full visibility re-checks", 5.0, 1.0, 40.0, 1.0);
    public final BooleanSetting ignorePlayers = new BooleanSetting("Ignore Players", "Never cull other players", true);
    public final BooleanSetting ignoreBosses = new BooleanSetting("Ignore Bosses", "Never cull bosses", true);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Never cull friends", true);
    public final BooleanSetting ignoreTargets = new BooleanSetting("Ignore Target", "Never cull the entity you are attacking", true);

    public SmartCulling() {
        super("SmartCulling", "Skips entities that are behind you or behind walls");
        addSetting(enabled);
        addSetting(frustum);
        addSetting(fovMargin);
        addSetting(occlusion);
        addSetting(occlusionDistance);
        addSetting(skipNametags);
        addSetting(skipShadows);
        addSetting(skipAnimations);
        addSetting(updateInterval);
        addSetting(ignorePlayers);
        addSetting(ignoreBosses);
        addSetting(ignoreFriends);
        addSetting(ignoreTargets);
    }

    public static boolean shouldCull(Entity entity) {
        try {
            SmartCulling m = com.anormal.client.module.ModuleManager.getModule(SmartCulling.class);
            if (m == null || !m.isEnabled() || !m.enabled.isEnabled()) return false;
            return m.check(entity);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean check(Entity e) {
        if (e == null || mc.player == null || e == mc.player) return false;

        if (ignorePlayers.isEnabled() && e instanceof PlayerEntity) return false;
        if (ignoreBosses.isEnabled() && (e instanceof EnderDragonEntity || e instanceof WitherEntity)) return false;
        if (ignoreTargets.isEnabled() && mc.targetedEntity == e) return false;
        if (ignoreFriends.isEnabled() && e instanceof PlayerEntity p) {
            try {
                if (com.anormal.client.module.impl.client.Friends.isFriend(p.getGameProfile().name())) return false;
            } catch (Throwable ignored) {
            }
        }

        if (frustum.isEnabled() && !inFrustum(e)) return true;

        if (occlusion.isEnabled()) {
            double d = Math.sqrt(mc.player.squaredDistanceTo(e));
            if (d > occlusionDistance.getValue() && !mc.player.canSee(e)) return true;
        }

        return false;
    }

    private boolean inFrustum(Entity e) {
        try {
            var dir = new net.minecraft.util.math.Vec3d(e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ())
                    .subtract(mc.player.getEyePos());
            if (dir.lengthSquared() < 0.001) return true;
            dir = dir.normalize();
            var look = mc.player.getRotationVec(1.0f).normalize();
            double cos = look.dotProduct(dir);
            double limit = Math.cos(Math.toRadians(90.0 + fovMargin.getValue()));
            return cos >= limit;
        } catch (Throwable t) {
            return true;
        }
    }
}
