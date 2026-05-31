# Mission 06. Redis Read-through Cache 검증

Mission 06은 M05-D 구조에 Redis를 추가해 `GET /api/{key}`의 `short_urls` read 경로를 캐시로 옮겼을 때 처리량과 병목이 어떻게 바뀌는지 측정한다.

## 비교 기준

M05-D 결과:

```text
Nginx 0.5 / App 1.0 x3 / DB 1.0 / SMART_BATCH
4326 RPS / avg 31ms / p95 61ms / fail 0%
```

## 하위 실험

### M06-A. Redis read-through cache 도입

```text
Nginx 0.5 / App 1.0 x3 / Redis 0.5 / DB 1.0 / SMART_BATCH
```

`GET /api/{key}`는 Redis를 먼저 조회한다. 캐시 hit이면 Redis 값으로 바로 redirect하고, miss이면 MySQL에서 읽은 뒤 Redis에 TTL과 함께 저장한다.

### M06-B. Redis CPU 병목 확인

```text
Redis 0.25 vs 0.5
```

Redis CPU를 낮췄을 때 RPS와 p95가 악화되는지 확인한다.

### M06-C. Cache 이후 DB CPU 민감도 확인

```text
DB 0.5 vs 1.0
```

캐시 hit 비율이 높아진 뒤에도 DB가 병목인지, 아니면 AccessLog batch write만 남는지 확인한다.

## 서버 실행

Windows Desktop에서 포트 `80`, `8081`, `8082`, `8083`, `3308`, `6379`를 이미 사용 중인 컨테이너가 있으면 먼저 내린다.

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

### M06-A

PowerShell:

```powershell
$env:NGINX_CPUS="0.5"
$env:APP_CPUS="1.0"
$env:REDIS_CPUS="0.5"
$env:DB_CPUS="1.0"
$env:SHORTENER_CACHE_ENABLED="true"
$env:SHORTENER_ACCESS_LOG_MODE="SMART_BATCH"
docker compose -f missions/missions-06/docker-compose.yml up -d --build --force-recreate
```

WSL2 bash:

```bash
NGINX_CPUS=0.5 APP_CPUS=1.0 REDIS_CPUS=0.5 DB_CPUS=1.0 \
SHORTENER_CACHE_ENABLED=true SHORTENER_ACCESS_LOG_MODE=SMART_BATCH \
  docker compose -f missions/missions-06/docker-compose.yml up -d --build --force-recreate
```

### M06-B

Redis CPU만 바꿔서 다시 올린다.

```powershell
$env:REDIS_CPUS="0.25"
docker compose -f missions/missions-06/docker-compose.yml up -d --build --force-recreate
```

### M06-C

DB CPU만 바꿔서 다시 올린다.

```powershell
$env:DB_CPUS="0.5"
docker compose -f missions/missions-06/docker-compose.yml up -d --build --force-recreate
```

## 헬스체크와 캐시 확인

Nginx 경유:

```bash
curl -i http://localhost/api/hello
```

캐시 상태 헤더 확인:

```bash
curl -s -D - http://localhost/api/<KEY> -o /dev/null
```

응답 헤더 `X-Shortener-Cache` 값은 `HIT`, `MISS`, `BYPASS`, `ERROR` 중 하나다.

개별 App의 로컬 캐시 통계:

```bash
curl http://localhost:8081/api/cache/stats
curl http://localhost:8082/api/cache/stats
curl http://localhost:8083/api/cache/stats
```

## 테스트 데이터 생성

처음 실행하면 Mission 06 전용 DB volume(`mysql-data-mission-06`)이 비어 있으므로 seed를 다시 만든다.

```bash
cd missions/missions-06/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api python3 seed-and-save.py
```

Redis를 cold cache 상태로 재측정하려면 Redis를 비운다.

```bash
docker exec shortener-redis-mission-06 redis-cli FLUSHALL
```

## k6 부하 테스트

```bash
cd missions/missions-06/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api TARGET_VUS=400 \
  k6 run --summary-export mission-06a-redis-400-summary.json mission-06.js
```

파일명은 하위 실험에 맞게 바꾼다.

```text
mission-06a-redis-400-summary.json
mission-06b-redis-025-400-summary.json
mission-06c-db-05-400-summary.json
```

`shortener_cache_hit` rate가 cache hit ratio다. 초반 miss를 포함하므로 seed key 수가 작을수록 빠르게 1.0에 가까워진다.

## Docker stats 수집

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\missions\missions-06\scripts\collect-docker-stats.ps1 120 3 missions/missions-06/results/mission-06a-docker-stats.csv
```

Bash:

```bash
./missions/missions-06/scripts/collect-docker-stats.sh 120 3 missions/missions-06/results/mission-06a-docker-stats.csv
```

## 판정 기준

- M06-A가 M05-D 기준선인 4326 RPS 또는 p95 61ms를 의미 있게 개선하는지 확인한다.
- k6 `shortener_cache_hit` rate로 cache hit ratio를 기록한다.
- Nginx, App 1/2/3, Redis, DB 중 어느 컨테이너가 먼저 CPU limit에 닿는지 확인한다.
- AccessLog row 증가량이 k6 request count와 일치하는지 확인한다.
- fail rate 0%를 유지한다.
