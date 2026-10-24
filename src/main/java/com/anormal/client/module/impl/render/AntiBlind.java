package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Hides harmful status effects from the mc, ported from Wurst's AntiBlind.
 *
 * <p>Wurst does this by intercepting {@code hasEffect()} in a mixin, which is exactly what this
 * module does: {@link com.anormal.client.mixin.LivingEntityMixin} asks
 * {@link #shouldHide(RegistryEntry)} before the game can apply the effect's visuals. Blindness fog,
 * the darkness overlay, the nausea camera wobble and the slowness FOV change all read
 * {@code hasStatusEffect}, so cancelling it removes them cleanly without touching packets.</p>
 *
 * <p>Only the mc is affected. Damage based effects such as poison and wither keep hurting you -
 * hiding them only removes the icon and the green overlay.</p>
 */
public class AntiBlind extends Module {

    public final BooleanSetting blindness = new BooleanSetting("Blindness",
            "Remove the blindness fog and screen darkening", true);
    public final BooleanSetting darkness = new BooleanSetting("Darkness",
            "Remove the warden's darkness pulse", true);
    public final BooleanSetting nausea = new BooleanSetting("Nausea",
            "Remove the camera wobble from nausea", true);
    public final BooleanSetting slowness = new BooleanSetting("Slowness",
            "Remove the FOV change and slowdown from slowness", true);
    public final BooleanSetting miningFatigue = new BooleanSetting("Mining Fatigue",
            "Hide mining fatigue (mining speed is still affected)", false);
    public final BooleanSetting weakness = new BooleanSetting("Weakness",
            "Hide weakness (damage is still reduced)", false);
    public final BooleanSetting poison = new BooleanSetting("Poison",
            "Hide the poison overlay (damage still applies)", false);
    public final BooleanSetting wither = new BooleanSetting("Wither",
            "Hide the wither overlay (damage still applies)", false);
    public final BooleanSetting hunger = new BooleanSetting("Hunger",
            "Hide the hunger effect", false);
    public final BooleanSetting levitation = new BooleanSetting("Levitation",
            "Hide levitation", false);
    public final BooleanSetting glowing = new BooleanSetting("Glowing",
            "Remove your own glowing outline", false);
    public final BooleanSetting badOmen = new BooleanSetting("Bad Omen",
            "Hide bad omen", false);

    public AntiBlind() {
        super("AntiBlind", "Removes blindness, darkness and other harmful effects", Category.RENDER);
        addSetting(blindness);
        addSetting(darkness);
        addSetting(nausea);
        addSetting(slowness);
        addSetting(miningFatigue);
        addSetting(weakness);
        addSetting(poison);
        addSetting(wither);
        addSetting(hunger);
        addSetting(levitation);
        addSetting(glowing);
        addSetting(badOmen);
    }

    /** Called from the mixin on every {@code hasStatusEffect} lookup. */
    public static boolean shouldHide(RegistryEntry<StatusEffect> effect) {
        try {
            AntiBlind module = ModuleManager.getModule(AntiBlind.class);
            return module != null && module.isEnabled() && module.hidden(effect);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hidden(RegistryEntry<StatusEffect> effect) {
        if (effect == null) return false;

        if (is(effect, StatusEffects.BLINDNESS)) return blindness.isEnabled();
        if (is(effect, StatusEffects.DARKNESS)) return darkness.isEnabled();
        if (is(effect, StatusEffects.NAUSEA)) return nausea.isEnabled();
        if (is(effect, StatusEffects.SLOWNESS)) return slowness.isEnabled();
        if (is(effect, StatusEffects.MINING_FATIGUE)) return miningFatigue.isEnabled();
        if (is(effect, StatusEffects.WEAKNESS)) return weakness.isEnabled();
        if (is(effect, StatusEffects.POISON)) return poison.isEnabled();
        if (is(effect, StatusEffects.WITHER)) return wither.isEnabled();
        if (is(effect, StatusEffects.HUNGER)) return hunger.isEnabled();
        if (is(effect, StatusEffects.LEVITATION)) return levitation.isEnabled();
        if (is(effect, StatusEffects.GLOWING)) return glowing.isEnabled();
        if (is(effect, StatusEffects.BAD_OMEN)) return badOmen.isEnabled();
        return false;
    }

    /** Compares by the effect instance, not by registry entry identity. */
    private static boolean is(RegistryEntry<StatusEffect> effect, RegistryEntry<StatusEffect> target) {
        try {
            return effect.value() == target.value();
        } catch (Throwable t) {
            return false;
        }
    }
}
