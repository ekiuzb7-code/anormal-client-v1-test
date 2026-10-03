package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {
    public final ColorSetting aimingColor = new ColorSetting("Aiming Color", "Trajectory color when it will hit an entity", ColorUtils.rgba(255, 60, 60, 230));
    public final ColorSetting trajectoryColor = new ColorSetting("Trajectory Color", "Trajectory color when it will not hit", ColorUtils.rgba(120, 220, 255, 200));
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Marker color at an entity intersection", ColorUtils.rgba(255, 220, 60, 255));
    public final BooleanSetting ghostBowCharge = new BooleanSetting("Ghost Bow Charge", "Shows full-charge trajectory without drawing", true);

    public Trajectories() {
        super("Trajectories", "Calculates and draws projectile trajectories for bow shots and pearls", Category.RENDER);
        addSetting(aimingColor);
        addSetting(trajectoryColor);
        addSetting(targetColor);
        addSetting(ghostBowCharge);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        String path;
        try {
            path = Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath();
        } catch (Throwable t) {
            return;
        }
        boolean bow = path.contains("bow");
        boolean throwable = path.equals("ender_pearl") || path.contains("splash_potion") || path.contains("lingering_potion")
                || path.equals("egg") || path.equals("snowball") || path.equals("wind_charge") || path.equals("trident");
        if (!bow && !throwable) return;
        double power;
        if (bow && !path.contains("cross")) {
            int useTime = 0;
            boolean using = false;
            try {
                using = mc.player.isUsingItem();
                useTime = mc.player.getItemUseTime();
            } catch (Throwable ignored) {}
            if (!using && !ghostBowCharge.isEnabled()) return;
            power = using ? Math.max(0.15, Math.min(1.0, useTime / 20.0)) : 1.0;
        } else power = 1.0;
        Vec3d look;
        Vec3d eye;
        try {
            look = mc.player.getRotationVec(tickDelta);
            eye = mc.player.getEyePos();
        } catch (Throwable t) {
            return;
        }
        double speed = bow ? 3.0 * power : path.equals("ender_pearl") ? 1.5 : path.contains("potion") ? 0.5 : 1.5;
        double gravity = bow ? 0.05 : 0.03;
        Vec3d pos = eye;
        Vec3d vel = look.multiply(speed);
        List<int[]> points = new ArrayList<>();
        LivingEntity hitEnt = null;
        try {
            for (int i = 0; i < 80; i++) {
                vel = vel.multiply(0.99);
                vel = vel.add(0, -gravity, 0);
                pos = pos.add(vel);
                BlockPos bpos;
                try {
                    bpos = BlockPos.ofFloored(pos);
                    if (!mc.world.getBlockState(bpos).isAir()) break;
                } catch (Throwable t) {
                    break;
                }
                for (Entity e : mc.world.getEntities()) {
                    if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) continue;
                    try {
                        if (living.getBoundingBox().expand(0.3).contains(pos)) {
                            hitEnt = living;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
                if (hitEnt != null) break;
                int[] s = com.anormal.client.util.ProjectionUtil.project(pos, tickDelta);
                if (s != null) {
                    if (!points.isEmpty()) {
                        int[] last = points.get(points.size() - 1);
                        if (Math.abs(s[0] - last[0]) + Math.abs(s[1] - last[1]) > 400) break;
                    }
                    points.add(s);
                }
            }
        } catch (Throwable ignored) {}
        int col = hitEnt != null ? aimingColor.getValue() : trajectoryColor.getValue();
        for (int i = 1; i < points.size(); i++) {
            int[] a = points.get(i - 1), b = points.get(i);
            line(context, a[0], a[1], b[0], b[1], col);
        }
        if (hitEnt != null) {
            int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(hitEnt.getX(), hitEnt.getY() + hitEnt.getHeight() / 2.0, hitEnt.getZ()), tickDelta);
            if (s != null) {
                int c = targetColor.getValue();
                context.fill(s[0] - 4, s[1] - 4, s[0] + 4, s[1] - 3, c);
                context.fill(s[0] - 4, s[1] + 3, s[0] + 4, s[1] + 4, c);
                context.fill(s[0] - 4, s[1] - 3, s[0] - 3, s[1] + 3, c);
                context.fill(s[0] + 3, s[1] - 3, s[0] + 4, s[1] + 3, c);
            }
        } else if (!points.isEmpty()) {
            int[] last = points.get(points.size() - 1);
            context.fill(last[0] - 1, last[1] - 1, last[0] + 2, last[1] + 2, trajectoryColor.getValue());
        }
    }



    private void line(DrawContext context, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.min(40, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        if (steps == 0) {
            context.fill(x1, y1, x1 + 1, y1 + 1, col);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            context.fill(x, y, x + 1, y + 1, col);
        }
    }
}
