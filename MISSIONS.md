## 📊 Engineering Log (Result Summary)

각 미션을 수행한 후 얻은 데이터를 이곳에 요약 기록합니다.

| Mission ID | Architecture | VUs | RPS | Avg Latency (ms) | P95 Latency (ms) | Fail Rate (%) | DB CPU (%) | Note (Bottleneck) |
|:---:|:---|:---:|:---:|:----------------:|:----------------:|:-------------:|:----------:|:------------------|
| **M-03** | Single App | 200 | 729 |       126        |       298        |      0%       |    54%     | Stable baseline |
| **M-03** | Single App | 400 | 927 |       184        |       564        |      0%       |    57%     | Latency limit around 900 RPS |
| **M-03A** | Async AccessLog | 400 | 477 |       371        |       1187       |      0%       |    101%    | Async write backlog |
| **M-03B** | Batch AccessLog | 400 | 850 |       202        |       685        |      0%       |     -      | Lossless batch, still slower than sync |
| **M-03C** | Smart Batch AccessLog | 400 | 737 |       235        |       793        |      0%       |    14%     | App CPU saturated, slower than batch |
| **M-03D** | Smart Batch + Rebalanced CPU | 400 | 3332 |        43        |        91        |      0%       |    51%     | App/DB CPU limits both utilized |
| **M-03D** | Sync + Rebalanced CPU | 400 | 794 |       218        |       661        |      0%       |    53%     | DB write wait dominates |
| **M-04** | App Direct + SMART_BATCH | 400 | 3460 |        41        |        92        |      0%       |     -      | M04 direct baseline |
| **M-04** | Nginx 경유 + SMART_BATCH | 400 | 3266 |        45        |       105        |      0%       |    48%     | 5.6% RPS drop, accepted |
| **M-05A** | App x3 Total CPU Increase | 400 | 3667 |        38        |        83        |      0%       |    51%     | DB CPU limit after scale-out |
| **M-05B** | App x3 Fixed Total App CPU | 400 | 827 |       209        |       1100       |      0%       |    20%     | App CPU quota dominates |
| **M-05C** | Nginx CPU Sensitivity | 400 | 3678 |        38        |        87        |      0%       |    53%     | Nginx 0.25 CPU limit visible |
| **M-05D** | DB CPU Sensitivity | 400 | 4326 |        31        |        61        |      0%       |    63%     | DB CPU increase improved throughput |
| **M-06A** | Redis Cache (ramp) | 400 | 4210 |        32        |        64        |      0%       |    13%     | 99.52% hit, read load moved to Redis |
| **M-06A** | Redis Cache (400 VU sustain) | 400 | 5572 |        52        |        80        |      0%       |    11%     | 100% hit, DB read effectively removed |

---

## Phase 1. 환경 구축 및 베이스라인 (The Baseline)

### 🎯 Mission 01. [통신 개통] "방화벽을 뚫고 Hello World"
- **Goal:** 이기종 환경(Mac Client ↔ Windows Server) 간의 네트워크 통신 성공.
- **Architecture:** `MacBook(Wi-Fi)` -> `Router` -> `Windows(Host)` -> `Docker Container`
- **Checklist:**
    - [x] `.wslconfig` 설정 확인 (Memory 10GB, Processors 6).
    - [x] Spring Boot `GET /api/hello` 구현.
    - [x] Windows 방화벽 인바운드 규칙(8080) 개방.
    - [x] **Verification:** Mac 터미널에서 `curl -v http://[WIN_IP]:8080/api/hello` 성공.

### 🎯 Mission 02. [기능 구현] "Docker Compose로 App + DB 연동"
- **Goal:** `docker-compose up` 명령 하나로 App과 DB를 동시에 실행하고 비즈니스 로직 검증.
- **Specs:**
    - `POST /api/shorten`: URL 단축 및 DB 저장.
    - `GET /api/{key}`: 원본 URL 리다이렉트 (`302 Found`).
- **Constraints:**
    - MySQL Container Resource: `cpus: 1.0`, `memory: 1G` 제한.
- **Verification:** Postman/Curl을 이용해 단축키 생성 후 실제 리다이렉트 동작 확인.

---

## Phase 2. 한계 측정 (Stress Testing)

### 🎯 Mission 03. [부하 측정] "Single Instance의 한계점 찾기"
- **Goal:** 서버 1대일 때의 최대 처리량(Max RPS)과 병목 지점 확인.
- **Tools:** `k6` (Run on MacBook)
- **Scenario:**
    - Read Only (`GET`) 테스트.
    - VUs: 10 -> 50 -> 100 -> 500 (Ramp-up strategy).
