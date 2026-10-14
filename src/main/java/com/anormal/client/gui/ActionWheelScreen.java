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
 * center action inspection, clean text layout, and in-game editor integration.
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

        double stepDeg = 360.0 / n;

        // Hover detection: calculate angle starting from top (-90 degrees)
        if (dist >= deadzoneRadius && dist <= wheelRadius + 25) {
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

        // 1. Draw base dark circular wheel disk
        drawFilledRing(context, cx, cy, (int) deadzoneRadius, (int) wheelRadius, ColorUtils.rgba(18, 22, 32, 235));

        // 2. Draw active hovered slice with smooth rasterized sector fill
        if (hoveredIndex >= 0 && hoveredIndex < n) {
            double startDeg = hoveredIndex * stepDeg - 90.0;
            double endDeg = startDeg + stepDeg;
            drawSmoothSector(context, cx, cy, deadzoneRadius, wheelRadius, startDeg, endDeg, accentColor);
        }

        // 3. Draw divider radial lines for all sectors
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(i * stepDeg - 90.0);
            int x1 = (int) Math.round(cx + deadzoneRadius * Math.cos(a));
            int y1 = (int) Math.round(cy + deadzoneRadius * Math.sin(a));
            int x2 = (int) Math.round(cx + wheelRadius * Math.cos(a));
            int y2 = (int) Math.round(cy + wheelRadius * Math.sin(a));
            RenderUtils.drawLine(context, x1, y1, x2, y2, ColorUtils.rgba(60, 65, 85, 200), 1.0f);
        }

        // 4. Outer & inner circle outlines
        drawCircleOutline(context, cx, cy, (int) wheelRadius, ColorUtils.rgba(70, 75, 95, 220));
        drawCircleOutline(context, cx, cy, (int) deadzoneRadius, ColorUtils.rgba(70, 75, 95, 220));

        // 5. Draw icons and labels on top of the sectors (clean two-line wrapping prevents overlapping)
        for (int i = 0; i < n; i++) {
            boolean hovered = (i == hoveredIndex);
            double startDeg = i * stepDeg - 90.0;
            double endDeg = startDeg + stepDeg;

            double midRad = Math.toRadians((startDeg + endDeg) / 2.0);
            double labelDist = (deadzoneRadius + wheelRadius) / 2.0;
            int lx = (int) Math.round(cx + labelDist * Math.cos(midRad));
            int ly = (int) Math.round(cy + labelDist * Math.sin(midRad));

            WheelAction action = actions.get(i);
            String icon = action.getIcon();
            String label = action.getLabel();

            int textCol = hovered ? 0xFFFFFFFF : 0xFFDDDDDD;
            int iconWidth = textRenderer.getWidth(icon);

            // Icon
            RenderUtils.drawText(context, textRenderer, icon, lx - iconWidth / 2, ly - 10, action.getColor(), true);

            // Label: split into two lines if contains space to avoid clipping neighboring sectors
            if (label.contains(" ")) {
                String[] parts = label.split(" ", 2);
                int w1 = textRenderer.getWidth(parts[0]);
                int w2 = textRenderer.getWidth(parts[1]);
                RenderUtils.drawText(context, textRenderer, parts[0], lx - w1 / 2, ly, textCol, true);
                RenderUtils.drawText(context, textRenderer, parts[1], lx - w2 / 2, ly + 9, textCol, true);
            } else {
                int lw = textRenderer.getWidth(label);
                RenderUtils.drawText(context, textRenderer, label, lx - lw / 2, ly + 2, textCol, true);
            }
        }

        // 6. Draw Center Hub (Deadzone circle)
        boolean centerHovered = (dist < deadzoneRadius);
        int hubBg = centerHovered ? ColorUtils.rgba(30, 35, 48, 245) : ColorUtils.rgba(14, 16, 22, 245);
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
                activateAndClose();
            } else if (isDown) {
                initialKeyDown = true;
            }
        }
    }

    /**
     * Smoothly rasterizes a mathematical circular pie sector without any blocky square artifacts.
     */
    private void drawSmoothSector(DrawContext context, int cx, int cy, double rIn, double rOut,
                                  double startDeg, double endDeg, int color) {
        double startRad = Math.toRadians(startDeg);
        double endRad = Math.toRadians(endDeg);

        while (endRad <= startRad) endRad += 2 * Math.PI;

        int minY = (int) Math.floor(-rOut);
        int maxY = (int) Math.ceil(rOut);

        double rIn2 = rIn * rIn;
        double rOut2 = rOut * rOut;

        for (int y = minY; y <= maxY; y++) {
            double py = y;
            double py2 = py * py;
            if (py2 > rOut2) continue;

            int maxDx = (int) Math.floor(Math.sqrt(rOut2 - py2));
            int spanStart = Integer.MAX_VALUE;
            int spanEnd = Integer.MIN_VALUE;

            for (int x = -maxDx; x <= maxDx; x++) {
                double px = x;
                double d2 = px * px + py2;
                if (d2 < rIn2 || d2 > rOut2) continue;

                double a = Math.atan2(py, px);
                while (a < startRad) a += 2 * Math.PI;
                while (a >= startRad + 2 * Math.PI) a -= 2 * Math.PI;

                if (a >= startRad && a <= endRad) {
                    if (x < spanStart) spanStart = x;
                    if (x > spanEnd) spanEnd = x;
                }
            }

            if (spanStart <= spanEnd) {
                context.fill(cx + spanStart, cy + y, cx + spanEnd + 1, cy + y + 1, color);
            }
        }
    }

    private void drawFilledRing(DrawContext context, int cx, int cy, int rIn, int rOut, int color) {
        int rIn2 = rIn * rIn;
        int rOut2 = rOut * rOut;

        for (int y = -rOut; y <= rOut; y++) {
            int maxDx = (int) Math.sqrt(rOut2 - y * y);
            int minDx = (y >= -rIn && y <= rIn) ? (int) Math.sqrt(Math.max(0, rIn2 - y * y)) : 0;

            if (minDx > 0) {
                // Left arc and right arc
                context.fill(cx - maxDx, cy + y, cx - minDx, cy + y + 1, color);
                context.fill(cx + minDx, cy + y, cx + maxDx + 1, cy + y + 1, color);
            } else {
                // Full row
                context.fill(cx - maxDx, cy + y, cx + maxDx + 1, cy + y + 1, color);
            }
        }
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
