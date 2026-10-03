# FreeLook vs FreeCam in Minecraft

## Overview

**FreeLook** and **FreeCam** are two different camera systems in Minecraft.

The easiest way to remember the difference is:

> **FreeLook = move your camera independently, but your player stays where they are.**  
> **FreeCam = detach the camera from your player and move the camera through the world.**

They may look similar to a player, but technically they work in different ways.

---

# 1. FreeLook

## What is FreeLook?

**FreeLook** allows you to rotate the camera independently from the direction your Minecraft player is facing.

Normally, Minecraft tightly connects the player's rotation and the camera rotation:

```text
Mouse movement
      ↓
Camera rotation
      ↓
Player rotation
```

For example, suppose your player is looking north:

```text
        N
        ↑
      PLAYER
        ↑
      CAMERA
```

You move your mouse to the right.

Normally:

```text
Camera → rotates right
Player → rotates right
```

With FreeLook:

```text
Camera → rotates right
Player → stays facing north
```

So the camera and player temporarily have different yaw/pitch values.

---

# 2. Normal Minecraft Camera

It helps to understand normal behavior first.

Minecraft uses two important rotation concepts:

## Yaw

Yaw is the **horizontal direction**.

Conceptually:

```text
Yaw = LEFT ↔ RIGHT
```

Examples:

```text
0°     = one horizontal direction
90°    = right relative to that reference
180°   = opposite direction
270°   = left relative to that reference
```

The exact mathematical convention depends on Minecraft's coordinate system and implementation, but the important idea is that yaw controls horizontal rotation.

## Pitch

Pitch is the **vertical direction**.

Conceptually:

```text
Looking up
    ↑
    |
 Player
    |
    ↓
Looking down
```

So, in normal gameplay:

```text
player yaw    = camera yaw
player pitch  = camera pitch
```

FreeLook breaks that relationship.

---

# 3. How FreeLook Works Conceptually

Suppose the player has:

```text
Player yaw   = 90°
Player pitch = 0°
```

The user activates FreeLook and moves the mouse.

The camera might become:

```text
Camera yaw   = 160°
Camera pitch = -20°
```

But the player's actual rotation remains:

```text
Player yaw   = 90°
Player pitch = 0°
```

So internally you can think about the state as:

```text
PlayerRotation
    yaw = 90
    pitch = 0

CameraRotation
    yaw = 160
    pitch = -20
```

The important relationship is:

```text
CameraRotation != PlayerRotation
```

while FreeLook is active.

---

# 4. What Does FreeLook Actually Change?

FreeLook normally changes **where the client renders the camera**, not the player's physical world position.

Conceptually:

```text
                  SERVER
                    │
             Player rotation
                    │
                    ▼
                [PLAYER]
                    │
                    │
              actual yaw/pitch


                  CLIENT
                    │
              FreeLook active
                    │
                    ▼
               [CAMERA]
            independent rotation
```

The player's position stays where it was.

The camera is simply allowed to have a different orientation.

This is why FreeLook can be useful for:

- looking around while moving
- checking behind yourself
- looking sideways
- inspecting surroundings
- third-person camera control
- cinematic camera movement

---

# 5. FreeLook Does NOT Mean the Player Is Moving Separately

This is one of the biggest misunderstandings.

Imagine:

```text
         CAMERA
            ↘

        [PLAYER]
           ↑
       facing north
```

The camera may be looking east while the player is still facing north.

So:

```text
Camera direction = East
Player direction = North
```

can exist at the same time.

The player has not necessarily turned east just because the camera is looking east.

---

# 6. FreeLook and Movement

This is where implementation becomes interesting.

Minecraft movement can depend on the player's orientation.

For example, pressing:

```text
W
```

normally means:

```text
Move forward relative to the player's yaw
```

Suppose the player is facing north:

```text
W → North
```

Now activate FreeLook and make the camera face east:

```text
Camera → East

Player → North
```

Depending on the implementation, pressing W can still mean:

```text
W → North
```

rather than:

```text
W → East
```

That is one of the defining characteristics of a proper FreeLook implementation: the visual camera direction can be different from the player's actual movement orientation.

---

# 7. FreeLook Can Be Implemented in Different Ways

There are several approaches.

## Method A — Separate Camera Rotation

A clean conceptual implementation is to maintain separate rotation state:

```java
float playerYaw;
float playerPitch;

float cameraYaw;
float cameraPitch;
```

Normally:

```java
cameraYaw = playerYaw;
cameraPitch = playerPitch;
```

When FreeLook is enabled:

```java
cameraYaw += mouseDeltaX * sensitivity;
cameraPitch += mouseDeltaY * sensitivity;
```

while the player's rotation remains independent.

The renderer uses:

```java
cameraYaw
cameraPitch
```

