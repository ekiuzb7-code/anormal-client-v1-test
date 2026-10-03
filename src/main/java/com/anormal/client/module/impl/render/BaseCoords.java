package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class BaseCoords extends Module {
    public final BooleanSetting saveHere = new BooleanSetting("Save Here", "Save current position as base", false);
    public final BooleanSetting recallChat = new BooleanSetting("Recall Chat", "Print saved base in chat", false);
    public final NumberSetting posX = new NumberSetting("Pos X", "Recall text X", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Recall text Y", 40.0, 0.0, 1080.0, 1.0);

    private double bx, by, bz;
    private String dim = "";
    private boolean has = false;

    public BaseCoords() {
        super("BaseCoords", "Saves one base position with recall", Category.RENDER);
        addSetting(saveHere);
        addSetting(recallChat);
        addSetting(posX);
        addSetting(posY);
    }

    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public void onTick() {
        if (saveHere.isEnabled()) {
            try { saveHere.setValue(false); } catch (Throwable ignored) {}
            if (mc.player != null) {
                try {
                    bx = mc.player.getX(); by = mc.player.getY(); bz = mc.player.getZ();
                    dim = curDim(); has = true;
                    if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(
                            Text.literal("§a[Base] §fsaved " + (int) bx + "/" + (int) by + "/" + (int) bz + " (" + dim + ")"));
                } catch (Throwable ignored) {}
            }
        }
        if (recallChat.isEnabled()) {
            try { recallChat.setValue(false); } catch (Throwable ignored) {}
            if (has && mc.inGameHud != null) {
                try {
                    mc.inGameHud.getChatHud().addMessage(
                            Text.literal("§a[Base] §f" + (int) bx + " / " + (int) by + " / " + (int) bz + " (" + dim + ")"));
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!has || mc.player == null || mc.textRenderer == null) return;
        try {
            String label = "§aBase §f" + (int) bx + "/" + (int) by + "/" + (int) bz;
            double dx = bx - mc.player.getX(), dy = by - mc.player.getY(), dz = bz - mc.player.getZ();
            label += " §7" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
            RenderUtils.drawText(context, mc.textRenderer, label, posX.getValue().intValue(), posY.getValue().intValue(), 0xFFFFFFFF, true);
        } catch (Throwable ignored) {}
    }
}
