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
| **F10** | Steps the window through 1x, 2x, 3x and back to 1x, leaving fullscreen if it was on |

Both work on every screen, including during a level, because they are on the
window rather than in any one screen's update.

The scale and the fullscreen flag are stored with the other settings
(`java.util.prefs`), so they survive a restart.

## Why the keys sometimes appear to do nothing

A window is only grown to a scale the desktop has room for. The window at 2x is
896x1072 including the title bar, which does not fit on a 1512x982 laptop
screen, so on that screen F10 keeps showing 1x whatever the setting says. The
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

## Checking it by hand

`engine.DisplayConfigTest` covers the settings and the pointer arithmetic, but
the window itself needs a person:

1. Run the game. Press F10 three times, F11 twice.
2. At each stop, hover the title menu entries and check the highlight follows
   the pointer, including near the edges of the image.
3. In fullscreen, hover the black border and check nothing highlights.
4. Check the keyboard still works after each change — the window has to keep
   the focus across a resize.
