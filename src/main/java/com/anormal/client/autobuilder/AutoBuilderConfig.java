package com.anormal.client.autobuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persisted AutoBuilder state. Written to {@code config/anormalclient/autobuilder.json}.
 *
 * <p>Every value is validated on load, so a hand edited or truncated file falls back to the
 * defaults instead of breaking the module.</p>
 */
public final class AutoBuilderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "autobuilder.json";

    public String directory = "";
    public String lastSchematic = "";

    public int originX;
    public int originY;
    public int originZ;
    public boolean originSet;

    public int rotation;
    public boolean mirrorX;
    public boolean mirrorZ;

    public boolean ghost = true;
    public int ghostOpacity = 120;
    public boolean showBox = true;
    public boolean showRemaining = false;

    public int blocksPerTick = 2;
    public int delayMs = 120;
    public double reach = 4.5;
    public boolean replaceConflicts = false;
    public boolean swing = true;

    public float cameraYaw = 35f;
    public float cameraPitch = 25f;
    public float cameraZoom = 1.0f;

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("anormalclient").resolve(FILE_NAME);
    }

    public static AutoBuilderConfig load() {
        AutoBuilderConfig config = new AutoBuilderConfig();
        try {
            Path file = path();
            if (!Files.isRegularFile(file)) return config;

            String json = Files.readString(file, StandardCharsets.UTF_8);
            AutoBuilderConfig parsed = GSON.fromJson(json, AutoBuilderConfig.class);
            if (parsed == null) return config;

            parsed.validate();
            return parsed;
        } catch (Throwable ignored) {
            return config;
        }
    }

    private void validate() {
        rotation = Math.floorMod(rotation, 360);
        rotation = (rotation / 90) * 90;
        ghostOpacity = clamp(ghostOpacity, 10, 255);
        blocksPerTick = clamp(blocksPerTick, 1, 20);
        delayMs = clamp(delayMs, 0, 2000);
        if (reach < 1.0 || reach > 6.0) reach = 4.5;
        if (cameraZoom < 0.1f || cameraZoom > 10f) cameraZoom = 1.0f;
        if (Float.isNaN(cameraYaw) || Float.isNaN(cameraPitch)) {
            cameraYaw = 35f;
            cameraPitch = 25f;
        }
        if (directory == null) directory = "";
        if (lastSchematic == null) lastSchematic = "";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }
}