- **Metric Check:**
    - `docker stats` 명령어로 Windows 서버의 DB CPU/Memory 실시간 모니터링.
    - **Note:** Wi-Fi 환경이므로 `http_req_duration`보다 `http_req_waiting` 지표를 신뢰할 것.
- **Acceptance Criteria:** DB CPU가 100%에 도달하거나 응답 속도가 200ms를 초과하는 정확한 RPS 지점 찾기.
- **Result (2026-05-23):**
    - 200 VU: 729 RPS, avg 126ms, p95 298ms, fail 0%.
    - 400 VU: 927 RPS, avg 184ms, p95 564ms, fail 0%.
    - Conclusion: Single App + Single MySQL의 지연 시간 기준 한계는 400 VU, 약 900 RPS 부근이다. CPU/Memory가 100%에 닿기 전 p95가 먼저 악화되므로 병목은 CPU 고갈보다 DB 동기 write, DB connection 대기, Tomcat worker 대기 등 queueing으로 추정한다.

### 🎯 Mission 03-A. [병목 분해] "AccessLog 동기 Write 분리"
- **Goal:** `GET /api/{key}`의 응답 경로에서 `access_logs` DB write가 p95 latency에 미치는 영향을 분리 측정.
- **Constraint:** AccessLog는 프로젝트 요구사항이므로 제거하지 않는다. 단, 설정으로 `sync`와 `async` write 방식을 전환한다.
- **Hypothesis:**
    - 현재 redirect 경로는 `short_urls SELECT` 후 `access_logs INSERT`를 같은 요청 안에서 수행한다.
    - 400 VU에서 CPU/Memory가 100%에 닿기 전 p95가 악화된 원인은 동기 DB write, DB connection 대기, Tomcat worker 대기 등 queueing일 가능성이 높다.
    - AccessLog write를 비동기로 분리하면 기능 요구사항은 유지하면서 redirect p95가 감소할 것이다.
- **Experiment:**
    - Baseline: `SHORTENER_ACCESS_LOG_MODE=sync`
    - Variant: `SHORTENER_ACCESS_LOG_MODE=async`
    - Same Load: `TARGET_VUS=400`
- **Acceptance Criteria:**
    - async 모드에서도 `access_logs` row가 DB에 저장되는지 확인.
    - 테스트 종료 후 executor drain 시간을 둔 뒤 k6 요청 수와 `access_logs` 증가량을 비교.
    - 400 VU 기준 p95 latency가 Mission 03 baseline 대비 개선되는지 확인.
    - App/DB CPU, Memory, PIDs를 함께 기록해 병목 위치를 설명.
- **Result (2026-05-25):**
    - 400 VU async: 477 RPS, avg 371ms, p95 1187ms, fail 0%.
    - `access_logs` row count 확인: 106,726 rows. async 모드에서도 DB 저장 경로는 동작했다.
    - Docker stats 발췌상 테스트 종료 직후 DB CPU가 82% -> 101%까지 상승했다. 이는 async executor가 로그 write를 제거한 것이 아니라 큐에 적체한 뒤 DB에 밀어 넣는 패턴으로 해석한다.
    - Conclusion: 단순 `@Async` 기반 AccessLog 저장은 현재 리소스 제한 환경에서 sync baseline보다 악화됐다. DB write 자체가 병목 후보이며, 다음 개선은 batch insert, bounded backpressure, 별도 write buffer, Redis Stream/Kafka 같은 완충 계층을 검토해야 한다.

### 🎯 Mission 03-B. [병목 개선] "AccessLog Batch Writer"
- **Goal:** AccessLog DB 저장 요구사항을 유지하면서 요청당 `INSERT + commit` 구조를 batch write 구조로 변경해 p95 latency를 개선한다.
- **Architecture:** `GET /api/{key}` -> in-memory queue -> batch writer thread -> `JdbcTemplate.batchUpdate`.
- **Constraint:**
    - AccessLog는 DB에 저장되어야 한다.
    - 큐가 가득 차면 로그를 버리지 않고 요청 스레드가 대기한다.
    - 메모리 큐 기반이므로 프로세스 장애 시 아직 flush되지 않은 로그는 유실될 수 있다.
- **Experiment:**
    - Baseline: M-03 400 VU sync result.
    - Failed Variant: M-03A 400 VU async result.
    - Batch Variant: `SHORTENER_ACCESS_LOG_MODE=batch`, `TARGET_VUS=400`.
    - Default batch settings: queue capacity 20000, batch size 500, flush interval 100ms.
