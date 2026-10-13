# Files that must be DELETED from the repository

This checkout is not a git working copy - files are copied into the real repo by
hand. Copying **cannot delete** anything, so every file that was removed or
renamed here is still present in the repository and can break the build.

The build failure `reference to NoWeather is ambiguous` was caused by exactly
this: the new `render/NoWeather.java` was copied in, but the obsolete
`legit/NoWeather.java` was never deleted.

**Delete these paths from the repository before pushing.**

| Path | Removed in | Why |
|---|---|---|
| `src/main/java/com/anormal/client/module/impl/legit/NoWeather.java` | Round 9 | Replaced by `render/NoWeather.java` (Wurst port). Two classes with the same simple name made `new NoWeather()` ambiguous. |
| `src/main/java/com/anormal/client/module/impl/player/NoHunger.java` | Round 9 | Replaced by `movement/AntiHunger.java` (Wurst + LiquidBounce). |
| `src/main/java/com/anormal/client/module/impl/combat/CrystalAura.java` | Round 3 | Replaced by `combat/AutoCrystal.java`. Unused, but stale. |

## How to check

From the repository root:

```bash
# Should print nothing:
ls src/main/java/com/anormal/client/module/impl/legit/NoWeather.java \
   src/main/java/com/anormal/client/module/impl/player/NoHunger.java \
   src/main/java/com/anormal/client/module/impl/combat/CrystalAura.java 2>/dev/null
```

Or run the duplicate-name guard that is now part of the local check:

```bash
bash tools/compile-check.sh
```

It prints a `=== duplicate simple class names ===` section at the top. If that
section lists anything, the repository still has a stale copy of a moved file
and the build will fail with "reference is ambiguous".

## Safety net

`ModuleManager` now refers to this one module by its full name:

```java
register(new com.anormal.client.module.impl.render.NoWeather());
```

so the build survives a stale `legit/NoWeather.java`. That is a guard, not a
fix - please still delete the old file so there is only one `NoWeather`.
