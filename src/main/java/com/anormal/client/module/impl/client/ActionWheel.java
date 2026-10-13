package com.anormal.client.module.impl.client;

import com.anormal.client.actionwheel.ActionWheelConfig;
import com.anormal.client.gui.ActionWheelScreen;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import org.lwjgl.glfw.GLFW;

/**
 * Universal Action Wheel (Radial Menu).
 * Hold key to display the circular wheel, hover over any action,
 * and release key to activate immediately. Fully customizable in-game.
 */
public class ActionWheel extends Module {

    public final BooleanSetting holdMode = new BooleanSetting("Hold to Open", "Hold key to show wheel, release to activate", true);
    public final NumberSetting radius = new NumberSetting("Wheel Radius", "Outer size of the radial wheel", 110.0, 70.0, 160.0, 5.0);
    public final NumberSetting deadzone = new NumberSetting("Center Deadzone", "Inner deadzone circle size", 40.0, 20.0, 60.0, 5.0);
    public final ColorSetting accentColor = new ColorSetting("Accent Color", "Hovered slice highlight color", ColorUtils.rgba(196, 71, 255, 255));
    public final BooleanSetting sound = new BooleanSetting("Sound Effects", "Play audio feedback on slice hover and select", true);

    public ActionWheel() {
        super("ActionWheel", "Universal customizable radial action wheel (Donut / SMP / Commands)", Category.CLIENT, GLFW.GLFW_KEY_G);
        addSetting(holdMode);
        addSetting(radius);
        addSetting(deadzone);
        addSetting(accentColor);
        addSetting(sound);
        ActionWheelConfig.init();
    }

    @Override
    public void onEnable() {
        ActionWheelConfig.init();
        if (mc.currentScreen == null) {
            mc.setScreen(new ActionWheelScreen(
                    this,
                    getKey(),
                    holdMode.isEnabled(),
                    radius.getValue(),
                    deadzone.getValue(),
                    accentColor.getValue(),
                    sound.isEnabled()
            ));
        }
        setEnabled(false); // ready for next hold/press
    }
}
