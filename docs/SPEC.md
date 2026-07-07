# 코노 다이어리 (Kono Diary) — 기술 스펙

혼자 코인노래방에서 녹음한 긴 오디오 파일(10분~1시간)을 가져와서:
1. 녹음 파일 안에서 **노래별 시작/끝 구간을 자동 추측**해서 보여주고 (프로토타입: 에너지 기반 분할, 자동 곡 인식 없음)
2. 사용자가 구간마다 **곡 정보(제목/가수)를 직접 등록**하고
3. 구간(테이크)을 **바로 재생**해서 들어보고
4. **별점(1~5)** 으로 스스로 평가해서, 곡별로 "가장 잘 부른 테이크"를 모아 볼 수 있게 하는 안드로이드 앱.

---

## 1. 기술 스택 (고정 — 임의 변경 금지)

| 항목 | 값 |
|---|---|
| 언어 / UI | Kotlin 2.0.21 / Jetpack Compose (Material3) |
| minSdk / target / compile | 26 / 35 / 35 |
| AGP / Gradle | 8.7.3 / 8.11.1 (wrapper) |
| DB | Room 2.6.1 (KSP `2.0.21-1.0.27`) |
| 재생 | Media3 ExoPlayer 1.5.1 (ClippingConfiguration으로 구간 재생) |
| 내비게이션 | navigation-compose 2.8.5 |
| DI | **없음** — 수동 `AppContainer` (Hilt 금지, 프로토타입 단순화) |
| 코루틴 | kotlinx-coroutines 1.9.0 |
| applicationId / namespace | `com.konodiary.app` |
| rootProject.name | `KonoDiary` |
| 앱 표시 이름 | 코노 다이어리 |

- 파일 접근: SAF(`OpenDocument`, `audio/*` + `video/3gpp`) + `takePersistableUriPermission`. 저장소 권한 불필요. INTERNET 권한은 곡 검색(iTunes Search API)에만 사용.
- UI 문자열: 한국어 하드코딩 허용 (프로토타입).
- 버전 카탈로그(`gradle/libs.versions.toml`) 사용.

## 2. 패키지 구조와 파일 소유권 (에이전트별 담당 — 다른 영역 수정 금지)

```
app/src/main/java/com/konodiary/app/
├── core/model/Models.kt              [계약 — 이미 작성됨, 수정 금지]
├── core/contracts/*.kt               [계약 — 이미 작성됨, 수정 금지]
├── KonoApp.kt                        [SCAFFOLD] Application, container 초기화
├── MainActivity.kt                   [SCAFFOLD] setContent { KonoDiaryTheme { AppRoot() } }
├── DefaultAppContainer.kt            [SCAFFOLD] 아래 §3의 구현 클래스명으로 조립
├── ui/theme/                         [SCAFFOLD] Color.kt, Theme.kt (KonoDiaryTheme), Type.kt
├── data/                             [DATA] Room entity/dao/db/mapper + repository 구현
├── audio/analysis/                   [AUDIO] 디코딩 + 구간 분할 + AnalysisController 구현
├── audio/player/                     [PLAYER] ExoPlayer 기반 SegmentPlayer 구현
└── ui/ (theme 제외), 예: ui/AppRoot.kt, ui/screens/**, ui/components/** [UI]
app/src/test/java/com/konodiary/app/audio/   [AUDIO] 분할 로직 순수 JVM 단위 테스트
```

## 3. 구현 클래스명 (계약 — `DefaultAppContainer`가 이 이름으로 조립함)

| 인터페이스 (core/contracts) | 구현 클래스 | 위치 | 생성자 |
|---|---|---|---|
| `RecordingRepository` | `DefaultRecordingRepository` | `data.repository` | `(db: KonoDatabase)` |
| `SegmentRepository` | `DefaultSegmentRepository` | `data.repository` | `(db: KonoDatabase)` |
| `SongRepository` | `DefaultSongRepository` | `data.repository` | `(db: KonoDatabase)` |
| `AudioAnalyzer` | `EnergyAudioAnalyzer` | `audio.analysis` | `(context: Context)` |
| `AnalysisController` | `DefaultAnalysisController` | `audio.analysis` | `(context: Context, scope: CoroutineScope, recordingRepository: RecordingRepository, audioAnalyzer: AudioAnalyzer)` |
| `SegmentPlayer` | `ExoSegmentPlayer` | `audio.player` | `(context: Context)` — Application.onCreate(메인 스레드)에서 생성됨 |
| Room DB | `KonoDatabase` | `data.db` | `KonoDatabase.getInstance(context: Context)` 컴패니언 팩토리 |

