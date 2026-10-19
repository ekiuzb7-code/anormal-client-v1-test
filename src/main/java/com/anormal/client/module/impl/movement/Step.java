package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Lets the player walk up full blocks without jumping.
 *
 * <p>Vanilla gives the player a step height of 0.6, which is just short of a full block. Raising
 * it makes the game's own collision code lift the player over the edge, so nothing is teleported
 * and the movement stays identical to vanilla auto-jump - just faster and without the jump arc.</p>
 */
public class Step extends Module {

    public final NumberSetting height = new NumberSetting("Height",
            "Step height in blocks (1.0 = one full block)", 1.0, 0.6, 2.5, 0.1);
    public final BooleanSetting onlyOnGround = new BooleanSetting("Only On Ground",
            "Only step while standing on the ground", true);
    public final BooleanSetting onlySprinting = new BooleanSetting("Only Sprinting",
            "Only step while sprinting", false);
    public final BooleanSetting disableSneaking = new BooleanSetting("Disable When Sneaking",
            "Do not step while sneaking", true);
    public final BooleanSetting disableInWater = new BooleanSetting("Disable In Water",
            "Do not step while in water or lava", true);
    public final BooleanSetting disableFlying = new BooleanSetting("Disable When Flying",
            "Do not step while flying, gliding or riding", true);

    public Step() {
        super("Step", "Walk up full blocks without jumping", Category.MOVEMENT);
        addSetting(height);
        addSetting(onlyOnGround);
        addSetting(onlySprinting);
        addSetting(disableSneaking);
        addSetting(disableInWater);
        addSetting(disableFlying);
    }

    /** Called from the entity mixin; {@code false} leaves the vanilla step height untouched. */
    public static boolean isActive() {
        try {
            Step module = ModuleManager.getModule(Step.class);
            if (module == null || !module.isEnabled()) return false;
            return module.allowed();
        } catch (Throwable t) {
            return false;
        }
    }

    public static float configuredHeight() {
        try {
            Step module = ModuleManager.getModule(Step.class);
            return module == null ? 0.6f : module.height.getValue().floatValue();
        } catch (Throwable t) {
            return 0.6f;
        }
    }

    private boolean allowed() {
        if (mc.player == null) return false;
        if (onlyOnGround.isEnabled() && !mc.player.isOnGround()) return false;
        if (onlySprinting.isEnabled() && !mc.player.isSprinting()) return false;
        if (disableSneaking.isEnabled() && mc.player.isSneaking()) return false;
        if (disableInWater.isEnabled() && (mc.player.isTouchingWater() || mc.player.isInLava())) return false;
        if (disableFlying.isEnabled() && (mc.player.getAbilities().flying || mc.player.isGliding()
                || mc.player.hasVehicle())) return false;
        return true;
    }
}
