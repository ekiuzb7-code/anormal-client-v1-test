package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;

public class AntiBot extends Module {
    public final BooleanSetting notInTabList = new BooleanSetting("Not In Tab", "Bot if missing from tab list", true);
    public final BooleanSetting duplicates = new BooleanSetting("Duplicates", "Bot if name appears twice", true);
    public final BooleanSetting literalNpc = new BooleanSetting("Literal NPC", "Bot if never seen on network", false);

    public AntiBot() {
        super("AntiBot", "Detects anticheat bots so other modules ignore them", Category.CLIENT);
        addSetting(notInTabList);
        addSetting(duplicates);
        addSetting(literalNpc);
    }

    public static boolean isBot(Entity entity) {
        try {
            com.anormal.client.module.ModuleManager.getModules();
            AntiBot self = com.anormal.client.module.ModuleManager.getModule(AntiBot.class);
            if (self == null || !self.isEnabled()) return false;
            if (!(entity instanceof PlayerEntity p) || entity == net.minecraft.client.MinecraftClient.getInstance().player) return false;
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return false;

            if (self.notInTabList.isEnabled()) {
                boolean inTab = false;
                try {
                    for (var e : mc.getNetworkHandler().getPlayerList()) {
                        try {
                            if (e.getProfile().id().equals(p.getUuid())) {
                                inTab = true;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
                if (!inTab) return true;
            }

            if (self.duplicates.isEnabled()) {
                String name = p.getName().getString();
                int count = 0;
                try {
                    for (Entity e : mc.world.getEntities()) {
                        if (e instanceof PlayerEntity other && other.getName().getString().equals(name)) {
                            count++;
                            if (count > 1) return true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