`AppRoot`: UI 에이전트가 `com.konodiary.app.ui` 패키지에 `@Composable fun AppRoot()` 제공 (MainActivity가 호출).

## 4. DB 스키마 (DATA)

- `recordings`: id(PK auto), fileUri, displayName, durationMs, importedAt, analysisState(문자열)
- `segments`: id(PK auto), recordingId(FK→recordings, CASCADE), startMs, endMs, songId(FK→songs, SET_NULL, nullable), rating(Int 0=미평가..5), memo, source("AUTO"/"MANUAL"), confidence(Float)
- `songs`: id(PK auto), title, artist
- `envelopes`: recordingId(PK, FK→recordings CASCADE), frameMs(Int), data(BLOB — FloatArray를 리틀엔디언 바이트로)

동작 규칙:
- `saveAnalysisResult`는 **트랜잭션**: 해당 recording의 AUTO 구간 삭제(MANUAL은 유지) → 새 AUTO 구간 삽입 → envelope upsert → recording의 durationMs 갱신 + analysisState=ANALYZED.
- `deleteSong` 시 참조하던 segment들의 songId는 null로 (FK SET_NULL).
- 구간 목록은 startMs 오름차순, 곡 목록(`observeSongsWithTakes`)은 최고 별점 내림차순 → 제목순.
- DB 파일명 `kono.db`.

## 5. 구간 분할 알고리즘 (AUDIO) — 프로토타입 핵심

`EnergyAudioAnalyzer.analyze(fileUri, onProgress)`:
1. `MediaExtractor` + `MediaCodec`으로 **스트리밍 디코딩** (전체 PCM을 메모리에 들고 있지 말 것 — 1시간 파일 대응). 16-bit PCM 가정하되 `KEY_PCM_ENCODING`이 float이면 대응. 다채널은 모노 믹스다운.
2. **100ms 프레임마다 RMS** 계산 → dBFS 배열 (이것만 메모리에 유지; 1시간 = 36,000 float).
3. 진행률: 처리한 presentationTime / 전체 duration → `onProgress(0f..1f)`.
4. 분할은 **순수 함수** `KaraokeSegmenter.segment(frameDb: FloatArray, frameMs: Int, params): List<DetectedSegment>` 로 분리 (JVM 단위 테스트 대상, android 의존성 금지):
   - 2초 이동 평균으로 스무딩.
   - 노이즈 플로어 = 스무딩값의 10퍼센타일, 음악 레벨 = 95퍼센타일. (처음엔 20퍼센타일이었으나, 쉬지 않고 부르는 실제 62분 녹음에서 조용한 시간이 ~12%뿐이라 플로어가 음악 모드에 얹혀 전체가 1구간으로 오판됨 — 실측 검증 후 10으로 조정.)
   - 임계값 = noiseFloor + 0.35 × (p95 − noiseFloor). 단 (p95 − noiseFloor) < 6dB면 전체가 균일 → 전체를 1개 구간으로 반환.
   - 프레임 분류(임계값 초과 = active) 후 모폴로지 정리: **12초 이하 비활성 갭은 메움**(노래 중 간주/브릿지), **50초 미만 active 구간은 버림**(멘트/곡 선택 시간).
   - 구간 경계에 앞 1초 / 뒤 1.5초 패딩 (파일 범위 클램프, 인접 구간과 겹치지 않게).
   - confidence = 구간 내 active 프레임 비율.
5. envelope: 스무딩 dB를 `noiseFloor..max` 기준 0..1로 정규화한 FloatArray (UI 파형용).
6. 코루틴 취소 대응(`ensureActive`), `Dispatchers.Default`에서 실행, codec/extractor는 finally에서 release.
7. 단위 테스트: 합성 envelope(예: 조용함 60s → 노래 180s → 조용함 40s → 노래 240s → 조용함 30s)으로 구간 수/경계 검증, 갭 메움/짧은 구간 제거/균일 입력 케이스 포함.

`DefaultAnalysisController`: `startAnalysis(recordingId)` → 이미 실행 중이면 무시, 아니면 scope에서: 상태 ANALYZING → analyzer 실행(진행률을 `progress` StateFlow 맵에 반영) → 성공 시 `saveAnalysisResult`, 실패 시 상태 FAILED. 종료 시 progress 맵에서 제거.

## 6. 재생 (PLAYER)

