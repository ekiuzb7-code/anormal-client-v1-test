package dev.zaza.client.ui;

import net.minecraft.client.Minecraft;

public final class UiScale {
    private UiScale() {}

    public static float current() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }
}
