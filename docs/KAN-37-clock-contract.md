# KAN-37 — Clock contract (Pause)

Rule: gameplay timers read GameClock (frozen during pause). Real-time timers keep System time.

| File | What it times | Clock |
|------|---------------|-------|
| Cooldown | gameplay fire / ability delays | GameClock |
| PlayerStatus, PlayerStatusEvent | slow and shield duration | GameClock |
| Ship | fire rate, damage blink | GameClock |
| Ship, ChameleonHull | chameleon effect | GameClock (⚠️ uses nanoTime, convert to ms) |
| BuffManager | buff duration | GameClock |
| OrbitalAnnihilator | item effect | GameClock |
| GameScreen, InfiniteGameScreen | game time, SPACE hold for annihilator | GameClock |
| GameScreen, InfiniteGameScreen | runSession calls (nanoTime) | System time (records) |
| DrawManager (drawFallingLaser, drawAnnihilatorReady) | gameplay visuals | GameClock |
| DrawManager (drawMenuIcon, drawTitle) | menu animations | System time |
| Particle, FloatingScoreHud | gameplay visual effects | GameClock (screen frozen in pause) |
| GameClock | the clock itself | System time |
| RunSession, RecordsService, Core | records and dates | System time (owned by records team) |
| Screen | frame rate loop, menu input delay | System time |
| PlayerRecordsScreen | menu input delay | System time |

Note: Cooldown is also used in menus (e.g. SettingsScreen). Menu cooldowns are created with `Core.getMenuCooldown()` (KAN-38) so they keep System time and menus still work during pause.
