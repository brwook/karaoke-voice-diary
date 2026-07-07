# 코노 다이어리 디자인 시스템 — "Neon Stage"

컨셉: **어두운 코인노래방 부스 + 네온사인.** 다크 퍼스트(브랜드 아이덴티티는 다크에서 완성), 라이트는 라벤더 틴트의 부드러운 파스텔. `dynamicColor`는 **끈다** (브랜드 색 유지).

## 1. 컬러 (Material3 ColorScheme)

### Dark (기본 아이덴티티)
| 토큰 | 값 | 용도 |
|---|---|---|
| primary | `#F472B6` | 네온 핑크 — 핵심 액션/포커스 |
| onPrimary | `#4A0D2E` | |
| primaryContainer | `#7A2853` | |
| onPrimaryContainer | `#FFD9E9` | |
| secondary | `#A78BFA` | 네온 바이올렛 — 보조 액센트 |
| onSecondary | `#2E1065` | |
| secondaryContainer | `#4C3494` | |
| onSecondaryContainer | `#E9DDFF` | |
| tertiary | `#67E8F9` | 네온 시안 — 진행/재생 위치 |
| onTertiary | `#003A42` | |
| tertiaryContainer | `#0E5560` | |
| onTertiaryContainer | `#C8F6FF` | |
| background / surface | `#14101B` | 보라빛 블랙 |
| onBackground / onSurface | `#EBE4F2` | |
| surfaceVariant | `#241C31` | |
| onSurfaceVariant | `#B3A8C4` | |
| surfaceContainerLowest | `#0F0C15` | |
| surfaceContainerLow | `#181220` | |
| surfaceContainer | `#1B1526` | 하단 바 등 |
| surfaceContainerHigh | `#221A30` | 카드 |
| surfaceContainerHighest | `#2A2139` | 미니플레이어 |
| outline | `#6E6480` / outlineVariant `#3A3149` | |
| error | `#FFB4AB`, onError `#690005`, errorContainer `#93000A`, onErrorContainer `#FFDAD6` | |

### Light
- primary `#C2337F`, onPrimary `#FFFFFF`, primaryContainer `#FFD9E9`, onPrimaryContainer `#3B0021`
- secondary `#6D28D9`, onSecondary `#FFFFFF`, secondaryContainer `#E9DDFF`, onSecondaryContainer `#22005D`
- tertiary `#00838F`, onTertiary `#FFFFFF`, tertiaryContainer `#C8F6FF`, onTertiaryContainer `#00363D`
- background/surface `#FFF7FB`, onSurface `#201A25`, surfaceVariant `#EFE6F2`, onSurfaceVariant `#4E4459`
- surfaceContainer 계열: `#FBF1F7` / High `#F5EBF3` / Highest `#EFE4EF`, outline `#7F7490`, outlineVariant `#D3C6DB`

### 시맨틱 추가 색 (Color.kt에 상수로)
- `StarGold = #FFC24B` (별점 채움), 빈 별은 outlineVariant
- `BestBadgeBg(dark) = #4A3A12`, `BestBadgeFg = #FFD873` (라이트: bg `#FFF0C9` / fg `#7A5B00`)

## 2. 타이포그래피 (시스템 폰트, 스케일만 정제)
- headlineSmall 24sp/SemiBold — 화면 타이틀급
- titleLarge 20sp/SemiBold — TopAppBar
- titleMedium 16sp/SemiBold — 카드 제목, letterSpacing 0sp
- bodyMedium 14sp/Normal, bodySmall 12sp — 보조 정보는 onSurfaceVariant
- labelLarge 14sp/Medium — 버튼/칩, labelSmall 11sp/Medium — 뱃지

## 3. 셰이프
extraSmall 8 / small 12 / **medium 16 / large 20** / extraLarge 28. 카드=large, 칩·뱃지=완전 라운드(50%), 다이얼로그=extraLarge, 미니플레이어 상단만 20.

## 4. 공용 컴포넌트 (THEME 에이전트가 `ui/components/DesignComponents.kt`에 생성 — 아래 시그니처 고정, 다른 에이전트는 호출만)

```kotlin
enum class ChipTone { NEUTRAL, ACTIVE, SUCCESS, ERROR, GOLD }

/** 상태 뱃지/칩. 완전 라운드, labelSmall, 톤별 컨테이너 색. */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier)

/** 별점. onRatingChange가 null이면 표시 전용. 같은 별 재탭 = 0(해제). */
@Composable
fun RatingStars(
    rating: Int,
    onRatingChange: ((Int) -> Unit)? = null,
    starSize: Dp = 22.dp,
    modifier: Modifier = Modifier,
)
```
- ChipTone 매핑: NEUTRAL=surfaceVariant/onSurfaceVariant, ACTIVE=tertiaryContainer/onTertiaryContainer, SUCCESS=secondaryContainer/onSecondaryContainer, ERROR=errorContainer/onErrorContainer, GOLD=BestBadge 색.
- RatingStars: 채움 StarGold, 빈 별 outlineVariant, Star/StarBorder 아이콘.

