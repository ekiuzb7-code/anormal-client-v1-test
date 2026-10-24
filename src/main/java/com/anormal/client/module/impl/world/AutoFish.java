package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * Wurst-style AutoFish.
 *
 * <p>The old version only watched the bobber's velocity, so a bite that happened while the hook
 * was drifting was missed and the rod was reeled in at random. This version uses the same two
 * detection modes Wurst offers:</p>
 *
 * <ul>
 *   <li><b>Sound</b> - listens for the bobber splash packet near the player. Resilient, but other
 *       players' bites can be picked up unless {@code Valid Range} is kept small.</li>
 *   <li><b>Entity</b> - watches the bobber itself (hooked entity + the dip in vertical velocity).
 *       More accurate, slightly more obvious to anti-cheats.</li>
 * </ul>
 *
 * <p>Cast, reel and retry are driven by three independent timers, so a failed cast no longer
 * leaves the module stuck.</p>
 */
public class AutoFish extends Module {

    public final ModeSetting biteMode = new ModeSetting("Bite Mode",
            "Sound listens for the splash packet, Entity watches the bobber itself",
            "Sound", "Sound", "Entity");

    public final NumberSetting validRange = new NumberSetting("Valid Range",
            "Bites further away than this are ignored (sound mode only)", 1.5, 0.25, 8.0, 0.25);

    public final NumberSetting catchDelay = new NumberSetting("Catch Delay",
            "Ticks to wait after a bite before reeling in", 0.0, 0.0, 60.0, 1.0);

    public final NumberSetting retryDelay = new NumberSetting("Retry Delay",
            "Ticks to wait before trying to cast again", 15.0, 0.0, 100.0, 1.0);

    public final NumberSetting patience = new NumberSetting("Patience",
            "Reel in and recast when nothing bites for this many seconds", 60.0, 10.0, 120.0, 1.0);

    public final BooleanSetting recastGround = new BooleanSetting("Recast Ground",
            "Reel in when the bobber lands on a block", true);

    public final BooleanSetting recastCaught = new BooleanSetting("Recast Caught",
            "Reel in when the bobber snags an entity", true);

    public final BooleanSetting autoRod = new BooleanSetting("Auto Rod",
            "Switch to a fishing rod when the held item is not one", true);

    public final BooleanSetting showStatus = new BooleanSetting("Show Status",
            "Show the fishing state on screen", false);

    // ------------------------------------------------------------------
    //  State
    // ------------------------------------------------------------------

    private static final int CAST_WAIT = 6;

    private int castTimer;
    private int catchTimer;
    private int patienceTicks;
    private int lastHookId = -1;
    private double lastVelY;
    private boolean hadHook;
    private boolean bite;
    private String status = "";

    public AutoFish() {
        super("AutoFish", "Catches fish and recasts the line automatically", Category.WORLD);
        addSetting(biteMode);
        addSetting(validRange);
        addSetting(catchDelay);
        addSetting(retryDelay);
        addSetting(patience);
        addSetting(recastGround);
        addSetting(recastCaught);
        addSetting(autoRod);
        addSetting(showStatus);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        castTimer = 0;
        catchTimer = 0;
        patienceTicks = 0;
        lastHookId = -1;
        lastVelY = 0.0;
        hadHook = false;
        bite = false;
        status = "";
    }

    // ------------------------------------------------------------------
    //  Bite input (called from ConnectionMixin for sound mode)
    // ------------------------------------------------------------------

    /** A bobber splash packet arrived. */
    public void onBiteSound(double x, double y, double z) {
        if (!isEnabled() || mc.player == null) return;
        if (!biteMode.is("Sound")) return;
        if (mc.player.fishHook == null) return;
        double range = validRange.getValue();
        if (mc.player.squaredDistanceTo(x, y, z) > range * range) return;
        bite = true;
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.isSpectator()) return;

        if (!holdingRod()) {
            if (autoRod.isEnabled() && selectRod()) return;
            status = "No fishing rod";
            return;
        }

        FishingBobberEntity hook = mc.player.fishHook;

        // --- reel-in phase ---
        if (catchTimer > 0) {
            catchTimer--;
            status = "Reeling in";
            if (catchTimer == 0) {
                reel();
                castTimer = retryDelay.getValue().intValue();
                bite = false;
                hadHook = false;
                patienceTicks = 0;
            }
            return;
        }

        // --- waiting to cast ---
        if (castTimer > 0) {
            castTimer--;
            status = "Casting in " + castTimer;
            return;
        }

        // --- no hook: cast ---
        if (hook == null) {
            if (hadHook) {
                hadHook = false;
                patienceTicks = 0;
                castTimer = retryDelay.getValue().intValue();
                status = "Line came back";
                return;
            }
            cast();
            castTimer = CAST_WAIT;
            status = "Casting";
            return;
        }

        // --- hook in the water ---
        hadHook = true;
        patienceTicks++;
        status = "Waiting for a bite";

        if (hook.getId() != lastHookId) {
            lastHookId = hook.getId();
            patienceTicks = 0;
            bite = false;
        }

        if (biteMode.is("Entity")) detectEntityBite(hook);

        if (bite) {
            bite = false;
            int delay = catchDelay.getValue().intValue();
            if (delay <= 0) {
                reel();
                castTimer = retryDelay.getValue().intValue();
                hadHook = false;
                patienceTicks = 0;
                status = "Caught";
            } else {
                catchTimer = delay;
            }
            return;
        }

        // Patience: nothing bit for too long, so start over.
        int patienceLimit = patience.getValue().intValue() * 20;
        if (patienceTicks > patienceLimit) {
            reel();
            castTimer = retryDelay.getValue().intValue();
            hadHook = false;
            patienceTicks = 0;
            status = "Patience ran out";
        }
    }

    /** Bobber-based detection: a snagged entity or the characteristic dip of a bite. */
    private void detectEntityBite(FishingBobberEntity hook) {
        try {
            if (recastCaught.isEnabled() && hook.getHookedEntity() != null) {
                bite = true;
                return;
            }
            if (recastGround.isEnabled() && hook.isOnGround()) {
                bite = true;
                return;
            }

            double vy = hook.getVelocity().y;
            // A bite pulls the bobber down sharply; only trust it while the bobber floats.
            if (hook.isTouchingWater() && lastVelY >= -0.05 && vy < -0.15) {
                bite = true;
            }
            lastVelY = vy;
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private boolean holdingRod() {
        return mc.player.getMainHandStack().getItem() instanceof FishingRodItem
                || mc.player.getOffHandStack().getItem() instanceof FishingRodItem;
    }

    /** Switches the hotbar to a rod. @return true when a switch was made. */
    private boolean selectRod() {
        try {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof FishingRodItem)) continue;
                if (mc.player.getInventory().getSelectedSlot() != slot) {
                    mc.player.getInventory().setSelectedSlot(slot);
                }
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void cast() {
        try {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    private void reel() {
        try {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showStatus.isEnabled() || mc.textRenderer == null || mc.getWindow() == null) return;
        if (status.isEmpty()) return;
        try {
            String line = "§6AutoFish §7| §f" + status;
            context.drawText(mc.textRenderer, line, 6, mc.getWindow().getScaledHeight() - 52, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {
        }
    }
}
