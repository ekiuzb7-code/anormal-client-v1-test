package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

public class DuelInfo extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 200.0, 0.0, 1080.0, 1.0);

    private String opponentName = "—";
    private int hitsGiven = 0;
    private int hitsTaken = 0;
    private int potsUsed = 0;
    private int oppPots = 0;
    private long lastReset = 0;
    private boolean wasHurt = false;
    private int swingCooldown = 0;

    public DuelInfo() {
        super("DuelInfo", "Tracks hit and potion advantages during 1v1 duels", Category.UZNY11);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        // Track current opponent
        if (mc.targetedEntity instanceof LivingEntity living && living != mc.player) {
            String name = living.getName().getString();
            if (!name.equals(opponentName)) {
                opponentName = name;
                hitsGiven = 0;
                hitsTaken = 0;
                potsUsed = 0;
                oppPots = 0;
                lastReset = now;
            }
        }

        // Hits given: attack press while looking at living target
        if (swingCooldown > 0) swingCooldown--;
        if (mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity && swingCooldown == 0) {
            hitsGiven++;
            swingCooldown = 6;
        }

        // Hits taken
        boolean hurt = mc.player.hurtTime > 0;
        if (hurt && !wasHurt) hitsTaken++;
        wasHurt = hurt;

        // Pots used: use-key press while holding drinkable (approx: any use press, 20t cooldown)
        if (mc.options.useKey.isPressed() && now - lastReset > 1000 && swingCooldown == 0) {
            if (mc.player.getMainHandStack().getItem().toString().contains("potion")
                    || mc.player.getOffHandStack().getItem().toString().contains("potion")) {
                potsUsed++;
                lastReset = now;
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();

        int hitDiff = hitsGiven - hitsTaken;
        int potDiff = oppPots - potsUsed;

        String title = "⚔ DUEL: " + opponentName;
        String hits = "Hits: " + (hitDiff >= 0 ? "+" : "") + hitDiff + " (" + hitsGiven + "/" + hitsTaken + ")";
        String pots = "Pots: " + (potDiff >= 0 ? "+" : "") + potDiff;

        int w = Math.max(mc.textRenderer.getWidth(title),
                Math.max(mc.textRenderer.getWidth(hits), mc.textRenderer.getWidth(pots))) + 12;
        int h = 34;

        RenderUtils.fill(context, x, y, x + w, y + h, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());

        RenderUtils.drawText(context, mc.textRenderer, title, x + 6, y + 3, 0xFFFFAA00, true);
        int hitColor = hitDiff > 0 ? 0xFF55FF55 : (hitDiff < 0 ? 0xFFFF5555 : 0xFFAAAAAA);
        int potColor = potDiff > 0 ? 0xFF55FF55 : (potDiff < 0 ? 0xFFFF5555 : 0xFFAAAAAA);
        RenderUtils.drawText(context, mc.textRenderer, hits, x + 6, y + 13, hitColor, true);
        RenderUtils.drawText(context, mc.textRenderer, pots, x + 6, y + 23, potColor, true);
    }
}
