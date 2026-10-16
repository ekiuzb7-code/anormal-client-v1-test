package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.NotificationManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaves the server automatically when things go wrong.
 *
 * <p>Each trigger is independent, so you can combine them freely. When any
 * trigger fires the module waits {@code Delay} ticks (so you can cancel), then
 * runs the chosen action and reports the reason in chat.</p>
 */
public class AutoDisconnect extends Module {

    // ---- Triggers ----
    public final BooleanSetting health = new BooleanSetting("Low Health", "Trigger when your health drops", true);
    public final NumberSetting healthThreshold = new NumberSetting("Health Threshold", "Health value that triggers it", 8.0, 1.0, 20.0, 1.0);

    public final BooleanSetting hunger = new BooleanSetting("Low Hunger", "Trigger when your hunger drops", false);
    public final NumberSetting hungerThreshold = new NumberSetting("Hunger Threshold", "Hunger value that triggers it", 6.0, 1.0, 20.0, 1.0);

    public final BooleanSetting totems = new BooleanSetting("Low Totems", "Trigger when you run out of totems", false);
    public final NumberSetting minTotems = new NumberSetting("Min Totems", "Trigger below this many totems", 1.0, 0.0, 10.0, 1.0);

    public final BooleanSetting players = new BooleanSetting("Player Nearby", "Trigger when another player comes close", true);
    public final NumberSetting playerRange = new NumberSetting("Player Range", "Distance that counts as nearby", 32.0, 4.0, 128.0, 2.0);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Do not trigger on the Friends list", true);
    public final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible", "Do not trigger on invisible players", true);
    public final BooleanSetting onlyArmed = new BooleanSetting("Only If Armed", "Only trigger when the player holds a weapon", false);
    public final BooleanSetting onlyTargeting = new BooleanSetting("Only If Targeted", "Only trigger when the player is looking at you", false);

    public final BooleanSetting falling = new BooleanSetting("Falling", "Trigger while falling a long way", false);
    public final NumberSetting fallDistance = new NumberSetting("Fall Distance", "Blocks fallen before triggering", 10.0, 3.0, 100.0, 1.0);

    public final BooleanSetting lava = new BooleanSetting("In Lava", "Trigger while standing in lava", false);
    public final BooleanSetting fire = new BooleanSetting("On Fire", "Trigger while burning", false);
    public final BooleanSetting freezing = new BooleanSetting("Freezing", "Trigger while freezing in powder snow", false);

    public final BooleanSetting voidCheck = new BooleanSetting("In Void", "Trigger when you fall below the Y level below", false);
    public final NumberSetting voidY = new NumberSetting("Void Y", "Y level used by the In Void trigger", -60.0, -128.0, 64.0, 1.0);

    public final BooleanSetting durability = new BooleanSetting("Low Durability", "Trigger when your gear is nearly broken", false);
    public final NumberSetting durabilityThreshold = new NumberSetting("Durability Threshold", "Remaining durability that triggers it", 10.0, 1.0, 100.0, 1.0);

    public final BooleanSetting onDeath = new BooleanSetting("On Death", "Trigger when you die", false);

    // ---- Actions ----
    public final ModeSetting action = new ModeSetting("Action", "What to do when a trigger fires", "Disconnect",
            "Disconnect", "Quit Game", "Warning Only");
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks to wait before acting", 0.0, 0.0, 60.0, 1.0);
    public final BooleanSetting chatWarning = new BooleanSetting("Chat Warning", "Print the reason in chat", true);
    public final BooleanSetting notification = new BooleanSetting("Notification", "Show a toast notification", true);
    public final BooleanSetting sound = new BooleanSetting("Sound", "Play a sound when triggering", true);
    public final BooleanSetting disableAfter = new BooleanSetting("Disable After", "Switch the module off after firing", true);
    public final BooleanSetting showReason = new BooleanSetting("Show Reason", "Include the reason in the disconnect message", true);

