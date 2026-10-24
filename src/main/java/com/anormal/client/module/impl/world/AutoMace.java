package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.rotation.Rotation;
import com.anormal.client.rotation.RotationController;
import com.anormal.client.rotation.Smoothing;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Mace smash helper.
 *
 * <p>Three things made the old version fail on real servers:</p>
 * <ol>
 *   <li><b>Reach.</b> It targeted up to 4.5 blocks while the server only accepts ~3.0, so every
 *       hit beyond arm's length was silently dropped.</li>
 *   <li><b>Silent aim.</b> It set the rotation, attacked and restored it in the same tick. The
 *       server never received the rotated look packet, so the hit was validated against the old
 *       rotation - which is exactly why it "worked in singleplayer" but not online.</li>
 *   <li><b>No line of sight check.</b> It happily swung at players through walls.</li>
 * </ol>
 *
 * <p>Now the aim goes through {@link RotationController}, which sends the look packet before the
 * attack packet, and the range is clamped to the player's real interaction range.</p>
 */
public class AutoMace extends Module {

    // ---------------- Targets ----------------
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting hostiles = new BooleanSetting("Hostiles", "Target hostile mobs", true);
    public final NumberSetting range = new NumberSetting("Range", "Target distance (clamped to your real reach)", 3.0, 1.0, 6.0, 0.1);
    public final BooleanSetting lineOfSight = new BooleanSetting("Line Of Sight", "Skip targets behind blocks", true);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Never hit friends", true);
    public final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible", "Skip invisible targets", true);
    public final BooleanSetting useTargetFilter = new BooleanSetting("Use Target Filter", "Apply TargetFilter rules", true);

