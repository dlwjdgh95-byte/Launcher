# NAS 선정·구성 가이드 (2026-11-02)

용도 세 가지: ① `nas-relay`(WoL 중계) + Tailscale 24시간, ② 폰 사진 자동 백업, ③ 중요 파일 백업.
기준: 가성비를 보되 안정성(소프트웨어 성숙도·국내 AS·백업 도구)을 우선한다. 가격은 다나와 최저가(2026-11-02, 본체만)다.

## 1. 결론

| 구분 | 추천 | 이유 |
|---|---|---|
| **본체** | **Synology DS225+** (약 49.3만 원, 3년 보증) | Docker(Container Manager)·Tailscale·사진 백업(Synology Photos)·백업(Hyper Backup)·스냅샷이 전부 공식 경로. DSM 7.3부터 서드파티 HDD 허용. 2.5GbE. 국내 총판·한국사업본부(2026-01 출범) |
| **디스크** | CMR 4TB × 2 미러 (IronWolf 약 30.6만, WD Red Plus 약 33.9만, HAT3300 약 32.7만 원) | 사진+문서는 4TB로 충분. 나중에 큰 디스크로 한 장씩 교체하면 SHR이 용량을 늘려 준다. WD Red(비Plus)는 SMR이라 제외 |
| **UPS** | APC BE650G2-GR급 (약 19만 원, 선택) | 정전 시 USB 신호로 안전 종료. 미러보다 정전이 데이터를 더 자주 깨뜨린다 |
| **오프사이트** | Backblaze B2 (월 $6.95/TB) 또는 외장 USB | RAID는 백업이 아니다. 중요 폴더만 B2로 보내면 월 1만 원 안팎 |

총액(본체 + IronWolf 4TB×2): **약 110만 원**, UPS 포함 약 130만 원.

### 저가 구성 (저장 + PC 켜고 끄기만이면 이쪽, 2026-11-03 추가)

용도를 "파일·사진 저장 + WoL 중계"로 한정하면 **Synology DS223j(약 27.8만 원)** 로 충분하다.

| 항목 | 내용 |
|---|---|
| 되는 것 | Container Manager(armv8 빌드가 공식 아카이브에 있다 — `ContainerManager-armv8-24.0.2-1706.spk`), Tailscale armv8 SPK, Synology Photos 모바일 자동 백업, Hyper Backup, Snapshot Replication, SHR |
| 제약 | RAM 1GB 고정(증설 불가), 1GbE, 보증 2년. 사진 얼굴·객체 인식은 기대하지 않는다(객체 인식은 2GB 이상 모델). 컨테이너는 중계 서버 하나만 돌린다 |
| 디스크 | 2TB가 4TB와 값이 비슷하므로 4TB. **1장으로 시작(SHR)** 하고 외장 USB 4TB에 Hyper Backup 주 1회. 나중에 한 장 더 꽂으면 SHR이 미러로 바뀐다 |
| 총액 | 본체 27.8만 + WD Red Plus 4TB 24.4만(IronWolf 30.6만) + 외장 4TB 21.4만 ≈ **74만 원**. 미러(4TB×2)로 가면 77만~89만 원 |

- 미러는 디스크 고장만 막고 실수 삭제·랜섬웨어는 못 막는다. 같은 돈이면 "1장 + 외장 백업"이 데이터 보호에는 낫다.
- DS124(1베이, 약 21.3만 원): 6.5만 원 아끼는 대신 나중에 미러로 못 늘린다. 그 돈 차이면 DS223j.
- Asustor AS1202T(약 20.5만 원): CPU·RAM이 DS223j와 같지만 Docker·Tailscale 동작 근거를 확인하지 못해 보류.
- BeeStation: Docker·패키지 설치가 안 되는 폐쇄형이라 중계 서버를 못 돌린다. 제외.
- 구매 전 확인 한 가지: 시놀로지 사이트에서 DS223j 모델을 고른 뒤 패키지 목록에 Container Manager가 보이는지 본다(armv8 빌드 존재로 지원이 확인되지만, 모델 페이지에서 한 번 더 보는 게 안전하다).

### 다른 선택지

| 후보 | 본체 | 장점 | 안 고른 이유 |
|---|---|---|---|
| Synology DS223j | 27.8만 | 최저가, Docker·Tailscale 됨 | ARM 1GB·1GbE·2년 보증. 사진 얼굴 인식 불가. 릴레이+백업만이면 가능 |
| QNAP TS-264-4G-KR | 41.4만 | N5095 4코어·4GB, DS225+보다 싸고 빠름 | DeadBolt 등 보안 이력. Tailscale만 쓰면 위험은 줄지만 "안정성 우선"과 어긋남 |
| UGREEN DXP2800 | 75.9만(직구 59~69만) | N100·8GB DDR5, 하드웨어 가성비 최고 | UGOS 1년 남짓, Tailscale은 Docker로만, 국내 AS 미확인 |
| TerraMaster F2-424 | 59만 | N95·8GB | TOS 성숙도, Tailscale 앱 미확인, 국내 AS 미확인 |
| Asustor AS5402T | 53.2만 | N5105, Tailscale 커뮤니티 패키지, 3년 보증 | 사진 앱 얼굴 인식이 베타, 생태계 작음 |
| 직접 조립(미니PC + TrueNAS) | 40만~ | 성능·자유도 | 유지보수가 사용자 몫. "안정성·손 덜 가기"와 반대 |

RAM: DS225+ 기본 2GB(최대 6GB). Synology Photos 객체 인식까지 쓰려면 4GB 이상으로 증설한다. 릴레이·사진 백업·얼굴 인식은 2GB로 된다.

