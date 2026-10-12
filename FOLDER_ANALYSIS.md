# Anormal Client — Papka Tahlili (Folder Analysis)

**Tahlil sanasi:** 2026-10-12
**Papka:** `N:/workbuddy/outputs/client`

---

## 1. Umumiy ma'lumot

Bu papka — **Anormal Client** nomli **Minecraft Fabric mod** (utility/hack client) loyihasi.
Loyiha Vape V4, LiquidBounce va Wurst kabi mashhur clientlardan ilhomlangan.

| Ko'rsatkich | Qiymat |
|---|---|
| Turi | Minecraft Fabric Client Mod (Java) |
| Maqsadli versiya | **Minecraft 1.21.11** (README'da xato: 1.21.1) |
| Java | 21 |
| Build tizimi | Gradle + Fabric Loom 1.14-SNAPSHOT |
| Java fayllar | **195 ta** |
| Umumiy kod hajmi | **~20 082 satr** |
| Modullar | **~171 ta** (8 kategoriya) |
| Mixinlar | 6 ta |
| Muallif | ANORMAL444 |

---

## 2. Papka tuzilishi

```
client/
├── src/main/java/com/anormal/client/     # Asosiy kod (195 fayl)
│   ├── AnormalClient.java                # Kirish nuqtasi (ClientModInitializer)
│   ├── gui/                              # ClickGUI, HudEditor, XRay selector
│   ├── mixin/                            # 6 ta Mixin
│   ├── module/
│   │   ├── Module.java, ModuleManager.java, Category.java
│   │   └── impl/                         # 8 kategoriyadagi modullar
│   ├── setting/                          # Boolean, Color, Keybind, Mode, Number
│   ├── theme/                            # Theme, ThemeManager
│   └── util/                             # ColorUtils, ProjectionUtil, RenderUtils
├── src/main/resources/                   # fabric.mod.json, mixins.json, assets
├── .github/workflows/build.yml           # CI: JAR build qilish
├── build/                                # 17 MB, bo'sh (klass yo'q)
├── gradle/wrapper/                       # Gradle 8.10.2
├── README.md, error.txt
├── vape_module.md (97 KB), vape_extra.md (47 KB)   # Reference hujjatlar
└── FreeLook_vs_FreeCam_Minecraft_Detailed.md (20 KB)
```

### Modul kategoriyalari
| Kategoriya | Modullar soni |
|---|---|
| Render | 54 |
| Legit HUD | 38 |
| World | 23 |
| Combat | 17 |
| Movement | 11 |
| Player | 10 |
| Inventory | 10 |
| Client | 8 |
| **Jami** | **~171** |

---

## 3. Arxitektura

- **Kirish nuqtasi:** `AnormalClient.java` → `ClientModInitializer` orqali ishga tushadi.
- **Modul tizimi:** `ModuleManager.init()` da barcha ~171 modul ro'yxatga olinadi. `Module` bazaviy klassidan meros oladi (`onTick`, `onRender2D`, `toggle`).
- **Sozlamalar:** `Setting` bazaviy klassi + 5 xil tur (Boolean, Color, Keybind, Mode, Number).
- **Tema tizimi:** `ThemeManager` 2 ta tema — **Vape V4** (to'q, orange accent) va **Glassmorphism** (shaffof, neon ko'k).
- **Keybind:** Right Shift → ClickGUI; Minecraft'ning native Controls menyusiga integratsiya qilingan.
- **Mixinlar:** `GameRendererMixin`, `XRayMixin`, `CameraMixin`, `NetworkMixin`, `ClientAccessor`, `GammaAccessor`.
- **HUD:** `HudRenderCallback` orqali 2D overlay'lar; `HudEditorScreen` bilan joylashuvni saqlaydi.

---

## 4. Aniqlangan muammolar (Muhim)

### 4.1 Versiya ziddiyati (README eskirgan)
- `README.md` → **Minecraft 1.21.1** deb yozilgan.
- `gradle.properties`, `fabric.mod.json`, CI workflow → **1.21.11**.
- **Tavsiya:** README'dagi versiyani 1.21.11 ga yangilash.

### 4.2 `error.txt` — eskirgan xato logi
- Faylda GitHub Actions'dagi build xatosi: `FakeLagV2.java:121: cannot find symbol getFoodComponent()`.
- **Lekin** joriy kod allaqachon tuzatilgan — endi `stack.get(DataComponentTypes.FOOD)` ishlatiladi.
- Ya'ni bu fayl **eski** log; repodan o'chirish yoki yangilash tavsiya etiladi.

### 4.3 Build holati
- `build/classes` ichida **0 ta `.class` fayl**, `build/libs` ichida **JAR yo'q** → loyiha shu papkada muvaffaqiyatli build qilinmagan.
- `error.txt` bo'yicha oxirgi CI build **FAILED** bo'lgan (endi tuzatilgan ko'rinadi).

### 4.4 Gradle versiyalari mos emas
- `gradle/wrapper/gradle-wrapper.properties` → **Gradle 8.10.2**.
- CI (`build.yml`) → `gradle-version: '9.2.1'` va `gradle remapJar` (wrapper emas, global `gradle`) ishlatadi.
- **Tavsiya:** CI'ni `./gradlew` ga o'tkazish yoki wrapper'ni 9.2.1 ga ko'tarish.

### 4.5 `LICENSE` fayli yo'q
- `build.gradle` `jar { from("LICENSE") }` deb LICENSE'ni kutadi, `fabric.mod.json` esa MIT litsenziyasini e'lon qiladi — ammo papkada LICENSE fayli yo'q.

### 4.6 Papkadagi ortiqcha fayllar
- `a416bb57-...-removebg-preview (1).png` (769 KB) — ildizda turgan tasodifiy rasm.
- `vape_module.md` + `vape_extra.md` (jami 144 KB) — reference hujjatlar, kod emas.
- `build/` (17 MB) — `.gitignore`da bor, lekin lokal qolgan.
- `.claude/settings.local.json` — commit tarixini ochib beruvchi lokal sozlamalar fayli.

---

## 5. Xulosa

Loyiha **yaxshi tuzilgan, katta hajmli** Fabric client: 195 fayl, ~20k satr, ~171 modul, ikki temali zamonaviy GUI.
Kod arxitekturasi toza (modul/sozlama/tema/mixin bo'linmalari).

**Ustuvor tuzatishlar:**
1. README versiyasini 1.21.11 ga moslashtirish.
2. Eskirgan `error.txt` ni o'chirish/yangilash.
3. Gradle wrapper va CI versiyasini birlashtirish.
4. LICENSE faylini qo'shish.
5. Ildizdagi ortiqcha rasm va hujjatlarni tozalash.
6. Lokal `./gradlew build` orqali build'ni tekshirish.
