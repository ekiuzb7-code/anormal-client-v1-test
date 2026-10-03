package com.anormal.client.module;

import com.anormal.client.module.impl.client.*;
import com.anormal.client.module.impl.combat.*;
import com.anormal.client.module.impl.inventory.*;
import com.anormal.client.module.impl.legit.*;
import com.anormal.client.module.impl.movement.*;
import com.anormal.client.module.impl.player.*;
import com.anormal.client.module.impl.render.*;
import com.anormal.client.module.impl.world.*;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void init() {
        // --- COMBAT ---
        register(new AimAssist());
        register(new AutoClicker());
        register(new Triggerbot());
        register(new BlockHit());
        register(new HitFlick());
        register(new HitSelect());
        register(new Reach());
        register(new RightClicker());
        register(new SilentAura());
        register(new CrystalAura());
        register(new Velocity());
        register(new WTap());
        register(new JumpReset());
        register(new Criticals());
        register(new KeepSprint());

        // --- MOVEMENT ---
        register(new Sprint());
        register(new NoJumpDelay());
        register(new InvMove());
        register(new SafeWalk());
        register(new Parkour());
        register(new Spider());
        register(new ElytraFly());
        register(new NoFall());
        register(new NoWeb());
        register(new NoSlowdown());

        // --- RENDER ---
        register(new Fullbright());
        register(new ESP());
        register(new Chams());
        register(new Tracers());
        register(new BlockOverlay());
        register(new NoHurtCam());
        register(new NoLevitation());
        register(new ClearWater());
        register(new FreeLook());
        register(new Health());
        register(new Indicators());
        register(new ItemESP());
        register(new NameTags());
        register(new Projectiles());
        register(new SpawnerFinder());
        register(new StorageESP());
        register(new StashFinder());
        register(new DeathPoints());
        register(new EntityCulling());
        register(new ParticleOptimizer());
        register(new DynamicRenderDistance());
        register(new PerformanceOverlay());
        register(new Waypoints());
        register(new Trajectories());
        register(new Search());
        register(new AntiDebuff());
        register(new Arrows());
        register(new Explosions());
        register(new PortalFinder());
        register(new BedESP());
        register(new DonkeyFinder());
        register(new ShulkerFinder());
        register(new BaseFinder());
        register(new EndPortalFinder());
        register(new AncientCityFinder());
        register(new StrongholdFinder());
        register(new TrialChambersFinder());
        register(new OreESP());
        register(new ArmorStandESP());
        register(new ItemFrameESP());
        register(new PaintingESP());
        register(new BannerESP());
        register(new SignESP());
        register(new BlockESPCoord());
        register(new StructureESP());
        register(new VillageESP());
        register(new VehicleESP());
        register(new ProjectileESP());
        register(new LogoutSpots());
        register(new LogoutESP());
        register(new BaseCoords());
        register(new BaseManager());

        // --- PLAYER ---
        register(new FastPlace());
        register(new AutoTool());
        register(new NoClickDelay());
        register(new ChestStealer());
        register(new NoHunger());
        register(new AutoEat());
        register(new StorageManager());
        register(new ChestLabels());
        register(new ShulkerLabels());
        register(new PortalManager());

        // --- WORLD ---
        register(new Scaffold());
        register(new BlockIn());
        register(new MLG());
        register(new XRay());
        register(new Freecam());
        register(new AntiAFK());
        register(new AutoFish());
        register(new AutoAnchor());
        register(new AutoMace());
        register(new AutoPearl());
        register(new Clutch());
        register(new HitSwap());
        register(new ShieldBreaker());
        register(new WindCharge());
        register(new Surround());
        register(new AntiFireball());
        register(new PearlCatch());
        register(new TargetFilter());
        register(new Panic());
        register(new Blink());
        register(new FakeLag());
        register(new BackTrack());
        register(new CoordinateLogger());

        // --- INVENTORY ---
        register(new InvCleaner());
        register(new ArmorSwitch());
        register(new AutoArmor());
        register(new AutoHotbar());
        register(new AutoTotem());
        register(new InventoryManager());
        register(new InventoryFill());
        register(new Refill());
        register(new ThrowDebuff());
        register(new Throwpot());

        // --- LEGIT & OVERLAYS ---
        register(new Keystrokes());
        register(new ArmorStatus());
        register(new Coords());
        register(new FPS());
        register(new Clock());
        register(new ReachDisplay());
        register(new PotionStatus());
        register(new DuelInfo());
        register(new InventoryOverlay());
        register(new PartyOverlay());
        register(new Radar());
        register(new Rearview());
        register(new TargetInfo());
        register(new BlockhitAnimation());
        register(new Compass());
        register(new HitColor());
        register(new InventoryBlur());
        register(new MouseDelayFix());
        register(new Scoreboard());
        register(new TimeChanger());
        register(new Weather());
        register(new NoWeather());
        register(new BiomeHUD());
        register(new EnemyArmor());
        register(new EnemyInventory());
        register(new ToolDurability());
        register(new ArmorDurability());
        register(new FoodStatus());
        register(new ItemCounter());
        register(new BlockCounter());
        register(new EnchantHelper());
        register(new AnvilHelper());
        register(new MemoryHUD());
        register(new RenderStats());
        register(new DirectionHUD());
        register(new TravelDistance());
        register(new CoordinateShare());

        // --- UZNY11 ---
        register(new com.anormal.client.module.impl.uzny11.AimAssist());
        register(new com.anormal.client.module.impl.uzny11.AutoClicker());
        register(new com.anormal.client.module.impl.uzny11.BlockHit());
        register(new com.anormal.client.module.impl.uzny11.HitSelect());
        register(new com.anormal.client.module.impl.uzny11.HitFlick());
        register(new com.anormal.client.module.impl.uzny11.Reach());
        register(new com.anormal.client.module.impl.uzny11.RightClicker());
        register(new com.anormal.client.module.impl.uzny11.SilentAura());
        register(new com.anormal.client.module.impl.uzny11.Triggerbot());
        register(new com.anormal.client.module.impl.uzny11.WTap());
        register(new com.anormal.client.module.impl.uzny11.JumpReset());
        register(new com.anormal.client.module.impl.uzny11.Velocity());
        register(new com.anormal.client.module.impl.uzny11.CrystalAura());
        register(new com.anormal.client.module.impl.uzny11.Criticals());
        register(new com.anormal.client.module.impl.uzny11.KeepSprint());
        register(new com.anormal.client.module.impl.uzny11.Sprint());
        register(new com.anormal.client.module.impl.uzny11.SafeWalk());
        register(new com.anormal.client.module.impl.uzny11.Parkour());
        register(new com.anormal.client.module.impl.uzny11.Spider());
        register(new com.anormal.client.module.impl.uzny11.ElytraFly());
        register(new com.anormal.client.module.impl.uzny11.FastPlace());
        register(new com.anormal.client.module.impl.uzny11.AutoTool());
        register(new com.anormal.client.module.impl.uzny11.NoClickDelay());
        register(new com.anormal.client.module.impl.uzny11.ChestStealer());
        register(new com.anormal.client.module.impl.uzny11.XRay());
        register(new com.anormal.client.module.impl.uzny11.FakeLag());
        register(new com.anormal.client.module.impl.uzny11.BackTrack());
        register(new com.anormal.client.module.impl.uzny11.Blink());
        register(new com.anormal.client.module.impl.uzny11.Scaffold());
        register(new com.anormal.client.module.impl.uzny11.AntiAFK());
        register(new com.anormal.client.module.impl.uzny11.AutoFish());
        register(new com.anormal.client.module.impl.uzny11.AutoPearl());
        register(new com.anormal.client.module.impl.uzny11.AutoMace());
        register(new com.anormal.client.module.impl.uzny11.AutoAnchor());
        register(new com.anormal.client.module.impl.uzny11.Clutch());
        register(new com.anormal.client.module.impl.uzny11.MLG());
        register(new com.anormal.client.module.impl.uzny11.Panic());
        register(new com.anormal.client.module.impl.uzny11.PearlCatch());
        register(new com.anormal.client.module.impl.uzny11.ShieldBreaker());
        register(new com.anormal.client.module.impl.uzny11.WindCharge());
        register(new com.anormal.client.module.impl.uzny11.TargetFilter());
        register(new com.anormal.client.module.impl.uzny11.Freecam());
        register(new com.anormal.client.module.impl.uzny11.Surround());
        register(new com.anormal.client.module.impl.uzny11.BlockIn());
        register(new com.anormal.client.module.impl.uzny11.HitSwap());
        register(new com.anormal.client.module.impl.uzny11.InvCleaner());
        register(new com.anormal.client.module.impl.uzny11.ArmorSwitch());
        register(new com.anormal.client.module.impl.uzny11.AutoArmor());
        register(new com.anormal.client.module.impl.uzny11.AutoHotbar());
        register(new com.anormal.client.module.impl.uzny11.AutoTotem());
        register(new com.anormal.client.module.impl.uzny11.InventoryManager());
        register(new com.anormal.client.module.impl.uzny11.InventoryFill());
        register(new com.anormal.client.module.impl.uzny11.Refill());
        register(new com.anormal.client.module.impl.uzny11.ThrowDebuff());
        register(new com.anormal.client.module.impl.uzny11.Throwpot());
        register(new com.anormal.client.module.impl.uzny11.Fullbright());
        register(new com.anormal.client.module.impl.uzny11.ESP());
        register(new com.anormal.client.module.impl.uzny11.Tracers());
        register(new com.anormal.client.module.impl.uzny11.Chams());
        register(new com.anormal.client.module.impl.uzny11.Search());
        register(new com.anormal.client.module.impl.uzny11.StorageESP());
        register(new com.anormal.client.module.impl.uzny11.StashFinder());
        register(new com.anormal.client.module.impl.uzny11.DeathPoints());
        register(new com.anormal.client.module.impl.uzny11.Arrows());
        register(new com.anormal.client.module.impl.uzny11.ItemESP());
        register(new com.anormal.client.module.impl.uzny11.NameTags());
        register(new com.anormal.client.module.impl.uzny11.Projectiles());
        register(new com.anormal.client.module.impl.uzny11.Trajectories());
        register(new com.anormal.client.module.impl.uzny11.SpawnerFinder());
        register(new com.anormal.client.module.impl.uzny11.Explosions());
        register(new com.anormal.client.module.impl.uzny11.Indicators());
        register(new com.anormal.client.module.impl.uzny11.BlockOverlay());
        register(new com.anormal.client.module.impl.uzny11.FreeLook());
        register(new com.anormal.client.module.impl.uzny11.Health());
        register(new com.anormal.client.module.impl.uzny11.AntiDebuff());
        register(new com.anormal.client.module.impl.uzny11.NoHurtCam());
        register(new com.anormal.client.module.impl.uzny11.ClearWater());
        register(new com.anormal.client.module.impl.uzny11.Keystrokes());
        register(new com.anormal.client.module.impl.uzny11.ArmorStatus());
        register(new com.anormal.client.module.impl.uzny11.Coords());
        register(new com.anormal.client.module.impl.uzny11.FPS());
        register(new com.anormal.client.module.impl.uzny11.Clock());
        register(new com.anormal.client.module.impl.uzny11.Compass());
        register(new com.anormal.client.module.impl.uzny11.PotionStatus());
        register(new com.anormal.client.module.impl.uzny11.ReachDisplay());
        register(new com.anormal.client.module.impl.uzny11.TargetInfo());
        register(new com.anormal.client.module.impl.uzny11.DuelInfo());
        register(new com.anormal.client.module.impl.uzny11.PartyOverlay());
        register(new com.anormal.client.module.impl.uzny11.Radar());
        register(new com.anormal.client.module.impl.uzny11.Rearview());
        register(new com.anormal.client.module.impl.uzny11.Scoreboard());
        register(new com.anormal.client.module.impl.uzny11.InventoryOverlay());
        register(new com.anormal.client.module.impl.uzny11.TimeChanger());
        register(new com.anormal.client.module.impl.uzny11.Weather());
        register(new com.anormal.client.module.impl.uzny11.NoWeather());
        register(new com.anormal.client.module.impl.uzny11.Timer());
        register(new com.anormal.client.module.impl.uzny11.Phase());
        register(new com.anormal.client.module.impl.uzny11.BedBreaker());
        register(new com.anormal.client.module.impl.uzny11.MurderFinder());
        register(new com.anormal.client.module.impl.uzny11.LeftClicker());
        register(new com.anormal.client.module.impl.uzny11.BowAimbot());
        register(new com.anormal.client.module.impl.uzny11.Speed());
        register(new com.anormal.client.module.impl.uzny11.Step());
        register(new com.anormal.client.module.impl.uzny11.Strafe());
        register(new com.anormal.client.module.impl.uzny11.Fly());
        register(new com.anormal.client.module.impl.uzny11.LongJump());
        register(new com.anormal.client.module.impl.uzny11.NoFall());
        register(new com.anormal.client.module.impl.uzny11.NoSlowdown());
        register(new com.anormal.client.module.impl.uzny11.OmniSprint());
        register(new com.anormal.client.module.impl.uzny11.Regen());
        register(new com.anormal.client.module.impl.uzny11.AutoHeal());
        register(new com.anormal.client.module.impl.uzny11.PotionSaver());
        register(new com.anormal.client.module.impl.uzny11.AntiFall());
        register(new com.anormal.client.module.impl.uzny11.HitBoxes());
        register(new com.anormal.client.module.impl.uzny11.InvWalk());
        register(new com.anormal.client.module.impl.uzny11.KillAura());
        register(new com.anormal.client.module.impl.uzny11.PropHunt());
        register(new com.anormal.client.module.impl.uzny11.BedPlates());
        register(new com.anormal.client.module.impl.uzny11.Animations());
        register(new com.anormal.client.module.impl.uzny11.CPSMod());
        // Uzny11 section is always OFF at startup (user enables manually)
        for (Module m : modules) {
            try {
                if (m.getCategory() == Category.UZNY11 && m.isEnabled()) m.setEnabled(false);
            } catch (Throwable ignored) {}
        }
        // --- CLIENT & SETTINGS ---
        register(new TextGUI());
        register(new ClientSettings());
        register(new Macros());
        register(new Friends());
        register(new Profiles());
        register(new AntiBot());
        register(new NameProtect());
        // Watermark renders last so the logo is never covered by other HUD
        register(new Watermark());
        register(new PlayerLogger());
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
