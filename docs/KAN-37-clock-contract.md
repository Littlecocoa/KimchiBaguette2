# KAN-37 — Clock contract (Pause)

Rule: gameplay timers read GameClock (frozen during pause). Real-time timers keep System time.

| File | What it times | Clock |
|------|---------------|-------|
| Cooldown | fire / ability delays in game | GameClock |
| PlayerStatus, PlayerStatusEvent | slow and shield duration | GameClock |
| Ship | fire rate, chameleon, damage blink | GameClock |
| BuffManager | buff duration | GameClock |
| ChameleonHull, OrbitalAnnihilator | item effects | GameClock |
| GameScreen, InfiniteGameScreen | game time, space hold | GameClock |
| DrawManager, Particle, FloatingScoreHud | visual blink / effects | GameClock (screen frozen in pause) |
| GameClock | the clock itself | System time |
| RunSession, RecordsService, Core | records and dates | System time (owned by records team) |
| Screen | frame rate loop | System time |
| PlayerRecordsScreen | menu input delay | System time |

Note: Cooldown is also used in menus (e.g. SettingsScreen). Menu cooldowns must keep System time, otherwise menus freeze during pause.