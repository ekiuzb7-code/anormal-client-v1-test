package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ESP extends Module {
    public final ColorSetting playerColor = new ColorSetting("Player Color", "Color for player entities", ColorUtils.rgba(255, 60, 60, 255));
    public final ModeSetting mode = new ModeSetting("Mode", "ESP render style", "2D", "3D", "2D", "Skeleton", "Outline");
    public final BooleanSetting boundingBox = new BooleanSetting("Bounding Box", "2D bounding square", true);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", "Vertical health bar", true);
    public final BooleanSetting name = new BooleanSetting("Name", "Name above box", true);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Render invisible entities", false);
    public final BooleanSetting hideBots = new BooleanSetting("Hide Bots", "Hide anticheat bots", true);

    public ESP() {
        super("ESP", "Renders entities through walls", Category.UZNY11);
        addSetting(playerColor);
        addSetting(mode);
        addSetting(boundingBox);
        addSetting(healthBar);
        addSetting(name);
        addSetting(invisibles);
        addSetting(hideBots);
    }

    private boolean wanted(Entity entity) {
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity)) return false;
        if (!invisibles.isEnabled() && entity.isInvisible()) return false;
        if (hideBots.isEnabled() && com.anormal.client.module.impl.client.AntiBot.isBot(entity)) return false;
        return true;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        boolean glow = mode.is("3D") || mode.is("Outline");
        for (Entity entity : mc.world.getEntities()) {
            try {
                entity.setGlowing(wanted(entity) && glow);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity entity : mc.world.getEntities()) {
            try {
                entity.setGlowing(false);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mode.is("3D") || mode.is("Outline") || mc.world == null || mc.player == null) return;
        boolean corners = mode.is("Skeleton");
        for (Entity entity : mc.world.getEntities()) {
            try {
                if (!wanted(entity)) continue;
                Box box = entity.getBoundingBox();
                double hgt = box.maxY - box.minY;
                double[] lc = com.anormal.client.util.ProjectionUtil.lerpEntity(entity.getId(),
                        (box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0,
                        (box.minZ + box.maxZ) / 2.0, tickDelta);
                double cx0 = Math.round(lc[0] * 16.0) / 16.0;
                double cz0 = Math.round(lc[2] * 16.0) / 16.0;
                double cyMid = lc[1];
                int[] sTop = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid + hgt / 2.0 + 0.1, cz0), tickDelta);
                int[] sBot = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid - hgt / 2.0, cz0), tickDelta);
                int[] sMid = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid, cz0), tickDelta);
                if (sTop == null || sBot == null || sMid == null) continue;
                int h = Math.max(4, sBot[1] - sTop[1]);
                int w = Math.max(4, h / 3);
                int x = sMid[0];
                // Bottom-anchored: feet projection is exact, top derives from height.
                int yBot = Math.max(sTop[1], sBot[1]);
                int yTop = yBot - h;
                int col = playerColor.getValue();

                if (boundingBox.isEnabled()) {
                    if (corners) {
                        int cl = Math.min(w / 3, 8);
                        context.fill(x - w / 2, yTop, x - w / 2 + cl, yTop + 1, col);
                        context.fill(x - w / 2, yTop, x - w / 2 + 1, yTop + cl, col);
                        context.fill(x + w / 2 - cl, yTop, x + w / 2, yTop + 1, col);
                        context.fill(x + w / 2 - 1, yTop, x + w / 2, yTop + cl, col);
                        context.fill(x - w / 2, yBot - 1, x - w / 2 + cl, yBot, col);
                        context.fill(x - w / 2, yBot - cl, x - w / 2 + 1, yBot, col);
                        context.fill(x + w / 2 - cl, yBot - 1, x + w / 2, yBot, col);
                        context.fill(x + w / 2 - 1, yBot - cl, x + w / 2, yBot, col);
                    } else {
                        context.fill(x - w / 2, yTop, x + w / 2, yTop + 1, col);
                        context.fill(x - w / 2, yBot - 1, x + w / 2, yBot, col);
                        context.fill(x - w / 2, yTop, x - w / 2 + 1, yBot, col);
                        context.fill(x + w / 2 - 1, yTop, x + w / 2, yBot, col);
                    }
                }
                if (name.isEnabled() && mc.textRenderer != null) {
                    String nm = entity.getName().getString();
                    RenderUtils.drawText(context, mc.textRenderer, nm, x - mc.textRenderer.getWidth(nm) / 2, yTop - 11, 0xFFFFFFFF, true);
                }
                if (healthBar.isEnabled() && entity instanceof LivingEntity living) {
                    float maxHp = Math.max(1.0f, living.getMaxHealth());
                    float pct = Math.max(0.0f, Math.min(1.0f, living.getHealth() / maxHp));
                    int barH = (int) (h * pct);
                    int barCol = pct > 0.6 ? 0xFF55FF55 : (pct > 0.3 ? 0xFFFFFF55 : 0xFFFF5555);
                    context.fill(x - w / 2 - 4, yTop, x - w / 2 - 2, yBot, 0xAA222222);
                    context.fill(x - w / 2 - 4, yBot - barH, x - w / 2 - 2, yBot, barCol);
                }
            } catch (Throwable ignored) {}
        }
    }
}