    // ---- Filters ----
    public final BooleanSetting ignoreCreative = new BooleanSetting("Ignore In Creative", "Never trigger in creative mode", true);
    public final BooleanSetting ignoreDead = new BooleanSetting("Ignore When Dead", "Do not trigger after you already died", true);

    private int countdown = -1;
    private String pendingReason = null;

    public AutoDisconnect() {
        super("AutoDisconnect", "Leaves the server automatically when a chosen condition is met", Category.COMBAT);
        addSetting(health);
        addSetting(healthThreshold);
        addSetting(hunger);
        addSetting(hungerThreshold);
        addSetting(totems);
        addSetting(minTotems);
        addSetting(players);
        addSetting(playerRange);
        addSetting(ignoreFriends);
        addSetting(ignoreInvisible);
        addSetting(onlyArmed);
        addSetting(onlyTargeting);
        addSetting(falling);
        addSetting(fallDistance);
        addSetting(lava);
        addSetting(fire);
        addSetting(freezing);
        addSetting(voidCheck);
        addSetting(voidY);
        addSetting(durability);
        addSetting(durabilityThreshold);
        addSetting(onDeath);
        addSetting(action);
        addSetting(delay);
        addSetting(chatWarning);
        addSetting(notification);
        addSetting(sound);
        addSetting(disableAfter);
        addSetting(showReason);
        addSetting(ignoreCreative);
        addSetting(ignoreDead);
    }

    @Override
    public void onDisable() {
        countdown = -1;
        pendingReason = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (ignoreCreative.isEnabled() && mc.player.getAbilities().creativeMode) return;
        if (ignoreDead.isEnabled() && !mc.player.isAlive()) return;

        // Already counting down?
        if (countdown >= 0) {
            if (countdown-- <= 0) {
                execute(pendingReason);
            }
            return;
        }

        String reason = evaluate();
        if (reason != null) {
            pendingReason = reason;
            countdown = delay.getValue().intValue();
            if (chatWarning.isEnabled() && mc.inGameHud != null) {
                try {
                    mc.inGameHud.getChatHud().addMessage(Text.literal("§c[AutoDisconnect] §f" + reason));
                } catch (Throwable ignored) {
                }
            }
            if (notification.isEnabled()) {
                NotificationManager.push("AutoDisconnect", reason, 0xFFFF5555);
            }
            if (sound.isEnabled()) playSound();
            if (countdown <= 0) execute(reason);
        }
    }

    // ------------------------------------------------------------------
    //  Trigger evaluation
    // ------------------------------------------------------------------

    /** @return a human-readable reason, or null when nothing fired. */
    private String evaluate() {
        if (health.isEnabled() && mc.player.getHealth() <= healthThreshold.getValue()) {
            return "Low health (" + (int) mc.player.getHealth() + ")";
        }
        if (hunger.isEnabled() && mc.player.getHungerManager().getFoodLevel() <= hungerThreshold.getValue()) {
            return "Low hunger (" + mc.player.getHungerManager().getFoodLevel() + ")";
        }
        if (totems.isEnabled() && countItem(Items.TOTEM_OF_UNDYING) <= minTotems.getValue().intValue()) {
            return "Out of totems";
        }
        if (players.isEnabled()) {
            PlayerEntity threat = nearestThreat();
            if (threat != null) {
                return "Player nearby: " + threat.getGameProfile().name();
            }
        }
        if (falling.isEnabled() && mc.player.fallDistance > fallDistance.getValue()) {
            return "Falling (" + (int) mc.player.fallDistance + " blocks)";
        }
        if (lava.isEnabled() && mc.player.isInLava()) return "In lava";
        if (fire.isEnabled() && mc.player.isOnFire()) return "On fire";
        if (freezing.isEnabled() && mc.player.isFrozen()) return "Freezing";
        if (voidCheck.isEnabled() && mc.player.getY() < voidY.getValue()) {
            return "In the void (Y " + (int) mc.player.getY() + ")";
        }
        if (durability.isEnabled()) {
            int left = lowestDurability();
            if (left >= 0 && left <= durabilityThreshold.getValue()) {
                return "Low durability (" + left + ")";
            }
        }
        if (onDeath.isEnabled() && !mc.player.isAlive()) return "You died";
        return null;
    }

