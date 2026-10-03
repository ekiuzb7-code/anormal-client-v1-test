package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ItemESP extends Module {
    public final BooleanSetting distance = new BooleanSetting("Distance", "Shows distance next to the item name", true);
    public final BooleanSetting groupItems = new BooleanSetting("Group Items", "Groups nearby drops into a single tag", true);
    public final BooleanSetting autoScale = new BooleanSetting("Auto Scale", "Keeps tag size consistent with distance", true);
    public final NumberSetting scale = new NumberSetting("Scale", "Maximum or constant tag scale", 1.0, 0.5, 2.0, 0.1);
    public final BooleanSetting whitelistOnly = new BooleanSetting("Whitelist Only", "Only renders tags on whitelisted items", false);
    public final ModeSetting whitelist = new ModeSetting("Whitelist", "Which items count as valuable", "Diamond+", "Diamond+", "Netherite", "Ores", "Rare");
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Only renders items within this range", 48.0, 8.0, 128.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Item tag color", ColorUtils.rgba(255, 255, 255, 255));

    public ItemESP() {
        super("ItemESP", "Renders glowing highlights and tags on dropped items", Category.RENDER);
        addSetting(distance);
        addSetting(groupItems);
        addSetting(autoScale);
        addSetting(scale);
        addSetting(whitelistOnly);
        addSetting(whitelist);
        addSetting(maxDistance);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;
        try {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ItemEntity item) item.setGlowing(wanted(item));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        try {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ItemEntity item) item.setGlowing(false);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            List<ItemEntity> items = new ArrayList<>();
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ItemEntity item)) continue;
                if (!wanted(item)) continue;
                if (mc.player.distanceTo(e) > maxDistance.getValue()) continue;
                items.add(item);
            }
            List<String> tags = new ArrayList<>();
            List<Vec3d> at = new ArrayList<>();
            List<Double> dists = new ArrayList<>();
            for (ItemEntity item : items) {
                if (groupItems.isEnabled()) {
                    boolean merged = false;
                    for (int i = 0; i < at.size(); i++) {
                        if (at.get(i).distanceTo(new Vec3d(item.getX(), item.getY(), item.getZ())) < 2.5) {
                            merged = true;
                            break;
                        }
                    }
                    if (merged) continue;
                }
                int count = item.getStack().getCount();
                if (groupItems.isEnabled()) {
                    for (ItemEntity other : items) {
                        if (other != item && new Vec3d(other.getX(), other.getY(), other.getZ()).distanceTo(new Vec3d(item.getX(), item.getY(), item.getZ())) < 2.5
                                && other.getStack().getItem() == item.getStack().getItem()) count += other.getStack().getCount();
                    }
                }
                String name = item.getStack().getName().getString();
                double d = mc.player.distanceTo(item);
                String tag = (groupItems.isEnabled() && count > item.getStack().getCount() ? count + "x " : "x" + count + " ") + name;
                if (distance.isEnabled()) tag += " [" + (int) d + "m]";
                tags.add(tag);
                at.add(new Vec3d(item.getX(), item.getY(), item.getZ()));
                dists.add(d);
            }
            for (int i = 0; i < tags.size(); i++) {
                int[] s = com.anormal.client.util.ProjectionUtil.project(at.get(i).add(0, 0.6, 0), tickDelta);
                if (s == null) continue;
                float sc = autoScale.isEnabled()
                        ? (float) Math.max(0.5, Math.min(scale.getValue(), 12.0 / Math.max(1.0, dists.get(i))))
                        : scale.getValue().floatValue();
                drawTag(context, s[0], s[1], tags.get(i), sc);
            }
        } catch (Throwable ignored) {}
    }

    private boolean wanted(ItemEntity item) {
        if (!whitelistOnly.isEnabled()) return true;
        String path;
        try {
            path = Registries.ITEM.getId(item.getStack().getItem()).getPath();
        } catch (Throwable t) {
            return false;
        }
        if (whitelist.is("Netherite")) return path.contains("netherite") || path.contains("ancient_debris");
        if (whitelist.is("Ores")) return path.contains("_ore") || path.startsWith("raw_");
        if (whitelist.is("Rare"))
            return path.contains("netherite") || path.contains("diamond") || path.contains("emerald")
                    || path.contains("totem") || path.contains("elytra") || path.contains("trident") || path.contains("mace");
        return path.contains("diamond") || path.contains("emerald") || path.contains("netherite") || path.contains("ancient_debris");
    }

    private void drawTag(DrawContext context, int x, int y, String text, float s) {
        int w = (int) (mc.textRenderer.getWidth(text) * s);
        context.fill(x - w / 2 - 3, y - 12, x + w / 2 + 3, y + 2, 0xAA000000);
        RenderUtils.drawText(context, mc.textRenderer, text, x - mc.textRenderer.getWidth(text) / 2, y - 9, color.getValue(), true);
    }


}
