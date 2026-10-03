package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ESP extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "ESP visual mode", "2D Box", "Glow", "2D Box", "Corner", "Outline", "V2");
    public final BooleanSetting players = new BooleanSetting("Players", "Highlight other players", true);
    public final BooleanSetting mobs = new BooleanSetting("Monsters", "Highlight hostile mobs", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Highlight passive animals", false);
    public final BooleanSetting items = new BooleanSetting("Items", "Highlight dropped items", false);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", "Show health indicators", true);
    public final ColorSetting color = new ColorSetting("Color", "ESP highlight color", ColorUtils.rgba(255, 60, 60, 255));
    public final ColorSetting mobColor = new ColorSetting("Mob Color", "Hostile mob color", ColorUtils.rgba(255, 170, 0, 255));
    public final ColorSetting animalColor = new ColorSetting("Animal Color", "Passive animal color", ColorUtils.rgba(85, 255, 85, 255));
    public final ColorSetting itemColor = new ColorSetting("Item Color", "Dropped item color", ColorUtils.rgba(255, 255, 85, 255));
    public final BooleanSetting outline = new BooleanSetting("Outline", "Bright outer edge on boxes", true);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Translucent box fill (V2)", true);
    public final BooleanSetting showName = new BooleanSetting("Mob Names", "Show name above mobs", true);

    public ESP() {
        super("ESP", "Highlights players, mobs, and items through walls with customizable styles", Category.RENDER);
        addSetting(mode);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(items);
        addSetting(healthBar);
        addSetting(color);
        addSetting(mobColor);
        addSetting(animalColor);
        addSetting(itemColor);
        addSetting(outline);
        addSetting(fill);
        addSetting(showName);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;

            boolean shouldHighlight = false;
            if (entity instanceof PlayerEntity && players.isEnabled()) shouldHighlight = true;
            else if (entity instanceof Monster && mobs.isEnabled()) shouldHighlight = true;
            else if (entity instanceof AnimalEntity && animals.isEnabled()) shouldHighlight = true;
            else if (entity instanceof ItemEntity && items.isEnabled()) shouldHighlight = true;

            // Glow + Outline modes use wall-through model outline; box modes draw in onRender2D
            try {
                boolean glowOn = shouldHighlight && (mode.is("Glow") || mode.is("Outline"));
                entity.setGlowing(glowOn);
                if (mode.is("Outline")) {
                    if (shouldHighlight) assignTeam(entity);
                    else removeFromTeams(entity);
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity entity : mc.world.getEntities()) {
            try {
                entity.setGlowing(false);
            } catch (Throwable ignored) {}
        }
        clearTeams();
    }

    // Per-type outline colors via client scoreboard teams: the model-hugging glow
    // takes the team color, so players/mobs render in their own colors.
    private static final java.util.Set<String> OUR_ENTRIES = new java.util.HashSet<>();

    private net.minecraft.scoreboard.Team team(String name, int rgb) {
        try {
            net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
            net.minecraft.scoreboard.Team team = board.getTeam(name);
            if (team == null) team = board.addTeam(name);
            team.setColor(nearestFormatting(rgb));
            return team;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void assignTeam(Entity entity) {
        try {
            net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
            String entry = entity instanceof PlayerEntity
                    ? entity.getName().getString()
                    : entity.getUuid().toString();
            String want;
            int rgb;
            if (entity instanceof PlayerEntity) {
                want = "anormalP";
                rgb = color.getValue();
            } else if (entity instanceof Monster) {
                want = "anormalM";
                rgb = mobColor.getValue();
            } else if (entity instanceof AnimalEntity) {
                want = "anormalA";
                rgb = animalColor.getValue();
            } else {
                want = "anormalI";
                rgb = itemColor.getValue();
            }
            net.minecraft.scoreboard.Team team = team(want, rgb);
            if (team == null) return;
            net.minecraft.scoreboard.Team cur = board.getScoreHolderTeam(entry);
            if (cur != team) {
                if (cur != null) {
                    try {
                        board.removeScoreHolderFromTeam(entry, cur);
                    } catch (Throwable ignored) {}
                }
                board.addScoreHolderToTeam(entry, team);
                OUR_ENTRIES.add(entry);
            }
        } catch (Throwable ignored) {}
    }

    private void removeFromTeams(Entity entity) {
        try {
            net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
            String entry = entity instanceof PlayerEntity
                    ? entity.getName().getString()
                    : entity.getUuid().toString();
            if (!OUR_ENTRIES.contains(entry)) return;
            net.minecraft.scoreboard.Team cur = board.getScoreHolderTeam(entry);
            if (cur != null) {
                board.removeScoreHolderFromTeam(entry, cur);
            }
            OUR_ENTRIES.remove(entry);
        } catch (Throwable ignored) {}
    }

    private void clearTeams() {
        try {
            net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
            for (String name : new String[]{"anormalP", "anormalM", "anormalA", "anormalI"}) {
                try {
                    net.minecraft.scoreboard.Team team = board.getTeam(name);
                    if (team != null) board.removeTeam(team);
                } catch (Throwable ignored) {}
            }
            OUR_ENTRIES.clear();
        } catch (Throwable ignored) {}
    }

    private net.minecraft.util.Formatting nearestFormatting(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int[][] table = {
                {0, 0, 0}, {0, 0, 170}, {0, 170, 0}, {0, 170, 170}, {170, 0, 0},
                {170, 0, 170}, {255, 170, 0}, {170, 170, 170}, {85, 85, 85}, {85, 85, 255},
                {85, 255, 85}, {85, 255, 255}, {255, 85, 85}, {255, 85, 255}, {255, 255, 85}, {255, 255, 255}
        };
        net.minecraft.util.Formatting[] colors = {
                net.minecraft.util.Formatting.BLACK, net.minecraft.util.Formatting.DARK_BLUE,
                net.minecraft.util.Formatting.DARK_GREEN, net.minecraft.util.Formatting.DARK_AQUA,
                net.minecraft.util.Formatting.DARK_RED, net.minecraft.util.Formatting.DARK_PURPLE,
                net.minecraft.util.Formatting.GOLD, net.minecraft.util.Formatting.GRAY,
                net.minecraft.util.Formatting.DARK_GRAY, net.minecraft.util.Formatting.BLUE,
                net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.AQUA,
                net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.LIGHT_PURPLE,
                net.minecraft.util.Formatting.YELLOW, net.minecraft.util.Formatting.WHITE
        };
        int best = 7;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            long dr = r - table[i][0], dg = g - table[i][1], db = b - table[i][2];
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return colors[best];
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mode.is("Glow") || mc.world == null || mc.player == null) return;
        boolean corners = mode.is("Corner"); // Outline draws full box + glow outline
        for (Entity entity : mc.world.getEntities()) {
            try {
                if (entity == mc.player) continue;
                if (entity instanceof PlayerEntity) { if (!players.isEnabled()) continue; }
                else if (entity instanceof Monster) { if (!mobs.isEnabled()) continue; }
                else if (entity instanceof AnimalEntity) { if (!animals.isEnabled()) continue; }
                else if (entity instanceof ItemEntity) { if (!items.isEnabled()) continue; }
                else continue;

                Box box = entity.getBoundingBox();
                // Render-time position by tracked displacement (exact under
                // acceleration; velocity extrapolation swam back and forth).
                double hgt = box.maxY - box.minY;
                double[] lc = com.anormal.client.util.ProjectionUtil.lerpEntity(entity.getId(),
                        (box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0,
                        (box.minZ + box.maxZ) / 2.0, tickDelta);
                double cx0 = Math.round(lc[0] * 16.0) / 16.0;
                double cz0 = Math.round(lc[2] * 16.0) / 16.0;
                double cyMid = lc[1];
                int[] sTop = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid + hgt / 2.0 + 0.1, cz0), tickDelta);
                int[] sBot = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid - hgt / 2.0, cz0), tickDelta);
                // X from MID-height projection: top/bottom centers skew under perspective,
                // which pushed the box ahead of / behind the hitbox
                int[] sMid = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid, cz0), tickDelta);
                if (sTop == null || sBot == null || sMid == null) continue;
                int h = Math.max(4, sBot[1] - sTop[1]);
                int w = Math.max(4, h / 3);
                int x = sMid[0];
                // Bottom-anchored: feet projection is exact, top derives from height.
                // Top-anchoring let maxY padding + projection error float the whole box.
                int yBot = Math.max(sTop[1], sBot[1]);
                int yTop = yBot - h;
                int col = typeColor(entity);
                boolean v2 = mode.is("V2");

                if (v2 && fill.isEnabled()) {
                    // Translucent 3D-box feel: filled body with bright edge
                    context.fill(x - w / 2, yTop, x + w / 2, yBot, (col & 0x00FFFFFF) | 0x32000000);
                }

                if (corners) {
                    int cl = Math.min(w / 3, 8);
                    // top-left, top-right, bottom-left, bottom-right corners
                    context.fill(x - w / 2, yTop, x - w / 2 + cl, yTop + 1, col);
                    context.fill(x - w / 2, yTop, x - w / 2 + 1, yTop + cl, col);
                    context.fill(x + w / 2 - cl, yTop, x + w / 2, yTop + 1, col);
                    context.fill(x + w / 2 - 1, yTop, x + w / 2, yTop + cl, col);
                    context.fill(x - w / 2, yBot - 1, x - w / 2 + cl, yBot, col);
                    context.fill(x - w / 2, yBot - cl, x - w / 2 + 1, yBot, col);
                    context.fill(x + w / 2 - cl, yBot - 1, x + w / 2, yBot, col);
                    context.fill(x + w / 2 - 1, yBot - cl, x + w / 2, yBot, col);
                } else {
                    context.fill(x - w / 2, yTop, x + w / 2, yTop + 1, col);
                    context.fill(x - w / 2, yBot - 1, x + w / 2, yBot, col);
                    context.fill(x - w / 2, yTop, x - w / 2 + 1, yBot, col);
                    context.fill(x + w / 2 - 1, yTop, x + w / 2, yBot, col);
                }

                if (outline.isEnabled() && !corners) {
                    // Extra bright outer edge
                    context.fill(x - w / 2 - 1, yTop - 1, x + w / 2 + 1, yTop, col);
                    context.fill(x - w / 2 - 1, yBot, x + w / 2 + 1, yBot + 1, col);
                    context.fill(x - w / 2 - 1, yTop, x - w / 2, yBot, col);
                    context.fill(x + w / 2, yTop, x + w / 2 + 1, yBot, col);
                }

                if (v2 && mc.textRenderer != null) {
                    try {
                        String nm = entity.getName().getString();
                        RenderUtils.drawText(context, mc.textRenderer, nm, x - mc.textRenderer.getWidth(nm) / 2, yTop - 11, 0xFFFFFFFF, true);
                    } catch (Throwable ignored) {}
                }

                if (showName.isEnabled() && !(entity instanceof PlayerEntity) && mc.textRenderer != null) {
                    try {
                        String mob = entity.getName().getString();
                        RenderUtils.drawText(context, mc.textRenderer, mob, x - mc.textRenderer.getWidth(mob) / 2, yTop - (v2 ? 22 : 11), 0xFFFFFFFF, true);
                    } catch (Throwable ignored) {}
                }

                if (healthBar.isEnabled() && entity instanceof LivingEntity living) {
                    float maxHp = Math.max(1.0f, living.getMaxHealth());
                    float pct = Math.max(0.0f, Math.min(1.0f, living.getHealth() / maxHp));
                    int barH = (int) (h * pct);
                    int barCol = pct > 0.6 ? 0xFF55FF55 : (pct > 0.3 ? 0xFFFFFF55 : 0xFFFF5555);
                    context.fill(x - w / 2 - 4, yTop, x - w / 2 - 2, yBot, 0xAA222222);
                    context.fill(x - w / 2 - 4, yBot - barH, x - w / 2 - 2, yBot, barCol);
                }
            } catch (Throwable ignored) {}
        }
    }

    private int typeColor(Entity entity) {
        try {
            if (entity instanceof PlayerEntity) return color.getValue();
            if (entity instanceof Monster) return mobColor.getValue();
            if (entity instanceof AnimalEntity) return animalColor.getValue();
            if (entity instanceof ItemEntity) return itemColor.getValue();
        } catch (Throwable ignored) {}
        return color.getValue();
    }
}
