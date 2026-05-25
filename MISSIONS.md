## 📊 Engineering Log (Result Summary)

각 미션을 수행한 후 얻은 데이터를 이곳에 요약 기록합니다.

| Mission ID | Architecture | VUs | RPS | Avg Latency (ms) | P95 Latency (ms) | Fail Rate (%) | DB CPU (%) | Note (Bottleneck) |
|:---:|:---|:---:|:---:|:----------------:|:----------------:|:-------------:|:----------:|:------------------|
| **M-03** | Single App | 200 | 729 |       126        |       298        |      0%       |    54%     | Stable baseline |
| **M-03** | Single App | 400 | 927 |       184        |       564        |      0%       |    57%     | Latency limit around 900 RPS |
| **M-03A** | Async AccessLog | 400 | 477 |       371        |       1187       |      0%       |    101%    | Async write backlog |
| **M-03B** | Batch AccessLog | 400 | 850 |       202        |       685        |      0%       |     -      | Lossless batch, still slower than sync |
| **M-03C** | Smart Batch AccessLog | 400 | 737 |       235        |       793        |      0%       |     -      | Lossless, slower than batch |
| **M-05** | 3 Apps + LB | 150 |  -  |        -         |        -         |       -       |     -      | -                 |
| **M-07** | Redis Cache | 500 |  -  |        -         |        -         |       -       |     -      | -                 |

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
    - M-03C는 M-03B보다 작은 batch size와 짧은 flush interval을 사용했지만, 결과는 M-03B보다 악화됐다.
    - Conclusion: flush burst를 줄이는 방향 자체는 타당한 가설이었지만, 현재 리소스 제한에서는 더 잦은 DB batch write가 read redirect 경로와 더 자주 경쟁하면서 p95를 악화시킨 것으로 해석한다. AccessLog write 경로만 계속 미세 조정하기보다, 다음 단계에서는 read 경로의 DB 의존도를 줄이는 cache 계층 또는 read/write 분리를 검증하는 편이 더 유효하다.

### 🎯 Mission 04. [인프라 확장] "Nginx 로드밸런싱과 오버헤드"
- **Goal:** 리버스 프록시(Nginx) 도입 시 발생하는 네트워크 오버헤드 측정.
- **Architecture:** `Client` -> `Nginx(80)` -> `App(8080)` -> `DB`
- **Hypothesis:** Nginx를 거치면 Hop이 추가되어 Latency가 미세하게 증가할 것이다.
- **Acceptance Criteria:** Mission 3 결과 대비 RPS 감소폭이 10% 이내여야 함 (설정 오류 검증).

---

## Phase 3. 스케일 아웃과 리소스 관리 (Scale-Out)

### 🎯 Mission 05. [수평 확장] "3중 분신술 (Scale-Out)의 효과 검증"
- **Goal:** App 인스턴스를 3배로 늘렸을 때 처리량도 선형적으로 증가하는가?
- **Architecture:** `Nginx` -> `App 1, 2, 3` (Round Robin) -> `MySQL`
- **Key Variable:** DB가 병목이라면 App을 늘려도 RPS는 오르지 않음.
- **Acceptance Criteria:**
    - RPS가 1.5배 이상 증가했는가? (3배 미만일 경우 DB 병목 증명).
    - App 1, 2, 3 로그에서 요청 분산 확인.

### 🎯 Mission 06. [장애 시뮬레이션] "리소스 제한과 좀비 서버"
- **Goal:** 서버 장애 발생 시 Nginx의 Failover 및 무중단 서비스 검증.
- **Condition:** `docker-compose.yml`에서 App 리소스를 `cpus: '0.1'`로 극단적 제한.
- **Action:** 부하 테스트 도중 `docker stop [Container_ID]`로 1대 강제 종료.
- **Acceptance Criteria:**
    - Nginx가 죽은 서버를 제외하고 나머지 2대로 트래픽 라우팅.
    - `502 Bad Gateway` 에러 비율 1% 미만 유지.

---

## Phase 4. 성능 최적화 (Caching Strategy)

### 🎯 Mission 07. [캐시 적용] "Redis 도입과 Read 성능 폭발"
- **Goal:** DB 부하를 제거하고 네트워크 대역폭(100Mbps) 한계까지 성능 끌어올리기.
- **Architecture:** Look Aside (`App` -> `Redis` -> `DB`)
- **Acceptance Criteria:**
    - Mission 3 대비 **RPS 5배 이상** 증가.
    - 부하 테스트 중 MySQL CPU 사용률 **5% 미만** 유지.

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