- **Acceptance Criteria:**
    - 테스트 전후 `access_logs` row count 차이가 k6 request count와 일치하거나, drain 대기 후 수렴하는지 확인.
    - 400 VU 기준 p95 latency가 M-03A async보다 개선되는지 확인.
    - 가능하면 M-03 sync baseline 대비 p95도 개선되는지 확인.
    - App/DB CPU, Memory, PIDs를 함께 기록해 batch write가 DB backlog를 줄였는지 설명.
- **Result (2026-05-25):**
    - 400 VU batch: 850 RPS, avg 202ms, p95 685ms, fail 0%.
    - `access_logs` row count: 106,726 -> 149,248. 증가량 42,522 rows가 k6 request count 42,522와 일치해 로그 유실은 없었다.
    - Batch writer는 M-03A async보다 개선됐지만 M-03 sync baseline보다 느렸다.
    - Conclusion: batch write는 단순 async backlog 문제를 완화했지만, 현재 설정(batch size 500, flush interval 100ms)에서는 sync baseline을 넘지 못했다. AccessLog 병목은 DB write/commit 비용뿐 아니라 queue 대기, flush burst, read/write connection 경쟁까지 함께 고려해야 한다.

### 🎯 Mission 03-C. [Batch 튜닝] "Smart AccessLog Batch Writer"
- **Goal:** M-03B의 batch write 구조를 유지하되, flush burst와 queue contention을 줄여 400 VU p95 latency를 개선한다.
- **Architecture:** `GET /api/{key}` -> lock-free queue -> bounded backpressure -> size/time triggered batch writer -> `JdbcTemplate.batchUpdate`.
- **Constraint:**
    - AccessLog는 DB에 저장되어야 한다.
    - 큐가 가득 차면 로그를 버리지 않고 요청 스레드가 대기한다.
    - 메모리 큐 기반이므로 프로세스 장애 시 아직 flush되지 않은 로그는 유실될 수 있다.
- **Hypothesis:**
    - M-03B는 batch size 500, flush interval 100ms로 한 번에 큰 write burst가 발생할 수 있다.
    - 더 작은 batch size와 더 짧은 flush interval을 사용하면 DB write를 더 고르게 분산해 read redirect 요청과 write batch 간 경쟁이 줄어들 것이다.
    - `ArrayBlockingQueue` 대신 `ConcurrentLinkedQueue + Semaphore`로 queue 경합을 낮추면 요청 스레드의 queue 진입 비용도 줄어들 것이다.
- **Experiment:**
    - Baseline: M-03 400 VU sync result.
    - Previous Variant: M-03B 400 VU batch result.
    - Smart Batch Variant: `SHORTENER_ACCESS_LOG_MODE=SMART_BATCH`, `TARGET_VUS=400`.
    - Default smart batch settings: queue capacity 20000, batch size 200, flush interval 50ms.
- **Acceptance Criteria:**
    - 테스트 전후 `access_logs` row count 차이가 k6 request count와 일치하거나, drain 대기 후 수렴하는지 확인.
    - 400 VU 기준 p95 latency가 M-03B batch 결과보다 개선되는지 확인.
    - 가능하면 M-03 sync baseline 대비 p95도 개선되는지 확인.
    - App/DB CPU, Memory, PIDs를 함께 기록해 smart batch가 flush burst를 줄였는지 설명.
- **Result (2026-05-25):**
    - 400 VU smart batch: 737 RPS, avg 235ms, p95 793ms, fail 0%.
    - `access_logs` row count: 149,248 -> 186,090. 증가량 36,842 rows가 k6 request count 36,842와 일치해 로그 유실은 없었다.
    - Docker stats 기준 App CPU는 테스트 중 48~50% 부근에 계속 머물렀고, App memory는 최대 약 349MiB/512MiB(68%)까지 증가했다. DB CPU는 대부분 6~14% 수준, DB memory는 약 384MiB/1GiB(38%)로 낮았다.
    - M-03C는 M-03B보다 작은 batch size와 짧은 flush interval을 사용했지만, 결과는 M-03B보다 악화됐다.
    - Conclusion: flush burst를 줄이는 방향 자체는 타당한 가설이었지만, 현재 리소스 제한에서는 DB가 아니라 App CPU quota와 queue/write orchestration overhead가 더 큰 병목으로 나타났다. AccessLog write 경로만 계속 미세 조정하기보다, 다음 단계에서는 read 경로의 DB 의존도를 줄이는 cache 계층 또는 read/write 분리를 검증하는 편이 더 유효하다.

