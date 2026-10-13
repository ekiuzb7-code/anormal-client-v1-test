package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Places and detonates end crystals around a target.
 *
 * <p>Four interchangeable implementations, each mirroring a different client:
 * <ul>
 *   <li><b>V1</b> - Meteor (target-range, damage limits, rotate, auto-switch, place/break modules)</li>
 *   <li><b>V2</b> - Wurst (Range, AutoPlace, CheckLOS - the minimal implementation)</li>
 *   <li><b>V3</b> - LiquidBounce (separate Place/Destroy submodules, walls ranges, swing/switch modes)</li>
 *   <li><b>V4</b> - Vape (damage limits, anti-suicide, rotate, auto-switch, pause options)</li>
 * </ul>
 */
public class AutoCrystal extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2", "V3", "V4");

    // ---------------- Target filter (shared) ----------------
    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players", "Target other players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", "Target hostile mobs (zombies, wardens, ...)", false);
    public final BooleanSetting targetAnimals = new BooleanSetting("Target Animals", "Target passive animals", false);
    public final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible", "Ignore invisible targets", true);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Never target friends", true);

    // ---------------- V1 - Meteor ----------------
    public final NumberSetting v1TargetRange = new NumberSetting("V1 Target Range", "Range for finding targets", 10.0, 1.0, 16.0, 0.5);
    public final BooleanSetting v1PredictMovement = new BooleanSetting("V1 Predict Movement", "Lead moving targets", true);
    public final NumberSetting v1MinDamage = new NumberSetting("V1 Min Damage", "Minimum damage dealt to the target", 6.0, 0.0, 20.0, 0.5);
    public final NumberSetting v1MaxDamage = new NumberSetting("V1 Max Damage", "Maximum damage dealt to the target", 20.0, 0.0, 36.0, 0.5);
    public final BooleanSetting v1AntiSuicide = new BooleanSetting("V1 Anti Suicide", "Never take lethal self damage", true);
    public final BooleanSetting v1IgnoreNakeds = new BooleanSetting("V1 Ignore Nakeds", "Ignore targets without armor", false);
    public final BooleanSetting v1Rotate = new BooleanSetting("V1 Rotate", "Rotate towards the crystal", true);
    public final BooleanSetting v1AutoSwitch = new BooleanSetting("V1 Auto Switch", "Switch to crystals automatically", true);
    public final NumberSetting v1SwitchDelay = new NumberSetting("V1 Switch Delay", "Ticks between switch actions", 1.0, 0.0, 10.0, 1.0);
    public final BooleanSetting v1Place = new BooleanSetting("V1 Place", "Place crystals", true);
    public final NumberSetting v1PlaceDelay = new NumberSetting("V1 Place Delay", "Ticks between placements", 1.0, 0.0, 10.0, 1.0);
    public final NumberSetting v1PlaceRange = new NumberSetting("V1 Place Range", "Placement range", 4.5, 1.0, 6.0, 0.5);
    public final BooleanSetting v1Break = new BooleanSetting("V1 Break", "Break crystals", true);
    public final NumberSetting v1BreakDelay = new NumberSetting("V1 Break Delay", "Ticks between breaks", 1.0, 0.0, 10.0, 1.0);
    public final NumberSetting v1BreakRange = new NumberSetting("V1 Break Range", "Breaking range", 4.5, 1.0, 6.0, 0.5);
    public final BooleanSetting v1OnlyOwn = new BooleanSetting("V1 Only Own", "Only break your own crystals", false);
    public final NumberSetting v1TicksExisted = new NumberSetting("V1 Ticks Existed", "Min age of a crystal before breaking", 0.0, 0.0, 20.0, 1.0);
    public final NumberSetting v1AttackFrequency = new NumberSetting("V1 Attack Frequency", "Attacks per second cap", 20.0, 1.0, 20.0, 1.0);
    public final BooleanSetting v1PauseOnUse = new BooleanSetting("V1 Pause On Use", "Pause while using an item", true);
    public final BooleanSetting v1PauseOnMine = new BooleanSetting("V1 Pause On Mine", "Pause while mining", true);
    public final NumberSetting v1PauseHealth = new NumberSetting("V1 Pause Health", "Pause below this health", 0.0, 0.0, 20.0, 1.0);
    public final ModeSetting v1SwingMode = new ModeSetting("V1 Swing", "Swing hand mode", "Client", "Client", "Server", "None");

    // ---------------- V2 - Wurst ----------------
    public final NumberSetting v2Range = new NumberSetting("V2 Range", "Range for placing and breaking", 4.5, 1.0, 6.0, 0.5);
    public final BooleanSetting v2AutoPlace = new BooleanSetting("V2 Auto Place", "Automatically place crystals", true);
    public final BooleanSetting v2CheckLOS = new BooleanSetting("V2 Check LOS", "Require line of sight", true);

    // ---------------- V3 - LiquidBounce ----------------
    public final ModeSetting v3PlaceSwing = new ModeSetting("V3 Place Swing", "Swing mode when placing", "DoNotHide", "DoNotHide", "Hide", "None");
    public final ModeSetting v3PlaceSwitch = new ModeSetting("V3 Place Switch", "Switch mode when placing", "Silent", "Silent", "Normal", "None");
    public final BooleanSetting v3OldVersion = new BooleanSetting("V3 1_12_2", "Use 1.12.2 placement behaviour", false);
    public final NumberSetting v3PlaceDelay = new NumberSetting("V3 Place Delay", "Delay between placements (ms)", 0.0, 0.0, 1000.0, 10.0);
    public final NumberSetting v3PlaceRange = new NumberSetting("V3 Place Range", "Placement range", 4.5, 1.0, 6.0, 0.5);
    public final NumberSetting v3PlaceWallsRange = new NumberSetting("V3 Place Walls Range", "Placement range through walls", 4.5, 0.0, 6.0, 0.5);
    public final BooleanSetting v3OnlyAbove = new BooleanSetting("V3 Only Above", "Only place on top faces", false);
    public final BooleanSetting v3Sequenced = new BooleanSetting("V3 Sequenced", "Only place after a successful break", false);
    public final BooleanSetting v3Jitter = new BooleanSetting("V3 Jitter", "Add rotation jitter", false);
    public final ModeSetting v3BreakSwing = new ModeSetting("V3 Break Swing", "Swing mode when breaking", "DoNotHide", "DoNotHide", "Hide", "None");
    public final NumberSetting v3BreakDelay = new NumberSetting("V3 Break Delay", "Delay between breaks (ms)", 0.0, 0.0, 1000.0, 10.0);
    public final NumberSetting v3BreakRange = new NumberSetting("V3 Break Range", "Breaking range", 4.5, 1.0, 6.0, 0.5);
    public final NumberSetting v3BreakWallsRange = new NumberSetting("V3 Break Walls Range", "Breaking range through walls", 4.5, 0.0, 6.0, 0.5);
    public final BooleanSetting v3PrioritizeVisibleFaces = new BooleanSetting("V3 Prioritize Visible Faces", "Prefer crystals you can see", false);

    // ---------------- V4 - Vape ----------------
    public final NumberSetting v4TargetRange = new NumberSetting("V4 Target Range", "Range for finding targets", 8.0, 1.0, 16.0, 0.5);
    public final NumberSetting v4MinDamage = new NumberSetting("V4 Min Damage", "Minimum damage dealt to the target", 4.0, 0.0, 20.0, 0.5);
    public final NumberSetting v4MaxSelfDamage = new NumberSetting("V4 Max Self Damage", "Maximum self damage allowed", 6.0, 0.0, 20.0, 0.5);
    public final BooleanSetting v4AntiSuicide = new BooleanSetting("V4 Anti Suicide", "Never take lethal self damage", true);
    public final BooleanSetting v4Place = new BooleanSetting("V4 Place", "Place crystals", true);
    public final NumberSetting v4PlaceDelay = new NumberSetting("V4 Place Delay", "Ticks between placements", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting v4Break = new BooleanSetting("V4 Break", "Break crystals", true);
    public final NumberSetting v4BreakDelay = new NumberSetting("V4 Break Delay", "Ticks between breaks", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting v4Rotate = new BooleanSetting("V4 Rotate", "Rotate towards the crystal", true);
    public final NumberSetting v4AimSpeed = new NumberSetting("V4 Aim Speed", "Rotation speed", 8.0, 1.0, 20.0, 0.5);
    public final BooleanSetting v4AutoSwitch = new BooleanSetting("V4 Auto Switch", "Switch to crystals automatically", true);
    public final BooleanSetting v4PauseOnUse = new BooleanSetting("V4 Pause On Use", "Pause while using an item", true);

    // ---------------- Shared render ----------------
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", "Highlight the current target", true);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Target highlight color", ColorUtils.rgba(0, 230, 255, 255));
    public final BooleanSetting centerScreen = new BooleanSetting("Center Screen", "Show crystal count near the screen centre", true);

    private int breakCooldown = 0;
    private int placeCooldown = 0;
    private long lastPlaceMs = 0L;
    private long lastBreakMs = 0L;
    private LivingEntity target;
    private int crystalCount = 0;
    private int lastCrystalId = -1;

    public AutoCrystal() {
        super("AutoCrystal", "Places and detonates end crystals around targets", Category.COMBAT);
        addSetting(version);
        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(targetAnimals);
        addSetting(ignoreInvisible);
        addSetting(ignoreFriends);

        addSetting(v1TargetRange);
        addSetting(v1PredictMovement);
        addSetting(v1MinDamage);
        addSetting(v1MaxDamage);
        addSetting(v1AntiSuicide);
        addSetting(v1IgnoreNakeds);
        addSetting(v1Rotate);
        addSetting(v1AutoSwitch);
        addSetting(v1SwitchDelay);
        addSetting(v1Place);
        addSetting(v1PlaceDelay);
        addSetting(v1PlaceRange);
        addSetting(v1Break);
        addSetting(v1BreakDelay);
        addSetting(v1BreakRange);
        addSetting(v1OnlyOwn);
        addSetting(v1TicksExisted);
        addSetting(v1AttackFrequency);
        addSetting(v1PauseOnUse);
        addSetting(v1PauseOnMine);
        addSetting(v1PauseHealth);
        addSetting(v1SwingMode);

        addSetting(v2Range);
        addSetting(v2AutoPlace);
        addSetting(v2CheckLOS);

        addSetting(v3PlaceSwing);
        addSetting(v3PlaceSwitch);
        addSetting(v3OldVersion);
        addSetting(v3PlaceDelay);
        addSetting(v3PlaceRange);
        addSetting(v3PlaceWallsRange);
        addSetting(v3OnlyAbove);
        addSetting(v3Sequenced);
        addSetting(v3Jitter);
        addSetting(v3BreakSwing);
        addSetting(v3BreakDelay);
        addSetting(v3BreakRange);
        addSetting(v3BreakWallsRange);
        addSetting(v3PrioritizeVisibleFaces);

        addSetting(v4TargetRange);
        addSetting(v4MinDamage);
        addSetting(v4MaxSelfDamage);
        addSetting(v4AntiSuicide);
        addSetting(v4Place);
        addSetting(v4PlaceDelay);
        addSetting(v4Break);
        addSetting(v4BreakDelay);
        addSetting(v4Rotate);
        addSetting(v4AimSpeed);
        addSetting(v4AutoSwitch);
        addSetting(v4PauseOnUse);

        addSetting(showTarget);
        addSetting(targetColor);
        addSetting(centerScreen);

        // Only the settings of the selected version are shown.
        for (var s : new com.anormal.client.setting.Setting[]{
                v1TargetRange, v1PredictMovement, v1MinDamage, v1MaxDamage, v1AntiSuicide, v1IgnoreNakeds,
                v1Rotate, v1AutoSwitch, v1SwitchDelay, v1Place, v1PlaceDelay, v1PlaceRange, v1Break,
                v1BreakDelay, v1BreakRange, v1OnlyOwn, v1TicksExisted, v1AttackFrequency, v1PauseOnUse,
                v1PauseOnMine, v1PauseHealth, v1SwingMode}) {
            s.visibleIf(() -> version.is("V1"));
        }
        for (var s : new com.anormal.client.setting.Setting[]{v2Range, v2AutoPlace, v2CheckLOS}) {
            s.visibleIf(() -> version.is("V2"));
        }
        for (var s : new com.anormal.client.setting.Setting[]{
                v3PlaceSwing, v3PlaceSwitch, v3OldVersion, v3PlaceDelay, v3PlaceRange, v3PlaceWallsRange,
                v3OnlyAbove, v3Sequenced, v3Jitter, v3BreakSwing, v3BreakDelay, v3BreakRange,
                v3BreakWallsRange, v3PrioritizeVisibleFaces}) {
            s.visibleIf(() -> version.is("V3"));
        }
        for (var s : new com.anormal.client.setting.Setting[]{
                v4TargetRange, v4MinDamage, v4MaxSelfDamage, v4AntiSuicide, v4Place, v4PlaceDelay,
                v4Break, v4BreakDelay, v4Rotate, v4AimSpeed, v4AutoSwitch, v4PauseOnUse}) {
            s.visibleIf(() -> version.is("V4"));
        }
    }

    // =====================================================================
    //  Version parameter bundle
    // =====================================================================
    private static final class Params {
        double targetRange, placeRange, breakRange;
        double minDamage, maxDamage, maxSelfDamage;
        boolean antiSuicide, place, brk, rotate, autoSwitch, pauseOnUse, pauseOnMine, onlyOwn, predict, checkLOS;
        int placeDelay, breakDelay;
        double aimSpeed;
        int ticksExisted;
        String swingMode;
    }

    private Params params() {
        Params p = new Params();
        if (version.is("V1")) {
            p.targetRange = v1TargetRange.getValue();
            p.placeRange = v1PlaceRange.getValue();
            p.breakRange = v1BreakRange.getValue();
            p.minDamage = v1MinDamage.getValue();
            p.maxDamage = v1MaxDamage.getValue();
            p.maxSelfDamage = 6.0;
            p.antiSuicide = v1AntiSuicide.isEnabled();
            p.place = v1Place.isEnabled();
            p.brk = v1Break.isEnabled();
            p.rotate = v1Rotate.isEnabled();
            p.autoSwitch = v1AutoSwitch.isEnabled();
            p.pauseOnUse = v1PauseOnUse.isEnabled();
            p.pauseOnMine = v1PauseOnMine.isEnabled();
            p.onlyOwn = v1OnlyOwn.isEnabled();
            p.predict = v1PredictMovement.isEnabled();
            p.placeDelay = v1PlaceDelay.getValue().intValue();
            p.breakDelay = v1BreakDelay.getValue().intValue();
            p.aimSpeed = 8.0;
            p.ticksExisted = v1TicksExisted.getValue().intValue();
            p.swingMode = v1SwingMode.getValue();
        } else if (version.is("V2")) {
            p.targetRange = v2Range.getValue() + 4.0;
            p.placeRange = v2Range.getValue();
            p.breakRange = v2Range.getValue();
            p.minDamage = 0.0;
            p.maxDamage = 36.0;
            p.maxSelfDamage = 20.0;
            p.antiSuicide = false;
            p.place = v2AutoPlace.isEnabled();
            p.brk = true;
            p.rotate = true;
            p.autoSwitch = true;
            p.pauseOnUse = true;
            p.pauseOnMine = false;
            p.onlyOwn = false;
            p.predict = false;
            p.checkLOS = v2CheckLOS.isEnabled();
            p.placeDelay = 1;
            p.breakDelay = 1;
            p.aimSpeed = 10.0;
            p.ticksExisted = 0;
            p.swingMode = "Client";
        } else if (version.is("V3")) {
            p.targetRange = v3PlaceRange.getValue() + 5.0;
            p.placeRange = v3PlaceRange.getValue();
            p.breakRange = v3BreakRange.getValue();
            p.minDamage = 0.0;
            p.maxDamage = 36.0;
            p.maxSelfDamage = 20.0;
            p.antiSuicide = true;
            p.place = true;
            p.brk = true;
            p.rotate = true;
            p.autoSwitch = !v3PlaceSwitch.is("None");
            p.pauseOnUse = true;
            p.pauseOnMine = false;
            p.onlyOwn = false;
            p.predict = true;
            p.placeDelay = (int) Math.ceil(v3PlaceDelay.getValue() / 50.0);
            p.breakDelay = (int) Math.ceil(v3BreakDelay.getValue() / 50.0);
            p.aimSpeed = 10.0;
            p.ticksExisted = 0;
            p.swingMode = v3PlaceSwing.getValue();
        } else {
            p.targetRange = v4TargetRange.getValue();
            p.placeRange = 4.5;
            p.breakRange = 4.5;
            p.minDamage = v4MinDamage.getValue();
            p.maxDamage = 36.0;
            p.maxSelfDamage = v4MaxSelfDamage.getValue();
            p.antiSuicide = v4AntiSuicide.isEnabled();
            p.place = v4Place.isEnabled();
            p.brk = v4Break.isEnabled();
            p.rotate = v4Rotate.isEnabled();
            p.autoSwitch = v4AutoSwitch.isEnabled();
            p.pauseOnUse = v4PauseOnUse.isEnabled();
            p.pauseOnMine = false;
            p.onlyOwn = false;
            p.predict = true;
            p.placeDelay = v4PlaceDelay.getValue().intValue();
            p.breakDelay = v4BreakDelay.getValue().intValue();
            p.aimSpeed = v4AimSpeed.getValue();
            p.ticksExisted = 0;
            p.swingMode = "Client";
        }
        return p;
    }

    @Override
    public void onEnable() {
        breakCooldown = 0;
        placeCooldown = 0;
        target = null;
        crystalCount = 0;
        lastCrystalId = -1;
        lastPlaceMs = 0L;
        lastBreakMs = 0L;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        Params p = params();

        if (version.is("V1") && mc.player.getHealth() <= v1PauseHealth.getValue()) {
            target = null;
            return;
        }
        if (p.pauseOnUse && mc.player.isUsingItem()) return;
        if (p.pauseOnMine && mc.interactionManager.isBreakingBlock()) return;

        if (breakCooldown > 0) breakCooldown--;
        if (placeCooldown > 0) placeCooldown--;

        target = selectTarget(p);
        if (target == null) return;

        // Break first: placing first starves the detonation of ticks.
        if (p.brk && breakCooldown <= 0 && tryBreak(p)) return;
        if (p.place && placeCooldown <= 0) tryPlace(p);
    }

    // ---------------------------------------------------------------------
    //  Target selection
    // ---------------------------------------------------------------------
    private LivingEntity selectTarget(Params p) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            if (ignoreInvisible.isEnabled() && living.isInvisible()) continue;

            if (living instanceof PlayerEntity pl) {
                if (!targetPlayers.isEnabled()) continue;
                if (mc.player.isTeammate(pl)) continue;
                if (ignoreFriends.isEnabled() && isFriend(pl)) continue;
                if (version.is("V1") && v1IgnoreNakeds.isEnabled() && pl.getArmor() == 0) continue;
            } else if (living instanceof HostileEntity) {
                if (!targetMobs.isEnabled()) continue;
            } else {
                if (!targetAnimals.isEnabled()) continue;
            }
            double dist = mc.player.distanceTo(living);
            if (dist > p.targetRange) continue;
            Vec3d aim = new Vec3d(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(
                    look.dotProduct(aim.subtract(eye).normalize()), -1.0, 1.0)));
            if (angle > 180.0) continue;
            if (dist < bestScore) {
                bestScore = dist;
                best = living;
            }
        }
        return best;
    }

    /** Friend check that mirrors the Friends module without hard-depending on it. */
    private boolean isFriend(PlayerEntity player) {
        try {
            var friends = com.anormal.client.module.ModuleManager.getModule(
                    com.anormal.client.module.impl.client.Friends.class);
            if (friends == null) return false;
            java.lang.reflect.Field f = friends.getClass().getDeclaredField("friends");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<String> list = (java.util.List<String>) f.get(friends);
            return list != null && list.contains(player.getName().getString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    //  Breaking
    // ---------------------------------------------------------------------
    private boolean tryBreak(Params p) {
        if (version.is("V3") && v3PrioritizeVisibleFaces.isEnabled()
                && !(mc.crosshairTarget instanceof BlockHitResult)) {
            // fall through: visible-face preference is only a tiebreaker here
        }

        EndCrystalEntity best = null;
        double bestScore = -1.0;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c) || !c.isAlive()) continue;
            if (p.onlyOwn && c.getId() != lastCrystalId) continue;
            double dist = mc.player.distanceTo(c);
            if (dist > p.breakRange + 1.0) continue;
            if (p.ticksExisted > 0 && c.age < p.ticksExisted) continue;

            double targetDamage = crystalDamage(distTo(c, target, p.predict));
            double selfDamage = crystalDamage(distTo(c, mc.player, false));
            if (targetDamage < p.minDamage || targetDamage > p.maxDamage) continue;
            if (p.antiSuicide && (selfDamage > p.maxSelfDamage || mc.player.getHealth() - selfDamage <= 0.0)) continue;

            double score = targetDamage - selfDamage * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }

        if (best == null) return false;
        if (p.rotate) rotateTo(best.getX(), best.getZ(), p.aimSpeed);
        mc.interactionManager.attackEntity(mc.player, best);
        swing(p.swingMode);
        crystalCount++;
        lastCrystalId = best.getId();
        breakCooldown = Math.max(0, p.breakDelay);
        lastBreakMs = System.currentTimeMillis();
        return true;
    }

    // ---------------------------------------------------------------------
    //  Placing
    // ---------------------------------------------------------------------
    private boolean tryPlace(Params p) {
        try {
            int slot = crystalSlot();
            if (slot == -1) return false;
            if (version.is("V3") && v3Sequenced.isEnabled() && System.currentTimeMillis() - lastBreakMs > 500) return false;

            BlockPos bestBase = null;
            double bestDist = Double.MAX_VALUE;
            BlockPos origin = target.getBlockPos();

            for (int x = -3; x <= 3; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -3; z <= 3; z++) {
                        BlockPos base = origin.add(x, y, z);
                        BlockPos above = base.up();
                        BlockPos above2 = base.up(2);
                        try {
                            if (!mc.world.isChunkLoaded(base)) continue;
                            if (!mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN)
                                    && !mc.world.getBlockState(base).isOf(Blocks.BEDROCK)) continue;
                            if (!mc.world.isAir(above) || !mc.world.isAir(above2)) continue;

                            double bdx = (base.getX() + 0.5) - mc.player.getX();
                            double bdy = (base.getY() + 0.5) - mc.player.getY();
                            double bdz = (base.getZ() + 0.5) - mc.player.getZ();
                            double d = Math.sqrt(bdx * bdx + bdy * bdy + bdz * bdz);
                            if (d > p.placeRange + 1.0 || d >= bestDist) continue;

                            // Damage sanity: don't place a crystal that hurts us more than the target.
                            double targetDamage = crystalDamage(distTo(new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), target, p.predict));
                            double selfDamage = crystalDamage(distTo(new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), mc.player, false));
                            if (targetDamage < p.minDamage) continue;
                            if (p.antiSuicide && (selfDamage > p.maxSelfDamage || mc.player.getHealth() - selfDamage <= 0.0)) continue;

                            bestDist = d;
                            bestBase = base;
                        } catch (Throwable ignored) {}
                    }
                }
            }
            if (bestBase == null) return false;

            if (p.rotate) rotateTo(bestBase.getX() + 0.5, bestBase.getZ() + 0.5, p.aimSpeed);

            int prev = mc.player.getInventory().getSelectedSlot();
            if (p.autoSwitch && prev != slot) {
                mc.player.getInventory().setSelectedSlot(slot);
                placeCooldown = Math.max(1, Math.min(p.placeDelay, 2));
                return true;
            }

            try {
                BlockHitResult bhr = new BlockHitResult(
                        new Vec3d(bestBase.getX() + 0.5, bestBase.getY() + 1.0, bestBase.getZ() + 0.5),
                        Direction.UP, bestBase, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                swing(p.swingMode);
            } finally {
                if (p.autoSwitch) {
                    try {
                        mc.player.getInventory().setSelectedSlot(prev);
                    } catch (Throwable ignored) {}
                }
            }
            placeCooldown = Math.max(1, p.placeDelay);
            lastPlaceMs = System.currentTimeMillis();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------
    /** Rough explosion damage with linear falloff (crystal power 6, ~12 max damage). */
    private static double crystalDamage(double distance) {
        double d = 1.0 - distance / 12.0;
        return d <= 0.0 ? 0.0 : d * d * 12.0;
    }

    private double distTo(Entity crystal, Entity entity, boolean predict) {
        if (entity == null) return 999.0;
        double ex = entity.getX(), ez = entity.getZ();
        if (predict) {
            Vec3d v = entity.getVelocity();
            ex += v.x * 3.0;
            ez += v.z * 3.0;
        }
        double dx = crystal.getX() - ex;
        double dy = crystal.getY() - (entity.getY() + entity.getHeight() / 2.0);
        double dz = crystal.getZ() - ez;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double distTo(Vec3d crystal, Entity entity, boolean predict) {
        if (entity == null) return 999.0;
        double ex = entity.getX(), ez = entity.getZ();
        if (predict) {
            Vec3d v = entity.getVelocity();
            ex += v.x * 3.0;
            ez += v.z * 3.0;
        }
        double dx = crystal.x - ex;
        double dy = crystal.y - (entity.getY() + entity.getHeight() / 2.0);
        double dz = crystal.z - ez;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private int crystalSlot() {
        for (int i = 0; i < 9; i++) {
            try {
                Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                if (id != null && id.getPath().equals("end_crystal")) return i;
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    private void rotateTo(double x, double z, double speed) {
        try {
            double dx = x - mc.player.getX();
            double dz = z - mc.player.getZ();
            float want = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float diff = MathHelper.wrapDegrees(want - mc.player.getYaw());
            float step = (float) speed * 0.7f;
            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(diff, -step, step));
            if (version.is("V3") && v3Jitter.isEnabled()) {
                mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + (float) ((Math.random() - 0.5) * 2.0), -90f, 90f));
            }
        } catch (Throwable ignored) {}
    }

    private void swing(String mode) {
        if (mode == null || mode.equalsIgnoreCase("None")) return;
        try {
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        if (showTarget.isEnabled() && target != null) {
            int[] s = com.anormal.client.util.ProjectionUtil.project(
                    com.anormal.client.util.ProjectionUtil.lerpPos(target, tickDelta)
                            .add(0.0, target.getHeight() + 0.4, 0.0), tickDelta);
            if (s != null) {
                int col = targetColor.getValue();
                RenderUtils.drawBorder(context, s[0] - 12, s[1] - 4, s[0] + 12, s[1] + 4, 1, col);
                RenderUtils.drawText(context, mc.textRenderer, target.getName().getString(), s[0] - mc.textRenderer.getWidth(target.getName().getString()) / 2, s[1] - 6, col, true);
            }
        }

        if (centerScreen.isEnabled() && mc.getWindow() != null) {
            String info = "AutoCrystal [" + crystalCount + "]";
            int x = mc.getWindow().getScaledWidth() / 2 - 70;
            int y = mc.getWindow().getScaledHeight() / 2 + 20;
            RenderUtils.drawText(context, mc.textRenderer, info, x, y, 0xFFFFFFFF, true);
        }
    }
}
