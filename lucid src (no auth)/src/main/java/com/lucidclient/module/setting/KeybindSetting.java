package com.lucidclient.module.setting;

import org.lwjgl.glfw.GLFW;

/**
 * A keybind setting storing a GLFW key code.
 *
 * Click the control in ClickGUI to enter "waiting for key" mode — the next
 * key press becomes the new binding (captured by LucidScreen.keyPressed).
 *
 * Has a getKeyName() helper that uses GLFW.glfwGetKeyName for printable keys
 * and falls back to a special-key mapping (Space, Tab, Escape, arrows, F1-F12,
 * modifiers, mouse buttons, numpad, etc.).
 *
 * NOTE: LWJGL 3 uses `GLFW_KEY_KP_*` for the numeric keypad
 * (NOT `GLFW_KEY_NUMPAD_*`, which existed only in LWJGL 2).
 */
public class KeybindSetting extends Setting<Integer> {

    /** When true, the next key press will be captured as the new binding. */
    private transient boolean waiting = false;

    public KeybindSetting(String name, String description, int defaultKeyCode) {
        super(name, description, defaultKeyCode);
    }

    public int getKey() { return value; }

    public void setKey(int keyCode) {
        this.value = keyCode;
        this.waiting = false;
    }

    /** Enter "Press a key..." mode. The next key press (captured by the ClickGUI
     *  screen's keyPressed handler) becomes the new binding. */
    public void startWaiting() { this.waiting = true; }

    public boolean isWaiting() { return waiting; }

    /** Cancel "Press a key..." mode without changing the binding. */
    public void cancelWaiting() { this.waiting = false; }

    /** Human-readable name for the currently bound key. */
    public String getKeyName() {
        return getKeyName(value);
    }

    /** Static helper: returns a human-readable name for any GLFW key code. */
    public static String getKeyName(int keyCode) {
        if (keyCode < 0) return "None";

        // Mouse buttons
        if (keyCode >= GLFW.GLFW_MOUSE_BUTTON_1 && keyCode <= GLFW.GLFW_MOUSE_BUTTON_8) {
            return "Mouse " + (keyCode - GLFW.GLFW_MOUSE_BUTTON_1 + 1);
        }

        // Printable keys — ask GLFW (handles a-z, 0-9, symbols for the active layout)
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null) {
            // Capitalise single-letter names for cleaner display
            if (name.length() == 1) return name.toUpperCase();
            return name;
        }

        // Special-key mapping
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_APOSTROPHE: return "'";
            case GLFW.GLFW_KEY_COMMA: return ",";
            case GLFW.GLFW_KEY_MINUS: return "-";
            case GLFW.GLFW_KEY_PERIOD: return ".";
            case GLFW.GLFW_KEY_SLASH: return "/";
            case GLFW.GLFW_KEY_SEMICOLON: return ";";
            case GLFW.GLFW_KEY_EQUAL: return "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET: return "[";
            case GLFW.GLFW_KEY_BACKSLASH: return "\\";
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return "]";
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return "`";
            case GLFW.GLFW_KEY_WORLD_1: return "W1";
            case GLFW.GLFW_KEY_WORLD_2: return "W2";

            case GLFW.GLFW_KEY_ESCAPE: return "Esc";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_BACKSPACE: return "Backspace";
            case GLFW.GLFW_KEY_INSERT: return "Insert";
            case GLFW.GLFW_KEY_DELETE: return "Delete";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_PAGE_UP: return "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PgDn";
            case GLFW.GLFW_KEY_HOME: return "Home";
            case GLFW.GLFW_KEY_END: return "End";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "CapsLk";
            case GLFW.GLFW_KEY_SCROLL_LOCK: return "ScrlLk";
            case GLFW.GLFW_KEY_NUM_LOCK: return "NumLk";
            case GLFW.GLFW_KEY_PRINT_SCREEN: return "PrtSc";
            case GLFW.GLFW_KEY_PAUSE: return "Pause";

            case GLFW.GLFW_KEY_F1: return "F1";
            case GLFW.GLFW_KEY_F2: return "F2";
            case GLFW.GLFW_KEY_F3: return "F3";
            case GLFW.GLFW_KEY_F4: return "F4";
            case GLFW.GLFW_KEY_F5: return "F5";
            case GLFW.GLFW_KEY_F6: return "F6";
            case GLFW.GLFW_KEY_F7: return "F7";
            case GLFW.GLFW_KEY_F8: return "F8";
            case GLFW.GLFW_KEY_F9: return "F9";
            case GLFW.GLFW_KEY_F10: return "F10";
            case GLFW.GLFW_KEY_F11: return "F11";
            case GLFW.GLFW_KEY_F12: return "F12";
            case GLFW.GLFW_KEY_F13: return "F13";
            case GLFW.GLFW_KEY_F14: return "F14";
            case GLFW.GLFW_KEY_F15: return "F15";
            case GLFW.GLFW_KEY_F16: return "F16";
            case GLFW.GLFW_KEY_F17: return "F17";
            case GLFW.GLFW_KEY_F18: return "F18";
            case GLFW.GLFW_KEY_F19: return "F19";
            case GLFW.GLFW_KEY_F20: return "F20";
            case GLFW.GLFW_KEY_F21: return "F21";
            case GLFW.GLFW_KEY_F22: return "F22";
            case GLFW.GLFW_KEY_F23: return "F23";
            case GLFW.GLFW_KEY_F24: return "F24";

            case GLFW.GLFW_KEY_LEFT_SHIFT: return "L-Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "L-Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT: return "L-Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER: return "L-Super";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R-Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R-Ctrl";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R-Alt";
            case GLFW.GLFW_KEY_RIGHT_SUPER: return "R-Super";
            case GLFW.GLFW_KEY_MENU: return "Menu";

            // Numeric keypad — LWJGL 3 uses GLFW_KEY_KP_* (NOT GLFW_KEY_NUMPAD_*)
            case GLFW.GLFW_KEY_KP_0: return "Num 0";
            case GLFW.GLFW_KEY_KP_1: return "Num 1";
            case GLFW.GLFW_KEY_KP_2: return "Num 2";
            case GLFW.GLFW_KEY_KP_3: return "Num 3";
            case GLFW.GLFW_KEY_KP_4: return "Num 4";
            case GLFW.GLFW_KEY_KP_5: return "Num 5";
            case GLFW.GLFW_KEY_KP_6: return "Num 6";
            case GLFW.GLFW_KEY_KP_7: return "Num 7";
            case GLFW.GLFW_KEY_KP_8: return "Num 8";
            case GLFW.GLFW_KEY_KP_9: return "Num 9";
            case GLFW.GLFW_KEY_KP_DECIMAL: return "Num .";
            case GLFW.GLFW_KEY_KP_DIVIDE: return "Num /";
            case GLFW.GLFW_KEY_KP_MULTIPLY: return "Num *";
            case GLFW.GLFW_KEY_KP_SUBTRACT: return "Num -";
            case GLFW.GLFW_KEY_KP_ADD: return "Num +";
            case GLFW.GLFW_KEY_KP_ENTER: return "Num ↵";
            case GLFW.GLFW_KEY_KP_EQUAL: return "Num =";

            default: return "Key " + keyCode;
        }
    }

    @Override public int getControlHeight() { return 24; }
    @Override public String getType() { return "keybind"; }
}
