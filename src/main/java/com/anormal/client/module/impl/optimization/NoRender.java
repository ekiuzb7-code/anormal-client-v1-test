package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.client.particle.Particle;

/**
 * Hides visual effects that cost frame time but tell you nothing.
 *
 * <p>Every toggle is independent and the whole module is <b>off by default</b>.
 * The actual cancelling happens in {@code ParticleManagerMixin},
 * {@code WorldRendererMixin} and {@code InGameHudMixin}.</p>
 */
public class NoRender extends Optimizer {

    // ---- Particles ----
    public final BooleanSetting explosions = new BooleanSetting("Explosion Particles", "Hide explosion and smoke particles", true);
    public final BooleanSetting blockBreak = new BooleanSetting("Block Break Particles", "Hide the particles from breaking blocks", true);
    public final BooleanSetting blockDust = new BooleanSetting("Block Dust", "Hide dust from falling / landing blocks", true);
    public final BooleanSetting potions = new BooleanSetting("Potion Particles", "Hide the swirls from potion effects", false);
    public final BooleanSetting flame = new BooleanSetting("Flame & Fire", "Hide flame, soul fire and campfire particles", false);
    public final BooleanSetting crit = new BooleanSetting("Crit & Enchant", "Hide critical hit and enchant particles", true);
    public final BooleanSetting totem = new BooleanSetting("Totem Particles", "Hide the totem pop burst", true);
    public final BooleanSetting note = new BooleanSetting("Note Particles", "Hide note block particles", false);
    public final BooleanSetting heart = new BooleanSetting("Heart Particles", "Hide breeding and damage hearts", false);
    public final BooleanSetting portal = new BooleanSetting("Portal Particles", "Hide nether / end portal particles", false);
    public final BooleanSetting redstone = new BooleanSetting("Redstone Particles", "Hide redstone dust particles", false);
    public final BooleanSetting bubbles = new BooleanSetting("Bubbles", "Hide water and lava bubbles", false);
    public final BooleanSetting drips = new BooleanSetting("Drips", "Hide water and lava dripping", false);
    public final BooleanSetting firework = new BooleanSetting("Firework Sparks", "Hide firework trail sparks", false);
    public final BooleanSetting others = new BooleanSetting("Everything Else", "Hide any particle not covered above", false);

    // ---- Overlays ----
    public final BooleanSetting fireOverlay = new BooleanSetting("Fire Overlay", "Hide the fire screen overlay", true);
    public final BooleanSetting waterOverlay = new BooleanSetting("Water Overlay", "Hide the underwater screen tint", false);
    public final BooleanSetting lavaOverlay = new BooleanSetting("Lava Overlay", "Hide the lava screen tint", false);
    public final BooleanSetting vignette = new BooleanSetting("Vignette", "Hide the dark screen edges", true);
    public final BooleanSetting portalOverlay = new BooleanSetting("Portal Overlay", "Hide the nether portal screen effect", true);
    public final BooleanSetting nausea = new BooleanSetting("Nausea", "Hide the nausea / warped vision effect", true);
    public final BooleanSetting spyglass = new BooleanSetting("Spyglass Overlay", "Hide the spyglass scope overlay", false);
    public final BooleanSetting sleep = new BooleanSetting("Sleep Overlay", "Hide the sleeping fade", false);
    public final BooleanSetting statusEffects = new BooleanSetting("Status Effect List", "Hide the potion effect icons", false);

    // ---- World ----
    public final BooleanSetting weather = new BooleanSetting("Weather Rendering", "Hide falling rain and snow", true);
    public final BooleanSetting blockBreaking = new BooleanSetting("Block Breaking Overlay", "Hide the crack texture while mining", false);
    public final BooleanSetting bossBar = new BooleanSetting("Boss Bar", "Hide the boss health bars", false);
    public final BooleanSetting scoreboard = new BooleanSetting("Scoreboard", "Hide the sidebar scoreboard", false);

