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
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Places and detonates beds next to a target.
 *
 * <p>In the Nether and the End a bed explodes the moment it is used, which is
 * what makes it the strongest close-range weapon there. This module finds a
 * spot next to the target, places the bed, and then detonates it by
 * right-clicking or by breaking the block - both modes are configurable, as is
 * everything else: damage limits, rotation, auto-switch, pause conditions and
 * a full render section.</p>
 */
public class AutoBed extends Module {

    // ------------------------------------------------------------------
    //  Targeting
    // ------------------------------------------------------------------
    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players", "Target other players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", "Target hostile mobs", false);
    public final BooleanSetting targetAnimals = new BooleanSetting("Target Animals", "Target passive animals", false);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Never target friends", true);
    public final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible", "Ignore invisible entities", true);
    public final NumberSetting targetRange = new NumberSetting("Target Range", "Range for finding targets", 8.0, 1.0, 16.0, 0.5);
    public final ModeSetting prioritize = new ModeSetting("Prioritize", "Which target to pick", "Nearest", "Nearest", "Lowest Health", "Most Damage");

    // ------------------------------------------------------------------
    //  Placement
    // ------------------------------------------------------------------
    public final NumberSetting placeRange = new NumberSetting("Place Range", "Maximum distance to place a bed", 4.5, 1.0, 6.0, 0.1);
    public final IntRangeSetting placeDelay = new IntRangeSetting("Place Delay", "Randomised ticks between placements", 2, 4, 0, 40);
    public final BooleanSetting onlyAbove = new BooleanSetting("Only Above", "Only place on top of solid blocks", true);
    public final BooleanSetting onlyInNether = new BooleanSetting("Only In Nether", "Only work in the Nether and the End", true);
    public final BooleanSetting airPlace = new BooleanSetting("Air Place", "Allow placing without a supporting block", false);
    public final BooleanSetting rotate = new BooleanSetting("Rotate", "Rotate towards the placement", true);
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", "How fast the aim moves", 30.0, 1.0, 180.0, 1.0);
    public final BooleanSetting strictRotation = new BooleanSetting("Strict Rotation", "Only act once the crosshair is on the spot", false);
    public final BooleanSetting swing = new BooleanSetting("Swing", "Swing the hand when placing", true);

    // ------------------------------------------------------------------
    //  Detonation
    // ------------------------------------------------------------------
    public final ModeSetting detonateMode = new ModeSetting("Detonate Mode", "How the placed bed is set off", "Right Click", "Right Click", "Break", "Both");
    public final IntRangeSetting detonateDelay = new IntRangeSetting("Detonate Delay", "Ticks to wait before detonating", 1, 2, 0, 20);
    public final BooleanSetting sequenced = new BooleanSetting("Sequenced", "Only place again after the previous bed exploded", true);
    public final BooleanSetting ignoreOwn = new BooleanSetting("Ignore Own", "Never detonate a bed you did not place", true);

    // ------------------------------------------------------------------
    //  Damage limits
    // ------------------------------------------------------------------
    public final NumberSetting minDamage = new NumberSetting("Min Damage", "Only place when the target takes at least this", 4.0, 0.0, 20.0, 0.5);
    public final NumberSetting maxSelfDamage = new NumberSetting("Max Self Damage", "Never place when you would take more than this", 6.0, 0.0, 20.0, 0.5);
    public final BooleanSetting antiSuicide = new BooleanSetting("Anti Suicide", "Never place a bed that could kill you", true);
    public final NumberSetting predictTicks = new NumberSetting("Predict Ticks", "How many ticks of movement to predict", 0.0, 0.0, 10.0, 1.0);