`ExoSegmentPlayer` (Media3 ExoPlayer 1개 재사용, 메인 스레드에서 접근):
- `play(clip)`: `MediaItem` + `ClippingConfiguration(startPositionMs, endPositionMs)` 세팅 후 재생. `endMs <= startMs`면 클리핑 없이 전체 재생.
- `state: StateFlow<PlayerUiState>` — 재생 중 200~300ms 간격으로 position 갱신(코루틴 or Handler, 메인 스레드). 클리핑된 아이템의 `currentPosition`은 클립 기준 상대값.
- `STATE_ENDED` → isPlaying=false, position=duration. `stop()` → clip=null. `seekTo`는 클립 상대 위치.
- 오디오 포커스: `setAudioAttributes(..., handleAudioFocus=true)`.

## 7. 화면 (UI)

`AppRoot()`: Scaffold + 하단 내비게이션 2탭(**녹음**, **노래**) + 재생 중일 때 하단 `MiniPlayerBar`(제목, 재생/일시정지, 진행 슬라이더(시크 가능), 닫기). NavHost 라우트: `home`, `recording/{id}`, `songs`, `song/{id}`.

1. **HomeScreen (녹음 탭)**: 녹음 목록(이름, 가져온 날짜, 길이 mm:ss, 분석 상태 뱃지 / 분석 중이면 진행률). FAB "가져오기" → SAF `OpenDocument(audio/*)` → `takePersistableUriPermission` → ContentResolver로 displayName, `MediaMetadataRetriever`로 duration(IO 스레드) → `importRecording`. 항목 탭 → 상세. 길게/삭제 버튼 → 확인 다이얼로그 후 삭제.
2. **RecordingDetailScreen**: 
   - 미분석이면 "노래 구간 찾기" 버튼, 분석 중이면 진행률 표시, 실패 시 재시도.
   - 분석 완료: envelope 파형(Canvas, 세로 막대들) + 구간을 색 오버레이로 표시.
   - 구간 카드 리스트: `#n 12:34 ~ 16:02 (3:28)`, 곡 미지정 시 "곡 등록" 버튼 → 다이얼로그(기존 곡 선택 or 새 곡 제목/가수 입력), 별점(탭해서 1~5, 다시 탭하면 해제=0), 재생 버튼(→ SegmentPlayer), 경계 미세조정(시작/끝 각각 ±5s 넛지 버튼), 메모, 삭제.
   - "구간 수동 추가" 버튼 → 시작/끝 mm:ss 입력 다이얼로그.
3. **SongsScreen (노래 탭)**: 등록된 곡 목록 — 제목/가수, 테이크 수, 최고 별점(★). 탭 → SongDetail.
4. **SongDetailScreen**: 해당 곡의 모든 테이크를 별점 내림차순으로 (어느 녹음/날짜, 길이, 별점, 재생 버튼). 최고 별점 테이크에 "BEST" 뱃지.

ViewModel: `(context.applicationContext as KonoApp).container`로 의존성 접근, `viewModel(factory = ...)` 패턴. 시간 포맷 유틸 `ui/common/TimeFormat.kt` (h:mm:ss / mm:ss).

### 7.5 곡 등록 다이얼로그 — 검색 우선 (2026-07-07 추가)

- 검색어 입력(2자 이상, 500ms 디바운스) 시 두 섹션 표시:
  1. **내 곡**: 등록된 곡 중 제목/가수에 검색어 포함(대소문자 무시) — 앨범아트, 제목/가수, "테이크 N개 · 최고 ★M". 탭 = 그 곡으로 바로 배정.
  2. **온라인 검색**: iTunes Search API 결과 — 앨범아트/제목/가수/앨범. 기존 곡과 제목+가수 일치(트림+대소문자 무시)하면 "등록됨 · 테이크 N개" 뱃지. 탭 = `findOrCreateSong` 후 배정.
- 별점은 이 다이얼로그에서 다루지 않음 (등록과 평가는 분리 — 별점은 구간 카드에서 나중에).
- 검색 실패/오프라인 시 안내 문구 + "직접 입력"(제목/가수 수동, `findOrCreateSong`) 폴백 항상 제공.
- API: `https://itunes.apple.com/search?media=music&entity=song&country=KR&limit=20&term=…` (키 불필요·무료). `artworkUrl100`의 `100x100bb`를 `300x300bb`로 치환해 저장. `Song.artworkUrl`(nullable), DB v2 마이그레이션(`ALTER TABLE songs ADD COLUMN artworkUrl TEXT`) — 기존 데이터 보존, 파괴적 마이그레이션 금지.
- 썸네일 렌더링: Coil `AsyncImage` (없으면 음표 아이콘 폴백).

