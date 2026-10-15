package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.IntRangeSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Breaks end crystals before they hurt you.
 *
 * <p>Scans the crystals around you, works out how much each one would actually
 * damage you with the vanilla explosion falloff, and destroys the most
 * dangerous one first. Everything that can be tuned is exposed: threat
 * thresholds so it stays quiet when a crystal is harmless, self-damage caps,
 * suicide protection, rotation, swing mode, auto-switch, pause conditions and
 * a full render section.</p>
 */
public class AntiCrystal extends Module {

    // ------------------------------------------------------------------
    //  Targeting
    // ------------------------------------------------------------------
    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players", "Consider crystals threatening other players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", "Consider crystals threatening hostile mobs", false);
    public final BooleanSetting targetAnimals = new BooleanSetting("Target Animals", "Consider crystals threatening passive animals", false);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Never count damage to friends as useful", true);
    public final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible", "Ignore invisible entities", true);
    public final NumberSetting targetRange = new NumberSetting("Target Range", "Only act on crystals near a valid target", 8.0, 1.0, 16.0, 0.5);

    // ------------------------------------------------------------------
    //  Breaking
    // ------------------------------------------------------------------
    public final NumberSetting breakRange = new NumberSetting("Break Range", "Maximum distance to break a crystal", 4.5, 1.0, 6.0, 0.1);
    public final NumberSetting wallsRange = new NumberSetting("Walls Range", "Break range when the crystal is behind a wall", 3.0, 0.0, 6.0, 0.1);
    public final IntRangeSetting breakDelay = new IntRangeSetting("Break Delay", "Randomised ticks between breaks", 1, 2, 0, 20);
    public final ModeSetting prioritize = new ModeSetting("Prioritize", "Which crystal to destroy first", "Threat", "Threat", "Nearest", "Lowest Health", "Most Damage");
    public final BooleanSetting rotate = new BooleanSetting("Rotate", "Rotate towards the crystal before breaking", true);
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", "How fast the aim moves onto the crystal", 25.0, 1.0, 180.0, 1.0);
    public final BooleanSetting strictRotation = new BooleanSetting("Strict Rotation", "Only break once the crosshair is actually on the crystal", false);
    public final ModeSetting swingMode = new ModeSetting("Swing", "When to swing the hand", "Client", "Client", "Server", "None");
    public final BooleanSetting onlyWhileStanding = new BooleanSetting("Only While Standing", "Skip while you are moving", false);
    public final NumberSetting ticksExisted = new NumberSetting("Ticks Existed", "Minimum age of a crystal before it is broken", 0.0, 0.0, 20.0, 1.0);
    public final BooleanSetting ignoreOwn = new BooleanSetting("Ignore Own", "Never break crystals you just placed yourself", true);

    // ------------------------------------------------------------------
    //  Damage limits
    // ------------------------------------------------------------------
    public final BooleanSetting ignoreIfSafe = new BooleanSetting("Ignore If Safe", "Do nothing when the crystal cannot really hurt you", true);
    public final NumberSetting minThreatDamage = new NumberSetting("Min Threat Damage", "Ignore crystals that would deal less than this to you", 1.0, 0.0, 20.0, 0.5);
    public final NumberSetting minTargetDamage = new NumberSetting("Min Target Damage", "Only break when the crystal would deal this to a target", 0.0, 0.0, 20.0, 0.5);
    public final NumberSetting maxSelfDamage = new NumberSetting("Max Self Damage", "Never break a crystal that would deal more than this to you", 6.0, 0.0, 20.0, 0.5);
    public final BooleanSetting antiSuicide = new BooleanSetting("Anti Suicide", "Never break a crystal that could kill you", true);
    public final NumberSetting predictTicks = new NumberSetting("Predict Ticks", "How many ticks of movement to predict", 0.0, 0.0, 10.0, 1.0);

    // ------------------------------------------------------------------
    //  Auto switch
    // ------------------------------------------------------------------
    public final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", "Switch to a weapon before breaking", false);
    public final BooleanSetting switchBack = new BooleanSetting("Switch Back", "Return to the previous slot afterwards", true);
    public final NumberSetting switchDelay = new NumberSetting("Switch Delay", "Ticks to wait after switching before breaking", 1.0, 0.0, 10.0, 1.0);

