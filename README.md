# Anormal Client (Minecraft 1.21.1 / Fabric)

A modern, highly customizable Minecraft Client built on **Fabric (1.21.1)** with two dynamic switchable UI themes: **Vape V4 Theme** and **Glassmorphism Theme**.

---

## ✨ Features & Architecture

### 🎨 2 Switchable Themes (Client Settings)
1. **Vape V4 Style**:
   - Dark charcoal matte panels (`#161616`, `#1e1e1e`).
   - Neon orange / cyan accents.
   - Smooth category tabs, sliders, mode selectors, and keybind badges.
2. **Glassmorphism Style**:
   - Translucent frosted glass effect (`rgba(18, 22, 34, 0.65)`).
   - Modern subtle glowing cyan/blue linear borders.
   - Sleek translucent cards and buttons.

> **Theme Switching**: Open ClickGUI -> Go to `Client` category -> Select `Theme: Vape V4` or `Theme: Glassmorphism`.

---

## ⌨️ Controls & Keybinds

1. **Minecraft Controls Integration**:
   - The ClickGUI keybind is registered directly into Minecraft's native settings:
   - Go to `Options` ➔ `Controls` ➔ `Key Binds` ➔ `Anormal Client` ➔ `Open ClickGUI Menu`.
   - Default key: **Right Shift (`RSHIFT`)**.
2. **In-GUI Keybind System**:
   - You can also bind any module to any keyboard key directly in the ClickGUI.

---

## 🚀 Modules Included (Based on Vape Docs)

- **Combat**: `AimAssist`, `AutoClicker`, `Triggerbot`, `WTap`, `JumpReset`, `HitSelect`, `Reach`, `SilentAura`
- **Movement**: `Sprint`, `NoJumpDelay`, `InvMove`
- **Render**: `Fullbright`, `ESP`, `Tracers`, `BlockOverlay`, `NoHurtCam`, `ClearWater`, `FreeLook`
- **Player**: `FastPlace`, `AutoTool`, `NoClickDelay`, `ChestStealer`
- **Legit HUD**: `Keystrokes`, `ArmorStatus`, `Coords`, `FPS`, `Clock`, `ReachDisplay`, `PotionStatus`
- **Client**: `TextGUI (ArrayList)`, `ClientSettings`

---

## 📦 Automated GitHub Actions JAR Build

The repository includes a GitHub Actions workflow located at [`.github/workflows/build.yml`](.github/workflows/build.yml).

Whenever you push commits to GitHub:
1. GitHub Actions sets up **JDK 21**.
2. Runs `./gradlew build`.
3. Compiles the Fabric Mod JAR.
4. Uploads the build artifact as `AnormalClient-1.21.1.jar` available for instant download in the Actions tab.
