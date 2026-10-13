package com.anormal.client.gui;

import com.anormal.client.actionwheel.ActionWheelConfig;
import com.anormal.client.actionwheel.WheelAction;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Modern Radial Action Wheel.
 * Supports hold-to-open and release-to-activate, smooth pie slices,
 * center action inspection, and in-game editor integration.
 */
public class ActionWheelScreen extends Screen {
    private static final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();

    private final Module parentModule;
    private final int bindKey;
    private final boolean holdMode;
    private final double wheelRadius;
    private final double deadzoneRadius;
    private final int accentColor;
    private final boolean soundFeedback;

    private int hoveredIndex = -1;
    private int lastHoveredIndex = -1;
    private boolean initialKeyDown = true;

    public ActionWheelScreen(Module parentModule, int bindKey, boolean holdMode,
                             double wheelRadius, double deadzoneRadius, int accentColor, boolean soundFeedback) {
        super(Text.literal("Action Wheel"));
        this.parentModule = parentModule;
        this.bindKey = bindKey;
        this.holdMode = holdMode;
        this.wheelRadius = wheelRadius;
        this.deadzoneRadius = deadzoneRadius;
        this.accentColor = accentColor;
        this.soundFeedback = soundFeedback;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark translucent background tint
        context.fill(0, 0, width, height, ColorUtils.rgba(5, 7, 12, 160));

        int cx = width / 2;
        int cy = height / 2;

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        List<WheelAction> actions = ActionWheelConfig.getActions();
        int n = actions.size();
        if (n == 0) {
            RenderUtils.drawText(context, textRenderer, "No actions configured. Press [E] to edit.",
                    cx - 90, cy - 5, 0xFFFFFFFF, true);
            return;
        }

        // Sector angle step
        double stepDeg = 360.0 / n;

        // Hover detection: offset by -stepDeg/2 so sector 0 is centered at the top (-90 deg)
        if (dist >= deadzoneRadius && dist <= wheelRadius + 25) {
            // Angle in degrees from top (12 o'clock)
            double angleDeg = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
            if (angleDeg < 0) angleDeg += 360.0;
            hoveredIndex = ((int) Math.floor(angleDeg / stepDeg)) % n;
            if (hoveredIndex != lastHoveredIndex) {
                if (soundFeedback && mc.player != null) {
                    try {
                        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.8f);
                    } catch (Throwable ignored) {}
                }
                lastHoveredIndex = hoveredIndex;
            }
        } else {
            hoveredIndex = -1;
            lastHoveredIndex = -1;
        }

        // Draw radial slices
        for (int i = 0; i < n; i++) {
            boolean hovered = (i == hoveredIndex);
            double startDeg = i * stepDeg - 90.0;
            double endDeg = startDeg + stepDeg;

            drawPieSector(context, cx, cy, deadzoneRadius, wheelRadius, startDeg, endDeg,
                    hovered ? accentColor : ColorUtils.rgba(16, 20, 28, 220), hovered);

            // Icon & label at mid-radius
            double midRad = Math.toRadians((startDeg + endDeg) / 2.0);
            double labelDist = (deadzoneRadius + wheelRadius) / 2.0;
            int lx = (int) Math.round(cx + labelDist * Math.cos(midRad));
            int ly = (int) Math.round(cy + labelDist * Math.sin(midRad));

            WheelAction action = actions.get(i);
            String icon = action.getIcon();
            String label = action.getLabel();

            int textCol = hovered ? 0xFFFFFFFF : 0xFFDDDDDD;
            int iconWidth = textRenderer.getWidth(icon);
            int labelWidth = textRenderer.getWidth(label);

            RenderUtils.drawText(context, textRenderer, icon, lx - iconWidth / 2, ly - 8, action.getColor(), true);
            RenderUtils.drawText(context, textRenderer, label, lx - labelWidth / 2, ly + 2, textCol, true);
        }

        // Draw Center Hub (Deadzone circle)
        boolean centerHovered = (dist < deadzoneRadius);
        int hubBg = centerHovered ? ColorUtils.rgba(30, 35, 48, 240) : ColorUtils.rgba(14, 16, 22, 240);
        drawFilledCircle(context, cx, cy, (int) deadzoneRadius, hubBg);
        drawCircleOutline(context, cx, cy, (int) deadzoneRadius, centerHovered ? ThemeManager.getAccentColor() : ColorUtils.rgba(60, 65, 80, 200));

        // Center content inspection
        if (hoveredIndex >= 0 && hoveredIndex < n) {
            WheelAction act = actions.get(hoveredIndex);
            String title = act.getLabel();
            String typeBadge = "[" + act.getType().getDisplay() + "]";
            String preview = act.getValue();
            if (preview.length() > 20) preview = preview.substring(0, 17) + "...";

            int tw = textRenderer.getWidth(title);
            int bw = textRenderer.getWidth(typeBadge);
            int pw = textRenderer.getWidth(preview);

            RenderUtils.drawText(context, textRenderer, act.getIcon(), cx - textRenderer.getWidth(act.getIcon()) / 2, cy - 22, act.getColor(), true);
            RenderUtils.drawText(context, textRenderer, title, cx - tw / 2, cy - 10, 0xFFFFFFFF, true);
            RenderUtils.drawText(context, textRenderer, typeBadge, cx - bw / 2, cy + 1, accentColor, true);
            RenderUtils.drawText(context, textRenderer, preview, cx - pw / 2, cy + 12, 0xFFAAAAAA, true);
        } else {
            // Default center prompt
            String title = "⚙ [E] Edit";
            String sub = centerHovered ? "Click to open" : "Hold & Release";
            int tw = textRenderer.getWidth(title);
            int sw = textRenderer.getWidth(sub);
            RenderUtils.drawText(context, textRenderer, title, cx - tw / 2, cy - 7, centerHovered ? accentColor : 0xFFFFFFFF, true);
            RenderUtils.drawText(context, textRenderer, sub, cx - sw / 2, cy + 5, 0xFF888888, true);
        }

