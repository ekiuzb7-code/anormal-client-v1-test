package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Drops detail for entities that are far away.
 *
 * <p>This is a level-of-detail pass: nearby entities look normal, distant ones
 * lose their nametag, shadow and animation updates - the parts that cost the
 * most while being the least visible.</p>
 */
public class EntityLodOptimizer extends Optimizer {

    public final BooleanSetting enabled = new BooleanSetting("Enable LOD", "Master switch", true);
    public final NumberSetting nametagDistance = new NumberSetting("Nametag Distance", "Hide nametags beyond this", 24.0, 4.0, 128.0, 2.0);
    public final NumberSetting shadowDistance = new NumberSetting("Shadow Distance", "Hide shadows beyond this", 32.0, 4.0, 128.0, 2.0);
    public final NumberSetting animationDistance = new NumberSetting("Animation Distance", "Stop animating beyond this", 48.0, 4.0, 160.0, 4.0);
    public final NumberSetting itemDistance = new NumberSetting("Item Distance", "Hide dropped items beyond this", 32.0, 4.0, 128.0, 2.0);
    public final NumberSetting armorStandDistance = new NumberSetting("Armor Stand Distance", "Hide armour stands beyond this", 16.0, 2.0, 64.0, 2.0);
    public final BooleanSetting hideItems = new BooleanSetting("Hide Distant Items", "Hide dropped items past their distance", false);
    public final BooleanSetting hideArmorStands = new BooleanSetting("Hide Distant Armor Stands", "Hide armour stands past their distance", false);
    public final BooleanSetting hideXpOrbs = new BooleanSetting("Hide Distant XP", "Hide experience orbs past the item distance", false);
    public final BooleanSetting keepPlayers = new BooleanSetting("Keep Players", "Always render players at full detail", true);
    public final BooleanSetting keepNamed = new BooleanSetting("Keep Named", "Always keep entities with a custom name", true);

    public EntityLodOptimizer() {
        super("EntityLodOptimizer", "Reduces entity detail with distance");
        addSetting(enabled);
        addSetting(nametagDistance);
        addSetting(shadowDistance);
        addSetting(animationDistance);
        addSetting(itemDistance);
        addSetting(armorStandDistance);
        addSetting(hideItems);
        addSetting(hideArmorStands);
        addSetting(hideXpOrbs);
        addSetting(keepPlayers);
        addSetting(keepNamed);
    }

    private static EntityLodOptimizer get() {
        EntityLodOptimizer m = com.anormal.client.module.ModuleManager.getModule(EntityLodOptimizer.class);
        return (m != null && m.isEnabled() && m.enabled.isEnabled()) ? m : null;
    }

    /** @return true when the entity should be skipped entirely. */
    public static boolean shouldHide(Entity e) {
        EntityLodOptimizer m = get();
        if (m == null || e == null || mc.player == null) return false;
        if (m.keepPlayers.isEnabled() && e instanceof PlayerEntity) return false;
        if (m.keepNamed.isEnabled()) {
            try {
                if (e.hasCustomName()) return false;
            } catch (Throwable ignored) {
            }
        }

        double d = Math.sqrt(mc.player.squaredDistanceTo(e));
        if (m.hideArmorStands.isEnabled() && e instanceof ArmorStandEntity) {
            return d > m.armorStandDistance.getValue();
        }
        if (m.hideItems.isEnabled() && e instanceof ItemEntity) {
            return d > m.itemDistance.getValue();
        }
        if (m.hideXpOrbs.isEnabled() && e instanceof net.minecraft.entity.ExperienceOrbEntity) {
            return d > m.itemDistance.getValue();
        }
        return false;
    }

    public static boolean hideNametag(Entity e) {
        EntityLodOptimizer m = get();
        if (m == null || e == null || mc.player == null) return false;
        if (m.keepPlayers.isEnabled() && e instanceof PlayerEntity) return false;
        return Math.sqrt(mc.player.squaredDistanceTo(e)) > m.nametagDistance.getValue();
    }

    public static boolean hideShadow(Entity e) {
        EntityLodOptimizer m = get();
        if (m == null || e == null || mc.player == null) return false;
        return Math.sqrt(mc.player.squaredDistanceTo(e)) > m.shadowDistance.getValue();
    }
}
