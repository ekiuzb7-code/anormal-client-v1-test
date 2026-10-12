# 4-bosqich: ESPv2 → Meteor ESP, BackTrackV2/FakeLagV2 → LiquidBounce

Sana: 2026-10-13 · Minecraft **1.21.11** / Fabric · Yarn `1.21.11+build.6`

Barchasi `javac` (JDK 21) bilan kompilyatsiya qilinib tekshirildi — **o'zgargan fayllarda 0 xato**.

---

## 0. Yangi setting turlari (infratuzilma) ✅

LiquidBounce/Meteor'ning settingslarini aynan takrorlash uchun avval setting tizimi kengaytirildi:

| Yangi klass | Maqsad | Misol |
|---|---|---|
| `IntRangeSetting` | Butun son oralig'i (min..max) | LB `intRange("Delay", 300..600, 0..1000)` |
| `DoubleRangeSetting` | O'nlik son oralig'i | LB `floatRange("Range", 2f..5f, 0f..10f)` |
| `MultiSelectSetting` | Bir nechta variantni yoqish | LB `multiEnumChoice("FlushOn", ...)` |

**ClickGUI ham yangilandi:** range settinglar ikki tutqichli slider sifatida chiziladi (min/max alohida
sudraladi), multi-select esa ochiladigan ro'yxat (`☑/☐`) sifatida. Scroll balandligi ham hisobga olinadi.

---

## 1. ESPv2 → Meteor ESP ✅

ESPv2 butunlay qayta yozildi — Meteor ESP bilan **bir xil settings**:

`mode` (Box / Wireframe / 2D / Shader / Glow) · `highlight-target` · `target-hitbox` ·
`outline-width` (1..10) · `glow-multiplier` (3.5) · `ignore-self` · `shape-mode` (Lines / Sides / Both) ·
`fill-opacity` (0.3) · `fade-distance` (3) · `entities` (Players / Animals / Water Animals / Monsters /
Ambient / Misc / Items) · `color-mode` (Entity Type / Distance / Health) · `show-friend-colors` ·
`non-living-entity-color` · `players-color` · `animals-color` · `water-animals-color` · `monsters-color` ·
`ambient-color` · `misc-color` · `target-color` · `target-hitbox-color`

Sozlamalar ko'rinishi (visibleIf) ham Meteor bilan aynan: `outline-width` faqat Shader'da,
`fill-opacity` faqat Lines bo'lmaganda va Glow bo'lmaganda, `target-hitbox-color` faqat
highlight+hitbox yoqilganda va h.k.

**Rang mantiqi** Meteor'ning `getColor()` ini takrorlaydi: highlight-target → target color;
Entity Type / Distance / Health rejimlari; friend override; `fade-distance` bo'yicha alfa pasayishi.

---

## 2. FakeLagV2 → LiquidBounce ✅

**Ikkita jiddiy nuqson topildi va tuzatildi:**

1. **Mixin hech qachon ishlamagan.** `NetworkMixin` `ClientPlayNetworkHandler.sendPacket` ni
   nishonga olgan edi, lekin bu metod faqat **ota-klassda** (`ClientCommonNetworkHandler`)
   e'lon qilingan → Mixin uni topa olmay, `require = 0` tufayli jimgina o'tkazib yuborgan.
   Endi to'g'ri klassga bog'langan.
2. **BlinkManager paketlarni tashlab yuborardi.** `flushOutgoing()` faqat `poll()` qilardi —
   paketlar qayta yuborilmasdi, ya'ni server sizning pozitsiyangizni **umuman olmay** qolardi.
   Endi flush haqiqiy: har bir paket `handler.sendPacket()` orqali qayta yuboriladi.

**Settings endi LiquidBounce bilan bir xil:** `Range` (2..5), `Delay` (300..600 ms),
`RecoilTime` (250 ms), `Mode` (Constant / Dynamic), `FlushOn` (EntityInteract / BlockInteract / Action).
Mantiq ham bir xil: recoil → delay → flushOn → consumable → Dynamic (enemy nearby) → aks holda paket ushlanadi.

## 3. BackTrackV2 → LiquidBounce ✅

**Settings endi LiquidBounce bilan bir xil:** `Range` (1..3), `Delay` (100..150 ms),
`NextBacktrackDelay` (0..10 ms), `TrackingBuffer` (500 ms), `Chance` (50%),
`PauseOnHurtTime` + `HurtTime` (3), `TargetMode` (Attack / Range), `LastAttackTimeToWork` (1000 ms),
`Esp` (Box / Wireframe / None).

**Kiruvchi paket hook'i tuzatildi:** yangi `ConnectionMixin` → `ClientConnection.channelRead0`
(eski kod mavjud bo'lmagan metodni nishonga olgan edi). Endi dushmanning pozitsiya paketlari
ushlanadi va `Packet.apply(listener)` orqali haqiqiy qayta yuboriladi — natijada dushman o'tmishdagi
pozitsiyasida qotib turadi (backtrack). Teleport/o'lish/diapazondan chiqishda avtomatik flush.

---

## Muhim API topilmalari (1.21.11 Yarn)

- `ClientCommonNetworkHandler.sendPacket` — **ota-klassda**; `ClientPlayNetworkHandler` da emas.
- `ClientConnection.channelRead0(ChannelHandlerContext, Packet<?>)` — kiruvchi paketlar nuqtasi.
- `Packet.apply(T listener)` — paketni qayta yuborish uchun (to'g'ridan-to'g'ri dispatch).
- `EntityPositionS2CPacket.entityId()` (record accessor), `EntityS2CPacket.getEntity(World)`.
- `Entity.getBoundingBox()` — ESP uchun AABB.
