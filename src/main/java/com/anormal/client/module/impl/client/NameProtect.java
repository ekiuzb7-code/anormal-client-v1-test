package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public class NameProtect extends Module {
    private static final String[] ALIASES = {"Anormal", "Player", "Steve", "Alex", "Hero", "Ghost", "Notch", "Friend", "Enemy"};

    public final BooleanSetting protectSelf = new BooleanSetting("Protect Self", "Replace own username", true);
    public final ModeSetting selfAlias = new ModeSetting("Custom Name", "Replacement for own name", "Anormal", ALIASES);
    public final BooleanSetting protectPlayer = new BooleanSetting("Protect Player", "Replace marked player", false);
    public final BooleanSetting markTarget = new BooleanSetting("Mark Target", "Mark crosshair player as target", false);
    public final ModeSetting targetAlias = new ModeSetting("Replacement Name", "Replacement for target", "Friend", ALIASES);
    public final BooleanSetting protectInNametags = new BooleanSetting("Protect In Nametags", "Replace in ESP nametags", true);

    private String targetName = "";

    public NameProtect() {
        super("NameProtect", "Local visual name replacement (server sees real names)", Category.CLIENT);
        addSetting(protectSelf);
        addSetting(selfAlias);
        addSetting(protectPlayer);
        addSetting(markTarget);
        addSetting(targetAlias);
        addSetting(protectInNametags);
    }

    @Override
    public void onTick() {
        if (markTarget.isEnabled()) {
            markTarget.setValue(false);
            try {
                if (mc.targetedEntity instanceof PlayerEntity p && p != mc.player) {
                    targetName = p.getName().getString();
                }
            } catch (Throwable ignored) {}
        }
    }

    public static String replaceName(String original) {
        try {
            NameProtect self = ModuleManager.getModule(NameProtect.class);
            if (self == null || !self.isEnabled() || !self.protectInNametags.isEnabled() || original == null) {
                return original;
            }
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (self.protectSelf.isEnabled() && mc.player != null) {
                try {
                    String own = mc.player.getName().getString();
                    if (original.equals(own)) return self.selfAlias.getValue();
                } catch (Throwable ignored) {}
            }
            if (self.protectPlayer.isEnabled() && !self.targetName.isEmpty() && original.equals(self.targetName)) {
                return self.targetAlias.getValue();
            }
        } catch (Throwable ignored) {}
        return original;
    }

    public static String replaceName(LivingEntity entity) {
        try {
            return replaceName(entity.getName().getString());
        } catch (Throwable ignored) {
            return "";
        }
    }
}
