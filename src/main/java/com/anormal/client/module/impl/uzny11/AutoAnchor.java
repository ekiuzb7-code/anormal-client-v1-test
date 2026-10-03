package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
public class AutoAnchor extends Module {
    public final ModeSetting anchorMode = new ModeSetting("Mode", "On Bind runs on enable, On Place reacts to anchors", "On Bind", "On Bind", "On Place");
    public final BooleanSetting doubleAnchor = new BooleanSetting("Double Anchor", "Place second anchor after first", false);
    public final BooleanSetting safeAnchor = new BooleanSetting("Safe Anchor", "Glowstone cover before charging", true);
    public final ModeSetting explosionItem = new ModeSetting("Explosion Item", "Held item for detonation", "Sword", "Sword", "Axe", "Mace", "None");
    public final BooleanSetting aimAssist = new BooleanSetting("Aim Assist", "Aim at anchor points", true);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Sequence without moving camera", false);
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Aim rotation speed", 12.0, 1.0, 30.0, 0.5);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between actions", 3.0, 0.0, 20.0, 1.0);
    private int stage = 0, timer = 0, retry = 0, originalSlot = -1;
    private boolean second = false;
    private BlockPos anchorPos = null;
    public AutoAnchor() {
        super("AutoAnchor", "Places, charges and detonates respawn anchors", Category.UZNY11);
        addSetting(anchorMode); addSetting(doubleAnchor); addSetting(safeAnchor); addSetting(explosionItem);
        addSetting(aimAssist); addSetting(silentAim); addSetting(aimSpeed); addSetting(delay);
    }
    @Override
    public void onEnable() {
        stage = anchorMode.is("On Bind") ? 1 : 0; timer = 0; retry = 0; second = false; anchorPos = null;
        originalSlot = mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
    }
    @Override
    public void onDisable() { restore(); stage = 0; timer = 0; anchorPos = null; }
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || !mc.player.isAlive()) return;
        if (timer > 0) { timer--; return; }
        timer = Math.max(0, delay.getValue().intValue());
        if (stage == 0) {
            BlockPos base = mc.player.getBlockPos();
            for (int dx = -5; dx <= 5 && anchorPos == null; dx++) for (int dy = -5; dy <= 5 && anchorPos == null; dy++) for (int dz = -5; dz <= 5; dz++)
                if (isAnchor(base.add(dx, dy, dz))) { anchorPos = base.add(dx, dy, dz); stage = 2; }
        } else if (stage == 1) {
            BlockPos place = findPlacePos(anchorTarget());
            int slot = findSlot("respawn_anchor");
            if (place == null || slot == -1) { idleOrOff(); return; }
            anchorPos = place; mc.player.getInventory().setSelectedSlot(slot);
            clickAt(place, false); retry = 6; stage = 2;
        } else if (stage == 2) {
            if (!isAnchor(anchorPos)) { if (anchorPos == null || --retry <= 0) idleOrOff(); else clickAt(anchorPos, false); return; }
            int glow = findSlot("glowstone");
            if (glow == -1) { idleOrOff(); return; }
            mc.player.getInventory().setSelectedSlot(glow);
            if (safeAnchor.isEnabled()) {
                int n = 0;
                for (int i = 0; i < 36; i++) { try { Identifier cid = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem()); if (cid != null && cid.getPath().equals("glowstone")) n++; } catch (Throwable ignored) {} }
                if (n >= 2) {
                    Vec3d eye = mc.player.getEyePos();
                    double cdx = eye.x - (anchorPos.getX() + 0.5), cdz = eye.z - (anchorPos.getZ() + 0.5);
                    BlockPos c = Math.abs(cdx) >= Math.abs(cdz) ? anchorPos.add(cdx > 0 ? 1 : -1, 1, 0) : anchorPos.add(0, 1, cdz > 0 ? 1 : -1);
                    if (mc.world.isAir(c)) for (Direction dd : Direction.values()) if (!mc.world.isAir(c.offset(dd))) { clickAt(c, false); break; }
                }
            }
            clickAt(anchorPos, true); stage = 3;
        } else {
            if (!isAnchor(anchorPos)) { idleOrOff(); return; }
            String want = explosionItem.getValue().toLowerCase();
            for (int i = 0; i < 9 && !want.equals("none"); i++)
                if (want.equals("sword") && itemPath(i).endsWith("_sword") || want.equals("axe") && itemPath(i).endsWith("_axe") || want.equals("mace") && itemPath(i).equals("mace")) { mc.player.getInventory().setSelectedSlot(i); break; }
            clickAt(anchorPos, true);
            if (doubleAnchor.isEnabled() && !second && findSlot("respawn_anchor") != -1) { second = true; anchorPos = null; stage = 1; }
            else idleOrOff();
        }
    }
    private void idleOrOff() { restore(); anchorPos = null; if (anchorMode.is("On Bind")) setEnabled(false); else stage = 0; }
    private void restore() { if (mc.player != null && originalSlot >= 0 && originalSlot < 9) { try { mc.player.getInventory().setSelectedSlot(originalSlot); } catch (Throwable ignored) {} } }
    private BlockPos anchorTarget() {
        for (Entity e : mc.world.getEntities())
            if (e instanceof PlayerEntity p && p != mc.player && p.isAlive() && mc.player.distanceTo(p) < 6.0) return p.getBlockPos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        return BlockPos.ofFloored(mc.player.getX() + look.x * 2.0, mc.player.getY(), mc.player.getZ() + look.z * 2.0);
    }
    private BlockPos findPlacePos(BlockPos target) {
        if (target == null) return null;
        for (int dy = 2; dy >= -2; dy--) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos p = target.add(dx, dy, dz);
            if (mc.world.isAir(p) && !mc.world.isAir(p.down())) for (Direction d : Direction.values()) if (!mc.world.isAir(p.offset(d))) return p;
        }
        return null;
    }
    private void clickAt(BlockPos p, boolean top) {
        if (top) {
            aimPoint(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5), Direction.UP, p, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }
        for (Direction d : Direction.values()) {
            BlockPos n = p.offset(d);
            if (mc.world.isAir(n)) continue;
            aimPoint(n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5), d.getOpposite(), n, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }
    }
    private boolean isAnchor(BlockPos p) {
        if (p == null) return false;
        try { Identifier id = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()); return id != null && id.getPath().equals("respawn_anchor"); }
        catch (Throwable ignored) { return false; }
    }
    private void aimPoint(double x, double y, double z) {
        if (!aimAssist.isEnabled() || silentAim.isEnabled()) return;
        Vec3d eye = mc.player.getEyePos();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z, s = aimSpeed.getValue();
        float ty = (float) Math.toDegrees(Math.atan2(-dx, dz)), tp = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float cur = mc.player.getYaw(), d = ty - cur;
        while (d > 180.0f) d -= 360.0f; while (d < -180.0f) d += 360.0f;
        mc.player.setYaw(cur + (float) Math.max(-s, Math.min(s, d)));
        float p = mc.player.getPitch();
        mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, p + (float) Math.max(-s, Math.min(s, tp - p)))));
    }
    private String itemPath(int slot) {
        try { Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(slot).getItem()); return id == null ? "" : id.getPath(); }
        catch (Throwable ignored) { return ""; }
    }
    private int findSlot(String path) { for (int i = 0; i < 9; i++) if (itemPath(i).equals(path)) return i; return -1; }
}
