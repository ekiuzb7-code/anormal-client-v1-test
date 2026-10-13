# Round 8 — SafeWalk (Wurst port) + GitHub Actions build

## 1. SafeWalk — Wurst bilan bir xil

Wurst'ning `SafeWalkHack` + `LocalPlayerMixin` mexanizmi 1:1 portlandi.

### Nima o'zgardi

Eski SafeWalk **har tick** sneak tugmasini qo'lda bosib-o'chirardi (`onTick` + timer).
Bu ishonchsiz edi: tick'ga bog'liq, va vanilla o'yinchini chekkadan **to'xtatmasdi**.

Wurst boshqa yo'l tutadi — u **vanilla'ning o'z "chekkada to'xtash" kodini** yoqadi:

| Wurst (Mojang mappings) | 1.21.11 Yarn | Vazifa |
|---|---|---|
| `Player.isStayingOnGroundSurface()` | `PlayerEntity.clipAtLedge()` | `true` qaytarsa → vanilla o'yinchini chekkada to'xtatadi |
| `Player.maybeBackOffFromEdge(Vec3, MoverType)` | `PlayerEntity.adjustMovementForSneaking(Vec3d, MovementType)` | Harakatni haqiqatan kesadi; kirish≠chiqish bo'lsa "chekkada ushlandi" degani |

Yangi `PlayerEntityMixin` aynan shu ikki nuqtaga inject qiladi:
- `clipAtLedge()` HEAD'da `true` qaytaradi (SafeWalk yoqilganda) → **endi chekkadan yura olmaysiz**.
- `adjustMovementForSneaking()` RETURN'da `!movement.equals(result)` ni hisoblab modulga uzatadi → **ko'rinadigan sneak** shu signal bilan boshqariladi.

### Settings — Wurst bilan aynan bir xil

| Setting | Tur | Default | Min | Max | Step | Wurst'dagi nomi |
|---|---|---|---|---|---|---|
| `Sneak at edges` | Boolean | **false** | — | — | — | "Sneak at edges" — *Visibly sneak at edges.* |
| `Sneak edge distance` | Number | **0.05** | 0.05 | 0.25 | 0.001 | "Sneak edge distance" |

Wurst'dagi kabi **boshqa hech qanday sozlama yo'q** (eski `On Ground Only`,
`Blocks Only`, `Edge Distance`, `Release Delay`, `Strict Mode` olib tashlandi).

### Mantiq (Wurst bilan satr-ba-satr)

```
onClipAtLedge(clipping):
    if !enabled || !sneak || !player.isOnGround():
        if sneaking: setSneaking(false); return

    box         = player.getBoundingBox()
    adjustedBox = box.expand(0, -player.getStepHeight(), 0)
                     .contract(edgeDistance, 0, edgeDistance)
    if world.isSpaceEmpty(player, adjustedBox):
        clipping = true
    setSneaking(clipping)

setSneaking(b):
    if b: sneakKey.setPressed(true)
    else: sneakKey.setPressed(isActuallyDown(sneakKey))   // resetPressedState()
```

`isActuallyDown()` — Wurst'dagi `IKeyMapping.isActuallyDown()` ning ekvivalenti:
`InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()).getCode()` →
`InputUtil.isKeyPressed(window, code)` (mouse uchun `GLFW.glfwGetMouseButton`).

`onEnable()` da Wurst kabi **Parkour avtomatik o'chiriladi** (ikkisi ziddiyatli).

---

## 2. GitHub Actions build

Workflow allaqachon to'g'ri edi; faqat artefakt tozalandi:

```yaml
path: |
  build/libs/AnormalClient-*.jar
  !build/libs/*-dev.jar
  !build/libs/*-sources.jar
```

Endi artefaktda faqat **ishlatiladigan remapped jar** bo'ladi (`-dev` va `-sources` chiqarib tashlanadi).

### CI zanjiri (tekshirilgan)
- Java **21** (temurin) ✓
- Gradle **9.2.1** ✓
- `gradle remapJar` ✓
- Artefakt: `build/libs/AnormalClient-*.jar` ✓
- `gradle.properties`: MC **1.21.11**, yarn `1.21.11+build.6:v2`, loader **0.18.1**, fabric-api **0.141.6+1.21.11** ✓

### Push qilishdan oldin ishonch hosil qiling
Yangi/o'zgargan fayllar ham ko'chirilishi kerak:
- `src/main/java/com/anormal/client/mixin/PlayerEntityMixin.java` **(yangi)**
- `src/main/java/com/anormal/client/module/impl/movement/SafeWalk.java` **(qayta yozildi)**
- `src/main/resources/anormalclient.mixins.json` **(PlayerEntityMixin qo'shildi)**
- `.github/workflows/build.yml` **(artefakt filtri)**

## 3. Tekshiruv

- `clipAtLedge`, `adjustMovementForSneaking`, `getStepHeight`, `Box.expand`,
  `Box.contract`, `CollisionView.isSpaceEmpty(Entity, Box)`, `InputUtil.isKeyPressed`,
  `Window.getHandle` — barchasi haqiqiy `minecraft-merged-1.21.11` jar'idan `javap` bilan tasdiqlandi.
- Butun loyiha `javac` (JDK 21) bilan kompilyatsiya qilindi → **o'zgargan fayllarda 0 xato**.
- `anormalclient.mixins.json` — valid JSON, 12 mixin (PlayerEntityMixin bilan).