### 🎯 Mission 03-D. [리소스 재분배] "App 1.0 CPU / DB 0.5 CPU"
- **Goal:** M-03C의 smart batch 악화가 구조 자체의 문제인지, App CPU quota 부족 때문인지 확인한다.
- **Architecture:** Single App + Single MySQL은 유지하되 CPU limit만 `App 0.5 / DB 1.0`에서 `App 1.0 / DB 0.5`로 재분배한다.
- **Hypothesis:**
    - M-03C에서 DB CPU는 낮고 App CPU가 0.5 CPU limit에 붙었으므로, App CPU를 늘리면 smart batch의 queue/write orchestration overhead를 더 잘 감당할 수 있다.
    - DB CPU를 0.5로 줄여도 short URL read와 AccessLog insert가 단순하므로 즉시 병목이 되지 않을 수 있다.
- **Experiment:**
    - Resource Variant: `APP_CPUS=1.0`, `DB_CPUS=0.5`.
    - Writer Variant 1: `SHORTENER_ACCESS_LOG_MODE=sync`, `TARGET_VUS=400`.
    - Writer Variant 2: `SHORTENER_ACCESS_LOG_MODE=SMART_BATCH`, `TARGET_VUS=400`.
- **Acceptance Criteria:**
    - `docker inspect`로 App `NanoCpus=1000000000`, DB `NanoCpus=500000000` 적용 여부를 확인한다.
    - sync와 smart batch를 같은 리소스 조건에서 비교한다.
    - p95 latency가 M-03C 또는 M-03 sync baseline보다 개선되는지 확인한다.
- **Result (2026-05-25, SMART_BATCH):**
    - 400 VU smart batch with rebalanced CPU: 3332 RPS, avg 43ms, p95 91ms, fail 0%.
    - `access_logs` row count: 224,052 -> 390,708. 증가량 166,656 rows가 k6 request count 166,656과 일치해 로그 유실은 없었다.
    - Docker stats 기준 App CPU는 테스트 중 약 96~101%까지 상승했고, DB CPU도 약 47~51%까지 상승했다. 이는 App 1.0 CPU와 DB 0.5 CPU 제한이 모두 실제로 사용됐음을 보여준다.
    - App memory는 최대 약 350MiB/512MiB(68%), DB memory는 최대 약 407MiB/1GiB(40%)로 메모리 병목은 아니었다.
- **Result (2026-05-25, SYNC):**
    - 400 VU sync with rebalanced CPU: 794 RPS, avg 218ms, p95 661ms, fail 0%.
    - `access_logs` row count: 390,708 -> 430,407. 증가량 39,699 rows가 k6 request count 39,699와 일치해 로그 유실은 없었다.
    - Docker stats 기준 초반에는 App CPU가 약 99~101%까지 상승했지만, 중후반에는 DB CPU가 약 49~53%로 DB 0.5 CPU 제한에 붙으면서 App CPU가 약 45~55% 수준까지 내려갔다.
    - Conclusion: 리소스 재분배 조건에서는 sync보다 smart batch가 압도적으로 우수했다. sync는 요청 스레드가 매 요청마다 AccessLog DB insert 완료를 기다리기 때문에 DB 0.5 CPU 제한에서 p95가 661ms까지 악화됐다. 반면 smart batch는 요청 경로에서 write wait를 제거하고 batch writer가 DB CPU를 효율적으로 사용해 3332 RPS, p95 91ms를 달성했다. M-03D 기준 최적 구조는 `App 1.0 CPU / DB 0.5 CPU + SMART_BATCH`다.

### 🎯 Mission 04. [진입점 검증] "Nginx를 앞단에 두어도 최적 구조가 유지되는가"
- **Goal:** M-03D에서 찾은 최적 단일 백엔드 구조(`App 1.0 CPU / DB 0.5 CPU + SMART_BATCH`) 앞에 Nginx를 추가했을 때, Nginx가 새 병목이 되는지 확인한다.
- **Architecture:**
    - App 직접 접근: `Client` -> `App(8080)` -> `DB`
    - Nginx 경유: `Client` -> `Nginx(80)` -> `App(8080)` -> `DB`
- **Hypothesis:**
    - Nginx keepalive 설정이 충분하면 단순 프록시 경유 비용은 제한적일 것이다.
    - Nginx CPU limit이 너무 작으면 App/DB보다 Nginx가 먼저 병목이 될 수 있다.
    - Mission 04의 핵심은 Nginx 자체의 미세한 latency 증가가 아니라, 외부 요청 진입점을 Nginx로 바꿔도 M-03D 최적 구조가 유지되는지 검증하는 것이다.