    // ------------------------------------------------------------------
    //  Auto switch
    // ------------------------------------------------------------------
    public final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", "Switch to a bed automatically", true);
    public final BooleanSetting switchBack = new BooleanSetting("Switch Back", "Return to the previous slot afterwards", true);
    public final NumberSetting switchDelay = new NumberSetting("Switch Delay", "Ticks to wait after switching", 1.0, 0.0, 10.0, 1.0);

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
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", "Draw a marker on the target", true);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Target marker colour", ColorUtils.rgba(255, 60, 60, 255));
    public final BooleanSetting showPlace = new BooleanSetting("Show Place Spot", "Draw a marker where the bed will go", true);
    public final ColorSetting placeColor = new ColorSetting("Place Color", "Placement marker colour", ColorUtils.rgba(255, 200, 0, 255));
    public final BooleanSetting showDamage = new BooleanSetting("Show Damage", "Show the predicted damage", true);
    public final BooleanSetting showRange = new BooleanSetting("Show Range", "Draw a circle at the place range", false);
    public final ColorSetting rangeColor = new ColorSetting("Range Color", "Range circle colour", ColorUtils.rgba(255, 255, 255, 90));
    public final BooleanSetting showStatus = new BooleanSetting("Show Status", "Show the module state near the screen centre", false);

    // ------------------------------------------------------------------
    //  Misc
    // ------------------------------------------------------------------
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log placements in chat", false);
    public final BooleanSetting notifySound = new BooleanSetting("Notify Sound", "Play a click when a bed explodes", false);

    private LivingEntity target;
    private BlockPos placedBed;
    private int placeCooldown = 0;
    private int detonateCooldown = 0;
    private int switchCooldown = 0;
    private int prevSlot = -1;
    private int placed = 0;
    private BlockPos previewSpot;

    public AutoBed() {
        super("AutoBed", "Places and detonates beds next to a target", Category.COMBAT);

        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(targetAnimals);
        addSetting(ignoreFriends);
        addSetting(ignoreInvisible);
        addSetting(targetRange);
        addSetting(prioritize);

        addSetting(placeRange);
        addSetting(placeDelay);
        addSetting(onlyAbove);
        addSetting(onlyInNether);
        addSetting(airPlace);
        addSetting(rotate);
        addSetting(rotationSpeed);
        addSetting(strictRotation);
        addSetting(swing);

        addSetting(detonateMode);
        addSetting(detonateDelay);
        addSetting(sequenced);
        addSetting(ignoreOwn);

        addSetting(minDamage);
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
        addSetting(showPlace);
        addSetting(placeColor);
        addSetting(showDamage);
        addSetting(showRange);
        addSetting(rangeColor);
        addSetting(showStatus);

        addSetting(chatLog);
        addSetting(notifySound);

        rotationSpeed.visibleIf(rotate::isEnabled);
        strictRotation.visibleIf(rotate::isEnabled);
        switchBack.visibleIf(autoSwitch::isEnabled);
        switchDelay.visibleIf(autoSwitch::isEnabled);
        targetColor.visibleIf(showTarget::isEnabled);
        placeColor.visibleIf(showPlace::isEnabled);
        rangeColor.visibleIf(showRange::isEnabled);
    }

    @Override
    public void onDisable() {
        target = null;
        placedBed = null;
        previewSpot = null;
        placeCooldown = 0;
        detonateCooldown = 0;
        switchCooldown = 0;
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

    /** Approximate bed explosion falloff (power 5, ~10 max damage). */
    private static double damage(double distance) {
        double d = 1.0 - distance / 10.0;
        return d <= 0.0 ? 0.0 : d * d * 10.0;
    }

    private double distanceTo(Vec3d point, LivingEntity entity) {
        double ex = entity.getX(), ey = entity.getY() + entity.getHeight() / 2.0, ez = entity.getZ();
        double pt = predictTicks.getValue();
        if (pt > 0) {
            Vec3d v = entity.getVelocity();
            ex += v.x * pt;
            ey += v.y * pt;
            ez += v.z * pt;
        }
        double dx = point.x - ex;
        double dy = point.y - ey;
        double dz = point.z - ez;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean inValidDimension() {
        if (!onlyInNether.isEnabled()) return true;
        try {
            String path = mc.world.getRegistryKey().getValue().getPath();
            return path.contains("nether") || path.contains("end");
        } catch (Throwable ignored) {
            return true;
        }
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

    private int bedSlot() {
        for (int i = 0; i < 9; i++) {
            try {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty()) continue;
                if (Registries.ITEM.getId(s.getItem()).getPath().endsWith("_bed")) return i;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private boolean isBed(BlockPos pos) {
        try {
            return Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).getPath().endsWith("_bed");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void rotateTo(Vec3d point) {
        try {
            Vec3d eye = mc.player.getEyePos();
            double dx = point.x - eye.x;
            double dy = point.y - eye.y;
            double dz = point.z - eye.z;
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

    private boolean lookingAt(Vec3d point) {
        try {
            Vec3d eye = mc.player.getEyePos();
            Vec3d look = mc.player.getRotationVector();
            Vec3d aim = point.subtract(eye).normalize();
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(aim), -1.0, 1.0)));
            return angle < 25.0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Finds the best empty spot next to the target that has something to place
     * the bed against.
     */
    private BlockPos findPlaceSpot(LivingEntity t, double range) {
        BlockPos base = t.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dy = 0; dy >= -2; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = base.add(dx, dy, dz);
                    try {
                        if (!mc.world.isAir(p)) continue;
                        if (!mc.world.isAir(p.up())) continue;

                        Vec3d center = new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                        double pd = mc.player.getEyePos().distanceTo(center);
                        if (pd > range) continue;

                        BlockPos support = findSupport(p);
                        if (support == null) continue;
                        if (onlyAbove.isEnabled() && support.getY() != p.getY() - 1) continue;

                        if (pd < bestDist) {
                            bestDist = pd;
                            best = p;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return best;
    }

    /** @return a neighbouring solid block the bed can be placed against. */
    private BlockPos findSupport(BlockPos p) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, Direction.UP};
        for (Direction d : order) {
            try {
                BlockPos n = p.offset(d);
                if (!mc.world.isAir(n) && mc.world.getBlockState(n).isSolidBlock(mc.world, n)) return n;
            } catch (Throwable ignored) {
            }
        }
        return airPlace.isEnabled() ? p : null;
    }

    private Direction faceTowards(BlockPos support, BlockPos p) {
        for (Direction d : Direction.values()) {
            if (support.offset(d).equals(p)) return d;
        }
        return Direction.UP;
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!inValidDimension()) return;

        if (!mc.player.isAlive()) return;
        if (pauseHealth.getValue() > 0 && mc.player.getHealth() <= pauseHealth.getValue()) return;
        if (pauseOnUse.isEnabled() && mc.player.isUsingItem()) return;
        if (pauseOnMine.isEnabled() && mc.interactionManager.isBreakingBlock()) return;
        if (pauseInInventory.isEnabled() && mc.currentScreen != null) return;

        // Detonate a bed we already placed.
        if (placedBed != null) {
            if (!isBed(placedBed)) {
                placedBed = null;
                previewSpot = null;
            } else if (detonateCooldown > 0) {
                detonateCooldown--;
            } else {
                detonate(placedBed);
                placedBed = null;
                previewSpot = null;
                placeCooldown = placeDelay.random();
                return;
            }
        }

        if (sequenced.isEnabled() && placedBed != null) return;

        if (switchCooldown > 0) {
            switchCooldown--;
            return;
        }
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        pickTarget();
        if (target == null) {
            previewSpot = null;
            return;
        }

        BlockPos spot = findPlaceSpot(target, placeRange.getValue());
        previewSpot = spot;
        if (spot == null) return;

        double targetDamage = damage(distanceTo(new Vec3d(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5), target));
        if (targetDamage < minDamage.getValue()) return;

        double selfDamage = damage(distanceTo(new Vec3d(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5), mc.player));
        if (antiSuicide.isEnabled() && selfDamage >= mc.player.getHealth()) return;
        if (maxSelfDamage.getValue() > 0 && selfDamage > maxSelfDamage.getValue()) return;

        BlockPos support = findSupport(spot);
        if (support == null) return;

        int slot = bedSlot();
        if (slot == -1) return;

        if (autoSwitch.isEnabled()) {
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

        Vec3d hitPoint = new Vec3d(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5);
        if (rotate.isEnabled()) {
            rotateTo(hitPoint);
            if (strictRotation.isEnabled() && !lookingAt(hitPoint)) return;
        }

        if (placeBed(spot, support)) {
            placedBed = spot;
            detonateCooldown = detonateDelay.random();
            placed++;
            if (notifySound.isEnabled()) {
                try {
                    mc.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
                } catch (Throwable ignored) {
                }
            }
            if (chatLog.isEnabled() && mc.inGameHud != null) {
                try {
                    mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                            String.format("§6[AutoBed] §fplaced §7(%.1f target dmg)", targetDamage)));
                } catch (Throwable ignored) {
                }
            }
        }

        if (switchBack.isEnabled() && prevSlot != -1) {
            try {
                mc.player.getInventory().setSelectedSlot(prevSlot);
            } catch (Throwable ignored) {
            }
            prevSlot = -1;
        }
    }

    private void pickTarget() {
        LivingEntity best = null;
        double bestScore = -Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living) || !passesFilter(living)) continue;
            double d = mc.player.distanceTo(living);
            if (d > targetRange.getValue()) continue;

            double score = switch (prioritize.getValue()) {
                case "Lowest Health" -> 40.0 - living.getHealth() - d;
                case "Most Damage" -> damage(d) - d * 0.1;
                default -> -d;
            };
            if (score > bestScore) {
                bestScore = score;
                best = living;
            }
        }
        target = best;
    }

    private boolean placeBed(BlockPos spot, BlockPos support) {
        try {
            Direction face = faceTowards(support, spot);
            Vec3d hit = new Vec3d(
                    support.getX() + 0.5 + face.getOffsetX() * 0.5,
                    support.getY() + 0.5 + face.getOffsetY() * 0.5,
                    support.getZ() + 0.5 + face.getOffsetZ() * 0.5);
            BlockHitResult bhr = new BlockHitResult(hit, face, support, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            if (swing.isEnabled()) mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void detonate(BlockPos bed) {
        try {
            switch (detonateMode.getValue()) {
                case "Break" -> {
                    mc.interactionManager.updateBlockBreakingProgress(bed, Direction.UP);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
                case "Both" -> {
                    BlockHitResult bhr = new BlockHitResult(
                            new Vec3d(bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5),
                            Direction.UP, bed, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    mc.interactionManager.updateBlockBreakingProgress(bed, Direction.UP);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
                default -> {
                    BlockHitResult bhr = new BlockHitResult(
                            new Vec3d(bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5),
                            Direction.UP, bed, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        if (showRange.isEnabled()) drawRangeCircle(context, tickDelta);

        if (target != null && showTarget.isEnabled()) {
            int[] s = ProjectionUtil.project(
                    ProjectionUtil.lerpPos(target, tickDelta).add(0.0, target.getHeight() + 0.4, 0.0), tickDelta);
            if (s != null) {
                int col = targetColor.getValue();
                RenderUtils.drawBorder(context, s[0] - 12, s[1] - 4, s[0] + 12, s[1] + 4, 1, col);
                if (showDamage.isEnabled()) {
                    double dmg = damage(distanceTo(
                            ProjectionUtil.lerpPos(target, tickDelta), target));
                    RenderUtils.drawText(context, mc.textRenderer,
                            String.format("§c%.1f", dmg), s[0] + 15, s[1] - 4, 0xFFFFFFFF, true);
                }
            }
        }

        if (previewSpot != null && showPlace.isEnabled()) {
            RenderUtils.drawBlockOutline(context, previewSpot, placeColor.getValue(), tickDelta, 1);
        }

        if (showStatus.isEnabled() && mc.getWindow() != null) {
            String info = "AutoBed [" + placed + "]";
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            RenderUtils.drawText(context, mc.textRenderer, info,
                    w / 2 - mc.textRenderer.getWidth(info) / 2, h / 2 + 46, 0xFFFFFFFF, true);
        }
    }

    private void drawRangeCircle(DrawContext context, float tickDelta) {
        try {
            double r = placeRange.getValue();
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
