# Vape.gg Modules Documentation

Source: https://docs.vape.gg/features/modules/

This file contains the module descriptions and settings collected from 71 module pages.

---

## AimAssist

**Source:** [https://docs.vape.gg/features/modules/combat/AimAssist](https://docs.vape.gg/features/modules/combat/AimAssist)

# AimAssist[Ghost](/guides/general/anti-cheats)

Helps you keep your aim on target by smoothly adjusting your crosshair's position.

---

### Mode[​](#mode "Direct link to Mode")

Controls which aiming behavior AimAssist uses.

- **Simple**: Lightweight smooth aiming.
- **Adaptive**: Advanced tracking with adaptive behavior.

### Target Settings[​](#target-settings "Direct link to Target Settings")

Specifies which entities are considered valid targets. For more information, [**see the documentation for "Target Settings".**](/values/TargetSettings)

### Require mouse down[​](#require-mouse-down "Direct link to Require mouse down")

Restricts AimAssist's functionality so that it only functions while you are holding down left mouse button.

Additionally, first target found will stay focused until mouse is released.

### Strafe Increase[​](#strafe-increase "Direct link to Strafe Increase")

Increases the speed of crosshair adjustment when you or your opponent are strafing.

### Check Block Break[​](#check-block-break "Direct link to Check Block Break")

Pauses the module while breaking blocks.

- **Break Blocks Whitelist**: Specify a list of items that will be able to break blocks, with AimAssist and Check Block Break enabled. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

### Aim Vertically[​](#aim-vertically "Direct link to Aim Vertically")

Enables vertical crosshair adjustment.

- **Vertical Speed**: Controls the speed of vertical aim adjustment.

### Horizontal Speed[​](#horizontal-speed "Direct link to Horizontal Speed")

Controls the speed of horizontal aim adjustment.

### Max Angle[​](#max-angle "Direct link to Max Angle")

Specifies the maximum angle from your crosshair position that a target can be, for that target to be considered a valid target.

### Distance[​](#distance "Direct link to Distance")

The maximum distance for an entity to be considered a target.

### Limit to Items[​](#limit-to-items "Direct link to Limit to Items")

Restricts AimAssist's functionality so that it only functions while specified items are held in your hand. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

### Target Area[​](#target-area "Direct link to Target Area")

Specifies which part of the target's hitbox the module will aim towards.

- **Center**: Aims towards the center of the target's hitbox.
- **Closest**: Aims towards the closest position on the target's hitbox.

### Target Mode[​](#target-mode "Direct link to Target Mode")

Determines how a module prioritizes target selection when multiple targets are available. Choose the factor that's most important for your gameplay.

- **Distance**: Prioritizes the closest targets.
- **Yaw**: Prioritizes targets closest to your crosshair, requiring minimal view angle adjustment.
- **Armor**: Prioritizes targets with weaker armor.
- **Threat**: Prioritizes the most dangerous targets based on their weapon and strength level.
- **Health**: Prioritizes targets with the lowest health.

---

## AutoClicker

**Source:** [https://docs.vape.gg/features/modules/combat/AutoClicker](https://docs.vape.gg/features/modules/combat/AutoClicker)

# AutoClicker

[Ghost](/guides/general/anti-cheats)

Automates clicking for you.

---

### Hold to Click[​](#hold-to-click "Direct link to Hold to Click")

Restricts AutoClicker's functionality so that it only functions while you're holding down the attack button.

### Trigger Mode[​](#trigger-mode "Direct link to Trigger Mode")

Clicks only when your cursor is hovering over an entity.

### Break Blocks[​](#break-blocks "Direct link to Break Blocks")

Pauses the module when you start breaking a block.

- **Break Blocks Delay**: Sets a random delay before switching to "block break" mode.
- **Break Blocks Whitelist**: Only pauses clicking when you're holding the specified items (e.g. pickaxe, shovel, etc). For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

### CPS[​](#cps "Direct link to CPS")

Sets the minimum and maximum clicks per second.

### Randomization[​](#randomization "Direct link to Randomization")

Controls the randomness of your CPS to avoid detection.

- **Normal**: Static randomization between CPS values.
- **Extra**: Additional randomization with basic human like patterns.
- **Extra+**: Full behavioral emulation that produces human like input patterns. Designed to resist statistical as well as model based detections.

### Jitter[​](#jitter "Direct link to Jitter")

Moves your cursor around while clicking to mimic human-like jitter clicking.

### Limit Items[​](#limit-items "Direct link to Limit Items")

Restricts AutoClicker's functionality so that it only functions while you're holding the specified items. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

### Inventory Fill[​](#inventory-fill "Direct link to Inventory Fill")

Moved to separate module: **[InventoryFill](/features/modules/inventory/InventoryFill)**

---

## BlockHit

**Source:** [https://docs.vape.gg/features/modules/combat/BlockHit](https://docs.vape.gg/features/modules/combat/BlockHit)

# BlockHit

[Ghost](/guides/general/anti-cheats)

Automatically performs block hits when holding a sword. It works with manual clicking and **[AutoClicker](/features/modules/combat/AutoClicker)**. SilentAura's CPS mode integrates with BlockHit.

---

### Chance[​](#chance "Direct link to Chance")

Sets the probability of performing a block hit for each click.

### Require Mouse Down[​](#require-mouse-down "Direct link to Require Mouse Down")

Restricts BlockHit's functionality so that it only functions while the right mouse button is held down.

### Mode[​](#mode "Direct link to Mode")

- **Manual**: Block hits based on your CPS. Each click has a chance to trigger a block hit.
- **Auto**: Legacy (old functionality) from when BlockHit was not a module and just an option in **[AutoClicker](/features/modules/combat/AutoClicker)**.
- **Predict**: Predicts incoming attacks and initiates block hits in advance.
- **Lag**: Lags you after blocking to maximize server side block time.

---

## HitFlick

**Source:** [https://docs.vape.gg/features/modules/combat/HitFlick](https://docs.vape.gg/features/modules/combat/HitFlick)

# HitFlick[Unknown](/guides/general/anti-cheats)

Flicks off and back onto a target during an attack to alter the knockback angle.

HitFlick can start from a normal attack on a valid target. It can also work with SilentAura when SilentAura is ready to click and its target is under the crosshair.

---

### Target Settings[​](#target-settings "Direct link to Target Settings")

Specifies which entities are considered valid targets. For more information, [**see the documentation for "Target Settings".**](/values/TargetSettings)

### Angle[​](#angle "Direct link to Angle")

Controls the flick direction in degrees.

- **0**: No offset.
- **90**: Flicks right.
- **180**: Pulls toward the target.
- **270**: Flicks left.

### Chance[​](#chance "Direct link to Chance")

The chance that HitFlick starts on a valid attack.

### Flick Delay[​](#flick-delay "Direct link to Flick Delay")

The minimum delay between HitFlick attempts.

### Randomize Offset[​](#randomize-offset "Direct link to Randomize Offset")

Randomizes the configured angle for each flick.

- **Randomize Offset**: The maximum angle range used around the configured **Angle**. For example, `10` means the flick can use **Angle** plus or minus 5 degrees.

### Strafe Invert[​](#strafe-invert "Direct link to Strafe Invert")

Flips the flick side when you strafe toward the current push direction.

### Select Hits[​](#select-hits "Direct link to Select Hits")

Only starts a flick when the target is vulnerable.

### Blink[​](#blink "Direct link to Blink")

Chokes outgoing packets during the flick and flushes them once the attack is sent.

### Limit to Items[​](#limit-to-items "Direct link to Limit to Items")

Restricts HitFlick so that it only functions while specified items are held. For more information, **[see the documentation for "Limit to Item" Settings](/values/LimitItems)**.

---

## HitSelect

**Source:** [https://docs.vape.gg/features/modules/combat/HitSelect](https://docs.vape.gg/features/modules/combat/HitSelect)

# HitSelect

[Ghost](/guides/general/anti-cheats)

Interrupts attacks to gain combat advantages like improved movement, reduced knockback, and increased critical hit frequency.

---

### Chance[​](#chance "Direct link to Chance")

The chance that an attack would be interrupted.

### Mode[​](#mode "Direct link to Mode")

- **Pause**: Static hit selection. Pauses attacks for a duration after a hit.
- **Active**: Dynamic hit selection. Pauses attacks only when advantageous, allowing regular hits to go through when it would be beneficial.
  - **Preference**: Choose which benefit to favor:
    - **KB reduction**: Prioritizes reducing knockback.
    - **Critical hits**: Prioritizes increasing critical hit frequency. Both modes will still provide some level of the other benefit.

---

## Reach

**Source:** [https://docs.vape.gg/features/modules/combat/Reach](https://docs.vape.gg/features/modules/combat/Reach)

# Reach[Warning](/guides/general/anti-cheats)

Increases your attack range, allowing you to hit entities from further away.

---

### Range[​](#range "Direct link to Range")

Sets the minimum and maximum range for your attack distance. A random value between these is used when the module activates.

### Chance[​](#chance "Direct link to Chance")

The percentage chance that the module will override your attack distance when you attack.

### Chance Mode[​](#chance-mode "Direct link to Chance Mode")

- **Advanced**: Intelligently decides when to activate Reach based on potential advantages.
- **Normal**: Straightforward activation based on the Chance setting.

### Misplace[​](#misplace "Direct link to Misplace")

Visually adjusts your opponent's position on your screen to match your attack distance, making hits appear legitimate in recordings.

- **Disadvantage**: Makes opponents appear further away when they hit you, potentially making their hits seem illegitimate.

### Vertical Check[​](#vertical-check "Direct link to Vertical Check")

Prevents the module from activating when hitting players slightly above or below you, reducing visual suspicion.

### Only While Sprinting[​](#only-while-sprinting "Direct link to Only While Sprinting")

Activates the module only when sprinting, making its effects less noticeable due to faster movement.

### Disable in Water[​](#disable-in-water "Direct link to Disable in Water")

Disables the module while swimming, as slower movement can make its effects more suspicious.

---

## RightClicker

**Source:** [https://docs.vape.gg/features/modules/combat/RightClicker](https://docs.vape.gg/features/modules/combat/RightClicker)

# RightClicker[Ghost](/guides/general/anti-cheats)

Automates right-clicking, useful for scaffolding/bridging or spamming throwables.

---

### CPS[​](#cps "Direct link to CPS")

Sets the minimum and maximum right clicks per second.

### Start Delay[​](#start-delay "Direct link to Start Delay")

Delay (in milliseconds) before the module activates after you start holding right click.

### Block Place Delay[​](#block-place-delay "Direct link to Block Place Delay")

Delay (in milliseconds) between block placements. Helps fine-tune bridge building and reduce suspicion.

### Randomization[​](#randomization "Direct link to Randomization")

Controls the randomness of your CPS to avoid detection.

- **Normal**: Minimal randomization.
- **Extra**: More randomization.
- **Extra+**: Even more randomization.

### Jitter[​](#jitter "Direct link to Jitter")

Moves your cursor around while clicking to mimic human-like jitter clicking.

### Use Item Whitelist[​](#use-item-whitelist "Direct link to Use Item Whitelist")

Restricts the module's functionality so that it only enables when holding certain blocks or items. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## SilentAura

**Source:** [https://docs.vape.gg/features/modules/combat/SilentAura](https://docs.vape.gg/features/modules/combat/SilentAura)

# SilentAura

[Ghost](/guides/general/anti-cheats)

A combination of AimAssist and AutoClicker, allowing attacks without disrupting your client-side view. Uses your [**Silent Aim**](/features/settings/Silent-Aim) settings.

---

### Target Settings[​](#target-settings "Direct link to Target Settings")

Specifies which entities are considered valid targets. For more information, [**see the documentation for "Target Settings".**](/values/TargetSettings)

### Aim Speed[​](#aim-speed "Direct link to Aim Speed")

Controls the speed of head movements toward the target.

### Click Mode[​](#click-mode "Direct link to Click Mode")

Controls how SilentAura times its attacks.

- **CPS**: Attacks at the configured **Attacks per Second** rate.
- **Trigger**: Waits for the attack cooldown to be ready and for SilentAura to be hovering the target. A valid mace smash bypasses the normal cooldown. Trigger mode and its settings are available on Minecraft 1.12.2 and newer.

### Attacks per Second[​](#attacks-per-second "Direct link to Attacks per Second")

Sets the minimum and maximum attacks per second used by **CPS** mode, randomly varying the rate between attacks.

### Trigger Settings[​](#trigger-settings "Direct link to Trigger Settings")

The following settings are available when **Click Mode** is set to **Trigger** on Minecraft 1.12.2 and newer.

- **Extra Delay**: Adds a delay after the attack cooldown. Negative values attack before the cooldown is complete. A valid mace smash bypasses the normal cooldown and this extra delay.
- **Mouse Over Delay**: Controls how long SilentAura must be hovering the target before attacking.
- **Select First Hit**: Waits for the opponent to hit you before attacking them.
- **Ignore Activation Click**: When **Require Mouse Down** is enabled, ignores the first manual click used to activate SilentAura unless the target is already under the crosshair and the attack is ready.
- **Air Crits**: Won't attack while airborne unless the hit will be a critical hit.
- **Shield Check**: Won't attack players who are actively shielding. This check is bypassed while an enabled **ShieldBreaker**, axe-enabled **HitSwap**, or Stun Slam-enabled **AutoMace** has an axe available.
- **Target Miss Chance**: A percentage chance to attack while ready without hovering a valid target, intentionally producing a missed attack.
- **Early Hit Chance**: A percentage chance to attack before the cooldown is completely ready.

### Extra Swing Distance[​](#extra-swing-distance "Direct link to Extra Swing Distance")

The distance beyond attack range where your character starts swinging, making attacks less suspicious.

### Max Angle[​](#max-angle "Direct link to Max Angle")

The maximum angle a target can be from your real crosshair position for SilentAura to consider them a valid target.

### Target Mode[​](#target-mode "Direct link to Target Mode")

Determines how a module prioritizes target selection when multiple targets are available. Choose the factor that's most important for your gameplay.

- **Distance**: Prioritizes the closest targets.
- **Yaw**: Prioritizes targets closest to your crosshair, requiring minimal view angle adjustment.
- **Armor**: Prioritizes targets with weaker armor.
- **Threat**: Prioritizes the most dangerous targets based on their weapon and strength level.
- **Health**: Prioritizes targets with the lowest health.

### Target Area[​](#target-area "Direct link to Target Area")

Specifies which part of the target's hitbox the module will aim towards.

- **Center**: Aims towards the center of the target's hitbox.
- **Closest**: Aims towards the closest position on the target's hitbox.

### Break Blocks[​](#break-blocks "Direct link to Break Blocks")

Temporarily disables the module while breaking blocks.

- **Break Blocks Delay**: A random delay before the module deactivates to allow block breaking.
- **Break Blocks Whitelist**: Only pauses the module when you're attempting to break a block, while holding the specified items.

### Require Mouse Down[​](#require-mouse-down "Direct link to Require Mouse Down")

Restricts the module's functionality so that it is only active while holding left mouse button.

### Disable on Death[​](#disable-on-death "Direct link to Disable on Death")

Automatically disables the module upon death. Useful for avoiding situations where the module is still active and targeting players when sent back to the lobby.

### Show Target[​](#show-target "Direct link to Show Target")

Highlights targeted and attacked entities with specified colors.

- **Target Color**: The color for targeted entities.
- **Attack Color**: The color for attacked entities.

### Limit to Items[​](#limit-to-items "Direct link to Limit to Items")

Restricts the module's functionality so that it is only active while you are holding the specified items. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## Sprint

**Source:** [https://docs.vape.gg/features/modules/combat/Sprint](https://docs.vape.gg/features/modules/combat/Sprint)

# Sprint[Ghost](/guides/general/anti-cheats)

Automatically sprints when you hold down the W key, as long as it's possible (e.g., not while sneaking).

---

### Cancel Invis[​](#cancel-invis "Direct link to Cancel Invis")

Disables sprinting while under the Invisibility potion effect to prevent sprint particles from revealing your position.

---

## JumpReset

**Source:** [https://docs.vape.gg/features/modules/combat/JumpReset](https://docs.vape.gg/features/modules/combat/JumpReset)

# JumpReset[Ghost](/guides/general/anti-cheats)

Abuses a mechanic in vanilla MC to reduce knockback by timing a jump before being hit.

---

### Chance[​](#chance "Direct link to Chance")

The percentage chance of the module activating to attempt a "perfect jump" when hit.

### Accuracy[​](#accuracy "Direct link to Accuracy")

If a jump will be attempted, this determines if the jump will be accurately timed. Meaning it might still attempt a jump but will purposely fail to time it perfectly.

### Only When Targeting[​](#only-when-targeting "Direct link to Only When Targeting")

Activates the module only when the opponent hitting you is near your crosshair.

### Water Check[​](#water-check "Direct link to Water Check")

Disables the module while in water or other liquids to avoid suspicion

---

## WTap

**Source:** [https://docs.vape.gg/features/modules/combat/WTap](https://docs.vape.gg/features/modules/combat/WTap)

# WTap[Ghost](/guides/general/anti-cheats)

Automates the "w-tapping" PVP strategy, useful in 1v1 combat scenarios.

---

### Chance[​](#chance "Direct link to Chance")

The chance of activating WTap when possible or beneficial.

- **Release Delay**: Delay before releasing W after hitting a target.
- **Re-press Delay**: Delay after releasing W before pressing it again.
- **Select Hits**: Activates WTap only when the target is vulnerable, helping close the distance and prevent being combo'd out of range.

---

## Triggerbot

**Source:** [https://docs.vape.gg/features/modules/combat/Triggerbot](https://docs.vape.gg/features/modules/combat/Triggerbot)

# Triggerbot

Automatically clicks on hover.
Triggerbot is available on Minecraft 1.21.4 and later.

---

### Target Settings[​](#target-settings "Direct link to Target Settings")

Specifies which entities are considered valid targets. For more information, [**see the documentation for "Target Settings".**](/values/TargetSettings)

### Extra delay[​](#extra-delay "Direct link to Extra delay")

Extra delay after attack cooldown (in ticks).
Negative values will attack before cooldown is complete.
A valid mace smash bypasses the normal attack cooldown and this extra delay.

### Mouse over delay[​](#mouse-over-delay "Direct link to Mouse over delay")

Controls how long your crosshair must remain over a valid target before attacking.

### Require mouse down[​](#require-mouse-down "Direct link to Require mouse down")

Only activates when the attack button is held down.

- **Ignore activation click**: Ignores the first manual click used to activate Triggerbot, unless you are already hovering a valid target and the attack is ready.

### Air crits[​](#air-crits "Direct link to Air crits")

Won't attack in air unless you will crit (falling).

### Shield check[​](#shield-check "Direct link to Shield check")

Won't attack players who are actively shielding. This check is bypassed while an enabled **ShieldBreaker**, axe-enabled **HitSwap**, or Stun Slam-enabled **AutoMace** has an axe available.

### Select first hit[​](#select-first-hit "Direct link to Select first hit")

Waits for the opponent to hit you before attacking them.

### Target miss chance[​](#target-miss-chance "Direct link to Target miss chance")

A percentage chance to attack when the attack is ready even though your crosshair is not hovering a valid target, intentionally producing a missed attack.

### Early hit chance[​](#early-hit-chance "Direct link to Early hit chance")

A percentage chance to attack earlier than the cooldown is ready. Adds variance to attack timing.

### Limit items[​](#limit-items "Direct link to Limit items")

Functions only while holding whitelisted items. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## CrystalAura

**Source:** [https://docs.vape.gg/features/modules/combat/CrystalAura](https://docs.vape.gg/features/modules/combat/CrystalAura)

# CrystalAura

Automatically places end crystals on obsidian and detonates them to deal damage to nearby targets.

CrystalAura is available on Minecraft 1.21.4 and later.

---

### Mode[​](#mode "Direct link to Mode")

Controls how CrystalAura places and breaks crystals.

- **Auto**: Automatically finds targets, places crystals, and breaks them.
- **Manual**: While holding right-click on obsidian with an end crystal, places and breaks crystals around the block you are aiming at.

While you are actively blocking with a shield, **Auto** mode pauses crystal placements and detonations that would deal self-damage.

The following target settings apply to **Auto** mode.

### Target Settings[​](#target-settings "Direct link to Target Settings")

Specifies which entities are considered valid targets. For more information, [**see the documentation for "Target Settings".**](/values/TargetSettings)

### Target Mode[​](#target-mode "Direct link to Target Mode")

Determines how a module prioritizes target selection when multiple targets are available. Choose the factor that's most important for your gameplay.

- **Distance**: Prioritizes the closest targets.
- **Yaw**: Prioritizes targets closest to your crosshair, requiring minimal view angle adjustment.
- **Armor**: Prioritizes targets with weaker armor.
- **Threat**: Prioritizes the most dangerous targets based on their weapon and strength level.
- **Health**: Prioritizes targets with the lowest health.

### Range[​](#range "Direct link to Range")

The maximum distance to check for valid targets.

### Max Angle[​](#max-angle "Direct link to Max Angle")

The maximum angle from your crosshair at which targets will be acquired.

### Aim Speed[​](#aim-speed "Direct link to Aim Speed")

Controls the speed of aim rotations when placing and detonating crystals.

### Delay[​](#delay "Direct link to Delay")

Controls the delay before activating placed crystals.

### Anti-Suicide[​](#anti-suicide "Direct link to Anti-Suicide")

Prevents placing or detonating crystals if it would result in fatal self-damage.

- **Max Self Damage**: The maximum amount of self-damage allowed per crystal detonation.

### Optimization[​](#optimization "Direct link to Optimization")

Controls how crystals are placed and detonated for maximum efficiency.

- **None**: Standard placement and detonation.
- **Rapid fire**: Rapidly places and detonates crystals when above a minimum efficiency threshold.
- **Predict**: Predicts explosion timing and pre-removes crystals for faster placement. This can be unsafe on some servers.

### Rapid Min Efficiency[​](#rapid-min-efficiency "Direct link to Rapid Min Efficiency")

The minimum damage efficiency required for **Rapid fire** optimization to trigger.

### Predict Attack Velocity[​](#predict-attack-velocity "Direct link to Predict Attack Velocity")

Predicts target movement when calculating damage after successfully attacking.

### Min Efficiency[​](#min-efficiency "Direct link to Min Efficiency")

Filters out crystals where surrounding blocks (such as the obsidian beneath a target) absorb too much of the explosion's line-of-sight. Higher values demand more direct hits and skip placements that would waste a large portion of the crystal's potential damage, while lower values accept more obstructed hits in exchange for more frequent crystal usage.

### Auto Obsidian[​](#auto-obsidian "Direct link to Auto Obsidian")

Automatically places obsidian blocks to create surfaces for crystal placement.

### Center Screen[​](#center-screen "Direct link to Center Screen")

Renders crystal count and CrystalAura status information near the center of the screen.

### Show Target[​](#show-target "Direct link to Show Target")

Highlights targeted and attacked entities with specified colors.

- **Target Color**: The color for targeted entities.
- **Attack Color**: The color for attacked entities.

### Manual Settings[​](#manual-settings "Direct link to Manual Settings")

The following settings apply to **Manual** mode.

### Manual Aim Speed[​](#manual-aim-speed "Direct link to Manual Aim Speed")

Controls aim rotation speed while breaking and placing crystals in Manual mode.

### Manual Anti-Suicide[​](#manual-anti-suicide "Direct link to Manual Anti-Suicide")

Prevents breaking crystals if it would result in fatal self-damage.

- **Max Self Damage**: The maximum amount of self-damage allowed per crystal detonation.

### Manual Delay[​](#manual-delay "Direct link to Manual Delay")

Controls the delay between break and place cycles.

### Manual Optimization[​](#manual-optimization "Direct link to Manual Optimization")

Controls how Manual mode handles crystal replacement.

- **None**: Standard break and place behavior.
- **Rapid fire**: Breaks and replaces crystals as quickly as possible when valid.
- **Predict**: Predicts explosion timing and pre-removes crystals for faster placement. This can be unsafe on some servers.

With **None** or **Rapid fire**, Manual mode waits for the server to confirm that the attacked crystal has been removed before replacing it. **Predict** anticipates that removal for faster placement instead.

### Place Obsidian[​](#place-obsidian "Direct link to Place Obsidian")

Automatically places obsidian from your hotbar when hovering a valid placement surface.

### Show Target Block[​](#show-target-block "Direct link to Show Target Block")

Highlights the target obsidian block used by Manual mode.

---

## Search

**Source:** [https://docs.vape.gg/features/modules/render/Search](https://docs.vape.gg/features/modules/render/Search)

# Search

Draws skeleton wireframes around specified blocks through walls.

![Search Module Demo](/img/search-demo.png)

---

### Range[​](#range "Direct link to Range")

Specifies the range in which matching blocks will be highlighted in. Blocks located further away from you than this value will not be highlighted.

### Only Caves[​](#only-caves "Direct link to Only Caves")

Limits the module to only highlight blocks that have at least one of their sides exposed to air. This is good for UHC gamemodes, and is generally a good choice to help make your luck seem less suspicious.

### Search Blocks[​](#search-blocks "Direct link to Search Blocks")

Specifies the blocks you want to highlight with the module. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

### Tracers[​](#tracers "Direct link to Tracers")

Draws a line from your camera to highlighted blocks, making them easier to locate at a distance.

---

## AntiDebuff

**Source:** [https://docs.vape.gg/features/modules/render/AntiDebuff](https://docs.vape.gg/features/modules/render/AntiDebuff)

# AntiDebuff[Warning](/guides/general/anti-cheats)

Removes or reduces the impact of negative status effects.

---

### Remove Nausea[​](#remove-nausea "Direct link to Remove Nausea")

Eliminates the swirling camera effect caused by the Nausea debuff.

### Remove Blindness[​](#remove-blindness "Direct link to Remove Blindness")

Restores your normal view distance even when affected by the Blindness debuff, and removes the blinding fog.

### Remove Slowness[​](#remove-slowness "Direct link to Remove Slowness")

Prevents the FOV change associated with the Slowness debuff

### Remove Effects[​](#remove-effects "Direct link to Remove Effects")[Warning](/guides/general/anti-cheats)

Completely removes all negative status effects, including non-client side effects (e.g. the impact to damage weakness gives, the impact to player speed that slowness gives).

---

## Arrows

**Source:** [https://docs.vape.gg/features/modules/render/Arrows](https://docs.vape.gg/features/modules/render/Arrows)

# Arrows

Displays layered indicators on your screen that point toward players outside your field of view, aiding in situational awareness.

Indicators become smaller as their target gets farther away. When multiple targets are in nearly the same direction and **Show Distance** is enabled, their distance labels are offset so they remain readable.

---

### Color[​](#color "Direct link to Color")

Customizes the color of the arrows.

### Radius Scale[​](#radius-scale "Direct link to Radius Scale")

Adjusts the size of the circle where arrows are displayed. Larger values spread the arrows further apart.

### Show Distance[​](#show-distance "Direct link to Show Distance")

Displays the distance to the player in a pill-shaped label beside the indicator for targets under roughly 170 blocks away.

### Scale Opacity[​](#scale-opacity "Direct link to Scale Opacity")

Makes indicators more transparent the farther away their targets are while retaining a minimum level of visibility.

---

## Chams

**Source:** [https://docs.vape.gg/features/modules/render/Chams](https://docs.vape.gg/features/modules/render/Chams)

# Chams

Renders players through walls.

---

### Hide Bots[​](#hide-bots "Direct link to Hide Bots")

Attempts to hide server-side anti-cheat bots from being rendered.

### Colored[​](#colored "Direct link to Colored")

Enables custom colors for chams instead of just showing their skin.

- **Visible Color**: The color for chams of visible players.
- **Color Behind Walls**: A different color for chams of players behind walls.

---

## ESP

**Source:** [https://docs.vape.gg/features/modules/render/ESP](https://docs.vape.gg/features/modules/render/ESP)

# ESP

Similar to **[Chams](/features/modules/render/Chams)**, ESP renders entities through walls with various visual options.

---

### Player Color[​](#player-color "Direct link to Player Color")

Customizes the color used to render player entities.

### Mode[​](#mode "Direct link to Mode")

- **3D**: Renders a wireframe cube around the entity's hitbox.
  - **Hitbox**: Adjusts the cube size to match the entity's adjusted hitbox (shows changes made by the **[**Hitboxes**](/features/modules/other/HitBoxes)** module)
  - **Show Normal**: Renders an additional cube showing the entity's actual hitbox size.
- **2D**: Renders two-dimensional information.
  - **Bounding Box**: Renders a wireframe square around the entity's bounding box.
    - **Priority Only**: Renders the bounding box only for entities marked as friends.
  - **Health Bar**: Displays a vertical health bar on the side of the entity.
  - **Name**: Renders the entity's username or display name above their bounding box.
    - **Use Display Name**: Shows the display name instead of the username.
    - **Show Background**: Adds a shaded background to the name, similar to vanilla nameplates.
- **Skeleton**: Renders a wireframe skeleton inside the entity.
- **Outline**: Renders a wireframe around the entity's edges, similar to Spectator mode.

### Invisibles[​](#invisibles "Direct link to Invisibles")

Enables ESP rendering for invisible entities.

### Hide Bots[​](#hide-bots "Direct link to Hide Bots")

Attempts to hide server-side anti-cheat bots from being rendered.

---

## Explosions

**Source:** [https://docs.vape.gg/features/modules/render/Explosions](https://docs.vape.gg/features/modules/render/Explosions)

# Explosions

Renders a 3-dimensional sphere around lit TNT entities before they explode, allowing you to visualize the area in which entities will take damage from the explosion's blast.

![Explosions Module Demo](/img/explosions-demo.png)

---

### Blast Ring[​](#blast-ring "Direct link to Blast Ring")

Renders a secondary 3-dimensional sphere in another color inside of the other one, visualizing the area in which blocks may be destroyed by the explosion's blast.

---

## Fullbright

**Source:** [https://docs.vape.gg/features/modules/render/Fullbright](https://docs.vape.gg/features/modules/render/Fullbright)

# Fullbright

Light up the world as if everything's as bright as possible.

---

### Mode[​](#mode "Direct link to Mode")

What type of night vision you want to apply.

- **Night Vision**: Applies the Night Vision potion effect (client-sided, servers will not be able to detect you using this) to your player.
- **Gamma**: Ramps the game's Gamma value to the max, lighting all blocks in absolute brightness.
  - **Fade**: Fades the effects of Gamma in or out slightly while moving between areas of varying brightness.

---

## Health

**Source:** [https://docs.vape.gg/features/modules/render/Health](https://docs.vape.gg/features/modules/render/Health)

# Health

Renders a small decal next to your crosshair showing how many hearts you have left.

---

## Indicators

**Source:** [https://docs.vape.gg/features/modules/render/Indicators](https://docs.vape.gg/features/modules/render/Indicators)

# Indicators

Displays on-screen arrows to warn you of incoming projectiles, helping you dodge and avoid damage.

---

### Alert Type[​](#alert-type "Direct link to Alert Type")

- **Always**: Shows indicators for all valid projectiles, regardless of their potential to hit you.
- **Threat**: Shows indicators only for projectiles that have a chance of hitting you based on your current movement.
- **Hit Only**: Shows indicators only for projectiles that are guaranteed to hit you if you don't move.

### Uncommon Projectile Color[​](#uncommon-projectile-color "Direct link to Uncommon Projectile Color")

Uses a different color for uncommon projectiles like Fireballs.

### Show Types[​](#show-types "Direct link to Show Types")

Enables indicators for the corresponding projectile type, based on the chosen Alert Type.

- **Arrows**: Enables indicators for Arrows.
- **Pearls**: Enables indicators for Pearls.
- **Potions**: Enables indicators for Potions.
- **Eggs**: Enables indicators for Eggs.
- **Snowballs**: Enables indicators for Snowballs.
- **Fireballs**: Enables indicators for Fireballs.

### Radius Scale[​](#radius-scale "Direct link to Radius Scale")

Adjusts the size of the circle where indicators are displayed. Larger values spread the indicators further apart.

### Show Distance[​](#show-distance "Direct link to Show Distance")

Displays the distance between you and the incoming projectile on the indicator.

---

## ItemESP

**Source:** [https://docs.vape.gg/features/modules/render/ItemESP](https://docs.vape.gg/features/modules/render/ItemESP)

# ItemESP

Displays tags on dropped items, similar to **[NameTags](/features/modules/render/NameTags)** but for items.

---

### Distance[​](#distance "Direct link to Distance")

Shows the distance between you and the dropped item next to its name.

### Group Items[​](#group-items "Direct link to Group Items")

Groups nearby dropped items into a single tag to reduce visual clutter.

### Auto Scale[​](#auto-scale "Direct link to Auto Scale")

Keeps the perceived size of item tags consistent regardless of your distance.

### Scale[​](#scale "Direct link to Scale")

Sets the maximum scale of item tags. If Auto Scale is enabled, this controls the constant size. Otherwise, it's the maximum size based on distance.

### Whitelist Only[​](#whitelist-only "Direct link to Whitelist Only")

Restricts the module's functionality so that it will only render tags on the specified items, allowing you to focus on valuable loot while blocking out visual clutter. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## NameTags

**Source:** [https://docs.vape.gg/features/modules/render/NameTags](https://docs.vape.gg/features/modules/render/NameTags)

# NameTags

Replaces vanilla nametags, vividly rendering them through walls and displaying additional information.

---

### Ignore Invisibles[​](#ignore-invisibles "Direct link to Ignore Invisibles")

Hides nametags on invisible entities.

### Auto Scale[​](#auto-scale "Direct link to Auto Scale")

Adjusts nametag size based on distance for consistent visibility.

### Scale[​](#scale "Direct link to Scale")

Sets the maximum or constant size of nametags, depending on Auto Scale.

### Hide Bots[​](#hide-bots "Direct link to Hide Bots")

Disables rendering of nametags on server-side anti-cheat bots.

### Render Players[​](#render-players "Direct link to Render Players")

Enables nametags for players.

- **Health**: Displays the player's health.
- **Distance**: Shows the distance to the player.
- **Effects**: Displays active potion effects.
- **Max Distance**: Limits nametag rendering to players within a certain distance.
- **Equipment**: Shows equipped armor and enchantments.
- **Strength Indicator**: Displays a symbol indicating your relative strength compared to the player.
- **Calculate Effects**: Includes potion effects in strength calculations (may be inaccurate on servers that hide effects).

### Render Animals[​](#render-animals "Direct link to Render Animals")

Enables nametags for peaceful animals.

- **Health**: Displays the animal's health.
- **Distance**: Shows the distance to the animal.
- **Effects**: Displays active potion effects on animals (if applicable).
- **Max Distance**: Limits nametag rendering to animals within a certain distance.

### Render Mobs[​](#render-mobs "Direct link to Render Mobs")

Enables nametags for aggressive mobs.

- **Health**: Displays the mob's health.
- **Distance**: Shows the distance to the mob.
- **Effects**: Displays active potion effects on mobs (if applicable).
- **Max Distance**: Limits nametag rendering to mobs within a certain distance.

---

## Projectiles

**Source:** [https://docs.vape.gg/features/modules/render/Projectiles](https://docs.vape.gg/features/modules/render/Projectiles)

# Projectiles

Renders the ballistic trajectories of projectiles while they are in flight, allowing you to visualize where arrows, pearls, etc will land.

---

### Show Arrows[​](#show-arrows "Direct link to Show Arrows")

When enabled, the module will render trajectories for Arrows in flight.

### Show Pearls[​](#show-pearls "Direct link to Show Pearls")

When enabled, the module will render trajectories for Pearls in flight.

### Show Potions[​](#show-potions "Direct link to Show Potions")

When enabled, the module will render trajectories for Potions in flight.

### Show Eggs[​](#show-eggs "Direct link to Show Eggs")

When enabled, the module will render trajectories for Eggs in flight.

### Show Snowballs[​](#show-snowballs "Direct link to Show Snowballs")

When enabled, the module will render trajectories for Snowballs in flight.

---

## SpawnerFinder

**Source:** [https://docs.vape.gg/features/modules/render/SpawnerFinder](https://docs.vape.gg/features/modules/render/SpawnerFinder)

# SpawnerFinder

Renders Spawners through walls.

---

### Scale[​](#scale "Direct link to Scale")

The scale of the rendered overlays.

### Show Distance[​](#show-distance "Direct link to Show Distance")

When enabled, shows the distance to the spawner on the rendered overlay.

### Spawners[​](#spawners "Direct link to Spawners")

Specifies which mob spawners you want to find. You can just specify the mob's name instead of the spawner's name, for ease of use. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## StorageESP

**Source:** [https://docs.vape.gg/features/modules/render/StorageESP](https://docs.vape.gg/features/modules/render/StorageESP)

# StorageESP

Renders visual outlines or ESP highlights on various storage containers in the game world.

---

### Outline Open[​](#outline-open "Direct link to Outline Open")

Draws a contrasting outline around open chests.

### Render <Container Type>[​](#render-container-type "Direct link to Render <Container Type>")

Enables ESP rendering for the corresponding container type. Supported types are `chests`, `trapped chests`, `ender chests`, `hoppers`, `furnaces`, `dispensers`, and `droppers`.

StorageESP also supports `shulker boxes` on Minecraft 1.12.2 and newer, and `barrels` on Minecraft 1.21.4 and newer.

- **<Container Type> Color**: Customizes the color of the ESP for each container type.

---

## Tracers

**Source:** [https://docs.vape.gg/features/modules/render/Tracers](https://docs.vape.gg/features/modules/render/Tracers)

# Tracers

Draws lines from your crosshairs to entities, helping you track down players and/or mobs and animals.

---

### Invisibles[​](#invisibles "Direct link to Invisibles")

When enabled, tracers will also be drawn to invisible entities.

### Color By Distance[​](#color-by-distance "Direct link to Color By Distance")

When enabled, the tracers rendered will differ in color depending on the entity's distance to you, painting tracers to nearby targets red, and distant targets green.

### Highlight If Focusing[​](#highlight-if-focusing "Direct link to Highlight If Focusing")

Highlights tracers drawn to entities that are looking at you.

### Render Players[​](#render-players "Direct link to Render Players")

When enabled, tracers will be rendered to Player entities.

#### Distance Check[​](#distance-check "Direct link to Distance Check")

When enabled, tracers will only be rendered on Player entities when they are within the distance range specified by **[Player Distance](#player-distance)**.

##### Player Distance[​](#player-distance "Direct link to Player Distance")

Specifies the minimum and maximum distance that a Player entity can be from you to have a tracer rendered to them. Players closer to you than the minimum, or further from you than the maximum will not have a tracer rendered for them.

#### Player Color[​](#player-color "Direct link to Player Color")

Specifies the color to paint tracers rendered to Player entities with.

### Render Animals[​](#render-animals "Direct link to Render Animals")

When enabled, tracers will be rendered to peaceful Animal (cow, chicken, etc) entities.

#### Distance Check[​](#distance-check-1 "Direct link to Distance Check")

When enabled, tracers will only be rendered on Animal entities when they are within the distance range specified by **[Animal Distance](#animal-distance)**.

##### Animal Distance[​](#animal-distance "Direct link to Animal Distance")

Specifies the minimum and maximum distance that a Animal entity can be from you to have a tracer rendered to them. Animals closer to you than the minimum, or further from you than the maximum will not have a tracer rendered for them.

#### Animal Color[​](#animal-color "Direct link to Animal Color")

Specifies the color to paint tracers rendered to Animal entities with.

### Render Mobs[​](#render-mobs "Direct link to Render Mobs")

When enabled, tracers will be rendered to hostile Mob (skeleton, zombie, etc) entities.

#### Distance Check[​](#distance-check-2 "Direct link to Distance Check")

When enabled, tracers will only be rendered on Mob entities when they are within the distance range specified by **[Mob Distance](#mob-distance)**.

##### Mob Distance[​](#mob-distance "Direct link to Mob Distance")

Specifies the minimum and maximum distance that a Mob entity can be from you to have a tracer rendered to them. Mobs closer to you than the minimum, or further from you than the maximum will not have a tracer rendered for them.

#### Mob Color[​](#mob-color "Direct link to Mob Color")

Specifies the color to paint tracers rendered to Mob entities with.

---

## Trajectories

**Source:** [https://docs.vape.gg/features/modules/render/Trajectories](https://docs.vape.gg/features/modules/render/Trajectories)

# Trajectories

Predicts and displays the flight path of projectiles, helping you aim more accurately.

---

### Aiming Color[​](#aiming-color "Direct link to Aiming Color")

The color of the trajectory when the projectile will hit an entity.

### Trajectory Color[​](#trajectory-color "Direct link to Trajectory Color")

The color of the trajectory when the projectile will not hit an entity.

### Target Color[​](#target-color "Direct link to Target Color")

The color of the marker at the end of the trajectory when it intersects with an entity.

### Ghost Bow Charge[​](#ghost-bow-charge "Direct link to Ghost Bow Charge")

Displays a trajectory for a fully charged bow shot even when not actively drawing the bow.

---

## AntiFireball

**Source:** [https://docs.vape.gg/features/modules/utility/AntiFireball](https://docs.vape.gg/features/modules/utility/AntiFireball)

# AntiFireball

Automatically aims and swings at incoming fireballs to deflect them.

---

### Angle Limit[​](#angle-limit "Direct link to Angle Limit")

Sets the maximum angle that an incoming fireball can be from your crosshair's position for the fireball to be deflected. Fireballs outside this FOV will not be deflected.

### Aim Speed[​](#aim-speed "Direct link to Aim Speed")

Controls how quickly your aim adjusts to intercept the fireball.

### Stop Movement[​](#stop-movement "Direct link to Stop Movement")

Disables movement inputs while deflecting fireballs, preventing accidental falls during sensitive maneuvers.

- **Move on Finish**: Re-enables movement keys after deflecting the fireball.

### Silent Aim[​](#silent-aim "Direct link to Silent Aim")

Adjusts your aim silently, so your view remains unchanged on your screen while your character appears to react to the fireball from other players' perspectives. For more information on how Silent Aim works, check out it's **[documentation](/features/settings/Silent-Aim)**.

---

## AutoAnchor

**Source:** [https://docs.vape.gg/features/modules/utility/AutoAnchor](https://docs.vape.gg/features/modules/utility/AutoAnchor)

# AutoAnchor

Automatically places, charges, and detonates a respawn anchor for explosive damage.

---

### Mode[​](#mode "Direct link to Mode")

Controls when AutoAnchor starts its anchor sequence.

- **On bind**: Places, charges, and detonates an anchor when the module bind is pressed.
- **On place**: Automatically charges and detonates after you manually place an anchor.

### Double Anchor[​](#double-anchor "Direct link to Double Anchor")

Places a second anchor immediately upon detonation of the first.

### Safe Anchor[​](#safe-anchor "Direct link to Safe Anchor")

Attempts to place a glowstone block between you and the anchor before charging it, reducing explosion damage by adding block cover.

If a safe cover position cannot be found, or if you do not have enough glowstone for both the cover block and the anchor charge, AutoAnchor continues with the normal charge sequence.

With **Aim Assist** enabled, AutoAnchor finds the exact cover placement face and aims at it automatically. With **Aim Assist** disabled, move your aim downward and then sweep it back upward toward the anchor.

### Explosion Item Whitelist[​](#explosion-item-whitelist "Direct link to Explosion Item Whitelist")

Swaps to the first whitelisted hotbar item before detonating the anchor instead of swapping back to the anchor slot.

- **Explosion Item**: The item list used when choosing what to hold for detonation. For more information, **[see the documentation for "Limit to Item" Settings](/values/LimitItems)**.

### Aim Assist[​](#aim-assist "Direct link to Aim Assist")

Automatically aims at anchor interaction points and Safe Anchor cover-placement targets while placing and charging.

### Silent Aim[​](#silent-aim "Direct link to Silent Aim")

Uses the Silent Aim system to aim without visually moving your camera. For more information, **[see the documentation for Silent Aim](/features/settings/Silent-Aim)**.

### Aim Speed[​](#aim-speed "Direct link to Aim Speed")

Controls the speed of aim rotations when placing and charging anchors.

### Delay[​](#delay "Direct link to Delay")

Controls the delay between each AutoAnchor action.

### Placement Handling[​](#placement-handling "Direct link to Placement Handling")

AutoAnchor searches for a valid placement target, aims at the target block or anchor, confirms placement after clicking, and retries briefly before ending the sequence if placement cannot be confirmed.

---

## AutoMace

**Source:** [https://docs.vape.gg/features/modules/utility/AutoMace](https://docs.vape.gg/features/modules/utility/AutoMace)

# AutoMace

Selects and swaps to an appropriate mace when attacking. It can also aim and attack automatically during a mace smash.

AutoMace is available on Minecraft 1.21.4 and later.

---

### Target Settings[​](#target-settings "Direct link to Target Settings")

Specifies which entities are considered valid targets. For more information, [**see the documentation for "Target Settings".**](/values/TargetSettings)

### Aim[​](#aim "Direct link to Aim")

Aims at the nearest valid target while you are falling for a smash attack.

- **Silent Aim**: Uses the Silent Aim system. **[Learn more here](/features/settings/Silent-Aim)**.
- **Aim Range**: Sets the maximum horizontal distance at which AutoMace searches for a target.

### Attack[​](#attack "Direct link to Attack")

Automatically attacks a valid mace target while you are falling.

- **Extra Delay**: Adjusts the attack timing in ticks after the normal attack cooldown. Negative values attack before the cooldown is complete. A valid mace smash bypasses the normal cooldown and this extra delay.

### Auto Unequip Elytra[​](#auto-unequip-elytra "Direct link to Auto Unequip Elytra")

Equips a chestplate from your hotbar when your predicted fall can reach a mace target, allowing the fall to become a mace smash.

- **Re-equip Elytra**: Puts the Elytra back on after AutoMace detects the upward movement from a mace bounce.

### Smash Only[​](#smash-only "Direct link to Smash Only")

Only swaps to a mace when the fall will count as a smash attack.

### Mace Selection[​](#mace-selection "Direct link to Mace Selection")

Controls how AutoMace chooses a mace from your hotbar.

- **Manual**: Uses the enchantment selected under **Mace Type**.
- **Auto**: Chooses the best mace enchantment for your fall distance and the target's armor.

#### Mace Type[​](#mace-type "Direct link to Mace Type")

Selects a **Density** or **Breach** mace while **Mace Selection** is set to **Manual**. This setting can be bound to a key to cycle between the two types in game.

### Stun Slam[​](#stun-slam "Direct link to Stun Slam")

When a usable axe and mace are available, attacking a player with a raised shield starts an axe hit followed by a mace hit.

- **Chance**: Sets the chance that the Stun Slam sequence activates.

### Limit to Items[​](#limit-to-items "Direct link to Limit to Items")

Restricts AutoMace so that it only functions while specified items are held. For more information, **[see the documentation for "Limit to Item" settings](/values/LimitItems)**.

---

## AutoPearl

**Source:** [https://docs.vape.gg/features/modules/utility/AutoPearl](https://docs.vape.gg/features/modules/utility/AutoPearl)

# AutoPearl

Detects and automatically throws a pearl to follow opponents who pearl away. Ideal for maintaining pressure in combat.

---

### Mode[​](#mode "Direct link to Mode")

- **On Bind**: Activates the module with a hotkey, giving you control over when to chase pearls.
- **Aggro**: Constantly scans for enemy pearls and automatically chases them.

### Aim speed[​](#aim-speed "Direct link to Aim speed")

The speed that you aim at.

### Angle Limit[​](#angle-limit "Direct link to Angle Limit")

The maximum angle (in degrees) from your crosshair for a pearl to be considered a valid target.

### Min health[​](#min-health "Direct link to Min health")

Minimum health you must have in order for the module to activate. This is useful for avoiding unnecessary deaths when you are low on health.

### Distance Limit[​](#distance-limit "Direct link to Distance Limit")

The minimum distance between you and the enemy pearl's landing spot for it to be chased. Helps prevent overly "eager" aggressive pearling, when the enemy is going to land only a few blocks away from you.

### Vertical Check[​](#vertical-check "Direct link to Vertical Check")

Only chases pearls landing at a reasonable height difference to avoid unfavorable situations

### Pearl cooldown[​](#pearl-cooldown "Direct link to Pearl cooldown")

The time in seconds that you must wait before throwing another pearl. This is useful for avoiding the "pearl cooldown" that occurs when you throw a pearl too quickly on servers.

### Silent Aim[​](#silent-aim "Direct link to Silent Aim")

Adjusts your aim silently. For more information, **[see the documentation for Silent Aim](/features/settings/Silent-Aim)**.

### Limit to items[​](#limit-to-items "Direct link to Limit to items")

Prevents module from working unless you're holding a whitelisted item. For more information, **[see the documentation for "Limit Items" Settings](/values/LimitItems)**.

---

## Clutch

**Source:** [https://docs.vape.gg/features/modules/utility/Clutch](https://docs.vape.gg/features/modules/utility/Clutch)

# Clutch

Automatically places blocks to prevent fatal falls, saving you in critical moments.

---

### Activation Conditions[​](#activation-conditions "Direct link to Activation Conditions")

- **On void**: Attempts to save you from falling into the void.
- **On lethal fall**: Activates when you're about to take lethal fall damage.
- **On more than x blocks**: Triggers when you're about to fall more than a specified number of blocks.
  - **Blocks**: Sets the minimum fall distance to trigger the module.

### Additional Settings[​](#additional-settings "Direct link to Additional Settings")

- **Silent aim**: Uses the Silent Aim system. **[Learn more here](/features/settings/Silent-Aim)**.
- **Show block count**: Displays the number of available blocks for clutching near your crosshair
- **Reset angle**: Resets your view angles to their original position after clutching
- **Return to slot**: Switches back to your original hotbar slot after clutching
- **Allow staircase up**: Allows clutch to staircase on repeat jumps
- **Clutch move delay**: Freezes movement for a few ticks after completing a long clutch
- **Max blocks**: Limits clutching to situations that require fewer than the specified number of blocks. Useful for preventing the module from building long, suspicious pillars.

### Block Selection[​](#block-selection "Direct link to Block Selection")

Specifies rules on which blocks should be allowed to be used for clutching.

- **Blacklist**: Specifies blocks that cannot be used for clutching.
- **Whitelist**: Restricts clutching to only when holding specific items.

For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## HitSwap

**Source:** [https://docs.vape.gg/features/modules/utility/HitSwap](https://docs.vape.gg/features/modules/utility/HitSwap)

# HitSwap

Swaps into another weapon on attack, copying its attributes.
Also known as BreachSwap, ZeroTick.

Works with manual attacks and compatible automated attacks from modules such as Triggerbot, SilentAura, and AutoMace's standard automated attacks.

HitSwap is available on Minecraft 1.21.4 and later.

---

### Maces[​](#maces "Direct link to Maces")

Allows swapping to maces. Useful for breach swapping.

- **Smash only**: Only swaps to a mace while falling, ensuring a smash attack.
- **Require Breach**: Only swaps to maces with the Breach enchantment.
- **Require Density**: Only swaps to maces with the Density enchantment.

### Stun Slam[​](#stun-slam "Direct link to Stun Slam")

Swaps to an axe first to break the target's shield, then follows up with a mace slam.

### Axes[​](#axes "Direct link to Axes")

Allows swapping to axes. Useful for breaking shields.

### Swords[​](#swords "Direct link to Swords")

Allows swapping to swords with other effects, for example will swap to a sword with fire aspect.

### Limit items[​](#limit-items "Direct link to Limit items")

Functions only while holding whitelisted items. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## Panic

**Source:** [https://docs.vape.gg/features/modules/utility/Panic](https://docs.vape.gg/features/modules/utility/Panic)

# Panic

Disables all currently enabled modules at the press of a hotkey. Must be bound to a hotkey to function. Useful for situations where you have an "oh shit" moment and don't want to get caught cheating, e.g. if you're playing on a server that regularly doesn't spawn anti-cheat bots around you unless the server thinks you might be cheating, and you suddenly see a bunch of bots flying around you (aka, the server thinks you might be cheating and wants to check if you start hitting those bots).

---

### Re-enable[​](#re-enable "Direct link to Re-enable")

If enabled, this module turns into a sort of "toggle", where when you hit it the first time it will disable all the currently enabled modules, and when you hit it the second time it will re-enable all those disabled modules.

---

## PearlCatch

**Source:** [https://docs.vape.gg/features/modules/utility/PearlCatch](https://docs.vape.gg/features/modules/utility/PearlCatch)

# PearlCatch

Throws an ender pearl, then throws a wind charge to catch the pearl and redirect its flight.

PearlCatch is available on Minecraft 1.21.4 and later. An ender pearl and a wind charge must both be in your hotbar. After the sequence, PearlCatch restores your original hotbar slot and disables itself.

---

### Aim Mode[​](#aim-mode "Direct link to Aim Mode")

Controls the direction used for the pearl and wind charge sequence.

- **Upward**: Throws the pearl straight up before aiming the wind charge to intercept it.
- **Current Aim**: Uses the direction you were looking when PearlCatch was activated and plans the pearl and wind charge throws around that direction.

### Aim Speed[​](#aim-speed "Direct link to Aim Speed")

Controls how quickly PearlCatch aims each throw.

### Silent Aim[​](#silent-aim "Direct link to Silent Aim")

Uses the Silent Aim system so that the required rotations do not visibly move your camera. **[Learn more here](/features/settings/Silent-Aim)**.

### Charge Delay[​](#charge-delay "Direct link to Charge Delay")

Sets how many ticks PearlCatch waits after throwing the pearl before throwing the wind charge. PearlCatch may wait longer when needed to line up a valid intercept.

---

## Scaffold

**Source:** [https://docs.vape.gg/features/modules/utility/Scaffold](https://docs.vape.gg/features/modules/utility/Scaffold)

# Scaffold

Assists with bridging techniques.

---

### Mode[​](#mode "Direct link to Mode")

Controls the bridging technique used by the module.

- **Legit**: Automatically sneaks at the edge of blocks when moving backwards. Also known as fast bridging, eagle bridging, or ninja bridging. **Does not place blocks for you** - right click manually, use right clicker, fastplace, etc.

  - **Sneak Delay**: The delay before releasing sneak after placing a block.
  - **Require Sneak**: Only activates while manually holding sneak.
- **GodBridge**: Places blocks while walking at full speed diagonally, without sneaking.

  - **Activation Blocks**: The number of blocks you must manually place before the module takes over.
- **TellyBridge**: Places blocks behind you while jumping.

  - **Require Right Click**: Only bridges while holding right click and the backwards movement key.
  - **Activation Blocks**: The number of blocks you must manually place before the module takes over.
  - **Y Increase**: The maximum upward movement allowed from the start of bridging.

### Block Count[​](#block-count "Direct link to Block Count")

Renders your current block count on the center of the screen.

### Pitch Check[​](#pitch-check "Direct link to Pitch Check")

Scaffold will only activate when you are aiming below the specified angle. Useful for preventing accidental activation.

### Block Selection[​](#block-selection "Direct link to Block Selection")

- **Blacklist**: Prevents scaffold from using certain blocks.
- **Whitelist**: Restricts scaffold to only activate when holding whitelisted blocks.

For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## ShieldBreaker

**Source:** [https://docs.vape.gg/features/modules/utility/ShieldBreaker](https://docs.vape.gg/features/modules/utility/ShieldBreaker)

# ShieldBreaker

Swaps to an axe when you attack a player with a raised shield, attacks, and then returns to your original hotbar slot.

ShieldBreaker is available on Minecraft 1.21.4 and later.

---

### Swap Delay[​](#swap-delay "Direct link to Swap Delay")

Sets the delay in ticks between swapping to an axe and attacking.

### Swap Back Delay[​](#swap-back-delay "Direct link to Swap Back Delay")

Sets the delay in ticks between attacking and returning to the original hotbar slot.

### Double Click[​](#double-click "Direct link to Double Click")

Attacks again immediately after breaking the shield to knock the target back.

### Limit to Items[​](#limit-to-items "Direct link to Limit to Items")

Restricts ShieldBreaker so that it only functions while specified items are held. For more information, **[see the documentation for "Limit to Item" settings](/values/LimitItems)**.

---

## TargetFilter

**Source:** [https://docs.vape.gg/features/modules/utility/TargetFilter](https://docs.vape.gg/features/modules/utility/TargetFilter)

# TargetFilter

Filters which players can be targeted by other modules. Consolidates team filtering and bot detection into a single module.

---

### Teams by Server[​](#teams-by-server "Direct link to Teams by Server")

Ignore players on your team as designated by the server.

### Teams by Color[​](#teams-by-color "Direct link to Teams by Color")

Ignore players that share your nametag color, treating them as teammates.

- **Recolor Visuals**: Changes the colors of visual modules (such as Tracers and ESP) to match each player's team color.
- **Auto-Detect Color**: Automatically detects your team color based on your nametag. When disabled, you can manually select your team color.

### AntiBot[​](#antibot "Direct link to AntiBot")

Prevents modules from attacking or targeting server-side anti-cheat bots.

---

## Velocity

**Source:** [https://docs.vape.gg/features/modules/utility/Velocity](https://docs.vape.gg/features/modules/utility/Velocity)

# Velocity[Warning](/guides/general/anti-cheats)

Reduces knockback taken when hit. Helps prevent long combos by keeping you closer to your opponent.

---

### Horizontal[​](#horizontal "Direct link to Horizontal")

Reduces knockback on the X/Z axes.

### Vertical[​](#vertical "Direct link to Vertical")

Reduces knockback on the Y axis.

### Ticks[​](#ticks "Direct link to Ticks")

Delays knockback reduction for the specified number of ticks. This does not delay **Kite Mode**.

### Kite Mode[​](#kite-mode "Direct link to Kite Mode")

Increases knockback when hit from behind, useful for kiting opponents.

- **Kite Horizontal**: Increases knockback on the X/Z axes when hit from behind.
- **Kite Vertical**: Increases knockback on the Y axis when hit from behind.
- **Always Kite**: Activates Kite Mode regardless of hit direction.

### Chance[​](#chance "Direct link to Chance")

The percentage chance of the module activating to reduce knockback.

### Only When Targeting[​](#only-when-targeting "Direct link to Only When Targeting")

Activates the module only when the opponent hitting you is near your crosshair.

### Water Check[​](#water-check "Direct link to Water Check")

Disables the module while in water to avoid suspicion.

---

## WindCharge

**Source:** [https://docs.vape.gg/features/modules/utility/WindCharge](https://docs.vape.gg/features/modules/utility/WindCharge)

# WindCharge

Automatically uses a wind charge upon pressing bind. Aims to feet and times a jump for maximum height. Only available in modern combat versions.

---

### Aim speed[​](#aim-speed "Direct link to Aim speed")

Speed of any aiming done.

### Silent aim[​](#silent-aim "Direct link to Silent aim")

Uses the Silent Aim system. **[Learn more here](/features/settings/Silent-Aim)**

---

## Block-In

**Source:** [https://docs.vape.gg/features/modules/world/Block-In](https://docs.vape.gg/features/modules/world/Block-In)

# Block-In[Ghost](/guides/general/anti-cheats)

Automatically builds walls around you for protection when you press the assigned hotkey.

---

### Aim Speed[​](#aim-speed "Direct link to Aim Speed")

Controls how quickly your view angles change while building. Higher values mean faster construction.

### Place Delay[​](#place-delay "Direct link to Place Delay")

Sets random delays between block placements, affecting the overall build speed.

### Silent Aim[​](#silent-aim "Direct link to Silent Aim")

Adjusts your aim silently, so you appear to be looking in a different direction while building. For more information, **[see the documentation for Silent Aim](/features/settings/Silent-Aim)**.

### Sneak[​](#sneak "Direct link to Sneak")

Automatically sneaks while placing blocks.

- **Keep Sneak**: Remains sneaking after building. Press sneak again to stop.

### Bed Finder[​](#bed-finder "Direct link to Bed Finder")

Creates an opening in the structure towards a nearby bed, useful in Bedwars.

### Block Priority[​](#block-priority "Direct link to Block Priority")

Controls which blocks are preferred when building the structure.

- **Lowest cost**: Prioritizes the least valuable blocks in your inventory.
- **Hardest**: Prioritizes the hardest (most durable) blocks in your inventory.

### Return to Last Slot[​](#return-to-last-slot "Direct link to Return to Last Slot")

Switches back to your original hotbar slot after building.

### Use Blacklist[​](#use-blacklist "Direct link to Use Blacklist")

Prevents the use of specific blocks when building the structure. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## ChestSteal

**Source:** [https://docs.vape.gg/features/modules/world/ChestSteal](https://docs.vape.gg/features/modules/world/ChestSteal)

# ChestSteal

Automatically loots items from chests when you open them.

---

### Check In Menu[​](#check-in-menu "Direct link to Check In Menu")

Attempts to avoid interacting with custom server menus (may not be 100% effective).

### Best Only[​](#best-only "Direct link to Best Only")

Only takes items that are better than what you already have, preventing clutter in gamemodes like Skywars or Survival Games.

### Keep Open[​](#keep-open "Direct link to Keep Open")

Keeps the chest open after looting, allowing you to manually manage items or organize your inventory.

### Shuffle[​](#shuffle "Direct link to Shuffle")

Takes items in a random order, mimicking human-like behavior.

### Click Delay[​](#click-delay "Direct link to Click Delay")

Sets random delays between clicks to make the looting process less suspicious.

### Blacklist[​](#blacklist "Direct link to Blacklist")

Specifies items that the module should never take, helping you avoid unwanted loot. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## Anti-AFK

**Source:** [https://docs.vape.gg/features/modules/world/Anti-AFK](https://docs.vape.gg/features/modules/world/Anti-AFK)

# Anti-AFK

Automatically prevents you from being kicked for being AFK by issuing movement inputs at intervals.

---

### Start Delay[​](#start-delay "Direct link to Start Delay")

Sets the minimum and maximum time (in seconds) the module waits for your input before activating.

### Frequency[​](#frequency "Direct link to Frequency")

Controls how often the module makes you move. Higher values mean more frequent movement.

### Keep Close[​](#keep-close "Direct link to Keep Close")

Limits movement to a small area around your original position, preventing you from wandering off or falling.

### Rotation[​](#rotation "Direct link to Rotation")

Enables the module to also adjust your look angles, potentially bypassing additional anti-AFK checks.

#### Silent Aim[​](#silent-aim "Direct link to Silent Aim")

Uses the Silent Aim system. **[Learn more here](/features/settings/Silent-Aim)**.

#### Max Yaw Change[​](#max-yaw-change "Direct link to Max Yaw Change")

Limits vertical head movement.

#### Max Pitch Change[​](#max-pitch-change "Direct link to Max Pitch Change")

Limits horizontal head movement.

---

## AutoFish

**Source:** [https://docs.vape.gg/features/modules/utility/AutoFish](https://docs.vape.gg/features/modules/utility/AutoFish)

# AutoFish

Automatically fishes for you.

---

### Recast Ground[​](#recast-ground "Direct link to Recast Ground")

Automatically recasts your fishing line if the hook hits the ground.

### Recast Caught[​](#recast-caught "Direct link to Recast Caught")

Automatically recasts your fishing line if the hook catches onto an Entity (e.g. if someone wanders in front of your fishing line while you're AFK, the module will reel in and recast so it's not stuck in a loop waiting for a fish to bite while your hook is actually not even in water).

---

## AutoTool

**Source:** [https://docs.vape.gg/features/modules/world/AutoTool](https://docs.vape.gg/features/modules/world/AutoTool)

# AutoTool

Automatically switches to the hotbar slot that contains the tool best suited for breaking whatever block you are breaking at the time.

---

### Swap Weapon[​](#swap-weapon "Direct link to Swap Weapon")

Automatically swaps to the best weapon on your hotbar when you start trying to hit an Entity.

### Wait Delay[​](#wait-delay "Direct link to Wait Delay")

Specifies how long to wait (in milliseconds) before switching to the optimal tool for the job.

### Swap Back[​](#swap-back "Direct link to Swap Back")

If enabled, when you're done swinging at the block / entity, switches back to the original hotbar slot you were using before AutoTool stepped in.

#### Swap Delay[​](#swap-delay "Direct link to Swap Delay")

Specifies how long to wait (in milliseconds) before switching back to the original slot.

### Tool Hover Check[​](#tool-hover-check "Direct link to Tool Hover Check")

Only swaps your hotbar slots if you are actually hovering over the block you're trying to break. Helpful to prevent switching to a tool while in Combat.

#### Hover Delay[​](#hover-delay "Direct link to Hover Delay")

Adds an extra delay before swapping to a tool after recently being in Combat. Helps prevent accidentally switching to a mining tool when you misclick a block while swinging your sword at an enemy.

### Only While Sneaking[​](#only-while-sneaking "Direct link to Only While Sneaking")

If enabled, AutoTool will only take effect while you are sneaking.

---

## FastPlace

**Source:** [https://docs.vape.gg/features/modules/world/FastPlace](https://docs.vape.gg/features/modules/world/FastPlace)

# FastPlace

Reduces the delay between block placements, allowing you to place blocks faster when holding right-click.

---

### Held Item[​](#held-item "Direct link to Held Item")

- **All**: Activates regardless of the held item.
- **Blocks**: Activates only when holding a block.
- **Projectiles**: Activates only when holding a projectile.

### Delay[​](#delay "Direct link to Delay")

Sets the delay (in ticks) between block placements while holding right-click. Lower values mean faster placement.

---

## Freecam

**Source:** [https://docs.vape.gg/features/modules/world/Freecam](https://docs.vape.gg/features/modules/world/Freecam)

# Freecam

Detach your camera from your player, allowing you to fly and see through walls without moving your character on the server.

---

### Speed[​](#speed "Direct link to Speed")

Controls how fast the camera moves in freecam mode.

### Allow Interacting[​](#allow-interacting "Direct link to Allow Interacting")

Enables interaction with blocks and entities while in freecam (may trigger anti-cheat on some servers).

### Spawn Fake[​](#spawn-fake "Direct link to Spawn Fake")

Spawns a "fake" player at your original location to maintain realistic physics interactions.

- **Move Fake**: Allows you to move the fake player with arrow keys while in freecam.

---

## MLG

**Source:** [https://docs.vape.gg/features/modules/world/MLG](https://docs.vape.gg/features/modules/world/MLG)

# MLG

Places water or cobwebs beneath you to prevent fall damage.

---

### On at least X damage[​](#on-at-least-x-damage "Direct link to On at least X damage")

Activates MLG when you're about to take at least this much fall damage. Takes into account feather falling and other effects.

### On lethal fall[​](#on-lethal-fall "Direct link to On lethal fall")

Activates MLG when you're about to take enough fall damage to kill you. Takes into account feather falling and other effects.

### Aim speed[​](#aim-speed "Direct link to Aim speed")

How quickly MLG changes your look angles to the necessary position.

### Silent Aim[​](#silent-aim "Direct link to Silent Aim")

Uses the Silent Aim system.

### Check inventory[​](#check-inventory "Direct link to Check inventory")

MLG will check your inventory for MLG items when one is not found in your hotbar, and will quickly move them to your hotbar to MLG with when needed.

### Use Buckets[​](#use-buckets "Direct link to Use Buckets")

Uses a Water Bucket from your hotbar to place water and then picks it back up.

#### Pick up water[​](#pick-up-water "Direct link to Pick up water")

Picks up the water after you're done MLG'ing.

### Use Cobwebs[​](#use-cobwebs "Direct link to Use Cobwebs")

Uses Cobwebs from your hotbar to place them beneath you.

---

## Parkour

**Source:** [https://docs.vape.gg/features/modules/world/Parkour](https://docs.vape.gg/features/modules/world/Parkour)

# Parkour

Automatically jumps for you when you're at the edge of a block.

---

## SafeWalk

**Source:** [https://docs.vape.gg/features/modules/world/SafeWalk](https://docs.vape.gg/features/modules/world/SafeWalk)

# SafeWalk

Helps prevent you from walking off the edge of a block by cancelling your movement input before you fall. This module does this without having to make your character sneak, so you can keep your regular speed while using this module.

---

### Direction Check[​](#direction-check "Direct link to Direction Check")

If enabled, the module will not prevent you from walking off the edge of a block if you're trying to move forward to get off the block. The module will still prevent you from falling if you're holding any other movement keybind (A / left, D / right, S / back).

---

## XRay

**Source:** [https://docs.vape.gg/features/modules/world/XRay](https://docs.vape.gg/features/modules/world/XRay)

# XRay

Make the world around you transparent, except for a select few blocks you're looking for, allowing you to find them easily and effectively.

---

### Opacity[​](#opacity "Direct link to Opacity")

How transparent unwanted blocks are (0 = fully see through, 100 = fully opaque)

### Cave Mode[​](#cave-mode "Direct link to Cave Mode")

Only highlights blocks that have at least one side exposed to air

### Xray Blocks[​](#xray-blocks "Direct link to Xray Blocks")

Specify the blocks you're looking for. For more information, **[**see the documentation for "Limit to Item" Settings**](/values/LimitItems).**

---

## InvCleaner

**Source:** [https://docs.vape.gg/features/modules/inventory/InvCleaner](https://docs.vape.gg/features/modules/inventory/InvCleaner)

# InvCleaner

Automatically removes unnecessary items from your inventory, keeping it clean and manageable. Especially useful for gamemodes with rapid item acquisition.

---

### Activation[​](#activation "Direct link to Activation")

- **On Key**: Activates the module with a hotkey.
  - **Open Inventory**: Automatically opens inventory when the hotkey is pressed, making the cleaning process less suspicious.
- **Toggle**: Runs continuously in the background, cleaning your inventory automatically.
  - **Inventory Only**: Only cleans when you manually open your inventory.

### Delay[​](#delay "Direct link to Delay")

Sets random delays between item drops to avoid suspicion.

### Best Items[​](#best-items "Direct link to Best Items")

Keeps the best armor, sword, axe, pickaxe, and bow, useful for helping keep momentum and snowball in fast-paced gamemodes like Skywars.

### Remove Negative Potions[​](#remove-negative-potions "Direct link to Remove Negative Potions")

Always discards negative potions, prioritizing combat effectiveness in fast-paced gamemodes.

### Remove Food[​](#remove-food "Direct link to Remove Food")

Always discards food, except for Golden Apples.

### Blacklist[​](#blacklist "Direct link to Blacklist")

Specifies which items should be removed from the inventory. By default, the blacklist contains sticks, string, flint, compasses, feathers, glass bottles, enchanting tables, chests, and anvils.

For more information, **[see the documentation for "Limit to Item" settings](/values/LimitItems)**.

---

## ArmorSwitch

**Source:** [https://docs.vape.gg/features/modules/inventory/ArmorSwitch](https://docs.vape.gg/features/modules/inventory/ArmorSwitch)

# ArmorSwitch

ArmorSwitch allows you to bind a hotkey to automatically switch between two configurable sets of armour.

This was implemented at the request of HCF players who liked to "dual-class"-- e.g. leaving the base as a bard (full gold armour), but carrying a backup set of diamond armour in their inventory if they need to switch to become a fighter quickly.

To put it simply, when you press the key this module is bound to, it will check what set of armour you're currently wearing, and switch to the other one if possible.

---

### Sets[​](#sets "Direct link to Sets")

Specifies the armor sets to switch between.

### Delay[​](#delay "Direct link to Delay")

The delay used between each piece of armour being equipped.

---

## AutoArmor

**Source:** [https://docs.vape.gg/features/modules/inventory/AutoArmor](https://docs.vape.gg/features/modules/inventory/AutoArmor)

# AutoArmor

Automatically manages your armor, equipping the best pieces available based on certain conditions. Useful for gamemodes like Skywars or Survival Games.

---

### Open Inventory[​](#open-inventory "Direct link to Open Inventory")

Allows the module to automatically open your inventory and equip better armor when found. Recommended to leave enabled for optimal performance.

tip

If you prefer not to be interrupted, disable this option and enable **Inventory Only** instead.

### Inventory Only[​](#inventory-only "Direct link to Inventory Only")

Restricts the module to only manage armor when you manually open your inventory.

### Check Durability[​](#check-durability "Direct link to Check Durability")

Considers both durability and material when choosing the best armor. Recommended to leave enabled for optimal choices.

### Drop Equipped[​](#drop-equipped "Direct link to Drop Equipped")

Automatically drops replaced armor pieces, useful for inventory management in fast-paced gamemodes.

### Combat Check[​](#combat-check "Direct link to Combat Check")

Prevents the module from swapping armor during combat, avoiding interruptions and suspicious behavior.

### Delay[​](#delay "Direct link to Delay")

Sets random delays between the module's actions to make it less obvious.

---

## AutoHotbar

**Source:** [https://docs.vape.gg/features/modules/inventory/AutoHotbar](https://docs.vape.gg/features/modules/inventory/AutoHotbar)

# AutoHotbar

Automatically organizes your hotbar to your liking, ensuring consistent item placement in fast-paced gamemodes. Helps improve muscle memory and reaction time in combat.

---

### Activation[​](#activation "Direct link to Activation")

- **On Key**: Activates the module with a hotkey, giving you control over when to reorganize your hotbar.
- **Toggle**: Runs continuously in the background, automatically adjusting your hotbar.
  - **Open Inventory**: Forces your inventory open when reorganizing, mimicking natural behavior but may be disruptive.

### Delay[​](#delay "Direct link to Delay")

The delay between each click in your inventory during reorganization.

### Hotbars[​](#hotbars "Direct link to Hotbars")

Allows you to create and save multiple hotbar loadouts. Select the desired loadout, and the module will try to replicate it with your available items.

---

## AutoTotem

**Source:** [https://docs.vape.gg/features/modules/inventory/AutoTotem](https://docs.vape.gg/features/modules/inventory/AutoTotem)

# AutoTotem

Automatically equips totems of undying to your offhand slot.

---

### Open Inventory[​](#open-inventory "Direct link to Open Inventory")

Opens your inventory to equip a totem when one is not in the hotbar.

- **Silent Open**: Silently opens your inventory without displaying the GUI.
  - **Silent Move Delay**: Controls how long AutoTotem waits before temporarily preventing movement inputs after silently opening the inventory.
- **Close Inventory**: Closes the visible inventory opened by AutoTotem after the totem has been equipped.

### Inventory Only[​](#inventory-only "Direct link to Inventory Only")

Only equips totems while the inventory screen is already open.

### Combat Only[​](#combat-only "Direct link to Combat Only")

Enabled by default. Only equips a totem after AutoTotem detects recent combat-like danger.

- **Activation Delay**: Controls how long combat danger must remain detected before AutoTotem becomes active.

### Refill[​](#refill "Direct link to Refill")

Refills a consumed main-hand totem from another inventory slot.

### Random Slot[​](#random-slot "Direct link to Random Slot")

Chooses a random totem from your inventory rather than always picking the first one found.

### Delay[​](#delay "Direct link to Delay")

A random delay before equipping a totem.

### Extra Randomization[​](#extra-randomization "Direct link to Extra Randomization")

Adds additional human-like timing variance to totem equipping.

### Show Totem Count[​](#show-totem-count "Direct link to Show Totem Count")

Displays the number of remaining totems on the center of the screen.

---

## InventoryManager

**Source:** [https://docs.vape.gg/features/modules/inventory/InventoryManager/](https://docs.vape.gg/features/modules/inventory/InventoryManager/)

# InventoryManager

Highly configurable, intelligently designed Inventory Management system. Capable of seamlessly maintaining an optimal hotbar setup, equipping the best armor available to you, all while managing your inventory and keeping it clean of junk. Effectively, this Module combines aspects of **[AutoHotbar](/features/modules/inventory/AutoHotbar)**, **[AutoArmor](/features/modules/inventory/AutoArmor)**, and **[InvCleaner](/features/modules/inventory/InvCleaner)**, with a significantly more customizable interface.

Great for fast-paced minigame style gamemodes where you have the opportunity to snowball, and keeping momentum is key.

---

### Activation[​](#activation "Direct link to Activation")

Specifies how, and when, you want the Module to take action and try to manage your inventory for you.

- **On Key**: The module will only kick into action when you toggle it on via hotkey. This allows you to have greater control and discretion over when the module "takes control".
- **Toggle**: Keeps the module running in the background, waiting for a moment where it can kick in and optimize your inventory.
  - **Open Inventory**: If enabled, the module will take action on it's own, whenever it detects that it can make a beneficial change for you. If disabled, the module will wait until you manually enter your inventory to make changes for you.
  - **Combat Check**: Prevents the module from erroneously kicking in while you're in Combat.

### Click Delay[​](#click-delay "Direct link to Click Delay")

Specifies the minimum and maximum delay used to randomize the time between clicks in the Inventory.

### Inventory Presets[​](#inventory-presets "Direct link to Inventory Presets")

This is where you can setup different inventory configurations to ask the Module to help you maintain. This GUI can be daunting at first, but it is only as complex as you need it to be. For our guide on how to use it, **[see here](/features/modules/inventory/InventoryManager/gui)**.

---

[## Using the GUIs

Learn how to navigate InventoryManager's GUIs](/features/modules/inventory/InventoryManager/gui)

---

## InventoryFill

**Source:** [https://docs.vape.gg/features/modules/inventory/InventoryFill](https://docs.vape.gg/features/modules/inventory/InventoryFill)

# InventoryFill

While holding shift, automatically clicks while in inventory to quickly move items.

---

### CPS[​](#cps "Direct link to CPS")

Clicks per second while filling inventory. Values at or above 20 will click as fast as possible.

---

## Refill

**Source:** [https://docs.vape.gg/features/modules/inventory/Refill](https://docs.vape.gg/features/modules/inventory/Refill)

# Refill

Automatically refills your hotbar with healing items. Very useful for PotPvP, HCF, SoupPvP, etc.

---

### Vertical[​](#vertical "Direct link to Vertical")

Takes items vertically (top left to bottom left) instead of horizontally (top left to top right).

### Scatter[​](#scatter "Direct link to Scatter")

Takes items in a scattered pattern, making the refilling appear more random and less suspicious.

### Hotbar Clear[​](#hotbar-clear "Direct link to Hotbar Clear")

Clears "Junk Items" from your hotbar before refilling, maximizing space for healing items.

- **Non Junk Items**: An optional list of items to exclude from clearing. For more information, see the documentation for [**"Limit Items"**](/values/LimitItems) settings.

### Delay[​](#delay "Direct link to Delay")

Sets random delays between actions for a less obvious refilling process.

### Type[​](#type "Direct link to Type")

- **Both**: Refills both Instant Healing Potions and Mushroom Soups.
- **Pots**: Refills only Instant Healing Potions.
- **Soup**: Refills only Mushroom Soups.

---

## ThrowDebuff

**Source:** [https://docs.vape.gg/features/modules/inventory/ThrowDebuff](https://docs.vape.gg/features/modules/inventory/ThrowDebuff)

# ThrowDebuff

Automatically throws debuff potions from your hotbar when activated by a hotkey.

---

### Mode[​](#mode "Direct link to Mode")

- **All**: Throws all debuff potions on your hotbar.
- **One of Each**: Throws only one of each type of debuff potion.
- **First**: Throws the first debuff potion found on your hotbar.

### Potion Types[​](#potion-types "Direct link to Potion Types")

- **Harming**: Includes Splash Instant Harming/Damage potions.
- **Weakness**: Includes Splash Weakness potions.
- **Poison**: Includes Splash Poison potions.
- **Slowness**: Includes Splash Slowness potions.

### Delay[​](#delay "Direct link to Delay")

Sets random delays between potion throws to avoid suspicion.

### Scroll[​](#scroll "Direct link to Scroll")

Simulates mousewheel scrolling when switching between potions.

- **Scroll Delay**: The delay between each scroll action.

---

## Throwpot

**Source:** [https://docs.vape.gg/features/modules/inventory/Throwpot](https://docs.vape.gg/features/modules/inventory/Throwpot)

# Throwpot

Throws or consumes healing items (Splash Healing, Mushroom Soup) from your hotbar when activated by a hotkey.

---

### Type[​](#type "Direct link to Type")

- **Both**: Consumes both Instant Healing Potions and Mushroom Soups.
- **Pots**: Consumes only Instant Healing Potions.
- **Soup**: Consumes only Mushroom Soups.

### Mode[​](#mode "Direct link to Mode")

- **Dynamic**: Uses the appropriate number of healing items based on your missing health.
- **Single**: Uses only one healing item at a time.

### Delay[​](#delay "Direct link to Delay")

Sets random delays between using healing items to avoid suspicion.

### Scroll[​](#scroll "Direct link to Scroll")

Simulates mousewheel scrolling when switching between items.

- **Scroll Delay**: The delay between each scroll action.

### Random[​](#random "Direct link to Random")

Selects a healing item at random from your hotbar instead of sequentially.

### Throw Bowls[​](#throw-bowls "Direct link to Throw Bowls")

Automatically discards empty bowls after consuming Mushroom Soup.

---

## BackTrack

**Source:** [https://docs.vape.gg/features/modules/utility/BackTrack](https://docs.vape.gg/features/modules/utility/BackTrack)

# BackTrack

Temporarily delays position updates for a player you attack while they are moving away, giving you the useful parts of higher latency without applying constant lag.

---

### Latency[​](#latency "Direct link to Latency")

Controls how long target position updates can be delayed when BackTrack is active.

### Render Server Pos[​](#render-server-pos "Direct link to Render Server Pos")

Renders the target's last known server-side position while its updates are being delayed.

- **Color**: Customizes the color of the rendered server position.

---

## Blink

**Source:** [https://docs.vape.gg/features/modules/utility/Blink](https://docs.vape.gg/features/modules/utility/Blink)

# Blink

Chokes the packets you send to the server while enabled.

---

### Direction[​](#direction "Direct link to Direction")

Determines whether the module should choke outgoing packets only, or incoming and outgoing.

- **Outgoing Only**: Only choke outgoing packets. You will see your opponent's movements around you, but your opponents will not see yours.
- **Bi-directional**: Chokes incoming packets as well. You will not see server updates around you while the module is enabled.

### Type[​](#type "Direct link to Type")

Determines what type of packets to choke.

#### All[​](#all "Direct link to All")

Chokes all packets (movement, chat, etc).

#### Movement Only[​](#movement-only "Direct link to Movement Only")[Warning](/guides/general/anti-cheats)

Exclusively chokes movement packets.

### Breadcrumbs[​](#breadcrumbs "Direct link to Breadcrumbs")

When enabled, a "breadcrumb" trail will be spawned in the path that you are following, showing you your previous position (where you were when you enabled the module), and the path you'll follow as you "blink".

### Spawn Fake[​](#spawn-fake "Direct link to Spawn Fake")

A fake entity of yourself will be spawned at your location when the module is enabled. This entity is only visible to you, it's to help you remember where you were when you enabled the module.

### Auto Send[​](#auto-send "Direct link to Auto Send")

The module will automatically "unchoke" all queued packets once the specified threshold quantity is queued.

- **Send Threshold**: The amount of packets that must be ready to be sent, before the module automatically sends them.

---

## FakeLag

**Source:** [https://docs.vape.gg/features/modules/utility/FakeLag](https://docs.vape.gg/features/modules/utility/FakeLag)

# FakeLag

Simulates lag by adding a delay to the packets you send to the server.

---

### Mode[​](#mode "Direct link to Mode")

Determines the method this module will use in how to delay your packets.

#### Latency[​](#latency "Direct link to Latency")

Adds a constant amount of delay to your packets.

#### Dynamic[​](#dynamic "Direct link to Dynamic")

Dynamically adjusts your connection speed in order to give you advantages in combat.

#### Repel[​](#repel "Direct link to Repel")

Tunes FakeLag with the goal of keeping your opponent as far away from you as possible.

##### Transmission Offset[​](#transmission-offset "Direct link to Transmission Offset")

Higher values may make your connection more unstable, which may be a disadvantage or an advantage based on your original connection to the server.

### Delay[​](#delay "Direct link to Delay")

The amount of delay (in milliseconds) to wait for, before sending any given packet to the server. If you regularly have 50ms to a server, and this is set to 100ms, then you will effectively be playing on 150ms.

---

## KnockbackDelay

**Source:** [https://docs.vape.gg/features/modules/utility/KnockbackDelay](https://docs.vape.gg/features/modules/utility/KnockbackDelay)

# KnockbackDelay

Delays incoming knockback packets after you are hit, making your knockback arrive later instead of immediately reducing its strength.

KnockbackDelay only starts delaying when a valid target is near your crosshair and the chance check passes. While active, it holds the incoming velocity packet and any packets that arrive behind it, then releases them after the selected delay.

---

### Chance[​](#chance "Direct link to Chance")

The percentage chance that incoming knockback will be delayed.

### Air Delay[​](#air-delay "Direct link to Air Delay")

The packet delay used when you have not been on the ground long enough before receiving knockback.

### Ground Delay[​](#ground-delay "Direct link to Ground Delay")

The packet delay used when you have been on the ground for at least a few ticks before receiving knockback.

### Water Check[​](#water-check "Direct link to Water Check")

Disables the module while in water or other liquids to avoid suspicion.

---

## BedPlates

**Source:** [https://docs.vape.gg/features/modules/minigames/BedPlates](https://docs.vape.gg/features/modules/minigames/BedPlates)

# BedPlates

Renders a display that shows you the unique block types surrounding a bed. Specifically useful for the Bedwars gamemode, allowing you to quickly determine which beds have the weakest defenses for you to target first.

---

### Show Distance[​](#show-distance "Direct link to Show Distance")

When enabled, the popup rendered will have also display your distance to the bed.

---

## BedBreaker

**Source:** [https://docs.vape.gg/features/modules/minigames/BedBreaker](https://docs.vape.gg/features/modules/minigames/BedBreaker)

# BedBreaker

[Warning](/guides/general/anti-cheats)

Allows you to break beds through walls. Specifically useful for the Bedwars gamemode.

---

## MurdererFinder

**Source:** [https://docs.vape.gg/features/modules/minigames/MurdererFinder](https://docs.vape.gg/features/modules/minigames/MurdererFinder)

# MurdererFinder

Designed for Murder Mystery gamemode, this module helps identify the murderer by tracking player held items.

---

### Callout[​](#callout "Direct link to Callout")

Automatically calls out the murderer in chat after a delay.

- **Delay**: The time (in milliseconds) between callouts.
- **Messages**: A list of customizable callout messages. Use '%s' to substitute the murderer's username.

### Murderer Items[​](#murderer-items "Direct link to Murderer Items")

A list of items that might indicate a player is the murderer. See [**Limit to Item Settings**](/values/LimitItems) for more information.

---

## PropHunt

**Source:** [https://docs.vape.gg/features/modules/minigames/PropHunt](https://docs.vape.gg/features/modules/minigames/PropHunt)

# PropHunt

Built for the PropHunt gamemode. Watches blocks around you and highlights blocks that have recently updated (indicative of them being a "fake block" that is actually a player).

---
