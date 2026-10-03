package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Tracers extends Module {
    public final BooleanSetting renderPlayers = new BooleanSetting("Render Players", "Tracers to player entities", true);
    public final BooleanSetting playerCheck = new BooleanSetting("Player Distance Check", "Limits player tracers to a distance band", false);
    public final NumberSetting playerMin = new NumberSetting("Player Min", "Minimum player tracer distance", 0.0, 0.0, 64.0, 1.0);
    public final NumberSetting playerMax = new NumberSetting("Player Max", "Maximum player tracer distance", 64.0, 4.0, 128.0, 4.0);
    public final ColorSetting playerColor = new ColorSetting("Player Color", "Color for player tracers", ColorUtils.rgba(255, 80, 80, 200));
    public final BooleanSetting renderAnimals = new BooleanSetting("Render Animals", "Tracers to peaceful animals", false);
    public final BooleanSetting animalCheck = new BooleanSetting("Animal Distance Check", "Limits animal tracers to a distance band", false);
    public final NumberSetting animalMin = new NumberSetting("Animal Min", "Minimum animal tracer distance", 0.0, 0.0, 64.0, 1.0);
    public final NumberSetting animalMax = new NumberSetting("Animal Max", "Maximum animal tracer distance", 48.0, 4.0, 128.0, 4.0);
    public final ColorSetting animalColor = new ColorSetting("Animal Color", "Color for animal tracers", ColorUtils.rgba(80, 255, 120, 200));
    public final BooleanSetting renderMobs = new BooleanSetting("Render Mobs", "Tracers to hostile mobs", false);
    public final BooleanSetting mobCheck = new BooleanSetting("Mob Distance Check", "Limits mob tracers to a distance band", false);
    public final NumberSetting mobMin = new NumberSetting("Mob Min", "Minimum mob tracer distance", 0.0, 0.0, 64.0, 1.0);
    public final NumberSetting mobMax = new NumberSetting("Mob Max", "Maximum mob tracer distance", 48.0, 4.0, 128.0, 4.0);
    public final ColorSetting mobColor = new ColorSetting("Mob Color", "Color for mob tracers", ColorUtils.rgba(255, 180, 60, 200));
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Draws tracers to invisible entities", false);
    public final BooleanSetting colorByDistance = new BooleanSetting("Color By Distance", "Nearby red, distant green", false);
    public final BooleanSetting focusHighlight = new BooleanSetting("Highlight If Focusing", "Highlights tracers of entities looking at you", true);

    public Tracers() {
        super("Tracers", "Renders directional lines from crosshair towards nearby entities", Category.RENDER);
        addSetting(renderPlayers);
        addSetting(playerCheck);
        addSetting(playerMin);
        addSetting(playerMax);
        addSetting(playerColor);
        addSetting(renderAnimals);
        addSetting(animalCheck);
        addSetting(animalMin);
        addSetting(animalMax);
        addSetting(animalColor);
        addSetting(renderMobs);
        addSetting(mobCheck);
        addSetting(mobMin);
        addSetting(mobMax);
        addSetting(mobColor);
        addSetting(invisibles);
        addSetting(colorByDistance);
        addSetting(focusHighlight);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        try {
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player || !e.isAlive()) continue;
                if (e.isInvisible() && !invisibles.isEnabled()) continue;
                int base;
                double dist = mc.player.distanceTo(e);
                if (e instanceof PlayerEntity) {
                    if (!renderPlayers.isEnabled()) continue;
                    if (playerCheck.isEnabled() && (dist < playerMin.getValue() || dist > playerMax.getValue())) continue;
                    base = playerColor.getValue();
                } else if (e instanceof AnimalEntity) {
                    if (!renderAnimals.isEnabled()) continue;
                    if (animalCheck.isEnabled() && (dist < animalMin.getValue() || dist > animalMax.getValue())) continue;
                    base = animalColor.getValue();
                } else if (e instanceof Monster) {
                    if (!renderMobs.isEnabled()) continue;
                    if (mobCheck.isEnabled() && (dist < mobMin.getValue() || dist > mobMax.getValue())) continue;
                    base = mobColor.getValue();
                } else continue;
                int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(e.getX(), e.getY() + e.getHeight() / 2.0, e.getZ()), tickDelta);
                if (s == null) continue;
                int col = colorByDistance.isEnabled() ? byDistance(dist) : base;
                line(context, w / 2, h / 2, s[0], s[1], col);
                if (focusHighlight.isEnabled() && e instanceof LivingEntity living && focusing(living)) {
                    line(context, w / 2 + 1, h / 2, s[0] + 1, s[1], 0xFFFFFFFF);
                    context.fill(s[0] - 2, s[1] - 2, s[0] + 2, s[1] + 2, 0xFFFFFFFF);
                }
            }
        } catch (Throwable ignored) {}
    }

    private int byDistance(double dist) {
        float f = (float) Math.max(0, Math.min(1, dist / 64.0));
        int r = (int) (255 * (1 - f));
        int g = (int) (60 + 195 * f);
        return ColorUtils.rgba(r, g, 60, 200);
    }

    private boolean focusing(LivingEntity e) {
        try {
            Vec3d look = e.getRotationVec(1.0f);
            Vec3d toMe = mc.player.getEyePos().subtract(new Vec3d(e.getX(), e.getEyeY(), e.getZ())).normalize();
            return look.dotProduct(toMe) > 0.85;
        } catch (Throwable t) {
            return false;
        }
    }



    private void line(DrawContext context, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.min(300, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
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
