

## 1. 범위

### 포함 (이 PRD)

- **F1. 보스 말하기** — 보스가 등장·페이즈 전환·특수공격·격파 시 대사를 말함
- **F2. 화면 흔들기** — 피격·폭발·보스 이벤트 시 화면이 흔들림 (+ 접근성 설정)
- **F3. 전체화면 / 해상도 스케일링** — 정수배 스케일 + 전체화면 토글 (+ 렌더 파이프라인 정리)

### 제외 (비범위)

- 일시정지 → **프랑스 팀**에 위임 (§8 핸드오프 참고)
- 현지화(i18n), 튜토리얼, 게임패드, 캠페인 진행 저장 → 이번 범위 아님
- 신규 스프라이트/사운드 제작 → 기존 자산 재사용

## 2. 작업 분배

| 담당 | 과제 | 브랜치 | 주 작업 파일 | 신규/수정 |
| --- | --- | --- | --- | --- |
| **나 (Yongwon)** | **M0** 계약 선행 + **F3** 전체화면 | `feature/retro-polish-contracts` → `feature/fullscreen-scaling` | `Frame`, `DrawManager`, `InputManager`, `DisplayConfig` | 신규 1 / 수정 3 |
| **노준아 (Junah)** | **F1-A** 보스 대사 데이터 · 트리거 | `feature/boss-dialogue-lines` | `boss/dialogue/*` | **신규 전용** |
| **지윤아 (Yoon-ah)** | **F1-B** 말풍선 렌더링 | `feature/boss-speech-bubble` | `DrawManager`, `GameScreen`(1줄) | 수정 2 |
| **오채민 (Chaemin)** | **F2-A** 흔들림 엔진 | `feature/screen-shake-engine` | `vfx/ScreenShake` | **신규 전용** |
| **권지예 (Jiye)** | **F2-B** 흔들림 트리거 · 접근성 설정 | `feature/screen-shake-triggers` | `GameScreen`, `InfiniteGameScreen`, `GameplayConfig`, `GameplaySettingsScreen` | 수정 4 |

**배정 근거**

- 노준아·오채민은 **신규 파일 전용** → 머지 충돌 위험 0. 가장 빠르게 끝낼 수 있음.
- F3은 세 기능의 공통 선행 조건이자 가장 리스크가 높아 조율자(나)가 가짐.
- 노준아는 `BulletType` enum 등 데이터·enum 설계 경험이 있어 대사 테이블에 적합.

## 3. M0 — 계약 선행 PR (가장 중요, 최우선)

> **목적: 4명이 서로를 기다리지 않게 만드는 것.** 시간이 없으므로 이게 1순위임.

M0은 **세 기능의 "구멍"만 뚫어놓는 초소형 PR**임. 작업량 10~20분.
M0이 `main`에 들어가면 나머지 4명이 **완전 병렬**로 작업 가능함.

M0에 포함되는 것 (전부 스텁, 동작 변화 없음):

```java
// 신규: src/vfx/ScreenShake.java  — 오채민이 내부를 채움
public final class ScreenShake {
    public static void add(double trauma) { }      // 흔들림 추가 (0.0~1.0)
    public static void update() { }                // 매 프레임 감쇠
    public static int getOffsetX() { return 0; }   // 이번 프레임 X 오프셋
    public static int getOffsetY() { return 0; }   // 이번 프레임 Y 오프셋
    public static void reset() { }                 // 즉시 정지 (일시정지·화면 전환용)
    public static void setIntensity(double v) { }  // 접근성 배율 0.0~1.0
}

// 신규: src/boss/dialogue/BossDialogueDirector.java — 노준아가 내부를 채움
public final class BossDialogueDirector implements BossEventListener {
    @Override public void onBossEvent(BossEvent event) { }
    public String getActiveLine() { return null; } // 지금 표시할 대사, 없으면 null
}

// 수정: DrawManager — 지윤아가 내부를 채움
public void drawBossSpeech(Screen screen, String text, int bossCenterX, int bossTopY) { }
```

그리고 `DrawManager.completeDrawing()`에 오프셋을 읽는 **1줄**을 미리 넣어둠:

```java
graphics.drawImage(backBuffer,
        frame.getInsets().left + ScreenShake.getOffsetX(),
        frame.getInsets().top  + ScreenShake.getOffsetY(), frame);
```