- **Experiment:**
    - Resource Variant: `NGINX_CPUS=0.25`, `APP_CPUS=1.0`, `DB_CPUS=0.5`.
    - Writer Variant: `SHORTENER_ACCESS_LOG_MODE=SMART_BATCH`.
    - App 직접 접근 기준선: `BASE_URL=http://<WINDOWS_LAN_IP>:8080/api`, `TARGET_VUS=400`.
    - Nginx 경유 실험: `BASE_URL=http://<WINDOWS_LAN_IP>/api`, `TARGET_VUS=400`.
- **Acceptance Criteria:**
    - Nginx 경유 RPS 감소폭이 App 직접 접근 대비 10% 이내인지 확인한다.
    - Nginx 경유 p95 latency가 M-03D 기준선인 91ms에서 크게 악화되는지 확인한다.
    - Docker stats로 Nginx, App, DB 중 어느 컨테이너가 먼저 CPU limit에 닿는지 확인한다.
    - AccessLog row 증가량이 k6 request count와 일치하는지 확인한다.
- **Result (2026-05-27):**
    - App 직접 접근 400 VU: 3460 RPS, avg 41ms, p95 92ms, fail 0%.
    - Nginx 경유 400 VU: 3266 RPS, avg 45ms, p95 105ms, fail 0%.
    - Nginx 경유 시 App 직접 접근 대비 RPS 감소폭은 약 5.6%로 acceptance 기준인 10% 이내였다.
    - AccessLog row count는 510,552 rows였다. Mission 04에서 수동 302 확인 2건, App 직접 접근 173,128건, 첫 Nginx 경유 측정 174,036건, stats와 맞춘 Nginx 재측정 163,386건을 합산한 값과 일치해 로그 유실은 없었다.
    - Docker stats 기준 Nginx 경유 재측정 중 App CPU는 최대 약 89%, DB CPU는 최대 약 48%, Nginx CPU는 최대 약 26%까지 상승했다. Nginx는 0.25 CPU 제한에 근접했지만, 처리량 감소폭과 p95 증가는 허용 범위였다.
    - App memory는 최대 약 421MiB/512MiB(82%), DB memory는 약 430MiB/1GiB(42%), Nginx memory는 약 10MiB/128MiB(8%) 수준이었다.
    - Conclusion: 400 VU 조건에서 Nginx를 외부 요청 진입점으로 추가해도 M-03D 최적 구조는 유지된다. Nginx 경유 비용은 관측됐지만 새 병목으로 보기는 어렵다. 다만 Nginx CPU가 0.25 CPU 제한에 가까워졌으므로, 다음 Mission 05에서 여러 App으로 확장할 때는 Nginx CPU도 함께 조정해야 한다.

---

## Phase 3. 스케일 아웃과 리소스 관리 (Scale-Out)

### 🎯 Mission 05. [수평 확장] "App 3개 확장이 실제 처리량을 올리는가"
- **Goal:** M-04의 Nginx 진입점 구조를 유지하면서 App 인스턴스를 3개로 늘렸을 때, 처리량 증가 원인이 수평 확장인지 리소스 총량 증가인지 분리해서 확인한다.
- **Architecture:** `Client` -> `Nginx(80)` -> `App 1, 2, 3` -> `MySQL`
- **Baseline:** M-04 Nginx 경유 결과: 3266 RPS, avg 45ms, p95 105ms, fail 0%.
- **Key Variables:**
    - App 인스턴스 수: 1 -> 3.
    - 총 App CPU: 증가시키는 경우와 M-04와 비슷하게 고정하는 경우를 분리한다.
    - Nginx CPU: App 3개 앞에서 Nginx가 새 병목이 되는지 확인한다.
    - DB CPU: App 처리 능력 증가 후 병목이 DB write로 이동하는지 확인한다.
- **Prerequisite:**
    - Base62 단축키는 대소문자를 모두 사용하므로 `short_urls.short_key`는 case-sensitive collation이어야 한다. MySQL 기본 collation이 case-insensitive이면 `FZ`와 `Fz`를 같은 값으로 판단해 unique index 충돌이 발생한다.
- **Mission 05-A. App 총 CPU 증가:**
    - `NGINX_CPUS=0.5`, `APP_CPUS=1.0`, `DB_CPUS=0.5`.
    - App 3개 각각 1 CPU를 부여해 총 App CPU를 늘린다.
    - M-04 대비 RPS가 의미 있게 증가하는지 확인한다.
