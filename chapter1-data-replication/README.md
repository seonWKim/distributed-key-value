# Chapter 1: Data Replication

This chapter focuses on building the foundational blocks of **reliable and consistent data replication** across distributed nodes. You will implement patterns commonly used in real-world distributed databases and consensus systems like Raft, Etcd, and Cassandra.

---

## 🎯 What We Will Learn

In this chapter, you'll gain hands-on understanding of:

- ✅ **Write-Ahead Logging (WAL)** for durability and replay
- ✅ **Leader-Follower Replication** and when to use quorum
- ✅ **Log Watermarks** (Low/High) for tracking replication state
- ✅ **Heartbeats** for liveness detection and log coordination
- ✅ **Generational and Lamport Clocks** for operation ordering
- ✅ **Replicated Logs** and consistency guarantees
- ✅ **RAFT Consensus Algorithm** fundamentals
- ✅ **Request Pipeline and Batching** for efficient async communication
- ✅ **Idempotency** in networked systems
- ✅ **Versioning and Version Vectors** to track conflicts and causality

---

## 🛠 What We Will Implement

You will **implement and test** the following components and behaviors:

### ✅ Core Mechanisms

- **Write-Ahead Log (WAL)**  
  A persistent, append-only log that captures every write operation before applying it to memory.

- **Low Watermark & Log Cleanup**  
  Safely remove old log entries replicated to all followers.

- **High Watermark**  
  Track the highest log entry that has been successfully replicated to a quorum of nodes.

- **Leader-Follower Role Architecture**
  - A single leader accepts writes.
  - Followers replicate logs passively.
  - Simple **leader election** with state transitions.

- **Heartbeats**
  - Sent from leader to followers.
  - Optional: Explore **gossip-based** heartbeats in larger clusters.

- **RAFT-like Coordination (Intro Level)**  
  Implement the key aspects of RAFT including:
  - Log replication
  - Leadership
  - Commit confirmation

### ✅ Advanced Mechanics

- **Generational Clock**  
  Resettable clock to distinguish between leadership epochs.

- **Lamport Clock**  
  Logical timestamp for ordering events across nodes.

- **Replicated Logs**  
  Ensure log consistency between leader and followers.

- **Idempotent Operation Handling**  
  Prevent duplicate application of commands after retries.

- **Request Pipelining & Batching**  
  Enable multiple outstanding requests and batch them for better throughput.

- **Version Vectors** and **LWW (Last Writer Wins)** conflict resolution.

---

## 📘 How We Should Learn This Chapter

This chapter is divided into incremental exercises that build on top of each other:

1. **Start with a Single Node WAL**
  - Focus on durability and recovery.

2. **Introduce Leader-Follower Replication**
  - Track watermarks and log syncing.

3. **Add Clocks and Versioning**
  - Build Lamport Clock and simple vector versioning.

4. **RAFT Fundamentals**
  - Focus on core idea: log replication and leadership.

5. **Enhance with Pipelining, Idempotency, and Gossip**
  - Improve performance and reliability.

6. **Test Everything with Failure Scenarios**
  - Crash leader, retry writes, restart follower.

---

## 🧪 Suggested Tools & Practices

- Use **JUnit** for testing log integrity and replication.
- Mock follower nodes to simulate network behavior.
- Use in-memory + file-based logs to test durability.
- Add logs/metrics to understand internal behavior.

---

## 🚀 Outcome

After completing this chapter, you will be able to:

- Implement a simplified version of **log replication and consensus**
- Explain core RAFT behaviors and why they work
- Build robust logging and communication pipelines for distributed systems
- Understand the importance of idempotency and versioning in eventual consistency
