# Phase 2 구현계획서 — 인게임 보드 UI (android/PLAN-phase2.md)

> `android/PLAN.md` Phase 2의 상세 전개. 레이아웃 소스 = `docs/planning/game-loop.md`,
> 룰/점수 = `docs/planning/rules.md`·`scoring.md`, 도메인 API = `:domain` 확정 구현.
> 이 문서는 "코드 착수 전 설계"만 담는다. 작성: 2026-07-08 · 상태: 계획(미착수)

---

## 1. 목표와 완료 기준

- **완료 기준(PLAN.md §4)**: AI 없이 **양쪽 수동 조작(hotseat)으로 1판 완주**.
  - "완주" = 딜 → 턴 교대 → 매칭/뒤집기/특수상황 반영 → 3점↑ 도달 시 고/스톱 선택 → 판 종료 3조건(스톱/총통/나가리) 중 하나로 결과 도달.
- **Phase 2에서 하는 것**: 렌더링, 입력, 상태 배선, 오버레이(고·스톱/특수상황)를 **시각 수준**까지.
- **Phase 2에서 안 하는 것**: AI(Phase 3), SFX/햅틱/임팩트 프레임 동기화(Phase 4), 결과 화면 카타르시스 연출·타이틀·일시정지 정식판(Phase 5). Phase 2에는 **최소 결과 표시**만 둔다(다음 판 재시작 가능한 수준).

**설계 대전제(game-loop §0)**: 세로 예산 경쟁. 바닥패(≈24%)·내 손패(≈20%)에 예산을 몰고, 획득패는 압축 스트립, AI 손패는 최소 높이.

---

## 2. 진행 방식 — 세로 슬라이스 5단계

각 단계는 **그 자체로 빌드·실행 가능한 산출물**을 남긴다. 단계 끝마다 `:app:assembleDebug` 확인 + 필요 시 code-reviewer 검수.

| 단계 | 산출물 | "동작한다"의 정의 |
|---|---|---|
| **2-A. 상태 배선** | `GameViewModel` + `StateFlow<GameUiState>`, `:feature:game`에서 도메인 호출 | 딜된 초기 상태가 로그/텍스트로 보이고, 버튼으로 `PlayCard`를 쏘면 상태가 갱신된다 |
| **2-B. 정적 보드 렌더** | 8밴드 레이아웃 + 카드 렌더(Coil-SVG 로더), 좌표계 확정 | 딜 직후 보드가 실제 카드 이미지로 그려진다(입력 없음, read-only) |
| **2-C. 입력·상호작용** | 손패 선택(아크 팝업/히트박스 확장), 드래그→바닥 스냅, 더미 뒤집기 | 손으로 카드를 내고 바닥에 스냅되며 매칭/획득이 화면에 반영된다 |
| **2-D. 오버레이·특수상황** | 고/스톱 모달, 특수상황 비파괴 스탬프/토스트(뻑/쪽/따닥/싹쓸이), 흔들기/폭탄 액션바 | 3점↑에서 고/스톱을 고르고, 특수상황이 발생 지점에 표시된다 |
| **2-E. 완주·결과** | 판 종료 3조건 처리 + 최소 결과 표시 + 새 딜 재시작 | 스톱/총통/나가리로 1판이 끝나고 다시 시작할 수 있다 |

> 리스크가 앞에 몰리도록 배치했다. 2-A/2-B에서 도메인↔UI 계약과 좌표계가 굳으면 이후는 반복 작업이다.

---

## 3. 상태 관리 아키텍처 (2-A)

PLAN.md §1-7 확정: 도메인 = 불변 상태 + 순수 전이, 앱 = ViewModel + StateFlow.

```
UI(Compose) --PlayerAction--> GameViewModel --TurnEngine.apply--> TurnResult(state, events)
      ▲                              │
      └──── GameUiState (StateFlow) ─┘   events는 1회성 → 애니메이션 채널로
```