while gameplay uses:

```java
playerYaw
playerPitch
```

The exact code will depend on the Minecraft version, mappings, and rendering/input architecture.

---

# 8. What Happens When FreeLook Is Released?

A polished FreeLook module needs to define what happens when the key is released.

## Snap Back

The camera immediately returns to the player's rotation:

```text
FreeLook ON

Player → North
Camera → East


FreeLook OFF

Player → North
Camera → North
```

This is simple and predictable.

## Smooth Synchronization

The camera gradually returns to the player's rotation.

Conceptually:

```text
East
 ↓
Southeast
 ↓
South
 ↓
Southwest
 ↓
North
```

This can create a smoother visual transition.

---

# 9. Third-Person FreeLook

FreeLook becomes especially noticeable in third person.

Normally:

```text
        CAMERA
           |
           |
         PLAYER
           ↑
```

FreeLook can allow:

```text
             CAMERA
               ↘
                \
                 \
                PLAYER
                  ↑
```

The camera can look at the player from another orientation while the actual player remains oriented differently.

---

# 10. FreeCam

Now we get to a substantially different system.

## What Is FreeCam?

**FreeCam detaches the camera from the player's physical position.**

Instead of only changing camera rotation:

```text
Player position = same
Camera rotation = different
```

FreeCam changes the effective camera position too:

```text
Player position = A
Camera position = B
```

where:

```text
A != B
```

For example:

```text
PLAYER
  ●
  │
  │
  │
  ● CAMERA
```

The camera may be several blocks away from the actual player.

---

# 11. FreeCam Is Basically a Separate Virtual Camera

Conceptually, you can think of FreeCam as creating a **virtual camera**.

The real player might be:

```text
Player
position = (100, 64, 200)
```

The camera starts there:

```text
Camera
position = (100, 64, 200)
```

Then the user presses a movement key.

The camera becomes:

```text
Camera
position = (100, 64, 190)
```

while the real player stays:

```text
Player
position = (100, 64, 200)
```

So:

```text
Player position != Camera position
```

That is the fundamental difference.

---

# 12. FreeCam Movement

A typical FreeCam camera can have independent movement:

```text
W      → forward
S      → backward
A      → left
D      → right
Space  → up
Shift  → down
```

It may also have configurable values such as:

```text
speed
horizontal speed
vertical speed
acceleration
```

For example:

```text
Speed 1
Speed 2
Speed 5
Speed 10
```

or a configurable multiplier.

---

# 13. Why FreeCam Feels Different From Spectator Mode

Minecraft already has a built-in free-flying camera-like experience through **Spectator Mode**.

However, FreeCam can behave differently because it can keep the player in a normal gameplay state while the client renders the world from an independent camera.

Conceptually:

## Spectator

```text
Player/Game mode changes
        ↓
Spectator camera
        ↓
Player is effectively spectating
```

## FreeCam

```text
Normal player
     +
independent client camera
     ↓
Camera moves elsewhere
```

The exact details depend on the specific implementation and Minecraft version.

---

# 14. Most Important Distinction: Camera Position

Here is the simplest technical comparison.

## FreeLook

```text
Player position = Camera position
Player rotation != Camera rotation
```

## FreeCam

```text
Player position != Camera position
Player rotation != Camera rotation
```

This is the cleanest way to distinguish them.

---

# 15. Example With Coordinates

Imagine your player is at:

```text
X = 100
Y = 64
Z = 100
```

## FreeLook

You rotate your camera toward a mountain:

```text
Player:
100, 64, 100

Camera:
100, 64, 100

Player rotation:
North

Camera rotation:
East
```

The camera's **position** is still the same.

Only its orientation is different.

---

## FreeCam

You fly the camera toward the mountain:

```text
Player:
100, 64, 100

Camera:
140, 80, 160
```

Now:

```text
Player position != Camera position
```

This is what makes FreeCam fundamentally different.

---

# 16. What the Renderer Is Doing

Minecraft needs a camera to render the world.

Conceptually:

```text
World
  ↓
Camera position
  ↓
Camera rotation
  ↓
View / Frustum
  ↓
Visible objects
  ↓
Screen
```

Normally:

```text
Camera position = Player position
Camera rotation = Player rotation
```

With FreeLook:

```text
Camera position = Player position
Camera rotation = Custom rotation
```

With FreeCam:

```text
Camera position = Custom position
Camera rotation = Custom rotation
```

So:

```text
NORMAL

Player Position ──────► Camera Position
       │
       └──────────────► Camera Rotation


FREELOOK

Player Position ──────► Camera Position

Player Rotation

Camera Rotation ◄────── Independent


FREECAM

Player Position

Camera Position ◄────── Independent
Camera Rotation ◄────── Independent
```

---

# 17. What FreeCam Does NOT Automatically Mean

