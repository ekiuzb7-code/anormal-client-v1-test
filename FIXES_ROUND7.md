# 7-bosqich: Flight v1-v4

Sana: 2026-10-13 · Minecraft **1.21.11** / Fabric

`javac` (JDK 21) bilan tekshirildi — **0 xato**.

---

## Flight — v1/v2/v3/v4 rejimlar ✅

Yangi `Flight` moduli (Movement kategoriyasida) — loyihada ilgari Fly/Flight umuman yo'q edi.
Yangi `Version` sozlamasi; har bir rejim o'z source'ining ichki settingslari va ishlashini takrorlaydi.

| Rejim | Manba | Sozlamalar |
|---|---|---|
| **V1** | Meteor | Mode (Abilities / Velocity), Speed, Vertical Speed Match, No Sneak, AntiKick (Normal / Packet / None) + AntiKick Delay + Off Time |
| **V2** | Wurst | Horizontal Speed, Vertical Speed, Allow Unsafe Vertical Speed, Scroll To Change Speed, Show Speed, Anti-Kick + Interval + Distance |
| **V3** | LiquidBounce | Mode (Generic / Hypixel / Grim / Polar / Sentinel / Spartan / Verus / Vulcan / Enderpearl / Fireball), DisableOnSetback, Stride |
| **V4** | Vape | Speed, Vertical Speed |

**Umumiy:** `Show Speed` — HUD'da joriy tezlikni ko'rsatadi.

### Ishlash

- **V1 Abilities** rejimi: `PlayerAbilities.flying = true`, `allowFlying = true`, `setFlySpeed(speed)` —
  vanilla uchish fizikasi ishlatiladi (Meteor kabi).
- **Boshqa barcha rejimlar (velocity flight):** har tick'da yaw bo'yicha forward/right vektorlar
  hisoblanadi, WASD inputi qo'llanadi, diagonal normallashtiriladi, jump/sneak vertikal tezlikni
  beradi va **gravitatsiya kompensatsiyasi (+0.08)** qo'shiladi — shu sababli o'yinchi haqiqatan
  havoda turadi (aks holda har tick 0.08 ga cho'kardi).
- **Anti-kick:** V1 (Delay/OffTime) va V2 (Interval/Distance) davriy ravishda pastga tezlik berib,
  serverning "flying is not enabled" kick'ini oldini oladi.
- V3 texnikalari har xil tezlik presetlariga ega (Hypixel 0.25, Grim 0.08, ...).

### Holatni saqlash

`onEnable` da eski abilities (`flying`, `allowFlying`, `flySpeed`) saqlanadi va `onDisable` da
qaytariladi — modul o'chirilganda o'yinchi havoda qotib qolmaydi.

---

## API tekshiruvlari

- `PlayerAbilities` — `flying`, `allowFlying` (public maydonlar), `getFlySpeed()` / `setFlySpeed(float)`.
- `PlayerEntity.getAbilities()`.
- `Entity.setOnGround(boolean)`.
- `Entity.fallDistance` — **double** (float emas).
