# Mission 07. App 장애 시뮬레이션

Mission 07은 Mission 06에서 확정한 Redis cache 포함 구조에서 App 인스턴스 1대가 중단되어도 Nginx가 나머지 App으로 트래픽을 우회하는지 확인한다.

앱 코드는 수정하지 않는다. 장애는 컨테이너 중단으로 발생시킨다.

## 비교 기준

Mission 06-A sustain 결과:

```text
Nginx 0.5 / App 1.0 x3 / Redis 0.5 / DB 1.0 / SMART_BATCH
5572 RPS / avg 52ms / p95 80ms / fail 0% / cache hit 100%
```

## 서버 실행

Mission 06 compose가 떠 있으면 포트가 겹치므로 먼저 내린다. volume은 유지된다.

```powershell
docker compose -f missions/missions-06/docker-compose.yml down
```

Mission 07 stack을 올린다.

```powershell
$env:NGINX_CPUS="0.5"
$env:APP_CPUS="1.0"
$env:REDIS_CPUS="0.5"
$env:DB_CPUS="1.0"
$env:SHORTENER_CACHE_ENABLED="true"
$env:SHORTENER_ACCESS_LOG_MODE="SMART_BATCH"
docker compose -f missions/missions-07/docker-compose.yml up -d --build --force-recreate
```

## 테스트 데이터 생성

Mission 07은 별도 DB volume(`mysql-data-mission-07`)을 사용하므로 처음 실행 시 seed가 필요하다.

```bash
cd missions/missions-07/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api python3 seed-and-save.py
```

캐시를 warm-up하려면 부하 테스트 전 짧게 한 번 조회하거나, 같은 테스트를 한 번 예열로 돌린다.

## 장애 주입

k6 실행 후 30초 정도 지난 시점에 App 1대를 중지한다.

```powershell
docker stop shortener-app1-mission-07
```

더 강한 장애는 `docker kill`로 별도 실험한다. 기본 실험은 해석이 깔끔한 `docker stop`을 사용한다.

## k6 부하 테스트

```bash
cd missions/missions-07/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api TARGET_VUS=400 HOLD_DURATION=90s \
  k6 run --summary-export mission-07-app1-stop-400-summary.json mission-07-failover.js
```

## Docker stats 수집

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\missions\missions-07\scripts\collect-docker-stats.ps1 150 3 missions/missions-07/results/mission-07-app1-stop-docker-stats.csv
```

Bash:

```bash
./missions/missions-07/scripts/collect-docker-stats.sh 150 3 missions/missions-07/results/mission-07-app1-stop-docker-stats.csv
```

## 판정 기준

- 전체 fail rate가 1% 미만인지 확인한다.
- 502 Bad Gateway 비율이 1% 미만인지 확인한다.
- 장애 시점의 p95 spike를 기록한다.
- app1 중단 후 app2/app3 CPU가 증가하는지 확인한다.
- 성공 요청 수와 `access_logs` 증가량이 일치하는지 확인한다.
