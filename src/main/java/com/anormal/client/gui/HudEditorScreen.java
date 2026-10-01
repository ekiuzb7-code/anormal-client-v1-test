package com.anormal.client.gui;

import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.legit.*;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HudEditorScreen extends Screen {
    private final Screen parentScreen;
    private HudElement draggingElement = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public static class HudElement {
        public final String name;
        public final Module module;
        public int x;
        public int y;
        public int width;
        public int height;

        public HudElement(String name, Module module, int x, int y, int width, int height) {
            this.name = name;
            this.module = module;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private final List<HudElement> elements = new ArrayList<>();

    public HudEditorScreen(Screen parentScreen) {
        super(Text.literal("HUD Editor"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        elements.clear();
        Keystrokes ks = ModuleManager.getModule(Keystrokes.class);
        if (ks != null) {
            elements.add(new HudElement("Keystrokes", ks, ks.posX.getValue().intValue(), ks.posY.getValue().intValue(), 68, 70));
        }
        Coords coords = ModuleManager.getModule(Coords.class);
        if (coords != null) {
            elements.add(new HudElement("Coords", coords, coords.posX.getValue().intValue(), coords.posY.getValue().intValue(), 130, 16));
        }
        FPS fps = ModuleManager.getModule(FPS.class);
        if (fps != null) {
            elements.add(new HudElement("FPS", fps, fps.posX.getValue().intValue(), fps.posY.getValue().intValue(), 50, 16));
        }
        ArmorStatus armor = ModuleManager.getModule(ArmorStatus.class);
        if (armor != null) {
            elements.add(new HudElement("ArmorStatus", armor, armor.posX.getValue().intValue(), armor.posY.getValue().intValue(), 80, 20));
        }
        TargetInfo targetInfo = ModuleManager.getModule(TargetInfo.class);
        if (targetInfo != null) {
            elements.add(new HudElement("TargetInfo", targetInfo, targetInfo.posX.getValue().intValue(), targetInfo.posY.getValue().intValue(), 110, 36));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark translucent overlay
        RenderUtils.fill(context, 0, 0, width, height, ColorUtils.rgba(0, 0, 0, 160));

        // Top notification banner
        String banner = "§6[HUD LAYOUT EDITOR] §fClick and drag elements to position anywhere on screen. Press §e[ESC]§f to save.";
        int bw = textRenderer.getWidth(banner);
        RenderUtils.fill(context, (width - bw) / 2 - 10, 10, (width + bw) / 2 + 10, 26, ColorUtils.rgba(15, 15, 20, 220));
        RenderUtils.drawBorder(context, (width - bw) / 2 - 10, 10, (width + bw) / 2 + 10, 26, 1, ThemeManager.getAccentColor());
        RenderUtils.drawText(context, textRenderer, banner, (width - bw) / 2, 15, 0xFFFFFFFF, true);

        // Handle dragging
        if (draggingElement != null) {
            draggingElement.x = Math.max(0, Math.min(width - draggingElement.width, mouseX - dragOffsetX));
            draggingElement.y = Math.max(0, Math.min(height - draggingElement.height, mouseY - dragOffsetY));

            // Sync to module settings
            if (draggingElement.module instanceof Keystrokes ks) {
                ks.posX.setValue((double) draggingElement.x);
                ks.posY.setValue((double) draggingElement.y);
            } else if (draggingElement.module instanceof Coords c) {
                c.posX.setValue((double) draggingElement.x);
                c.posY.setValue((double) draggingElement.y);
            } else if (draggingElement.module instanceof FPS f) {
                f.posX.setValue((double) draggingElement.x);
                f.posY.setValue((double) draggingElement.y);
            } else if (draggingElement.module instanceof ArmorStatus a) {
                a.posX.setValue((double) draggingElement.x);
                a.posY.setValue((double) draggingElement.y);
            } else if (draggingElement.module instanceof TargetInfo ti) {
                ti.posX.setValue((double) draggingElement.x);
                ti.posY.setValue((double) draggingElement.y);
            }
        }

        // Render each HUD element bounding box
        for (HudElement el : elements) {
            boolean hovered = el.isHovered(mouseX, mouseY) || el == draggingElement;
            int bg = hovered ? ColorUtils.rgba(255, 120, 0, 60) : ColorUtils.rgba(20, 25, 35, 120);
            int border = hovered ? ThemeManager.getAccentColor() : ColorUtils.rgba(100, 110, 130, 180);

            RenderUtils.fill(context, el.x, el.y, el.x + el.width, el.y + el.height, bg);
            RenderUtils.drawBorder(context, el.x, el.y, el.x + el.width, el.y + el.height, 1, border);

            String label = "§e" + el.name;
            RenderUtils.drawText(context, textRenderer, label, el.x + 4, el.y + 4, 0xFFFFFFFF, true);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (HudElement el : elements) {
                if (el.isHovered((int) mouseX, (int) mouseY)) {
                    draggingElement = el;
                    dragOffsetX = (int) mouseX - el.x;
                    dragOffsetY = (int) mouseY - el.y;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingElement = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (client != null) {
                client.setScreen(parentScreen);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
