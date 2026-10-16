package com.anormal.client.util;

import net.minecraft.client.MinecraftClient;

/**
 * Lets modules hold movement keys on the player's behalf.
 *
 * <p>Naively calling {@code forwardKey.setPressed(true)} once per tick is not
 * enough: {@code KeyBinding.updatePressedStates()} rebuilds every binding from
 * the real hardware state (it is called from {@code Mouse}), which silently
 * cancels the fake press before the next movement tick. {@code KeyBindingMixin}
 * therefore re-applies whatever is forced here right after that refresh, so a
 * forced key behaves exactly like a held key.</p>
 */
public final class InputForcer {

    private InputForcer() {}

    public static volatile boolean forward;
    public static volatile boolean backward;
    public static volatile boolean left;
    public static volatile boolean right;
    public static volatile boolean jump;
    public static volatile boolean sneak;

    public static void clear() {
        forward = backward = left = right = jump = sneak = false;
    }

    /** Re-asserts the forced keys. Called from the KeyBinding hook. */
    public static void apply(MinecraftClient mc) {
        if (mc == null || mc.options == null) return;
        try {
            if (forward) mc.options.forwardKey.setPressed(true);
            if (backward) mc.options.backKey.setPressed(true);
            if (left) mc.options.leftKey.setPressed(true);
            if (right) mc.options.rightKey.setPressed(true);
            if (jump) mc.options.jumpKey.setPressed(true);
            if (sneak) mc.options.sneakKey.setPressed(true);
        } catch (Throwable ignored) {
        }
    }
}
