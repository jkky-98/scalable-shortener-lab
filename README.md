# 🧪 Scalable URL Shortener Lab: Infrastructure Engineering Log

> **"From Zero to High-Availability"**
>
> 단일 서버에서 대용량 트래픽을 처리하는 분산 시스템으로 진화하는 과정을 기록한 인프라 엔지니어링 실습 로그입니다.
> 단순 기능 구현이 아닌 **리소스 제약 환경에서의 병목 해결(Bottleneck Optimization)**과 **아키텍처 설계**에 집중합니다.

## 🎯 Project Goal
- **Architecture First:** 코드는 단순하게, 인프라는 견고하게.
- **Resource Constraints:** 고사양 장비를 의도적으로 제한하여 극한의 상황을 시뮬레이션.
- **Data Driven:** 감이 아닌 수치(RPS, Latency, CPU %)로 성능을 증명.

## 💻 Lab Environment (Constraints)

실제 프로덕션 환경과 유사한 병목을 유도하기 위해 하드웨어 및 네트워크에 엄격한 제약을 설정했습니다.

### 1. Host Server (Windows Desktop)
- **CPU:** AMD Ryzen 7 9800X3D (8C/16T)
- **RAM:** 32GB DDR5
- **Network:** Wired LAN (1Gbps internal, but limited by ISP/Router bandwidth)
- **Virtualization Strategy (WSL2):**
    - `.wslconfig`를 통해 도커 엔진이 사용할 리소스를 엄격히 제한.
    - **Allocated Limit:** `6 vCPU`, `10GB RAM` (실험실 전체 총량)
    - **Swap:** `4GB`

### 2. Load Generator Client (MacBook Pro)
- **Model:** Apple MacBook Pro (M1 Pro)
- **Network:** Wi-Fi (Wireless Jitter exists)
- **Role:** `k6`를 사용한 부하 발생 및 트래픽 주입.

### 3. Network Bottleneck
- **Bandwidth Limit:** **100Mbps** (Home Network Environment)
- **Theoretical Max RPS:** API 응답이 500Byte일 경우, 물리적 한계는 약 **25,000 RPS**.
- **Challenge:** 네트워크 대역폭이 포화되기 전에 DB나 애플리케이션의 병목을 먼저 찾아내고 튜닝해야 함.

---

## 🛠 Tech Stack

- **Application:** Java 21, Spring Boot 3.x
- **Database:** MySQL 8.0 (Master/Slave Replication)
- **Cache:** Redis (Look Aside Strategy)
- **Infrastructure:** Docker, Docker Compose, WSL2
- **Load Balancer:** Nginx
- **Monitoring:** Prometheus, Grafana, Spring Actuator
- **Testing:** k6 (Load Testing)

---

## 🗺️ Mission Roadmap & Status

상세한 미션 수행 과정과 체크리스트는 **[MISSIONS.md](./MISSIONS.md)** 파일에 기록되어 있습니다.

| Phase | Mission | Status | Key Metric (Example) |
|:---:|:---|:---:|:---|
| **P1** | **[Mission 01](./MISSIONS.md#mission-01-통신-개통-방화벽을-뚫고-hello-world)** : Hello World on Docker | ⬜ | Connectivity Check |
| **P1** | **[Mission 02](./MISSIONS.md#mission-02-기능-구현-docker-compose로-app--db-연동)** : App + DB Setup | ⬜ | Functional Test |
| **P2** | **[Mission 03](./MISSIONS.md#mission-03-부하-측정-single-instance의-한계점-찾기)** : Baseline Stress Test | ⬜ | Max RPS (Single) |
| **P2** | **[Mission 04](./MISSIONS.md#mission-04-인프라-확장-nginx-로드밸런싱과-오버헤드)** : Nginx Load Balancing | ⬜ | Latency Overhead |
| **P3** | **[Mission 05](./MISSIONS.md#mission-05-수평-확장-3중-분신술-scale-out의-효과-검증)** : Scale-Out (x3) | ⬜ | Throughput x3? |
| **P3** | **[Mission 06](./MISSIONS.md#mission-06-장애-시뮬레이션-리소스-제한과-좀비-서버)** : Fault Tolerance | ⬜ | 0% Downtime |
| **P4** | **[Mission 07](./MISSIONS.md#mission-07-캐시-적용-redis-도입과-read-성능-폭발)** : Redis Caching | ⬜ | DB CPU < 5% |
| **P4** | **[Mission 08](./MISSIONS.md#mission-08-캐시-관리-ttl-설정과-정합성-테스트)** : Cache Stampede | ⬜ | Latency Spike |
| **P5** | **[Mission 09](./MISSIONS.md#mission-09-db-이중화-replication과-readwrite-splitting)** : DB Replication | ⬜ | Write/Read Split |
| **P5** | **[Mission 10](./MISSIONS.md#mission-10-최종-리포트-10만-건-처리-데이터-시각화)** : Final Dashboard | ⬜ | **Final Max RPS** |

---

## 📂 Project Structure

```text
scalable-shortener-lab/
├── src/                     # Spring Boot Application (Shared Code)
├── missions/                # Infrastructure as Code (Mission-specific)
│   ├── mission-01/          # Docker Basic
│   ├── mission-04/          # Nginx Settings
│   └── ...
├── tests/                   # k6 Load Test Scripts
├── Dockerfile               # Base Image Builder
└── MISSIONS.md              # Detailed Mission Guide