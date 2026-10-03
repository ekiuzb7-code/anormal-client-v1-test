package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class Scoreboard extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "Right-edge anchor X", 1914.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Top anchor Y", 60.0, 0.0, 1080.0, 1.0);
    public final NumberSetting maxLines = new NumberSetting("Max Lines", "Max sidebar lines shown", 12.0, 3.0, 20.0, 1.0);
    public final BooleanSetting showNumbers = new BooleanSetting("Show Numbers", "Show sidebar scores", false);

    public Scoreboard() {
        super("Scoreboard", "Repositionable sidebar scoreboard display", Category.UZNY11);
        addSetting(posX);
        addSetting(posY);
        addSetting(maxLines);
        addSetting(showNumbers);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.textRenderer == null) return;

        net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
        ScoreboardObjective objective = board.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return;

        List<Text> lines = new ArrayList<>();
        for (ScoreboardEntry entry : board.getScoreboardEntries(objective)) {
            String name = entry.owner();
            if (name == null || name.startsWith("#")) continue;
            if (showNumbers.isEnabled()) {
                lines.add(Text.literal(name + " §c" + entry.value()));
            } else {
                lines.add(Text.literal(name));
            }
            if (lines.size() >= maxLines.getValue().intValue()) break;
        }
        if (lines.isEmpty()) return;

        Text title = objective.getDisplayName();
        int maxW = mc.textRenderer.getWidth(title);
        for (Text t : lines) {
            maxW = Math.max(maxW, mc.textRenderer.getWidth(t));
        }

        int rightEdge = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        int w = maxW + 10;
        int x = rightEdge - w;
        int h = 13 + lines.size() * 10;

        RenderUtils.fill(context, x, y, x + w, y + h, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, mc.textRenderer, title.getString(), x + (w - mc.textRenderer.getWidth(title)) / 2, y + 2, ThemeManager.getAccentColor(), true);

        int ly = y + 13;
        for (Text t : lines) {
            RenderUtils.drawText(context, mc.textRenderer, t.getString(), x + 5, ly, 0xFFFFFFFF, true);
            ly += 10;
        }
    }
}
