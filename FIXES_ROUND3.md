# 3-bosqich tuzatishlar

Sana: 2026-10-13 · Minecraft **1.21.11** / Fabric · Yarn `1.21.11+build.6`

Barchasi `javac` (JDK 21) bilan kompilyatsiya qilinib tekshirildi — **o'zgargan fayllarda 0 xato**.

---

## 1. AutoCrystal — v1/v2/v3/v4 rejimlar ✅

Eski `CrystalAura` o'rniga yangi `AutoCrystal` moduli yozildi. Yangi `Version` sozlamasi —
har bir rejim o'z source'ining ichki settingslari va ishlashini takrorlaydi (faqat tanlangan
rejim sozlamalari ko'rinadi):

| Rejim | Manba | Asosiy settings |
|---|---|---|
| **V1** | Meteor | Target Range, Predict Movement, Min/Max Damage, Anti Suicide, Ignore Nakeds, Rotate, Auto Switch, Switch Delay, Place + Place Delay/Range, Break + Break Delay/Range, Only Own, Ticks Existed, Attack Frequency, Pause On Use/Mine, Pause Health, Swing |
| **V2** | Wurst | Range, Auto Place, Check LOS (minimal implementatsiya) |
| **V3** | LiquidBounce | Place Swing/Switch, 1_12_2, Place Delay/Range/Walls Range, Only Above, Sequenced, Jitter, Break Swing/Delay/Range/Walls Range, Prioritize Visible Faces |
| **V4** | Vape | Target Range, Min Damage, Max Self Damage, Anti Suicide, Place + Delay, Break + Delay, Rotate + Aim Speed, Auto Switch, Pause On Use |

Umumiy: `Show Target` + rang, `Center Screen` (crystal counter).
Ishlash: target tanlash → avval mavjud kristallni portlatish → keyin obsidian/bedrock ustiga
yangi kristall qo'yish. Damage hisoblash (target va o'zimiz uchun), anti-suicide, rotate, auto-switch.

> `CrystalAura.java` o'chirildi, `ModuleManager` `AutoCrystal` ga o'tkazildi.

## 2. ItemESP — jitter/flicker tuzatildi ✅

**Sabab:** tag **xom (tick'ga kvantlangan)** pozitsiyadan proyeksiya qilinardi → item
qimirlaganda tegi titrab, o'qib bo'lmasdi. Yana ramka (fon) matn o'lchamidan mustaqil
hisoblanardi → mos kelmasdi.

- Endi pozitsiya `Entity.getLerpedPos(tickDelta)` dan olinadi (har render freymda interpolyatsiya).
- Fon va matn **bir xil transform** ostida chiziladi (matritsa scaling).

## 3. Tracers — tuzatildi ✅

**Sabab:** xuddi shu — endpointlar xom pozitsiyadan hisoblanardi, shuning uchun chiziqlar
titrar va ekran chetida yo'qolib-yonib turardi.

- Endpointlar endi interpolyatsiya qilinadi.
- `Max Distance` va `Thickness` sozlamalari qo'shildi.
- Chizish optimallashtirildi (2000 nuqta chegarasi).

## 4. ProjectionUtil — ildiz sabab tuzatildi (47 modulga ta'sir qiladi) ✅

Bu **eng muhim** tuzatish. `ProjectionUtil.project()` ni 47 ta modul ishlatadi (ESP, ItemESP,
Tracers, XRay markers, EndPortalFinder, TrialChambersFinder, Waypoints...).

**Sabab:** eski versiya ko'z pozitsiyasini o'zi `prevEye/lastEye` maydonlari bilan kuzatardi va
har chaqiruvda ularni qayta yozardi. HUD renderer `project()` ni **har bir entity uchun bir
marta** chaqiradi → faqat **birinchi** entity interpolyatsiya qilingan ko'zni olardi, qolganlari
xom pozitsiyadan foydalanardi → ular titrardi.

**Yechim:** ko'z pozitsiyasi endi `Entity.getLerpedPos(tickDelta)` dan olinadi — Minecraft uni
har render freymda yangilab turadi. Natijada **barcha** entitylar silliq.

---

## Muhim API topilmalari (1.21.11 Yarn)

- `Entity.getLerpedPos(float tickDelta)` — har freymda interpolyatsiya qilingan pozitsiya.
- `Entity.lastRenderX/Y/Z`, `getLastRenderPos()` ham mavjud.
- `DrawContext.getMatrices()` → `org.joml.Matrix3x2fStack` (`pushMatrix`/`translate`/`scale`).
- `Entity.getYaw(float)` / `getPitch(float)` — interpolyatsiya qilingan burchaklar.
- `PlayerEntity.getArmor()`, `World.isAir(BlockPos)`, `BlockState.isOf(Block)` mavjud.
