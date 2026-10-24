package dev.zaza.client.render.hud;

import dev.zaza.ZazaHook;
import dev.zaza.client.ZazaClient;
import dev.zaza.client.module.Module;
import dev.zaza.client.render.Draw;
import dev.zaza.client.render.GlassPane;
import dev.zaza.client.render.Text;
import dev.zaza.client.ui.Theme;
import dev.zaza.client.ui.ThemeAdjust;
import dev.zaza.client.ui.UiScale;
import dev.zaza.client.util.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

public final class ZazaHud {
    private ZazaHud() {}

    public static void init() {
        HudElementRegistry.addLast(ZazaHook.id("overlay"), ZazaHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Theme.update();
        ThemeAdjust.update();
        float scale = UiScale.current();
        int height = Math.round(14 * scale);
        float padding = 6 * scale;
        String title = "zaza.hook";
        String suffix = " [TRIDENT]";
        float titleWidth = Text.width(title, 6.4f * scale);
        float suffixWidth = Text.width(suffix, 6.4f * scale);
        int width = Math.round(padding * 2 + titleWidth + suffixWidth);
        int x = Math.round(4 * scale);
        int y = Math.round(4 * scale);
        int radius = Math.round(4 * scale);
        GlassPane.submit(graphics, x, y, width, height, radius, Colors.withAlpha(Theme.MAIN, 0.78f));
        float baseline = Text.centeredBaseline(title, 6.4f * scale, y + height / 2f);
        Text.draw(graphics, List.of(new Text.Span(title, Theme.TEXT), new Text.Span(suffix, Theme.MUTED)),
                x + padding, baseline, 6.4f * scale);
        List<Module> enabled = ZazaClient.MODULES.all().stream().filter(Module::enabled).toList();
        if (enabled.isEmpty()) return;
        float rowHeight = 10 * scale;
        float rowWidth = 0;
        for (Module module : enabled) {
            rowWidth = Math.max(rowWidth, Text.width(module.name(), 5.8f * scale));
        }
        rowWidth += padding * 2 + 6 * scale;
        int listY = y + height + Math.round(3 * scale);
        for (int i = 0; i < enabled.size(); i++) {
            Module module = enabled.get(i);
            int rowY = listY + Math.round(i * (rowHeight + 2 * scale));
            GlassPane.submit(graphics, x, rowY, Math.round(rowWidth), Math.round(rowHeight),
                    Math.round(3 * scale), Colors.withAlpha(Theme.MAIN, 0.72f));
            Draw.rect(graphics, x + 2 * scale, rowY + rowHeight / 2f - 2 * scale,
                    Math.max(1f, 1.4f * scale), 4 * scale, 1f, Theme.ACCENT);
            Text.draw(graphics, module.name(), x + padding,
                    Text.centeredBaseline(module.name(), 5.8f * scale, rowY + rowHeight / 2f),
                    5.8f * scale, Theme.TEXT);
        }
    }
}
