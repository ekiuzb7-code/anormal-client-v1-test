package dev.zaza.client.module.impl;

import dev.zaza.client.module.Category;
import dev.zaza.client.module.Module;
import net.minecraft.world.phys.Vec3;

public final class MovementModule extends Module {
    public MovementModule(String name, String id) {
        super(name, id, Category.LOCAL);
    }

    @Override
    public void tick() {
        var player = mc().player;
        if (player == null) return;
        switch (id()) {
            case "nofall" -> player.fallDistance = 0f;
            case "sprint" -> {
                if (player.input != null && player.input.hasForwardImpulse() && !player.isSprinting()) {
                    player.setSprinting(true);
                }
            }
            case "safewalk" -> {
                if (!player.onGround()) return;
                Vec3 delta = player.getDeltaMovement();
                if (delta.y <= 0 && !player.level().noCollision(player,
                        player.getBoundingBox().move(0, delta.y - 1.0, 0))) {
                    player.setDeltaMovement(0, Math.min(delta.y, 0), 0);
                }
            }
        }
    }
}