- **`GameViewModel`** (`:feature:game`)
  - 보유: `var current: GameState` (내부), `MutableStateFlow<GameUiState>`.
  - 입력 API: `fun onAction(action: PlayerAction)` — `TurnEngine.apply` 호출 → 상태 갱신 + 이벤트 방출.
  - 난수 주입: `Dealer`에 `Random` 주입(§1-7). 테스트/리플레이용 시드 보관.
  - **hotseat**: `state.turn`을 그대로 신뢰. 양쪽 다 사람이 조작하므로 AI 분기 없음. "지금 조작 주체 = `state.turn`".
- **`GameUiState`** (UI 전용 모델, 도메인 GameState의 뷰 투영)
  - `myHand / oppHandCount / floor / pileCount / myCaptured(카테고리 집계) / oppCaptured / turn / phase / myScore / oppScore / canGo / shakeableMonths / bombableMonths / result`.
  - 도메인 GameState를 그대로 Compose에 넘기지 않고 **투영**한다 → 획득패 카테고리 집계·흔들기/폭탄 가능 월 계산 등 뷰 로직을 여기서 흡수.
- **이벤트(1회성)**: `TurnResult.events`는 상태가 아니라 **효과**다. `Channel<GameEvent>` 또는 `SharedFlow`로 흘려 애니메이션 레이어가 소비. Phase 2는 시각 트리거만, Phase 4에서 SFX/햅틱을 같은 채널에 얹는다.
  - collectAsStateWithLifecycle 사용(백그라운드 낭비 방지).

**도메인 계약 확인 결과(착수 전 점검 완료)**:
- `TurnEngine.apply(state, action)` 순수 함수 — ViewModel 배선에 그대로 적합.
- 액션 5종: `PlayCard(card, matchTarget?)` · `PlayBomb(month)` · `FlipOnly` · `DeclareGo` · `DeclareStop`.
- `GameState.phase(GamePhase)`로 UI가 "지금 무엇을 기다리는가"를 안다(플레이 대기 / 고스톱 대기 등). **고/스톱 모달 노출 트리거 = phase + `GoStopChoice` 이벤트**.
- 매칭 후보가 2장일 때 `PlayCard.matchTarget`으로 선택 전달(도메인 `autoPick` 존재하나 UI에선 사용자 선택 우선).

---

## 4. 좌표계와 레이아웃 (2-B)

### 4-1. 밴드 배분 (game-loop §3)
`Column` + `Modifier.weight`로 8밴드. 괄호는 세로 예산 제안값(가용 높이 기준 비율).

| 밴드 | weight(≈) | 렌더 |
|---|---|---|
| HUD 상단 | 0.06 | 일시정지/AI상태/사운드 토글 — Phase 2는 텍스트 최소 |
| AI 획득 스트립 | 0.09 | 카테고리 겹침 스택 + 배지 + 점수 |
| AI 손패(뒷면) | 0.08 | `card_back.svg` N장 겹침 |
| **바닥패+더미** | **0.24** | Canvas — 8장 그리드 + 월별 더미/뻑 더미 + 우측 더미 |
| 내 획득 스트립 | 0.10 | AI와 대칭 + ▲(3점 도달) |
| **내 손패** | **0.20** | Canvas/Layout — 미세 아크 겹침 1줄 |
| 액션바 | 0.07 | [흔들기][폭탄][설정], 조건부 활성 |

### 4-2. 렌더 전략 — Compose 하이브리드
- **정적/이산 요소**(HUD·스트립·액션바·손패 카드 슬롯): 일반 Composable + `Image`(캐시된 `ImageBitmap`). Compose가 레이아웃/스킵 처리.
- **연속/애니메이션 요소**(바닥 배치, 카드 비행, 드래그 추종): 필요한 곳만 `Canvas`/`graphicsLayer`. 매 프레임 값(드래그 offset·비행 progress)은 **Composition이 아니라 Layout/Draw에서 읽는다**(`Modifier.offset { }`, `graphicsLayer { }`, `drawWithCache`) → 스크롤/드래그 시 전체 recomposition 회피.
  - 근거 스킬: deferring-state-reads, optimizing-lazy-layouts, ordering-modifier-chains.
- **결정 필요 없음**: SurfaceView는 배제(§PLAN 1-1에서 Compose+Canvas 확정).

