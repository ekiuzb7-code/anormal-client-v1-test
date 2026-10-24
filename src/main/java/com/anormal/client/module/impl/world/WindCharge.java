package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.rotation.RotationController;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/**
 * Wind charge boost.
 *
 * <p>The old version set the pitch and threw in the same tick, but never sent a look packet. The
 * server therefore threw the charge using the <em>previous</em> rotation - straight ahead instead
 * of at your feet - which is why it seemed to do nothing online while working in singleplayer.</p>
 *
 * <p>Now the rotation is sent explicitly right before the item is used, so the server throws it
 * where it is supposed to go. No velocity is written either: the charge itself is the boost, and
 * faking velocity just gets corrected by the server.</p>
 */
public class WindCharge extends Module {

    public final ModeSetting mode = new ModeSetting("Mode",
            "Jump Boost throws at your feet and jumps, Throw Only just throws",
            "Jump Boost", "Jump Boost", "Throw Only");

    public final NumberSetting aimPitch = new NumberSetting("Aim Pitch",
            "Pitch used for the throw (90 = straight down)", 90.0, 40.0, 90.0, 1.0);

    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim",
            "Send the rotation without moving your camera", true);

    public final BooleanSetting restorePitch = new BooleanSetting("Restore Pitch",
            "Put your camera back after the throw (visible mode)", true);

    public final BooleanSetting jump = new BooleanSetting("Jump",
            "Press jump after the throw for extra height", true);

    public final NumberSetting jumpTicks = new NumberSetting("Jump Ticks",
            "How long the jump key stays pressed", 3.0, 1.0, 10.0, 1.0);

    public final NumberSetting delay = new NumberSetting("Delay",
            "Ticks to wait before throwing", 0.0, 0.0, 40.0, 1.0);

    public final BooleanSetting autoDisable = new BooleanSetting("Auto Disable",
            "Switch the module off after one use", true);

    // ------------------------------------------------------------------
    //  State
    // ------------------------------------------------------------------
    private static final int IDLE = 0;
    private static final int THROWING = 1;
    private static final int RECOVER = 2;

    private final RotationController rotation = new RotationController();

    private int stage = IDLE;
    private int timer = 0;
    private int originalSlot = -1;
    private float originalPitch;
    private boolean wePressedJump = false;

    public WindCharge() {
        super("WindCharge", "Throws a wind charge at your feet and jumps for height", Category.WORLD);
        addSetting(mode);
        addSetting(aimPitch);
        addSetting(silentAim);
        addSetting(restorePitch);
        addSetting(jump);
        addSetting(jumpTicks);
        addSetting(delay);
        addSetting(autoDisable);

        restorePitch.visibleIf(() -> !silentAim.isEnabled());
        jumpTicks.visibleIf(() -> jump.isEnabled());
    }

    @Override
    public void onEnable() {
        stage = IDLE;
        timer = delay.getValue().intValue();
        originalSlot = -1;
        wePressedJump = false;
        rotation.reset();
        if (mc.player != null) originalPitch = mc.player.getPitch();
    }

    @Override
    public void onDisable() {
        releaseJump();
        restoreSlot();
        restoreCamera();
        stage = IDLE;
        timer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        switch (stage) {
            case IDLE -> {
                if (timer > 0) {
                    timer--;
                    return;
                }
                throwCharge();
            }
            case THROWING -> {
                if (--timer > 0) return;
                releaseJump();
                restoreCamera();
                stage = RECOVER;
                timer = 2;
            }
            case RECOVER -> {
                if (--timer > 0) return;
                restoreSlot();
                stage = IDLE;
                timer = 0;
                if (autoDisable.isEnabled()) setEnabled(false);
            }
            default -> stage = IDLE;
        }
    }

    // ------------------------------------------------------------------
    //  Throw
    // ------------------------------------------------------------------

    private void throwCharge() {
        int slot = findSlot("wind_charge");
        if (slot == -1) {
            if (autoDisable.isEnabled()) setEnabled(false);
            return;
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        originalPitch = mc.player.getPitch();
        mc.player.getInventory().setSelectedSlot(slot);

        float yaw = mc.player.getYaw();
        float pitch = aimPitch.getValue().floatValue();

        // The server needs the rotation BEFORE the item packet, otherwise it throws using the old
        // look direction. This is the whole fix.
        rotation.send(yaw, pitch);
        if (!silentAim.isEnabled()) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }

        try {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }

        if (jump.isEnabled() && mode.is("Jump Boost")) pressJump();

        stage = THROWING;
        timer = Math.max(1, jumpTicks.getValue().intValue());
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    private void pressJump() {
        try {
            if (mc.options.jumpKey.isPressed()) return; // the player is already jumping
            mc.options.jumpKey.setPressed(true);
            wePressedJump = true;
        } catch (Throwable ignored) {
        }
    }

    private void releaseJump() {
        if (!wePressedJump) return;
        try {
            mc.options.jumpKey.setPressed(false);
        } catch (Throwable ignored) {
        }
        wePressedJump = false;
    }

    private void restoreCamera() {
        if (silentAim.isEnabled() || !restorePitch.isEnabled()) return;
        try {
            mc.player.setPitch(originalPitch);
        } catch (Throwable ignored) {
        }
    }

    private void restoreSlot() {
        if (originalSlot < 0 || mc.player == null) return;
        try {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        } catch (Throwable ignored) {
        }
        originalSlot = -1;
    }

    private int findSlot(String path) {
        for (int i = 0; i < 9; i++) {
            try {
                Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                if (id != null && id.getPath().equals(path)) return i;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }
}
