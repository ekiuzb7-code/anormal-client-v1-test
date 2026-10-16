package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

/**
 * Prevents you from walking off the edges of blocks.
 *
 * <p>Two independent mechanisms, so it works even if one of them is bypassed:</p>
 * <ol>
 *   <li><b>Invisible clipping</b> - {@code PlayerEntityMixin} forces
 *       {@code clipAtLedge()} to true, which makes vanilla itself refuse the
 *       part of your movement that would take you off a block edge. This is
 *       the same trick Wurst and Meteor use.</li>
 *   <li><b>Edge sneak</b> - every tick we check the Wurst "adjusted box"; when
 *       you are close enough to an edge we hold the real sneak key. This both
 *       gives the visible feedback (when "Sneak at edges" is on) and acts as a
 *       hard safety net that stops the fall regardless of the mixin.</li>
 * </ol>
 */
public class SafeWalk extends Module {

    /** Wurst: CheckboxSetting("Sneak at edges", "Visibly sneak at edges.", false) */
    public final BooleanSetting sneak = new BooleanSetting(
            "Sneak at edges",
            "Visibly sneak at edges.",
            true);

    /** Wurst: SliderSetting("Sneak edge distance", ..., 0.05, 0.05, 0.25, 0.001) */
    public final NumberSetting edgeDistance = new NumberSetting(
            "Sneak edge distance",
            "How close SafeWalk will let you get to the edge before sneaking. "
                    + "This setting is only used when \"Sneak at edges\" is enabled.",
            0.05, 0.05, 0.25, 0.001);

    private boolean sneaking;

    public SafeWalk() {
        super("SafeWalk", "Prevents you from walking off the edges of blocks.", Category.MOVEMENT);
        addSetting(sneak);
        addSetting(edgeDistance);
    }

    @Override
    public void onEnable() {
        // Wurst disables Parkour when SafeWalk is enabled (they conflict).
        Parkour parkour = ModuleManager.getModule(Parkour.class);
        if (parkour != null && parkour.isEnabled())
            parkour.setEnabled(false);
        sneaking = false;
    }

    @Override
    public void onDisable() {
        if (sneaking)
            setSneaking(false);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            if (sneaking)
                setSneaking(false);
            return;
        }

        // Only meaningful while actually standing on something.
        if (!mc.player.isOnGround() || mc.player.isSpectator()
                || mc.player.getAbilities().flying || mc.player.isTouchingWater()) {
            if (sneaking)
                setSneaking(false);
            return;
        }

        boolean nearEdge = isNearEdge();

        if (sneak.isEnabled()) {
            // Visible sneak at edges (Wurst's "Sneak at edges").
            setSneaking(nearEdge);
        } else if (sneaking) {
            // Invisible mode: leave the real key alone, the mixin clips instead.
            setSneaking(false);
        }
    }

    /**
     * Wurst's condition: a box shrunk by {@code edgeDistance} and pushed down by
     * the step height has no collision - i.e. part of your feet is hanging over
     * an edge.
     */
    private boolean isNearEdge() {
        try {
            Box box = mc.player.getBoundingBox();
            Box adjusted = box.expand(0, -mc.player.getStepHeight(), 0)
                    .contract(edgeDistance.getValue(), 0, edgeDistance.getValue());
            return mc.world.isSpaceEmpty(mc.player, adjusted);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Called from {@code PlayerEntityMixin} after vanilla has clipped the
     * player's movement. {@code clipping} is true when vanilla refused part of
     * the movement because of a ledge.
     */
    public void onClipAtLedge(boolean clipping) {
        if (mc.player == null || mc.world == null) {
            if (sneaking)
                setSneaking(false);
            return;
        }

        // Wurst: !isEnabled() || !sneak.isChecked() || !player.onGround()
        if (!isEnabled() || !sneak.isEnabled() || !mc.player.isOnGround()) {
            if (sneaking)
                setSneaking(false);
            return;
        }

        Box box = mc.player.getBoundingBox();
        Box adjustedBox = box.expand(0, -mc.player.getStepHeight(), 0)
                .contract(edgeDistance.getValue(), 0, edgeDistance.getValue());

        if (mc.world.isSpaceEmpty(mc.player, adjustedBox))
            clipping = true;

        setSneaking(clipping);
    }

    /**
     * Wurst's {@code setSneaking}: hold the real sneak key while sneaking, and
     * otherwise restore it to the user's actual physical key state
     * ({@code resetPressedState()}) rather than blindly releasing it.
     */
    private void setSneaking(boolean sneaking) {
        KeyBinding sneakKey = mc.options.sneakKey;

        if (sneaking)
            sneakKey.setPressed(true);
        else
            sneakKey.setPressed(isActuallyDown(sneakKey));

        this.sneaking = sneaking;
    }

    /** Wurst's {@code IKeyMapping.isActuallyDown()}. */
    private static boolean isActuallyDown(KeyBinding key) {
        String translationKey = key.getBoundKeyTranslationKey();
        int code = InputUtil.fromTranslationKey(translationKey).getCode();
        if (code <= 0)
            return false;

        if (translationKey.startsWith("key.mouse"))
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), code) == 1;

        return InputUtil.isKeyPressed(mc.getWindow(), code);
    }
}
