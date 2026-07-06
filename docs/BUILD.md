# 코노 다이어리 — 빌드 가이드

이 문서는 로컬에서 앱을 **빌드 / 테스트 / 실기기 설치**하는 방법을 정리한다.
IDE(Android Studio) 없이 **명령줄만으로** 완결된다.

---

## 0. 한 줄 요약

```powershell
$env:JAVA_HOME = "C:\Users\Jobs\.andev\jdk"   # 세션마다 1번
.\gradlew.bat :app:assembleDebug              # APK 빌드
```

산출물: `app\build\outputs\apk\debug\app-debug.apk`

---

## 1. 사전 준비 (이미 설치됨)

| 구성요소 | 위치 | 확인 명령 |
|---|---|---|
| JDK 17 (Temurin) | `C:\Users\Jobs\.andev\jdk` | `C:\Users\Jobs\.andev\jdk\bin\java.exe -version` |
| Android SDK | `C:\Users\Jobs\.andev\sdk` | `dir C:\Users\Jobs\.andev\sdk\platforms` |
| SDK 구성 | platform-tools / platforms;android-35 / build-tools;35.0.0 | — |
| Gradle | wrapper 8.11.1 (프로젝트 동봉, 별도 설치 불필요) | `.\gradlew.bat --version` |

- SDK 경로는 [`local.properties`](../local.properties)의 `sdk.dir`로 지정되어 있다. 이 파일은 git에 커밋하지 않는다.
- **추가로 다운로드할 것은 없다.** 위 툴체인만 있으면 빌드가 완결된다.

---

## 2. 환경 변수 (JAVA_HOME)

Gradle이 JDK 17을 쓰도록 `JAVA_HOME`을 잡아준다. 시스템 PATH에 java가 없어도
`JAVA_HOME`만 지정하면 된다.

**PowerShell (현재 세션만):**
```powershell
$env:JAVA_HOME = "C:\Users\Jobs\.andev\jdk"
```

**PowerShell (영구 — 한 번만 실행):**
```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Users\Jobs\.andev\jdk", "User")
# 새 터미널을 열어야 적용됨
```

> 영구 설정을 해두면 이후 터미널에서 `JAVA_HOME` 지정 없이 바로 `.\gradlew.bat`만 쓰면 된다.

---

## 3. 자주 쓰는 명령

모두 **프로젝트 루트**(`coin-karaoke-voice-diary`)에서 실행한다.

| 목적 | 명령 |
|---|---|
| 디버그 APK 빌드 | `.\gradlew.bat :app:assembleDebug` |
| 단위 테스트 (분할 알고리즘 등) | `.\gradlew.bat :app:testDebugUnitTest` |
| 컴파일만 빠르게 확인 | `.\gradlew.bat :app:compileDebugKotlin` |
| 클린 후 재빌드 | `.\gradlew.bat clean :app:assembleDebug` |
| 사용 가능한 태스크 목록 | `.\gradlew.bat :app:tasks` |
| 릴리즈 APK (미서명) | `.\gradlew.bat :app:assembleRelease` |

로그를 깔끔하게 보려면 `--console=plain`, 오류를 자세히 보려면 `--stacktrace`를 덧붙인다.
```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

---

## 4. 실기기 설치 & 실행

`adb`는 `C:\Users\Jobs\.andev\sdk\platform-tools\adb.exe`에 있다.

1. 안드로이드폰: **설정 → 개발자 옵션 → USB 디버깅** 켜기 (개발자 옵션은
   "빌드 번호" 7번 탭으로 활성화).
2. USB로 연결 후 폰에서 디버깅 허용.
3. 연결 확인 / 설치 / 실행:

```powershell
$adb = "C:\Users\Jobs\.andev\sdk\platform-tools\adb.exe"
& $adb devices                                    # 기기가 목록에 뜨는지 확인
& $adb install -r app\build\outputs\apk\debug\app-debug.apk   # 설치(-r=재설치)
& $adb shell am start -n com.konodiary.app/.MainActivity      # 앱 실행
```

- 로그 보기: `& $adb logcat --pid=$(& $adb shell pidof -s com.konodiary.app)`
- `applicationId` / 실행 액티비티: `com.konodiary.app` / `.MainActivity`

---

## 5. 빌드 산출물 위치

| 산출물 | 경로 |
|---|---|
| 디버그 APK | `app\build\outputs\apk\debug\app-debug.apk` |
| 단위 테스트 리포트 | `app\build\reports\tests\testDebugUnitTest\index.html` |
| 릴리즈 APK(미서명) | `app\build\outputs\apk\release\app-release-unsigned.apk` |

---

## 6. 문제 해결 (Troubleshooting)

**`SDK location not found` / `sdk.dir`**
→ 루트에 [`local.properties`](../local.properties)가 있고 `sdk.dir=C\:\\Users\\Jobs\\.andev\\sdk`
로 되어 있는지 확인.

**`Could not find a Java installation` / JDK 관련 오류**
→ `JAVA_HOME`이 `C:\Users\Jobs\.andev\jdk`를 가리키는지 확인 (2절).

**`License for package ... not accepted` / SDK 구성요소 설치 실패**
→ 라이선스 미동의 상태. 아래로 수락 후 재시도:
```powershell
$env:JAVA_HOME = "C:\Users\Jobs\.andev\jdk"
$sdkm = "C:\Users\Jobs\.andev\sdk\cmdline-tools\latest\bin\sdkmanager.bat"
& $sdkm --sdk_root="C:\Users\Jobs\.andev\sdk" --licenses   # 프롬프트에 모두 y
```
> 최초 툴체인 설치 시 이 단계가 누락되어 platform-tools/android-35/build-tools 설치가
> 건너뛰어진 적이 있음. 라이선스 수락으로 해결됨.

**첫 빌드가 느림 / 멈춘 것처럼 보임**
→ 최초 실행은 Gradle 배포판(~130MB)과 AGP·의존성을 내려받으므로 수 분 걸린다.
빌드 데몬이 뜨면 이후 빌드는 빨라진다. (`--console=plain`으로 진행 로그 확인)

**`adb: no devices/emulators found`**
→ USB 디버깅 활성화 + 케이블 연결 + 폰의 디버깅 허용 팝업 확인.
`& $adb kill-server; & $adb start-server`로 재시작해볼 것.

---

## 7. 빌드가 하는 일 (참고)

- Kotlin 2.0.21 + Compose, Room은 **KSP**로 코드 생성(`kspDebugKotlin` 태스크).
- 최소 SDK 26 / 타깃·컴파일 35.
- 자세한 기술 스택·아키텍처는 [`SPEC.md`](SPEC.md) 참고.
