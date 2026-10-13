# Round 9 — 24 fixes + ports

Everything below compiles clean (`tools/compile-check.sh`, JDK 21, real
`minecraft-merged-1.21.11` jar). Only `AnormalClient.java` reports errors
locally, and those are the known intermediary/classpath artifact that does not
exist in a real Loom build.

---

## 1. The root cause behind almost every "flicker / shifting" report

`ProjectionUtil` — used by **47 modules** — read `mc.player.getYaw()/getPitch()`
(the **tick-quantised** values, updated 20×/second) and rebuilt the eye height
itself from `getEyePos().y - getY()`. Minecraft renders with **interpolated**
values, so every marker was drawn against a camera that did not match the one
the world was rendered with. The faster you moved, the further the markers slid.

**Fix:** the projection now reads the real render camera —
`Camera.getYaw()`, `Camera.getPitch()`, `Camera.getCameraPos()` — which
`Camera.update()` interpolates once per frame. It is cached once per frame by
`ProjectionUtil.beginFrame()`, called from `ModuleManager.onRender2D`. Freecam
and FreeLook are handled for free, because `CameraMixin` writes their transform
into that same object.

**This one change fixed:** SpawnerFinder, EndPortalFinder, ArmorStandESP,
OreESP, TrialChambersFinder, PlayerESP and every other marker.

Also fixed in `PlayerESP`: the box was built from a **1/16-rounded** centre with
a **derived width** (`w = h/3`). Now it uses the real interpolated AABB.

## 2. The root cause behind the TrialChambersFinder crash

Two problems, both fixed:

* **Scan:** the old code swept a full sphere slice **every tick** — up to
  257×257 = 66 000 world lookups per tick at max range.
* **Render:** up to 3000 blocks × 8 corners projected per frame, and
  `RenderUtils.drawLine` emitted **one `fill()` per pixel** of a line that could
  stretch far off-screen — millions of draw calls per frame.

Fixes: a **budgeted incremental scanner** (`util/BlockScanner`, nearest-first
offsets, constant cost per tick), a **hard per-frame render time budget**, and
**off-screen culling** before any geometry is emitted. `RenderUtils.drawLine`
now clips to the screen (Liang–Barsky) and merges horizontal runs into single
fills. `drawBlockOutline`/`drawBlockFill` gained a culling fast-path.

Same treatment applied to `EndPortalFinder` (which was doing ~1.6 **million**
lookups in one tick) and `SpawnerFinder`.

## 3. Module-by-module