- **Mission 05-B. App 총 CPU 고정:**
    - `NGINX_CPUS=0.5`, `APP_CPUS=0.33`, `DB_CPUS=0.5`.
    - 총 App CPU를 M-04와 비슷하게 유지하고 인스턴스 수만 늘린다.
    - 성능이 좋아지지 않으면 M05-A의 향상은 수평 확장 자체보다 CPU 총량 증가 영향이 크다고 해석한다.
- **Mission 05-C. Nginx CPU 병목 확인:**
    - `NGINX_CPUS=0.25`와 `NGINX_CPUS=0.5`를 비교한다.
    - App 3개 앞에서 Nginx가 먼저 CPU limit에 닿는지 확인한다.
- **Mission 05-D. DB CPU 병목 확인:**
    - `DB_CPUS=0.5`와 `DB_CPUS=1.0`을 비교한다.
    - App 확장 이후 병목이 DB로 이동하는지 확인한다.
- **Acceptance Criteria:**
    - M05-A RPS가 M-04 Nginx 경유 결과 대비 의미 있게 증가하는지 확인한다.
    - M05-B로 총 App CPU 고정 조건에서도 수평 확장의 이득이 있는지 확인한다.
    - Docker stats로 Nginx, App 1/2/3, DB 중 어느 컨테이너가 먼저 CPU limit에 닿는지 확인한다.
    - Nginx 응답 헤더 `X-Upstream-Addr` 또는 컨테이너 stats로 요청 분산을 확인한다.
    - AccessLog row 증가량이 k6 request count와 일치하는지 확인한다.
- **Result (M05-A, 2026-05-27):**
    - 400 VU, `NGINX_CPUS=0.5`, `APP_CPUS=1.0 x3`, `DB_CPUS=0.5`, `SMART_BATCH`.
    - 3666.7 RPS, avg 38.5ms, p95 83.2ms, fail 0%.
    - `access_logs` 증가량 183,405 rows가 k6 request count 183,405와 일치했다.
    - Docker stats 기준 Nginx는 약 28~36%, App 3개는 초반 70~80% 피크 후 분산, DB는 약 49~51%로 DB 0.5 CPU limit에 붙었다.
    - Conclusion: App 3개 확장은 M-04 Nginx 경유 기준 3266 RPS 대비 약 12.3% 향상됐다. 단, App 처리 능력이 늘면서 다음 병목은 DB 0.5 CPU로 이동했다.
- **Result (M05-B, 2026-05-28):**
    - 400 VU, `NGINX_CPUS=0.5`, `APP_CPUS=0.33 x3`, `DB_CPUS=0.5`, `SMART_BATCH`.
    - 826.6 RPS, avg 209.3ms, p95 1.1s, fail 0%. 성능 threshold `p95<500ms`는 실패했다.
    - `access_logs` 증가량 41,337 rows가 k6 request count 41,337과 일치했다.
    - Docker stats 기준 Nginx는 약 9~11%, DB는 약 18~20%였고, App 3개가 각각 약 32~35%에 붙었다.
    - Conclusion: 총 App CPU를 M-04와 비슷하게 고정하면 App 3개 분산만으로는 성능 이득이 없었다. M05-A의 개선은 수평 확장 자체보다 총 App CPU 증가 효과가 지배적이었다.
- **Result (M05-C, 2026-05-28):**
    - 400 VU, `NGINX_CPUS=0.25`, `APP_CPUS=1.0 x3`, `DB_CPUS=1.0`, `SMART_BATCH`.
    - 3677.7 RPS, avg 38.4ms, p95 87.2ms, fail 0%.
    - `access_logs` 증가량 183,888 rows가 k6 request count 183,888과 일치했다.
    - Docker stats summary 기준 Nginx max 24.99%, App max 90.56~99.01%, DB max 52.53%, memory max는 모두 제한 내에 있었다.
    - Conclusion: Nginx CPU를 0.5에서 0.25로 줄이면 M05-D 대비 RPS가 약 15.0% 하락하고 p95가 61.1ms에서 87.2ms로 악화됐다. Nginx 0.25 CPU는 처리 가능하지만 limit에 닿아 성능 영향이 있다.
