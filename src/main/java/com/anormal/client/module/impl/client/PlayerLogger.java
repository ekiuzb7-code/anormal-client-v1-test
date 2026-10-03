package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class PlayerLogger extends Module {
    public final BooleanSetting showCount = new BooleanSetting("Show Count", "Show seen player count on HUD", true);
    public final NumberSetting posX = new NumberSetting("Pos X", "HUD text X", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "HUD text Y", 60.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Dump seen players to chat", false);
    public final BooleanSetting clear = new BooleanSetting("Clear", "Forget all seen players", false);

    private static final class Seen {
        final String name, time, dim;
        Seen(String name, String time, String dim) {
            this.name = name; this.time = time; this.dim = dim;
        }
    }

    private final List<Seen> seen = new ArrayList<>();

    public PlayerLogger() {
        super("PlayerLogger", "Records players seen with time and dimension", Category.CLIENT);
        addSetting(showCount);
        addSetting(posX);
        addSetting(posY);
        addSetting(chatLog);
        addSetting(clear);
    }

    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private String now() {
        try {
            return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Throwable t) {
            return "??:??:??";
        }
    }

    @Override
    public void onTick() {
        if (clear.isEnabled()) {
            try { clear.setValue(false); } catch (Throwable ignored) {}
            seen.clear();
            return;
        }
        if (chatLog.isEnabled()) {
            try { chatLog.setValue(false); } catch (Throwable ignored) {}
            dump();
        }
        if (mc.player == null || mc.world == null) return;
        try {
            String dim = curDim();
            for (Entity e : mc.world.getEntities()) {
                try {
                    if (!(e instanceof PlayerEntity p) || e == mc.player) continue;
                    String name = p.getName().getString();
                    boolean known = false;
                    for (Seen s : seen) if (s.name.equals(name)) { known = true; break; }
                    if (!known) {
                        seen.add(0, new Seen(name, now(), dim));
                        while (seen.size() > 30) seen.remove(seen.size() - 1);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void dump() {
        try {
            if (mc.inGameHud == null) return;
            if (seen.isEmpty()) mc.inGameHud.getChatHud().addMessage(Text.literal("§7[Seen] §fno players seen"));
            for (Seen s : seen) mc.inGameHud.getChatHud().addMessage(Text.literal("§7[Seen] §f" + s.name + " §8" + s.time + " " + s.dim));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showCount.isEnabled() || mc.textRenderer == null) return;
        try {
            RenderUtils.drawText(context, mc.textRenderer, "§7Seen: §f" + seen.size(),
                    posX.getValue().intValue(), posY.getValue().intValue(), 0xFFFFFFFF, true);
        } catch (Throwable ignored) {}
    }
}
