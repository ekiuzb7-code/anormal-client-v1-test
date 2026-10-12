# 5-bosqich: StorageESP / SpawnerFinder outline, ESP flicker + mob fix, barcha finderlar

Sana: 2026-10-13 · Minecraft **1.21.11** / Fabric

Barchasi `javac` (JDK 21) bilan tekshirildi — **o'zgargan fayllarda 0 xato**.

---

## 1. Flicker'ning ildiz sababi: `+= 2` skan qadami

**Muammo:** deyarli barcha finderlar va ba'zi ESP'lar skanni `x += 2`, `y += 2`, `z += 2`
qadami bilan yurardi. Bu degani — qaysi bloklar tekshirilishi **o'yinchining aniq koordinatasiga
bog'liq** edi. Bir blok yurishingiz bilan namuna olingan nuqtalar suriladi → markerlar
paydo bo'lib, yo'qolib turadi ("siljib yotadi").

**Ikkinchi sabab:** `found.size() < 300` kabi chegaralar skanni yarim yo'lda to'xtatardi —
o'yinchi yurganda to'xtash nuqtasi o'zgarib, markerlar to'plami beqaror bo'lardi.

**Tuzatilgan fayllar (qadam 2 → 1, chegara 2000, interval uzaytirildi):**

| Fayl | Oldin | Endi |
|---|---|---|
| `AncientCityFinder` | `+= 2`, cap 300, 40 tick | `+= 1`, cap 2000, 80 tick |
| `BaseFinder` | `+= 2`, cap 300, 40 tick | `+= 1`, cap 2000, 80 tick |
| `StashFinder` | `+= 2`, cap 400, 40 tick | `+= 1`, cap 2000, 80 tick |
| `StrongholdFinder` | `+= 2`, cap 64, 40 tick | `+= 1`, cap 2000, 80 tick |
| `VillageESP` | `+= 2`, 60 tick | `+= 1`, 100 tick |

> Endi loyihada **birorta ham `+= 2` skan qolmadi** (tekshirildi).

---

## 2. StorageESP — outline + per-block settings ✅

Butunlay qayta yozildi:

- **3D blok outline** (devor orqasidan ko'rinadi) + **translucent fill** — ikkalasi ham alohida
  yoqib/o'chiriladi.
- **Har bir blok turi uchun alohida toggle va rang:** Chests, Trapped Chests, Ender Chests,
  Hoppers, Furnaces (+blast/smoker), Dispensers, Droppers, Shulkers, Barrels, **Crafters** (yangi).
- Yangi render sozlamalari: `Outline`, `Fill`, `Fill Opacity`, `Outline Width`, `Max Rendered`.
- **Incremental skan** (har tick'da bitta gorizontal qatlam) — lag yo'q, natijalar barqaror.
- Bloklar `Blocks.X` bilan to'g'ridan-to'g'ri solishtiriladi (avval har blok uchun `Identifier`
  yasardi — juda sekin edi).

## 3. SpawnerFinder — outline settings ✅

- **3D blok outline** + **translucent fill** sozlamalari qo'shildi (`Outline`, `Fill`,
  `Fill Opacity`, `Outline Width`, `Max Rendered`).
- Skan qadami 1 + incremental (barqaror, lag yo'q).
- Spawner turi (Dungeon / Trial) endi skan paytida keshlanadi (render paytida qayta o'qilmaydi).

## 4. ESP — siljish va moblar tuzatildi ✅

**Siljish sababi:** box pozitsiyasi **1/16 blokka yumaloqlanardi** (`Math.round(x*16)/16`) va
kengligi balandlikdan hosil qilinardi (`w = h/3`). Ikkalasi ham box'ni kvantlab, entity
harakatlanganda sakrab-sakrab siljishiga olib kelardi.

**Yechim:** box endi entity'ning **haqiqiy AABB**'idan, `getLerpedPos(tickDelta)` bo'yicha
hisoblanadi — yumaloqlash yo'q, hosilaviy kenglik yo'q. Natijada silliq va aniq.

**Moblar sababi:** faqat `Monster` va `AnimalEntity` tekshirilardi — **slime, suv hayvonlari,
ambient va boshqa barcha moblar butunlay tashlab yuborilardi**.

**Yechim:** "Monsters" toggle endi **har qanday LivingEntity**'ni qamrab oladi (player va
passive animal'dan tashqari). Scoreboard-team orqali glow rangi ham shunga mos yangilandi.

---

## Umumiy yordamchi

`RenderUtils` ga ikkita umumiy metod qo'shildi (barcha blok-ESP modullar ishlatadi):

- `drawBlockOutline(context, pos, color, tickDelta, lineWidth)` — 3D wireframe box
- `drawBlockFill(context, pos, color, tickDelta, alpha)` — translucent fill

Bular orqali StorageESP, SpawnerFinder (va kelajakda boshqalar) bir xil ko'rinishga ega bo'ladi.
