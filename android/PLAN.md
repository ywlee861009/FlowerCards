# FlowerCards Android 작업 계획 (android/PLAN.md)

> `docs/` 기획 문서(overview / rules / scoring / game-loop)를 소스 오브 트루스로 하는 **구현 계획서**.
> 이 문서는 "어떻게 만들 것인가"를 정의하며, "무엇을 만들 것인가"는 기획 문서를 따른다.
> 최종 갱신: 2026-07-08

---

## 1. 아키텍처 결정 (확정)

| # | 항목 | 결정 | 근거 |
|---|---|---|---|
| 1 | 렌더링 | **Jetpack Compose + Canvas** | UI(HUD/오버레이/모달)와 게임 보드를 한 프레임워크로 처리. 화투 게임 수준의 렌더링 부하는 Compose로 충분하며 개발 속도·유지보수 유리. (overview §6 미결정 해소) |
| 2 | 모듈 구조 | **계층형 멀티모듈**: `:domain`(순수 Kotlin JVM) + `:data` + `:feature:game` + `:feature:setting` + `:app` | 룰·점수 엔진을 Android 의존성 0으로 격리하고, 앱 진입점·게임 UI·설정/정책 UI·데이터 경계를 분리해 Phase 2 이후 확장 비용을 낮춤 |
| 3 | 손맛·사운드 훅 | 도메인이 **`GameEvent` sealed class 스트림을 방출** | rules §7 AC "특수 상황 발생 시 사운드/햅틱 트리거 훅 호출" 충족. UI/사운드 레이어는 이벤트 구독만 하면 됨. game-loop §5 트리거 표와 1:1 대응 |
| 4 | 룰 파라미터화 | 확정 룰 13건을 **`RuleSet` 객체 필드**로 (기본값 = 확정값) | 지역 룰 변형 대비. 로직에 하드코딩 금지 |
| 5 | minSdk / targetSdk | **26** / 35 | 국내 커버리지 99%+ 확보 + SoundPool/햅틱 API 안정 |
| 6 | 언어/빌드 | Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, 버전 카탈로그(Kotlin DSL) | 이 머신에서 검증된 조합 |
| 7 | 상태 관리 | 도메인 = 불변 상태 + 순수 함수 전이(`GameState -> (GameState, List<GameEvent>)`), 앱 = ViewModel + StateFlow | 턴 엔진을 결정적(deterministic)으로 유지 → 테스트·리플레이 용이. 난수는 `Random` 주입 |

> CLI 빌드 시 JDK 17~21 필요 (Gradle 8.9는 JDK 24 미지원). Android Studio는 내장 JBR 사용하므로 무관.

## 2. 확정된 룰 (13건 스냅샷)

rules.md ⚠️ 8건 + scoring.md ⚠️ 5건을 문서 기본값(제안)대로 확정 (2026-07-07 사용자 승인):

| 항목 | 확정값 |
|---|---|
| 쌍피 처리 | **없음** (국진은 열끗 고정) |
| 선(先) 결정 | **랜덤** |
| 총통 | **적용** — 즉시 승 (점수는 RuleSet 파라미터, 기본 10점) |
| 딜 시 바닥 같은 월 4장 | **리딜** |
| 자뻑 피 뺏기 | **1장** |
| 폭탄 추가 턴 | **있음** |
| 나가리 곱 | **없음** |
| 고 방식 | **1·2고 +1점, 3고부터 ×2 누적** |
| 멍박(2인) | **미적용** |
| 배수 누적 | **곱연산** (피박×흔들기 = ×4) |
| 점수→판돈 환산 | **MVP 제외** |

## 3. 모듈·패키지 구조

```
android/
  settings.gradle.kts            # :app, :domain, :data, :feature:game, :feature:setting
  gradle/libs.versions.toml
  domain/                        # 순수 Kotlin JVM — Android 의존성 0
    src/main/kotlin/com/flowercards/domain/
      model/      # Month, CardKind, TtiColor, Card, Deck(48장 팩토리)
      rule/       # RuleSet (확정 13건 파라미터)
      deal/       # Dealer — 10/10/8/20 분배, 총통 검출, 바닥4장 리딜
      match/      # 같은 월 매칭 0/1/2/3장, 따조
      engine/     # GameState, TurnEngine(상태머신), GameEvent
      score/      # ScoreCalculator, 배수(피박/광박/흔들기/폭탄), 고 가산, 고박 정산
    src/test/kotlin/...          # 수용 기준 기반 단위 테스트
  data/                          # Android data layer
    src/main/kotlin/com/flowercards/data/
      GameRepository.kt          # 저장/복원/리플레이 등 데이터 경계
      InMemoryGameRepository.kt  # Phase 2 전까지 쓰는 임시 구현
  feature/
    game/                        # Android feature module (Compose)
      src/main/kotlin/com/flowercards/feature/game/
        GameRoute.kt             # Phase 1: 게임 화면 placeholder, Phase 2 보드 UI 진입점
    setting/                     # Android feature module (Compose)
      src/main/kotlin/com/flowercards/feature/setting/
        SettingRoute.kt          # 설정/오픈소스 라이선스 표시 화면
  app/                           # Android 앱 shell
    src/main/kotlin/com/flowercards/app/
      MainActivity.kt            # 앱 진입점. Game/Setting route 전환
```

