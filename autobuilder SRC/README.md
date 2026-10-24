# Auto cc — Auto Builder

Client-side Fabric mod for Minecraft **1.21.11** that reads Litematica `.litematic` files and  
builds them for you, block by block, in survival: it walks to each cell, selects the right hotbar  
item, and performs the click — including the state clicks redstone needs (repeater delay,  
comparator mode, lever, note block, and so on).

## Requirements

|               |                                     |
| ------------- | ----------------------------------- |
| Minecraft     | 1.21.11 (Java Edition, client only) |
| Fabric Loader | 0.19.5+                             |
| Fabric API    | 0.141.6+1.21.11                     |
| Java          | 21 (to build from source)           |

## Build from source

```bash
./gradlew build          # Windows: gradlew.bat build
```

The mod jar lands in `build/libs/auto-cc-1.0.0.jar` — drop that into `.minecraft/mods/`  
together with Fabric API.

To launch a development client instead:

```bash
./gradlew runClient      # dev game directory is ./run
```

The Gradle wrapper is included, so no local Gradle install is needed (it downloads Gradle 9.7.1  
with the versions pinned in `gradle.properties`). The first build also downloads Minecraft,  
Yarn mappings and Fabric API, so it needs network access.

## Using it in game

1. Open the Auto cc screen with **Right Shift** (`key.auto-cc.open`).
2. Drag a `.litematic` file onto the window (files are also listed from the schematics folder).
3. Pick the schematic, press **Start**, and the builder takes over — it pauses while the menu is  
   open and resumes when you close it.

## Source layout

- `src/client/java/dev/autocc/client/AutoCcClient.java` — mod entrypoint, keybind, tick hook
- `src/client/java/dev/autocc/client/build/` — schematic planner, build order, path planner,  
  progress tracker and `BuildEngine` (the builder itself: walking, placing, redstone state clicks)
- `src/client/java/dev/autocc/client/gui/` — the Auto cc screen
- `src/client/java/dev/autocc/client/litematic/` — `.litematic` reader plus the isometric preview
- `src/main/resources/` — `fabric.mod.json` and language file
- `tools/` — Python helpers used while developing: `litematic_dump.py` prints a schematic's  
  palette and block counts, `make_ceiling_test.py` / `make_state_test.py` generate test  
  schematics

## Notes

- Crouch/sprint are driven through a helper that copes with Minecraft's **toggle crouch** and  
  **toggle sprint** options: when a toggle is on, holding a key binding is not possible, so the  
  engine taps it instead of pressing and holding. This is what lets the builder click a block it  
  is standing next to while crouched on a toggle-crouch client.
