# 6-bosqich: Scaffold v1-v4

Sana: 2026-10-13 · Minecraft **1.21.11** / Fabric

`javac` (JDK 21) bilan tekshirildi — **0 xato**.

---

## Scaffold — v1/v2/v3/v4 rejimlar ✅

Yangi `Version` sozlamasi. Har bir rejim o'z source'ining ichki settingslari va ishlashini
takrorlaydi (faqat tanlangan rejim sozlamalari ko'rinadi).

| Rejim | Manba | Sozlamalar |
|---|---|---|
| **V1** | Meteor | Blocks Filter, Fast Tower + Tower Speed, While Moving, Only On Click, Swing, Auto Switch, Rotate, Air Place, Ahead Distance, Closest Block Range, Radius, Blocks Per Tick |
| **V2** | Wurst (`ScaffoldWalk`) | **Sozlama yo'q** — Wurst'da ham umuman sozlama yo'q, shuning uchun aynan shunday qoldirildi |
| **V3** | LiquidBounce | Delay (`intRange` 0..40), MinDist (0..0.25), Timer (0.01..10), SameY (Off/On), Consider Inventory, Rotation Timing (Normal/Pre/Post), Swing (DoNotHide/Hide/None), FailedAttemptsOnly, AutoSpeed, Ledge, Technique (Normal/GodBridge/Breezily/Expand) |
| **V4** | Vape | Pitch, Mode (Legit/Blatant/Telly), Pitch Check, Block Count Display, Whitelist, Blacklist |

**Umumiy:** `Block Count` (hotbar'dagi bloklar soni), `Pitch Check`.

**Ishlash:** maqsad pozitsiya (tagida, `Ahead Distance` bo'lsa oldinda) topiladi → hotbar'dan mos
blok tanlanadi (V1 filter: to'liq kub va yiqilmaydigan; V4 whitelist/blacklist) → slotga o'tib,
qo'shni blok yuzasiga bosib joylashtiriladi → slot qaytariladi.
V3'da texnikalar (`GodBridge` oldinga, `Breezily` yarim, `Expand` kengroq) va ledge'da sneak bor.

---

## API tekshiruvlari

- `AbstractBlockState.isFullCube(BlockView, BlockPos)` — V1 "blocks filter" uchun.
- `BlockPos.ofFloored(double,double,double)`, `BlockPos.ORIGIN`.
- `IntRangeSetting` (4-bosqichda qo'shilgan) V3 `delay` uchun ishlatildi.
