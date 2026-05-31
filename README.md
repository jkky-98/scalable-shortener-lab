# 🧪 Scalable URL Shortener Lab: Infrastructure Engineering Log

> **"A local performance lab for learning how bottlenecks move."**
>
> 단순한 URL shortener를 대상으로 부하 테스트를 반복하며, 아키텍처 변경에 따라 병목이 어떻게 이동하는지 기록하는 백엔드 성능 실험 프로젝트입니다.
> 목표는 운영 성능을 보증하는 것이 아니라, 같은 로컬 환경 안에서 **상대 성능**, **p95 latency**, **CPU quota**, **DB write/read 병목**, **scale-out의 한계**를 수치로 관찰하는 것입니다.

## ⚠️ What This Lab Is, and Is Not

이 프로젝트는 집/개인 장비에서 수행하는 로컬 실험실입니다. 따라서 결과 수치를 그대로 운영 환경의 처리량 보증으로 해석하면 안 됩니다.

- **This is a learning lab:** 같은 네트워크, 같은 장비, 같은 부하 시나리오에서 구조를 하나씩 바꾸며 상대 비교를 합니다.
- **This is not a production benchmark:** Docker Desktop, WSL2, 공유기, Wi-Fi, 단일 MySQL, 작은 데이터셋의 영향을 받습니다.
- **The important output is diagnosis:** "몇 RPS가 나왔다"보다 "왜 p95가 악화됐는가", "어느 컨테이너가 CPU limit에 닿았는가", "로그 유실이 있었는가"를 더 중요하게 봅니다.
- **Claims are scoped:** 이 레포의 결과는 "이 로컬 제한 환경에서 관찰된 병목 이동"을 설명합니다. 클라우드, Kubernetes, bare metal 운영 성능은 별도 검증이 필요합니다.

## 🎯 Project Goal
- **Bottleneck First:** 기능보다 요청 경로의 병목을 찾고 설명하는 데 집중합니다.
- **Resource Constraints:** Docker CPU/Memory limit으로 작은 서버 환경을 만들고, 제한이 성능에 미치는 영향을 관찰합니다.
- **Data Driven:** 감이 아니라 RPS, avg/p95 latency, CPU %, fail rate, row count로 판단합니다.
- **Honest Results:** 성공한 실험뿐 아니라 느려진 실험도 기록합니다. 실패한 실험이 병목 이해에 더 큰 단서를 줄 때가 많습니다.

## 💻 Lab Environment (Constraints)

반복 가능한 상대 비교를 위해 하드웨어 및 네트워크 조건을 고정하고, Docker resource limit으로 실험별 리소스를 조정합니다.

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
- **Interpretation:** 네트워크, Docker Desktop, OS scheduling의 영향을 받을 수 있으므로 절대 수치보다 실험 간 변화량을 더 신뢰합니다.

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
| **P2** | **[Mission 04](./MISSIONS.md#mission-04-진입점-검증-nginx를-앞단에-두어도-최적-구조가-유지되는가)** : Nginx Entrypoint | ⬜ | Proxy Overhead |
| **P3** | **[Mission 05](./MISSIONS.md#mission-05-수평-확장-app-3개-확장이-실제-처리량을-올리는가)** : Scale-Out (x3) | ⬜ | Resource Sensitivity |
| **P4** | **[Mission 06](./MISSIONS.md#mission-06-캐시-적용-redis가-read-병목을-제거하는가)** : Redis Caching | ⬜ | Cache Hit Ratio |
| **P4** | **[Mission 07](./MISSIONS.md#mission-07-장애-시뮬레이션-redis-포함-구조에서-일부-app-장애를-견디는가)** : Fault Tolerance | ⬜ | Failover Behavior |
| **P4** | **[Mission 08](./MISSIONS.md#mission-08-캐시-관리-ttl-설정과-정합성-테스트)** : Cache Stampede | ⬜ | Latency Spike |
| **P5** | **[Mission 09](./MISSIONS.md#mission-09-db-이중화-replication과-readwrite-splitting)** : DB Replication | ⬜ | Write/Read Split |
| **P5** | **[Mission 10](./MISSIONS.md#mission-10-최종-리포트-10만-건-처리-데이터-시각화)** : Final Dashboard | ⬜ | **Final Max RPS** |

---

## 📂 Project Structure

```text
scalable-shortener-lab/
├── src/                     # Spring Boot Application (Shared Code)
├── missions/                # Infrastructure as Code (Mission-specific)
│   ├── phase-1/             # Mission 01-02 baseline compose
│   ├── phase-2/             # Mission 03 load-test assets
│   ├── missions-04/         # Nginx entrypoint experiment
│   ├── missions-05/         # App scale-out experiment
│   ├── missions-06/         # Redis cache experiment
│   └── ...
├── Dockerfile               # Base Image Builder
└── MISSIONS.md              # Detailed Mission Guide
