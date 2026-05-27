# Mission 04. Nginx 진입점 검증

Mission 04는 M03-D의 최적 단일 백엔드 구성 앞에 Nginx를 추가해도 처리량과 지연 시간이 유지되는지 검증한다.

단순히 Nginx 때문에 latency가 조금 늘어나는지 보는 것이 아니라, Nginx가 외부 요청의 진입점이 되었을 때 새 병목이 되는지 확인한다.

## 비교 구조

같은 App/DB 컨테이너를 두 경로로 비교한다.

```text
직접 접근: Client -> App(8080) -> DB
Nginx 경유: Client -> Nginx(80) -> App(8080) -> DB
```

기준선은 M03-D smart batch 결과다.

```text
M03-D SMART_BATCH + App 1.0 / DB 0.5
3332 RPS / avg 43ms / p95 91ms / fail 0%
```

## 서버 실행

Windows Desktop에서 포트 `80`, `8080`, `3307`을 이미 사용 중인 컨테이너가 있으면 먼저 내린다.

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

Windows Desktop의 WSL2 터미널:

```bash
APP_CPUS=1.0 DB_CPUS=0.5 NGINX_CPUS=0.25 SHORTENER_ACCESS_LOG_MODE=SMART_BATCH \
  docker compose -f missions/missions-04/docker-compose.yml up -d --build --force-recreate
```

PowerShell:

```powershell
$env:APP_CPUS="1.0"
$env:DB_CPUS="0.5"
$env:NGINX_CPUS="0.25"
$env:SHORTENER_ACCESS_LOG_MODE="SMART_BATCH"
docker compose -f missions/missions-04/docker-compose.yml up -d --build --force-recreate
```

기본 포트:

- Nginx 경유: `80`
- App 직접 접근: `8080`
- MySQL host port: `3307`

포트가 겹치면 `NGINX_PORT`, `APP_PORT`, `DB_PORT`로 바꾼다.

## 헬스체크

Windows Desktop:

```bash
curl http://localhost/api/hello
curl http://localhost:8080/api/hello
```

MacBook:

```bash
curl http://<WINDOWS_LAN_IP>/api/hello
curl http://<WINDOWS_LAN_IP>:8080/api/hello
```

## 테스트 데이터 생성

처음 실행하면 Mission 04 전용 DB volume(`mysql-data-mission-04`)이 비어 있으므로 seed를 다시 만든다.

```bash
cd missions/missions-04/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api python3 seed-and-save.py
```

## k6 부하 테스트

Nginx 경유:

```bash
cd missions/missions-04/tests/k6
BASE_URL=http://<WINDOWS_LAN_IP>/api TARGET_VUS=400 \
  k6 run --summary-export mission-04-nginx-smart-batch-400-summary.json mission-04.js
```

App 직접 접근:

```bash
BASE_URL=http://<WINDOWS_LAN_IP>:8080/api TARGET_VUS=400 \
  k6 run --summary-export mission-04-direct-smart-batch-400-summary.json mission-04.js
```

## Docker stats 수집

Nginx, App, DB를 함께 수집한다.

```bash
./missions/missions-04/scripts/collect-docker-stats.sh 120 1 missions/missions-04/results/mission-04-docker-stats.csv
```

## 판정 기준

- Nginx 경유 RPS 감소폭이 App 직접 접근 대비 10% 이내인지 확인한다.
- Nginx 경유 p95가 M03-D 기준선인 91ms에서 크게 악화되는지 확인한다.
- Nginx CPU가 먼저 한계에 붙는지 확인한다.
- AccessLog row 증가량이 k6 요청 수와 일치하는지 확인한다.