- **Result (M05-D, 2026-05-28):**
    - 400 VU, `NGINX_CPUS=0.5`, `APP_CPUS=1.0 x3`, `DB_CPUS=1.0`, `SMART_BATCH`.
    - 4326.3 RPS, avg 31.0ms, p95 61.1ms, fail 0%.
    - `access_logs` 증가량 216,359 rows가 k6 request count 216,359와 일치했다.
    - Docker stats summary 기준 Nginx max 35.18%, App max 83.47~100.9%, DB max 63.21%, memory max는 모두 제한 내에 있었다.
    - Conclusion: DB CPU를 0.5에서 1.0으로 늘리자 M05-A 대비 RPS는 약 18.0% 증가하고 p95는 83.2ms에서 61.1ms로 개선됐다. M05-A의 DB CPU 병목 가설은 타당했다.

---

## Phase 4. 성능 최적화 (Caching Strategy)

### 🎯 Mission 06. [캐시 적용] "Redis가 Read 병목을 제거하는가"
- **Goal:** M-05에서 확인한 scale-out 한계를 바탕으로, `GET /api/{key}`의 `short_urls` read 경로를 Redis cache로 옮겼을 때 병목이 어떻게 이동하는지 확인한다.
- **Baseline:** M-05D 결과: `Nginx 0.5 / App 1.0 x3 / DB 1.0 / SMART_BATCH`, 4326 RPS, avg 31ms, p95 61ms, fail 0%.
- **Architecture:** `Client` -> `Nginx` -> `App 1, 2, 3` -> `Redis` -> `MySQL`
- **Constraint:**
    - AccessLog DB write는 기존 요구사항이므로 유지한다.
    - Cache hit/miss를 측정할 수 있어야 한다.
    - Cache miss 시에는 MySQL에서 원본 URL을 읽고 Redis에 저장한다.
- **Mission 06-A. Redis Read-through Cache 도입:**
    - `GET /api/{key}`에서 `short_urls` 조회를 Redis 우선 조회로 변경한다.
    - Cache hit이면 Redis 값으로 즉시 redirect하고, cache miss이면 MySQL 조회 후 Redis에 저장한다.
    - AccessLog 저장 방식은 `SMART_BATCH`를 유지한다.
- **Acceptance Criteria:**
    - M-05D 대비 RPS 또는 p95가 의미 있게 개선되는지 확인한다.
    - Redis cache hit ratio를 기록한다.
    - AccessLog row 증가량이 k6 request count와 일치하는지 확인한다.
    - Docker stats로 Nginx, App 1/2/3, Redis, DB 중 어느 컨테이너가 먼저 CPU limit에 닿는지 확인한다.
    - Cache hit 조건에서도 fail rate 0%를 유지한다.
- **Result (M06-A, 2026-05-31, short ramp):**
    - 400 VU, `NGINX_CPUS=0.5`, `APP_CPUS=1.0 x3`, `REDIS_CPUS=0.5`, `DB_CPUS=1.0`, `SMART_BATCH`.
    - 4210.2 RPS, avg 32.2ms, p95 63.5ms, fail 0%.
    - Cache hit ratio는 99.52%였다. `shortener_cache_hit` 209,535건, `shortener_cache_miss` 1,000건, cache error/bypass 0건이었다.
    - `short_urls`는 1,000 rows, `access_logs`는 210,537 rows였다. k6 요청 210,535건과 수동 캐시 확인 2건이 모두 저장되어 로그 유실은 없었다.
    - Docker stats summary 기준 Redis max CPU 10.29%, DB max CPU 12.53%, Nginx max CPU 41.40%, App max CPU 76.24~80.81%였다.
    - M05-D의 같은 short ramp 기준 결과인 4326 RPS, p95 61.1ms와 비교하면 처리량 자체는 개선되지 않았다. 따라서 M05-D 시점의 주 병목은 `short_urls` read만은 아니었다.
- **Result (M06-A, 2026-05-31, 400 VU sustain):**
    - 10초 ramp-up, 60초 400 VU 유지, 10초 ramp-down 조건으로 재측정했다.
    - 5571.8 RPS, avg 52.3ms, p95 80.0ms, fail 0%.
    - Cache hit ratio는 100%였다. seed된 1,000개 short key가 이미 Redis에 적재된 warm cache 상태였으므로, redirect 요청의 `short_urls` read는 사실상 MySQL이 아니라 Redis에서 처리됐다.
    - Docker stats summary 기준 Redis max CPU 8.37%, DB max CPU 10.51%, Nginx max CPU 36.98%, App max CPU 29.60~38.85%였다. 서버 컨테이너 어느 쪽도 CPU limit에 붙지 않았다.
    - Conclusion: Redis read-through cache는 정상 동작했고, cache hit가 99.52~100%에 도달하면서 `short_urls` read 부하를 MySQL에서 Redis로 거의 완전히 이전했다. 그 결과 DB CPU는 M05-D의 max 63.21%에서 M06-A의 10~13% 수준으로 크게 낮아졌다. 다만 RPS가 즉시 크게 증가하지는 않았으므로, Redis의 1차 효과는 처리량 폭증보다 DB read 부하 제거와 DB 리소스 여유 확보로 해석하는 것이 타당하다.
