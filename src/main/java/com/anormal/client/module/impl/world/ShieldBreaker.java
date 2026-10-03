package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class ShieldBreaker extends Module {
    public final NumberSetting swapDelay = new NumberSetting("Swap Delay", "Ticks between axe swap and attack", 2.0, 0.0, 20.0, 1.0);
    public final NumberSetting swapBackDelay = new NumberSetting("Swap Back Delay", "Ticks between attack and slot restore", 4.0, 0.0, 20.0, 1.0);
    public final BooleanSetting doubleClick = new BooleanSetting("Double Click", "Attack again after breaking shield", true);
    public final BooleanSetting limitToItems = new BooleanSetting("Limit to Items", "Only while holding sword, axe or mace", false);

    private int stage = 0;
    private int timer = 0;
    private int originalSlot = -1;
    private int cooldown = 0;

    public ShieldBreaker() {
        super("ShieldBreaker", "Swaps to axe against raised shields then restores slot", Category.WORLD);
        addSetting(swapDelay);
        addSetting(swapBackDelay);
        addSetting(doubleClick);
        addSetting(limitToItems);
    }

    @Override
    public void onEnable() {
        stage = 0;
        timer = 0;
        cooldown = 0;
        originalSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (cooldown > 0) cooldown--;
        if (stage == 0) {
            if (cooldown > 0 || !mc.options.attackKey.isPressed()) return;
            if (!(mc.targetedEntity instanceof LivingEntity target) || !target.isAlive()) return;
            if (!target.isBlocking()) return;
            if (mc.player.distanceTo(target) > 4.5) return;
            if (limitToItems.isEnabled() && !isWeaponHeld()) return;
            int axe = findAxe();
            if (axe == -1) return;
            originalSlot = mc.player.getInventory().getSelectedSlot();
            if (originalSlot == axe) return;
            mc.player.getInventory().setSelectedSlot(axe);
            timer = Math.max(0, swapDelay.getValue().intValue());
            stage = 1;
        } else if (stage == 1) {
            if (--timer > 0) return;
            if (mc.targetedEntity instanceof LivingEntity target && target.isAlive()) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            timer = doubleClick.isEnabled() ? 2 : Math.max(0, swapBackDelay.getValue().intValue());
            stage = doubleClick.isEnabled() ? 2 : 3;
        } else if (stage == 2) {
            if (--timer > 0) return;
            if (mc.targetedEntity instanceof LivingEntity target && target.isAlive()) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            timer = Math.max(0, swapBackDelay.getValue().intValue());
            stage = 3;
        } else {
            if (--timer > 0) return;
            if (originalSlot >= 0 && originalSlot < 9) mc.player.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
            cooldown = 10;
            stage = 0;
        }
    }

    private boolean isWeaponHeld() {
        try {
            Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
            if (id == null) return false;
            String p = id.getPath();
            return p.endsWith("_sword") || p.endsWith("_axe") || p.equals("mace") || p.equals("trident");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int findAxe() {
        for (int i = 0; i < 9; i++) {
            try {
                Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                if (id != null && id.getPath().endsWith("_axe")) return i;
            } catch (Throwable ignored) {}
        }
        return -1;
    }
}