### 7.6 폴더 연결 일괄 가져오기 (2026-07-07 추가 · 같은 날 다중 폴더로 확장)

- 홈 FAB 메뉴: "파일 직접 선택" / "폴더 스캔"(연결 폴더가 있을 때) / "폴더 관리"(미연결 시 라벨 "보이스레코더 폴더 연결").
- 폴더 관리 다이얼로그: 연결된 폴더 목록(tree URI의 docId를 사람이 읽을 경로로 표시 + 해제 버튼)과 추가 옵션 —
  * **삼성 추천 경로 2개**: `Recordings/Voice Recorder`(2023-10 이후 녹음), `Voice Recorder`(그 이전 녹음). 탭하면 `OpenDocumentTree`가 해당 위치에서 열리게 initial URI 전달 (`DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:<경로>")`). 제조사가 삼성이면 "(삼성 기본 경로)" 안내 문구; 타 기기에서도 노출하되 picker가 해당 경로를 못 찾으면 기본 위치로 열림(무해).
  * "다른 폴더 직접 선택" — 기본 위치에서 picker.
- 연결: read 권한 영속화 + SharedPreferences(`kono_prefs`, `folder_tree_uris` — 개행(\n) 구분 목록, 삽입 순서 유지). **구 단일 키 `folder_tree_uri`가 있으면 최초 접근 시 목록으로 자동 이관 후 구 키 제거.** 연결 직후 즉시 스캔.
- 스캔은 연결된 모든 폴더의 직계 자식 대상. displayName 중복 판정은 전 폴더 공통 집합(같은 스캔 안에서 가져온 이름 포함)으로 — 두 폴더에 같은 파일명이 있어도 1회만 가져옴. 폴더 단위 실패(권한 회수 등)는 격리하고 나머지 폴더 계속.
- 스캔(`FolderSyncManager.scanAndImport`): 폴더 직계 자식 중 **파일명 "음성" 접두 + 오디오 확장자**(m4a/3ga/3gp/aac/mp3/wav/ogg, 대소문자 무시)만 대상 — 통화 녹음 등 오염 방지. displayName이 기존 녹음과 같으면 스킵. 새 파일은 가져오기(MediaMetadataRetriever로 길이) 후 **각각 분석 큐잉**.
- 분석 큐: 배터리 사용 중엔 동시 1개, **전원 연결 중엔 최대 3개 병렬**(첫 일괄 가져오기 백로그 대응; 보조 워커는 충전 중에만 큐 소비). 대기 항목도 progress 맵에 0f로 등록되어 "분석 중 0%"로 표시. 큐는 메모리 전용이므로 **앱 시작 시 NOT_ANALYZED 녹음 전부를 자동 재큐잉**(`enqueueAllPending` — stale ANALYZING 복구 직후 실행). 파일 직접 선택 가져오기도 즉시 분석 시작.
- **분석 Foreground Service** (`AnalysisService`, 2026-07-07 추가): 큐가 비어있다→작업 생김 전이 시 컨트롤러가 시작, progress 맵이 비면 stopSelf. 화면 꺼짐 대비 PARTIAL_WAKE_LOCK(6h 타임아웃 안전장치), 상시 알림("노래 구간 분석 중 · <파일명> · 대기 N개", 진행률 바, 채널 "analysis" LOW). FGS 타입: API 35+ `mediaProcessing`, 29+ `dataSync` 폴백. Android 15+의 6시간 제한은 `onTimeout`→graceful stop (다음 앱 실행 때 재큐잉으로 이어짐). POST_NOTIFICATIONS는 MainActivity에서 1회 요청(거부 시에도 동작, 알림만 미표시).
- 홈 진입 시 폴더가 연결되어 있으면 자동 스캔; 새 파일이 있을 때만 스낵바("새 녹음 N개 가져옴 · 분석 시작"). 실패 건은 개수만 안내.

## 8. 빌드/검증

- 로컬 툴체인: JDK `C:\Users\Jobs\.andev\jdk`, SDK `C:\Users\Jobs\.andev\sdk` (local.properties의 sdk.dir).
- 검증 명령: `.\gradlew.bat :app:assembleDebug` 및 `.\gradlew.bat :app:testDebugUnitTest`.
- 위 버전 조합이 maven에 없을 때만 근접 버전으로 조정 가능 (조정 시 보고).
