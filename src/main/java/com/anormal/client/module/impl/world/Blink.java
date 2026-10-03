package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Blink extends Module {
    public final ModeSetting direction = new ModeSetting("Direction", "Outgoing holds position, Bi-directional also locks look", "Outgoing Only", "Outgoing Only", "Bi-directional");
    public final ModeSetting type = new ModeSetting("Type", "All also releases interact keys, Movement Only freezes motion", "Movement Only", "All", "Movement Only");
    public final BooleanSetting breadcrumbs = new BooleanSetting("Breadcrumbs", "Record trail from Blink start position", true);
    public final BooleanSetting spawnFake = new BooleanSetting("Spawn Fake", "Keep client-side start snapshot marker", true);
    public final BooleanSetting autoSend = new BooleanSetting("Auto Send", "Trim trail once past threshold", true);
    public final NumberSetting sendThreshold = new NumberSetting("Send Threshold", "Trail points kept before auto trim", 120.0, 10.0, 1000.0, 10.0);

    private final List<Vec3d> trail = new ArrayList<>();
    private double anchorX, anchorY, anchorZ;
    private float anchorYaw, anchorPitch;
    private boolean hasAnchor = false;

    public Blink() {
        super("Blink", "Holds server position via movement freeze (packet-free, no teleport on release)", Category.WORLD);
        addSetting(direction);
        addSetting(type);
        addSetting(breadcrumbs);
        addSetting(spawnFake);
        addSetting(autoSend);
        addSetting(sendThreshold);
    }

    @Override
    public void onEnable() {
        trail.clear();
        hasAnchor = false;
        if (mc.player == null) return;
        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();
        anchorYaw = mc.player.getYaw();
        anchorPitch = mc.player.getPitch();
        hasAnchor = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || !hasAnchor) return;
        if (breadcrumbs.isEnabled()) {
            trail.add(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }
        if (autoSend.isEnabled()) {
            int max = Math.max(1, sendThreshold.getValue().intValue());
            while (trail.size() > max) trail.remove(0);
        } else {
            while (trail.size() > 400) trail.remove(0);
        }
        mc.player.setVelocity(new Vec3d(0.0, 0.0, 0.0));
        if (spawnFake.isEnabled()) mc.player.setPosition(anchorX, anchorY, anchorZ);
        mc.player.fallDistance = 0.0f;
        if (direction.is("Bi-directional")) {
            mc.player.setYaw(anchorYaw);
            mc.player.setPitch(anchorPitch);
        }
        if (type.is("All")) {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.setVelocity(new Vec3d(0.0, 0.0, 0.0));
        trail.clear();
        hasAnchor = false;
    }

    public List<Vec3d> getTrail() {
        return trail;
    }
}