- **Mission 06 종료 판단:**
    - M06-B(Redis CPU 민감도)와 M06-C(DB CPU 민감도)는 별도 진행하지 않는다.
    - 이번 조건에서는 seed 데이터가 1,000개뿐이고 warm cache hit ratio가 99.52~100%에 도달했기 때문에, Redis/DB CPU를 더 줄이는 실험은 결과가 예측 가능하고 학습 가치가 낮다.
    - 캐시에서 더 중요한 변수는 Redis 자체 속도가 아니라 hit ratio, TTL, key cardinality, hot/cold key 분포, cache miss burst, cache stampede다.
    - 따라서 Mission 06은 "read-through cache를 도입해 `short_urls` read 부하를 MySQL에서 Redis로 이전했다"는 결론으로 종료하고, TTL과 stampede 성격의 캐시 품질 검증은 Mission 08에서 다룬다.

### 🎯 Mission 07. [장애 시뮬레이션] "Redis 포함 구조에서 일부 App 장애를 견디는가"
- **Goal:** Redis cache까지 포함한 성능 개선 구조에서 App 인스턴스 장애 발생 시 Nginx가 정상 인스턴스로 트래픽을 우회하는지 확인한다.
- **Baseline:** Mission 06에서 확정한 Redis cache 구조.
- **Condition:** 부하 테스트 도중 App 컨테이너 1대를 중지한다.
- **Design Note:** 앱 코드는 수정하지 않는다. 장애는 애플리케이션 예외가 아니라 컨테이너 중단으로 발생시킨다.
- **Architecture:** `Client` -> `Nginx(passive failover)` -> `App 1, 2, 3` -> `Redis` -> `MySQL`
- **Nginx failover setting:**
    - upstream 서버별 `max_fails=1`, `fail_timeout=5s`.
    - `proxy_next_upstream error timeout http_502 http_503 http_504`.
    - `proxy_next_upstream_tries 3`.
- **Action:** k6 400 VU sustain 진행 중 `docker stop shortener-app1-mission-07`로 App 1대를 중지한다.
- **Acceptance Criteria:**
    - Nginx가 죽은 App을 제외하고 나머지 App으로 트래픽을 라우팅한다.
    - 장애 시점의 fail rate와 p95 spike를 기록한다.
    - 전체 테스트 기준 `502 Bad Gateway` 또는 요청 실패율 1% 미만을 유지한다.
    - AccessLog row 증가량이 성공 request count와 일치하는지 확인한다.

### 🎯 Mission 08. [캐시 관리] "TTL 설정과 정합성 테스트"
- **Goal:** 캐시 만료(TTL) 시 발생하는 DB 스파이크(Cache Stampede) 관측.
- **Condition:** Redis TTL을 10초로 설정.
- **Verification:** k6 그래프에서 10초 주기로 튀는 Latency 패턴(톱니바퀴 모양) 캡처 및 분석.

---

## Phase 5. 고가용성 아키텍처 (High Availability)

### 🎯 Mission 09. [DB 이중화] "Replication과 Read/Write Splitting"
- **Goal:** 쓰기(Master)와 읽기(Slave) 부하 분리.
- **Architecture:**
    - `Master DB` (Write)
    - `Slave DB` (Read)
- **Verification:**
    - Spring `@Transactional(readOnly=true)` 라우팅 동작 확인.
    - Master DB 컨테이너 중지 시에도 `GET` 요청 성공 확인.

### 🎯 Mission 10. [최종 리포트] "10만 건 처리 데이터 시각화"
- **Goal:** 대시보드를 통해 최종 아키텍처의 안정성 증명.
- **Tools:** `Prometheus` + `Grafana`
- **Action:**
    - k6 VUs 200명, 10분간 지속 부하 테스트.
    - Grafana에서 RPS, JVM Heap, DB Connection Pool 그래프 캡처.
- **Final Deliverable:** "이 아키텍처는 100Mbps 환경에서 최대 OOO RPS를 처리 가능하며, 주 병목은 OOO이다." 결론 도출.
