package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Sprint-resets when you attack ("W-tap"), which removes the knockback you
 * would otherwise take.
 *
 * <p>Two interchangeable implementations:</p>
 * <ul>
 *   <li><b>V1 - Vape</b>: "Sprint Reset" mode (Normal / Legit / Always) plus a
 *       "Right Click Cancel" mode, matching Vape's WTap module and its
 *       {@code WTapSprintResetMode} / {@code WTapRightClickUseCancelMode}
 *       submodules.</li>
 *   <li><b>V2</b>: the classic fixed-tick sprint reset - release the sprint key
 *       for a configurable number of ticks after every attack.</li>
 * </ul>
 */
public class WTap extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2");

    // ---- V1 - Vape ----
    public final ModeSetting v1SprintReset = new ModeSetting("V1 Sprint Reset", "Sprint reset behaviour", "Normal", "Off", "Normal", "Legit", "Always");
    public final ModeSetting v1RightClickCancel = new ModeSetting("V1 Right Click Cancel", "Cancel a right-click use when attacking", "Off", "Off", "On", "Only When Blocking");
    public final NumberSetting v1ResetTicks = new NumberSetting("V1 Reset Ticks", "Ticks the sprint key is released for", 1.0, 1.0, 10.0, 1.0);
    public final BooleanSetting v1OnlyWhileMoving = new BooleanSetting("V1 Only While Moving", "Only reset while you are moving forwards", false);

    // ---- V2 ----
    public final NumberSetting v2ResetTicks = new NumberSetting("V2 Reset Ticks", "Ticks the sprint key is released for", 1.0, 1.0, 10.0, 1.0);
    public final BooleanSetting v2RequireSprint = new BooleanSetting("V2 Require Sprint", "Only reset when you are actually sprinting", true);
    public final NumberSetting v2Delay = new NumberSetting("V2 Delay", "Ticks to wait after the reset before sprinting again", 0.0, 0.0, 10.0, 1.0);

    private int releaseTicks = 0;
    private int resumeDelay = 0;
    private boolean released = false;

    public WTap() {
        super("WTap", "Sprint-resets on attack to reduce your knockback", Category.COMBAT);
        addSetting(version);
        addSetting(v1SprintReset);
        addSetting(v1RightClickCancel);
        addSetting(v1ResetTicks);
        addSetting(v1OnlyWhileMoving);
        addSetting(v2ResetTicks);
        addSetting(v2RequireSprint);
        addSetting(v2Delay);

        for (var s : new com.anormal.client.setting.Setting<?>[]{v1SprintReset, v1RightClickCancel, v1ResetTicks, v1OnlyWhileMoving}) {
            s.visibleIf(() -> version.is("V1"));
        }
        for (var s : new com.anormal.client.setting.Setting<?>[]{v2ResetTicks, v2RequireSprint, v2Delay}) {
            s.visibleIf(() -> version.is("V2"));
        }
    }

    @Override
    public void onDisable() {
        releaseTicks = 0;
        resumeDelay = 0;
        if (released && mc.options != null) {
            try {
                mc.options.sprintKey.setPressed(false);
            } catch (Throwable ignored) {
            }
        }
        released = false;
    }

    /** Called by the attack path right after a hit lands. */
    public void onAttack() {
        if (!isEnabled() || mc.player == null) return;

        if (version.is("V1")) {
            if (v1SprintReset.is("Off")) return;
            if (v1OnlyWhileMoving.isEnabled() && mc.player.forwardSpeed <= 0) return;
            if (v1SprintReset.is("Legit") && !mc.player.isSprinting()) return;
            releaseTicks = v1ResetTicks.getValue().intValue();
        } else {
            if (v2RequireSprint.isEnabled() && !mc.player.isSprinting()) return;
            releaseTicks = v2ResetTicks.getValue().intValue();
            resumeDelay = v2Delay.getValue().intValue();
        }
        released = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) return;

        if (releaseTicks > 0) {
            releaseTicks--;
            try {
                mc.player.setSprinting(false);
                mc.options.sprintKey.setPressed(false);
            } catch (Throwable ignored) {
            }
            return;
        }

        if (released) {
            if (resumeDelay > 0) {
                resumeDelay--;
                return;
            }
            released = false;
            try {
                mc.options.sprintKey.setPressed(mc.options.forwardKey.isPressed());
            } catch (Throwable ignored) {
            }
        }
    }
}
