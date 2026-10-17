package com.anormal.client.gui;

import com.anormal.client.autobuilder.MaterialAnalyzer;
import com.anormal.client.module.impl.world.AutoBuilder;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Material breakdown for the currently loaded schematic.
 *
 * <p>Every number comes from {@link MaterialAnalyzer}, which counts real inventory items and
 * real world blocks, so nothing here is an estimate.</p>
 */
public class AutoBuilderMaterialsScreen extends Screen {

    private final Screen parent;
    private final AutoBuilder module;

    private MaterialAnalyzer.Result result;
    private int scroll;

    private final List<int[]> rows = new ArrayList<>();
    private int refreshX, refreshY, refreshW, refreshH;

    public AutoBuilderMaterialsScreen(Screen parent, AutoBuilder module) {
        super(Text.literal("Materials"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        this.result = module.materials();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        rows.clear();

        int panelW = Math.min(520, width - 40);
        int panelH = Math.min(400, height - 40);
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        ThemeManager.renderWindow(context, x, y, panelW, panelH, "Materials");

        if (result == null) {
            result = module.materials();
        }

        RenderUtils.drawText(context, textRenderer,
                "§7Required §f" + result.totalRequired
                        + "   §aDone §f" + result.totalSatisfied
                        + "   §eLeft §f" + result.totalRemaining
                        + "   §cMissing §f" + result.totalMissing,
                x + 10, y + 24, 0xFFFFFFFF, false);

        if (result.unresolved > 0) {
            RenderUtils.drawText(context, textRenderer,
                    "§c" + result.unresolved + " item(s) have no placeable item and are unresolved",
                    x + 10, y + 36, 0xFFFF8888, false);
        }

        int tableY = y + (result.unresolved > 0 ? 50 : 38);
        int tableH = panelH - (tableY - y) - 34;

        // column header
        RenderUtils.fill(context, x + 8, tableY, x + panelW - 8, tableY + 13, ColorUtils.rgba(22, 26, 36, 230));
        RenderUtils.drawText(context, textRenderer, "§8Material", x + 12, tableY + 3, 0xFF999999, false);
        drawRight(context, "§8Need", x + panelW - 190, tableY + 3);
        drawRight(context, "§8Done", x + panelW - 148, tableY + 3);
        drawRight(context, "§8Bag", x + panelW - 108, tableY + 3);
        drawRight(context, "§8Missing", x + panelW - 56, tableY + 3);

        int listY = tableY + 15;
        int rowH = 13;
        int maxRows = Math.max(1, tableH / rowH);
        int maxScroll = Math.max(0, result.rows.size() - maxRows);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        try {
            context.enableScissor(x + 8, listY, x + panelW - 8, listY + tableH);
            for (int i = 0; i < result.rows.size(); i++) {
                int ry = listY + i * rowH - scroll * rowH;
                if (ry + rowH < listY || ry > listY + tableH) continue;

                MaterialAnalyzer.Row row = result.rows.get(i);
                boolean complete = row.isComplete();

                if (i % 2 == 0) {
                    RenderUtils.fill(context, x + 8, ry, x + panelW - 8, ry + rowH,
                            ColorUtils.rgba(16, 18, 26, 160));
                }

                String label = row.label.length() > 22 ? row.label.substring(0, 20) + ".." : row.label;
                RenderUtils.drawText(context, textRenderer, (complete ? "§a" : "§f") + label, x + 12, ry + 3, 0xFFFFFFFF, false);

                drawRight(context, "§7" + row.required, x + panelW - 190, ry + 3);
                drawRight(context, "§a" + row.satisfied, x + panelW - 148, ry + 3);
                drawRight(context, "§7" + (row.hotbar + row.inventory), x + panelW - 108, ry + 3);
                drawRight(context, (row.missing() > 0 ? "§c" : "§8") + row.missing(), x + panelW - 56, ry + 3);
            }
            context.disableScissor();
        } catch (Throwable ignored) {
        }

        if (result.rows.isEmpty()) {
            RenderUtils.drawText(context, textRenderer, "§8No materials - load a schematic first",
                    x + 12, listY + 6, 0xFF666666, false);
        }

        refreshW = 56;
        refreshH = 14;
        refreshX = x + panelW - refreshW - 10;
        refreshY = y + panelH - 22;
        drawButton(context, refreshX, refreshY, refreshW, refreshH, "Refresh", mouseX, mouseY);

        drawButton(context, x + 10, refreshY, 50, refreshH, "Back", mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRight(DrawContext context, String text, int rightX, int y) {
        int w = textRenderer.getWidth(text);
        RenderUtils.drawText(context, textRenderer, text, rightX - w, y, 0xFFCCCCCC, false);
    }

    private void drawButton(DrawContext context, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        RenderUtils.fill(context, x, y, x + w, y + h,
                hovered ? ColorUtils.rgba(34, 38, 50, 230) : ColorUtils.rgba(20, 22, 30, 210));
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());
        int tx = x + (w - textRenderer.getWidth(label)) / 2;
        RenderUtils.drawText(context, textRenderer, label, tx, y + (h - 8) / 2 + 1, 0xFFFFFFFF, false);
        rows.add(new int[]{x, y, w, h});
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY);
    }

    private boolean handleClick(double mouseX, double mouseY) {
        if (mouseX >= refreshX && mouseX <= refreshX + refreshW && mouseY >= refreshY && mouseY <= refreshY + refreshH) {
            result = module.materials();
            scroll = 0;
            return true;
        }

        int panelW = Math.min(520, width - 40);
        int panelH = Math.min(400, height - 40);
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        if (mouseX >= x + 10 && mouseX <= x + 60 && mouseY >= y + panelH - 22 && mouseY <= y + panelH - 8) {
            if (client != null) client.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) Math.signum(verticalAmount));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (client != null) client.setScreen(parent);
            return true;
        }
        return super.keyPressed(input);
    }
}
