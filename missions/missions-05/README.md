# Mission 05. 3개 App 수평 확장 검증

Mission 05는 M04의 Nginx 진입점 구조를 유지하면서 App 인스턴스를 3개로 늘렸을 때 처리량이 어떻게 변하는지 측정한다.

핵심은 "App을 3개로 늘리면 빨라지는가"만 보는 것이 아니라, 성능 변화가 총 CPU 증가 때문인지, 인스턴스 분산 때문인지, 혹은 Nginx/DB가 새 병목이 되는지 분리해서 확인하는 것이다.

## 비교 기준

M04 Nginx 경유 결과:

```text
Nginx 0.25 / App 1.0 x1 / DB 0.5 / SMART_BATCH
3266 RPS / avg 45ms / p95 105ms / fail 0%
```

## 하위 실험

### M05-A. App 총 CPU 증가

```text
Nginx 0.5 / App 1.0 x3 / DB 0.5 / SMART_BATCH
```

App 인스턴스와 총 App CPU를 모두 늘린다. M04 대비 RPS가 증가하는지 확인한다.

### M05-B. App 총 CPU 고정

```text
Nginx 0.5 / App 0.33 x3 / DB 0.5 / SMART_BATCH
```

총 App CPU를 M04와 비슷하게 유지한 상태에서 인스턴스 수만 늘리는 실험이다. 성능이 좋아지지 않으면, M05-A의 향상은 수평 확장 자체보다 CPU 총량 증가 영향이 크다고 볼 수 있다.

### M05-C. Nginx CPU 병목 확인

```text
Nginx 0.25 vs 0.5 / App 1.0 x3 / DB 0.5 / SMART_BATCH
```

M04에서 Nginx 0.25 CPU가 한계에 가까워졌으므로, App 3개 앞에서 Nginx가 먼저 병목이 되는지 확인한다.

### M05-D. DB CPU 병목 확인

```text
Nginx 0.5 / App 1.0 x3 / DB 0.5 vs 1.0 / SMART_BATCH
```

App 처리 능력을 늘렸을 때 병목이 DB write로 이동하는지 확인한다.

## 서버 실행

Windows Desktop에서 포트 `80`, `8081`, `8082`, `8083`, `3308`을 이미 사용 중인 컨테이너가 있으면 먼저 내린다.

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

### M05-A

PowerShell:

```powershell
$env:NGINX_CPUS="0.5"
$env:APP_CPUS="1.0"
$env:DB_CPUS="0.5"
$env:SHORTENER_ACCESS_LOG_MODE="SMART_BATCH"
docker compose -f missions/missions-05/docker-compose.yml up -d --build --force-recreate
```

WSL2 bash:

```bash
NGINX_CPUS=0.5 APP_CPUS=1.0 DB_CPUS=0.5 SHORTENER_ACCESS_LOG_MODE=SMART_BATCH \
  docker compose -f missions/missions-05/docker-compose.yml up -d --build --force-recreate
```

### M05-B

```powershell
$env:NGINX_CPUS="0.5"
$env:APP_CPUS="0.33"
$env:DB_CPUS="0.5"
$env:SHORTENER_ACCESS_LOG_MODE="SMART_BATCH"
docker compose -f missions/missions-05/docker-compose.yml up -d --build --force-recreate
```

### M05-C

Nginx CPU만 바꿔서 다시 올린다.

```powershell
$env:NGINX_CPUS="0.25"
docker compose -f missions/missions-05/docker-compose.yml up -d --build --force-recreate
```

### M05-D

DB CPU만 바꿔서 다시 올린다.

```powershell
$env:DB_CPUS="1.0"
docker compose -f missions/missions-05/docker-compose.yml up -d --build --force-recreate
```

## 헬스체크와 분산 확인

Nginx 경유:

```bash
curl -i http://localhost/api/hello
```

개별 App 직접 접근:

```bash
curl http://localhost:8081/api/hello
curl http://localhost:8082/api/hello
curl http://localhost:8083/api/hello
```

Nginx 응답 헤더의 `X-Upstream-Addr`를 여러 번 확인하면 요청이 App 3개로 분산되는지 볼 수 있다.

```bash
for i in {1..9}; do curl -s -D - http://localhost/api/hello -o /dev/null | grep X-Upstream-Addr; done
```

## 테스트 데이터 생성

처음 실행하면 Mission 05 전용 DB volume(`mysql-data-mission-05`)이 비어 있으므로 seed를 다시 만든다.
Base62 단축키는 대소문자를 모두 사용하므로 `short_urls.short_key`는 case-sensitive collation이어야 한다. 기존 볼륨에서 `Duplicate entry 'Fz'` 같은 seed 실패가 나면 아래 보정 SQL을 한 번 실행한 뒤 seed를 다시 수행한다.

```bash
docker exec shortener-db-mission-05 mysql -uroot -proot shortener -e "alter table short_urls modify short_key varchar(10) character set utf8mb4 collate utf8mb4_bin;"
```

```bash
cd missions/missions-05/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api python3 seed-and-save.py
```

## k6 부하 테스트

```bash
cd missions/missions-05/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api TARGET_VUS=400 \
  k6 run --summary-export mission-05a-scale-out-400-summary.json mission-05.js
```

파일명은 하위 실험에 맞게 바꾼다.

```text
mission-05a-scale-out-400-summary.json
mission-05b-fixed-total-app-cpu-400-summary.json
mission-05c-nginx-025-400-summary.json
mission-05d-db-1cpu-400-summary.json
```

## Docker stats 수집

PowerShell:

```powershell
.\missions\missions-05\scripts\collect-docker-stats.ps1 120 1 missions/missions-05/results/mission-05a-docker-stats.csv
```

Bash:

```bash
./missions/missions-05/scripts/collect-docker-stats.sh 120 1 missions/missions-05/results/mission-05a-docker-stats.csv
```

## 판정 기준

- M05-A가 M04 Nginx 경유 결과인 3266 RPS를 의미 있게 넘는지 확인한다.
- M05-B로 총 App CPU를 고정했을 때도 이득이 있는지 확인한다.
- Nginx, DB, 각 App 중 어느 컨테이너가 먼저 CPU limit에 닿는지 확인한다.
- Nginx 응답 헤더 또는 컨테이너 stats로 요청이 App 3개에 분산되는지 확인한다.
- AccessLog row 증가량이 k6 request count와 일치하는지 확인한다.
