# KimchiBaguette — Pause Feature PRD

> Created: 2026-11-23 · Owner: **French members** (Julie, Félicie, Lena, Édouard)
> Related: [`KimchiBaguette_Retro_Polish_PRD.md`](KimchiBaguette_Retro_Polish_PRD.md) (Korean members' work)
> Workflow: [`KimchiBaguette_Git_Workflow.md`](KimchiBaguette_Git_Workflow.md)

## 1. Why this matters

The game currently has **no way to stop playing**. Once a run starts you are
locked in until you die or clear the level.

This is not a small convenience gap — it is the single most visible leftover from
1978 arcade machines. A coin-op cabinet had no reason to pause, because you paid
per play and walked away when you were done. Every game released today treats
`ESC` → pause as an absolute standard. Its absence reads as "unfinished", not as
"retro".

Worse, the current `ESC` behaviour in Infinite mode is actively hostile: it
**throws your run away**.

## 2. Scope

### In scope

- `ESC` toggles pause during gameplay (`GameScreen` and `InfiniteGameScreen`)
- Game logic, timers, buffs, enemy fire and BGM all genuinely freeze while paused
- A pause menu: **Resume / Settings / Abandon run** (abandon requires confirmation)
- Migrating the existing "ESC abandons the run" behaviour in Infinite mode behind
  that confirmation

### Out of scope

- Saving a paused run to disk and resuming it after quitting the game
- Pausing the menu screens (there is nothing to pause there)
- The three features owned by the Korean members (boss dialogue, screen shake,
  fullscreen) — see the Retro Polish PRD

## 3. Good news: a lot of this is already built

Before writing any code, know that **three pieces already exist and nobody is
using them.**

| Already in the codebase | Location | State |
| --- | --- | --- |
| Pause overlay drawing (pause icon + "Paused" label) | `DrawManager.drawPaused()` — `src/engine/DrawManager.java:531` | Fully written, **called from nowhere** |
| Pause icon artwork | `Icon.PAUSE` — `src/vfx/Icon.java:151` | Ready to use |
| BGM pause / resume | `AudioManager.pauseBGM()` / `resumeBGM()` — `src/audio/AudioManager.java:122` / `:133` | Fully working |
| Per-bus playback freeze | `AudioBus.setPaused(boolean)` — `src/audio/AudioBus.java:128` | Preserves playback positions |

So the drawing and the audio side are **done**. Do not rewrite them.

## 4. What is actually missing

1. **`GameScreen` has no `VK_ESCAPE` handler at all.** Nothing to extend — the
   key is simply not read during normal gameplay.
2. **`InfiniteGameScreen.java:156` binds `ESC` to abandoning the run:**
   ```java
   if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
       this.abandoned = true;
       if (recordsService != null && runSession != null)
           recordsService.abandonRun(runSession.getRunId());
       ...
   }
   ```
   This must become "open the pause menu", with abandoning moved behind a
   confirmation inside that menu.
3. **No pause state in the loop.** `Screen.run()`
   (`src/screen/Screen.java`) calls `update()` unconditionally every frame.

## 5. The hard part — read this before you start

> **Skipping `update()` is not enough. If you only do that, the game will be
> broken in a way that looks like a different bug entirely.**

### The problem

`Cooldown` measures time against the **wall clock**:

```java
// src/engine/Cooldown.java
public final boolean checkFinished() {
    if ((this.time == 0)
            || this.time + this.duration < System.currentTimeMillis())
        return true;
    return false;
}

public final void reset() {
    this.time = System.currentTimeMillis();
    ...
}
```

The wall clock keeps running while the game is paused. So if you pause for 30
seconds and only skip `update()`:

- The shield and Hyperfire buffs **expire during the pause**, so you come back
  with no buffs you had before.
- **Every** enemy firing cooldown finishes, so the instant you resume, a wall of
  bullets fires at once.
- Boss attack windups, item spawn timers and the bonus-enemy timer all complete
  silently.

`Cooldown` is used by essentially every system in the game, so this affects
everything at once.

### This debt is already documented in the code

`src/entity/PlayerStatus.java` already provides an overridable clock, and its
javadoc names this exact feature:

```java
/**
 * Reads the clock the shield is measured against. Tests override this to
 * reach the expiry boundary without waiting. A pause feature would also
 * hook in here, since the wall clock keeps running while the game is
 * paused.
 */
long currentTimeMillis() {
```

So a seam was deliberately left for you. Use it.

### Recommended solution: one pausable game clock

Do **not** add a pause offset to each `Cooldown` individually. Introduce a single
clock that every gameplay timer reads, and freeze that clock:

```java
// new: src/engine/GameClock.java
public final class GameClock {
    private static long pausedAt = 0;     // wall time when pause began, 0 = running
    private static long totalPaused = 0;  // accumulated paused milliseconds

    /** Gameplay time in milliseconds. Does not advance while paused. */
    public static long now() {
        if (pausedAt != 0)
            return pausedAt - totalPaused;
        return System.currentTimeMillis() - totalPaused;
    }

    public static void pause()  { if (pausedAt == 0) pausedAt = System.currentTimeMillis(); }
    public static void resume() {
        if (pausedAt != 0) {
            totalPaused += System.currentTimeMillis() - pausedAt;
            pausedAt = 0;
        }
    }
    public static void reset()  { pausedAt = 0; totalPaused = 0; }
}
```

Then:

- In `Cooldown`, replace both `System.currentTimeMillis()` calls with `GameClock.now()`.
- Override `PlayerStatus.currentTimeMillis()` to return `GameClock.now()`
  (the hook is already there — do not change its signature, the tests rely on it).
- Call `GameClock.reset()` when a new run starts, so paused time does not leak
  between runs.

Because `reset()` and `checkFinished()` then read the **same** clock, pausing
needs no per-timer bookkeeping at all. This is why it is worth doing as its own
pull request before anything else.

> ⚠️ Do **not** route `RunSession` / `RecordsService` play-time tracking through
> `GameClock` without checking with the Records owners first — those deliberately
> measure real elapsed time (`System.nanoTime()`), and "active duration" may be
> meant to exclude paused time or not. Ask before changing it.

## 6. Requirements

### R1 — Pause toggle

- `ESC` during gameplay enters pause; `ESC` again resumes
- Works in both `GameScreen` and `InfiniteGameScreen`
- Must be edge-triggered: holding `ESC` toggles **once**, not every frame
  (see how `Screen.handleMuteKey()` already solves this with a `wasDown` flag)
- Ignored while `inputDelay` is still running, and ignored once the game is over

### R2 — Everything freezes

While paused:

- No ship movement, no enemy movement, no firing, no collisions, no particles
- `GameClock` is frozen, so no cooldown, buff or debuff makes progress
- BGM pauses via `AudioManager.pauseBGM()` and resumes on unpause
- The frame keeps being drawn (the overlay must be visible), so only the
  **update** half of the frame is skipped, not the **draw** half

### R3 — Pause overlay and menu

- Draw the existing `DrawManager.drawPaused()` overlay — do not write a new one
- Add a menu with: **Resume** / **Settings** / **Abandon run**
- Follow the existing menu conventions used by `SettingsScreen`:
  keyboard up/down + `SPACE`, mouse hover + click, and the
  `menu_move.wav` / `menu_select.wav` / `menu_back.wav` sounds
- **Abandon run** asks for confirmation, and only then does what
  `InfiniteGameScreen.java:156` currently does on a bare `ESC`
  (`abandoned = true`, `recordsService.abandonRun(...)`)

### R4 — Correct resume

- On resume, call `ScreenShake.reset()` (see §7) so no shake queued before the
  pause fires afterwards
- The pause overlay draws **on top of** the boss speech bubble
- Resuming must not grant a free frame of input (e.g. the ship should not jump)

## 7. Coordination with the Korean members

| Topic | What you need to know |
| --- | --- |
| `ScreenShake` | Arrives in the **M0** pull request on `main` (owner: Yongwon). It provides `ScreenShake.reset()`. **Wait for M0 to merge before implementing R4.** |
| Boss speech bubble | Drawn by `DrawManager.drawBossSpeech(...)` (owner: Yoon-ah). Draw the pause overlay after it so the overlay is on top. |
| Boss dialogue timers | Owner Junah uses `Core.getCooldown(...)`, so the dialogue timer freezes automatically once §5 is done. No coordination needed. |
| `DrawManager` | Yongwon is changing `initDrawing()` / `completeDrawing()` for fullscreen, and Yoon-ah is adding `drawBossSpeech()`. **Your changes should stay inside `drawPaused()` and its new menu helper** to avoid a three-way conflict in a 2104-line file. |
| `GameScreen` | Also edited by Yoon-ah (1 line) and Jiye (6 spots). This is the single most conflict-prone file in the repo (Workflow §5). Keep your branch short and rebase often. |

## 8. Suggested task split

Three separable tasks; **P1 must merge before P2 and P3.** Julie decides the
final assignment — this is a suggestion based on how the code splits.

| Task | Content | Files | Branch |
| --- | --- | --- | --- |
| **P1** | Pausable game clock (§5). The hard, central piece. | new `engine/GameClock`, `engine/Cooldown`, `entity/PlayerStatus` | `feature/pausable-game-clock` |
| **P2** | Pause state, `ESC` toggle, overlay wiring, BGM pause | `screen/Screen`, `screen/GameScreen`, `screen/InfiniteGameScreen` | `feature/pause-state` |
| **P3** | Pause menu + abandon-with-confirmation migration | `engine/DrawManager` (inside `drawPaused`), `screen/InfiniteGameScreen` | `feature/pause-menu` |

```
P1 (game clock) ──merge──> P2 (pause state) ──merge──> P3 (pause menu)
```

P1 is the one that needs the most care and the most review attention. P2 and P3
are mostly wiring once P1 is in.

## 9. Acceptance criteria

- [ ] `ESC` pauses and unpauses in both `GameScreen` and `InfiniteGameScreen`
- [ ] Holding `ESC` down toggles exactly once
- [ ] **Pause for 30 seconds with an active shield, then resume — the shield has
      the same time left as before the pause**
- [ ] **Pause for 30 seconds mid-level, then resume — no burst of enemy fire**
- [ ] Boss attack windups do not complete during a pause
- [ ] BGM stops on pause and continues from the same position on resume
- [ ] The overlay is visible and readable, and sits on top of the boss speech bubble
- [ ] Pause menu: Resume returns to play, Settings opens settings, Abandon asks
      for confirmation first
- [ ] Abandoning from the menu records the run exactly as the old bare-`ESC`
      behaviour did
- [ ] Pausing while the game is over does nothing
- [ ] Difficulty, records and shop behaviour are all unchanged (no regression)

## 10. Manual test plan

There is no CI that runs the game, so manual verification is the only real gate
(Workflow §4). Run `engine.Core` and check:

1. Campaign mode → pause → resume → finish the level normally
2. Infinite mode → pause → resume → the run is **not** marked abandoned
3. Pick up a shield → pause 30s → resume → shield time is preserved
4. Pause during a boss fight → resume → no instant special attack
5. Pause → Abandon → confirm → the score screen and the saved record are correct
6. Pause → Abandon → cancel → play continues
7. Pause → Settings → back → still paused (not resumed by accident)
8. Mute key (`M`) and pause do not interfere with each other

Add unit tests for `GameClock` under `test/engine/GameClockTest.java`.

> ⚠️ If you write a standalone `main()` test, you **must** register it in
> `smokeTestClasses` in `build.gradle`, or the `verifyTestRegistration` task will
> **fail the build**. JUnit tests under `test/` are discovered automatically.

Suggested `GameClock` tests:

- `now()` advances while running
- `now()` does not advance between `pause()` and `resume()`
- after `pause()` → wait → `resume()`, a `Cooldown` started before the pause has
  the same remaining time
- `reset()` clears accumulated paused time
- `pause()` twice in a row is harmless; `resume()` without `pause()` is harmless

## 11. Pull request rules (Workflow §3 · §4)

Commits: `[Type] Short description` — `[Feat]` `[Fix]` `[Refactor]` `[Docs]`,
`[Test]`, `[Style]`. One change per commit.

PR description uses exactly these two sections:

```text
### Changes
- ...

### Test
- ...
```

Before opening a PR:

- [ ] The feature works when you actually run the game
- [ ] No regression in existing features
- [ ] Debug code and leftover comments removed
- [ ] Commit messages follow the team format
- [ ] New standalone `main()` tests registered in `smokeTestClasses`
- [ ] PR targets `main` of **`origin`** (`YitWub/KimchiBaguette`) —
      **never** the shared course repository (`fork`)
- [ ] Approved by at least one other team member, then **Squash and Merge**,
      then delete the branch