A very important technical point:

**Moving the client-side camera does not necessarily move the server-side player.**

For example:

```text
Real player:
X = 100
Y = 64
Z = 100

FreeCam camera:
X = 500
Y = 100
Z = 500
```

The server may still consider the real player to be at:

```text
X = 100
Y = 64
Z = 100
```

The client is simply rendering from a different viewpoint.

Therefore, FreeCam is fundamentally a **camera/rendering feature**, not just a player teleport feature.

---

# 18. Networking Considerations

FreeCam implementations need to deal with **chunks**.

Minecraft does not necessarily have the entire world loaded on the client.

Normally:

```text
Player
   ↓
Nearby chunks
   ↓
Loaded into memory
```

With FreeCam:

```text
Player
    ●

              Camera
                ●
                ↓
        far-away chunks
```

The camera can move outside the area that was originally loaded.

Possible results include:

```text
unloaded chunks
missing terrain
delayed chunk loading
rendering gaps
```

A good implementation needs to account for this.

---

# 19. Why Some FreeCams Appear to Stop

Depending on the implementation, FreeCam may be limited by things such as:

- loaded chunk data
- render distance
- server chunk sending
- client-side chunk storage
- camera distance limits
- unloaded regions

Conceptually:

```text
PLAYER
████████████████████
loaded world area
             ↓
           CAMERA
             ↓
           ??????
```

Once the camera leaves the available client world data, there may not be enough terrain information to render what lies ahead.

---

# 20. Collision Behavior

Normal player movement interacts with blocks.

Conceptually:

```text
Player
  ↓
Collision detection
  ↓
Cannot walk through block
```

A typical FreeCam makes the **camera** ignore normal player collision:

```text
Camera → can move through blocks
```

while:

```text
Player → remains at original location
```

So the camera and the player can occupy completely different spatial relationships with blocks.

---

# 21. FreeCam and Camera Velocity

A more advanced FreeCam does not simply teleport the camera from point to point.

It can maintain its own velocity:

```java
double velocityX;
double velocityY;
double velocityZ;
```

Then conceptually:

```java
position += velocity;
```

This allows smooth movement.

A simple flow can be:

```text
velocity = 0
       ↓
Press W
       ↓
velocity = forward * speed
       ↓
camera moves
```

An even more advanced system can add acceleration and friction.

Conceptually:

```text
velocity += acceleration;
velocity *= friction;
position += velocity;
```

This makes the camera feel more like a real flying camera.

---

# 22. Camera Speed

A polished FreeCam can expose settings such as:

```text
FreeCam Speed
Vertical Speed
Acceleration
Smoothness
```

For example:

```text
Normal:
1.0

Fast:
3.0

Very Fast:
6.0
```

A multiplier-based design is also useful:

```java
cameraSpeed = baseSpeed * multiplier;
```

---

# 23. FreeLook Mouse Handling

One of the trickiest parts of FreeLook is mouse input.

Normally:

```text
Mouse movement
       ↓
Minecraft mouse handler
       ↓
Player yaw/pitch
```

With FreeLook:

```text
Mouse movement
       ↓
FreeLook handler
       ↓
Camera yaw/pitch
```

while preventing that same mouse input from directly changing the player's rotation.

Conceptually:

```java
if (freeLookEnabled) {
    cameraYaw += deltaX * sensitivity;
    cameraPitch += deltaY * sensitivity;
} else {
    playerYaw += deltaX * sensitivity;
    playerPitch += deltaY * sensitivity;
}
```

The actual implementation depends strongly on the Minecraft version, mappings, and client architecture.

---

# 24. Pitch Limits

The camera should usually have vertical limits.

Without proper limits, camera pitch could eventually become nonsensical.

Conceptually:

```java
pitch = clamp(pitch, minPitch, maxPitch);
```

A common conceptual range is around:

```text
-90° ≤ pitch ≤ +90°
```

but the exact range and convention depend on how the camera is implemented.

---

# 25. Third-Person Complications

FreeLook and FreeCam can become more complicated in third person because Minecraft applies camera offsets.

Conceptually:

```text
PLAYER
  ●
   \
    \
     ● CAMERA
```

The camera is not necessarily exactly at the player's coordinates.

Other factors can matter too:

- third-person distance
- camera rotation
- camera collision
- entity rendering
- interpolation
- hand rendering
- view bobbing
- perspective transitions

A client should therefore keep these concepts separate:

```text
player position
camera position
camera rotation
render position
render rotation
```

---

# 26. Interpolation

Minecraft uses interpolation for smooth rendering.

Conceptually, there can be:

```text
previous position
current position
```

and the renderer displays an intermediate value.

For example:

```text
Previous = 100
Current  = 101
Render   = 100.5
```