### 4-3. 카드 좌표 모델
- 논리 좌표: 카드마다 `slot`(밴드 내 인덱스) → 밴드가 픽셀 위치로 변환. 비행 애니메이션은 `start slot → end slot` 보간.
- 카드 종횡비 고정: SVG viewBox 103.2×168.2 (≈0.614). 모든 렌더 크기를 이 비율로.

---

## 5. 카드 렌더링 로더 — Coil-SVG (2-B, 확정)

PLAN.md §1-8 확정 방식. SVG 원본 1벌 유지 + 런타임 비트맵 캐시.

- **의존성**: `io.coil-kt:coil-compose` + `io.coil-kt:coil-svg`를 `gradle/libs.versions.toml`에 추가, `:feature:game`에 적용. (버전은 카탈로그 최신 안정)
- **로더**: 앱 시작 시 `ImageLoader`에 `SvgDecoder.Factory()` 등록. `assets/cards/{Card.id}.svg` 경로 로드.
- **캐시 전략**: 매 프레임 벡터 드로잉 회피가 목적. 48종 앞면 + 뒷면 1종 = 49개를 **초기(Splash/딜 전) 프리로드해 `ImageBitmap` 메모리 캐시**로 상주. game-loop §1 Splash가 "첫 프레임 디코딩 지연 숨김"을 명시 → 프리로드를 여기 건다.
  - Coil memoryCache 사용 + `Card.id → ImageBitmap` 맵을 ViewModel 상위(또는 rememberSaveable 아닌 단일 소유자)에서 보관.
- **파일명 계약**: `assets/cards/`의 파일명 = `Card.id`(`m##_gwang/yeol/tti/pi#`), 뒷면 `card_back.svg`. 이미 1:1 확보됨(PLAN §5.0).
- **폴백**: 이미지 로드 실패 시 단색 Rect + 월 텍스트(디버그 가시성).

---

## 6. 입력 처리 (2-C)

game-loop §6(미세 아크 겹침) + §4-2(흔들기/폭탄) 반영.

- **손패 선택**: 터치다운 → 해당 카드 팝업(위로 솟음+확대), 릴리스로 확정, 아래로 내리면 취소(§6).
- **히트박스 확장**: 시각 겹침과 무관하게 탭 영역을 인접 카드 경계 중앙까지(§6). `pointerInput` 히트 판정에서 슬롯 폭 > 시각 폭.
- **드래그→스냅**: 손패 카드 드래그 → 바닥 영역 릴리스 → 가장 가까운 유효 슬롯/매칭 대상으로 스냅. offset은 Draw/Layout에서만 읽기.
- **매칭 대상 2장**: 바닥에 같은 월 2장 존재 시 드롭 지점으로 대상 선택 → `PlayCard.matchTarget` 전달.
- **더미 뒤집기**: 카드 낸 뒤 자동(`resolveFlipAndCapture`). UI는 결과 이벤트를 flip 애니로 표현.
- **흔들기/폭탄 활성 조건**: 손패 같은 월 3장 감지 → 액션바 버튼 활성 + 해당 3장 하이라이트(§4-2). `GameUiState.shakeableMonths/bombableMonths`로 노출.
  - 흔들기: 내기 **전** 선언(해당 판 ×2). 폭탄: 선언 후 3장 슬램 → `PlayBomb(month)`.

---

## 7. 이벤트 → 시각 반응 매핑 (2-D)

game-loop §5 트리거 표 중 **시각 반응만** Phase 2에서 구현(사운드/햅틱 트리거 지점은 Phase 4). 비파괴 오버레이 원칙(§4-3): 화면 안 갈아끼움.

