package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Throwpot extends Module {
    public final ModeSetting type = new ModeSetting("Type", "Healing item kind to use", "Both", "Both", "Pots", "Soup");
    public final ModeSetting mode = new ModeSetting("Mode", "Dynamic scales with missing health, Single uses one", "Dynamic", "Dynamic", "Single");
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between healing items", 4.0, 0.0, 20.0, 1.0);
    public final BooleanSetting scroll = new BooleanSetting("Scroll", "Steps through slots instead of jumping", false);
    public final NumberSetting scrollDelay = new NumberSetting("Scroll Delay", "Ticks between scroll steps", 1.0, 0.0, 10.0, 1.0);
    public final BooleanSetting random = new BooleanSetting("Random", "Picks a random healing item", false);
    public final BooleanSetting throwBowls = new BooleanSetting("Throw Bowls", "Discards empty bowls after soup", true);

    private final Random rng = new Random();
    private int cooldown = 0;
    private int scrollTicks = 0;
    private int quota = 0;
    private boolean eating = false;
    private int eatTicks = 0;

    public Throwpot() {
        super("Throwpot", "Automatically aims and splashes healing potions to restore health", Category.INVENTORY);
        addSetting(type);
        addSetting(mode);
        addSetting(delay);
        addSetting(scroll);
        addSetting(scrollDelay);
        addSetting(random);
        addSetting(throwBowls);
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        scrollTicks = 0;
        quota = 0;
        eating = false;
    }

    @Override
    public void onDisable() {
        if (eating) mc.options.useKey.setPressed(false);
        eating = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        if (eating) {
            eatTicks++;
            String held = Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath();
            if (!held.equals("mushroom_stew") || eatTicks > 45) {
                mc.options.useKey.setPressed(false);
                eating = false;
                if (throwBowls.isEnabled() && held.equals("bowl")) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                            36 + mc.player.getInventory().getSelectedSlot(), 1, SlotActionType.THROW, mc.player);
                }
                quota--;
                cooldown = delay.getValue().intValue();
            }
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        float missing = mc.player.getMaxHealth() - mc.player.getHealth();
        if (missing <= 1.0f) {
            quota = 0;
            return;
        }
        if (quota <= 0) quota = mode.is("Single") ? 1 : Math.min(3, (int) Math.ceil(missing / 6.0f));
        int slot = pickSlot();
        if (slot == -1) return;
        int cur = mc.player.getInventory().getSelectedSlot();
        if (scroll.isEnabled() && cur != slot) {
            if (scrollTicks > 0) {
                scrollTicks--;
                return;
            }
            mc.player.getInventory().setSelectedSlot(cur + Integer.compare(slot, cur));
            scrollTicks = scrollDelay.getValue().intValue();
            return;
        }
        if (cur != slot) mc.player.getInventory().setSelectedSlot(slot);
        String held = Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath();
        if (held.equals("mushroom_stew")) {
            mc.options.useKey.setPressed(true);
            eating = true;
            eatTicks = 0;
            return;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        quota--;
        cooldown = delay.getValue().intValue();
    }

    private int pickSlot() {
        List<Integer> pots = new ArrayList<>();
        List<Integer> soups = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String p = Registries.ITEM.getId(s.getItem()).getPath();
            if (p.equals("mushroom_stew")) soups.add(i);
            else if ((p.equals("splash_potion") || p.equals("lingering_potion")) && isHealing(s)) pots.add(i);
        }
        List<Integer> pool = new ArrayList<>();
        if (!type.is("Soup")) pool.addAll(pots);
        if (!type.is("Pots")) pool.addAll(soups);
        if (pool.isEmpty()) return -1;
        return random.isEnabled() ? pool.get(rng.nextInt(pool.size())) : pool.get(0);
    }

    private static boolean isHealing(ItemStack s) {
        PotionContentsComponent c = s.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        boolean[] found = {false};
        c.forEachEffect(e -> {
            if (e.getEffectType().value() == StatusEffects.INSTANT_HEALTH.value()) found[0] = true;
        }, 1.0f);
        return found[0];
    }
}