    // ------------------------------------------------------------------
    //  Pause
    // ------------------------------------------------------------------
    public final BooleanSetting pauseOnUse = new BooleanSetting("Pause On Use", "Pause while you are using an item", true);
    public final BooleanSetting pauseOnMine = new BooleanSetting("Pause On Mine", "Pause while you are mining", true);
    public final NumberSetting pauseHealth = new NumberSetting("Pause Health", "Pause below this health (0 = never)", 0.0, 0.0, 20.0, 1.0);
    public final BooleanSetting pauseInInventory = new BooleanSetting("Pause In Inventory", "Pause while a container screen is open", true);

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", "Draw a marker on the crystal being broken", true);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Marker colour", ColorUtils.rgba(255, 90, 90, 255));
    public final BooleanSetting showRange = new BooleanSetting("Show Range", "Draw a circle at the break range", false);
    public final ColorSetting rangeColor = new ColorSetting("Range Color", "Range circle colour", ColorUtils.rgba(255, 255, 255, 90));
    public final BooleanSetting showDamage = new BooleanSetting("Show Damage", "Show the predicted damage next to the crystal", true);
    public final BooleanSetting showStatus = new BooleanSetting("Show Status", "Show the module state near the centre of the screen", false);

    // ------------------------------------------------------------------
    //  Misc
    // ------------------------------------------------------------------
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log breaks in chat", false);
    public final BooleanSetting notifySound = new BooleanSetting("Notify Sound", "Play a click when a crystal is broken", false);

    private final Set<Integer> ownCrystals = new HashSet<>();
    private int ownPlaceTicks = 0;
    private int breakCooldown = 0;
    private int switchCooldown = 0;
    private int prevSlot = -1;
    private int broken = 0;
    private EndCrystalEntity current;

    public AntiCrystal() {
        super("AntiCrystal", "Breaks end crystals before they can damage you", Category.COMBAT);

        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(targetAnimals);
        addSetting(ignoreFriends);
        addSetting(ignoreInvisible);
        addSetting(targetRange);

        addSetting(breakRange);
        addSetting(wallsRange);
        addSetting(breakDelay);
        addSetting(prioritize);
        addSetting(rotate);
        addSetting(rotationSpeed);
        addSetting(strictRotation);
        addSetting(swingMode);
        addSetting(onlyWhileStanding);
        addSetting(ticksExisted);
        addSetting(ignoreOwn);

        addSetting(ignoreIfSafe);
        addSetting(minThreatDamage);
        addSetting(minTargetDamage);
        addSetting(maxSelfDamage);
        addSetting(antiSuicide);
        addSetting(predictTicks);

        addSetting(autoSwitch);
        addSetting(switchBack);
        addSetting(switchDelay);

        addSetting(pauseOnUse);
        addSetting(pauseOnMine);
        addSetting(pauseHealth);
        addSetting(pauseInInventory);

        addSetting(showTarget);
        addSetting(targetColor);
        addSetting(showRange);
        addSetting(rangeColor);
        addSetting(showDamage);
        addSetting(showStatus);

        addSetting(chatLog);
        addSetting(notifySound);

        targetRange.visibleIf(() -> targetPlayers.isEnabled() || targetMobs.isEnabled() || targetAnimals.isEnabled());
        rotationSpeed.visibleIf(rotate::isEnabled);
        strictRotation.visibleIf(rotate::isEnabled);
        wallsRange.visibleIf(() -> breakRange.getValue() > 0);
        minThreatDamage.visibleIf(ignoreIfSafe::isEnabled);
        switchBack.visibleIf(autoSwitch::isEnabled);
        switchDelay.visibleIf(autoSwitch::isEnabled);
        rangeColor.visibleIf(showRange::isEnabled);
        targetColor.visibleIf(showTarget::isEnabled);
    }

    @Override
    public void onDisable() {
        ownCrystals.clear();
        ownPlaceTicks = 0;
        breakCooldown = 0;
        switchCooldown = 0;
        current = null;
        if (prevSlot != -1 && mc.player != null) {
            try {
                mc.player.getInventory().setSelectedSlot(prevSlot);
            } catch (Throwable ignored) {
            }
            prevSlot = -1;
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /** Vanilla crystal explosion falloff (power 6, ~12 max damage). */
    private static double damage(double distance) {
        double d = 1.0 - distance / 12.0;
        return d <= 0.0 ? 0.0 : d * d * 12.0;
    }

    private double distanceTo(Entity crystal, LivingEntity entity) {
        double ex = entity.getX(), ey = entity.getY() + entity.getHeight() / 2.0, ez = entity.getZ();
        double pt = predictTicks.getValue();
        if (pt > 0) {
            Vec3d v = entity.getVelocity();
            ex += v.x * pt;
            ey += v.y * pt;
            ez += v.z * pt;
        }
        double dx = crystal.getX() - ex;
        double dy = crystal.getY() - ey;
        double dz = crystal.getZ() - ez;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean passesFilter(LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (ignoreInvisible.isEnabled() && e.isInvisible()) return false;
        if (e instanceof PlayerEntity pl) {
            if (!targetPlayers.isEnabled()) return false;
            if (mc.player.isTeammate(pl)) return false;
            if (ignoreFriends.isEnabled() && isFriend(pl)) return false;
            return true;
        }
        if (e instanceof HostileEntity) return targetMobs.isEnabled();
        if (e instanceof PassiveEntity) return targetAnimals.isEnabled();
        return targetMobs.isEnabled();
    }

    private boolean isFriend(PlayerEntity pl) {
        try {
            var friends = com.anormal.client.module.ModuleManager.getModule(
                    com.anormal.client.module.impl.client.Friends.class);
            if (friends == null) return false;
            java.lang.reflect.Field f = friends.getClass().getDeclaredField("friends");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) f.get(friends);
            return list != null && list.contains(pl.getName().getString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Tracks crystals we place ourselves so "Ignore Own" can skip them. */
    private void trackOwnPlacement() {
        try {
            ItemStack held = mc.player.getMainHandStack();
            boolean holdingCrystal = !held.isEmpty()
                    && Registries.ITEM.getId(held.getItem()).getPath().equals("end_crystal");
            if (holdingCrystal && mc.options.useKey.isPressed()) {
                ownPlaceTicks = 10;
            }
        } catch (Throwable ignored) {
        }
        if (ownPlaceTicks > 0) ownPlaceTicks--;
    }

    private void rememberOwnCrystals() {
        if (ownPlaceTicks <= 0) return;
        try {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof EndCrystalEntity)) continue;
                if (mc.player.distanceTo(e) <= 5.0) ownCrystals.add(e.getId());
            }
        } catch (Throwable ignored) {
        }
        if (ownCrystals.size() > 64) ownCrystals.clear();
    }

    private int weaponSlot() {
        for (int i = 0; i < 9; i++) {
            try {
                String path = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem()).getPath();
                if (path.endsWith("_sword") || path.endsWith("_axe") || path.contains("pickaxe")) return i;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private void rotateTo(Entity crystal) {
        try {
            Vec3d eye = mc.player.getEyePos();
            double dx = crystal.getX() - eye.x;
            double dy = crystal.getY() + 0.5 - eye.y;
            double dz = crystal.getZ() - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);

            float wantYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

            float speed = rotationSpeed.getValue().floatValue() * 0.7f;
            float yawDiff = MathHelper.wrapDegrees(wantYaw - mc.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees(wantPitch - mc.player.getPitch());

            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDiff, -speed, speed));
            mc.player.setPitch(MathHelper.clamp(
                    mc.player.getPitch() + MathHelper.clamp(pitchDiff, -speed, speed), -90.0f, 90.0f));
        } catch (Throwable ignored) {
        }
    }

    private boolean lookingAt(Entity crystal) {
        try {
            Vec3d eye = mc.player.getEyePos();
            Vec3d look = mc.player.getRotationVec(1.0f);
            Vec3d aim = new Vec3d(crystal.getX(), crystal.getY() + 0.5, crystal.getZ()).subtract(eye).normalize();
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(aim), -1.0, 1.0)));
            return angle < 25.0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        trackOwnPlacement();
        rememberOwnCrystals();

        if (!mc.player.isAlive()) return;
        if (pauseHealth.getValue() > 0 && mc.player.getHealth() <= pauseHealth.getValue()) return;
        if (pauseOnUse.isEnabled() && mc.player.isUsingItem()) return;
        if (pauseOnMine.isEnabled() && mc.interactionManager.isBreakingBlock()) return;
        if (pauseInInventory.isEnabled() && mc.currentScreen != null) return;
        if (onlyWhileStanding.isEnabled()
                && (Math.abs(mc.player.getVelocity().x) > 0.01 || Math.abs(mc.player.getVelocity().z) > 0.01)) {
            return;
        }

        if (switchCooldown > 0) {
            switchCooldown--;
            return;
        }
        if (breakCooldown > 0) {
            breakCooldown--;
            return;
        }

        // Gather the crystals worth breaking.
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof LivingEntity living && passesFilter(living)) targets.add(living);
        }

        double range = breakRange.getValue();
        double minThreat = ignoreIfSafe.isEnabled() ? minThreatDamage.getValue() : 0.0;
        int minAge = ticksExisted.getValue().intValue();

        EndCrystalEntity best = null;
        double bestScore = -1.0;
        double bestSelfDamage = 0.0;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity crystal) || !crystal.isAlive()) continue;
            if (crystal.age < minAge) continue;
            if (ignoreOwn.isEnabled() && ownCrystals.contains(crystal.getId())) continue;

            double dist = mc.player.distanceTo(crystal);
            if (dist > range) continue;
            if (dist > wallsRange.getValue() && !mc.player.canSee(crystal)) continue;

            double selfDamage = damage(distanceTo(crystal, mc.player));
            if (selfDamage < minThreat) continue;
            if (antiSuicide.isEnabled() && selfDamage >= mc.player.getHealth()) continue;
            if (maxSelfDamage.getValue() > 0 && selfDamage > maxSelfDamage.getValue()) continue;

            // How much would this crystal hurt the best nearby target?
            double targetDamage = 0.0;
            for (LivingEntity t : targets) {
                if (mc.player.distanceTo(t) > targetRange.getValue()) continue;
                targetDamage = Math.max(targetDamage, damage(distanceTo(crystal, t)));
            }
            if (targetDamage < minTargetDamage.getValue()) continue;

            double score = switch (prioritize.getValue()) {
                case "Nearest" -> -dist;
                case "Most Damage" -> targetDamage;
                case "Lowest Health" -> {
                    double lowest = 20.0;
                    for (LivingEntity t : targets) lowest = Math.min(lowest, t.getHealth());
                    yield targetDamage + (20.0 - lowest);
                }
                default -> selfDamage + targetDamage;
            };

            if (score > bestScore) {
                bestScore = score;
                best = crystal;
                bestSelfDamage = selfDamage;
            }
        }

        current = best;
        if (best == null) return;

        // Optionally switch to a weapon first.
        if (autoSwitch.isEnabled()) {
            int slot = weaponSlot();
            if (slot != -1) {
                int cur = mc.player.getInventory().getSelectedSlot();
                if (cur != slot) {
                    if (prevSlot == -1) prevSlot = cur;
                    try {
                        mc.player.getInventory().setSelectedSlot(slot);
                    } catch (Throwable ignored) {
                    }
                    switchCooldown = switchDelay.getValue().intValue();
                    return;
                }
            }
        }

        if (rotate.isEnabled()) {
            rotateTo(best);
            if (strictRotation.isEnabled() && !lookingAt(best)) return;
        }

        boolean ok = breakCrystal(best, bestSelfDamage);

        if (switchBack.isEnabled() && prevSlot != -1) {
            try {
                mc.player.getInventory().setSelectedSlot(prevSlot);
            } catch (Throwable ignored) {
            }
            prevSlot = -1;
        }

        if (ok) {
            broken++;
            breakCooldown = breakDelay.random();
            if (notifySound.isEnabled()) {
                try {
                    mc.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.6f);
                } catch (Throwable ignored) {
                }
            }
            if (chatLog.isEnabled() && mc.inGameHud != null) {
                try {
                    mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                            String.format("§c[AntiCrystal] §fbreak §7%.1f§f self dmg", bestSelfDamage)));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean breakCrystal(EndCrystalEntity crystal, double selfDamage) {
        try {
            mc.interactionManager.attackEntity(mc.player, crystal);
            switch (swingMode.getValue()) {
                case "Client" -> mc.player.swingHand(Hand.MAIN_HAND);
                case "Server" -> mc.getNetworkHandler().sendPacket(
                        new net.minecraft.network.packet.c2s.play.HandSwingC2SPacket(Hand.MAIN_HAND));
                default -> { }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        if (showRange.isEnabled()) {
            drawRangeCircle(context, tickDelta);
        }

        if (current != null && showTarget.isEnabled()) {
            Vec3d pos = ProjectionUtil.lerpPos(current, tickDelta).add(0.0, 0.4, 0.0);
            int[] s = ProjectionUtil.project(pos, tickDelta);
            if (s != null) {
                int col = targetColor.getValue();
                RenderUtils.drawBorder(context, s[0] - 10, s[1] - 10, s[0] + 10, s[1] + 10, 1, col);
                if (showDamage.isEnabled()) {
                    double dmg = damage(distanceTo(current, mc.player));
                    RenderUtils.drawText(context, mc.textRenderer,
                            String.format("§c%.1f", dmg), s[0] + 13, s[1] - 4, 0xFFFFFFFF, true);
                }
            }
        }

        if (showStatus.isEnabled() && mc.getWindow() != null) {
            String info = "AntiCrystal [" + broken + "]";
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            RenderUtils.drawText(context, mc.textRenderer, info,
                    w / 2 - mc.textRenderer.getWidth(info) / 2, h / 2 + 34, 0xFFFFFFFF, true);
        }
    }

    private void drawRangeCircle(DrawContext context, float tickDelta) {
        try {
            double r = breakRange.getValue();
            Vec3d c = ProjectionUtil.lerpPos(mc.player, tickDelta);
            int col = rangeColor.getValue();
            int segments = 48;
            int[] prev = null;
            for (int i = 0; i <= segments; i++) {
                double a = (Math.PI * 2.0) * i / segments;
                Vec3d p = new Vec3d(c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r);
                int[] sc = ProjectionUtil.project(p, tickDelta);
                if (prev != null && sc != null) {
                    RenderUtils.drawLine(context, prev[0], prev[1], sc[0], sc[1], col, 1.0f);
                }
                prev = sc;
            }
        } catch (Throwable ignored) {
        }
    }
}