    public NoRender() {
        super("NoRender", "Hides particles, overlays and effects you do not need");
        addSetting(explosions);
        addSetting(blockBreak);
        addSetting(blockDust);
        addSetting(potions);
        addSetting(flame);
        addSetting(crit);
        addSetting(totem);
        addSetting(note);
        addSetting(heart);
        addSetting(portal);
        addSetting(redstone);
        addSetting(bubbles);
        addSetting(drips);
        addSetting(firework);
        addSetting(others);
        addSetting(fireOverlay);
        addSetting(waterOverlay);
        addSetting(lavaOverlay);
        addSetting(vignette);
        addSetting(portalOverlay);
        addSetting(nausea);
        addSetting(spyglass);
        addSetting(sleep);
        addSetting(statusEffects);
        addSetting(weather);
        addSetting(blockBreaking);
        addSetting(bossBar);
        addSetting(scoreboard);
    }

    /** @return true when this particle should not be spawned at all. */
    public static boolean shouldHideParticle(Particle particle) {
        try {
            NoRender module = com.anormal.client.module.ModuleManager.getModule(NoRender.class);
            if (module == null || !module.isEnabled() || particle == null) return false;

            String name = particle.getClass().getName().toLowerCase();
            return module.hides(name);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hides(String name) {
        if (explosions.isEnabled() && (name.contains("explosion") || name.contains("smoke") || name.contains("poof"))) return true;
        if (blockBreak.isEnabled() && (name.contains("blockbreak") || name.contains("blockbreaking"))) return true;
        if (blockDust.isEnabled() && (name.contains("dust") || name.contains("falling"))) return true;
        if (potions.isEnabled() && (name.contains("spell") || name.contains("effect") || name.contains("potion"))) return true;
        if (flame.isEnabled() && (name.contains("flame") || name.contains("fire") || name.contains("lava") || name.contains("soul"))) return true;
        if (crit.isEnabled() && (name.contains("crit") || name.contains("enchant") || name.contains("sweep"))) return true;
        if (totem.isEnabled() && name.contains("totem")) return true;
        if (note.isEnabled() && name.contains("note")) return true;
        if (heart.isEnabled() && (name.contains("heart") || name.contains("damage"))) return true;
        if (portal.isEnabled() && (name.contains("portal") || name.contains("reverse_portal"))) return true;
        if (redstone.isEnabled() && name.contains("redstone")) return true;
        if (bubbles.isEnabled() && name.contains("bubble")) return true;
        if (drips.isEnabled() && (name.contains("drip") || name.contains("dripping"))) return true;
        if (firework.isEnabled() && (name.contains("firework") || name.contains("spark"))) return true;
        return others.isEnabled();
    }

    // ------------------------------------------------------------------
    //  Static queries used by the mixins
    // ------------------------------------------------------------------

    private static NoRender get() {
        NoRender m = com.anormal.client.module.ModuleManager.getModule(NoRender.class);
        return (m != null && m.isEnabled()) ? m : null;
    }

    public static boolean hideWeather() {
        NoRender m = get();
        return m != null && m.weather.isEnabled();
    }

    public static boolean hideBlockBreaking() {
        NoRender m = get();
        return m != null && m.blockBreaking.isEnabled();
    }

    public static boolean hideFireOverlay() {
        NoRender m = get();
        return m != null && m.fireOverlay.isEnabled();
    }

    public static boolean hideWaterOverlay() {
        NoRender m = get();
        return m != null && m.waterOverlay.isEnabled();
    }

    public static boolean hideLavaOverlay() {
        NoRender m = get();
        return m != null && m.lavaOverlay.isEnabled();
    }

    public static boolean hideVignette() {
        NoRender m = get();
        return m != null && m.vignette.isEnabled();
    }

    public static boolean hidePortalOverlay() {
        NoRender m = get();
        return m != null && m.portalOverlay.isEnabled();
    }

    public static boolean hideNausea() {
        NoRender m = get();
        return m != null && m.nausea.isEnabled();
    }

    public static boolean hideSpyglass() {
        NoRender m = get();
        return m != null && m.spyglass.isEnabled();
    }

    public static boolean hideSleep() {
        NoRender m = get();
        return m != null && m.sleep.isEnabled();
    }

    public static boolean hideStatusEffects() {
        NoRender m = get();
        return m != null && m.statusEffects.isEnabled();
    }

    public static boolean hideBossBar() {
        NoRender m = get();
        return m != null && m.bossBar.isEnabled();
    }

    public static boolean hideScoreboard() {
        NoRender m = get();
        return m != null && m.scoreboard.isEnabled();
    }
}