**수용 기준**: 빌드 통과 + 게임 실행 시 기존과 **완전히 동일하게** 동작 (스텁이라 변화 없음).

---

## 4. F1 — 보스 말하기

### 4.1 왜 되는가 (기존 인프라)

보스 시스템에 **이미 이벤트 버스가 있음**. 보스 코드를 한 줄도 안 건드리고 붙일 수 있음.

- `boss/BossEventManager` — 리스너 등록/발행
- `boss/BossEvent.Type` — `SPAWNED` / `DAMAGED` / `PHASE_CHANGED` / `ATTACK` / `DEFEATED`
- `BossEvent`가 보스 타입·좌표·HP·페이즈·공격명·windup 여부를 전부 들고 옴
- 등록 지점도 이미 있음: `GameScreen.java:518`, `:523`

### 4.2 F1-A 보스 대사 데이터 · 트리거 — 노준아

**신규 파일만 만듦** (`src/boss/dialogue/`), 기존 파일 수정은 `GameScreen` 등록 1줄뿐.

- `BossDialogue.java` — 대사 테이블. `BossType` × 트리거 → 대사 목록
- `BossDialogueDirector.java` — `BossEventListener` 구현. 이벤트 수신 → 큐 적재 → 표시 시간·쿨다운 관리 → `getActiveLine()`으로 노출

**트리거 매핑**

| BossEvent.Type | 대사 사용 | 비고 |
| --- | --- | --- |
| `SPAWNED` | ✅ 등장 대사 | |
| `PHASE_CHANGED` | ✅ 페이즈 2 / 페이즈 3 각각 | `getPhase()`로 분기 |
| `DEFEATED` | ✅ 격파 대사 | |
| `ATTACK` | ⚠️ `isWindup() == true` **이고** `isSpecial() == true` 인 경우만 | 예고 대사 |
| `DAMAGED` | ❌ **절대 사용 금지** | 피격마다 발생 → 초당 수십 회. 대사가 폭주함 |

**대사 분량**: `BOSS_A`, `BOSS_B` 각각 (등장 1 + 페이즈2 1 + 페이즈3 1 + 격파 1 + 특수공격 2) = **최소 12줄**.
게임 UI가 전부 영어이므로 **대사도 영어**로 작성. 한 줄 최대 **44자** (말풍선 2줄 × 22자).

**타이밍 규칙**

- 1줄 표시 시간: **2.5초**
- 대사 간 최소 간격: **2.0초** (연타 방지)
- 표시 중 새 이벤트: 큐에 넣되 **최대 1개만 보관**, 넘치면 버림
- 단, `DEFEATED`는 **즉시 현재 대사를 밀어내고** 표시 (가장 중요한 순간)
- `Cooldown`은 `Core.getCooldown(...)`으로 생성 (팀 공통 패턴)

**수용 기준**

- 보스 등장 → 2.5초간 등장 대사 표시
- HP가 페이즈 2/3 경계를 넘을 때 해당 대사 1회만 표시
- 특수공격 예고 시 대사 표시, 일반 공격에는 대사 없음
- 피격을 아무리 많이 해도 대사가 폭주하지 않음
- 보스 격파 시 격파 대사가 반드시 표시됨
- `BOSS_A`와 `BOSS_B`의 대사가 서로 다름

**단위 테스트** (`test/boss/BossDialogueTest.java`, 표준 main 방식 → `build.gradle`의 `smokeTestClasses`에 등록 필수)

- `DAMAGED` 100회 발행 → `getActiveLine()`이 바뀌지 않음
- 2.5초 경과 후 `getActiveLine()`이 `null`로 돌아옴
- 모든 `BossType` × 모든 트리거에 대사가 비어있지 않음

### 4.3 F1-B 말풍선 렌더링 — 지윤아

- `DrawManager.drawBossSpeech(screen, text, bossCenterX, bossTopY)` 구현
  - 말풍선 박스 + 꼬리, 보스 **위쪽**에 배치
  - 폰트는 `fontRegular`, 색은 `GameColors` 사용 (하드코딩 금지)
  - 긴 대사 자동 줄바꿈, **최대 2줄**
  - **화면 밖으로 나가지 않게 클램프** — 보스가 좌·우 끝에 있을 때 반드시 확인
  - 보스가 화면 상단에 붙어 있으면 말풍선을 보스 **아래쪽**으로 뒤집기
