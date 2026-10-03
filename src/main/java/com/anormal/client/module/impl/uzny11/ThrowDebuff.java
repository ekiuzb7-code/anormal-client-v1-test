package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThrowDebuff extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "How many debuff potions to throw", "All", "All", "One of Each", "First");
    public final BooleanSetting harming = new BooleanSetting("Harming", "Includes splash harming potions", true);
    public final BooleanSetting weakness = new BooleanSetting("Weakness", "Includes splash weakness potions", true);
    public final BooleanSetting poison = new BooleanSetting("Poison", "Includes splash poison potions", true);
    public final BooleanSetting slowness = new BooleanSetting("Slowness", "Includes splash slowness potions", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between potion throws", 4.0, 0.0, 20.0, 1.0);
    public final BooleanSetting scroll = new BooleanSetting("Scroll", "Steps through slots instead of jumping", false);
    public final NumberSetting scrollDelay = new NumberSetting("Scroll Delay", "Ticks between scroll steps", 1.0, 0.0, 10.0, 1.0);

    private final List<Integer> targets = new ArrayList<>();
    private int index = 0;
    private int cooldown = 0;
    private int scrollTicks = 0;

    public ThrowDebuff() {
        super("ThrowDebuff", "Quickly throws negative splash potions at opponent", Category.UZNY11);
        addSetting(mode);
        addSetting(harming);
        addSetting(weakness);
        addSetting(poison);
        addSetting(slowness);
        addSetting(delay);
        addSetting(scroll);
        addSetting(scrollDelay);
    }

    @Override
    public void onEnable() {
        targets.clear();
        index = 0;
        cooldown = 0;
        scrollTicks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        if (targets.isEmpty() && index == 0) collect();
        if (index >= targets.size()) {
            setEnabled(false);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = targets.get(index);
        int cur = mc.player.getInventory().getSelectedSlot();
        if (scroll.isEnabled() && cur != slot) {
            if (scrollTicks > 0) {
                scrollTicks--;
                return;
            }
            int next = cur + Integer.compare(slot, cur);
            mc.player.getInventory().setSelectedSlot(next);
            scrollTicks = scrollDelay.getValue().intValue();
            return;
        }
        if (cur != slot) mc.player.getInventory().setSelectedSlot(slot);
        if (mc.player.getMainHandStack().isEmpty()) {
            index++;
            return;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        index++;
        cooldown = delay.getValue().intValue();
    }

    private void collect() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            String key = debuffKey(s);
            if (key == null) continue;
            if (mode.is("First")) {
                targets.add(i);
                return;
            }
            if (mode.is("One of Each")) {
                if (seen.add(key)) targets.add(i);
            } else {
                targets.add(i);
            }
        }
    }

    private String debuffKey(ItemStack s) {
        if (s.isEmpty()) return null;
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        if (!p.equals("splash_potion") && !p.equals("lingering_potion")) return null;
        List<StatusEffectInstance> fx = new ArrayList<>();
        PotionContentsComponent c = s.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return null;
        c.forEachEffect(fx::add, 1.0f);
        for (StatusEffectInstance e : fx) {
            RegistryEntry<StatusEffect> t = e.getEffectType();
            if (t.value() == StatusEffects.INSTANT_DAMAGE.value() && harming.isEnabled()) return "harming";
            if (t.value() == StatusEffects.WEAKNESS.value() && weakness.isEnabled()) return "weakness";
            if (t.value() == StatusEffects.POISON.value() && poison.isEnabled()) return "poison";
            if (t.value() == StatusEffects.SLOWNESS.value() && slowness.isEnabled()) return "slowness";
        }
        return null;
    }
}
