# AI 커버 PoC 계획 — "내 목소리로 부른 다시 사랑한다 말할까"

목표: 코인노래방 녹음에서 추출한 **내 목소리 음색**을 원곡 보컬에 입혀, "이 곡을 내가 부르면 어떤 느낌일지" 미리듣기를 만든다.
이 문서는 GPU 환경에서 이어서 작업할 수 있도록 이 PC(GPU 없음)에서 준비해 둔 자산과 전체 레시피를 기록한 것이다.

주의: AI 커버는 **원곡 가수의 음정·박자·표현 + 내 음색**이다. "내 실력"의 시뮬레이션이 아니라 "내 목소리의 이상적 버전" 미리듣기에 가깝다. 개인 감상용으로만 사용할 것 (커버 공개 배포는 원곡 저작권 이슈).

---

## 1. 이 PC에 준비된 자산

| 경로 | 내용 |
|---|---|
| `poc\segments\take1~4.wav` | **내 노래 구간 4개** (18분 코노 녹음에서 컷, 48kHz mono PCM, 보컬+반주+에코 섞임). 원본: `samples\음성 260703_193446.m4a`, 경계 1:31–5:55 / 6:20–10:03 / 10:36–14:02 / 14:34–18:22 |
| `poc\target\원곡_다시사랑한다말할까.mp3` | 김동률 원곡 (192kbps, 폰 멜론 폴더에서 확보) |
| `poc\target\MR_다시사랑한다말할까.mp3` | **같은 곡 노래방 MR** — 반주 분리 불필요, 믹스에 그대로 사용 |
| `C:\Users\Jobs\.andev\aicover\venv` | Python venv (demucs + torch 2.12.1 **CPU**, 737MB). GPU 머신에서는 새로 만들 것 |
| `samples\` (62분/18분 m4a) | 참조 음성을 더 뽑고 싶을 때의 원본. 62분 파일 구간 경계는 `docs\SPEC.md` §5 알고리즘으로 재계산: `.\gradlew.bat :app:testDebugUnitTest -PsampleDir=<wav폴더>` (하네스가 구간 리포트 출력, JAVA_HOME=C:\Users\Jobs\.andev\jdk) |

`poc\`와 `samples\`는 gitignore되어 있으므로 GPU 머신으로는 **직접 복사**해야 한다 (필요 파일: segments 4개 + target 2개 ≈ 100MB).

## 2. 파이프라인 개요

```
[내 코노 녹음 take1~4] ─ Demucs ─→ 내 보컬 스템(에코 포함) ─┐
                                                        (참조 음성)
[원곡 mp3] ─ Demucs ─→ 김동률 보컬 스템 ──── Seed-VC/RVC ──→ 내 음색 보컬 ─┐
                                                                      ffmpeg 믹스
[노래방 MR mp3] ───────────────────────────────────────── (반주 그대로) ─┴→ 데모.mp3
```

## 3. 공통 준비 (GPU 머신)

```bash
# venv (Python 3.10~3.11 권장 — seed-vc 호환)
python -m venv venv && source venv/bin/activate   # Windows: venv\Scripts\activate
pip install torch torchaudio --index-url https://download.pytorch.org/whl/cu121   # CUDA 버전에 맞게
pip install demucs

# ① 내 보컬 스템 추출 (take별 2~3분/GPU)
demucs --two-stems=vocals -n htdemucs -o sep_out poc/segments/take1.wav   # take2~4 반복
# → sep_out/htdemucs/take1/vocals.wav = 참조 음성 (no_vocals는 버림)

# ② 원곡 보컬 스템
demucs --two-stems=vocals -n htdemucs -o sep_out "poc/target/원곡_다시사랑한다말할까.mp3"
```

품질 팁: 참조 음성에 **노래방 에코가 배어 있음**. 여유가 되면 UVR(Ultimate Vocal Remover) GUI의 De-Echo/De-Reverb 모델(예: UVR-DeEcho-DeReverb)을 vocals에 한 번 더 통과시키면 음색 추출 품질이 올라간다. PoC에서는 생략 가능 — 결과에 에코 특성이 섞이는 것만 감안.

## 4. 경로 A — Seed-VC 제로샷 (PoC, 학습 불필요, GPU 30분 내)

```bash
git clone https://github.com/Plachtaa/seed-vc && cd seed-vc
pip install -r requirements.txt

# 하이라이트 45초만 컷 (예: 후렴 2:10~2:55 — 실제 위치는 들어보고 조정)
ffmpeg -i ../sep_out/htdemucs/원곡_다시사랑한다말할까/vocals.wav -ss 130 -t 45 src_45s.wav
ffmpeg -i ../poc/target/MR_다시사랑한다말할까.mp3 -ss 130 -t 45 mr_45s.wav

# 제로샷 변환 (노래 모드: f0 컨디셔닝 필수)
python inference.py --source src_45s.wav \
  --target ../sep_out/htdemucs/take1/vocals.wav \
  --output out/ --diffusion-steps 50 --f0-condition True --auto-f0-adjust True
# --target은 참조 음성(내 목소리). take1이 별로면 take2~4로 교체하며 비교

# 믹스 (변환 보컬 + MR)
ffmpeg -i out/변환결과.wav -i mr_45s.wav -filter_complex \
  "[0:a]volume=1.0[v];[1:a]volume=0.85[m];[v][m]amix=inputs=2:duration=shortest" demo_45s.mp3
```

- 참조 음성은 20~30초 잘라 써도 충분 (가장 또렷하게 부른 부분으로).
- MR과 원곡의 싱크가 다르면(전주 길이 차이) `-ss` 오프셋을 MR 쪽만 따로 조정.

## 5. 경로 B — RVC 학습 (PoC가 만족스러우면, 본격 품질)

- 도구: **Applio**(https://github.com/IAHispano/Applio — RVC 계열 중 설치/CLI 가장 깔끔) 또는 RVC WebUI.
- 데이터: take1~4 vocals(≈15분)로 시작, 부족하면 62분 파일에서 추가 추출(§1 하네스). **디리버브 필수 권장** — 에코가 모델에 학습되는 걸 줄임. 무음/박수/멘트 구간은 잘라낼 것.
- 학습: 48kHz(v2), 200~300 epoch, RTX 30xx급에서 30분~1시간. RMS 정규화 켜기.
- 추론: 원곡 보컬 스템 입력, index rate 0.5~0.75, f0 방식 rmvpe. 이후 믹스는 §4와 동일.
- 제로샷 대비 기대 효과: 음색 유사도·발음 안정성 큰 폭 향상.

## 6. 결과 판단 기준 (PoC에서 들어볼 것)

1. **음색 유사도**: 내 목소리로 들리는가? (가족/지인 블라인드 테스트가 정확)
2. **에코 오염**: 노래방 울림이 거슬리는 수준인가 → 심하면 디리버브 추가 후 재시도
3. **고음/저음 아티팩트**: 김동률 음역과 내 음역 차이에서 갈라짐이 있는가 → seed-vc `--auto-f0-adjust`, RVC transpose로 완화
4. 위 1·2가 합격이면 → 경로 B(RVC 학습) + 앱에 "학습 데이터 내보내기"(별점 높은 테이크 wav 팩) 기능 추가 가치 있음

## 7. 앱 연계 (나중)

앱은 이미 구간·별점 데이터를 갖고 있으므로, "별점 4+ 테이크를 wav로 내보내기" 기능(하루 작업)만 붙이면 학습 데이터 준비가 자동화된다. 완전 자동 커버 생성(인앱)은 GPU 백엔드/유료 API 필요 — PoC 결과를 보고 결정.
