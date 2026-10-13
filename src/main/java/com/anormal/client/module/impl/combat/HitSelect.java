package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Ported from Vape V4's HitSelect module.
 *
 * <p>HitSelect decides <i>when</i> your attack is actually sent. Instead of
 * swinging the instant you click, it holds the hit until the moment that gives
 * the best trade - which is how Vape wins knockback exchanges without changing
 * your aim.</p>
 *
 * <p>Vape's settings, mirrored here: mode, delay, chance, only-in-combat and
 * the item gate.</p>
 */
public class HitSelect extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Hit selection strategy", "Perfect", "Perfect", "Legit", "Spam", "Crit");
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks the attack is held for", 2.0, 0.0, 20.0, 1.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Chance to apply hit selection", 100.0, 1.0, 100.0, 1.0);
    public final BooleanSetting onlyInCombat = new BooleanSetting("Only In Combat", "Only apply while an enemy is nearby", true);
    public final NumberSetting combatRange = new NumberSetting("Combat Range", "Range that counts as combat", 5.0, 1.0, 10.0, 0.5);
    public final BooleanSetting onlyKillAura = new BooleanSetting("Only KillAura", "Only apply when KillAura is enabled", false);
    public final BooleanSetting allowSwords = new BooleanSetting("Swords", "Only apply while holding a sword", false);
    public final BooleanSetting allowFood = new BooleanSetting("Food", "Only apply while holding food", false);
    public final BooleanSetting allowPotions = new BooleanSetting("Potions", "Only apply while holding a potion", false);

    private int holdTicks = 0;
    private boolean pending = false;

    public HitSelect() {
        super("HitSelect", "Times your attacks for the best trade", Category.COMBAT);
        addSetting(mode);
        addSetting(delay);
        addSetting(chance);
        addSetting(onlyInCombat);
        addSetting(combatRange);
        addSetting(onlyKillAura);
        addSetting(allowSwords);
        addSetting(allowFood);
        addSetting(allowPotions);
    }

    @Override
    public void onDisable() {
        holdTicks = 0;
        pending = false;
    }

    /**
     * Called by the attack path. Returning {@code false} means "hold this hit".
     *
     * @return true when the attack should be allowed through right now.
     */
    public boolean shouldAttack() {
        if (!isEnabled() || mc.player == null) return true;
        if (mode.is("Spam")) return true;
        if (Math.random() * 100.0 > chance.getValue()) return true;
        if (onlyKillAura.isEnabled() && !killAuraEnabled()) return true;
        if (onlyInCombat.isEnabled() && !enemyNear(combatRange.getValue())) return true;
        if (!itemAllowed()) return true;

        if (mode.is("Crit")) {
            // Only allow the hit while falling, so it lands as a critical.
            return !mc.player.isOnGround();
        }

        if (pending) {
            if (holdTicks > 0) {
                holdTicks--;
                return false;
            }
            pending = false;
            return true;
        }

        pending = true;
        holdTicks = mode.is("Perfect")
                ? (int) Math.max(1, delay.getValue())
                : (int) Math.max(0, delay.getValue());
        return holdTicks <= 0;
    }

    private boolean enemyNear(double range) {
        try {
            for (var e : mc.world.getEntities()) {
                if (!(e instanceof net.minecraft.entity.player.PlayerEntity p) || p == mc.player) continue;
                if (mc.player.distanceTo(p) <= range) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean killAuraEnabled() {
        try {
            KillAura ka = com.anormal.client.module.ModuleManager.getModule(KillAura.class);
            return ka != null && ka.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean itemAllowed() {
        if (!allowSwords.isEnabled() && !allowFood.isEnabled() && !allowPotions.isEnabled()) return true;
        try {
            var stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) return false;
            String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
            if (allowSwords.isEnabled() && path.endsWith("_sword")) return true;
            if (allowFood.isEnabled() && path.contains("apple")) return true;
            if (allowPotions.isEnabled() && path.contains("potion")) return true;
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
