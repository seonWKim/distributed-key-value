# Distributed Key Value Store

A hands-on project for learning and implementing distributed computing concepts through a key-value store system. This project is structured as a progressive learning journey through five chapters, each focusing on a specific aspect of distributed systems.

## Purpose

This project provides practical experience with distributed computing concepts such as:
- Data replication and consistency
- Data partitioning and sharding
- Distributed time and ordering
- Cluster management and coordination
- Inter-node communication

## Project Structure

The project is organized into five chapters, each building on the concepts of the previous ones:

1. **Chapter 1: Data Replication** - Focuses on reliable and consistent data replication
2. **Chapter 2: Data Partitioning** - Explores efficient and scalable data partitioning strategies
3. **Chapter 3: Distributed Time** - Implements time and ordering mechanisms for distributed systems
4. **Chapter 4: Cluster Management** - Builds robust cluster management and coordination mechanisms
5. **Chapter 5: Inter-Node Communication** - Develops efficient and reliable communication between nodes

Each chapter is a separate module with its own implementation and README.md file detailing the specific concepts and components.

## Features by Chapter

### Chapter 1: Data Replication

- Write-Ahead Logging (WAL) for durability and replay
- Leader-Follower Replication and quorum-based consistency
- Log Watermarks for tracking replication state
- Heartbeats for liveness detection
- Generational and Lamport Clocks for operation ordering
- RAFT Consensus Algorithm fundamentals

### Chapter 2: Data Partitioning

- Hash-based and Range-based Partitioning
- Partition Mapping to cluster nodes
- Coordinator Nodes for routing client requests
- Two-Phase Commit (2PC) for atomic cross-partition operations
- Consistent Hashing for minimizing rebalancing
- Hot Spot Detection and Mitigation

### Chapter 3: Distributed Time

- Logical, Lamport, and Vector Clocks for event ordering
- Version Vectors for conflict detection and resolution
- Hybrid Logical Clocks combining physical and logical time
- Generational Clocks for leadership epochs
- Causal Consistency using logical time

### Chapter 4: Cluster Management

- Lease-based Resource Management
- Change Notification Systems for configuration updates
- Gossip Protocols for information dissemination
- Spontaneous Leader Election
- Failure Detection mechanisms
- Service Discovery and Health Monitoring

### Chapter 5: Inter-Node Communication

- Single TCP Socket Channel for efficient communication
- Request Batching and Pipelining
- Asynchronous Communication patterns
- Flow Control and Backpressure handling
- Protocol Buffers for efficient serialization
- Fault Tolerance in communication

## Requirements

- Java 21 or higher
- Gradle 7.x or higher

## How to Build and Run

### Building the Project

To build the entire project:

```bash
./gradlew build
```

To build a specific chapter:

```bash
./gradlew chapter1-data-replication:build
```

### Running a Chapter

Each chapter can be run using the provided scripts in the `scripts` directory 

## Learning Path

For the best learning experience, it's recommended to work through the chapters sequentially:

1. Start with Chapter 1 to understand data replication and consistency
2. Move to Chapter 2 to learn about data partitioning
3. Continue with Chapter 3 to explore distributed time concepts
4. Proceed to Chapter 4 for cluster management techniques
5. Finish with Chapter 5 to master inter-node communication

Each chapter's README.md provides detailed information about the concepts, implementation details, and learning objectives.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is open source and available under the [MIT License](LICENSE).
