# zaza.hook [TRIDENT]

A Fabric **26.2** client mod with a Right-Shift toggle UI: frosted-glass panels,
GPU-rendered SDF rounded rects, MSDF text, six complete color themes, and
shatter/dissolve close animations.

## Install (dev)
- Java 25 toolchain (auto-provisioned by Gradle via foojay resolver)
- `./gradlew build` → `build/libs/zazahook-1.0.0.jar`
- `./gradlew runClient` to test in-game

## Install (player)
- Fabric Loader 0.19.5+, Minecraft ~26.2, Fabric API 0.159.0+26.2
- Drop the jar into `.minecraft/mods`

## Usage
- **Right Shift** opens/closes the UI (rebindable under `zazahook > open_gui`)
- Tabs: `combat` `visuals` `local` `world` `configurações`
- Drag the title bar to move the panel; Escape or Right Shift closes it

## Configurações page
- **Toolbar:** sound set (glass / click / synth), volume slider, close animation
  (shatter / dissolve), reset
- **Complete color themes:** six cards with isometric 3D cube swatches —
  red/black, ice/midnight, violet/graphite, lime/terminal, gold/sepia,
  pink/obsidian. Switching crossfades every UI color over ~360 ms.
- **Appearance:** Modo Fundo (Sólido / Acrílico / Imagem), image Upload with
  Cover / Contain / Stretch fit modes, Opacidade Fundo and Escurecimento Tint
  sliders.

## Rendering stack (26.2-native)
- Custom `RenderPipeline`s submitted through the 26.2 GUI render-state system
  (`GuiElementRenderState`) — no legacy VertexRendering
- SDF rounded-rect + hairline-border shaders with 4×4 supersampled coverage
- MSDF (multi-channel signed distance field) text: Inter Bold + Material Icons
- Kawase-blur frosted-glass post pass with refraction, dispersion and fresnel
  rim (Acrílico mode + HUD chips)
- All animation via a time-based `Transition` primitive (EASE_OUT_EXPO /
  EASE_OUT_SETTLE / EASE_IN_CUBIC per direction)

## Modules
- **Combat:** aim assist — FOV-capped targeting (multipoint/head/body bones), Regular or Silent rotation,
  Ease-out-cubic / WindMouse smoothing, smoothness slider, on-hold and weapons-only options,
  closest-to-FOV or lowest-health priority. Settings are editable in the GUI's settings pane and
  persist to `config/zazahook.json`.
- **Local:** sprint, no fall, safe walk
- **Visuals:** full bright, no hurt cam, custom fov, no rain
- **World/util:** fps, coordinates, auto tool, fast place

## Aim assist internals (ported from Cold Client)
- `rotation/` — `RotationManager` + `Rotator` strategies (`EaseOutCubic`, `WindMouse`), GCD-quantized
  silent rotations sent via `LocalPlayer.sendPosition`, camera turns for visible mode, render-state
  body rotation sync, server-correction re-anchoring via `ClientPacketListener`.
- Mixins: `MouseHandler.turnPlayer` (frame turns), `LocalPlayer.sendPosition` (outgoing rot),
  `Entity.getViewVector` (gameplay look vector), `EntityRenderer.createRenderState` (player model),
  `Minecraft.startAttack` (don't mine while tracking), `ClientPacketListener` handleMove/handleRotate.

## Config
`config/zazahook.json` (auto-created): module toggles, aim assist settings, theme, background mode,
opacity, dim, sound set, volume, close animation.