- `GameScreen.draw()`에 호출 **1줄** 추가:
  ```java
  String line = dialogueDirector.getActiveLine();
  if (line != null)
      drawManager.drawBossSpeech(this, line, boss.getPositionX() + boss.getWidth() / 2,
              boss.getPositionY());
  ```

**F1-A를 기다리지 않는 방법**: 디렉터가 아직 `null`만 반환하므로, 개발 중에는
`"I am the beginning and the end."` 같은 테스트 문자열을 하드코딩해서 렌더링을 완성하고,
**PR 올리기 전에 하드코딩을 제거**함 (워크플로 §4: 디버그 코드 제거).

**수용 기준**

- 보스가 화면 **왼쪽 끝 / 오른쪽 끝 / 상단**에 있을 때 말풍선이 잘리지 않음
- 44자 대사가 2줄로 정상 줄바꿈됨
- `Friends` 팀의 보스 HUD(`BossHudInfo`)와 겹치지 않음
- 대사가 없을 때(`null`) 아무것도 그리지 않음

---

## 5. F2 — 화면 흔들기

### 5.1 F2-A 흔들림 엔진 — 오채민

**신규 파일 1개만 작성** (`src/vfx/ScreenShake.java`). 다른 파일 수정 없음 → 충돌 0.

**trauma 모델** (업계 표준 기법)

```
add(amount):     trauma = clamp(trauma + amount, 0, 1)
update():        trauma = trauma * 0.90          // 매 프레임 감쇠
                 directionX = random(-1, +1)     // 이 프레임의 방향, 축마다 따로
                 directionY = random(-1, +1)
getOffsetX():    round(MAX_OFFSET * trauma * trauma * directionX * intensity)
```

- **`trauma`를 제곱하는 것이 핵심**. 제곱하지 않으면 작은 흔들림이 질질 끌려서 "흔들리는 게 아니라 떨리는" 느낌이 됨.
- **반드시 `round`로 반올림할 것. `(int)` 절삭은 안 됨.** `|direction| < 1`이므로
  절삭하면 `MAX_OFFSET × trauma² < 1`인 구간, 즉 **`trauma ≤ 0.40`이 통째로 0**이 됨.
  `MAX_OFFSET = 6`인데 실제 최대도 5px에 그치고 범위가 `[-6, 5]`로 비대칭이 됨 (KAN-60).
- **방향은 축마다 따로 뽑되 `update()`에서 한 번만 뽑을 것.** X·Y가 같은 난수를 쓰면
  항상 45° 대각선으로만 흔들림. 읽을 때마다 새로 뽑으면 한 프레임 안에서 값이 달라져,
  오프셋을 두 번 읽는 호출자가 어긋남.
- `MAX_OFFSET = 6` 픽셀 권장. 448×520 화면에서 6px ≈ 1.3%. 그 이상은 멀미를 유발함.
- `trauma < 0.01`이면 `0`으로 스냅 + 오프셋 `0` 반환 (잔떨림 제거)
- `reset()`은 `trauma = 0` 즉시 적용
- `setIntensity(0.0)`이면 **항상 오프셋 0** (접근성 Off)
- 상태는 `static`. 호출 지점이 두 화면(`GameScreen`, `InfiniteGameScreen`)에 흩어져 있고 화면당 1개면 충분하므로 싱글턴 비용이 아까움.

**수용 기준 / 단위 테스트** (`test/vfx/ScreenShakeTest.java`)

> JUnit으로 작성하면 `test/` 아래에서 자동 발견되므로 **`smokeTestClasses`에 넣지 말 것.**
> 그 목록은 `main()` 방식 standalone 테스트 전용임.

- `trauma = 0` → `getOffsetX() == 0 && getOffsetY() == 0`
- `add(1.0)` 후 `update()` 60회 → 오프셋이 `0`으로 수렴
- `setIntensity(0)` + `add(1.0)` → 오프셋이 항상 `0`
- 오프셋 절댓값이 `MAX_OFFSET`을 절대 초과하지 않음
- `reset()` 직후 오프셋 `0`

### 5.2 F2-B 흔들림 트리거 · 접근성 설정 — 권지예

