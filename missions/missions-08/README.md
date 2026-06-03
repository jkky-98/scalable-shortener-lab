# Mission 08. TTL과 Cache Stampede 관측

Mission 08은 Redis read-through cache의 TTL 만료 시점에 cache miss가 몰리면서 DB read spike와 latency spike가 발생하는지 확인한다.

앱 코드는 수정하지 않는다. Mission 06에서 추가한 `SHORTENER_CACHE_TTL_SECONDS` 설정만 사용한다.

## 핵심 질문

```text
TTL이 짧고 hot key 트래픽이 집중되면,
캐시 만료 시점마다 여러 요청이 동시에 MySQL로 떨어지는가?
그 결과 p95 latency와 DB CPU가 주기적으로 튀는가?
```

## 실험 구성

```text
Nginx 0.5 / App 1.0 x3 / Redis 0.5 / DB 1.0 / SMART_BATCH
SHORTENER_CACHE_TTL_SECONDS=10
```

## 서버 실행

Mission 07 compose가 떠 있으면 포트가 겹치므로 먼저 내린다.

```powershell
docker compose -f missions/missions-07/docker-compose.yml down
```

Mission 08 stack을 올린다.

```powershell
$env:NGINX_CPUS="0.5"
$env:APP_CPUS="1.0"
$env:REDIS_CPUS="0.5"
$env:DB_CPUS="1.0"
$env:SHORTENER_CACHE_ENABLED="true"
$env:SHORTENER_CACHE_TTL_SECONDS="10"
$env:SHORTENER_ACCESS_LOG_MODE="SMART_BATCH"
docker compose -f missions/missions-08/docker-compose.yml up -d --build --force-recreate
```

## 테스트 데이터 생성

Mission 08은 별도 DB volume(`mysql-data-mission-08`)을 사용하므로 처음 실행 시 seed가 필요하다.

```bash
cd missions/missions-08/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api python3 seed-and-save.py
```

## Redis 초기화

TTL 패턴을 깨끗하게 보기 위해 측정 직전에 Redis를 비운다.

```powershell
docker exec shortener-redis-mission-08 redis-cli FLUSHALL
```

## M08-A. Hot Key TTL Stampede

모든 VU가 같은 key 1개를 조회한다. TTL 10초가 만료될 때마다 cache miss가 같은 시점에 몰릴 가능성이 가장 크다.

```bash
cd missions/missions-08/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api TARGET_VUS=400 HOLD_DURATION=90s HOT_KEY_COUNT=1 \
  k6 run --out json=mission-08a-hot-key-ttl.json \
  --summary-export mission-08a-hot-key-ttl-summary.json \
  mission-08-hot-key-ttl.js
```

초 단위 latency와 cache miss를 CSV로 변환한다.

```bash
python3 ../../scripts/analyze-k6-json.py \
  mission-08a-hot-key-ttl.json \
  mission-08a-hot-key-ttl-timeseries.csv
```

## M08-B. Random Key TTL 비교

1,000개 key를 랜덤 조회한다. hot key 1개보다 TTL 만료 시점이 분산되는지 비교한다.

```bash
cd missions/missions-08/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api TARGET_VUS=400 HOLD_DURATION=90s \
  k6 run --out json=mission-08b-random-ttl.json \
  --summary-export mission-08b-random-ttl-summary.json \
  mission-08-random-ttl.js
```

```bash
python3 ../../scripts/analyze-k6-json.py \
  mission-08b-random-ttl.json \
  mission-08b-random-ttl-timeseries.csv
```

## Docker stats 수집

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\missions\missions-08\scripts\collect-docker-stats.ps1 150 3 missions/missions-08/results/mission-08a-hot-key-docker-stats.csv
```

Bash:

```bash
./missions/missions-08/scripts/collect-docker-stats.sh 150 3 missions/missions-08/results/mission-08a-hot-key-docker-stats.csv
```

## 판정 기준

- TTL 10초 주기로 `cache_misses`가 증가하는지 확인한다.
- miss가 발생한 초에 `duration_p95_ms` 또는 `duration_max_ms`가 함께 튀는지 확인한다.
- Docker stats에서 DB CPU spike가 같이 나타나는지 확인한다.
- Hot key 1개와 random key 1,000개 조건의 miss 분포와 latency spike를 비교한다.
- spike가 확인되면 현재 read-through cache에는 stampede 방지(single-flight, lock, TTL jitter 등)가 없다는 결론을 기록한다.