| # | Request | Result |
|---|---|---|
| 1 | HUD editor can't reach the screen edge | `moveBy` now clamps the **measured frame**, not raw `posX`, so modules that draw offset/centred can reach both edges |
| 2 | TrialChambersFinder freeze/crash | Budgeted scan + render time budget + culling |
| 3 | KillAura v1–v4 | **New.** V1 Meteor, V2 Wurst, V3 LiquidBounce, V4 Vape. SilentAura untouched |
| 4 | AutoCrystal v2 mob/name bug | It was targeting `HostileEntity`. Added Target Players/Mobs/Animals + Ignore Invisible/Friends; players only by default |
| 5 | Scaffold placed a 9×9 slab | It searched the whole radius box and placed at the first air block. Now places **only** the one block you are about to stand on; the radius is a fallback for finding support |
| 6 | TargetLock | **New**, LiquidBounce port (Combat, FilterType, MaximumTime, MaximumRange, WhenNoLock + rotation/render) |
| 7 | AutoTool → Wurst | Rewritten verbatim: Use swords / Use hands / Repair mode / Switch back |
| 8 | BlockOverlay broken | It drew a **screen-space square** sized by distance. Now a real 3D block outline + fill |
| 9 | NoWeather → Wurst | New module + `WorldMixin` (`getRainGradient`, `getTimeOfDay`). Old crude `legit/NoWeather` deleted |
| 10 | BlockHit / HitFlick / HitSelect → Vape | All three rewritten with Vape's item gate (swords/food/potions) and behaviour settings |
| 11 | SpawnerFinder outline sliding | Fixed by the `ProjectionUtil` change |
| 12 | WTap v1/v2 | V1 Vape (Sprint Reset + Right Click Cancel), V2 fixed-tick reset |
| 13 | TriggerBot v1/v2 | V1 Wurst (Range, Speed, randomization, swing hand, attack while blocking, simulate click), V2 Vape (early hit chance, shield check, miss chance, air crits, …) |
| 14 | Criticals v1/v2 | V1 Meteor, V2 Wurst, V3 LiquidBounce (all three sources have it) |
| 15 | GUI mouse scroll | Wheel scroll clamped to content, plus a **draggable scrollbar** with a widened hit box |
| 16 | Arrows confusing | Rewritten: real filled triangle with outline, camera-space direction from the render camera, name + distance labels |
| 17 | Nametags → LiquidBounce | Rewritten with LB's settings: BorderWidth, BackgroundRadius, equipment slots + fill/outline, enchantment list + scale + max count + filter, and text parts |
| 18 | ArmorStandESP flicker | Now uses `getLerpedPos(tickDelta)` + real AABB + shared line drawing |
| 19 | OreESP per-ore outline + colour | Every ore has its own toggle, **outline toggle** and **outline colour**; real 3D outlines; budgeted scan; no sliding |
| 20 | Waypoints list/rename/delete | **New `WaypointsScreen`**: full list with name, coords, dimension, distance; per-row toggle / rename (inline typing) / copy coords / delete; "+ Add Here"; scrollable. Opened from a new "⌖ Waypoints" button in the ClickGUI header |
| 21 | AntiAFK → Wurst | Rewritten: Use AI, AI range, Non-AI range, Wait time, Wait time randomization, Show wait time |
| 22 | AutoArmor v1/v2 | V1 Meteor, V2 Wurst, V3 Vape |
| 23 | EndPortalFinder sliding | Fixed by the `ProjectionUtil` change (+ budgeted scan + real outlines) |
| 24 | NoHunger → AntiHunger v1/v2 | `NoHunger` deleted. **New AntiHunger**: V1 Wurst (rewrites the outgoing movement packet's `onGround` to false — the exact Wurst behaviour, via `NetworkMixin`), V2 LiquidBounce (WhileSwimming, KeepFloating) |

## 4. New plumbing

* `util/BlockScanner` — shared budgeted ellipsoid scanner (nearest-first).
* `ProjectionUtil.beginFrame()` + `cameraSpace()` + `cameraYaw/Pitch()`.
* `InteractionManagerMixin` — hooks `ClientPlayerInteractionManager.attackEntity`,
  the single choke point for **all** attacks. It drives HitSelect (may cancel),
  Criticals (must run before the hit) and WTap / HitFlick / BlockHit (after).
  Without this, those five modules would never have fired.
* `WorldMixin` — NoWeather.
* `NetworkMixin` — AntiHunger packet rewrite added.

## 5. Minecraft 1.21.11 API notes discovered

* `Camera.getYaw()/getPitch()/getCameraPos()` — interpolated render camera.
* `Camera` has **no** `getMoonPhase`; `World` has no moon-phase getter either,
  so NoWeather's moon-phase option is present for parity but is a no-op.
* **`SwordItem` and `ArmorItem` no longer exist.** Swords/armour are identified
  by registry path (`_sword`, `_helmet`, `_chestplate`, `_leggings`, `_boots`).
* `ItemEnchantmentsComponent.getEnchantments()` returns
  `Set<RegistryEntry<Enchantment>>`; get the id with
  `entry.getKey().map(k -> k.getValue().getPath())`.
* `PlayerMoveC2SPacket` subclasses take an extra `horizontalCollision` flag.
* `ClientPlayerInteractionManager.attackEntity(PlayerEntity, Entity)`.

## 6. Verify

```
bash tools/compile-check.sh
```

Expected: `=== files with errors ===` lists only `AnormalClient.java`, and
`=== non-AnormalClient errors ===` prints `NONE`.

---

## 7. CI fix — `reference to NoWeather is ambiguous`

**The failure:** `ModuleManager.java:77: reference to NoWeather is ambiguous` —
both `render.NoWeather` and `legit.NoWeather` matched.

**Why it only appeared in CI:** this checkout is not a git working copy. Files
are copied into the repository by hand, and **copying cannot delete**. The new
`render/NoWeather.java` arrived, the obsolete `legit/NoWeather.java` stayed, and
`ModuleManager`'s wildcard imports (`impl.render.*` and `impl.legit.*`) then made
`new NoWeather()` genuinely ambiguous. The local build was green because the old
file had been deleted here.

**The fix:** the registration is now fully qualified, so it resolves even when
the stale file is present:

```java
register(new com.anormal.client.module.impl.render.NoWeather());
```

Verified by temporarily re-creating `legit/NoWeather.java` and recompiling: the
project stays clean. (WorldMixin was never affected - it uses an explicit
single-type import, which always beats a wildcard.)

**Prevention:** `tools/compile-check.sh` now prints a
`=== duplicate simple class names ===` section before compiling. Anything listed
there means the repository still holds a stale copy of a moved file and the
build will fail with "reference is ambiguous".

**Action required:** delete the stale files. See `DELETED_FILES.md`:

* `module/impl/legit/NoWeather.java`
* `module/impl/player/NoHunger.java`
* `module/impl/combat/CrystalAura.java`

### About the other line in the log

```
Cannot remap modifiers because it does not exists in any of the targets [] or their parents.
```

This is a **benign Loom warning**, not an error - it is emitted while Loom
builds the mixin refmap and the build continues normally afterwards (the only
fatal line was the compile error above). It comes from the empty
`"mixins": []` section of `anormalclient.mixins.json`, which is the standard
form for a client-only mod. No change needed.
