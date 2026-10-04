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
        register(new BoatFly());

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
