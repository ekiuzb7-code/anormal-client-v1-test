package com.anormal.client.setting;

import org.lwjgl.glfw.GLFW;

public class KeybindSetting extends Setting<Integer> {
    // Mouse buttons are encoded as negative codes: MOUSE_BASE - glfwButton.
    // e.g. side buttons: MOUSE4 (glfw 3) -> -103, MOUSE5 (glfw 4) -> -104.
    public static final int MOUSE_BASE = -100;

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public static int mouseCode(int glfwButton) {
        return MOUSE_BASE - glfwButton;
    }

    public static boolean isMouseCode(int code) {
        return code <= MOUSE_BASE && code >= MOUSE_BASE - 8;
    }

    public static int mouseButton(int code) {
        return MOUSE_BASE - code;
    }

    public boolean isMouse() {
        return isMouseCode(getValue());
    }

    public String getKeyName() {
        int key = getValue();
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == 0) return "NONE";
        if (isMouseCode(key)) {
            int btn = mouseButton(key);
            return switch (btn) {
                case 0 -> "MOUSE1";
                case 1 -> "MOUSE2";
                case 2 -> "MOUSE3";
                case 3 -> "MOUSE4";
                case 4 -> "MOUSE5";
                default -> "MOUSE" + (btn + 1);
            };
        }
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            default -> "KEY_" + key;
        };
    }
}
