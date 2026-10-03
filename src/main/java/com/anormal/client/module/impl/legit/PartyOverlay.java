package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class PartyOverlay extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 300.0, 0.0, 1080.0, 1.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max players listed", 6.0, 1.0, 20.0, 1.0);
    public final BooleanSetting showSelf = new BooleanSetting("Show Self", "Include yourself in list", false);

    public PartyOverlay() {
        super("PartyOverlay", "Displays nearby players health, distance and held item", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(maxShown);
        addSetting(showSelf);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;

        List<PlayerEntity> nearby = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PlayerEntity p && p.isAlive()) {
                if (p == mc.player && !showSelf.isEnabled()) continue;
                nearby.add(p);
            }
        }
        nearby.sort((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)));

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        String title = "👥 PARTY (" + nearby.size() + ")";

        List<String> lines = new ArrayList<>();
        int maxW = mc.textRenderer.getWidth(title);
        int shown = 0;
        for (PlayerEntity p : nearby) {
            if (shown >= maxShown.getValue().intValue()) break;
            String held = p.getMainHandStack().isEmpty() ? "—" : p.getMainHandStack().getName().getString();
            if (held.length() > 14) held = held.substring(0, 13) + "…";
            String line = String.format("%s %.0f❤ %dm %s", p.getName().getString(),
                    p.getHealth() + p.getAbsorptionAmount(), (int) p.distanceTo(mc.player), held);
            lines.add(line);
            maxW = Math.max(maxW, mc.textRenderer.getWidth(line));
            shown++;
        }
        if (lines.isEmpty()) lines.add("§7No players nearby");

        int w = maxW + 12;
        int h = 14 + lines.size() * 11;

        RenderUtils.fill(context, x, y, x + w, y + h, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, mc.textRenderer, title, x + 6, y + 3, 0xFF55FFFF, true);

        int ly = y + 14;
        for (String line : lines) {
            RenderUtils.drawText(context, mc.textRenderer, line, x + 6, ly, 0xFFFFFFFF, true);
            ly += 11;
        }
    }
}
