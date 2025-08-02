## 📚 Implemented Patterns (분산 시스템 이야기)

### ✅ 분산 시스템의 약속과 위험
Exploring the trade-offs of availability, consistency, and partition tolerance based on the CAP theorem.

---

### 🧩 패턴 개요
This project is organized around practical implementations of known distributed system patterns categorized below.

---

## 🔁 데이터 복제 패턴 (Data Replication)

- **쓰기 전 로그 (Write-Ahead Log)**  
  Ensures durability by persisting writes before applying them to the data store.
- **분할 로그 (Segmented Log)**  
  Supports efficient log compaction and management by splitting the log into segments.
- **로우 워터마크 (Low Watermark)**  
  Indicates the minimum acknowledged index for replication.
- **하이 워터마크 (High Watermark)**  
  Marks the highest safely committed index.
- **리더-팔로워 (Leader-Follower)**  
  A replication strategy where one node accepts writes and followers replicate logs.
- **하트비트 (Heartbeat)**  
  Regular pings from leader to followers to assert leadership and detect failures.
- **과반수 정족수 (Quorum/Majority Acknowledgment)**  
  Commits are only accepted when acknowledged by a majority of nodes.
- **세대 시계 (Epoch Clock)**  
  Used to identify leadership generations and prevent stale operations.
- **팍소스 (Paxos)** *(optional)*  
  A consensus algorithm to safely agree on a value in a distributed system.
- **복제 로그 (Replicated Log)**  
  Keeps logs consistent across replicas for deterministic state machines.
- **단일 갱신 큐 (Single Update Queue)**  
  Ensures operations are serialized at the leader before replication.
- **요청 대기 목록 (Pending Request Queue)**  
  Buffers client requests while waiting for replication/commit.
- **멱등 수신자 (Idempotent Receiver)**  
  Handles retries without applying the same operation twice.
- **팔로워 읽기 (Follower Read)**  
  Enables stale reads from followers to improve read scalability.
- **버전화 값 (Versioned Value)**  
  Associates a version with each value for consistency and conflict resolution.
- **버전 벡터 (Version Vector)**  
  Tracks causality and helps detect conflicting updates across replicas.

---

## 📦 데이터 파티션 (Data Partitioning)

- **고정 파티션 (Static Partitioning)**  
  Manually assigns key ranges to nodes.
- **키 범위 파티션 (Key Range Partitioning)**  
  Partitions data by key ranges (e.g., A–F, G–L).
- **2단계 커밋 (Two-Phase Commit)**  
  Ensures consistency across partitions during distributed transactions.

---

## ⏱️ 분산 시간 패턴 (Distributed Time Patterns)

- **램포트 시계 (Lamport Clock)**  
  Orders events in distributed systems without synchronized clocks.
- **하이브리드 시계 (Hybrid Logical Clock)**  
  Combines physical and logical clocks for better timestamp accuracy.
- **시계 제한 대기 (Clock-Bound Delay)**  
  Adds artificial delay to ensure causality under clock drift.

---

## 🛠 클러스터 관리 패턴 (Cluster Management)

- **일관성 코어 (Consistency Core)**  
  A subset of nodes managing writes and coordination.
- **리스 (Lease)**  
  Time-bound ownership of leadership or resource access.
- **상태 감시 (Health Monitoring)**  
  Tracks node health to detect failures or recovery.
- **가십 전파 (Gossip Dissemination)**  
  Spreads node and cluster state in a decentralized fashion.
- **자생적 리더 (Self-Elected Leader)**  
  Nodes can autonomously initiate leader election upon detection of failure.

---

## 📡 노드 간 통신 패턴 (Inter-Node Communication)

- **단일 소켓 채널 (Single Socket Channel)**  
  Uses one communication channel per peer to simplify state.
- **묶음 요청 (Batch Requesting)**  
  Groups multiple requests into a single message to reduce overhead.
- **요청 파이프라인 (Request Pipelining)**  
  Enables concurrent requests over the same connection without waiting.

