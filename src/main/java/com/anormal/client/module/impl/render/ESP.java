package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Highlights players, mobs and items through walls.
 *
 * <p><b>Shift / flicker fix:</b> the box used to be built from a position that
 * was rounded to 1/16 of a block and a width derived from the projected height
 * ({@code h / 3}). Both quantised the box, so it visibly jumped around while the
 * entity moved. The box is now the projected bounding box of the entity's real
 * AABB at its interpolated render position, so it is smooth and exact.</p>
 *
 * <p><b>Mob fix:</b> only {@code Monster} and {@code AnimalEntity} were matched,
 * so slimes, water animals, ambient mobs and every other living entity were
 * skipped entirely. The "Monsters" toggle now covers every living entity that is
 * not a player or a passive animal.</p>
 */
public class ESP extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "ESP visual mode", "2D Box", "Glow", "2D Box", "Corner", "Outline", "V2");
    public final BooleanSetting players = new BooleanSetting("Players", "Highlight other players", true);
    public final BooleanSetting mobs = new BooleanSetting("Monsters", "Highlight monsters and every other mob", false);
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

    /** Single source of truth for what counts as a target. */
    private boolean shouldRender(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (entity instanceof PlayerEntity) return players.isEnabled();
        if (entity instanceof ItemEntity) return items.isEnabled();
        if (entity instanceof AnimalEntity) return animals.isEnabled();
        // Every other living entity (monsters, slimes, water animals, ambient, ...).
        return entity instanceof LivingEntity && mobs.isEnabled();
    }

    private int typeColor(Entity entity) {
        if (entity instanceof PlayerEntity) return color.getValue();
        if (entity instanceof ItemEntity) return itemColor.getValue();
        if (entity instanceof AnimalEntity) return animalColor.getValue();
        return mobColor.getValue();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        boolean glowMode = mode.is("Glow") || mode.is("Outline");
        for (Entity entity : mc.world.getEntities()) {
            boolean on = glowMode && shouldRender(entity);
            try {
                entity.setGlowing(on);
                if (mode.is("Outline")) {
                    if (on) assignTeam(entity);
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
            } else if (entity instanceof AnimalEntity) {
                want = "anormalA";
                rgb = animalColor.getValue();
            } else if (entity instanceof ItemEntity) {
                want = "anormalI";
                rgb = itemColor.getValue();
            } else {
                want = "anormalM";
                rgb = mobColor.getValue();
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
        boolean corners = mode.is("Corner");
        boolean v2 = mode.is("V2");

        for (Entity entity : mc.world.getEntities()) {
            try {
                if (!shouldRender(entity)) continue;

                // Exact, per-frame interpolated AABB: no rounding, no derived width.
                Vec3d lerp = ProjectionUtil.lerpPos(entity, tickDelta);
                Box box = entity.getBoundingBox();
                double ox = lerp.x - entity.getX(), oy = lerp.y - entity.getY(), oz = lerp.z - entity.getZ();
                double x0 = box.minX + ox, y0 = box.minY + oy, z0 = box.minZ + oz;
                double x1 = box.maxX + ox, y1 = box.maxY + oy, z1 = box.maxZ + oz;

                int[][] c = {
                        ProjectionUtil.project(new Vec3d(x0, y0, z0), tickDelta),
                        ProjectionUtil.project(new Vec3d(x1, y0, z0), tickDelta),
                        ProjectionUtil.project(new Vec3d(x1, y0, z1), tickDelta),
                        ProjectionUtil.project(new Vec3d(x0, y0, z1), tickDelta),
                        ProjectionUtil.project(new Vec3d(x0, y1, z0), tickDelta),
                        ProjectionUtil.project(new Vec3d(x1, y1, z0), tickDelta),
                        ProjectionUtil.project(new Vec3d(x1, y1, z1), tickDelta),
                        ProjectionUtil.project(new Vec3d(x0, y1, z1), tickDelta)
                };

                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
                boolean any = false;
                for (int[] q : c) {
                    if (q == null) continue;
                    any = true;
                    minX = Math.min(minX, q[0]);
                    minY = Math.min(minY, q[1]);
                    maxX = Math.max(maxX, q[0]);
                    maxY = Math.max(maxY, q[1]);
                }
                if (!any) continue;

                int w = Math.max(4, maxX - minX);
                int h = Math.max(4, maxY - minY);
                int x = minX;
                int yTop = minY;
                int yBot = minY + h;
                int col = typeColor(entity);

                if ((v2 || fill.isEnabled()) && fill.isEnabled()) {
                    context.fill(x, yTop, x + w, yBot, (col & 0x00FFFFFF) | 0x32000000);
                }

                if (corners) {
                    int cl = Math.min(w / 3, 8);
                    context.fill(x, yTop, x + cl, yTop + 1, col);
                    context.fill(x, yTop, x + 1, yTop + cl, col);
                    context.fill(x + w - cl, yTop, x + w, yTop + 1, col);
                    context.fill(x + w - 1, yTop, x + w, yTop + cl, col);
                    context.fill(x, yBot - 1, x + cl, yBot, col);
                    context.fill(x, yBot - cl, x + 1, yBot, col);
                    context.fill(x + w - cl, yBot - 1, x + w, yBot, col);
                    context.fill(x + w - 1, yBot - cl, x + w, yBot, col);
                } else {
                    context.fill(x, yTop, x + w, yTop + 1, col);
                    context.fill(x, yBot - 1, x + w, yBot, col);
                    context.fill(x, yTop, x + 1, yBot, col);
                    context.fill(x + w - 1, yTop, x + w, yBot, col);

                    if (outline.isEnabled()) {
                        context.fill(x - 1, yTop - 1, x + w + 1, yTop, col);
                        context.fill(x - 1, yBot, x + w + 1, yBot + 1, col);
                        context.fill(x - 1, yTop, x, yBot, col);
                        context.fill(x + w, yTop, x + w + 1, yBot, col);
                    }
                }

                if (mc.textRenderer != null) {
                    String nm = entity.getName().getString();
                    int nameY = yTop - (v2 ? 22 : 11);
                    if (v2 || (showName.isEnabled() && !(entity instanceof PlayerEntity))) {
                        RenderUtils.drawText(context, mc.textRenderer, nm,
                                x + w / 2 - mc.textRenderer.getWidth(nm) / 2, nameY, 0xFFFFFFFF, true);
                    }
                }

                if (healthBar.isEnabled() && entity instanceof LivingEntity living) {
                    float maxHp = Math.max(1.0f, living.getMaxHealth());
                    float pct = Math.max(0.0f, Math.min(1.0f, living.getHealth() / maxHp));
                    int barH = (int) (h * pct);
                    int barCol = pct > 0.6 ? 0xFF55FF55 : (pct > 0.3 ? 0xFFFFFF55 : 0xFFFF5555);
                    context.fill(x - 4, yTop, x - 2, yBot, 0xAA222222);
                    context.fill(x - 4, yBot - barH, x - 2, yBot, barCol);
                }
            } catch (Throwable ignored) {}
        }
    }
}
