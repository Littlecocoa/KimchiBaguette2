# Display scaling and fullscreen

Related tickets: KAN-32, KAN-33, KAN-35. Rendering half: KAN-34.

The game draws in a fixed space of 448x520 logical pixels. The window is that
size times a whole number, and fullscreen is the same image centred on the
screen with the leftover space left black. Nothing in the game knows about any
of this: screens still draw in 448x520 and read mouse positions in 448x520.

## Keys

| Key | What it does |
| --- | --- |
| **F11** | Fills the screen, or goes back to a window |
| **F9** | Steps the window through 1x, 2x, 3x and back to 1x, leaving fullscreen if it was on |

Both work on every screen, including during a level, because they are on the
window rather than in any one screen's update.

Not F10, which was the first choice: Windows reserves it for the window's own
menu and takes it before Swing sees it, so pressing it opened the system menu
and no further key reached the game.

The scale and the fullscreen flag are stored with the other settings
(`java.util.prefs`), so they survive a restart.

## Why the keys sometimes appear to do nothing

A window is only grown to a scale the desktop has room for. The window at 2x is
896x1072 including the title bar, which does not fit on a 1512x982 laptop
screen, so on that screen F9 keeps showing 1x whatever the setting says. The
setting is still remembered: plug in a larger screen and it will be used.

Fullscreen has the same rule. The image is drawn at the largest whole-number
scale that fits the screen, so a screen shorter than 1040 points shows the game
at 1x with a wide black border rather than stretched. A non-integer scale would
blur the pixel art, which looks worse than the border.

## Why whole numbers only

Scaling pixel art by 1.5 turns one source pixel into one screen pixel here and
two there, and the sprites come out uneven. Integer scale plus a letterbox is
what pixel games do instead. `DrawManager.integerScale` picks the scale and
`DrawManager.letterbox` centres the result.

## Two things the window must not share

**Swing's per-window buffer.** `Frame` sets `swing.bufferPerWindow` to `false`
before any window exists. Swing's paint manager keeps its own buffer strategy
on the window, a window has room for one, and ours is the one frames are
presented through. The two together showed up on Windows as a black fullscreen,
and as a `NullPointerException` out of Swing's paint manager when a resize
pulled the buffers out from under it. We paint every frame ourselves, so Swing
has nothing to buffer.

**The thread.** A display change is asked for by a key, applied between frames
by `DrawManager`, and carried out on the event thread, which the drawing thread
waits for. Resizing a window is Swing's work and belongs on its thread; doing
it while a frame is being presented invalidates the buffers mid-draw. Waiting
rather than posting is what keeps both true at once.

## The part that breaks everything if it is wrong

`TitleScreen`, `SettingsScreen`, `GameplaySettingsScreen`, `ShopScreen` and
`ShipSelectionScreen` all pick their entries by mouse position. At 2x, the
pointer is twice as far from the corner as the thing it is pointing at, so the
position has to be divided back down before any of them sees it:

```
logical = (position in the content area - letterbox) / scale
```

`DrawManager` publishes where it put the image (`DisplayConfig.setViewport`),
and `InputManager.getMouseX` and `getMouseY` undo it on the way out, so the
screens never deal with it. A pointer on the letterbox is on no logical pixel
and reads as `-1`, the same as a pointer that has left the window.

The shake offset is deliberately left out of that: it moves the image only, and
including it would make every menu's hover jitter with the screen.

## Checking it

`engine.DisplayConfigTest` runs with `check` and covers the settings and the
pointer arithmetic. The window itself cannot be checked headless, so there is
a second one that needs a display:

```sh
./gradlew displayScalingDemo
```

It opens a window a quarter of the game's size — on a laptop screen the game
at 2x is taller than the desktop, so a full-size window would only ever be
shown at 1x and the scaling would go unexercised — and walks it through 1x,
2x, 3x, fullscreen and back, checking at each stop that the content area is
exactly the logical size times the scale, that a pointer aimed at a menu entry
reads back as that entry, and that a pointer on the letterbox reads as off the
image. Keep the mouse off the window while it runs: a real pointer over the
window reports its own position over the top of the one being tested. For the
same reason it does not check the pointer in fullscreen, where the window
covers the screen and the real pointer is always on it.

Three things only a person can check, in the game itself:

1. Press F9 and F11, and confirm the keyboard still works afterwards. The
   window has to keep the focus across a resize.
2. Hover the menus at each scale and watch the highlight follow the pointer.
3. In fullscreen, hover a menu entry and then the black border, and check the
   highlight follows the first and not the second.
