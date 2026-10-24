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
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effects Module — Rich visual combat effects and satisfying hit sounds:
 * <ul>
 *   <li><b>Hit Effects (14 Themes):</b> Spiral, Body Rings, Lightning, Blood, Totem,
 *       Star Shower, Firestorm, Frost Shards, Hearts, Sonic Boom, Vortex, Sparkles, Skull, Shockwave, Off.</li>
 *   <li><b>Hit Sounds:</b> Ding, Crack, Bell, Pop, Crit, Off with custom volume and pitch.</li>
 *   <li><b>Customization:</b> Strands, Spiral Speed, Spiral Height, Hit Life, Body Ring alpha & radius.</li>
 * </ul>
 */
public class Effects extends Module {

    public final ModeSetting hitEffect = new ModeSetting("Hit Effect", "Visual animation on attack",
            "Spiral",
            "Spiral", "Body Rings", "Lightning", "Blood", "Totem",
            "Star Shower", "Firestorm", "Frost Shards", "Hearts",
            "Sonic Boom", "Vortex", "Sparkles", "Skull", "Shockwave", "Off");

    public final ModeSetting hitSound = new ModeSetting("Hit Sound", "Audio cue when hitting an enemy",
            "Ding", "Ding", "Crack", "Bell", "Pop", "Crit", "Off");

    public final NumberSetting soundVolume = new NumberSetting("Sound Volume", "Hit sound volume percentage",
            75.0, 5.0, 100.0, 5.0);

    public final NumberSetting soundPitch = new NumberSetting("Sound Pitch", "Hit sound frequency pitch",
            1.0, 0.5, 2.0, 0.05);

    // --- Geometry & Physics settings ---
    public final NumberSetting strands = new NumberSetting("Strands", "Number of effect streams / particles",
            12.0, 2.0, 24.0, 1.0);

    public final NumberSetting hitSize = new NumberSetting("Hit Size", "Diameter of hit animation in blocks",
            1.50, 0.4, 4.0, 0.1);

    public final NumberSetting hitLife = new NumberSetting("Hit Life (ms)", "Duration of hit animation in ms",
            750.0, 200.0, 2000.0, 50.0);

    public final NumberSetting spiralHeight = new NumberSetting("Spiral Height", "Height the effect rises in blocks",
            2.80, 0.5, 6.0, 0.1);

    public final NumberSetting spiralSpeed = new NumberSetting("Spiral Speed", "Rotation speed in degrees per second",
            360.0, 90.0, 720.0, 30.0);

    // --- Body Rings ---
    public final BooleanSetting bodyRings = new BooleanSetting("Body Rings", "Horizontal rotating rings around targets",
            true);

    public final NumberSetting bodyRingRadius = new NumberSetting("Body Ring Radius", "Ring radius in blocks",
            0.60, 0.2, 2.0, 0.05);

    public final NumberSetting bodyRingAlpha = new NumberSetting("Body Ring Alpha", "Opacity of the body ring",
            65.0, 10.0, 100.0, 5.0);

    public final BooleanSetting selfRings = new BooleanSetting("Self Rings", "Show ambient rings around own player",
            false);

    // --- Colors ---
    public final ColorSetting effectColor = new ColorSetting("Color", "Primary effect color",
            ColorUtils.rgba(255, 119, 0, 255));

    public final BooleanSetting rainbow = new BooleanSetting("Rainbow", "Cycle effect hue continuously",
            false);

    public final NumberSetting rainbowSpeed = new NumberSetting("Rainbow Speed", "Hue cycling speed",
            1.0, 0.2, 5.0, 0.1);

    private final List<HitAnimation> activeHits = new ArrayList<>();

    public Effects() {
        super("Effects", "Combat hit visual animations (14 themes) and satisfying hit sounds", Category.RENDER);
        addSetting(hitEffect);
        addSetting(hitSound);
        addSetting(soundVolume);
        addSetting(soundPitch);
        addSetting(strands);
        addSetting(hitSize);
        addSetting(hitLife);
        addSetting(spiralHeight);
        addSetting(spiralSpeed);
        addSetting(bodyRings);
        addSetting(bodyRingRadius);
        addSetting(bodyRingAlpha);
        addSetting(selfRings);
        addSetting(effectColor);
        addSetting(rainbow);
        addSetting(rainbowSpeed);
    }

    /**
     * Triggered directly from InteractionManagerMixin when the player hits an entity.
     */
    public void onAttack(Entity target) {
        if (!isEnabled() || mc.player == null) return;

        playHitSound();

        if (target != null && !hitEffect.is("Off")) {
            Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
            activeHits.add(new HitAnimation(targetPos, System.currentTimeMillis()));
        }
    }

