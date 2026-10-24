package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared filter that every targeting module asks before it accepts an entity.
 *
 * <p>The module used to be settings only - nothing ever read them, so switching it on changed
 * nothing at all. It now exposes {@link #isValid(Entity)} and the combat modules call it.</p>
 */
public class TargetFilter extends Module {

    public final BooleanSetting teamsByServer = new BooleanSetting("Teams by Server",
            "Ignore players on your scoreboard team", true);
    public final BooleanSetting teamsByColor = new BooleanSetting("Teams by Color",
            "Ignore players sharing your nametag colour", true);
    public final BooleanSetting antiBot = new BooleanSetting("AntiBot",
            "Filters out fake server anticheat bots", true);
    public final BooleanSetting ignoreInvisibles = new BooleanSetting("Invisibles",
            "Keep targeting invisible players", true);
    public final BooleanSetting ignoreSleeping = new BooleanSetting("Ignore Sleeping",
            "Ignore players sleeping in a bed", true);

    public TargetFilter() {
        super("TargetFilter", "Filters which players can be targeted by combat modules", Category.WORLD);
        addSetting(teamsByServer);
        addSetting(teamsByColor);
        addSetting(antiBot);
        addSetting(ignoreInvisibles);
        addSetting(ignoreSleeping);
    }

    /** @return true when the entity may be targeted. Always true when the module is off. */
    public static boolean isValid(Entity entity) {
        try {
            TargetFilter filter = ModuleManager.getModule(TargetFilter.class);
            if (filter == null || !filter.isEnabled()) return true;
            return filter.accept(entity);
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean accept(Entity entity) {
        if (mc.player == null || entity == null) return true;
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;

        if (!(entity instanceof PlayerEntity player)) {
            // Mobs and animals are not what this filter is about.
            return true;
        }

        if (!ignoreInvisibles.isEnabled() && player.isInvisible()) return false;

        if (ignoreSleeping.isEnabled() && player.isSleeping()) return false;

        if (teamsByServer.isEnabled()) {
            try {
                if (mc.player.isTeammate(player)) return false;
            } catch (Throwable ignored) {
            }
        }

        if (teamsByColor.isEnabled()) {
            try {
                int mine = mc.player.getTeamColorValue();
                int theirs = player.getTeamColorValue();
                // 0xFFFFFF means "no team colour"; only compare real colours.
                if (mine != 0xFFFFFF && mine == theirs) return false;
            } catch (Throwable ignored) {
            }
        }

        if (antiBot.isEnabled()) {
            try {
                // Anti-cheat bots are spawned with a non-random (version 2) UUID, which is the
                // heuristic every mc uses for this.
                if (player.getUuid().version() == 2) return false;
            } catch (Throwable ignored) {
            }
        }

        return true;
    }
}