## 5. 컴포넌트 규칙
- **카드**: `surfaceContainerHigh`, shape large(20), elevation 0, 내부 패딩 16dp, 항목 간 12dp. 리스트 좌우 패딩 16dp.
- **TopAppBar**: 배경 surface(스크롤 시 surfaceContainer), titleLarge.
- **하단 내비**: NavigationBar 배경 surfaceContainer, 선택 아이콘 primary + indicator primaryContainer.
- **FAB(가져오기)**: primaryContainer/onPrimaryContainer 유지 (M3 기본), shape large.
- **파형(Waveform)**: 컨테이너 = surfaceVariant, radius 16, 높이 유지. 막대는 **수직 그라데이션 Brush(secondary→primary)**. 구간 오버레이 = primary 22% alpha 채움 + 구간 상단 2dp primary 라인. (재생 위치 표시가 있으면 tertiary 세로선.)
- **앨범아트 폴백**: secondaryContainer 배경 + onSecondaryContainer MusicNote, radius 12.
- **MiniPlayerBar**: surfaceContainerHighest, 위쪽 모서리만 20 라운드, 좌측 44dp 아트/폴백, 제목 titleMedium 1줄 ellipsis, 슬라이더는 primary(트랙 outlineVariant) 얇게. 재생 버튼은 FilledIconButton(primary).
- **빈 상태(Empty state)**: 화면 중앙, 큰 아이콘(64dp, onSurfaceVariant 30%) + titleMedium 안내 + bodyMedium 보조문 + (홈이면) 유도 문구. 예: 홈 "🎤 아직 녹음이 없어요 / 가져오기로 코노 녹음을 불러오세요".
- **다이얼로그**: shape extraLarge, 배경 surfaceContainerHigh.
- **상태 뱃지 매핑**: 미분석=NEUTRAL "미분석", 분석 중=ACTIVE "분석 중 n%", 완료=SUCCESS "구간 N개"(구간 수 표시), 실패=ERROR "분석 실패", BEST=GOLD "BEST".

## 6. 런처 아이콘 (THEME 에이전트)
- 어댑티브 아이콘: **배경** = 대각(좌상→우하) 그라데이션 벡터 `#7C3AED → #EC4899`.
- **포그라운드** = 흰색 마이크 글리프(캡슐형 헤드 + 스탠드 호 + 받침) 중앙 배치, 우상단에 작은 8분음표. 세이프존(중앙 66x66/108) 준수, 단순·볼드하게.
- **monochrome** 레이어 = 포그라운드와 동일 글리프 (테마 아이콘 대응).
- `values-v31/themes.xml`: `android:windowSplashScreenBackground = #14101B` 지정.

## 7. 화면별 지침
- **홈**: 녹음 카드 = [제목 titleMedium / 날짜·길이 bodySmall(onSurfaceVariant)] + 우측 상태 StatusChip. 삭제는 카드 우측 아이콘 유지하되 onSurfaceVariant. 분석 중이면 카드 하단에 얇은(4dp, 라운드) LinearProgressIndicator(primary/surfaceVariant).
- **녹음 상세**: 상단 파형을 히어로로(카드 없이 여백 위 바로), 그 아래 구간 카드 리스트. 구간 카드: 좌측 원형 인덱스 뱃지(primaryContainer, labelLarge) + 시간 범위 titleMedium + 길이·conf bodySmall + RatingStars + 곡 정보(있으면 아트 썸네일+제목, 없으면 "곡 등록" FilledTonalButton). 재생 버튼 FilledIconButton(primary). 넛지/메모/삭제는 하단 정렬 아이콘 행(onSurfaceVariant).
- **노래 탭**: 행 = 56dp 아트 + 제목 titleMedium/가수 bodySmall + 우측 "테이크 N" 칩(NEUTRAL) + 최고 별점은 StarGold 별 하나 + 숫자.
- **곡 상세**: 헤더 = 96dp 아트 + 제목 headlineSmall + 가수. 테이크 카드에 BEST는 GOLD StatusChip.
- **SongPickerDialog / FolderManageDialog**: 섹션 제목 labelLarge(onSurfaceVariant), 행 간 명확한 터치 타겟(48dp+), 검색 TextField는 라운드(12).
- 모든 화면: 기존 **기능·콜백·상태 로직은 절대 변경하지 않는다** — 스타일/레이아웃만.