    private void playHitSound() {
        if (hitSound.is("Off") || mc.player == null) return;

        SoundEvent sound = switch (hitSound.getValue()) {
            case "Ding" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "Crack" -> SoundEvents.BLOCK_ANVIL_LAND;
            case "Bell" -> SoundEvents.BLOCK_BELL_USE;
            case "Pop" -> SoundEvents.ENTITY_CHICKEN_EGG;
            case "Crit" -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
            default -> null;
        };

        if (sound != null) {
            float vol = (float) ((double) soundVolume.getValue() / 100.0);
            float pitch = (float) (double) soundPitch.getValue();
            try {
                mc.player.playSound(sound, vol, pitch);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long maxLife = (long) (double) hitLife.getValue();
        int baseColor = resolveColor();

        // 1. Render active hit animations
        Iterator<HitAnimation> it = activeHits.iterator();
        while (it.hasNext()) {
            HitAnimation hit = it.next();
            long elapsed = now - hit.startTime;
            if (elapsed > maxLife) {
                it.remove();
                continue;
            }

            float progress = (float) elapsed / maxLife; // 0.0 -> 1.0
            float fadeAlpha = (1.0f - progress);

            int alpha = (int) (255 * fadeAlpha);
            int colorWithAlpha = (baseColor & 0x00FFFFFF) | (alpha << 24);

            String selectedEffect = hitEffect.getValue();
            switch (selectedEffect) {
                case "Spiral" -> renderSpiral(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Body Rings" -> renderBodyRing(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Lightning" -> renderLightning(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Blood" -> renderBlood(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Totem" -> renderTotemPillar(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Star Shower" -> renderStarShower(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Firestorm" -> renderFirestorm(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Frost Shards" -> renderFrostShards(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Hearts" -> renderHearts(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Sonic Boom" -> renderSonicBoom(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Vortex" -> renderVortex(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Sparkles" -> renderSparkles(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Skull" -> renderSkull(context, hit.pos, progress, colorWithAlpha, tickDelta);
                case "Shockwave" -> renderShockwave(context, hit.pos, progress, colorWithAlpha, tickDelta);
            }
        }

        // 2. Ambient body rings around self (if enabled)
        if (selfRings.isEnabled() && mc.options.getPerspective().isFirstPerson() == false) {
            Vec3d selfPos = ProjectionUtil.lerpPos(mc.player, tickDelta);
            float timeSec = (now % 10000) / 1000.0f;
            int ringCol = (baseColor & 0x00FFFFFF) | ((int) ((double) bodyRingAlpha.getValue() * 2.55) << 24);
            renderRotatingRing(context, selfPos.add(0, 0.8, 0), (float) (double) bodyRingRadius.getValue(), timeSec, ringCol, tickDelta);
            renderRotatingRing(context, selfPos.add(0, 1.3, 0), (float) (double) bodyRingRadius.getValue() * 0.85f, -timeSec * 1.2f, ringCol, tickDelta);
        }
    }

    // -------------------------------------------------------------------------
    //  1. SPIRAL
    // -------------------------------------------------------------------------
    private void renderSpiral(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        int numStrands = (int) (double) strands.getValue();
        double radius = (double) hitSize.getValue() * 0.5 * (1.0 - progress * 0.2);
        double maxH = (double) spiralHeight.getValue();
        double speed = (double) spiralSpeed.getValue();
        double currentAngleOffset = Math.toRadians((progress * speed * 360.0) / 1000.0);

        for (int s = 0; s < numStrands; s++) {
            double strandOffset = (2 * Math.PI / numStrands) * s;
            int segments = 8;
            int[] prevScreen = null;

            for (int seg = 0; seg <= segments; seg++) {
                double segProgress = Math.max(0.0, progress - (seg * 0.03));
                double angle = currentAngleOffset + strandOffset - (seg * 0.35);
                double h = segProgress * maxH;

                double px = center.x + Math.cos(angle) * radius;
                double py = center.y + h;
                double pz = center.z + Math.sin(angle) * radius;

                int[] screen = ProjectionUtil.project(new Vec3d(px, py, pz), tickDelta);
                if (screen != null) {
                    if (prevScreen != null) {
                        RenderUtils.drawLine(context, prevScreen[0], prevScreen[1], screen[0], screen[1], color, 1.8f);
                    } else if (seg == 0) {
                        context.fill(screen[0] - 2, screen[1] - 2, screen[0] + 3, screen[1] + 3, color);
                    }
                }
                prevScreen = screen;
            }
        }
    }

    // -------------------------------------------------------------------------
    //  2. BODY RINGS
    // -------------------------------------------------------------------------
    private void renderBodyRing(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        double r = (double) bodyRingRadius.getValue() * (1.0 + progress * 0.4);
        double h = progress * 1.8;
        renderRotatingRing(context, center.add(0, h, 0), (float) r, progress * 4.0f, color, tickDelta);
    }

    private void renderRotatingRing(DrawContext context, Vec3d center, float radius, float angleOffset, int color, float tickDelta) {
        int steps = 16;
        int[] firstPoint = null;
        int[] prevPoint = null;

        for (int i = 0; i <= steps; i++) {
            double angle = angleOffset + (2 * Math.PI / steps) * i;
            double px = center.x + Math.cos(angle) * radius;
            double py = center.y;
            double pz = center.z + Math.sin(angle) * radius;

            int[] screen = ProjectionUtil.project(new Vec3d(px, py, pz), tickDelta);
            if (screen != null) {
                if (firstPoint == null) firstPoint = screen;
                if (prevPoint != null) {
                    RenderUtils.drawLine(context, prevPoint[0], prevPoint[1], screen[0], screen[1], color, 1.5f);
                }
            }
            prevPoint = screen;
        }

        if (firstPoint != null && prevPoint != null) {
            RenderUtils.drawLine(context, prevPoint[0], prevPoint[1], firstPoint[0], firstPoint[1], color, 1.5f);
        }
    }

    // -------------------------------------------------------------------------
    //  3. LIGHTNING
    // -------------------------------------------------------------------------
    private void renderLightning(DrawContext context, Vec3d targetPos, float progress, int color, float tickDelta) {
        if (progress > 0.6f) return;

        int segments = 7;
        double totalH = 5.5;
        int boltColor = ColorUtils.rgba(255, 255, 255, (int) ((1.0f - progress / 0.6f) * 255));

        int[] prevScreen = null;
        for (int i = 0; i <= segments; i++) {
            double frac = (double) i / segments;
            double h = totalH * (1.0 - frac);
            double jitterX = (i == 0 || i == segments) ? 0 : Math.sin(i * 123.0 + targetPos.x) * 0.35;
            double jitterZ = (i == 0 || i == segments) ? 0 : Math.cos(i * 123.0 + targetPos.z) * 0.35;

            Vec3d boltPoint = targetPos.add(jitterX, h, jitterZ);
            int[] sc = ProjectionUtil.project(boltPoint, tickDelta);
            if (sc != null && prevScreen != null) {
                RenderUtils.drawLine(context, prevScreen[0], prevScreen[1], sc[0], sc[1], boltColor, 2.5f);
            }
            prevScreen = sc;
        }
    }

    // -------------------------------------------------------------------------
    //  4. BLOOD
    // -------------------------------------------------------------------------
    private void renderBlood(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        int particles = (int) (double) strands.getValue() * 2;
        int bloodColor = ColorUtils.rgba(230, 20, 20, (int) ((1.0f - progress) * 255));

        for (int i = 0; i < particles; i++) {
            double seed = i * (Math.PI * 2 / particles);
            double dirX = Math.cos(seed) * (0.8 + (i % 3) * 0.4);
            double dirZ = Math.sin(seed) * (0.8 + (i % 3) * 0.4);
            double dist = progress * (double) hitSize.getValue();
            double dropY = (progress * 0.6) - (progress * progress * 2.2);

            Vec3d p = center.add(dirX * dist, 1.0 + dropY, dirZ * dist);
            int[] sc = ProjectionUtil.project(p, tickDelta);
            if (sc != null) {
                context.fill(sc[0] - 1, sc[1] - 1, sc[0] + 2, sc[1] + 2, bloodColor);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  5. TOTEM PILLAR
    // -------------------------------------------------------------------------
    private void renderTotemPillar(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        double height = progress * (double) spiralHeight.getValue() * 1.4;
        Vec3d bottom = center.add(0, 0, 0);
        Vec3d top = center.add(0, height, 0);

        int[] scB = ProjectionUtil.project(bottom, tickDelta);
        int[] scT = ProjectionUtil.project(top, tickDelta);

        if (scB != null && scT != null) {
            RenderUtils.drawLine(context, scB[0], scB[1], scT[0], scT[1], color, 3.0f);
            context.fill(scT[0] - 3, scT[1] - 3, scT[0] + 4, scT[1] + 4, ColorUtils.rgba(255, 230, 0, 255));
        }
    }

    // -------------------------------------------------------------------------
    //  6. STAR SHOWER
    // -------------------------------------------------------------------------
    private void renderStarShower(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        int stars = 8;
        int starCol = ColorUtils.rgba(255, 255, 120, (int) ((1.0f - progress) * 255));

        for (int i = 0; i < stars; i++) {
            double angle = (2 * Math.PI / stars) * i;
            double dist = progress * (double) hitSize.getValue();
            double drop = (progress * 1.5) - (progress * progress * 3.0);

            Vec3d p = center.add(Math.cos(angle) * dist, 1.8 + drop, Math.sin(angle) * dist);
            int[] sc = ProjectionUtil.project(p, tickDelta);
            if (sc != null) {
                // Cross / 4-point star shape
                int x = sc[0], y = sc[1];
                context.fill(x - 3, y, x + 4, y + 1, starCol);
                context.fill(x, y - 3, x + 1, y + 4, starCol);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  7. FIRESTORM
    // -------------------------------------------------------------------------
    private void renderFirestorm(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        int embers = 14;
        for (int i = 0; i < embers; i++) {
            double angle = i * 0.7 + (progress * 6.0);
            double r = (1.0 - progress * 0.4) * 0.8;
            double h = progress * 2.5 + (i * 0.1);

            int emberCol = (i % 2 == 0)
                    ? ColorUtils.rgba(255, 80, 0, (int) ((1.0f - progress) * 255))
                    : ColorUtils.rgba(255, 210, 0, (int) ((1.0f - progress) * 255));

            Vec3d p = center.add(Math.cos(angle) * r, h, Math.sin(angle) * r);
            int[] sc = ProjectionUtil.project(p, tickDelta);
            if (sc != null) {
                context.fill(sc[0] - 2, sc[1] - 2, sc[0] + 2, sc[1] + 2, emberCol);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  8. FROST SHARDS
    // -------------------------------------------------------------------------
    private void renderFrostShards(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        int shards = 6;
        int iceCol = ColorUtils.rgba(120, 230, 255, (int) ((1.0f - progress) * 255));

        for (int i = 0; i < shards; i++) {
            double angle = (2 * Math.PI / shards) * i + (progress * 0.5);
            double dist = progress * (double) hitSize.getValue() * 1.2;

            Vec3d p = center.add(Math.cos(angle) * dist, 1.0 + (i % 2 == 0 ? 0.3 : -0.2), Math.sin(angle) * dist);
            int[] sc = ProjectionUtil.project(p, tickDelta);
            if (sc != null) {
                // Diamond shard shape
                int x = sc[0], y = sc[1];
                context.fill(x - 2, y - 2, x + 3, y + 3, iceCol);
                context.fill(x - 1, y - 3, x + 2, y + 4, ColorUtils.rgba(255, 255, 255, 200));
            }
        }
    }

    // -------------------------------------------------------------------------
    //  9. HEARTS
    // -------------------------------------------------------------------------
    private void renderHearts(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        int heartCount = 4;
        int heartCol = ColorUtils.rgba(255, 60, 120, (int) ((1.0f - progress) * 255));

        for (int i = 0; i < heartCount; i++) {
            double offsetAngle = (2 * Math.PI / heartCount) * i;
            double dist = Math.sin(progress * Math.PI) * 0.6;
            double h = 1.0 + progress * 1.8 + (i * 0.15);

            Vec3d p = center.add(Math.cos(offsetAngle) * dist, h, Math.sin(offsetAngle) * dist);
            int[] sc = ProjectionUtil.project(p, tickDelta);
            if (sc != null) {
                // Pixelated heart shape
                int x = sc[0], y = sc[1];
                context.fill(x - 3, y - 2, x - 1, y, heartCol);
                context.fill(x + 1, y - 2, x + 3, y, heartCol);
                context.fill(x - 3, y, x + 3, y + 2, heartCol);
                context.fill(x - 1, y + 2, x + 1, y + 3, heartCol);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  10. SONIC BOOM
    // -------------------------------------------------------------------------
    private void renderSonicBoom(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        double r1 = progress * 2.2;
        double r2 = Math.max(0.0, (progress - 0.15) * 2.2);

        int sonicCol = ColorUtils.rgba(0, 230, 210, (int) ((1.0f - progress) * 255));
        renderRotatingRing(context, center.add(0, 1.2, 0), (float) r1, 0, sonicCol, tickDelta);
        if (r2 > 0.1) {
            renderRotatingRing(context, center.add(0, 1.2, 0), (float) r2, 0, sonicCol, tickDelta);
        }
    }

    // -------------------------------------------------------------------------
    //  11. VORTEX
    // -------------------------------------------------------------------------
    private void renderVortex(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        int arms = 3;
        double inwardRadius = Math.max(0.1, (1.0 - progress) * (double) hitSize.getValue());
        double spinAngle = progress * 12.0;

        int voidCol = ColorUtils.rgba(180, 50, 255, (int) ((1.0f - progress * 0.5f) * 255));

        for (int a = 0; a < arms; a++) {
            double baseA = (2 * Math.PI / arms) * a;
            int[] prev = null;
            for (int seg = 0; seg < 6; seg++) {
                double aProgress = seg / 6.0;
                double curR = inwardRadius * aProgress;
                double curA = baseA + spinAngle + (seg * 0.4);

                Vec3d p = center.add(Math.cos(curA) * curR, 1.0 + (aProgress * 0.4), Math.sin(curA) * curR);
                int[] sc = ProjectionUtil.project(p, tickDelta);
                if (sc != null && prev != null) {
                    RenderUtils.drawLine(context, prev[0], prev[1], sc[0], sc[1], voidCol, 2.0f);
                }
                prev = sc;
            }
        }
    }

    // -------------------------------------------------------------------------
    //  12. SPARKLES
    // -------------------------------------------------------------------------
    private void renderSparkles(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        int sparkleCount = 10;
        for (int i = 0; i < sparkleCount; i++) {
            double seed = i * 55.0;
            double r = Math.sin(progress * Math.PI) * (double) hitSize.getValue() * 0.7;
            double h = 0.5 + progress * 2.0 + Math.sin(seed) * 0.5;

            int sparkleCol = (i % 2 == 0)
                    ? ColorUtils.rgba(255, 255, 255, (int) ((1.0f - progress) * 255))
                    : ColorUtils.rgba(255, 200, 50, (int) ((1.0f - progress) * 255));

            Vec3d p = center.add(Math.cos(seed) * r, h, Math.sin(seed) * r);
            int[] sc = ProjectionUtil.project(p, tickDelta);
            if (sc != null) {
                int x = sc[0], y = sc[1];
                context.fill(x - 2, y, x + 3, y + 1, sparkleCol);
                context.fill(x, y - 2, x + 1, y + 3, sparkleCol);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  13. SKULL / REAPER
    // -------------------------------------------------------------------------
    private void renderSkull(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        Vec3d headPos = center.add(0, 1.8 + progress * 0.8, 0);
        int[] sc = ProjectionUtil.project(headPos, tickDelta);
        if (sc == null) return;

        int x = sc[0];
        int y = sc[1];
        int skullCol = ColorUtils.rgba(240, 240, 240, (int) ((1.0f - progress) * 255));
        int eyeCol = ColorUtils.rgba(220, 30, 30, (int) ((1.0f - progress) * 255));

        // Pixelated skull
        context.fill(x - 5, y - 6, x + 5, y + 2, skullCol);
        context.fill(x - 3, y + 2, x + 3, y + 5, skullCol);
        // Eyes
        context.fill(x - 3, y - 3, x - 1, y - 1, eyeCol);
        context.fill(x + 1, y - 3, x + 3, y - 1, eyeCol);
        // Teeth slits
        context.fill(x - 2, y + 4, x - 1, y + 5, ColorUtils.rgba(20, 20, 20, 255));
        context.fill(x + 1, y + 4, x + 2, y + 5, ColorUtils.rgba(20, 20, 20, 255));
    }

    // -------------------------------------------------------------------------
    //  14. SHOCKWAVE
    // -------------------------------------------------------------------------
    private void renderShockwave(DrawContext context, Vec3d center, float progress, int color, float tickDelta) {
        double r = progress * (double) hitSize.getValue() * 1.6;
        int waveCol = (color & 0x00FFFFFF) | ((int) ((1.0f - progress) * 255) << 24);
        renderRotatingRing(context, center.add(0, 0.05, 0), (float) r, 0, waveCol, tickDelta);
    }

    private int resolveColor() {
        if (rainbow.isEnabled()) {
            double spd = (double) rainbowSpeed.getValue();
            float hue = (float) ((System.currentTimeMillis() % (long) (3000 / spd)) / (3000.0 / spd));
            int rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f);
            return (0xFF << 24) | (rgb & 0x00FFFFFF);
        }
        return effectColor.getColor();
    }

    private static class HitAnimation {
        final Vec3d pos;
        final long startTime;

        HitAnimation(Vec3d pos, long startTime) {
            this.pos = pos;
            this.startTime = startTime;
        }
    }
}
