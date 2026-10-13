package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.MathHelper;

/**
 * Ported from Vape V4's HitFlick behaviour.
 *
 * <p>On every attack the aim is flicked away and back again. On the server this
 * looks like a small aim correction between hits, which is what Vape uses to
 * keep a natural-looking rotation while still landing every hit.</p>
 *
 * <p>Vape drives this from the same item gate as BlockHit, so the sword / food
 * / potion toggles are mirrored here too.</p>
 */
public class HitFlick extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Which axes to flick", "Yaw", "Yaw", "Pitch", "Both");
    public final NumberSetting flickAngle = new NumberSetting("Flick Angle", "How far to flick the aim", 12.0, 1.0, 90.0, 1.0);
    public final NumberSetting flickTicks = new NumberSetting("Flick Ticks", "Ticks the flick is held for", 1.0, 1.0, 10.0, 1.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Chance to flick on each hit", 100.0, 1.0, 100.0, 1.0);
    public final BooleanSetting randomize = new BooleanSetting("Randomize", "Randomize the flick direction", true);
    public final BooleanSetting onlyKillAura = new BooleanSetting("Only KillAura", "Only flick when a KillAura target exists", false);
    public final BooleanSetting allowSwords = new BooleanSetting("Swords", "Only flick while holding a sword", false);
    public final BooleanSetting allowFood = new BooleanSetting("Food", "Only flick while holding food", false);
    public final BooleanSetting allowPotions = new BooleanSetting("Potions", "Only flick while holding a potion", false);

    private float savedYaw;
    private float savedPitch;
    private int restoreTimer = 0;

    public HitFlick() {
        super("HitFlick", "Flicks your aim on every attack", Category.COMBAT);
        addSetting(mode);
        addSetting(flickAngle);
        addSetting(flickTicks);
        addSetting(chance);
        addSetting(randomize);
        addSetting(onlyKillAura);
        addSetting(allowSwords);
        addSetting(allowFood);
        addSetting(allowPotions);
    }

    @Override
    public void onDisable() {
        restoreTimer = 0;
    }

    /** Called by the attack path right after a hit lands. */
    public void onAttack() {
        if (!isEnabled() || mc.player == null) return;
        if (Math.random() * 100.0 > chance.getValue()) return;
        if (onlyKillAura.isEnabled() && !killAuraHasTarget()) return;
        if (!itemAllowed()) return;
        if (restoreTimer > 0) return; // already flicking

        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();

        float angle = flickAngle.getValue().floatValue();
        float dir = randomize.isEnabled() && Math.random() < 0.5 ? -1.0f : 1.0f;

        try {
            if (mode.is("Yaw") || mode.is("Both")) {
                mc.player.setYaw(savedYaw + angle * dir);
            }
            if (mode.is("Pitch") || mode.is("Both")) {
                mc.player.setPitch(MathHelper.clamp(savedPitch + angle * dir, -90.0f, 90.0f));
            }
        } catch (Throwable ignored) {
        }
        restoreTimer = flickTicks.getValue().intValue();
    }

    @Override
    public void onTick() {
        if (restoreTimer <= 0 || mc.player == null) return;
        restoreTimer--;
        if (restoreTimer == 0) {
            try {
                mc.player.setYaw(savedYaw);
                mc.player.setPitch(savedPitch);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean itemAllowed() {
        if (!allowSwords.isEnabled() && !allowFood.isEnabled() && !allowPotions.isEnabled()) return true;
        try {
            var stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) return false;
            String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
            if (allowSwords.isEnabled() && path.endsWith("_sword")) return true;
            if (allowFood.isEnabled() && stack.getItem().getComponents().contains(
                    net.minecraft.component.DataComponentTypes.FOOD)) return true;
            if (allowPotions.isEnabled() && path.contains("potion")) return true;
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean killAuraHasTarget() {
        try {
            KillAura ka = com.anormal.client.module.ModuleManager.getModule(KillAura.class);
            return ka != null && ka.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