의존성 방향:

```
:app -> :feature:game
:app -> :feature:setting
:feature:game -> :domain
:data -> :domain
```

## 4. 단계별 로드맵

| Phase | 내용 | 완료 기준 | 상태 |
|---|---|---|---|
| **1. 도메인 로직** | 모델/딜/매칭/턴 엔진/점수/이벤트 + 단위 테스트 | `:domain:test` 전체 통과 — rules §7·scoring §6 AC 커버, scoring 예시 4종 재현 | **완료** (테스트 59종, 리뷰 반영) |
| **2. 보드 UI** | game-loop §3 세로 레이아웃(8밴드), Compose Canvas 보드, 카드 드래그/스냅/뒤집기, 고/스톱·특수상황 오버레이 | AI 없이 양쪽 수동 조작으로 1판 완주 | 대기 |
| **3. AI 상대** | 기본 난이도 1종 (카드 선택·고/스톱·흔들기 휴리스틱) | AI와 1판 완결 루프 | 대기 |
| **4. 손맛·사운드·햅틱** | game-loop §5 트리거 표 전체 — SFX(SoundPool 저지연), 햅틱, 애니메이션 커브, 화면 shake | 트리거 표 전 항목 발동 + 임팩트 프레임 동기화 | 대기 |
| **5. 결과·폴리시** | 결과 화면(점수 내역 연출), 타이틀/일시정지/설정, 코치마크 | overview §4 MVP 포함 항목 전부 충족 | 일부 완료 — 설정 모듈/라이선스 표시 화면 추가 |

Phase 경계마다 커밋 + `code-reviewer`/`qa-tester` 서브에이전트 검수를 거친다.

## 5.1 에셋 라이선스 결정

- 화투 이미지는 Wikimedia Commons의 `SVG Hwatu` 계열을 사용한다.
- 대표 출처: `https://commons.wikimedia.org/wiki/Category:SVG_Hwatu`
- 라이선스: **Creative Commons Attribution-ShareAlike 4.0 International**
- 앱 내 `설정 > 오픈소스 라이선스` 화면에 저작자, 출처, 라이선스 URL, 변경사항 없음(`No changes made`)을 표시한다.
- 현재 구현은 이미지를 수정하지 않는 전제로 출처 표시만 반영한다. 이미지 수정/재가공이 생기면 변경사항 표기와 파생물 라이선스 조건을 다시 점검한다.

## 6. 도메인 설계 핵심

- **턴 흐름** (rules §4): `PlayCard → MatchFromHand → FlipFromPile → MatchFromFlip → Capture → SpecialJudge(뻑/자뻑/따닥/쪽/싹쓸이) → ScoreJudge → GoStop?/NextTurn`
- **GameEvent** (game-loop §5 트리거 1:1): `Dealt, CardPlayed, CardFlipped, Captured, Ppeok, JaPpeok, Ttadak, Ttajo, Jjok, Sweep, Shake, Bomb, GoDeclared, Stopped, Chongtong, Nagari, PiStolen, …`
- **점수 계산 순서** (scoring §4): 기본점수(광+열끗·고도리+띠·단+피) → 고 가산/배수 → 박(피박/광박) → 흔들기/폭탄 → 고박 정산
- **불변식**: 어떤 시점에도 `손패+손패+바닥+더미+양쪽 획득 = 48장` (엔진 내부 assert + 테스트)

## 7. 테스트 전략 (Phase 1)

수용 기준을 테스트 이름에 그대로 매핑한다:
- rules §7: 덱 구성표 일치(월/종류별 개수), 딜 불변식 10/10/8/20, 매칭 0/1/2/3장, 뻑/자뻑/따닥/따조/쪽/싹쓸이/폭탄 발생 조건, 3점 이상에서만 고/스톱, 판 종료 3조건(스톱/총통/나가리)
- scoring §6: 광 3/3비광/4/5, 임계값(열끗5·띠5·피10), 고도리/홍단/청단/초단, 피박·광박 발동, 고 가산/배수, 배수 곱연산 누적, 고박 역전 케이스, **예시 계산 4종(§5 표) 재현**

## 8. 검증 명령

```bash
cd android
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :domain:test        # 도메인 단위 테스트
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug  # 앱 빌드
```
