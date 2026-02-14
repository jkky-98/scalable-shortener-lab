## 📊 Engineering Log (Result Summary)

각 미션을 수행한 후 얻은 데이터를 이곳에 요약 기록합니다.

| Mission ID | Architecture | VUs | RPS | Avg Latency (ms) | P95 Latency (ms) | Fail Rate (%) | DB CPU (%) | Note (Bottleneck) |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| **M-03** | Single App | 50 | - | - | - | - | - | - |
| **M-05** | 3 Apps + LB | 150 | - | - | - | - | - | - |
| **M-07** | Redis Cache | 500 | - | - | - | - | - | - |

---

## Phase 1. 환경 구축 및 베이스라인 (The Baseline)

### 🎯 Mission 01. [통신 개통] "방화벽을 뚫고 Hello World"
- **Goal:** 이기종 환경(Mac Client ↔ Windows Server) 간의 네트워크 통신 성공.
- **Architecture:** `MacBook(Wi-Fi)` -> `Router` -> `Windows(Host)` -> `Docker Container`
- **Checklist:**
    - [ ] `.wslconfig` 설정 확인 (Memory 10GB, Processors 6).
    - [ ] Spring Boot `GET /api/hello` 구현.
    - [ ] Windows 방화벽 인바운드 규칙(8080) 개방.
    - [ ] **Verification:** Mac 터미널에서 `curl -v http://[WIN_IP]:8080/api/hello` 성공.

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