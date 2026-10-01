package com.anormal.client.setting;

import org.lwjgl.glfw.GLFW;

public class KeybindSetting extends Setting<Integer> {
    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public String getKeyName() {
        int key = getValue();
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == 0) return "NONE";
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
