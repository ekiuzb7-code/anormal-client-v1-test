package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders glowing highlights and name tags on dropped items.
 *
 * <p><b>Jitter fix:</b> the tag was projected from the item's raw, tick-quantised
 * position, so it visibly vibrated whenever the item moved. Positions now come
 * from {@link Entity#getLerpedPos(float)}, which is interpolated per render
 * frame. The background box is also drawn with the same scale as the text
 * instead of being sized independently.</p>
 */
public class ItemESP extends Module {
    public final BooleanSetting distance = new BooleanSetting("Distance", "Shows distance next to the item name", true);
    public final BooleanSetting groupItems = new BooleanSetting("Group Items", "Groups nearby drops into a single tag", true);
    public final BooleanSetting autoScale = new BooleanSetting("Auto Scale", "Keeps tag size consistent with distance", true);
    public final NumberSetting scale = new NumberSetting("Scale", "Maximum or constant tag scale", 1.0, 0.5, 2.0, 0.1);
    public final BooleanSetting whitelistOnly = new BooleanSetting("Whitelist Only", "Only renders tags on whitelisted items", false);
    public final ModeSetting whitelist = new ModeSetting("Whitelist", "Which items count as valuable", "Diamond+", "Diamond+", "Netherite", "Ores", "Rare");
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Only renders items within this range", 48.0, 8.0, 128.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Item tag color", ColorUtils.rgba(255, 255, 255, 255));

    private static final double GROUP_RADIUS_SQ = 6.25; // 2.5 blocks

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

            List<ItemEntity> anchors = new ArrayList<>();
            List<String> tags = new ArrayList<>();
            List<Double> dists = new ArrayList<>();

            for (ItemEntity item : items) {
                if (groupItems.isEnabled()) {
                    boolean merged = false;
                    for (ItemEntity a : anchors) {
                        if (a.getStack().getItem() == item.getStack().getItem()
                                && a.squaredDistanceTo(item) < GROUP_RADIUS_SQ) {
                            merged = true;
                            break;
                        }
                    }
                    if (merged) continue;
                }

                int own = item.getStack().getCount();
                int count = own;
                if (groupItems.isEnabled()) {
                    for (ItemEntity other : items) {
                        if (other != item && other.getStack().getItem() == item.getStack().getItem()
                                && other.squaredDistanceTo(item) < GROUP_RADIUS_SQ) {
                            count += other.getStack().getCount();
                        }
                    }
                }

                String name = item.getStack().getName().getString();
                double d = mc.player.distanceTo(item);
                String tag = (groupItems.isEnabled() && count > own ? count + "x " : "x" + own + " ") + name;
                if (distance.isEnabled()) tag += " [" + (int) d + "m]";

                anchors.add(item);
                tags.add(tag);
                dists.add(d);
            }

            for (int i = 0; i < anchors.size(); i++) {
                ItemEntity item = anchors.get(i);
                Vec3d pos = ProjectionUtil.lerpPos(item, tickDelta).add(0.0, 0.6, 0.0);
                int[] s = ProjectionUtil.project(pos, tickDelta);
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

    /** Draws the tag; text and background share the exact same transform. */
    private void drawTag(DrawContext context, int x, int y, String text, float s) {
        int tw = mc.textRenderer.getWidth(text);
        try {
            var m = context.getMatrices();
            m.pushMatrix();
            m.translate((float) x, (float) y);
            m.scale(s, s);
            context.fill(-tw / 2 - 3, -12, tw / 2 + 3, 2, 0xAA000000);
            RenderUtils.drawText(context, mc.textRenderer, text, -tw / 2, -9, color.getValue(), true);
            m.popMatrix();
        } catch (Throwable t) {
            // Fallback: no scaling, but still correctly placed.
            context.fill(x - tw / 2 - 3, y - 12, x + tw / 2 + 3, y + 2, 0xAA000000);
            RenderUtils.drawText(context, mc.textRenderer, text, x - tw / 2, y - 9, color.getValue(), true);
        }
    }
}
