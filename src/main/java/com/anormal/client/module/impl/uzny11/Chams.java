package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class Chams extends Module {
    public final BooleanSetting hideBots = new BooleanSetting("Hide Bots", "Hides server-side anti-cheat bots", true);
    public final BooleanSetting colored = new BooleanSetting("Colored", "Uses custom colors instead of skins", true);
    public final ColorSetting visibleColor = new ColorSetting("Visible Color", "Color for visible players", ColorUtils.rgba(80, 255, 120, 255));
    public final ColorSetting behindColor = new ColorSetting("Color Behind Walls", "Color for players behind walls", ColorUtils.rgba(255, 60, 60, 255));

    public Chams() {
        super("Chams", "Renders entity models and player textures through walls", Category.UZNY11);
        addSetting(hideBots);
        addSetting(colored);
        addSetting(visibleColor);
        addSetting(behindColor);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        try {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof PlayerEntity p) || e == mc.player) continue;
                e.setGlowing(!hideBots.isEnabled() || !isBot(p));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        try {
            for (Entity e : mc.world.getEntities())
                if (e instanceof PlayerEntity && e != mc.player) e.setGlowing(false);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!colored.isEnabled() || mc.player == null || mc.world == null) return;
        try {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || !p.isAlive()) continue;
                if (hideBots.isEnabled() && isBot(p)) continue;
                int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX(), p.getY() + p.getHeight() / 2.0, p.getZ()), tickDelta);
                if (s == null) continue;
                boolean vis = visible(p);
                int c = vis ? visibleColor.getValue() : behindColor.getValue();
                context.fill(s[0] - 4, s[1] - 7, s[0] + 4, s[1] + 7, (c & 0x00FFFFFF) | 0x40000000);
                context.fill(s[0] - 4, s[1] - 7, s[0] + 4, s[1] - 6, c);
                context.fill(s[0] - 4, s[1] + 6, s[0] + 4, s[1] + 7, c);
                context.fill(s[0] - 4, s[1] - 6, s[0] - 3, s[1] + 6, c);
                context.fill(s[0] + 3, s[1] - 6, s[0] + 4, s[1] + 6, c);
            }
        } catch (Throwable ignored) {}
    }

    private boolean visible(PlayerEntity p) {
        try {
            Vec3d from = mc.player.getEyePos();
            Vec3d to = new Vec3d(p.getX(), p.getEyeY(), p.getZ());
            RaycastContext ctx = new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player);
            return mc.world.raycast(ctx).getType() == HitResult.Type.MISS;
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean isBot(PlayerEntity p) {
        try {
            if (mc.getNetworkHandler() == null) return false;
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList())
                if (e.getProfile().id().equals(p.getUuid())) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
