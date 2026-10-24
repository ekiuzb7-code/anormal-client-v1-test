package com.anormal.client.lucid;

import com.anormal.client.mixin.MinecraftClientAccessor;
import com.anormal.client.mixin.MouseAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simulated mouse clicks (ported from Argon's MouseSimulation) — used by the
 * Click Simulation feature of Anchor Macro so the CPS HUD sees a click that
 * never came from the physical button. The click is dispatched through the
 * vanilla {@link net.minecraft.client.Mouse#onMouseButton} handler so every
 * vanilla input path (attack/use cooldowns, GUI screens) reacts exactly as if
 * the user had clicked.
 */
public final class MouseSimulation {
    public static final Map<Integer, Boolean> mouseButtons = new HashMap<>();
    private static final ExecutorService clickExecutor = Executors.newFixedThreadPool(8);

    private MouseSimulation() {}

    public static MouseAccessor getMouseHandler() {
        return (MouseAccessor) ((MinecraftClientAccessor) MinecraftClient.getInstance()).lucid$getMouse();
    }

    public static boolean isMouseButtonPressed(int keyCode) {
        Boolean key = mouseButtons.get(keyCode);
        return key != null && key;
    }

    public static void mousePress(int keyCode) {
        mouseButtons.put(keyCode, true);
        getMouseHandler().lucid$press(MinecraftClient.getInstance().getWindow().getHandle(),
            new MouseInput(keyCode, 0), GLFW.GLFW_PRESS);
    }

    public static void mouseRelease(int keyCode) {
        getMouseHandler().lucid$press(MinecraftClient.getInstance().getWindow().getHandle(),
            new MouseInput(keyCode, 0), GLFW.GLFW_RELEASE);
    }

    public static void mouseClick(int keyCode, int millis) {
        clickExecutor.submit(() -> {
            try {
                mousePress(keyCode);
                Thread.sleep(millis);
                mouseRelease(keyCode);
            } catch (InterruptedException ignored) {
            }
        });
    }

    public static void mouseClick(int keyCode) {
        mouseClick(keyCode, 35);
    }
}