    // ---------------- Aim ----------------
    public final BooleanSetting aim = new BooleanSetting("Aim", "Aim at the target while attacking", true);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim",
            "Send the rotation without moving your camera (off = the character visibly turns)", false);
    public final ModeSetting rotationSmoothing = new ModeSetting("Rotation Smoothing",
            "How the aim approaches the target", "Ease Out Cubic", Smoothing.NAMES);
    public final NumberSetting rotationSmoothness = new NumberSetting("Rotation Smoothness",
            "0 = snap instantly, 100 = slowest", 25.0, 0.0, 100.0, 1.0);
    public final BooleanSetting rotationGcd = new BooleanSetting("Rotation GCD",
            "Round the rotation to your mouse sensitivity step", true);

    // ---------------- Attack ----------------
    public final BooleanSetting attack = new BooleanSetting("Attack", "Auto attack the smash target", true);
    public final NumberSetting extraDelay = new NumberSetting("Extra Delay", "Ticks after cooldown, negative is early", 0.0, -10.0, 20.0, 1.0);
    public final NumberSetting minFall = new NumberSetting("Min Fall", "Minimum fall distance for a smash hit", 1.5, 0.0, 5.0, 0.1);
    public final BooleanSetting smashOnly = new BooleanSetting("Smash Only", "Only attack while falling with enough height", true);

    // ---------------- Mace ----------------
    public final ModeSetting maceSelection = new ModeSetting("Mace Selection", "Manual uses Mace Type, Auto picks best", "Manual", "Manual", "Auto");
    public final ModeSetting maceType = new ModeSetting("Mace Type", "Preferred mace for Manual selection", "Density", "Density", "Breach");

    // ---------------- Shield ----------------
    public final BooleanSetting stunSlam = new BooleanSetting("Stun Slam", "Axe hit to drop the shield, then mace", true);
    public final NumberSetting stunChance = new NumberSetting("Stun Chance", "Stun Slam activation chance", 50.0, 0.0, 100.0, 1.0);
    public final NumberSetting stunDelay = new NumberSetting("Stun Delay", "Ticks between the axe hit and the mace hit", 2.0, 0.0, 10.0, 1.0);

    // ---------------- Elytra ----------------
    public final BooleanSetting autoUnequipElytra = new BooleanSetting("Auto Unequip Elytra", "Swap the elytra for a chestplate when gliding", true);
    public final BooleanSetting reequipElytra = new BooleanSetting("Re-equip Elytra", "Put the elytra back after the bounce", true);

    public final BooleanSetting limitToItems = new BooleanSetting("Limit to Items", "Only while holding a weapon", false);

    // ---------------- State ----------------
    private final RotationController rotation = new RotationController();

    private int originalSlot = -1;
    private int noTargetTicks = 0;
    private int equipCooldown = 0;
    private int stunStage = 0;
    private int stunTimer = 0;
    private LivingEntity stunTarget;
    private boolean swapped = false;
    private boolean wasGliding = false;

    public AutoMace() {
        super("AutoMace", "Swaps to a mace and times smash attacks while falling", Category.WORLD);

        addSetting(players);
        addSetting(hostiles);
        addSetting(range);
        addSetting(lineOfSight);
        addSetting(ignoreFriends);
        addSetting(ignoreInvisible);
        addSetting(useTargetFilter);

        addSetting(aim);
        addSetting(silentAim);
        addSetting(rotationSmoothing);
        addSetting(rotationSmoothness);
        addSetting(rotationGcd);

        addSetting(attack);
        addSetting(extraDelay);
        addSetting(minFall);
        addSetting(smashOnly);

        addSetting(maceSelection);
        addSetting(maceType);

        addSetting(stunSlam);
        addSetting(stunChance);
        addSetting(stunDelay);

        addSetting(autoUnequipElytra);
        addSetting(reequipElytra);

        addSetting(limitToItems);

        // Only show what applies to the chosen mode.
        maceType.visibleIf(() -> maceSelection.is("Manual"));
        stunChance.visibleIf(() -> stunSlam.isEnabled());
        stunDelay.visibleIf(() -> stunSlam.isEnabled());
        rotationSmoothing.visibleIf(() -> aim.isEnabled());
        rotationSmoothness.visibleIf(() -> aim.isEnabled());
        rotationGcd.visibleIf(() -> aim.isEnabled());
        reequipElytra.visibleIf(() -> autoUnequipElytra.isEnabled());
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        restore();
        reset();
    }

    private void reset() {
        originalSlot = -1;
        swapped = false;
        noTargetTicks = 0;
        equipCooldown = 0;
        stunStage = 0;
        stunTimer = 0;
        stunTarget = null;
        wasGliding = false;
        rotation.reset();
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || !mc.player.isAlive()) {
            restore();
            return;
        }

        if (limitToItems.isEnabled() && !holdingWeapon()) {
            restore();
            return;
        }

        if (equipCooldown > 0) equipCooldown--;

        // --- elytra handling ---
        if (mc.player.isGliding()) {
            wasGliding = true;
            if (!autoUnequipElytra.isEnabled()) {
                restore();
                return;
            }
            tryUnequipElytra();
            return;
        }
        if (wasGliding && reequipElytra.isEnabled() && mc.player.getVelocity().y > 0.3) {
            tryReequipElytra();
        }
        if (!mc.player.isGliding() && mc.player.isOnGround()) wasGliding = false;

        // --- two-stage shield break ---
        if (stunStage > 0) {
            if (stunTimer > 0) {
                stunTimer--;
                return;
            }
            finishStun();
            return;
        }

        boolean falling = !mc.player.isOnGround() && mc.player.getVelocity().y < -0.2;
        boolean smashReady = mc.player.fallDistance >= minFall.getValue().floatValue();

        LivingEntity target = selectTarget();
        if (target == null) {
            if (++noTargetTicks > 20) restore();
            return;
        }
        noTargetTicks = 0;

        if (smashOnly.isEnabled() && !(falling && smashReady)) {
            restore();
            return;
        }

        int mace = findBestMace(maceSelection.is("Auto") ? null : maceType.getValue());
        if (mace == -1) {
            restore();
            return;
        }

        if (stunSlam.isEnabled() && target.isBlocking() && Math.random() * 100.0 < stunChance.getValue()) {
            if (startStun(target)) return;
        }

        if (!heldPath().equals("mace")) swapTo(mace);

        if (aim.isEnabled()) aimAt(target);

        if (attack.isEnabled()) {
            float progress = mc.player.getAttackCooldownProgress(0.5f);
            int extra = extraDelay.getValue().intValue();
            float needed = extra >= 0 ? 1.0f : Math.max(0.0f, 1.0f + extra / 20.0f);

            if (progress >= needed && canReach(target)) {
                swing(target);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Shield break: axe first, mace after the shield drops
    // ------------------------------------------------------------------

    private boolean startStun(LivingEntity target) {
        int axe = findSlot("_axe", false);
        if (axe == -1) return false;

        swapTo(axe);
        try {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
        stunTarget = target;
        stunStage = 1;
        stunTimer = stunDelay.getValue().intValue();
        return true;
    }

    private void finishStun() {
        LivingEntity target = stunTarget;
        stunTarget = null;
        stunStage = 0;
        if (target == null || !target.isAlive() || !canReach(target)) return;

        int mace = findBestMace(maceSelection.is("Auto") ? null : maceType.getValue());
        if (mace == -1) return;

        swapTo(mace);
        if (aim.isEnabled()) aimAt(target);
        swing(target);
    }

    // ------------------------------------------------------------------
    //  Target selection
    // ------------------------------------------------------------------

    /** The setting is only an upper bound - the server enforces the real reach. */
    private double reach() {
        double configured = range.getValue();
        try {
            configured = Math.min(configured, mc.player.getEntityInteractionRange());
        } catch (Throwable ignored) {
        }
        return configured;
    }

    private boolean canReach(LivingEntity target) {
        return mc.player.squaredDistanceTo(target) <= reach() * reach();
    }

    private LivingEntity selectTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double max = reach();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            if (living.isSpectator()) continue;
            if (ignoreInvisible.isEnabled() && living.isInvisible()) continue;

            if (living instanceof PlayerEntity player) {
                if (!players.isEnabled()) continue;
                if (ignoreFriends.isEnabled() && Friends.isFriend(player.getGameProfile().name())) continue;
                try {
                    if (mc.player.isTeammate(player)) continue;
                } catch (Throwable ignored) {
                }
            } else if (living instanceof HostileEntity) {
                if (!hostiles.isEnabled()) continue;
            } else {
                continue;
            }

            if (useTargetFilter.isEnabled() && !TargetFilter.isValid(living)) continue;

            double distance = mc.player.distanceTo(living);
            if (distance > max) continue;
            if (lineOfSight.isEnabled() && !mc.player.canSee(living)) continue;

            // Prefer the target the crosshair is closest to, so we hit what the player is aiming at.
            Vec3d aimPoint = new Vec3d(living.getX(), living.getY() + living.getHeight() * 0.5, living.getZ());
            double angle = Rotation.angle(mc.player.getRotationVec(1.0f), aimPoint.subtract(mc.player.getEyePos()));
            double score = angle + distance * 4.0;
            if (score >= bestScore) continue;

            best = living;
            bestScore = score;
        }
        return best;
    }

    // ------------------------------------------------------------------
    //  Aim + attack
    // ------------------------------------------------------------------

    private Vec3d aimPoint(LivingEntity target) {
        // Chest height gives the most reliable hit registration for a smash.
        return new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.6, target.getZ());
    }

    private void aimAt(LivingEntity target) {
        try {
            Vec3d point = aimPoint(target);
            Rotation aimRotation = rotation.step(
                    point,
                    rotationSmoothness.getValue().floatValue() / 100.0f,
                    Smoothing.byName(rotationSmoothing.getValue()),
                    true,
                    silentAim.isEnabled());
            rotation.apply(aimRotation, silentAim.isEnabled(), rotationGcd.isEnabled());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Attacks. In silent mode the look packet is re-sent immediately before the attack, so the
     * server validates the hit against the rotated view instead of the camera.
     */
    private void swing(LivingEntity target) {
        try {
            if (silentAim.isEnabled() && aim.isEnabled()) {
                Rotation wanted = Rotation.toward(mc.player.getEyePos(), aimPoint(target));
                rotation.send(wanted.yaw(), wanted.pitch());
            }
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Items
    // ------------------------------------------------------------------

    private boolean holdingWeapon() {
        String held = heldPath();
        return held.endsWith("_sword") || held.endsWith("_axe") || held.equals("mace") || held.equals("trident");
    }

    private String heldPath() {
        try {
            Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
            return id == null ? "" : id.getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private int findSlot(String path, boolean exact) {
        for (int i = 0; i < 9; i++) {
            try {
                Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                if (id != null && (exact ? id.getPath().equals(path) : id.getPath().endsWith(path))) return i;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * Picks the mace to swing. Density and Breach are enchantments, not separate items, so the
     * choice has to be made by reading the enchantment levels.
     */
    private int findBestMace(String preferred) {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack == null || stack.isEmpty()) continue;

            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id == null || !id.getPath().equals("mace")) continue;

            int density = enchantLevel(stack, "density");
            int breach = enchantLevel(stack, "breach");
            int score = "Breach".equals(preferred) ? breach * 3 + density : density * 3 + breach;

            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private int enchantLevel(ItemStack stack, String path) {
        try {
            var component = stack.getEnchantments();
            for (var entry : component.getEnchantments()) {
                String id = entry.getKey().map(key -> key.getValue().getPath()).orElse("");
                if (id.equals(path)) return component.getLevel(entry);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    // ------------------------------------------------------------------
    //  Elytra
    // ------------------------------------------------------------------

    /** Holding a chestplate does nothing on its own - a right click is what swaps the chest slot. */
    private void tryUnequipElytra() {
        if (equipCooldown > 0) return;
        int chest = findSlot("_chestplate", false);
        if (chest == -1) return;

        swapTo(chest);
        try {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
        equipCooldown = 12;
    }

    private void tryReequipElytra() {
        if (equipCooldown > 0) return;
        int elytra = findSlot("elytra", true);
        if (elytra == -1) return;

        swapTo(elytra);
        try {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
        equipCooldown = 12;
        wasGliding = false;
    }

    // ------------------------------------------------------------------
    //  Slots
    // ------------------------------------------------------------------

    private void swapTo(int slot) {
        if (!swapped) originalSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        swapped = true;
    }

    private void restore() {
        if (swapped && mc.player != null && originalSlot >= 0 && originalSlot < 9) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } catch (Throwable ignored) {
            }
        }
        swapped = false;
        originalSlot = -1;
        noTargetTicks = 0;
    }
}
