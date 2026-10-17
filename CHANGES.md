# AutoBuilder fix — nima o'zgardi

Papkalar loyiha ildiziga (`anormal/`) shu yo'l bilan ustiga yoziladi.

## Qotib qolish sabablari (topilgan)
1. **Ghost (Show world)** har kadrda `HashMap` ni aylanib, har blok uchun 8 ta proyeksiya + 12 ta chiziq
   (piksel-piksel `fill`) chizardi → 1400 blokda ~10^5..10^6 GUI element/kadr. Bloklar tasodifiy (hash) tartibda tanlanardi.
2. **Reja (plan)** asosiy oqimda, har bir surish/aylantirishda to'liq hisoblanardi
   (har blok uchun `getBlockState` + `findFace` = 6 ta qo'shimcha o'qish + sort).
3. `rebuild()` (rotate/mirror) ham asosiy oqimda, hatto faqat X/Y/Z surilganda ham qayta ishlardi.
4. Executor `tick()` har tikda reja uzunligidan 2 barobar skan qilardi.
5. GUI 3D preview har kadrda butun `HashMap` ni aylanardi.

## Tuzatishlar
- `GhostShell` (yangi): sirt bloklari bir marta, worker thread'da int[] massivlarga yig'iladi (ichki ko'milgan bloklar tashlanadi).
  Ghost va GUI preview shu massivdan foydalanadi.
- Ghost: faqat eng yaqin 500 blok, 48 blok radiusda, kameraga juda yaqinlari o'tkaziladi, blok konturlari yo'q (bounding box qoldi).
  Opacity 0.45 ga ko'paytiriladi (2D to'rtburchaklar bloklardan kattaroq).
- Rotate/mirror va shell: worker thread'da, faqat oxirgi so'rov qo'llanadi. Surish endi qayta transform qilmaydi.
- Planner: vaqt bo'laklab (6 ms/tik), sort alohida thread'da, `findFace` kerak bo'lganda. Progress haqiqiy (Planning 37%).
- Executor: tik boshiga max 600 vazifa; tugagan vazifalar tartibni buzmasdan belgilanadi (avval swap-with-last
  tartibni buzardi: tom devordan oldin qo'yilardi). Qurilish paytida joy/aylantirish o'zgarsa to'xtatiladi.
- Transformer: Minecraft'ning o'zining `BlockState.rotate/mirror` ishlatiladi (devor/panjara/zinapoya/eshik/rels
  to'g'ri aylanadi; avval north/south/east/west/shape xossalari aylantirilmasdi).
- Footer progress: placed / (placed + remaining).

## Cheklovlar
- Ghost hamon 2D proyeksiya (loyihada 3D world-render hook yo'q): terrain orqasida yashirinmaydi, haqiqiy blok modeli emas.
- `MaterialAnalyzer` hali asosiy oqimda (Materials ochilganda/Refresh bosilganda bir marta).
- Men bu yerda kompilyatsiya qila olmadim (javac yo'q). Faqat 1.21.11 yarn jar'da mavjudligi tekshirilgan API'lar ishlatilgan.