**트리거 세기 표**

| 상황 | trauma | 결과 | 호출 지점 (현재 코드) |
| --- | ---: | --- | --- |
| 플레이어 피격 | 0.80 | 4px, 87% | `GameScreen:1262`, `InfiniteGameScreen:391`/`:418` (`spawnPlayerExplosion` 옆) |
| 보스 격파 | 1.00 | 6px, 92% | `GameScreen.handleBossEvent()` → `case DEFEATED:` (`:1478`) |
| 보스 등장 | 0.70 | 3px, 83% | `GameScreen.handleBossEvent()` → `case SPAWNED:` (`:1465`) |
| 보스 페이즈 전환 | 0.60 | 2px, 77% | `GameScreen.handleBossEvent()` → `case PHASE_CHANGED:` (`:1468`) |
| 수류탄/섬멸 아이템 폭발 | 0.52 | 2px, 69% | `Grenade` / `AnnihilatorItem` 폭발 처리부 (`GameScreen` 내 수류탄 처리 구간) |
| 일반 적 폭발 | 0.40 | 1px, 48% | `GameScreen:1084`/`:1122`/`:1848`/`:1866`, `InfiniteGameScreen:366`/`:411` |

> **이 값들은 한 번 올렸음.** 처음에 쓴 0.15~0.60은 §5.1의 제곱 곡선과 맞지 않았음.
> `6 × 0.15² = 0.135`라 **일반 적 폭발이 단 1픽셀도 움직이지 않았고**, 아래 수용
> 기준의 첫 줄을 충족할 수 없었음 (KAN-60). 제곱은 그대로 둠 — 꼬리를 빠르게
> 죽이는 게 제곱의 역할이고, 문제는 곡선이 아니라 값이었음. 업계 trauma 모델도
> 단일 이벤트에 0.4~0.6을 씀.
>
> 누적은 안전함. 0.5초 간격으로 적을 연달아 잡아도 감쇠(0.90/프레임)가 추가분을
> 상쇄해 `trauma`가 0.42 근처에서 평형을 이루므로 계속 1px임.

> **호출 지점을 새로 찾을 필요 없음.**
>
> - **보스 3종은 한 곳에 다 모여 있음.** `GameScreen.handleBossEvent()` (`:1463`)의
>   `switch` 문에 `SPAWNED` / `PHASE_CHANGED` / `DEFEATED` case가 이미 다 있고,
>   각 case에서 이미 `AudioManager.playSFX(...)`를 호출하고 있음.
>   **그 줄 바로 옆에 `ScreenShake.add(...)` 한 줄씩** 넣으면 끝.
> - **나머지는 전부 기존 파티클 호출 옆.** `particleSystem.spawnEnemyExplosion(...)` /
>   `spawnPlayerExplosion(...)` 호출 바로 아래에 한 줄씩 추가.

또한 각 게임 화면의 `update()`에 `ScreenShake.update()` 호출 1줄, 화면 진입 시 `ScreenShake.reset()` 1줄 추가.

> ⚠️ **`update()`는 매 프레임 반드시 호출해야 함.** 감쇠뿐 아니라 **그 프레임의
> 흔들림 방향까지 여기서 뽑음** (한 프레임 안에서 오프셋을 여러 번 읽어도 같은
> 값이 나오게 하기 위함). 호출하지 않으면 감쇠가 안 되는 정도가 아니라 **흔들림이
> 아예 없음**.

**접근성 설정 (이게 F2-B 작업량의 대부분)**

- `GameplayConfig`에 흔들림 강도 추가: `Off(0.0) / Low(0.5) / Normal(1.0)`, 기본값 **Normal**
- `GameplaySettingsScreen`에 항목 추가
- ⚠️ **주의**: 현재 `GameplaySettingsScreen`은 **난이도 전용 구조**임.
  `selectedOption`이 그대로 난이도 값으로 쓰이고 (`GameplayConfig.setDifficulty(selectedOption)`),
  `OPTION_COUNT = 3`이 난이도 3종을 의미함.
  **"설정 항목 선택"과 "항목의 값 선택"을 분리하는 리팩터가 필요함.**
  위/아래로 항목 이동, 좌/우로 값 변경하는 2축 구조를 권장.
  마우스 호버 경로(`getGameplaySettingsOptionAt`)도 같이 고쳐야 함.
