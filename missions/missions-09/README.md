# Mission 09. DB Replication과 Read/Write Splitting

Mission 09는 MySQL primary/replica replication을 구성하고, Spring의 read-only transaction을 기준으로 읽기 트래픽이 replica로 이동하는지 확인한다.

## 핵심 질문

```text
short_urls read를 replica로 보냈을 때 primary write 병목이 줄어드는가?
Redis를 함께 켰을 때 replica read 부하까지 사라지는가?
```

## 실험 구분

- **M09-A:** DB replication infra validation. primary에 쓴 데이터가 replica로 복제되는지 확인한다.
- **M09-B:** Redis OFF. `GET /api/{key}`의 DB read가 replica로 라우팅되는지 확인한다.
- **M09-C:** Redis ON. `short_urls` read가 Redis hit로 흡수되면서 DB split 구조의 실제 부하가 어떻게 바뀌는지 확인한다.

## 구조

```text
Client -> Nginx -> App 1,2,3
                  ├─ write transaction -> MySQL primary
                  ├─ read-only transaction -> MySQL replica
                  └─ cache enabled 시 Redis -> miss only read-only DB lookup
```

앱 라우팅은 `SHORTENER_DATASOURCE_ROUTING_ENABLED=true`일 때만 켜진다. 기본값은 `false`라서 Mission 01~08의 단일 DB 구성은 유지된다.

## 서버 실행

이전 미션 compose가 떠 있으면 포트가 겹치므로 먼저 내린다.

```powershell
docker compose -f missions/missions-08/docker-compose.yml down
```

M09-B는 Redis를 끈 DB split 기준선으로 시작한다.

```powershell
$env:NGINX_CPUS="0.5"
$env:APP_CPUS="1.0"
$env:REDIS_CPUS="0.5"
$env:DB_PRIMARY_CPUS="1.0"
$env:DB_REPLICA_CPUS="1.0"
$env:SHORTENER_CACHE_ENABLED="false"
$env:SHORTENER_ACCESS_LOG_MODE="SMART_BATCH"
docker compose -f missions/missions-09/docker-compose.yml up -d --build --force-recreate
```

Replication 연결은 DB 컨테이너가 healthy가 된 뒤 한 번 실행한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\missions\missions-09\scripts\setup-replication.ps1
```

`SHOW REPLICA STATUS\G` 출력에서 `Replica_IO_Running: Yes`, `Replica_SQL_Running: Yes`를 확인한다.

## M09-A. Replication 확인

primary에 테스트 row를 쓰고 replica에서 보이는지 확인한다.

```powershell
docker exec shortener-db-primary-mission-09 mysql -uroot -proot -D shortener -e "CREATE TABLE IF NOT EXISTS replication_probe (id BIGINT PRIMARY KEY AUTO_INCREMENT, marker VARCHAR(64) NOT NULL); INSERT INTO replication_probe(marker) VALUES ('m09-probe');"
docker exec shortener-db-replica-mission-09 mysql -uroot -proot -D shortener -e "SELECT COUNT(*) AS replicated_rows FROM replication_probe;"
```

## 테스트 데이터 생성

```bash
cd missions/missions-09/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api python3 seed-and-save.py
```

seed는 `/api/shorten` write라서 primary에 저장되고, replica로 복제되어야 한다.

## M09-B. DB Read/Write Split, Redis OFF

```bash
cd missions/missions-09/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api TARGET_VUS=400 HOLD_DURATION=60s \
  k6 run --summary-export mission-09b-read-split-no-cache-summary.json \
  mission-09-read-split.js
```

기대값:

- `shortener_cache_bypass`가 100%에 가깝다.
- `short_urls` read는 replica로 간다.
- `access_logs` write와 `/api/shorten` write는 primary로 간다.

## M09-C. DB Split + Redis ON

Redis를 비우고 cache enabled로 stack을 재생성한다.

```powershell
$env:SHORTENER_CACHE_ENABLED="true"
$env:SHORTENER_CACHE_TTL_SECONDS="3600"
docker compose -f missions/missions-09/docker-compose.yml up -d --build --force-recreate
powershell -ExecutionPolicy Bypass -File .\missions\missions-09\scripts\setup-replication.ps1
docker exec shortener-redis-mission-09 redis-cli FLUSHALL
```

```bash
cd missions/missions-09/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api TARGET_VUS=400 HOLD_DURATION=60s \
  k6 run --summary-export mission-09c-read-split-redis-summary.json \
  mission-09-read-split.js
```

기대값:

- 초반 miss 이후 `shortener_cache_hit`가 99% 이상으로 올라간다.
- replica read 부하는 M09-B보다 낮아진다.
- primary는 여전히 AccessLog write를 처리한다.

## Docker stats 수집

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\missions\missions-09\scripts\collect-docker-stats.ps1 120 3 missions/missions-09/results/mission-09b-docker-stats.csv
```

Bash:

```bash
./missions/missions-09/scripts/collect-docker-stats.sh 120 3 missions/missions-09/results/mission-09b-docker-stats.csv
```

## Row count 확인

```powershell
docker exec shortener-db-primary-mission-09 mysql -uroot -proot -D shortener -e "SELECT COUNT(*) AS short_urls FROM short_urls; SELECT COUNT(*) AS access_logs FROM access_logs;"
docker exec shortener-db-replica-mission-09 mysql -uroot -proot -D shortener -e "SELECT COUNT(*) AS short_urls FROM short_urls; SELECT COUNT(*) AS access_logs FROM access_logs;"
```

## 판정 기준

- M09-A에서 primary write가 replica로 복제된다.
- M09-B에서 Redis OFF 상태의 redirect는 `X-Shortener-Cache: BYPASS`를 반환한다.
- M09-B Docker stats에서 replica CPU/IO가 read 부하를 받는지 확인한다.
- M09-C에서 cache hit ratio가 높아지고 replica 부하가 M09-B보다 낮아지는지 확인한다.
- AccessLog row 증가량이 k6 successful request count와 일치한다.