A FreeCam should usually have its own camera state rather than accidentally treating the camera like the real player's interpolated position.

Otherwise the result can be:

```text
jitter
shaking
snapping
teleport-like movement
```

---

# 27. FreeCam Activation

A polished module normally has its own state:

```java
boolean enabled = false;
```

When enabled:

```text
enabled = true
cameraPosition = playerPosition
cameraYaw = playerYaw
cameraPitch = playerPitch
```

Then during updates/rendering:

```text
update camera
render world from camera
```

When disabled:

```text
cameraPosition = playerPosition
cameraYaw = playerYaw
cameraPitch = playerPitch
enabled = false
```

The restoration step is important because the camera should return cleanly to normal Minecraft behavior.

---

# 28. Conceptual Architecture for Anormal V1

For a Minecraft client such as **Anormal V1**, it is useful to think of the system like this:

```text
                    Camera System
                         │
             ┌───────────┴───────────┐
             │                       │
         FreeLook                 FreeCam
             │                       │
     custom rotation         custom position
             │                 + rotation
             │                       │
             └───────────┬───────────┘
                         │
                      Renderer
                         │
                        World
```

FreeLook primarily controls:

```text
Custom Rotation
```

FreeCam primarily controls:

```text
Custom Position
+
Custom Rotation
```

---

# 29. Common FreeLook Settings

A client can expose options such as:

```text
FreeLook
 ├─ Hold Mode
 ├─ Toggle Mode
 ├─ Sensitivity
 ├─ Horizontal Limit
 ├─ Vertical Limit
 ├─ Smooth Camera
 └─ Reset on Disable
```

## Hold Mode

Hold the assigned key:

```text
Hold key   → FreeLook ON
Release    → FreeLook OFF
```

## Toggle Mode

Press once:

```text
Press → ON
```

Press again:

```text
Press → OFF
```

## Sensitivity

Controls how quickly the camera rotates in response to mouse movement.

---

# 30. Common FreeCam Settings

A more advanced FreeCam can expose:

```text
FreeCam
 ├─ Speed
 ├─ Vertical Speed
 ├─ Acceleration
 ├─ No Clip Camera
 ├─ Smooth Movement
 ├─ Camera Distance
 ├─ Disable View Bobbing
 └─ Reset Position
```

---

# 31. FreeLook Example In-Game

Suppose you are moving forward.

Your player:

```text
       N
       ↑
       │
     PLAYER
       ↑
```

You activate FreeLook and rotate the camera 90°:

```text
           CAMERA →

           PLAYER
              ↑
              N
```

You are still controlling the same player.

The visual difference is:

```text
PLAYER direction ≠ CAMERA direction
```

That is why FreeLook is primarily a **view/orientation feature**.

---

# 32. FreeCam Example In-Game

Now imagine:

```text
PLAYER
  ●
  │
  │
  │
  │
  │
  │
  ● CAMERA
```

The camera can then travel somewhere else:

```text
             CAMERA
                ●
               /
              /
             /
            /
         PLAYER
           ●
```

The player is still at the original location.

That is why FreeCam is primarily a **camera-position feature**.

---

# 33. The Easiest Mental Model

Think of a **security camera**.

## FreeLook

The camera stays mounted in the same place:

```text
       CAMERA
          ●
          ↻
```

It can rotate.

## FreeCam

The camera itself can fly around:

```text
       ●
     CAMERA

        ↓

                  ●
                CAMERA
```

It can:

```text
MOVE
+
ROTATE
```

This is essentially the conceptual difference.

---

# 34. Camera vs Player

When designing a Minecraft client, avoid thinking:

> "The camera is the player."

A better mental model is:

```text
Player = gameplay object
Camera = viewpoint
```

Normally they happen to be synchronized:

```text
Player ───────── Camera
  same position
  same rotation
```

FreeLook breaks:

```text
rotation synchronization
```

FreeCam breaks:

```text
position synchronization
+
rotation synchronization
```

---

# 35. Final Comparison

```text
NORMAL

Player Position  = Camera Position
Player Rotation  = Camera Rotation


FREELOOK

Player Position  = Camera Position
Player Rotation  ≠ Camera Rotation


FREECAM

Player Position  ≠ Camera Position
Player Rotation  ≠ Camera Rotation
```

The core difference can be summarized in one sentence:

> **FreeLook lets you look independently of your player, while FreeCam lets your entire viewpoint independently move around the world.**

---

# 36. Quick Technical Summary

```text
                 POSITION           ROTATION

Normal           Same               Same

FreeLook         Same               Different

FreeCam          Different          Different
```

Or even more simply:

```text
FreeLook = Independent camera rotation

FreeCam  = Independent camera position + rotation
```

These two concepts are worth keeping separate in a client architecture because they control different parts of Minecraft's view system.
