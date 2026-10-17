package com.anormal.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the body colour of a player and keeps a cache of generated textures.
 *
 * <p>1.21.11 draws entities through {@code OrderedRenderCommandQueue}, so there is no vertex
 * stream left to tint. The only practical hook is the texture returned by
 * {@code PlayerEntityRenderer#getTexture}, therefore every colour is baked into its own
 * small texture and cached by (skin, colour, mode).</p>
 */
public final class PlayerColorizer {

    public static final int OFF = 0;
    public static final int FLAT = 1;
    public static final int TINT = 2;

    /** Hard cap so a cycling rainbow cannot leak textures forever. */
    private static final int MAX_TEXTURES = 96;

    private static int mode = OFF;
    private static float tintStrength = 0.7f;

    private static final Map<UUID, Integer> COLORS = new HashMap<>();
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static int sequence = 0;

    private PlayerColorizer() {
    }

    public static void setMode(int value) {
        if (value == mode) return;
        mode = value;
        if (mode == OFF) dropTextures();
    }

    public static void setTintStrength(float value) {
        tintStrength = Math.max(0f, Math.min(1f, value));
    }

    public static boolean isActive() {
        return mode != OFF;
    }

    /** Clears the per-tick colour table. Called once per client tick. */
    public static void beginTick() {
        COLORS.clear();
    }

    public static void track(PlayerEntity player, int argb) {
        if (player != null) COLORS.put(player.getUuid(), argb);
    }

    public static void reset() {
        mode = OFF;
        COLORS.clear();
        dropTextures();
    }

    /** @return the texture to draw instead of the skin, or {@code null} to keep vanilla. */
    public static Identifier textureFor(PlayerEntity player, Identifier skin) {
        if (mode == OFF || player == null || skin == null) return null;
        Integer argb = COLORS.get(player.getUuid());
        if (argb == null) return null;
        return resolve(skin, argb, mode == TINT);
    }

    private static Identifier resolve(Identifier skin, int argb, boolean tint) {
        String key = (tint ? "t:" : "f:") + skin + ':' + Integer.toHexString(argb)
                + ':' + Math.round(tintStrength * 100f);

        Identifier known = CACHE.get(key);
        if (known != null) return known;

        try {
            NativeImage image = bake(skin, argb, tint);
            if (image == null) return null;

            if (CACHE.size() >= MAX_TEXTURES) dropTextures();

            Identifier id = Identifier.of("anormalclient", "gen/body_" + (sequence++));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "anormal-body", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            CACHE.put(key, id);
            return id;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NativeImage bake(Identifier skin, int argb, boolean tint) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        if (!tint) {
            NativeImage image = new NativeImage(64, 64, false);
            image.fillRect(0, 0, 64, 64, 0xFF000000 | (r << 16) | (g << 8) | b);
            return image;
        }

        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(skin);
        if (!(texture instanceof NativeImageBackedTexture backed)) return null;
        NativeImage source = backed.getImage();
        if (source == null) return null;

        float strength = tintStrength;
        return source.applyToCopy(pixel -> {
            int alpha = (pixel >>> 24) & 0xFF;
            if (alpha == 0) return pixel;

            int pr = (pixel >> 16) & 0xFF;
            int pg = (pixel >> 8) & 0xFF;
            int pb = pixel & 0xFF;

            int nr = Math.round(pr + (pr * r / 255f - pr) * strength);
            int ng = Math.round(pg + (pg * g / 255f - pg) * strength);
            int nb = Math.round(pb + (pb * b / 255f - pb) * strength);

            return (alpha << 24) | (nr << 16) | (ng << 8) | nb;
        });
    }

    private static void dropTextures() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Identifier id : CACHE.values()) {
            try {
                mc.getTextureManager().destroyTexture(id);
            } catch (Throwable ignored) {
            }
        }
        CACHE.clear();
    }
}
