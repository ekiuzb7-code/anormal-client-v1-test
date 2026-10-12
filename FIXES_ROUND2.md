# 2-bosqich tuzatishlar

Sana: 2026-10-13 · Minecraft **1.21.11** / Fabric · Yarn `1.21.11+build.6`

Barchasi `javac` (JDK 21) bilan kompilyatsiya qilinib tekshirildi — **o'zgargan fayllarda 0 xato**.

---

## 1. TrialChambersFinder — blok ESP + har bir tur uchun toggle ✅

Modul butunlay qayta yozildi.

- **Endi trial chamberdagi HAR BIR blok turi 3D wireframe outline bilan devor orqasidan ko'rinadi** (avval faqat chamber markazida kichik kvadrat chizilardi).
- **Per-block toggles** (settings ichida): `Trial Spawners`, `Vaults`, `Copper` (+ barcha waxed variantlari), `Tuff`, `Deepslate`, `Stone Bricks`, `Dark Oak`, `Misc` (pots, crafters, chests, bars, glass, lodestone...). Har birini alohida o'chirish mumkin.
- Har bir kategoriya uchun alohida rang.
- **Incremental skan**: har tick'da bitta gorizontal qatlam → lag yo'q.
- Bloklar registry orqali bir marta yechib olinadi (`==` bilan solishtiriladi) — juda tez.
- Qo'shimcha: `Outline`, `Center Dot`, `Show Distance`, `Max Rendered`, `Chat Log`.

## 2. EndPortalFinder — flicker tuzatildi ✅

**Sabab:** skan qadami `+= 2` edi → portal blokini topish o'yinchining koordinata juftligiga bog'liq edi. Bir blok yursangiz marker yo'qolib qolardi.

- Qadam **1** ga o'zgartirildi.
- Natijalar atomik almashtiriladi (eski markerlar skan tugaguncha yo'qolmaydi).
- `end_portal` va `end_portal_frame` alohida ko'rsatiladi (`Show Portal` / `Show Frames` + alohida ranglar).
- Yangi `Vertical Range`, `Chat Log`.

## 3. Clutch — Vape hujjati bo'yicha qayta yozildi ✅

`vape_module.md` dagi Clutch bo'limi o'qildi va settings aynan mos qilindi:

`On Void` · `On Lethal Fall` · `On More Than Blocks` + `Blocks` · `Silent Aim` · `Show Block Count` · `Reset Angle` · `Return To Slot` · `Allow Staircase Up` · `Clutch Move Delay` · `Max Blocks` · `Block Selection` (Off / Blacklist / Whitelist).

**Ishlashi:** yiqilishni har tick tekshiradi → pastga qaraydi → ostidagi birinchi qattiq yuza ustiga blok qo'yadi; pastda hech narsa bo'lmasa yon devorga qo'yadi (void uchun). `Max Blocks` limiti, hotbar slotni tiklash, harakatni muzlatish — hammasi ishlaydi.

## 4. HUD editor ramkalari tuzatildi ✅

**Sabab:** ramkalar modul chizgan haqiqiy kontentga emas, qo'lda yozilgan taxminiy o'lchamlarga (`defaultSize`) asoslanardi → ramka kontentga to'g'ri kelmasdi.

- `RenderUtils` ga **measurement mode** qo'shildi: modul render qilganda chizilgan barcha text/fill chegaralari yozib olinadi.
- Editor endi har bir element uchun `beginMeasure() → onRender2D() → endMeasure()` qiladi va **ramkani haqiqiy chizilgan kontent atrofida** chizadi (o'lchov bo'lmasa zaxira o'lchamga qaytadi).
- Drag endi **delta asosida** (mouse harakatiga qarab) ishlaydi — ramka kontentdan siljigan bo'lsa ham to'g'ri.
- Hit-test yuqoridan pastga; bosilgan element oldinga chiqadi.
- Label joylashuvi tuzatildi (joy bo'lmasa pastga tushadi).

## 5. AutoTotem — v1/v2/v3/v4 rejimlar ✅

Yangi `Version` sozlamasi. Har bir rejim o'z source'ining settings va ishlashini takrorlaydi (faqat tanlangan rejim sozlamalari ko'rinadi):

| Rejim | Manba | Sozlamalar |
|---|---|---|
| **V1** | Meteor | Mode (Smart/Strict), Delay, Health, Elytra, Fall, Explosion (damage prediction bilan) |
| **V2** | Wurst | Show Totem Counter, Delay, Health (hearts, 0 = ignore), 2-click move |
| **V3** | LiquidBounce | Switch Delay, Switch Back Delay, Health + Threshold, Safety + Safe Health, Subtract Calculated Damage, Predict Explosion Entities/Blocks, Predict Fall Damage, Ignore Elytra, Missing Armor, Switch Back, Send Directly |
| **V4** | Vape | Open Inventory, Silent Open, Silent Move Delay, Close Inventory, Inventory Only, Combat Only, Activation Delay, Refill, Random Slot, Delay, Extra Randomization, Show Totem Count |

---

## Hali qilinmagan (keyingi bosqich)

Quyidagilar **yangi setting turlarini** va **3D world render** ni talab qiladi — alohida bosqichda bajariladi:

1. **ESPv2 → Meteor ESP** — Meteor'da ~20 ta setting (`mode` Box/Wireframe/2D/Glow, `shape-mode`, `fill-opacity`, `fade-distance`, `entities` ro'yxati, `color-mode`, 8 ta rang...). To'liq mos qilish uchun entity-list va 3D box render kerak.
2. **BackTrackV2 / FakeLagV2 → LiquidBounce** — LiquidBounce'da `floatRange` (Range 2..5), `intRange` (Delay 300..600 ms), `multiEnumChoice` (FlushOn) kabi setting turlari bor. Bizning setting tizimida range/multi-select turlari yo'q — avval ularni qo'shish kerak.
3. **AutoCrystal → LiquidBounce** — eng katta port (prediction, rotation, damage hisoblash).

> Bularning barchasi uchun avval `Setting` tizimiga `IntRangeSetting`, `DoubleRangeSetting`, `MultiSelectSetting` va `StringListSetting` qo'shish tavsiya etiladi — shundan keyin paritet to'liq bo'ladi.
