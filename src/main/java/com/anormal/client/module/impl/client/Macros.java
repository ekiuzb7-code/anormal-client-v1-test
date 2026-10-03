package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class Macros extends Module {
    private static final String[] ITEMS = {
            "None", "Ender Pearl", "Wind Charge", "Water Bucket", "Lava Bucket",
            "Golden Apple", "Totem", "Fishing Rod", "Snowball", "Egg"
    };

    public final ModeSetting macro1 = new ModeSetting("Macro 1", "Item to use", "None", ITEMS);
    public final KeybindSetting key1 = new KeybindSetting("Key 1", "Macro 1 key", GLFW.GLFW_KEY_UNKNOWN);
    public final ModeSetting macro2 = new ModeSetting("Macro 2", "Item to use", "None", ITEMS);
    public final KeybindSetting key2 = new KeybindSetting("Key 2", "Macro 2 key", GLFW.GLFW_KEY_UNKNOWN);
    public final ModeSetting macro3 = new ModeSetting("Macro 3", "Item to use", "None", ITEMS);
    public final KeybindSetting key3 = new KeybindSetting("Key 3", "Macro 3 key", GLFW.GLFW_KEY_UNKNOWN);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks before restoring slot", 6.0, 1.0, 40.0, 1.0);
    public final BooleanSetting doubleClick = new BooleanSetting("Double Click", "Second use (rod recast, bucket pickup)", false);
    public final NumberSetting doubleDelay = new NumberSetting("Double Delay", "Ticks before second use", 8.0, 1.0, 40.0, 1.0);

    private int state = 0; // 0 idle, 1 used-waiting restore, 2 waiting double
    private int timer = 0;
    private int savedSlot = -1;
    private ModeSetting activeMacro = null;
    private final boolean[] keyDown = new boolean[3];

    public Macros() {
        super("Macros", "One key: swap to item, use it, swap back", Category.CLIENT);
        addSetting(macro1);
        addSetting(key1);
        addSetting(macro2);
        addSetting(key2);
        addSetting(macro3);
        addSetting(key3);
        addSetting(delay);
        addSetting(doubleClick);
        addSetting(doubleDelay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null || mc.getWindow() == null) return;

        if (state == 1) {
            if (--timer <= 0) {
                if (doubleClick.isEnabled() && activeMacro != null && isRod(activeMacro)) {
                    // Fishing rod smart recast: second use then restore
                    useActive();
                    state = 2;
                    timer = doubleDelay.getValue().intValue();
                } else {
                    restore();
                }
            }
            return;
        }
        if (state == 2) {
            if (--timer <= 0) restore();
            return;
        }

        ModeSetting[] macros = {macro1, macro2, macro3};
        KeybindSetting[] keys = {key1, key2, key3};
        for (int i = 0; i < 3; i++) {
            if (macros[i].is("None")) {
                keyDown[i] = false;
                continue;
            }
            boolean down = isDown(keys[i].getValue());
            if (down && !keyDown[i]) fire(macros[i]);
            keyDown[i] = down;
        }
    }

    private void fire(ModeSetting macro) {
        int hotbar = findItem(macro.getValue());
        if (hotbar == -1) return;
        try {
            savedSlot = mc.player.getInventory().getSelectedSlot();
            activeMacro = macro;
            mc.player.getInventory().setSelectedSlot(hotbar);
            useActive();
            state = 1;
            timer = delay.getValue().intValue();
        } catch (Throwable ignored) {
            restore();
        }
    }

    private void useActive() {
        try {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }

    private void restore() {
        try {
            if (savedSlot >= 0) mc.player.getInventory().setSelectedSlot(savedSlot);
        } catch (Throwable ignored) {}
        state = 0;
        savedSlot = -1;
        activeMacro = null;
    }

    private int findItem(String name) {
        String id = switch (name) {
            case "Ender Pearl" -> "ender_pearl";
            case "Wind Charge" -> "wind_charge";
            case "Water Bucket" -> "water_bucket";
            case "Lava Bucket" -> "lava_bucket";
            case "Golden Apple" -> "golden_apple";
            case "Totem" -> "totem_of_undying";
            case "Fishing Rod" -> "fishing_rod";
            case "Snowball" -> "snowball";
            case "Egg" -> "egg";
            default -> "";
        };
        if (id.isEmpty()) return -1;
        try {
            for (int i = 0; i < 9; i++) {
                String path = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem()).getPath();
                if (path.equals(id)) return i;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private boolean isRod(ModeSetting macro) {
        return macro != null && macro.is("Fishing Rod");
    }

    private boolean isDown(int code) {
        try {
            long window = mc.getWindow().getHandle();
            if (KeybindSetting.isMouseCode(code)) {
                int btn = KeybindSetting.mouseButton(code);
                return btn >= 0 && GLFW.glfwGetMouseButton(window, btn) == GLFW.GLFW_PRESS;
            }
            if (code > 0 && code <= GLFW.GLFW_KEY_LAST) {
                return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
