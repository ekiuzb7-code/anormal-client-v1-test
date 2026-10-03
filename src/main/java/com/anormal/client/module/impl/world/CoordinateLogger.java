package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class CoordinateLogger extends Module {
    public final NumberSetting interval = new NumberSetting("Interval Min", "Minutes between auto logs", 5.0, 1.0, 60.0, 1.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Print coords in chat", true);
    public final BooleanSetting fileLog = new BooleanSetting("File Log", "Append coords to coords.txt", true);
    public final BooleanSetting logNow = new BooleanSetting("Log Now", "Log coords immediately", false);

    private long last = 0;

    public CoordinateLogger() {
        super("CoordinateLogger", "Auto-logs coordinates to chat and file", Category.WORLD);
        addSetting(interval);
        addSetting(chatLog);
        addSetting(fileLog);
        addSetting(logNow);
    }

    @Override
    public void onEnable() {
        last = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (logNow.isEnabled()) {
                logNow.setValue(false);
                log();
                last = System.currentTimeMillis();
                return;
            }
            long gap = (long) (interval.getValue() * 60000.0);
            if (System.currentTimeMillis() - last >= gap) {
                last = System.currentTimeMillis();
                log();
            }
        } catch (Throwable ignored) {}
    }

    private void log() {
        try {
            String dim = "overworld";
            try { dim = mc.world.getRegistryKey().getValue().getPath(); } catch (Throwable ignored) {}
            String line = String.format("%.0f / %.0f / %.0f (%s)", mc.player.getX(), mc.player.getY(), mc.player.getZ(), dim);
            if (chatLog.isEnabled() && mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal("§b[Coords] §f" + line));
            if (fileLog.isEnabled()) {
                try {
                    File dir = new File(mc.runDirectory, "config/anormal");
                    dir.mkdirs();
                    Files.write(new File(dir, "coords.txt").toPath(),
                            (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
