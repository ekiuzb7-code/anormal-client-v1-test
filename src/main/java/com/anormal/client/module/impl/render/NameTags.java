package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

public class NameTags extends Module {
    public final BooleanSetting ignoreInvisibles = new BooleanSetting("Ignore Invisibles", "Hides nametags on invisible entities", true);
    public final BooleanSetting autoScale = new BooleanSetting("Auto Scale", "Adjusts nametag size based on distance", true);
    public final NumberSetting scale = new NumberSetting("Scale", "Maximum or constant nametag size", 1.0, 0.5, 2.0, 0.1);
    public final BooleanSetting hideBots = new BooleanSetting("Hide Bots", "Hides nametags on anti-cheat bots", true);
    public final BooleanSetting renderPlayers = new BooleanSetting("Render Players", "Enables nametags for players", true);
    public final BooleanSetting pHealth = new BooleanSetting("Player Health", "Displays player health", true);
    public final BooleanSetting pDistance = new BooleanSetting("Player Distance", "Shows distance to the player", true);
    public final BooleanSetting pEffects = new BooleanSetting("Player Effects", "Displays active potion effects", true);
    public final NumberSetting pMaxDistance = new NumberSetting("Player Max Distance", "Limits player nametag range", 64.0, 4.0, 256.0, 4.0);
    public final BooleanSetting pEquipment = new BooleanSetting("Equipment", "Shows equipped armor and held item", true);
    public final BooleanSetting pStrength = new BooleanSetting("Strength Indicator", "Shows relative strength symbol", true);
    public final BooleanSetting calcEffects = new BooleanSetting("Calculate Effects", "Includes potion effects in strength calc", true);
    public final BooleanSetting renderAnimals = new BooleanSetting("Render Animals", "Enables nametags for animals", false);
    public final BooleanSetting aHealth = new BooleanSetting("Animal Health", "Displays animal health", true);
    public final BooleanSetting aDistance = new BooleanSetting("Animal Distance", "Shows distance to the animal", false);
    public final BooleanSetting aEffects = new BooleanSetting("Animal Effects", "Displays potion effects on animals", false);
    public final NumberSetting aMaxDistance = new NumberSetting("Animal Max Distance", "Limits animal nametag range", 32.0, 4.0, 128.0, 4.0);
    public final BooleanSetting renderMobs = new BooleanSetting("Render Mobs", "Enables nametags for aggressive mobs", false);
    public final BooleanSetting mHealth = new BooleanSetting("Mob Health", "Displays mob health", true);
    public final BooleanSetting mDistance = new BooleanSetting("Mob Distance", "Shows distance to the mob", false);
    public final BooleanSetting mEffects = new BooleanSetting("Mob Effects", "Displays potion effects on mobs", false);
    public final NumberSetting mMaxDistance = new NumberSetting("Mob Max Distance", "Limits mob nametag range", 32.0, 4.0, 128.0, 4.0);

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public NameTags() {
        super("NameTags", "Renders enhanced player nametags, health, and distance through walls", Category.RENDER);
        addSetting(ignoreInvisibles);
        addSetting(autoScale);
        addSetting(scale);
        addSetting(hideBots);
        addSetting(renderPlayers);
        addSetting(pHealth);
        addSetting(pDistance);
        addSetting(pEffects);
        addSetting(pMaxDistance);
        addSetting(pEquipment);
        addSetting(pStrength);
        addSetting(calcEffects);
        addSetting(renderAnimals);
        addSetting(aHealth);
        addSetting(aDistance);
        addSetting(aEffects);
        addSetting(aMaxDistance);
        addSetting(renderMobs);
        addSetting(mHealth);
        addSetting(mDistance);
        addSetting(mEffects);
        addSetting(mMaxDistance);

    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) continue;
                boolean player = e instanceof PlayerEntity;
                boolean animal = e instanceof AnimalEntity;
                boolean mob = e instanceof Monster;
                if (player && !renderPlayers.isEnabled()) continue;
                if (animal && !renderAnimals.isEnabled()) continue;
                if (mob && !renderMobs.isEnabled()) continue;
                if (!player && !animal && !mob) continue;
                if (ignoreInvisibles.isEnabled() && living.isInvisible()) continue;
                if (player && hideBots.isEnabled() && isBot((PlayerEntity) e)) continue;
                double dist = mc.player.distanceTo(e);
                double max = player ? pMaxDistance.getValue() : animal ? aMaxDistance.getValue() : mMaxDistance.getValue();
                if (dist > max) continue;
                boolean showHp = player ? pHealth.isEnabled() : animal ? aHealth.isEnabled() : mHealth.isEnabled();
                boolean showDist = player ? pDistance.isEnabled() : animal ? aDistance.isEnabled() : mDistance.isEnabled();
                boolean showFx = player ? pEffects.isEnabled() : animal ? aEffects.isEnabled() : mEffects.isEnabled();
                String name;
                try {
                    name = com.anormal.client.module.impl.client.NameProtect.replaceName(e.getName().getString());
                } catch (Throwable t) {
                    continue;
                }
                String line1 = name;
                if (showHp) line1 += " " + (int) Math.ceil(living.getHealth()) + "hp";
                if (showDist) line1 += " [" + (int) dist + "m]";
                if (showFx) {
                    String fx = effects(living);
                    if (!fx.isEmpty()) line1 += " " + fx;
                }
                String line2 = "";
                if (player) {
                    if (pEquipment.isEnabled()) line2 = equipment((PlayerEntity) e);
                    if (pStrength.isEnabled()) line2 += strengthSymbol((PlayerEntity) e);
                }
                int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(e.getX(), e.getY() + e.getHeight() + 0.4, e.getZ()), tickDelta);
                if (s == null) continue;
                float sc = autoScale.isEnabled()
                        ? (float) Math.max(0.5, Math.min(scale.getValue(), 14.0 / Math.max(1.0, dist)))
                        : scale.getValue().floatValue();
                drawTag(context, s[0], s[1], line1, line2.trim(), sc);
            }
        } catch (Throwable ignored) {}
    }

    private String effects(LivingEntity e) {
        StringBuilder sb = new StringBuilder("[");
        try {
            if (e.hasStatusEffect(StatusEffects.SPEED)) sb.append("Spd ");
            if (e.hasStatusEffect(StatusEffects.STRENGTH)) sb.append("Str ");
            if (e.hasStatusEffect(StatusEffects.REGENERATION)) sb.append("Reg ");
            if (e.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) sb.append("FR ");
            if (e.hasStatusEffect(StatusEffects.INVISIBILITY)) sb.append("Inv ");
            if (e.hasStatusEffect(StatusEffects.JUMP_BOOST)) sb.append("Jmp ");
            if (e.hasStatusEffect(StatusEffects.SLOWNESS)) sb.append("Slo ");
            if (e.hasStatusEffect(StatusEffects.WEAKNESS)) sb.append("Wea ");
            if (e.hasStatusEffect(StatusEffects.POISON)) sb.append("Psi ");
            if (e.hasStatusEffect(StatusEffects.WITHER)) sb.append("Wth ");
        } catch (Throwable ignored) {}
        if (sb.length() <= 1) return "";
        sb.setLength(sb.length() - 1);
        return sb.append("]").toString();
    }

    private String equipment(PlayerEntity p) {
        try {
            int armor = 0;
            for (EquipmentSlot slot : ARMOR_SLOTS) if (!p.getEquippedStack(slot).isEmpty()) armor++;
            String held = p.getMainHandStack().isEmpty() ? "" : Registries.ITEM.getId(p.getMainHandStack().getItem()).getPath() + " ";
            return held + armor + "/4";
        } catch (Throwable t) {
            return "";
        }
    }

    private String strengthSymbol(PlayerEntity p) {
        try {
            double mine = gearScore(mc.player, calcEffects.isEnabled());
            double theirs = gearScore(p, calcEffects.isEnabled());
            if (mine > theirs + 1) return " \u25B2";
            if (theirs > mine + 1) return " \u25BC";
            return " =";
        } catch (Throwable t) {
            return "";
        }
    }

    private double gearScore(PlayerEntity p, boolean fx) {
        String held;
        try {
            held = Registries.ITEM.getId(p.getMainHandStack().getItem()).getPath();
        } catch (Throwable t) {
            held = "";
        }
        double s = 0;
        if (held.endsWith("_sword")) s += 4;
        else if (held.equals("mace")) s += 5;
        else if (held.endsWith("_axe") || held.equals("trident")) s += 3;
        try {
            for (EquipmentSlot slot : ARMOR_SLOTS) if (!p.getEquippedStack(slot).isEmpty()) s += 1;
            if (fx) {
                if (p.hasStatusEffect(StatusEffects.STRENGTH)) s += 2;
                if (p.hasStatusEffect(StatusEffects.SPEED)) s += 1;
                if (p.hasStatusEffect(StatusEffects.REGENERATION)) s += 1;
                if (p.hasStatusEffect(StatusEffects.WEAKNESS)) s -= 2;
            }
        } catch (Throwable ignored) {}
        return s;
    }

    private boolean isBot(PlayerEntity p) {
        try {
            if (mc.getNetworkHandler() == null) return false;
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList())
                if (e.getProfile().id().equals(p.getUuid())) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void drawTag(DrawContext context, int x, int y, String l1, String l2, float s) {
        int w1 = mc.textRenderer.getWidth(l1);
        int w2 = l2.isEmpty() ? 0 : mc.textRenderer.getWidth(l2);
        int w = (int) (Math.max(w1, w2) * s);
        int tall = (int) ((l2.isEmpty() ? 14 : 24) * s);
        context.fill(x - w / 2 - 3, y - tall, x + w / 2 + 3, y + 2, 0xAA000000);
        RenderUtils.drawText(context, mc.textRenderer, l1, x - w1 / 2, y - tall + 3, 0xFFFFFFFF, true);
        if (!l2.isEmpty()) RenderUtils.drawText(context, mc.textRenderer, l2, x - w2 / 2, y - tall + 13, 0xFFFFCC55, true);
    }


}
