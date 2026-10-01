# Vape.gg Overlays, Legit Modules, Misc and Settings

Sources:
- https://docs.vape.gg/features/overlays/
- https://docs.vape.gg/features/modules/legit/
- https://docs.vape.gg/features/misc/Profiles/
- https://docs.vape.gg/features/misc/Friends/
- https://docs.vape.gg/features/misc/Macros
- https://docs.vape.gg/features/settings/

This file contains text from 42 requested pages, including all overlay and Legit module detail pages listed on their index pages.

---

## overlays

**Source:** [https://docs.vape.gg/features/overlays/](https://docs.vape.gg/features/overlays/)

# Overlays

Overlays are GUI components provided by Vape that render helpful information on your HUD.

You can open the Overlays menu to configure which ones are shown by:

- Opening the Vape GUI
- Clicking the Overlays Icon in the bottom right
- Enable the Overlays you want to use
- Drag them around to your desired positions
- Click the thumbtack icon at the top right of the Overlay component to make the Overlay visible once you close the GUI

![Overlays GUI](/img/overlays.png)

---

[## Duel Info

Shows difference in hits and potions used in 1v1's](/features/overlays/Duel-Info)

[## Inventory

Displays your inventory on the HUD](/features/overlays/Inventory)

[## Party Overlay

Shows Vape Friends' inventories and status](/features/overlays/Party-Overlay)

[## Radar

Shows where other players are around you](/features/overlays/Radar)

[## Rearview

Tapping F5 twice is too much work, anyways](/features/overlays/Rearview)

[## Target Info

Displays your target's health, potion usage, and more](/features/overlays/Target-Info)

[## Text GUI

Displays a list of enabled modules](/features/overlays/Text-GUI)

---

## Duel-Info

**Source:** [https://docs.vape.gg/features/overlays/Duel-Info](https://docs.vape.gg/features/overlays/Duel-Info)

# Duel Info Overlay

Provides a real-time display of your duel's progress, showing the difference in hits landed and potions used between you and your opponent.

## How it Works[​](#how-it-works "Direct link to How it Works")

- The overlay automatically tracks the player you last hit.
- The left side shows the difference in potions thrown and hits landed.
- Positive values (+4) indicate your advantage, negative values (-4) your opponent's.
- Lines visually represent the advantage/disadvantage, moving left or right as the duel progresses.
- Sword Hits line moves left when you hit, right when you're hit.
- Potions line moves similarly based on potion usage.

This module is especially helpful for PotPvP and Practice servers, giving you immediate feedback on your performance relative to your opponent.

---

## Inventory

**Source:** [https://docs.vape.gg/features/overlays/Inventory](https://docs.vape.gg/features/overlays/Inventory)

# Inventory Overlay

Displays the three main rows of your inventory on the HUD, including item stack counts. The overlay can also display your hotbar as a fourth row.

---

### Show Hotbar[​](#show-hotbar "Direct link to Show Hotbar")

Displays your nine hotbar slots below the main inventory rows.

### Render Background[​](#render-background "Direct link to Render Background")

Renders a dark, blurred background and individual slot backgrounds behind the inventory.

---

## Party-Overlay

**Source:** [https://docs.vape.gg/features/overlays/Party-Overlay](https://docs.vape.gg/features/overlays/Party-Overlay)

# Party Overlay

Shows you the real-time status of other Vapers in your **[Vape Online Party](/features/misc/Online/#parties)**, when you're on the same server as them.

Importantly, Party Overlay communicates all this information through Vape Online's servers, meaning it completely bypasses any 3rd-party plugins that the Server has installed to prevent you from reading other Player's information (e.g., on servers where NameTags reports everyone is at 0.5 hearts).

![](/img/overlays/party-overlay.png)

The overlay shows the following information:

- **Current Health**: The health of each player in your party
- **Equipment**: The held item and armor of each player in your party
- **CPS**: How fast they are currently clicking
- **Distance**: How far they are from you
- **Direction**: Their direction from you
- **Inventory**: The contents of their inventory (provided they are [**sharing their inventory**](/features/misc/Online/Options))

![](/img/overlays/party-overlay-inventory.png)

---

### Show Inventory Bind[​](#show-inventory-bind "Direct link to Show Inventory Bind")

The keybind used to show the inventory of your Vape Online Party members.

### Bind Mode[​](#bind-mode "Direct link to Bind Mode")

How the keybind acts.

- **Toggle**: Once pressed, the Party Overlay will show the contents of your Party Members' inventories until it is pressed again.
- **Hold**: The Party Overlay will show the contents of your Party Members' inventories while the hotkey is pressed down.

### Show Self[​](#show-self "Direct link to Show Self")

Whether or not to show yourself in the Party Overlay.

### Render Background[​](#render-background "Direct link to Render Background")

Renders a slightly opaque greyish background behind the Party Overlay, making it easier to see for some people.

### CPS Display[​](#cps-display "Direct link to CPS Display")

Renders the CPS of each of your Party Members, while they are clicking.

---

## Radar

**Source:** [https://docs.vape.gg/features/overlays/Radar](https://docs.vape.gg/features/overlays/Radar)

# Radar Overlay

Displays a real-time radar on your HUD, showing the positions of other players.

## Mode[​](#mode "Direct link to Mode")

The format of the Radar-- how it is actually drawn on your HUD.

### Text Radar[​](#text-radar "Direct link to Text Radar")

A text-based list of players, and their distances from you.

- **Max Distance**: The maximum distance a player can be from you, for them to be shown on the radar.
- **Max Shown**: The maximum amount of players to render on the radar.

### 2D Radar[​](#2d-radar "Direct link to 2D Radar")

A visual radar overlay.

- **Radar Size**: Controls the size of the minimap.
- **Radar Scale**: Adjusts the zoom level of the minimap.
- **Show Cross**: Displays a crosshair centered at your current position on the minimap.
- **Clamp Radar**: Keeps all dots within the minimap, regardless of scale or size.

#### Color Mode[​](#color-mode "Direct link to Color Mode")

How to color the dots displayed on the Radar, representing other players.

- **Relationship**: Colors dots based on their relationship to you (friend or foe).
  - **Friendly Color**: Custom color for friendly players.
  - **Enemy Color**: Custom color for enemy players.
- **Team**: Colors dots based on their team (server-dependent).
- **Custom**: Uses a single custom color for all players.

#### Dot Style[​](#dot-style "Direct link to Dot Style")

The style of the dots shown on the Radar.

- **Circles**: Draw the dots as circles.
- **Squares**: Draw the dots as squares.

#### Radar Style[​](#radar-style "Direct link to Radar Style")

The style and shape of the Radar itself.

- **Circle**: Draw the Radar as a circle.
- **Square**: Draw the Radar as a square.

## Show Background[​](#show-background "Direct link to Show Background")

Adds a background to the radar for better visibility.

---

## Rearview

**Source:** [https://docs.vape.gg/features/overlays/Rearview](https://docs.vape.gg/features/overlays/Rearview)

# Rearview Overlay

Adds a real-time "rearview mirror" to your HUD, showing what's happening behind you.

---

### Size[​](#size "Direct link to Size")

Adjusts the size of the rearview mirror overlay.

### FPS[​](#fps "Direct link to FPS")

Sets the frame rate for the overlay. Higher values may impact game performance.

### FOV[​](#fov "Direct link to FOV")

Controls the field of view of the mirrored view. Higher values provide a wider view.

### Level View[​](#level-view "Direct link to Level View")

Keeps the mirrored view level with your current Y position, even if you're looking up or down.

---

## Target-Info

**Source:** [https://docs.vape.gg/features/overlays/Target-Info](https://docs.vape.gg/features/overlays/Target-Info)

# Target Info Overlay

Displays real-time information about your current target, including health, damage comparison, combo counter, and potion usage.

---

### Show Hovered[​](#show-hovered "Direct link to Show Hovered")

- When enabled, the overlay shows information about the entity you're looking at.
- When disabled, it shows information about the player you last attacked.

### Damage Comparator[​](#damage-comparator "Direct link to Damage Comparator")

Displays the difference in damage per swing between you and your opponent, helping you gauge your relative strength.

- Negative number: You have the disadvantage.
- Positive number: You have the advantage.
- Zero: Equal matchup.

### Combo Counter[​](#combo-counter "Direct link to Combo Counter")

Tracks the number of consecutive hits landed without being hit back.

- Positive number: You are on a combo streak.
- Negative number: You are being combo'd.
- Zero: Equal matchup.

### Hits Comparator[​](#hits-comparator "Direct link to Hits Comparator")

Shows the difference in the number of hits landed between you and your opponent.

- Positive number: You have landed more hits.
- Negative number: Your opponent has landed more hits.
- Zero: Equal matchup.

### Pots Used[​](#pots-used "Direct link to Pots Used")

Displays the difference in the number of potions used.

- Positive number: Your opponent has used more potions.
- Negative number: You have used more potions.
- Zero: Equal matchup.

---

## Text-GUI

**Source:** [https://docs.vape.gg/features/overlays/Text-GUI](https://docs.vape.gg/features/overlays/Text-GUI)

# Text GUI Overlay

Displays a list of your currently enabled modules, offering various customization options.

---

### Sort[​](#sort "Direct link to Sort")

Controls the order in which the module names are displayed on the GUI.

- **Alphabetical**: Sorts modules alphabetically.
- **Length**: Sorts modules by the length of their names.

### Suffix Mode[​](#suffix-mode "Direct link to Suffix Mode")

Controls the amount of additional information displayed next to each Module's name.

- **Extended**: Adds the greatest level of detail for each Module (e.g. shows CPS for **[**AutoClicker**](/features/modules/combat/AutoClicker)**).
- **Basic**: Adds only a broad level of detail for each Module (e.g. shows the selected **[**Scaffold Mode**](/features/modules/utility/Scaffold#mode)**).
- **None**: Displays no additional information, only the Module's name.

### Color Mode[​](#color-mode "Direct link to Color Mode")

- **Module Color**: Uses a unique color for each module.
- **Match GUI Color**: Uses your GUI Theme color.
- **Custom Color**: Uses a custom color you specify.
  - **Text GUI Color**: The color used on the Text GUI.

### Visual Settings[​](#visual-settings "Direct link to Visual Settings")

- **Scale**: Adjusts the size of the text.
- **Shadow**: Adds a drop shadow to the text for better readability.
- **Animations**: Adds smooth animations when modules are enabled or disabled.
- **Smooth Font**: Uses the Vape GUI font instead of your resource pack's font.
- **Watermark**: Displays the Vape V4 logo above the list.
- **Render Background**: Adds a dark background behind the text.

### Filtering and Customization[​](#filtering-and-customization "Direct link to Filtering and Customization")

- **Hide Modules**: Allows you to hide specific modules from the list.
- **Add Custom Text**: Lets you add custom text to the overlay.
  - **Set Custom Text Color**: Customizes the color of the added text.

### Misc[​](#misc "Direct link to Misc")

- **Click Disable**: Allows you to click on a Module's name in the Text GUI to quickly toggle it on or off. Note that the module will still show on your Text GUI, just rendered with a `Disabled` tag next to it until you turn it off from the Client GUI, or once you click on it again to toggle it back on.

---

## legit

**Source:** [https://docs.vape.gg/features/modules/legit/](https://docs.vape.gg/features/modules/legit/)

# Legit mod menu

The Legit mod menu provides you with mods that are typically seen in legit clients. Such as KeyStrokes, ArmorStatus, etc. These mods are designed to enhance your gameplay experience without giving you an unfair advantage over other players.

---

## Legit Modules GUI[​](#legit-modules-gui "Direct link to Legit Modules GUI")

You can open the legit mod menu by clicking the switch icon at the top of the Vape GUI.

![Legit menu switch](/img/gui/legit_menu_switch.png)

![Legit Module Icon](/img/legit-modules-gui.png)

Inside the Legit Modules GUI, you can filter the modules shown by the four tabs shown:

- Favorite: Only shows you the modules you have marked as your "favorite", which you can do by clicking the three dots at the top right of a module's card, then clicking the star icon at the top right of the slide-in options menu that pops up.
- All: Shows you all the legit modules.
- HUD: Only shows you legit modules that do something with your HUD (e.g. ArmorStatus displays your armor's durability on your screen, so it's a HUD module).
- Game: Only shows you legit modules that do something with your game (e.g. NoJumpDelay removes the delay between jumps after hitting your head on a block, so it's a game module).

---

[## ArmorStatus

See your armor's durability outside your Inventory](/features/modules/legit/ArmorStatus)

[## Block Overlay

Customize how a Block looks when you're looking at it](/features/modules/legit/Block-Overlay)

[## Blockhit Animation

Use 1.7-style animations for blockhitting](/features/modules/legit/Blockhit-Animation)

[## Clear Water

Digital diving goggles](/features/modules/legit/Clear-Water)

[## Clock

Displays the real-world time on your HUD](/features/modules/legit/Clock)

[## Compass

Displays a compass on your HUD](/features/modules/legit/Compass)

[## Coords

Displays your coordinattes on your HUD](/features/modules/legit/Coords)

[## FPS

Displays your FPS on your HUD](/features/modules/legit/FPS)

[## FreeLook

Lets you look side-to-side without changing direction](/features/modules/legit/FreeLook)

[## Hit Color

Customize the colour entities turn after they're hit](/features/modules/legit/Hit-Color)

[## Inventory Blur

Adds a cinematic blur to the background when a container is open](/features/modules/legit/Inventory-Blur)

[## Keystrokes

Displays your keystrokes on your HUD](/features/modules/legit/Keystrokes)

[## MouseDelayFix

Fixes a bug in 1.8.x that causes aiming and hits to be inaccurate](/features/modules/legit/MouseDelayFix)

[## NoClickDelay

Removes the click delay after missing an attack](/features/modules/legit/NoClickDelay)

[## NoHurtCam

Removes the camera flinch when taking damage.](/features/modules/legit/NoHurtCam)

[## NoJumpDelay

Removes the jump delay after bonking your head on a block](/features/modules/legit/NoJumpDelay)

[## Potion Status

Displays your current status effects on your HUD](/features/modules/legit/Potion-Status)

[## Reach Display

Displays the range of your last attack on your HUD](/features/modules/legit/Reach-Display)

[## Scoreboard

Customize the scoreboard shown on the side of your HUD](/features/modules/legit/Scoreboard)

[## Time Changer

Turn day into night, and night into day](/features/modules/legit/Time-Changer)

[## Weather

Turn rain into sunshine, and sunshine into rain.](/features/modules/legit/Weather)

---

## ArmorStatus

**Source:** [https://docs.vape.gg/features/modules/legit/ArmorStatus](https://docs.vape.gg/features/modules/legit/ArmorStatus)

# ArmorStatus

The ArmorStatus module displays an element on your HUD showing you the armor you're currently wearing, as well as the durability of each piece of armor.

![](/img/armor-status.png)

---

### Render Background[​](#render-background "Direct link to Render Background")

When enabled, a slightly opaque grey background will be shown behind the HUD element, making the text easier to read.

### Compact[​](#compact "Direct link to Compact")

When enabled, the style of the HUD element will be made more compact, aligning itself vertically instead of horizontally. Useful if you play on a smaller resolution, or if you just prefer less visual clutter.

---

## Block-Overlay

**Source:** [https://docs.vape.gg/features/modules/legit/Block-Overlay](https://docs.vape.gg/features/modules/legit/Block-Overlay)

# Block Overlay

Changes the way the block you're looking at is highlighted, from Vanilla MC's black outline to whatever color you can dream of.

![](/img/block-overlay.png)

---

### Overlay Color[​](#overlay-color "Direct link to Overlay Color")

The color to paint the inside of the block with.

### Outline Color[​](#outline-color "Direct link to Outline Color")

The color to paint the outline of the block with.

---

## Blockhit-Animation

**Source:** [https://docs.vape.gg/features/modules/legit/Blockhit-Animation](https://docs.vape.gg/features/modules/legit/Blockhit-Animation)

# Blockhit Animation

Replaces the current blockhit animation (swinging a sword while right-clicking) with the one used in Minecraft version 1.7.10.

tip

This is purely a visual change, it has no impact on the mechanics of blockhitting.

---

## Clear-Water

**Source:** [https://docs.vape.gg/features/modules/legit/Clear-Water](https://docs.vape.gg/features/modules/legit/Clear-Water)

# Clear Water

The Clear Water module disables the "fog" that limits how far you can see while underwater in Vanilla MC.

---

## Clock

**Source:** [https://docs.vape.gg/features/modules/legit/Clock](https://docs.vape.gg/features/modules/legit/Clock)

# Clock

Displays the real-world time (and optionally the date) on your HUD, ensuring you never miss dinner time.

---

### Render Background[​](#render-background "Direct link to Render Background")

Adds a subtle grey background to improve text readability.

### Clock Type[​](#clock-type "Direct link to Clock Type")

- **Digital**: Displays a digital clock, similar to an alarm clock.
  - **Show Date**: Includes the current date alongside the time.
- **Analog**: Displays a classic analog clock face.

### 24 Hour Time[​](#24-hour-time "Direct link to 24 Hour Time")

Displays the time in 24-hour format.

---

## Compass

**Source:** [https://docs.vape.gg/features/modules/legit/Compass](https://docs.vape.gg/features/modules/legit/Compass)

# Compass

The Compass module renders a compass showing you which direction you're facing at any given time (surprisingly enough).

---

### Render Background[​](#render-background "Direct link to Render Background")

If enabled, a slightly opaque grey background will be drawn behind the HUD element, making the text easier to read.

---

## Coords

**Source:** [https://docs.vape.gg/features/modules/legit/Coords](https://docs.vape.gg/features/modules/legit/Coords)

# Coords

Displays your current position, biome, and facing direction on your HUD, helpful for gamemodes requiring callouts.

---

### Render Background[​](#render-background "Direct link to Render Background")

Adds a subtle grey background for better text readability.

### Display Type[​](#display-type "Direct link to Display Type")

- **Vertical**: Aligns the elements vertically (recommended).
- **Horizontal**: Aligns the elements horizontally.

---

## FPS

**Source:** [https://docs.vape.gg/features/modules/legit/FPS](https://docs.vape.gg/features/modules/legit/FPS)

# FPS

Renders a GUI element showing you the FPS your game is running at.

---

### Render Background[​](#render-background "Direct link to Render Background")

If enabled, a slightly opaque grey background will be drawn behind the HUD element, making the text easier to read.

---

## FreeLook

**Source:** [https://docs.vape.gg/features/modules/legit/FreeLook](https://docs.vape.gg/features/modules/legit/FreeLook)

# FreeLook

Temporarily switches to a detached 3rd-person view, allowing you to look around without changing your facing direction.

---

### Activate Freelook[​](#activate-freelook "Direct link to Activate Freelook")

- **Hold**: Activates freelook only while the keybind is held down.
- **Toggle**: Activates/deactivates freelook with a single key press.

### Starting Position[​](#starting-position "Direct link to Starting Position")

- **Forward**: The camera looks in your current facing direction when freelook is activated.
- **Backward**: The camera looks directly behind you when freelook is activated.

### Use Custom Sensitivity[​](#use-custom-sensitivity "Direct link to Use Custom Sensitivity")

Allows you to set a custom sensitivity for the freelook camera, independent of your Minecraft sensitivity.

### Keybind[​](#keybind "Direct link to Keybind")

Specifies the key or mouse button to activate the module.

---

## Hit-Color

**Source:** [https://docs.vape.gg/features/modules/legit/Hit-Color](https://docs.vape.gg/features/modules/legit/Hit-Color)

# Hit Color

The Hit Color module allows you to change the color entities turn when they are hit, from the default blood red to whatever color you prefer.

---

### Color[​](#color "Direct link to Color")

The color to paint hurt entities with.

---

## Inventory-Blur

**Source:** [https://docs.vape.gg/features/modules/legit/Inventory-Blur](https://docs.vape.gg/features/modules/legit/Inventory-Blur)

# Inventory Blur

The Inventory Blur module simply blurs the background / world around you while you have a container or any GUI open. The optimal choice for a cinematic experience.

---

## Keystrokes

**Source:** [https://docs.vape.gg/features/modules/legit/Keystrokes](https://docs.vape.gg/features/modules/legit/Keystrokes)

# Keystrokes

Renders a GUI element on your HUD that shows which buttons you are pressing at any given time, as well as your current CPS.

---

### Key Style[​](#key-style "Direct link to Key Style")

Specifies the style of the icons drawn that represent your directional input (forward, left, etc) keys.

#### Keyboard[​](#keyboard "Direct link to Keyboard")

Renders directional input keys as their actual keyboard letters (WASD).

#### Arrow[​](#arrow "Direct link to Arrow")

Renders directional input keys as the directions they move you in, or like keyboard arrow keys.

### Mouse Style[​](#mouse-style "Direct link to Mouse Style")

Specifies the style of the icons drawn that represent your mouse input (attack, use) keys.

#### Button[​](#button "Direct link to Button")

Renders mouse input keys as their names (LMB = Left Mouse Button, RMB = Right Mouse Button).

#### Icon[​](#icon "Direct link to Icon")

Renders mouse input keys on top of a mockup of a physical mouse.

### Show Spacebar[​](#show-spacebar "Direct link to Show Spacebar")

If enabled, the spacebar will also be shown on the rendered GUI element.

### Show CPS Only[​](#show-cps-only "Direct link to Show CPS Only")

If enabled, the only thing that will be drawn to your GUI is your CPS-- all other elements will be hidden.

### Render Background[​](#render-background "Direct link to Render Background")

If enabled, a slightly opaque grey background will be drawn behind the HUD element, making the text easier to read.

---

## MouseDelayFix

**Source:** [https://docs.vape.gg/features/modules/legit/MouseDelayFix](https://docs.vape.gg/features/modules/legit/MouseDelayFix)

# MouseDelayFix

The MouseDelayFix Module [**fixes a bug**](https://bugs.mojang.com/browse/MC-67665) that is present on 1.8.x (e.g. 1.8.9) versions of Minecraft that causes aiming and hit registration to be less accurate. This makes combat feel more like 1.7.10.

---

## NoClickDelay

**Source:** [https://docs.vape.gg/features/modules/legit/NoClickDelay](https://docs.vape.gg/features/modules/legit/NoClickDelay)

# NoClickDelay

The NoClickDelay module removes the delay that is added after you miss an attack in version 1.8.9 and later.

---

## NoHurtCam

**Source:** [https://docs.vape.gg/features/modules/legit/NoHurtCam](https://docs.vape.gg/features/modules/legit/NoHurtCam)

# NoHurtCam

Removes the camera flinch that plays when taking damage.

---

## NoJumpDelay

**Source:** [https://docs.vape.gg/features/modules/legit/NoJumpDelay](https://docs.vape.gg/features/modules/legit/NoJumpDelay)

# NoJumpDelay

The NoJumpDelay module removes the delay between jumps after hitting your head against a block while holding spacebar. This lets you sprint-jump down 2x1 corridors at the speed you'd get if you were spamming the spacebar, but you can just hold the spacebar instead.

---

## Potion-Status

**Source:** [https://docs.vape.gg/features/modules/legit/Potion-Status](https://docs.vape.gg/features/modules/legit/Potion-Status)

# Potion Status

The Potion Status module renders a GUI element on your HUD showing you which potions are currently active on you, and their remaining duration.

---

### Render Background[​](#render-background "Direct link to Render Background")

If enabled, a slightly opaque grey background will be drawn behind the HUD element, making the text easier to read.

### Show Positive Effects[​](#show-positive-effects "Direct link to Show Positive Effects")

If enabled, positive effects / buffs will be shown on the HUD element (e.g. Swiftness, Strength, etc).

### Show Negative Effects[​](#show-negative-effects "Direct link to Show Negative Effects")

If enabled, negative effects / debuffs will be shown on the HUD element (e.g. Slowness, Weakness, etc).

---

## Reach-Display

**Source:** [https://docs.vape.gg/features/modules/legit/Reach-Display](https://docs.vape.gg/features/modules/legit/Reach-Display)

# Reach Display

The Reach Display module renders a GUI element on your HUD showing you the distance between you and your opponent at the time you last landed a hit on them.

---

### Render Background[​](#render-background "Direct link to Render Background")

If enabled, a slightly opaque grey background will be drawn behind the HUD element, making the text easier to read.

---

## Scoreboard

**Source:** [https://docs.vape.gg/features/modules/legit/Scoreboard](https://docs.vape.gg/features/modules/legit/Scoreboard)

# Scoreboard

Allows you to reposition, customize the text, and adjust visual settings of the Vanilla Minecraft scoreboard.

---

### Render Background[​](#render-background "Direct link to Render Background")

Adds a subtle grey background for improved text readability.

### Show Score Numbers[​](#show-score-numbers "Direct link to Show Score Numbers")

Displays the score numbers next to each line on the scoreboard.

### Replace Scoreboard Text[​](#replace-scoreboard-text "Direct link to Replace Scoreboard Text")

Replaces specific strings of text on the scoreboard with your preferred text, useful for removing server names in recordings.

---

## Time-Changer

**Source:** [https://docs.vape.gg/features/modules/legit/Time-Changer](https://docs.vape.gg/features/modules/legit/Time-Changer)

# Time Changer

The Time Changer module allows you to override the time of day shown to you client-side.

---

### Time[​](#time "Direct link to Time")

Specifies the time of day you want to set the game to.

---

## Weather

**Source:** [https://docs.vape.gg/features/modules/legit/Weather](https://docs.vape.gg/features/modules/legit/Weather)

# Weather

The Weather module allows you to override the weather that is shown ingame to you, client-side. This does not change the weather for other players, only you.

---

### Weather[​](#weather-1 "Direct link to Weather")

Specifies what type of weather you want to have shown to you.

#### Clear[​](#clear "Direct link to Clear")

Sets the weather to be clear.

#### Raining[​](#raining "Direct link to Raining")

Sets the weather to be raining.

---

## Profiles

**Source:** [https://docs.vape.gg/features/misc/Profiles/](https://docs.vape.gg/features/misc/Profiles/)

# Profiles

Profiles allow you to save and manage different settings for various servers or playstyles.

## Features[​](#features "Direct link to Features")

- Save module settings, enabling you to have different configurations for different scenarios.
- Easily switch between profiles to adapt to different servers or gamemodes.
- View and edit profile names, delete profiles, and access the Advanced Editor.
- Enable or disable modules associated with a profile.
- Optionally, auto-load module states when switching profiles.
- Choose to save GUI frame and overlay positions globally or per profile.
- Re-order and toggle the visibility of your profiles.

## Advanced Editor[​](#advanced-editor "Direct link to Advanced Editor")

Provides a powerful interface to fine-tune your profile settings.

- See which modules have been modified from their defaults.
- Reset modules to their default settings.
- Directly edit module settings.

---

### Auto-Load Module States[​](#auto-load-module-states "Direct link to Auto-Load Module States")

When enabled, will store the enabled state of each of your modules in the Profile. Upon activating the Profile, the previously enabled modules will be re-activated.

### Frame Positions per Profile[​](#frame-positions-per-profile "Direct link to Frame Positions per Profile")

When enabled, will store the positions of GUI frames (e.g. Overlays, Module Category dropdowns, etc), and will restore the positions of all those GUI frames upon activating the Profile.

---

## Public

**Source:** [https://docs.vape.gg/features/misc/Profiles/Public](https://docs.vape.gg/features/misc/Profiles/Public)

# Public Profiles

Public Profiles are shared profiles available through Vape Online. These are community-created presets for different servers, anticheats, and gamemodes, allowing you to quickly find effective settings without extensive testing.

info

Public Profiles are not tested by the Vape Team, so exercise caution when using them, especially on servers with unfamiliar AntiCheats.

![](/img/profiles/public_profiles_main.png)

## Searching and Publishing Profiles[​](#searching-and-publishing-profiles "Direct link to Searching and Publishing Profiles")

- **Search**: Use the Public Profiles Browser to search by name, tags, or full-text.
- **Publish**: Create a Public Profile from an existing Private Profile or your current settings.
  - **Privacy options**:
    - Upload Anonymously
    - Discoverable with a Share Code only
    - Friends only Discovery (requires share code discovery)

## Linking Public and Private Profiles[​](#linking-public-and-private-profiles "Direct link to Linking Public and Private Profiles")

- Public Profiles are linked to a "parent" Private Profile.
- You cannot delete the parent Private Profile until you delete the Public Profile or change its source.

## Updating a Public Profile[​](#updating-a-public-profile "Direct link to Updating a Public Profile")

- Changes to the Private Profile do not automatically sync to the Public Profile.
- Manually update the Public Profile to include the latest changes.

### Update Options[​](#update-options "Direct link to Update Options")

- Change the underlying Private Profile the Public one is derived from
- Directly edit Module Settings without affecting the Private Profile
- Delete the Public Profile
- Edit the Description, Tags, and Privacy Settings
- Copy or regenerate the Share Code
- View Reviews and Stats of your Profile

---

## Friends

**Source:** [https://docs.vape.gg/features/misc/Friends/](https://docs.vape.gg/features/misc/Friends/)

# Friends

Vape allows you to add other Minecraft players and Vape users as friends, enhancing various features and interactions within the client.

## Minecraft Friends[​](#minecraft-friends "Direct link to Minecraft Friends")

Add friends by their Minecraft username. These friends have limited functionality compared to Vape Online friends.

- **Manual Specification of Friend or Foe**: Overrides team settings in certain modules, ensuring friends are treated as friendly.
- **Username Aliasing**: Assign custom names (aliases) to friends, useful for anonymity in recordings or identifying alt accounts.

tip

To add an alias for a Minecraft friend, append a space followed by the preferred alias when entering their Username.

For example, if adding Manthe as a friend, to set his alias as prplz, write: `manthe prplz`.

## Vape Online Friends[​](#vape-online-friends "Direct link to Vape Online Friends")

Connect with other Vape users for enhanced features and information sharing. Requires both players to be using Vape and signed in to Vape Online.

- **Adding Friends**: Open the "Vape Friends" tab, sign in if needed, and click the "+" icon to add friends by their Vape Online username.

tip

Vape Online Friends enable additional features like party messaging, location sharing, and inventory viewing, as explained in the [**Vape Online**](/features/misc/Online/) section.

---

## Options

**Source:** [https://docs.vape.gg/features/misc/Friends/Options](https://docs.vape.gg/features/misc/Friends/Options)

# Friends - Options

Configure how the Friends system interacts with the client.

## Ping Keybind[​](#ping-keybind "Direct link to Ping Keybind")

Set a keybind to ping locations in the world, visible only to your Vape Online party members on the same server.

## Notification Settings[​](#notification-settings "Direct link to Notification Settings")

Controls settings for Notifications related to Vape Friends.

- **Too Many Pings**: Notifies you when you've been rate limited for sending too many pings.
- **Friend Requests**: Notifies you of incoming Vape Online friend requests.
- **Chats**: Notifies you of incoming Vape Online messages.
- **Friend Online**: Notifies you when a Vape Friend comes online.
- **Party Invites**: Notifies you of incoming party invites.
- **Party Invite Accepted**: Notifies you when someone accepts your party invite.

## Friend Settings[​](#friend-settings "Direct link to Friend Settings")

Controls settings about how the Friends system interacts with Vape Client.

### Recolor Visuals[​](#recolor-visuals "Direct link to Recolor Visuals")

Uses a custom color for GUI elements representing Minecraft Friends in render modules.

### Use Friends[​](#use-friends "Direct link to Use Friends")

Excludes Minecraft Friends from being targeted by certain modules.

### Use Alias[​](#use-alias "Direct link to Use Alias")

Uses aliases instead of usernames for Minecraft Friends in certain modules.

- **Spoof Alias**: Replaces usernames with aliases in vanilla Minecraft nametags and chat.

### Add Friend Bind[​](#add-friend-bind "Direct link to Add Friend Bind")

Set a keybind to quickly add a player as a friend when your crosshair is over them.

### Indicator Color[​](#indicator-color "Direct link to Indicator Color")

Customizes the color of the overhead indicator for party members.

- **Party**: Uses unique colors for each party member.
- **Team**: Uses the color of their server team.
- **Friend**: Uses the color specified in Recolor Visuals.

### Party Overhead Indicator[​](#party-overhead-indicator "Direct link to Party Overhead Indicator")

Displays a colored dot above party members' heads.

### Target Indicators[​](#target-indicators "Direct link to Target Indicators")

Highlights and marks the targets your party members are attacking.

- **Self Target Indicators**: Also highlights your own targets.

---

## Macros

**Source:** [https://docs.vape.gg/features/misc/Macros](https://docs.vape.gg/features/misc/Macros)

# Macros

Macros automate switching to and using items or executing chat commands at the press of a key. After triggering, the macro returns you to your original hotbar slot.

## Macro Types[​](#macro-types "Direct link to Macro Types")

The type of macro is determined by the name you give it.

### Item Macros[​](#item-macros "Direct link to Item Macros")

For item macros, enter an item name (e.g. `ender pearl`, `lava bucket`) or numeric item ID.

When triggered, the macro finds the item in your hotbar, switches to it, right-clicks, and switches back after a short delay.

### Command Macros[​](#command-macros "Direct link to Command Macros")

Any name starting with `/` is treated as a command macro. The entire name is sent as a chat message when triggered — for example, `/msg Steve ready to go`.

tip

Command macros execute instantly and do not use the Delay or Double Click settings.

### Fishing Rod Macros[​](#fishing-rod-macros "Direct link to Fishing Rod Macros")

Any name starting with `fishing rod` is treated as a fishing rod macro. This is a special item macro with smart recast behavior.

With Double Click enabled, it detects whether the rod has caught a player or is stuck in the ground, and if so, automatically reels in and recasts without waiting for the full delay.

## Creating a Macro[​](#creating-a-macro "Direct link to Creating a Macro")

Open the Vape GUI and navigate to Macros. Enter the item name or chat command, then press a key to bind it.

### Delay[​](#delay "Direct link to Delay")

Sets the minimum and maximum delay (in milliseconds) before restoring your original hotbar slot after right-clicking. A random value within this range is used each time. Only applies to item and fishing rod macros.

### Double Click[​](#double-click "Direct link to Double Click")

If enabled, issues a second right-click shortly after the first. Useful for fishing rods (recast after catching), lava/water buckets (place and pick up), and similar items. Not applicable to command macros.

- **Double Click Delay**: Sets the minimum and maximum delay (in milliseconds) to wait before issuing the second right-click.

---

## settings

**Source:** [https://docs.vape.gg/features/settings/](https://docs.vape.gg/features/settings/)

# Global settings

This section covers the general client settings. These settings generally provide functionality not related to specific modules.

To see these settings, open the GUI and click the gear icon at the top right of the main menu that pops up.

![](/img/gui/open-general-settings.png)

## Setting categories[​](#setting-categories "Direct link to Setting categories")

[**General**](/features/settings/General)

General settings and preferences

[**GUI**](/features/settings/GUI)

Customize the appearance of the Vape GUI

[**Modules**](/features/settings/Modules)

Customize global behaviour for modules

[**Notifications**](/features/settings/Notifications)

Configure Vape's Notification system

[**Silent Aim**](/features/settings/Silent-Aim)

Configure how Vape's global Silent Aiming system works

[**Sound**](/features/settings/Sound)

Configures how sound is handled in the Client

## GUI Theme[​](#gui-theme "Direct link to GUI Theme")

The color used throughout the Vape Client GUI to color certain GUI elements with.

## Rebind GUI[​](#rebind-gui "Direct link to Rebind GUI")

Keybind used to open the Vape GUI. By default this is set to RSHIFT.

---

## General

**Source:** [https://docs.vape.gg/features/settings/General](https://docs.vape.gg/features/settings/General)

# General

These settings manage various preferences used throughout the client.

### Enable Multi-Keybinding[​](#enable-multi-keybinding "Direct link to Enable Multi-Keybinding")

Allows you to set multi-key combinations (e.g., Ctrl+Shift+F) as hotkeys for actions.

### Allow Setting Keybinds[​](#allow-setting-keybinds "Direct link to Allow Setting Keybinds")

Allows supported toggle settings inside modules to be bound directly to a key. When enabled, hover over a toggle setting and click the keybind button that appears.

Setting binds support **Toggle** and **Enable while held** modes. Shift-click the setting's bind button to switch modes; **Enable while held** binds are shown with an underline.

### Auto Save[​](#auto-save "Direct link to Auto Save")

Automatically saves and synchronizes any changes you make to your profile.

### Cache Data[​](#cache-data "Direct link to Cache Data")

Stores some client data locally for faster startup. Disabling this may result in longer loading times.

### Language[​](#language "Direct link to Language")

Sets your preferred language for the Vape GUI. Some parts may not be fully translated.

### Reset Current Profile[​](#reset-current-profile "Direct link to Reset Current Profile")

Resets all settings in your current profile to their default values, except for enabled modules and GUI element positions.

---

## GUI

**Source:** [https://docs.vape.gg/features/settings/GUI](https://docs.vape.gg/features/settings/GUI)

# GUI Settings

These settings allow you to customize the visual appearance of the Vape Client GUI.

### Blur Background[​](#blur-background "Direct link to Blur Background")

Blurs the game world behind the Vape GUI when it's open.

### GUI Bind Indicator[​](#gui-bind-indicator "Direct link to GUI Bind Indicator")

Displays a notification showing the keybind to open the Vape GUI upon injecting the client.

### Show Tooltips[​](#show-tooltips "Direct link to Show Tooltips")

Shows brief descriptions when hovering over GUI elements.

### Show Legit Mode[​](#show-legit-mode "Direct link to Show Legit Mode")

Displays the button to switch to the **[Legit Modules GUI](/features/modules/legit/)** at the top of the client.

### GUI Style[​](#gui-style "Direct link to GUI Style")

Controls which Vape GUI layout is used.

- **Frames**: Uses the classic movable frame-based GUI.
- **Central**: Uses the central GUI layout.

### Rainbow Speed[​](#rainbow-speed "Direct link to Rainbow Speed")

Controls the speed at which colors cycle when using the "rainbow" color option.

### GUI Scale[​](#gui-scale "Direct link to GUI Scale")

Adjusts the size of GUI elements. Smaller values are recommended for lower resolutions.

### Search Bar Style[​](#search-bar-style "Direct link to Search Bar Style")

![](/img/search-bar-floating.png)

Determines the placement of the search bar for quickly finding modules.

- **Floating**: Places the search bar at the top center of your screen.
- **None**: Hides the search bar completely.
- **Integrated**: Embeds the search bar within the main Vape GUI.

### Reset GUI Positions[​](#reset-gui-positions "Direct link to Reset GUI Positions")

Resets the positions of all GUI elements to their defaults. Useful when changing resolutions or GUI scale.

### Sort GUI[​](#sort-gui "Direct link to Sort GUI")

Sorts all GUI elements in order of their expanded size.

---

## Modules

**Source:** [https://docs.vape.gg/features/settings/Modules](https://docs.vape.gg/features/settings/Modules)

# Modules

Configure options affecting various modules and how they target enemies.

---

## Teams & Filtering[​](#teams--filtering "Direct link to Teams & Filtering")

Team identification and bot filtering have been moved to the **[TargetFilter](/features/modules/utility/TargetFilter)** module.

## Other Options[​](#other-options "Direct link to Other Options")

- **Lobby Check**: Disables certain modules in server lobbies to avoid unintended actions.
- **Sanity Check**: Disables all active modules upon connecting or disconnecting from a server.
- **Show NBT Tags**: Displays NBT tags on items for debugging or creating item lists.
- **Health Prediction**: Attempts to predict other players' health based on observed events.

  warning

  Health prediction may be inaccurate, especially on servers that hide information or provide misleading data.

  - **Estimate Food**: Considers food-based health regeneration in predictions.
  - **Estimate Fall**: Includes fall damage in health predictions.

---

## Notifications

**Source:** [https://docs.vape.gg/features/settings/Notifications](https://docs.vape.gg/features/settings/Notifications)

# Notifications

The Notifications settings subsection allows you to specify the behavior of notifications used throughout the client.

### Notifications[​](#notifications "Direct link to Notifications")

Allows you to toggle whether notifications will be used at all. If enabled, notifications will be shown for the enabled events.

- **Toggle Alert**: Triggered when toggling a module on or off. Useful if you are prone to fat-fingering keybinds.
- **Setting Toggle Alert**: Triggered when a bound module setting is toggled.
- **Profile Switch**: Triggered when switching profiles, including a summary of how many modules were enabled.
- **Friend Notifications**: Catch-all category for all the notifications used by the **[Vape Online Friends](/features/misc/Friends/)** system.

---

## Silent-Aim

**Source:** [https://docs.vape.gg/features/settings/Silent-Aim](https://docs.vape.gg/features/settings/Silent-Aim)

## Silent Aim[​](#silent-aim "Direct link to Silent Aim")

Silent Aim modifies your view angles server-side, allowing you to look in any direction while appearing to aim elsewhere.

tip

Silent Aim is anticheat safe while using the `Proper` movement option. Switching to `None` or `Slow` is unsafe and not recommended.

### Movement[​](#movement "Direct link to Movement")

**Proper** movement will only allow normal movement according to your server side look angles, which is fully safe and highly recommended. You can switch movement to having no correction (**None**) or **Slow**, but these other modes are unsafe.

### 3rd Person Aim View[​](#3rd-person-aim-view "Direct link to 3rd Person Aim View")

In 3rd person, your character's head will point towards the Silent Aim target, helping visualize its effects.

### Aim Indicator[​](#aim-indicator "Direct link to Aim Indicator")

Draws a line from your actual crosshair to the Silent Aim target, indicating the direction it's aiming.

---

info

Enabling the **[Reach](/features/modules/combat/Reach)** or **[Hitboxes](/features/modules/other/HitBoxes)** modules are always unsafe and not recommended.

Silent Aim itself is safe and does not extend reach/hitboxes on its own.

### Use Reach[​](#use-reach "Direct link to Use Reach")[Warning](/guides/general/anti-cheats)

Allows the **[Reach Module](/features/modules/combat/Reach)** to increase Reach for modules using Silent Aim, e.g. **[SilentAura](/features/modules/combat/SilentAura)**. Note that you must also have the module Reach itself enabled for it to have any affect.

With this option is **off**, then Silent Aim will always safely use the default game reach.

### Use Hitboxes[​](#use-hitboxes "Direct link to Use Hitboxes")[Warning](/guides/general/anti-cheats)

Allows the **[Hitboxes Module](/features/modules/other/HitBoxes)** to expand HitBoxes for modules using Silent Aim. Note that you must also have the Hitboxes module itself enabled for it to have any affect.

With this option is **off**, then Silent Aim will always safely use the default game hitbox size.

---

## Sound

**Source:** [https://docs.vape.gg/features/settings/Sound](https://docs.vape.gg/features/settings/Sound)

# Sound

The Sound settings subsection allows you to configure the volume of sound effects used in the client (e.g. for notifications).

Sounds are played for the following Vape Online events:

- **Ping**: When a member of your Vape Online party pings a place in the world
- **Incoming Invite**: When you've received an invite to a Vape Online party
- **Message Received**: When you've received a message from someone via Vape Online

---

### Volume[​](#volume "Direct link to Volume")

Specifies the relative volume of sound effects.

### Muted[​](#muted "Direct link to Muted")

If enabled, sound effects will be muted entirely.

---
