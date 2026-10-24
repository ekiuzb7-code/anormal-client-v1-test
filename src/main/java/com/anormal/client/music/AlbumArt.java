package com.anormal.client.music;

import com.anormal.client.lucid.LucidLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a track's embedded cover into a registered texture, once per track.
 *
 * <p>Registration happens on first use, which is the render thread, so the texture is never created
 * from a worker thread. Entries are capped: a long queue would otherwise keep every cover in VRAM
 * forever.</p>
 */
public final class AlbumArt {

    private static final int MAX_CACHED = 24;

    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Map<String, NativeImageBackedTexture> TEXTURES = new HashMap<>();

    private AlbumArt() {
    }

    /** @return the texture for this track, or null when it has no cover */
    public static Identifier get(MusicTrack track) {
        if (track == null) return null;
        String key = track.path.toString();
        if (CACHE.containsKey(key)) return CACHE.get(key);

        BufferedImage image = track.cover();
        if (image == null) {
            CACHE.put(key, null);
            return null;
        }

        try {
            NativeImage nativeImage = toNativeImage(image);
            Identifier id = Identifier.of("anormal", "albumart/" + Math.abs(key.hashCode()));

            if (CACHE.size() >= MAX_CACHED) {
                evictOldest();
            }

            NativeImageBackedTexture texture =
                    new NativeImageBackedTexture(() -> "anormal-albumart", nativeImage);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);

            CACHE.put(key, id);
            TEXTURES.put(key, texture);
            return id;
        } catch (Throwable error) {
            LucidLog.LOGGER.warn("could not build album art for {}", track.path, error);
            CACHE.put(key, null);
            return null;
        }
    }

    private static void evictOldest() {
        String victim = CACHE.keySet().iterator().next();
        CACHE.remove(victim);
        NativeImageBackedTexture old = TEXTURES.remove(victim);
        if (old != null) {
            try {
                old.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static NativeImage toNativeImage(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                // NativeImage expects ABGR little-endian packing.
                out.setColorArgb(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return out;
    }
}
