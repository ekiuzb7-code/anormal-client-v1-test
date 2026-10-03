package com.anormal.client.module;

// import com.anormal.client.module.impl.client.*;
// import com.anormal.client.module.impl.combat.*;
// import com.anormal.client.module.impl.inventory.*;
// import com.anormal.client.module.impl.legit.*;
// import com.anormal.client.module.impl.movement.*;
// import com.anormal.client.module.impl.player.*;
// import com.anormal.client.module.impl.render.*;
// import com.anormal.client.module.impl.world.*;
// import com.anormal.client.module.impl.uzny11.*; // Use fully qualified names to avoid conflicts
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void init() {
        // --- COMBAT ---
        register(new com.anormal.client.module.impl.uzny11.AimAssist());
        register(new com.anormal.client.module.impl.uzny11.AutoClicker());
        register(new com.anormal.client.module.impl.uzny11.Triggerbot());
        register(new com.anormal.client.module.impl.uzny11.BlockHit());
        register(new com.anormal.client.module.impl.uzny11.HitFlick());
        register(new com.anormal.client.module.impl.uzny11.HitSelect());
        register(new com.anormal.client.module.impl.uzny11.Reach());
        register(new com.anormal.client.module.impl.uzny11.RightClicker());
        register(new com.anormal.client.module.impl.uzny11.SilentAura());
        register(new com.anormal.client.module.impl.uzny11.CrystalAura());
        register(new com.anormal.client.module.impl.uzny11.Velocity());
        register(new com.anormal.client.module.impl.uzny11.WTap());
        register(new com.anormal.client.module.impl.combat.JumpReset());
        register(new com.anormal.client.module.impl.uzny11.Criticals());
        register(new com.anormal.client.module.impl.uzny11.KeepSprint());

        // --- MOVEMENT ---
        register(new com.anormal.client.module.impl.uzny11.Sprint());
        register(new com.anormal.client.module.impl.movement.NoJumpDelay());
        register(new com.anormal.client.module.impl.movement.InvMove());
        register(new com.anormal.client.module.impl.uzny11.SafeWalk());
        register(new com.anormal.client.module.impl.movement.Parkour());
        register(new com.anormal.client.module.impl.movement.Spider());
        register(new com.anormal.client.module.impl.movement.ElytraFly());
        register(new com.anormal.client.module.impl.uzny11.NoFall());
        register(new com.anormal.client.module.impl.movement.NoWeb());
        register(new com.anormal.client.module.impl.uzny11.NoSlowdown());
        register(new com.anormal.client.module.impl.movement.BoatFly());

        // --- RENDER ---
        register(new com.anormal.client.module.impl.uzny11.Fullbright());
        register(new com.anormal.client.module.impl.uzny11.ESP());
        register(new com.anormal.client.module.impl.uzny11.Chams());
        register(new com.anormal.client.module.impl.uzny11.Tracers());
        register(new com.anormal.client.module.impl.render.BlockOverlay());
        register(new com.anormal.client.module.impl.uzny11.NoHurtCam());
        register(new com.anormal.client.module.impl.uzny11.NoLevitation());
        register(new com.anormal.client.module.impl.uzny11.ClearWater());
        register(new com.anormal.client.module.impl.render.FreeLook());
        register(new com.anormal.client.module.impl.uzny11.Health());
        register(new com.anormal.client.module.impl.uzny11.Indicators());
        register(new com.anormal.client.module.impl.uzny11.ItemESP());
        register(new com.anormal.client.module.impl.uzny11.NameTags());
        register(new com.anormal.client.module.impl.uzny11.Projectiles());
        register(new com.anormal.client.module.impl.uzny11.SpawnerFinder());
        register(new com.anormal.client.module.impl.uzny11.StorageESP());
        register(new com.anormal.client.module.impl.render.StashFinder());
        register(new com.anormal.client.module.impl.render.DeathPoints());
        register(new com.anormal.client.module.impl.render.EntityCulling());
        register(new com.anormal.client.module.impl.render.ParticleOptimizer());
        register(new com.anormal.client.module.impl.render.DynamicRenderDistance());
        register(new com.anormal.client.module.impl.render.PerformanceOverlay());
        register(new com.anormal.client.module.impl.render.Waypoints());
        register(new com.anormal.client.module.impl.uzny11.Trajectories());
        register(new com.anormal.client.module.impl.uzny11.Search());
        register(new com.anormal.client.module.impl.uzny11.AntiDebuff());
        register(new com.anormal.client.module.impl.uzny11.Arrows());
        register(new com.anormal.client.module.impl.uzny11.Explosions());
        register(new com.anormal.client.module.impl.render.PortalFinder());
        register(new com.anormal.client.module.impl.render.BedESP());
        register(new com.anormal.client.module.impl.render.DonkeyFinder());
        register(new com.anormal.client.module.impl.render.ShulkerFinder());
        register(new com.anormal.client.module.impl.render.BaseFinder());
        register(new com.anormal.client.module.impl.render.EndPortalFinder());
        register(new com.anormal.client.module.impl.render.AncientCityFinder());
        register(new com.anormal.client.module.impl.render.StrongholdFinder());
        register(new com.anormal.client.module.impl.render.TrialChambersFinder());
        register(new com.anormal.client.module.impl.render.OreESP());
        register(new com.anormal.client.module.impl.render.ArmorStandESP());
        register(new com.anormal.client.module.impl.render.ItemFrameESP());
        register(new com.anormal.client.module.impl.render.PaintingESP());
        register(new com.anormal.client.module.impl.render.BannerESP());
        register(new com.anormal.client.module.impl.render.SignESP());
        register(new com.anormal.client.module.impl.render.BlockESPCoord());
        register(new com.anormal.client.module.impl.render.StructureESP());
        register(new com.anormal.client.module.impl.render.VillageESP());
        register(new com.anormal.client.module.impl.render.VehicleESP());
        register(new com.anormal.client.module.impl.render.ProjectileESP());
        register(new com.anormal.client.module.impl.render.LogoutSpots());
        register(new com.anormal.client.module.impl.render.LogoutESP());
        register(new com.anormal.client.module.impl.render.BaseCoords());
        register(new com.anormal.client.module.impl.render.BaseManager());

        // --- PLAYER ---
        register(new com.anormal.client.module.impl.player.FastPlace());
        register(new com.anormal.client.module.impl.uzny11.AutoTool());
        register(new com.anormal.client.module.impl.player.NoClickDelay());
        register(new com.anormal.client.module.impl.player.ChestStealer());
        register(new com.anormal.client.module.impl.player.NoHunger());
        register(new com.anormal.client.module.impl.player.AutoEat());
        register(new com.anormal.client.module.impl.player.StorageManager());
        register(new com.anormal.client.module.impl.player.ChestLabels());
        register(new com.anormal.client.module.impl.player.ShulkerLabels());
        register(new com.anormal.client.module.impl.player.PortalManager());

        // --- WORLD ---
        register(new com.anormal.client.module.impl.uzny11.Scaffold());
        register(new BlockIn());
        register(new com.anormal.client.module.impl.uzny11.MLG());
        register(new com.anormal.client.module.impl.uzny11.XRay());
        register(new com.anormal.client.module.impl.uzny11.Freecam());
        register(new com.anormal.client.module.impl.uzny11.AntiAFK());
        register(new com.anormal.client.module.impl.uzny11.AutoFish());
        register(new com.anormal.client.module.impl.uzny11.AutoAnchor());
        register(new com.anormal.client.module.impl.world.AutoMace());
        register(new com.anormal.client.module.impl.uzny11.AutoPearl());
        register(new com.anormal.client.module.impl.uzny11.Clutch());
        register(new com.anormal.client.module.impl.uzny11.HitSwap());
        register(new com.anormal.client.module.impl.world.ShieldBreaker());
        register(new com.anormal.client.module.impl.world.WindCharge());
        register(new com.anormal.client.module.impl.world.Surround());
        register(new com.anormal.client.module.impl.world.AntiFireball());
        register(new com.anormal.client.module.impl.world.PearlCatch());
        register(new com.anormal.client.module.impl.world.TargetFilter());
        register(new com.anormal.client.module.impl.uzny11.Panic());
        register(new com.anormal.client.module.impl.uzny11.Blink());
        register(new com.anormal.client.module.impl.uzny11.FakeLag());
        register(new com.anormal.client.module.impl.uzny11.BackTrack());
        register(new com.anormal.client.module.impl.world.CoordinateLogger());

        // --- INVENTORY ---
        register(new com.anormal.client.module.impl.uzny11.InvCleaner());
        register(new com.anormal.client.module.impl.inventory.ArmorSwitch());
        register(new com.anormal.client.module.impl.uzny11.AutoArmor());
        register(new com.anormal.client.module.impl.uzny11.AutoHotbar());
        register(new com.anormal.client.module.impl.uzny11.AutoTotem());
        register(new com.anormal.client.module.impl.uzny11.InventoryManager());
        register(new com.anormal.client.module.impl.inventory.InventoryFill());
        register(new com.anormal.client.module.impl.inventory.Refill());
        register(new com.anormal.client.module.impl.inventory.ThrowDebuff());
        register(new com.anormal.client.module.impl.inventory.Throwpot());

        // --- LEGIT & OVERLAYS ---
        register(new com.anormal.client.module.impl.legit.Keystrokes());
        register(new com.anormal.client.module.impl.legit.ArmorStatus());
        register(new com.anormal.client.module.impl.legit.Coords());
        register(new com.anormal.client.module.impl.legit.FPS());
        register(new com.anormal.client.module.impl.legit.Clock());
        register(new com.anormal.client.module.impl.legit.ReachDisplay());
        register(new com.anormal.client.module.impl.legit.PotionStatus());
        register(new com.anormal.client.module.impl.legit.DuelInfo());
        register(new com.anormal.client.module.impl.legit.InventoryOverlay());
        register(new com.anormal.client.module.impl.legit.PartyOverlay());
        register(new com.anormal.client.module.impl.legit.Radar());
        register(new com.anormal.client.module.impl.legit.Rearview());
        register(new com.anormal.client.module.impl.legit.TargetInfo());
        register(new com.anormal.client.module.impl.legit.BlockhitAnimation());
        register(new com.anormal.client.module.impl.legit.Compass());
        register(new com.anormal.client.module.impl.legit.HitColor());
        register(new com.anormal.client.module.impl.legit.InventoryBlur());
        register(new com.anormal.client.module.impl.legit.MouseDelayFix());
        register(new com.anormal.client.module.impl.legit.Scoreboard());
        register(new com.anormal.client.module.impl.legit.TimeChanger());
        register(new com.anormal.client.module.impl.legit.Weather());
        register(new com.anormal.client.module.impl.legit.NoWeather());
        register(new com.anormal.client.module.impl.legit.BiomeHUD());
        register(new com.anormal.client.module.impl.legit.EnemyArmor());
        register(new com.anormal.client.module.impl.legit.EnemyInventory());
        register(new com.anormal.client.module.impl.legit.ToolDurability());
        register(new com.anormal.client.module.impl.legit.ArmorDurability());
        register(new com.anormal.client.module.impl.legit.FoodStatus());
        register(new com.anormal.client.module.impl.legit.ItemCounter());
        register(new com.anormal.client.module.impl.legit.BlockCounter());
        register(new com.anormal.client.module.impl.legit.EnchantHelper());
        register(new com.anormal.client.module.impl.legit.AnvilHelper());
        register(new com.anormal.client.module.impl.legit.MemoryHUD());
        register(new com.anormal.client.module.impl.legit.RenderStats());
        register(new com.anormal.client.module.impl.legit.DirectionHUD());
        register(new com.anormal.client.module.impl.legit.TravelDistance());
        register(new com.anormal.client.module.impl.legit.CoordinateShare());

        // --- UZNY11 (Vape Modules - All) ---
        // Combat
        register(new com.anormal.client.module.impl.uzny11.KillAura());
        register(new com.anormal.client.module.impl.uzny11.CrystalAura());
        register(new com.anormal.client.module.impl.uzny11.AimAssist());
        register(new com.anormal.client.module.impl.uzny11.AutoClicker());
        register(new com.anormal.client.module.impl.uzny11.SilentAura());
        register(new com.anormal.client.module.impl.uzny11.Triggerbot());
        register(new com.anormal.client.module.impl.uzny11.Reach());
        register(new com.anormal.client.module.impl.uzny11.Velocity());
        register(new com.anormal.client.module.impl.uzny11.WTap());
        register(new com.anormal.client.module.impl.uzny11.HitSelect());
        register(new com.anormal.client.module.impl.uzny11.HitSwap());
        register(new com.anormal.client.module.impl.uzny11.BlockHit());
        register(new com.anormal.client.module.impl.uzny11.BowAimbot());
        register(new com.anormal.client.module.impl.uzny11.LeftClicker());
        register(new com.anormal.client.module.impl.uzny11.RightClicker());
        register(new com.anormal.client.module.impl.uzny11.Sprint());
        register(new com.anormal.client.module.impl.uzny11.Criticals());
        register(new com.anormal.client.module.impl.uzny11.KeepSprint());

        // Movement/Blatant
        register(new com.anormal.client.module.impl.uzny11.Fly());
        register(new com.anormal.client.module.impl.uzny11.NoFall());
        register(new com.anormal.client.module.impl.uzny11.Speed());
        register(new com.anormal.client.module.impl.uzny11.Scaffold());
        register(new com.anormal.client.module.impl.uzny11.Step());
        register(new com.anormal.client.module.impl.uzny11.Strafe());
        register(new com.anormal.client.module.impl.uzny11.Timer());
        register(new com.anormal.client.module.impl.uzny11.Phase());
        register(new com.anormal.client.module.impl.uzny11.LongJump());
        register(new com.anormal.client.module.impl.uzny11.OmniSprint());
        register(new com.anormal.client.module.impl.uzny11.Regen());
        register(new com.anormal.client.module.impl.uzny11.PotionSaver());
        register(new com.anormal.client.module.impl.uzny11.AntiBot());
        register(new com.anormal.client.module.impl.uzny11.AntiFML());
        register(new com.anormal.client.module.impl.uzny11.HitBoxes());
        register(new com.anormal.client.module.impl.uzny11.KnockbackTest());
        register(new com.anormal.client.module.impl.uzny11.SafeWalk());
        register(new com.anormal.client.module.impl.uzny11.NoSlowdown());
        register(new com.anormal.client.module.impl.uzny11.AutoAnchor());
        register(new com.anormal.client.module.impl.uzny11.AutoHeal());
        register(new com.anormal.client.module.impl.uzny11.AntiFall());
        register(new com.anormal.client.module.impl.uzny11.BackTrack());
        register(new com.anormal.client.module.impl.uzny11.Blink());
        register(new com.anormal.client.module.impl.uzny11.MLG());
        register(new com.anormal.client.module.impl.uzny11.Panic());
        register(new com.anormal.client.module.impl.uzny11.Clutch());
        register(new com.anormal.client.module.impl.uzny11.AutoTool());
        register(new com.anormal.client.module.impl.uzny11.AutoFish());
        register(new com.anormal.client.module.impl.uzny11.ChestSteal());
        register(new com.anormal.client.module.impl.uzny11.AutoArmor());
        register(new com.anormal.client.module.impl.uzny11.AutoHotbar());
        register(new com.anormal.client.module.impl.uzny11.AutoTotem());
        register(new com.anormal.client.module.impl.uzny11.InventoryManager());
        register(new com.anormal.client.module.impl.uzny11.InvCleaner());
        register(new com.anormal.client.module.impl.uzny11.AutoPearl());
        register(new com.anormal.client.module.impl.uzny11.InvWalk());
        register(new com.anormal.client.module.impl.uzny11.AntiAFK());
        register(new com.anormal.client.module.impl.inventory.Refill());
        register(new com.anormal.client.module.impl.inventory.ThrowDebuff());
        register(new com.anormal.client.module.impl.inventory.Throwpot());

        // World
        register(new com.anormal.client.module.impl.uzny11.FakeLag());
        register(new com.anormal.client.module.impl.uzny11.XRay());
        register(new com.anormal.client.module.impl.uzny11.Freecam());
        register(new BedBreaker());
        register(new MurderFinder());

        // Render
        register(new com.anormal.client.module.impl.uzny11.ESP());
        register(new com.anormal.client.module.impl.uzny11.Fullbright());
        register(new com.anormal.client.module.impl.uzny11.Tracers());
        register(new com.anormal.client.module.impl.uzny11.Chams());
        register(new com.anormal.client.module.impl.uzny11.NameTags());
        register(new com.anormal.client.module.impl.uzny11.ItemESP());
        register(new com.anormal.client.module.impl.uzny11.Search());
        register(new com.anormal.client.module.impl.uzny11.SpawnerFinder());
        register(new com.anormal.client.module.impl.uzny11.StorageESP());
        register(new com.anormal.client.module.impl.uzny11.Trajectories());
        register(new com.anormal.client.module.impl.uzny11.Projectiles());
        register(new com.anormal.client.module.impl.uzny11.AntiDebuff());
        register(new com.anormal.client.module.impl.uzny11.NoHurtCam());
        register(new com.anormal.client.module.impl.uzny11.ClearWater());
        register(new com.anormal.client.module.impl.uzny11.Arrows());
        register(new com.anormal.client.module.impl.uzny11.Indicators());
        register(new com.anormal.client.module.impl.uzny11.BedPlates());
        register(new com.anormal.client.module.impl.uzny11.Health());
        register(new com.anormal.client.module.impl.uzny11.Explosions());
        register(new PropHunt());
        register(new Animations());
        register(new com.anormal.client.module.impl.uzny11.NoLevitation());
        register(new BedBreaker());

        // Utility
        register(new com.anormal.client.module.impl.uzny11.AutoTool());
        register(new com.anormal.client.module.impl.uzny11.AutoFish());
        register(new com.anormal.client.module.impl.uzny11.ChestSteal());
        register(new com.anormal.client.module.impl.uzny11.AutoArmor());
        register(new com.anormal.client.module.impl.uzny11.AutoHotbar());
        register(new com.anormal.client.module.impl.uzny11.AutoTotem());
        register(new com.anormal.client.module.impl.uzny11.InventoryManager());
        register(new com.anormal.client.module.impl.uzny11.InvCleaner());
        register(new com.anormal.client.module.impl.uzny11.AutoPearl());
        register(new com.anormal.client.module.impl.uzny11.Clutch());
        register(new com.anormal.client.module.impl.uzny11.InvWalk());
        register(new com.anormal.client.module.impl.uzny11.AutoAnchor());
        register(new com.anormal.client.module.impl.uzny11.AntiAFK());
        register(new com.anormal.client.module.impl.uzny11.MLG());
        register(new com.anormal.client.module.impl.uzny11.Panic());
        register(new com.anormal.client.module.impl.inventory.Refill());
        register(new com.anormal.client.module.impl.inventory.ThrowDebuff());
        register(new com.anormal.client.module.impl.inventory.Throwpot());

        // --- CLIENT & SETTINGS ---
        register(new com.anormal.client.module.impl.client.TextGUI());
        register(new com.anormal.client.module.impl.client.ClientSettings());
        register(new com.anormal.client.module.impl.client.Macros());
        register(new com.anormal.client.module.impl.client.Friends());
        register(new com.anormal.client.module.impl.client.Profiles());
        register(new com.anormal.client.module.impl.uzny11.AntiBot());
        register(new com.anormal.client.module.impl.client.NameProtect());
        register(new com.anormal.client.module.impl.legit.Watermark());
        register(new com.anormal.client.module.impl.client.PlayerLogger());
    }

    public static void register(Module module) {
        modules.add(module);
    }

    public static List<Module> getModules() {
        return modules;
    }

    public static List<Module> getModulesByCategory(Category category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T getModule(Class<T> clazz) {
        return (T) modules.stream()
                .filter(m -> m.getClass() == clazz)
                .findFirst()
                .orElse(null);
    }

    public static void onTick() {
        for (Module module : modules) {
            if (module.isEnabled()) {
                try {
                    module.onTick();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void onRender2D(DrawContext context, float tickDelta) {
        for (Module module : modules) {
            if (module.isEnabled()) {
                try {
                    module.onRender2D(context, tickDelta);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void onKeyPressed(int keyCode) {
        if (keyCode <= 0) return;
        for (Module module : modules) {
            if (module.getKey() == keyCode) {
                module.toggle();
            }
        }
    }
}
