# Launcher — 폰에서 집 PC를 켜고·붙고·끄는 개인 앱 모음

이 레포는 Miner(투자 모노레포)와 별개인 **개인 유틸 앱** 레포다. 첫 번째 앱은
민티저 영상(https://youtu.be/31qcQLzcKkg)의 "PC 원격 접속" 앱 재현 — 갤럭시 폴드8에서
버튼 하나로 집 PC를 깨우고(Wake-on-LAN via NAS), Sunshine 응답을 감지해 Moonlight를
자동으로 열고, 다 쓰면 끄는 앱이다. 모든 통신은 Tailscale 안에서만 한다(포트포워딩 0).

## 문서 지도

| 문서 | 내용 |
|---|---|
| `docs/video-analysis.md` | 영상 자막 분석 — 구성·소감·셋업 설명·측정 수치·요금제 |
| `docs/setup-guide.md` | 셋업 설명과 준비물 — 하드웨어·소프트웨어·네트워크·순서·검증·비용 |
| `docs/app-spec.md` | "PC 원격 접속" 앱 명세 — 화면·동작·설정·보안·마일스톤·미결 사항 |

## 구조 (예정)

```
docs/          # 위 문서
nas-relay/     # NAS에서 도는 WoL 중계 서버 (Python 단일 파일 + Docker) — M1
app/           # Android 앱 (Kotlin · Jetpack Compose) — M2~
```

## 규약

- **시크릿·식별자는 코드에 박지 않는다.** Tailscale IP·MAC·중계 토큰은 앱 설정 화면과
  `nas-relay/.env`(git 밖)에만 둔다. 예시는 `*.example` 파일로만 커밋한다.
- 네트워크 노출 0 원칙: 중계 서버는 Tailscale 인터페이스(100.x)에만 바인딩하고 토큰을 요구한다.
  공유기 포트포워딩·UPnP·DDNS는 쓰지 않는다.
- 코드 변경은 draft PR로 올리고 사용자 승인을 기다린다. 문서만 바뀐 커밋도 같은 PR 흐름을 탄다.
- 테스트: `python -m pytest nas-relay/tests -q` (M1부터). Android 앱은 로컬 Android Studio에서
  빌드·실기기 검증한다 — 원격 컨테이너에는 Android SDK가 없다.
