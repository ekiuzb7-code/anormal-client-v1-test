# Tuzatishlar — XRay / FreeLook / FreeCam / TrialChambersFinder

Sana: 2026-10-12 · Maqsad: Minecraft **1.21.11** / Fabric · Yarn `1.21.11+build.6`

Barcha metod imzolari haqiqiy `minecraft-merged-1.21.11` jar'idan `javap` bilan tekshirilgan.
Butun loyiha `javac` bilan kompilyatsiya qilinib sinovdan o'tkazildi — **o'zgargan fayllar toza**.

---

## 1. XRay — umuman ishlamas edi

**Asosiy sabab (root cause):**
`XRayMixin` `@Mixin(BlockState.class)` ga bog'langan va `getRenderType()` ga inject qilgan edi.
Lekin bu metod **`BlockState` da e'lon qilinmagan** — u `AbstractBlock$AbstractBlockState` da.
Mixin metodni topa olmay, `require = 0` tufayli **jimgina o'tkazib yuborilgan** → XRay hech narsa qilmagan.

Qo'shimcha sabablar:
- Bloklarni yashirishning o'zi yetarli emas: chunk mesher qo'shni blok opaq bo'lsa yuzni cull qiladi → ko'milgan ruda ko'rinmaydi.
- Smart-culling (chunk occlusion) er ostida butun chunklarni o'chirib qo'yadi.
- Suyuqliklar (suv/lava) butunlay boshqa yo'l (`FluidRenderer`) orqali chiziladi.

**Tuzatish — 4 ta mixin:**

| Fayl | Vazifa |
|---|---|
| `XRayMixin.java` | Endi `AbstractBlock.AbstractBlockState.getRenderType()` ga inject → whitelist'da yo'q bloklar `BlockRenderType.INVISIBLE`. Tasdiqlangan: `SectionBuilder` `getRenderType() != MODEL` bo'lsa blokni chizmaydi. |
| `BlockModelRendererMixin.java` | `shouldDrawFace(...)` → whitelist bloklari uchun har doim `true` (devor orqasidan ko'rinadi). |
| `ChunkOcclusionDataBuilderMixin.java` | `markClosed(...)` bekor qilinadi → smart-culling o'chadi. |
| `FluidRendererMixin.java` | Tanlanmagan suyuqliklar chizilmaydi. |

Eslatma: eski `FluidStateMixin` (bir faylda, `mixins.json`da ro'yxatga olinmagan) o'chirildi — u mavjud bo'lmagan `FluidState.getRenderType()` ni nishonga olgan edi.

---

## 2. FreeLook / FreeCam — noto'g'ri ishlamas edi

**Sabablar:**
1. `CameraMixin` `Camera.update()` ni **HEAD'da cancel** qilardi. Aynan shu metod `ready = true` ni o'rnatadi — natijada kamera "ready" bo'lmay, dunyo chizilmasligi mumkin edi.
2. FreeLook kamera masofasi sifatida **`mc.options.getFov().getValue()`** (~70) ishlatilardi → kamera 70 blok narida turardi.
3. FreeLook o'yinchi tanasining burilishini tiklamasdi → sichqoncha bilan tana ham burilardi (aslida decouple bo'lmasdi).
4. Sichqoncha harakati tick (20/s) da o'qilardi → titroq.

**Tuzatish:**
- `CameraMixin` endi **`@At("RETURN")`** da inject qiladi (vanilla ishlaydi, keyin pos/rot ustidan yoziladi) — `ready` va boshqa ichki holat buzilmaydi.
- FreeLook: kamera **`eye - lookDir * 4`** formulasi bilan to'g'ri orbitada joylashadi (look vektori standart MC formulasi bilan hisoblanadi).
- Yangi `EntityMixin` — `Entity.changeLookDirection(double, double)` HEAD'da ushlanadi va **bekor qilinadi** (`cancel`), delta modulga uzatiladi. Natijada:
  - kamera har **freymda** silliq buriladi,
  - o'yinchi tanasi umuman burilmaydi.
  (Vanilla faktori `* 0.15` aynan takrorlangan.)
- `Freecam` qayta yozildi: pozitsiya interpolatsiyasi (`getInterpolatedX/Y/Z(tickProgress)`) → silliq harakat; tana anchor'da qotiriladi.
- Ishlamaydigan sozlamalar (`spawnFake`, `moveFake`, `v2KeepSneaking`, `v2NoPitchLimit`) olib tashlandi.

---

## 3. TrialChambersFinder — topmas edi

**Asosiy sabab:** skanerlash har o'q bo'yicha **`+= 2`** qadam bilan yurardi → hududning faqat **1/8** qismi tekshirilardi. Trial spawner/vault'lar ixtiyoriy koordinatalarda joylashadi, shuning uchun deyarli hammasi o'tkazib yuborilardi.

Bundan tashqari har bir blok uchun `Registries.BLOCK.getId(...)` bilan `Identifier` yasash juda sekin edi.

**Tuzatish:**
- Qadam **1** (to'liq skan).
- Bloklar bir marta yechib olinadi (`Registries.BLOCK.get(Identifier.of(...))`) va `==` bilan solishtiriladi (juda tez).
- `Blocks.OMINOUS_VAULT` 1.21.11'da alohida maydon emas → registry orqali xavfsiz yechiladi.
- Yangi `Vertical Range` sozlamasi (default ±32) — trial chamberlar chuqurda joylashgani uchun.
- Klaster radiusi 20 blok, `onDisable` da tozalanadi.

---

## 4. Yo'l-yo'lakay tuzatilgan CI xatolari

| Fayl | Muammo | Tuzatish |
|---|---|---|
| `NoClickDelay.java` | `mc.attackCooldown` **protected** → kompilyatsiya xatosi (CI build'ni buzardi) | `ClientAccessor` ga `@Accessor("attackCooldown") setAttackCooldown(int)` qo'shildi |
| `ModuleManager.java` | `getModule()` har chaqiruvda ~170 modulni stream bilan skanerlardi — yangi mixinlar buni **juda ko'p** marta chaqiradi | `Class → Module` HashMap keshi |

---

## 5. O'zgargan / qo'shilgan fayllar

**Mixinlar:** `XRayMixin`, `BlockModelRendererMixin` (yangi), `ChunkOcclusionDataBuilderMixin` (yangi),
`FluidRendererMixin` (yangi), `EntityMixin` (yangi), `CameraMixin`, `ClientAccessor`
**Modullar:** `Freecam`, `FreeLook`, `TrialChambersFinder`, `NoClickDelay`, `ModuleManager`
**Resurs:** `anormalclient.mixins.json` (4 ta yangi mixin ro'yxatga olindi)

## 6. Tekshiruv

- Har bir metod imzosi 1.21.11 Yarn jar'idan `javap` bilan tasdiqlangan.
- Butun loyiha `javac` (JDK 21) bilan kompilyatsiya qilindi — **o'zgargan fayllarda 0 xato**.
- GitHub Actions'da `gradle remapJar` ishlatiladi — o'zgarishlar CI'ga mos.