        // Hold-to-open logic: check if the key is released
        if (holdMode && bindKey > 0) {
            long window = mc.getWindow().getHandle();
            boolean isDown = (GLFW.glfwGetKey(window, bindKey) == GLFW.GLFW_PRESS);
            if (!isDown && initialKeyDown) {
                // Key released: activate currently hovered action and close
                activateAndClose();
            } else if (isDown) {
                initialKeyDown = true;
            }
        }
    }

    private void drawPieSector(DrawContext context, int cx, int cy, double rIn, double rOut,
                               double startDeg, double endDeg, int color, boolean hovered) {
        int steps = Math.max(8, (int) ((endDeg - startDeg) / 4.0));
        double step = (endDeg - startDeg) / steps;

        for (int i = 0; i < steps; i++) {
            double a1 = Math.toRadians(startDeg + i * step);
            double a2 = Math.toRadians(startDeg + (i + 1) * step);

            int x1 = (int) Math.round(cx + rIn * Math.cos(a1));
            int y1 = (int) Math.round(cy + rIn * Math.sin(a1));
            int x2 = (int) Math.round(cx + rOut * Math.cos(a1));
            int y2 = (int) Math.round(cy + rOut * Math.sin(a1));

            int x3 = (int) Math.round(cx + rOut * Math.cos(a2));
            int y3 = (int) Math.round(cy + rOut * Math.sin(a2));
            int x4 = (int) Math.round(cx + rIn * Math.cos(a2));
            int y4 = (int) Math.round(cy + rIn * Math.sin(a2));

            // Fill polygon quad via split triangles
            int minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
            int maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
            int minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
            int maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
            context.fill(minX, minY, maxX + 1, maxY + 1, color);
        }

        // Outer arc outline
        int edgeCol = hovered ? 0xFFFFFFFF : ColorUtils.rgba(50, 55, 75, 180);
        double aStart = Math.toRadians(startDeg);
        double aEnd = Math.toRadians(endDeg);
        int sxIn = (int) Math.round(cx + rIn * Math.cos(aStart));
        int syIn = (int) Math.round(cy + rIn * Math.sin(aStart));
        int sxOut = (int) Math.round(cx + rOut * Math.cos(aStart));
        int syOut = (int) Math.round(cy + rOut * Math.sin(aStart));
        RenderUtils.drawLine(context, sxIn, syIn, sxOut, syOut, edgeCol, hovered ? 1.5f : 1.0f);
    }

    private void drawFilledCircle(DrawContext context, int cx, int cy, int radius, int color) {
        int r2 = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            int dx = (int) Math.sqrt(r2 - y * y);
            context.fill(cx - dx, cy + y, cx + dx, cy + y + 1, color);
        }
    }

    private void drawCircleOutline(DrawContext context, int cx, int cy, int radius, int color) {
        int segments = 48;
        double step = (2.0 * Math.PI) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;
            int x1 = (int) Math.round(cx + radius * Math.cos(a1));
            int y1 = (int) Math.round(cy + radius * Math.sin(a1));
            int x2 = (int) Math.round(cx + radius * Math.cos(a2));
            int y2 = (int) Math.round(cy + radius * Math.sin(a2));
            RenderUtils.drawLine(context, x1, y1, x2, y2, color, 1.0f);
        }
    }

    private void activateAndClose() {
        List<WheelAction> actions = ActionWheelConfig.getActions();
        if (hoveredIndex >= 0 && hoveredIndex < actions.size()) {
            WheelAction action = actions.get(hoveredIndex);
            action.execute(mc);
            if (soundFeedback && mc.player != null) {
                try {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                } catch (Throwable ignored) {}
            }
        }
        close();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleMouseClick((int) click.x(), (int) click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleMouseClick((int) mouseX, (int) mouseY, button);
    }

    private boolean handleMouseClick(int mouseX, int mouseY, int button) {
        int cx = width / 2;
        int cy = height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        // Click center -> open editor
        if (dist < deadzoneRadius) {
            openEditor();
            return true;
        }

        // Click a slice -> execute
        if (hoveredIndex >= 0) {
            if (button == 1) { // Right click opens editor focused on this slot
                openEditor();
            } else {
                activateAndClose();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E) {
            openEditor();
            return true;
        }
        return super.keyPressed(input);
    }

    private void openEditor() {
        if (mc != null) {
            mc.setScreen(new ActionWheelEditorScreen(parentModule));
        }
    }
}