## 2. 구매 요령

- 다나와 최저가를 기준으로 쿠팡·공식총판(하이마트 등)과 비교한다. DS225+는 직구가 더 비싸다(약 74만 원).
- K-세일페스타(2026-10-29~11-15, 십일절 포함) 기간이다. 본체는 세일을, HDD는 2026년 급등 상태라 과거 최저가와 대조한다.
- 합본(본체+디스크)은 단품 합산과 꼭 비교한다. 같은 날 다나와에서 DS225+ 16TB 합본은 7만 원 쌌고, HAT3300 4TB×2 합본은 30만 원 비쌌다.
- 중고 HDD는 미러에 쓰지 않는다(보증 없음·가동시간 검증 어려움). 병행수입은 국내 AS 가능 여부를 먼저 묻는다.
- 미러는 같은 모델 두 장이면 되지만, 같은 로트 동시 고장을 피하려면 제조사나 구매처를 나눠도 된다.

## 3. 초기 구성 순서 (DS225+ 기준)

1. **스토리지**: 스토리지 풀 SHR(또는 RAID 1) + 볼륨 Btrfs 1개. "Unverified" 경고는 서드파티 HDD라 뜨는 것이고 사용에는 지장 없다.
2. **공유 폴더**: `photos`, `backup`, `docs`, `relay`(nas-relay 설정). 공유 폴더별 **Snapshot Replication** 예약: 매일 1회, 30개 보관.
3. **계정·보안**: 기본 `admin` 비활성화(다른 이름의 관리자 생성 후), 2단계 인증, 자동 차단, 방화벽은 LAN + `100.64.0.0/10`만 허용. **QuickConnect·UPnP·포트포워딩 끔** — 외부 접속은 Tailscale만. DSM 자동 업데이트 켬(2026-09 CVSS 9.8급 패치 사례).
4. **Tailscale**: 패키지 센터 판은 지원 종료라 **SPK 수동 설치**. 작업 스케줄러에 부팅 시 root로 `tailscale configure-host` + `pkgctl-Tailscale.service` 재시작 등록(DSM 7 TUN 제약). 관리 콘솔에서 이 노드의 키 만료 끔. 방화벽에 100.64.0.0/10 허용.
5. **nas-relay**: Container Manager > 프로젝트에서 `nas-relay/docker-compose.yml`을 올린다(`network_mode: host` 필수). `.env`는 `relay` 공유 폴더에 두고 `chmod 600`. `PC_HOST`는 PC의 LAN IP.
6. **사진 백업**: Synology Photos 모바일(Android 11+)에서 자동 백업 켬(Wi-Fi·충전 중 조건 권장). 삼성 갤러리는 NAS 직접 동기화를 지원하지 않으므로 Synology Photos 앱을 쓴다. 배터리 최적화 예외에 등록한다.
7. **백업(3-2-1)**: Hyper Backup으로 ① 외장 USB에 주 1회 전체, ② B2(S3 호환)에 `docs`·`photos` 매일 증분. 분기마다 Hyper Backup Explorer로 복구 테스트.
8. **유지보수**: 확장 S.M.A.R.T. 월 1회, 데이터 스크러빙 3개월, 알림은 이메일(또는 텔레그램 웹훅). UPS는 USB로 연결해 안전 종료 설정.
9. **전기**: HDD 2장 대기 기준 약 15~20W, 월 2,200~2,900원(200원/kWh 가정). 24시간 릴레이 컨테이너가 하이버네이션을 막을 수 있으니 절전은 기대하지 않는다.

## 4. 용량 산정

- 사진: 폰 1대, 연 20~40GB 수준이면 4TB 미러로 수년 여유.
- 중요 파일: 보통 수백 GB.
- 영상 보관·미디어 서버까지 하려면 8TB×2(약 +41만 원)로 시작하거나, 4베이(DS425+ 82만 원)로 간다.

## 출처 요약

- Synology: 제품 https://www.synology.com/ko-kr/products/DS225+ · Container Manager 적용 모델 https://www.synology.com/dsm/packages/ContainerManager · DSM 7.3 드라이브 정책 https://nascompares.com/2025/10/08/synology-dsm-7-3-nas-hdd-ssd-compatibility-in-the-plus-rs-xs-xs-sa-and-more/ · Tailscale KB https://tailscale.com/kb/1131/synology · Photos 얼굴 인식 모델 https://kb.synology.com/DSM/tutorial/Which_Synology_NAS_models_support_the_facial_recognition_feature_on_Synology_Photos · Hyper Backup https://www.synology.com/en-us/dsm/software_spec/hyper_backup
- 가격: 다나와 검색(DS225+, DS223j, QNAP TS-264, UGREEN DXP2800, 테라마스터 F2-424, 아수스토 AS5402T, WD Red Plus 4TB, 씨게이트 아이언울프 4TB, 시놀로지 HAT3300, APC UPS 650VA) · HDD 급등 https://nascompares.com/news/hdd-ssd-price-situation-worse-than-we-thought-2026/
- 보안: QNAP QSA-22-24 https://www.qnap.com/en-us/security-advisory/qsa-22-24 · Synology 2026-09 패치 https://csa.gov.sg/alerts-and-advisories/alerts/al-2026-127/
- B2 가격 https://www.backblaze.com/cloud-storage/pricing · UGREEN Tailscale 안내 https://ai.ugreen.com/blogs/how-to/ugreen-nas-remote-access · Immich 요건 https://docs.immich.app/install/requirements