    private PlayerEntity nearestThreat() {
        double max = playerRange.getValue();
        PlayerEntity best = null;
        double bestDist = max;
        for (PlayerEntity p : new ArrayList<>(mc.world.getPlayers())) {
            if (p == mc.player) continue;
            if (!p.isAlive()) continue;
            if (p.isInvisible() && ignoreInvisible.isEnabled()) continue;

            String name = p.getGameProfile().name();
            if (ignoreFriends.isEnabled() && Friends.isFriend(name)) continue;

            if (onlyArmed.isEnabled() && !isHoldingWeapon(p)) continue;
            if (onlyTargeting.isEnabled() && !isLookingAtMe(p)) continue;

            double d = mc.player.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private boolean isHoldingWeapon(PlayerEntity p) {
        ItemStack s = p.getMainHandStack();
        if (s.isEmpty()) return false;
        String path = net.minecraft.registry.Registries.ITEM.getId(s.getItem()).getPath();
        return path.endsWith("_sword") || path.endsWith("_axe") || path.contains("trident")
                || path.contains("mace") || path.endsWith("_bow") || path.contains("crossbow");
    }

    private boolean isLookingAtMe(PlayerEntity p) {
        try {
            var dir = p.getRotationVec(1.0f).normalize();
            var toMe = posOf(mc.player).add(0, 1.0, 0).subtract(p.getEyePos()).normalize();
            return dir.dotProduct(toMe) > 0.95;
        } catch (Throwable t) {
            return false;
        }
    }

    private int countItem(net.minecraft.item.Item item) {
        int total = 0;
        try {
            for (ItemStack s : mc.player.getInventory().getMainStacks()) {
                if (!s.isEmpty() && s.getItem() == item) total += s.getCount();
            }
            ItemStack off = mc.player.getOffHandStack();
            if (!off.isEmpty() && off.getItem() == item) total += off.getCount();
        } catch (Throwable ignored) {
        }
        return total;
    }

    /** Position of an entity without relying on version-specific accessors. */
    private static net.minecraft.util.math.Vec3d posOf(net.minecraft.entity.Entity e) {
        return new net.minecraft.util.math.Vec3d(e.getX(), e.getY(), e.getZ());
    }

    /** @return the lowest remaining durability found in the hotbar / armour, or -1. */
    private int lowestDurability() {
        int lowest = -1;
        try {
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < 9; i++) stacks.add(mc.player.getInventory().getStack(i));
            for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                    net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                    net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
                stacks.add(mc.player.getEquippedStack(slot));
            }

            for (ItemStack s : stacks) {
                if (s.isEmpty()) continue;
                int max = s.getMaxDamage();
                if (max <= 0) continue;
                int left = max - s.getDamage();
                if (lowest < 0 || left < lowest) lowest = left;
            }
        } catch (Throwable ignored) {
        }
        return lowest;
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    private void execute(String reason) {
        countdown = -1;
        pendingReason = null;

        String label = reason == null ? "Triggered" : reason;

        try {
            if (action.is("Disconnect")) {
                mc.disconnect(Text.literal(showReason.isEnabled()
                        ? "AutoDisconnect: " + label : "AutoDisconnect"));
            } else if (action.is("Quit Game")) {
                mc.scheduleStop();
            }
        } catch (Throwable ignored) {
        }

        if (disableAfter.isEnabled()) setEnabled(false);
    }

    private void playSound() {
        try {
            mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        } catch (Throwable ignored) {
        }
    }
}