- 설정값은 게임 시작 시 `ScreenShake.setIntensity(...)`로 주입

**수용 기준**

- 적을 쏴서 터뜨리면 화면이 미세하게 흔들림
- 플레이어 피격 시 명확하게 흔들림
- 보스 격파 시 가장 크게 흔들림
- 설정에서 **Off로 두면 어떤 상황에서도 흔들리지 않음**
- 난이도 설정이 **기존대로 정상 동작** (리팩터 회귀 확인 — 워크플로 §4)
- 흔들림 중에도 적·탄환 **충돌 판정이 바뀌지 않음** (오프셋은 렌더 전용, 로직 좌표에 영향 없음)

---

## 6. F3 — 전체화면 / 해상도 스케일링 — 나 (Yongwon)

### 6.1 현재 문제

- `Frame.java:35` `setResizable(false)`, 448×520 **고정** → 2026년 4K/레티나에서 손톱만 한 창
- `DrawManager.initDrawing()`이 **매 프레임** `new BufferedImage(448, 520, INT_RGB)` 생성
  → 프레임당 약 0.9MB, **초당 약 56MB의 GC 쓰레기**
- `BufferStrategy`를 쓰는 곳이 **0개**, `frame.getGraphics()`를 매 프레임 호출 → VSync 없음, 티어링

### 6.2 작업

- 신규 `engine/DisplayConfig` — 스케일(1x/2x/3x), 전체화면 여부, 설정 저장
- `DrawManager.initDrawing()` — 백버퍼를 **1회 할당 후 재사용** (이것만으로 GC 쓰레기 제거)
- `DrawManager.completeDrawing()` — `createBufferStrategy(2)` + **정수배** 스케일 + 레터박스 + `ScreenShake` 오프셋
- `Frame` — 전체화면 토글, **F11** 키 바인딩
- `InputManager` — **마우스 좌표를 논리 좌표로 역변환**

### 6.3 치명적 주의사항

> **마우스 좌표 역변환을 빼먹으면 모든 메뉴가 깨짐.**
> `TitleScreen`, `SettingsScreen`, `GameplaySettingsScreen`, `ShopScreen`,
> `ShipSelectionScreen` 등이 **전부 마우스 호버 선택**을 쓰고 있음.
> 스케일 2배 상태에서 역변환이 없으면 클릭 위치가 전부 어긋남.
> ```
> 논리X = (실제X - 레터박스X) / scale
> ```

> **정수배 스케일만 허용.** 픽셀 아트를 1.5배 같은 비정수로 늘리면 픽셀이 뭉개져서
> 오히려 싸구려로 보임. 정수배 + 레터박스가 현대 픽셀 게임의 정답임.

### 6.4 수용 기준

- 1x / 2x / 3x / 전체화면 전환이 게임 중·메뉴에서 모두 동작
- **네 상태 모두에서 메뉴 마우스 클릭이 정확히 동작** (최우선 확인 항목)
- 확대해도 픽셀이 선명함 (보간 없음, 정수배)
- 전체화면에서 종횡비 유지, 레터박스가 검정으로 채워짐
- 설정이 재시작 후에도 유지됨
- `ScreenShake` 오프셋이 스케일된 좌표계에서 정상 적용됨

---

## 7. 의존 순서와 병렬성

```
M0 (나, 계약 선행 PR) ── 머지 필수 ──┬── F1-A  노준아   (신규 전용, 독립)
                                     ├── F1-B  지윤아   (테스트 문자열로 선행 가능)
                                     ├── F2-A  오채민   (신규 전용, 독립)
                                     ├── F2-B  권지예   (스텁이 받아줌, 독립)
                                     └── F3    나       (독립)
```

**M0 머지 후에는 5명 전원이 서로를 기다리지 않음.** 시간이 없으므로 M0을 최우선으로 처리함.

| 순서 | 내용 |
| --- | --- |
| 1 | **M0 작성 → 리뷰 → 머지** (최우선) |
| 2 | F1-A · F1-B · F2-A · F2-B · F3 병렬 진행 |
| 3 | PR 리뷰 상호 교차, 순차 머지 |
| 4 | 통합 검증 — 전체화면 + 흔들림 + 보스 대사를 **동시에 켜고** 보스전 1판 완주 |
