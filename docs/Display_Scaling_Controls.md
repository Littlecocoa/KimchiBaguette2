# Display scaling and fullscreen

Related tickets: KAN-32, KAN-33, KAN-35. Rendering half: KAN-34.

The game draws in a fixed space of 448x520 logical pixels. Whatever size the
window is, the image is drawn at the largest whole-number scale that fits it
and centred, with the leftover space left black — unless a whole number would
leave most of the screen black, in which case the screen is filled instead.
Nothing in the game knows about any of this: screens still draw in 448x520 and
read mouse positions in 448x520.

## How to make it bigger

| What | What it does |
| --- | --- |
| **The window's own controls** | The green button on macOS, maximize on Windows, dragging a corner |
| **F11** | Fills the screen, or goes back to a window |
| **F9** | Steps the window through 1x, 2x, 3x and back to 1x, leaving fullscreen if it was on |

The window is resizable, so the platform's own fullscreen is the first answer
and needs nothing from us. The window cannot be dragged smaller than the game,
which would crop it rather than scale it down.

The keys work on every screen, including during a level, because they are on
the window rather than in any one screen's update. Two things to know about
them:

- **On macOS the function keys belong to the system.** F11 is Show Desktop or
  Mission Control depending on the version, and it never reaches the game. Use
  `fn`+F11, or turn on "Use F1, F2, etc. keys as standard function keys" in
  System Settings - Keyboard. The green button has none of this trouble, which
  is why it is first in the table.
- **Not F10**, which was the first choice for the scale key: Windows reserves
  it for the window's own menu and takes it before Swing sees it, so pressing
  it opened the system menu and no further key reached the game.

Each applied change is logged (`Display: 2x window, content 896x1040`), so
whether a key press arrived can be answered from the log rather than by
watching the window.

The scale and the fullscreen flag are stored with the other settings
(`java.util.prefs`), so they survive a restart.

## Why the scale key sometimes appears to do nothing

A window is only grown to a scale the desktop has room for. The window at 2x is
896x1072 including the title bar, which does not fit on a 1512x982 laptop
screen, so on that screen F9 keeps showing 1x whatever the setting says. The
green button is the way to fill that screen. The
setting is still remembered: plug in a larger screen and it will be used.

Fullscreen has the same rule. The image is drawn at the largest whole-number
scale that fits the screen, so a screen shorter than 1040 points shows the game
at 1x with a wide black border rather than stretched. A non-integer scale would
blur the pixel art, which looks worse than the border.

## Whole numbers, and when they are given up

Scaling pixel art by 1.5 turns one source pixel into one screen pixel here and
two there, and the sprites come out uneven. Integer scale plus a letterbox is
what pixel games do instead. `DrawManager.integerScale` picks the scale and
`DrawManager.letterbox` centres the result.

The exception is a screen with no room for the next whole scale. A 520 tall
game on a 982 tall screen can only double to 1040, so it stays at 1 and
fullscreen looks exactly like the window — 58 points short of twice, and half
the height wasted. There, the screen is filled: the back buffer is enlarged by
`ceil(fit)` with nearest neighbour and then brought down to the size that fits
with bilinear, which keeps the grid far closer to even than going straight
there would. 448x520 becomes 896x1040 and then 846x982.

Filling only happens when the whole-number scale covers less than 90% of what
would fit, so a screen with room to spare stays pixel-exact:

| Desktop | Fits | Whole | Covers | Result |
| --- | --- | --- | --- | --- |
| 1512x982 | 1.88 | 1 | 53% | filled to 846x982 |
| 1366x768 | 1.47 | 1 | 68% | filled to 661x768 |
| 2560x1440 | 2.77 | 2 | 72% | filled to 1241x1440 |
| 1920x1080 | 2.07 | 2 | 96% | 2x, letterboxed |
| 3840x2160 | 4.15 | 4 | 96% | 4x, letterboxed |

The decision is made on the geometry alone, never on the fullscreen setting,
because the green button and maximize do not go through that setting — and
those are the routes that made this worth fixing. `DrawManager.fillsBetter`
and `DrawManager.fittingScale` are the whole of it.

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
logical = (position in the content area - letterbox) * game size / image size
```

By the image size rather than by a scale, because a filled image is not a whole
multiple of the game and dividing by a whole number would drift across it.

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

Four things only a person can check, in the game itself:

1. Press F9 and F11, and confirm the keyboard still works afterwards. The
   window has to keep the focus across a resize.
2. Hover the menus at each scale and watch the highlight follow the pointer.
3. In fullscreen, hover a menu entry and then the black border, and check the
   highlight follows the first and not the second.
4. Use the window's own fullscreen (the green button, maximize) and drag the
   window to an odd size, and check the image stays centred and the menus
   still answer the pointer.
5. On a screen that gets filled rather than scaled by a whole number, look at
   the sprites and the text. Filling trades some of the even pixel grid for
   the screen, and whether that trade reads as sharp or as soft is the one
   thing no test can answer.
