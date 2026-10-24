package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Changes the game speed by scaling how many mc ticks run per frame.
 *
 * <p>Minecraft asks {@code RenderTickCounter.Dynamic} how many ticks to run for the frame that was
 * just rendered. Scaling that number (with a fractional carry so 1.5x really is 1.5x and not 2x)
 * speeds the whole simulation up or slows it down, exactly like the classic "timer" of older
 * clients. Rendering itself is untouched, so the FPS is unaffected.</p>
 */
public class Timer extends Module {

    public final NumberSetting speed = new NumberSetting("Speed",
            "Game speed multiplier (1.0 = vanilla)", 1.0, 0.1, 5.0, 0.05);
    public final BooleanSetting onlyWhileMoving = new BooleanSetting("Only While Moving",
            "Only speed up while the player is actually moving", false);
    public final BooleanSetting disableInScreens = new BooleanSetting("Disable In Screens",
            "Return to vanilla speed while a GUI is open", true);
    public final BooleanSetting showHud = new BooleanSetting("Show HUD",
            "Show the current speed on screen", false);

    /** Fractional ticks carried between frames so the multiplier is exact. */
    private static float carry = 0f;

    public Timer() {
        super("Timer", "Changes the game speed", Category.CLIENT);
        addSetting(speed);
        addSetting(onlyWhileMoving);
        addSetting(disableInScreens);
        addSetting(showHud);
    }

    @Override
    public void onDisable() {
        carry = 0f;
    }

    /** @return the multiplier to apply, or 1.0 when the timer should not act. */
    public static float activeSpeed() {
        try {
            Timer module = ModuleManager.getModule(Timer.class);
            if (module == null || !module.isEnabled()) return 1.0f;
            if (module.disableInScreens.isEnabled() && net.minecraft.client.MinecraftClient.getInstance().currentScreen != null) {
                return 1.0f;
            }
            if (module.onlyWhileMoving.isEnabled()) {
                var player = net.minecraft.client.MinecraftClient.getInstance().player;
                if (player == null) return 1.0f;
                if (Math.abs(player.getVelocity().x) + Math.abs(player.getVelocity().z) < 0.005) return 1.0f;
            }
            return module.speed.getValue().floatValue();
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    /** Scales a tick count with a fractional carry, so 1.5x is not rounded to 2x. */
    public static int scaleTicks(int vanillaTicks, float multiplier) {
        if (multiplier == 1.0f) {
            carry = 0f;
            return vanillaTicks;
        }
        float exact = vanillaTicks * multiplier + carry;
        int whole = (int) Math.floor(exact);
        carry = exact - whole;
        if (carry < 0f) carry = 0f;
        return Math.max(0, Math.min(20, whole));
    }
}
