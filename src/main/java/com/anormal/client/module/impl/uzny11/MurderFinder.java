package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

public class MurderFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Scan radius in blocks", 30.0, 5.0, 100.0, 1.0);
    public final BooleanSetting announce = new BooleanSetting("Announce", "Announce murderer in chat once", true);
    private final Set<String> announced = new HashSet<>();

    public MurderFinder() {
        super("MurderFinder", "Alerts when a player holds a weapon-like item", Category.UZNY11);
        addSetting(range);
        addSetting(announce);
    }

    @Override
    public void onDisable() {
        announced.clear();
    }

    private static boolean isWeaponId(String path) {
        return path.contains("sword") || path.contains("axe") || path.contains("knife") || path.contains("mace") || path.contains("trident");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == null || p == mc.player || !p.isAlive()) continue;
                if (mc.player.distanceTo(p) > range.getValue()) continue;
                boolean armed = false;
                try {
                    Identifier main = Registries.ITEM.getId(p.getMainHandStack().getItem());
                    Identifier off = Registries.ITEM.getId(p.getOffHandStack().getItem());
                    if ((main != null && isWeaponId(main.getPath())) || (off != null && isWeaponId(off.getPath()))) armed = true;
                } catch (Throwable ignored) {}
                if (armed && announce.isEnabled() && mc.inGameHud != null) {
                    String name = p.getName().getString();
                    if (announced.add(name)) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal("[MurderFinder] " + name + " is armed!"));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
