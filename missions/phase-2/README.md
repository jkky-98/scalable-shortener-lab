# Phase 2. Single Instance Baseline Test

Mission 03은 단일 App과 단일 MySQL 구성에서 최대 처리량과 지연 시간 한계 지점을 측정한다.

## 1. 서버 실행

Windows Desktop의 WSL2 터미널에서 실행한다.

```bash
docker compose -f missions/phase-2/docker-compose.yml up -d --build
```

Mission 03-A에서 AccessLog를 비동기로 저장하려면 앱을 async 모드로 올린다.

```bash
SHORTENER_ACCESS_LOG_MODE=async docker compose -f missions/phase-2/docker-compose.yml up -d --build
```

Mission 03-B에서 AccessLog를 batch writer로 저장하려면 앱을 batch 모드로 올린다.

```bash
SHORTENER_ACCESS_LOG_MODE=batch docker compose -f missions/phase-2/docker-compose.yml up -d --build
```

batch 설정 기본값:

- `SHORTENER_ACCESS_LOG_BATCH_QUEUE_CAPACITY=20000`
- `SHORTENER_ACCESS_LOG_BATCH_SIZE=500`
- `SHORTENER_ACCESS_LOG_BATCH_FLUSH_INTERVAL_MS=100`

Mission 03-C에서 AccessLog를 smart batch writer로 저장하려면 앱을 smart batch 모드로 올린다.

```bash
SHORTENER_ACCESS_LOG_MODE=SMART_BATCH docker compose -f missions/phase-2/docker-compose.yml up -d --build
```

smart batch 설정 기본값:

- `SHORTENER_ACCESS_LOG_SMART_BATCH_QUEUE_CAPACITY=20000`
- `SHORTENER_ACCESS_LOG_SMART_BATCH_SIZE=200`
- `SHORTENER_ACCESS_LOG_SMART_BATCH_FLUSH_INTERVAL_MS=50`

기본값은 기존 baseline과 같은 `sync`다.

컨테이너 상태 확인:

```bash
docker compose -f missions/phase-2/docker-compose.yml ps
```

서버 내부 헬스 체크:

```bash
curl http://localhost:8080/api/hello
```

## 2. Windows LAN IP 확인

MacBook에서 부하를 보내려면 Docker 컨테이너 IP가 아니라 Windows Desktop의 LAN IP를 사용해야 한다.

Windows PowerShell:

```powershell
ipconfig
```

현재 네트워크 어댑터의 `IPv4 Address`를 확인한다.

예:

```text
192.168.0.x
```

MacBook에서 접근 확인:

```bash
curl http://<WINDOWS_LAN_IP>:8080/api/hello
```

## 3. 테스트 데이터 생성

MacBook에서 실행한다.

```bash
cd missions/phase-2/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>:8080/api python3 seed-and-save.py
```

성공하면 같은 디렉터리에 `keys.json`이 생성된다.

## 4. k6 부하 테스트 실행

MacBook에서 실행한다.

```bash
cd missions/phase-2/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>:8080/api k6 run --summary-export mission-03-summary.json mission-03-real.js
```

VU를 바꿀 때는 `TARGET_VUS`를 지정한다.

```bash
BASE_URL=http://<WINDOWS_LAN_IP>:8080/api TARGET_VUS=400 \
  k6 run --summary-export mission-03-400-summary.json mission-03-real.js
```

M03-B batch 결과는 별도 파일로 저장한다.

```bash
BASE_URL=http://<WINDOWS_LAN_IP>:8080/api TARGET_VUS=400 \
  k6 run --summary-export mission-03b-batch-400-summary.json mission-03-real.js
```

M03-C smart batch 결과는 별도 파일로 저장한다.

```bash
BASE_URL=http://<WINDOWS_LAN_IP>:8080/api TARGET_VUS=400 \
  k6 run --summary-export mission-03c-smart-batch-400-summary.json mission-03-real.js
```

결과 파일:

```text
missions/phase-2/tests/k6/mission-03-summary.json
missions/phase-2/tests/k6/mission-03-400-summary.json
missions/phase-2/tests/k6/mission-03b-batch-400-summary.json
missions/phase-2/tests/k6/mission-03c-smart-batch-400-summary.json
```

## 5. Docker 리소스 수집

k6 실행 직전에 Windows Desktop의 WSL2 터미널에서 실행한다.

```bash
./missions/phase-2/scripts/collect-docker-stats.sh 60 1 missions/phase-2/results/mission-03-docker-stats.csv
```

인자 의미:

- `60`: 총 수집 시간, 초 단위
- `1`: 수집 간격, 초 단위
- `missions/phase-2/results/mission-03-docker-stats.csv`: 저장 파일

k6 테스트가 50초이므로 60초 정도 수집하면 ramp-up/ramp-down 구간까지 포함해서 볼 수 있다.

수집 대상 기본값:

- `shortener-app-phase-2`
- `shortener-db-phase-2`

컨테이너명이 다르면 환경변수로 바꿀 수 있다.

```bash
APP_CONTAINER=shortener-app DB_CONTAINER=shortener-db \
  ./missions/phase-2/scripts/collect-docker-stats.sh 60 1 missions/phase-2/results/mission-03-docker-stats.csv
```

## 6. 관측 지표

k6 결과에서 아래 값을 기록한다.

- `http_reqs` rate: RPS
- `http_req_duration avg`
- `http_req_duration p(95)`
- `http_req_failed`
- `checks`

Windows Desktop에서는 테스트 중 아래 명령으로 실시간 상태도 확인할 수 있다.

```bash
docker stats
```

기록할 값:

- App CPU / Memory
- DB CPU / Memory
- 병목으로 보이는 지점

## Notes

- `172.x.x.x` 형태의 IP는 Docker 내부 IP이므로 MacBook에서 직접 접근하는 주소로 쓰지 않는다.
- `192.168.x.x` 형태의 Windows LAN IP를 사용한다.
- k6가 exit code `99`로 종료되어도 요청 실패가 아니라 threshold 초과일 수 있다. `http_req_failed`와 `checks`를 먼저 확인한다.