| GameEvent | Phase 2 시각 처리 | 지속성 |
|---|---|---|
| `Dealt`/딜 | 더미→손패/바닥 순차 비산(stagger) | 1회 |
| `CardPlayed`/`CardFlipped` | 바닥 스냅 반동, 더미 3D flip | 1회 |
| `Captured` | 두 카드 붙어 획득 스트립으로 비행 | 1회 |
| `Ppeok`/`JaPpeok` | 바닥 해당 월 더미 위 "뻑" 스탬프 + 화면 shake + 붉은 플래시 | **지속 스탬프**(판 상태 영향) |
| `Jjok` | 발생 지점 "쪽" 말풍선 + 상대 피 비행 | 1회 토스트 |
| `Ttadak`/`Ttajo` | 캡처 지점 별 버스트 + 피 뺏김 이동 | 1회 버스트 |
| `Sweep` | 바닥 가로 스윕 라이트 + 얇은 배너 | 1회 |
| `Shake`/`Bomb` | 3장 흔들림 / 내리꽂기 슬램 | 1회 |
| `GoStopChoice` | 중앙 고/스톱 모달(바닥 dim), 점수+미리보기 | 대기(입력까지) |
| `GoDeclared` | 모달 상방 폭발 + 가장자리 골드 글로우 | 1회 |
| `Stopped`/`GameEnded` | 결과로 전환 | 종료 |
| `Nagari` | 중앙 "나가리" 페이드 | 종료 |
| `PiStolen` | 피 1장 상대→내 스트립 비행 | 1회 |

구분 규칙(§4-3): **판 상태 영향(뻑) = 바닥 위 지속 스탬프**, **순간 이벤트(쪽/따닥) = 짧은 토스트/버스트**.

---

## 8. 판 종료·결과 (2-E)

- 종료 3조건(rules §6, 도메인 `GameResult`): `Win`(스톱/점수) · `ChongtongWin`(총통 즉시) · `Nagari`(더미 소진).
- `GameState.result != null` → 결과 상태 진입. Phase 2는 **최소 결과 표시**(승자 + 점수 + [다시하기]) — 카타르시스 연출은 Phase 5.
- [다시하기] → `Dealer`로 새 딜. 총통은 딜 직후 즉시 판정되므로 재딜 경로에서도 처리.

---

## 9. 미결정·리스크

| # | 항목 | 영향 | 처리 |
|---|---|---|---|
| 1 | **아트 방향**(전통 vs 모던, game-loop §7-1) | 카드/보드 톤 | **Phase 2 블로커 아님** — 기존 SVG 에셋 그대로 렌더. 톤 폴리시는 후속. |
| 2 | 설정 표시 방식(오버레이 vs 별도화면, §7-2) | 진입 흐름 | Phase 2는 현재 별도화면 유지, 액션바 [설정]에서 진입. 최종 확정은 Phase 5. |
| 3 | 매칭 힌트 기본값(§7-3) | 초보 배려 | Phase 2는 힌트 off 기본, 옵션 배선은 후속. |
| 4 | 세로 예산 실측 | 소형 기기서 바닥/손패 짓눌림 위험 | 2-B에서 실제 기기/에뮬 다양한 높이로 검증. weight 조정 여지 확보. |
| 5 | 애니메이션 수치(game-feel.md 미작성) | 손맛 | Phase 2는 기본 커브로 두고, Phase 4 game-feel 확정 시 교체. |
| 6 | 드래그 중 recomposition 폭증 | 프레임 저하 | Draw/Layout 단계 상태 읽기 원칙(§4-2) 준수로 예방. Layout Inspector로 검증. |

---

## 10. 검증

```bash
cd android
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug   # 단계마다
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :domain:test         # 회귀(도메인 불변)
```

- 각 단계 끝: 실기기/에뮬에서 해당 단계 "동작한다" 정의 충족 확인.
- 2-E 완료: hotseat로 1판 완주 → code-reviewer(캔버스 성능·recomposition·메모리) + qa-tester(엣지: 총통/나가리/고박) 검수 후 커밋.
```
```

## 부록. 단계별 착수 순서 요약
1. **2-A** ViewModel/StateFlow/UiState + 도메인 배선 (텍스트로 검증)
2. **2-B** Coil-SVG 로더·프리로드 + 8밴드 정적 렌더 + 좌표계
3. **2-C** 손패 선택/드래그/스냅/뒤집기
4. **2-D** 고·스톱 모달 + 특수상황 오버레이 + 흔들기/폭탄
5. **2-E** 종료 3조건 + 최소 결과 + 재시작
