package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class PlayerESP extends Module {
    public final ModeSetting style = new ModeSetting("Style", "ESP visual style", "Lines and Boxes", "Lines and Boxes", "Lines Only", "Boxes Only", "Tracers Only");
    public final ModeSetting boxMode = new ModeSetting("Box Mode", "Box accuracy mode", "Accurate", "Accurate", "Fancy");
    public final BooleanSetting friends = new BooleanSetting("Friends", "Highlight friends with custom color", true);
    public final ColorSetting friendsColor = new ColorSetting("Friends Color", "Color for friends", ColorUtils.rgba(0, 128, 255, 255));
    public final BooleanSetting sleeping = new BooleanSetting("Show Sleeping", "Show sleeping players", false);
    public final BooleanSetting invisible = new BooleanSetting("Show Invisible", "Show invisible players", false);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Maximum render distance", 100.0, 10.0, 300.0, 10.0);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", "Show health bar on boxes", true);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show player name", true);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", "Tracer/line width", 1.0, 0.5, 3.0, 0.5);

    private final List<PlayerEntity> cachedPlayers = new ArrayList<>();

    public PlayerESP() {
        super("PlayerESP", "Advanced player ESP with tracers, boxes, and distance-based colors", Category.RENDER);
        addSetting(style);
        addSetting(boxMode);
        addSetting(friends);
        addSetting(friendsColor);
        addSetting(sleeping);
        addSetting(invisible);
        addSetting(maxDistance);
        addSetting(healthBar);
        addSetting(showName);
        addSetting(lineWidth);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        cachedPlayers.clear();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;
            if (!player.isAlive()) continue;
            if (!sleeping.isEnabled() && player.isSleeping()) continue;
            if (!invisible.isEnabled() && player.isInvisible()) continue;
            if (mc.player.distanceTo(player) > maxDistance.getValue()) continue;

            cachedPlayers.add(player);
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.player == null || cachedPlayers.isEmpty()) return;

        boolean drawBoxes = style.is("Lines and Boxes") || style.is("Boxes Only");
        boolean drawLines = style.is("Lines and Boxes") || style.is("Lines Only");
        boolean drawTracers = style.is("Lines and Boxes") || style.is("Tracers Only");

        for (PlayerEntity player : cachedPlayers) {
            int color = getPlayerColor(player);

            // Get interpolated box
            Box box = player.getBoundingBox();
            double hgt = box.maxY - box.minY;

            double[] lc = com.anormal.client.util.ProjectionUtil.lerpEntity(player.getId(),
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
            int yBot = Math.max(sTop[1], sBot[1]);
            int yTop = yBot - h;

            // Draw boxes
            if (drawBoxes) {
                drawBox(context, x, yTop, yBot, w, h, color, boxMode.is("Fancy"));
            }

            // Draw tracers/lines from crosshair
            if (drawLines || drawTracers) {
                int centerX = mc.getWindow().getScaledWidth() / 2;
                int centerY = mc.getWindow().getScaledHeight() / 2;

                int tracerY = drawTracers ? centerY : yTop;
                RenderUtils.drawLine(context, centerX, tracerY, x, yTop, color, lineWidth.getValue().floatValue());
            }

            // Health bar
            if (healthBar.isEnabled() && player instanceof LivingEntity living) {
                float maxHp = Math.max(1.0f, living.getMaxHealth());
                float pct = Math.max(0.0f, Math.min(1.0f, living.getHealth() / maxHp));
                int barH = (int) (h * pct);
                int barCol = pct > 0.6 ? 0xFF55FF55 : (pct > 0.3 ? 0xFFFFFF55 : 0xFFFF5555);

                int barX = x - w / 2 - 4;
                context.fill(barX, yTop, barX + 2, yBot, 0xAA222222);
                context.fill(barX, yBot - barH, barX + 2, yBot, barCol);
            }

            // Name
            if (showName.isEnabled() && mc.textRenderer != null) {
                String name = player.getName().getString();
                int nameX = x - mc.textRenderer.getWidth(name) / 2;
                int nameY = yTop - 11;
                RenderUtils.drawText(context, mc.textRenderer, name, nameX, nameY, 0xFFFFFFFF, true);
            }
        }
    }

    private int getPlayerColor(PlayerEntity player) {
        if (friends.isEnabled() && isFriend(player)) {
            return friendsColor.getValue();
        }

        // Distance-based color (green close -> red far)
        float dist = mc.player.distanceTo(player) / 20.0f;
        float r = Math.min(1.0f, Math.max(0.0f, 2.0f - dist));
        float g = Math.min(1.0f, Math.max(0.0f, dist));
        float[] rgb = {r, g, 0};
        return RenderUtils.toIntColor(rgb, 0.5f);
    }

    private boolean isFriend(PlayerEntity player) {
        // Check if player is in friends list (assuming Friends module exists)
        try {
            var friendsModule = com.anormal.client.module.ModuleManager.getModule(com.anormal.client.module.impl.client.Friends.class);
            if (friendsModule != null && friendsModule.isEnabled()) {
                var friendsList = friendsModule.getClass().getDeclaredField("friends");
                friendsList.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<String> list = (java.util.List<String>) friendsList.get(friendsModule);
                return list.contains(player.getName().getString());
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void drawBox(DrawContext context, int x, int yTop, int yBot, int w, int h, int color, boolean fancy) {
        if (fancy) {
            // Fancy: slightly larger box
            int pad = 2;
            context.fill(x - w / 2 - pad, yTop - pad, x + w / 2 + pad, yTop - pad + 1, color);
            context.fill(x - w / 2 - pad, yBot + pad - 1, x + w / 2 + pad, yBot + pad, color);
            context.fill(x - w / 2 - pad, yTop, x - w / 2 - pad + 1, yBot, color);
            context.fill(x + w / 2 + pad - 1, yTop, x + w / 2 + pad, yBot, color);
        } else {
            // Accurate: tight box
            context.fill(x - w / 2, yTop, x + w / 2, yTop + 1, color);
            context.fill(x - w / 2, yBot - 1, x + w / 2, yBot, color);
            context.fill(x - w / 2, yTop, x - w / 2 + 1, yBot, color);
            context.fill(x + w / 2 - 1, yTop, x + w / 2, yBot, color);
        }
    }
}