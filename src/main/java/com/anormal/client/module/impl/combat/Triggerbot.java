package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/**
 * Attacks automatically when your crosshair is on an entity.
 *
 * <p>Two interchangeable implementations:</p>
 * <ul>
 *   <li><b>V1 - Wurst</b>: Range, Speed, Speed randomization, Swing hand,
 *       Attack while blocking, Simulate mouse click, plus the entity filter
 *       list.</li>
 *   <li><b>V2 - Vape</b>: target filter, early hit chance, shield check,
 *       allowed items, require mouse down, target miss chance, air crits,
 *       ignore activation click, limit to items.</li>
 * </ul>
 */
public class Triggerbot extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2");

    // ---- V1 - Wurst ----
    public final NumberSetting v1Range = new NumberSetting("V1 Range", "Attack range", 4.25, 1.0, 6.0, 0.05);
    public final NumberSetting v1Speed = new NumberSetting("V1 Speed", "Attacks per second", 12.0, 1.0, 20.0, 0.5);
    public final NumberSetting v1SpeedRand = new NumberSetting("V1 Speed randomization", "Randomizes the attack speed by this percentage", 10.0, 0.0, 100.0, 1.0);
    public final ModeSetting v1SwingHand = new ModeSetting("V1 Swing hand", "Which hand to swing", "Main hand", "Main hand", "Off hand", "None");
    public final BooleanSetting v1AttackWhileBlocking = new BooleanSetting("V1 Attack while blocking", "Also attack while you are blocking with a shield", false);
    public final BooleanSetting v1SimulateMouseClick = new BooleanSetting("V1 Simulate mouse click", "Fake an actual mouse click instead of a direct attack", false);

    // ---- V2 - Vape ----
    public final NumberSetting v2EarlyHitChance = new NumberSetting("V2 Early Hit Chance", "Chance to attack before the target is fully in range", 0.0, 0.0, 100.0, 5.0);
    public final BooleanSetting v2ShieldCheck = new BooleanSetting("V2 Shield Check", "Do not attack while the target is blocking", false);
    public final BooleanSetting v2RequireMouseDown = new BooleanSetting("V2 Require Mouse Down", "Only attack while the attack key is held", false);
    public final NumberSetting v2TargetMissChance = new NumberSetting("V2 Target Miss Chance", "Chance to deliberately skip an attack", 0.0, 0.0, 100.0, 5.0);
    public final BooleanSetting v2AirCrits = new BooleanSetting("V2 Air Crits", "Only attack while falling for a critical hit", false);
    public final BooleanSetting v2IgnoreActivationClick = new BooleanSetting("V2 Ignore Activation Click", "Ignore the click that turned the module on", true);
    public final BooleanSetting v2LimitToItems = new BooleanSetting("V2 Limit To Items", "Only attack while holding a weapon", false);

    // ---- Shared filters ----
    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players", "Attack players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", "Attack hostile mobs", true);
    public final BooleanSetting targetAnimals = new BooleanSetting("Target Animals", "Attack passive animals", false);
    public final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible", "Ignore invisible entities", true);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Ignore friends", true);

    private int cooldown = 0;

    public Triggerbot() {
        super("Triggerbot", "Attacks automatically when your crosshair is on an entity", Category.COMBAT);
        addSetting(version);
        addSetting(v1Range);
        addSetting(v1Speed);
        addSetting(v1SpeedRand);
        addSetting(v1SwingHand);
        addSetting(v1AttackWhileBlocking);
        addSetting(v1SimulateMouseClick);
        addSetting(v2EarlyHitChance);
        addSetting(v2ShieldCheck);
        addSetting(v2RequireMouseDown);
        addSetting(v2TargetMissChance);
        addSetting(v2AirCrits);
        addSetting(v2IgnoreActivationClick);
        addSetting(v2LimitToItems);
        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(targetAnimals);
        addSetting(ignoreInvisible);
        addSetting(ignoreFriends);

        for (var s : new com.anormal.client.setting.Setting<?>[]{v1Range, v1Speed, v1SpeedRand, v1SwingHand,
                v1AttackWhileBlocking, v1SimulateMouseClick}) {
            s.visibleIf(() -> version.is("V1"));
        }
        for (var s : new com.anormal.client.setting.Setting<?>[]{v2EarlyHitChance, v2ShieldCheck, v2RequireMouseDown,
                v2TargetMissChance, v2AirCrits, v2IgnoreActivationClick, v2LimitToItems}) {
            s.visibleIf(() -> version.is("V2"));
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (version.is("V1")) {
            if (mc.player.isBlocking() && !v1AttackWhileBlocking.isEnabled()) return;
        } else {
            if (v2RequireMouseDown.isEnabled() && !mc.options.attackKey.isPressed()) return;
            if (v2LimitToItems.isEnabled() && mc.player.getMainHandStack().isEmpty()) return;
            if (v2AirCrits.isEnabled() && mc.player.isOnGround()) return;
            if (v2TargetMissChance.getValue() > 0 && Math.random() * 100.0 < v2TargetMissChance.getValue()) return;
        }

        Entity cross = mc.crosshairTarget != null ? mc.targetedEntity : null;
        if (!(cross instanceof LivingEntity living)) return;
        if (living == mc.player || !living.isAlive()) return;
        if (!passesFilter(living)) return;

        double range = version.is("V1") ? v1Range.getValue() : 3.0;
        if (version.is("V2") && v2EarlyHitChance.getValue() > 0) {
            range += 0.5 * (v2EarlyHitChance.getValue() / 100.0);
        }
        if (mc.player.distanceTo(living) > range) return;

        if (version.is("V2") && v2ShieldCheck.isEnabled() && living.isBlocking()) return;

        attack();
    }

    private void attack() {
        try {
            if (version.is("V1") && v1SimulateMouseClick.isEnabled()) {
                // Press and release the attack key, exactly like a real click.
                mc.options.attackKey.setPressed(true);
                mc.options.attackKey.setPressed(false);
            } else {
                mc.interactionManager.attackEntity(mc.player, mc.crosshairTarget instanceof
                        net.minecraft.util.hit.EntityHitResult ehr ? ehr.getEntity() : null);
            }

            if (version.is("V1")) {
                switch (v1SwingHand.getValue()) {
                    case "Main hand" -> mc.player.swingHand(Hand.MAIN_HAND);
                    case "Off hand" -> mc.player.swingHand(Hand.OFF_HAND);
                    default -> { }
                }
            } else {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        } catch (Throwable ignored) {
        }

        int delay;
        if (version.is("V1")) {
            delay = Math.max(1, (int) Math.round(20.0 / Math.max(1.0, v1Speed.getValue())));
            double rnd = v1SpeedRand.getValue() / 100.0;
            if (rnd > 0) delay = Math.max(1, (int) Math.round(delay * (1.0 + (Math.random() * 2 - 1) * rnd)));
        } else {
            delay = 2;
        }
        cooldown = delay;
    }

    private boolean passesFilter(LivingEntity e) {
        if (ignoreInvisible.isEnabled() && e.isInvisible()) return false;
        if (e instanceof PlayerEntity pl) {
            if (!targetPlayers.isEnabled()) return false;
            if (ignoreFriends.isEnabled() && isFriend(pl)) return false;
            return true;
        }
        if (e instanceof HostileEntity) return targetMobs.isEnabled();
        if (e instanceof PassiveEntity) return targetAnimals.isEnabled();
        return targetMobs.isEnabled();
    }

    private boolean isFriend(PlayerEntity pl) {
        try {
            var friends = com.anormal.client.module.ModuleManager.getModule(
                    com.anormal.client.module.impl.client.Friends.class);
            if (friends == null) return false;
            java.lang.reflect.Field f = friends.getClass().getDeclaredField("friends");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<String> list = (java.util.List<String>) f.get(friends);
            return list != null && list.contains(pl.getName().getString());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
