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
 * Ported 1:1 from Wurst's SafeWalkHack.
 *
 * <p>Settings and behaviour match Wurst exactly:</p>
 * <ul>
 *   <li>"Sneak at edges" (off by default) - visibly sneak at edges.</li>
 *   <li>"Sneak edge distance" (0.05m, 0.05..0.25, step 0.001) - how close you
 *       may get to the edge before sneaking; only used when "Sneak at edges"
 *       is enabled.</li>
 * </ul>
 *
 * <p>There is intentionally <b>no</b> tick logic here. Wurst's SafeWalk is
 * driven entirely by two vanilla hooks, injected in
 * {@code PlayerEntityMixin}: {@code clipAtLedge()} makes vanilla stop you at
 * ledges, and {@code adjustMovementForSneaking()} tells us when you were
 * actually clipped so we can sneak visibly.</p>
 */
public class SafeWalk extends Module {

    /** Wurst: CheckboxSetting("Sneak at edges", "Visibly sneak at edges.", false) */
    public final BooleanSetting sneak = new BooleanSetting(
            "Sneak at edges",
            "Visibly sneak at edges.",
            false);

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

        // Wurst:
        //   box.expandTowards(0, -player.maxUpStep(), 0)
        //      .inflate(-edgeDistance, 0, -edgeDistance)
        // Yarn: Box.expand(...) / Box.contract(...) / Entity.getStepHeight()
        Box box = mc.player.getBoundingBox();
        Box adjustedBox = box.expand(0, -mc.player.getStepHeight(), 0)
                .contract(edgeDistance.getValue(), 0, edgeDistance.getValue());

        // Wurst: MC.level.noCollision(player, adjustedBox) -> Yarn: World.isSpaceEmpty
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
