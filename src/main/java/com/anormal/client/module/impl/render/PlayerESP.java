package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.PlayerColorizer;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerESP extends Module {
    public final ModeSetting style = new ModeSetting("Style", "ESP visual style", "Lines and Boxes", "Lines and Boxes", "Lines Only", "Boxes Only", "Tracers Only", "Body Only");
    public final ModeSetting boxMode = new ModeSetting("Box Mode", "Box accuracy mode", "Accurate", "Accurate", "Fancy");
    public final BooleanSetting friends = new BooleanSetting("Friends", "Highlight friends with custom color", true);
    public final ColorSetting friendsColor = new ColorSetting("Friends Color", "Color for friends", ColorUtils.rgba(0, 128, 255, 255));
    public final BooleanSetting sleeping = new BooleanSetting("Show Sleeping", "Show sleeping players", false);
    public final BooleanSetting invisible = new BooleanSetting("Show Invisible", "Show invisible players", false);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Maximum render distance", 100.0, 10.0, 300.0, 10.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range",
            "How far above and below you to search", 64.0, 8.0, 200.0, 4.0);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", "Show health bar on boxes", true);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show player name", true);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", "Tracer/line width", 1.0, 0.5, 3.0, 0.5);

    // --- body colouring -------------------------------------------------
    public final ModeSetting bodyMode = new ModeSetting("Body Mode", "How the player model is painted", "Off", "Off", "Flat", "Tint");
    public final ColorSetting bodyColor = new ColorSetting("Body Color", "Colour used for the player model", ColorUtils.rgba(255, 60, 60, 255));
    public final ColorSetting bodyWallColor = new ColorSetting("Body Wall Color", "Colour used when the player is behind walls", ColorUtils.rgba(255, 170, 0, 255));
    public final BooleanSetting bodyFriends = new BooleanSetting("Body Friends", "Use a separate colour for friends", true);
    public final ColorSetting bodyFriendsColor = new ColorSetting("Body Friends Color", "Colour used for friends", ColorUtils.rgba(0, 160, 255, 255));
    public final BooleanSetting bodyDistance = new BooleanSetting("Body Distance Color", "Blend the colour toward red with distance", false);
    public final NumberSetting bodyTint = new NumberSetting("Body Tint Strength", "How strongly the tint is applied (Tint mode)", 70.0, 10.0, 100.0, 5.0);
    public final BooleanSetting bodySelf = new BooleanSetting("Body Self", "Also paint your own model in third person", false);
    public final BooleanSetting bodyRainbow = new BooleanSetting("Body Rainbow", "Cycle the body hue over time", false);
    public final NumberSetting bodyRainbowSpeed = new NumberSetting("Body Rainbow Speed", "How fast the rainbow cycles", 1.0, 0.2, 5.0, 0.1);
    public final BooleanSetting bodyFlash = new BooleanSetting("Body Damage Flash", "Flash the model red when the player takes damage", true);

    // --- body (real model) through walls ---------------------------------
    public final BooleanSetting bodyGlow = new BooleanSetting("Body Glow", "Show the real player model (skin, armor, items) through walls using a glow outline. Always on in 'Body Only' style", false);

    private final java.util.Set<UUID> glowing = new java.util.HashSet<>();
    private final List<PlayerEntity> cachedPlayers = new ArrayList<>();
    private final Map<UUID, Float> lastHealth = new HashMap<>();
    private final Map<UUID, Long> flashUntil = new HashMap<>();

    public PlayerESP() {
        super("PlayerESP", "Advanced player ESP with tracers, boxes, and distance-based colors", Category.RENDER);
        addSetting(style);
        addSetting(boxMode);
        addSetting(friends);
        addSetting(friendsColor);
        addSetting(sleeping);
        addSetting(invisible);
        addSetting(maxDistance);
        addSetting(verticalRange);
        addSetting(healthBar);
        addSetting(showName);
        addSetting(lineWidth);
        addSetting(bodyMode);
        addSetting(bodyColor);
        addSetting(bodyWallColor);
        addSetting(bodyFriends);
        addSetting(bodyFriendsColor);
        addSetting(bodyDistance);
        addSetting(bodyTint);
        addSetting(bodySelf);
        addSetting(bodyRainbow);
        addSetting(bodyRainbowSpeed);
        addSetting(bodyFlash);
        addSetting(bodyGlow);
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
            if (Math.abs(player.getY() - mc.player.getY()) > verticalRange.getValue()) continue;

            cachedPlayers.add(player);
        }

        trackDamage();
        updateBodyColors();
        updateGlow();
    }

    /**
     * 'Body Only' draws no boxes, tracers, health bars or names: the player's real model (skin,
     * armor, held items) is what you see, and the glow outline keeps it visible through walls.
     */
    private boolean bodyOnly() {
        return style.is("Body Only");
    }

    private void updateGlow() {
        if (mc.world == null) return;
        boolean active = bodyOnly() || bodyGlow.isEnabled();
        boolean chamsOn = isChamsActive();

        try {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                UUID id = p.getUuid();
                if (active && cachedPlayers.contains(p)) {
                    p.setGlowing(true); // re-applied every tick: server entity data may reset it
                    glowing.add(id);
                } else if (glowing.remove(id) && !chamsOn) {
                    p.setGlowing(false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void clearGlow() {
        if (mc.world != null && !glowing.isEmpty() && !isChamsActive()) {
            try {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    if (glowing.contains(p.getUuid())) p.setGlowing(false);
                }
            } catch (Throwable ignored) {
            }
        }
        glowing.clear();
    }

    private boolean isChamsActive() {
        try {
            Chams chams = com.anormal.client.module.ModuleManager.getModule(Chams.class);
            return chams != null && chams.isEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        clearGlow();
        PlayerColorizer.reset();
        lastHealth.clear();
        flashUntil.clear();
    }

    // ------------------------------------------------------------------
    //  Body colouring
    // ------------------------------------------------------------------

    private void updateBodyColors() {
        int mode = bodyMode.is("Flat") ? PlayerColorizer.FLAT
                : bodyMode.is("Tint") ? PlayerColorizer.TINT
                : PlayerColorizer.OFF;

        PlayerColorizer.setMode(mode);
        if (mode == PlayerColorizer.OFF) return;

        PlayerColorizer.setTintStrength(bodyTint.getValue().floatValue() / 100f);
        PlayerColorizer.beginTick();

        for (PlayerEntity player : cachedPlayers) {
            PlayerColorizer.track(player, resolveBodyColor(player));
        }
        if (bodySelf.isEnabled() && mc.player != null) {
            PlayerColorizer.track(mc.player, resolveBodyColor(mc.player));
        }
    }

    /** Remembers when a player lost health so the body can flash red for a moment. */
    private void trackDamage() {
        long now = System.currentTimeMillis();
        for (PlayerEntity player : cachedPlayers) {
            UUID id = player.getUuid();
            Float previous = lastHealth.get(id);
            if (previous != null && player.getHealth() < previous - 0.01f) {
                flashUntil.put(id, now + 350L);
            }
            lastHealth.put(id, player.getHealth());
        }
    }

    private int resolveBodyColor(PlayerEntity player) {
        if (bodyFlash.isEnabled()) {
            Long until = flashUntil.get(player.getUuid());
            if (until != null && System.currentTimeMillis() < until) {
                return ColorUtils.rgba(255, 45, 45, 255);
            }
        }

        if (bodyRainbow.isEnabled()) return rainbowColor();

        int color = bodyColor.getValue();
        if (bodyFriends.isEnabled() && isFriend(player)) color = bodyFriendsColor.getValue();

        if (bodyDistance.isEnabled() && mc.player != null) {
            float limit = Math.max(1f, maxDistance.getValue().floatValue());
            float ratio = Math.min(1f, mc.player.distanceTo(player) / limit);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            color = ColorUtils.rgba(
                    (int) (r + (255 - r) * ratio),
                    (int) (g * (1f - ratio)),
                    (int) (b * (1f - ratio)),
                    255);
        }

        if (behindWalls(player)) color = bodyWallColor.getValue();
        return color;
    }

    private boolean behindWalls(PlayerEntity player) {
        if (mc.player == null || mc.world == null) return false;
        try {
            Vec3d from = mc.player.getEyePos();
            Vec3d to = new Vec3d(player.getX(), player.getEyeY(), player.getZ());
            RaycastContext ctx = new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mc.player);
            return mc.world.raycast(ctx).getType() != HitResult.Type.MISS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int rainbowColor() {
        float speed = bodyRainbowSpeed.getValue().floatValue();
        float hue = (System.currentTimeMillis() % 12000L) / 12000f * speed;
        hue -= (float) Math.floor(hue);
        // Quantised so the texture cache stays small while the hue keeps cycling.
        hue = Math.round(hue * 24f) / 24f;
        return hsv(hue, 0.8f, 1.0f);
    }

    private static int hsv(float h, float s, float v) {
        int slice = (int) (h * 6f) % 6;
        float f = h * 6f - (int) (h * 6f);
        int p = (int) (v * (1f - s) * 255f);
        int q = (int) (v * (1f - s * f) * 255f);
        int t = (int) (v * (1f - s * (1f - f)) * 255f);
        int max = (int) (v * 255f);
        return switch (slice) {
            case 0 -> ColorUtils.rgba(max, t, p, 255);
            case 1 -> ColorUtils.rgba(q, max, p, 255);
            case 2 -> ColorUtils.rgba(p, max, t, 255);
            case 3 -> ColorUtils.rgba(p, q, max, 255);
            case 4 -> ColorUtils.rgba(t, p, max, 255);
            default -> ColorUtils.rgba(max, p, q, 255);
        };
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.player == null || cachedPlayers.isEmpty()) return;
        if (bodyOnly()) return; // body only: no 2D overlays at all

        boolean drawBoxes = style.is("Lines and Boxes") || style.is("Boxes Only");
        boolean drawLines = style.is("Lines and Boxes") || style.is("Lines Only");
        boolean drawTracers = style.is("Lines and Boxes") || style.is("Tracers Only");

        for (PlayerEntity player : cachedPlayers) {
            int color = getPlayerColor(player);

            // Real interpolated AABB (no 1/16 rounding, no derived width).
            Box box = player.getBoundingBox();
            double hgt = Math.max(0.1, box.maxY - box.minY);
            double wdt = Math.max(box.maxX - box.minX, box.maxZ - box.minZ);

            Vec3d lerped = com.anormal.client.util.ProjectionUtil.lerpPos(player, tickDelta);
            double cx0 = lerped.x;
            double cz0 = lerped.z;
            double cyMid = lerped.y + hgt / 2.0;

            int[] sTop = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid + hgt / 2.0 + 0.1, cz0), tickDelta);
            int[] sBot = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid - hgt / 2.0, cz0), tickDelta);
            int[] sMid = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid, cz0), tickDelta);

            if (sTop == null || sBot == null || sMid == null) continue;

            int h = Math.max(4, Math.abs(sBot[1] - sTop[1]));
            int w = Math.max(4, (int) (h * (wdt / hgt)));
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