# Heartbeat Implementation

This document describes the heartbeat implementation in the distributed key-value store.

## Overview

Heartbeats are a critical component of distributed systems, particularly those that follow the Raft consensus algorithm. They serve several important purposes:

1. **Leader Election**: Heartbeats prevent unnecessary leader elections by signaling that the leader is still alive and functioning.
2. **Failure Detection**: Followers can detect when a leader has failed by monitoring heartbeats.
3. **Commit Progress**: Heartbeats can carry information about the leader's commit progress, allowing followers to advance their commit index.

## Implementation Details

### Leader Node

The `SimpleLeaderNode` class is responsible for sending heartbeats to all followers at regular intervals:

1. **Heartbeat Scheduling**: When a leader starts, it schedules a task to send heartbeats to all followers at a fixed interval (default: 100ms).
2. **Follower Registration**: The leader maintains a list of follower proxies. Followers can register themselves with the leader using the `registerFollowerProxy` method.
3. **Heartbeat Content**: Each heartbeat contains the leader's current term and commit index (high watermark).
4. **Sending Mechanism**: Heartbeats are sent as `SimpleRequestHeartbeatCommand` objects through the follower proxies.

Key code snippets:

```kotlin
// Schedule heartbeat task
scheduler.scheduleAtFixedRate(
    { sendHeartbeats() },
    0,
    heartbeatIntervalMs,
    TimeUnit.MILLISECONDS
)

// Send heartbeats to all followers
override fun sendHeartbeats() {
    followerProxies.forEach { followerProxy ->
        try {
            // Create a heartbeat request
            val heartbeatCommand = SimpleRequestHeartbeatCommand(
                term = currentTerm,
                leaderCommit = highWatermark
            )
            
            // Convert to byte array and send to follower
            // ...
            
            // Send request to follower
            followerProxy.process(request)
                .exceptionally { e ->
                    log.error("Failed to send heartbeat: ${e.message}", e)
                    null
                }
        } catch (e: Exception) {
            log.error("Error sending heartbeat to follower: ${e.message}")
        }
    }
}
```

### Follower Node

The `SimpleFollowerNode` class processes heartbeats from the leader and implements an election timeout mechanism:

1. **Heartbeat Processing**: When a follower receives a heartbeat, it updates its `lastHeartbeatTime` and processes the heartbeat information.
2. **Election Timeout**: If a follower doesn't receive a heartbeat within the election timeout period (default: 1000ms), it transitions to the CANDIDATE state.
3. **Timer Implementation**: The election timeout is implemented using a scheduled executor that checks for timeouts at regular intervals.

Key code snippets:

```kotlin
// Process heartbeat from leader
override fun processHeartbeat(term: Long, leaderCommit: Long): Boolean {
    // Update last heartbeat time
    lastHeartbeatTime = System.currentTimeMillis()
    
    // If the term is greater than our current term, update our term
    if (term > currentTerm) {
        currentTerm = term
    }
    
    // If the leader's commit index is greater than ours, update our commit index
    if (leaderCommit > commitIndex) {
        commitIndex = minOf(leaderCommit, wal.getLastPosition())
    }
    
    return term >= currentTerm
}

// Check for election timeout
private fun checkElectionTimeout() {
    if (!running) return
    
    val currentTime = System.currentTimeMillis()
    val timeSinceLastHeartbeat = currentTime - lastHeartbeatTime
    
    if (timeSinceLastHeartbeat > electionTimeoutMs) {
        log.info("Election timeout: No heartbeat received for ${timeSinceLastHeartbeat}ms (timeout: ${electionTimeoutMs}ms)")
        
        // Transition to CANDIDATE state
        if (nodeState == NodeState.FOLLOWER) {
            log.info("Transitioning from FOLLOWER to CANDIDATE state")
            nodeState = NodeState.CANDIDATE
            
            // Increment term
            currentTerm++
            
            // Reset last heartbeat time to avoid immediate re-election
            lastHeartbeatTime = currentTime
        }
    }
}
```

## Testing

The heartbeat implementation is tested in `HeartbeatTest.kt` with three test scenarios:

1. **testHeartbeats**: Verifies that a follower's `lastHeartbeatTime` is updated when it receives a heartbeat.
2. **testElectionTimeout**: Verifies that a follower transitions to CANDIDATE state if it doesn't receive heartbeats within the election timeout.
3. **testProcessHeartbeat**: Verifies that a follower processes heartbeats correctly, updating its term if necessary.

## Future Improvements

1. **Randomized Election Timeouts**: To prevent split votes, election timeouts could be randomized within a range.
2. **Adaptive Heartbeat Intervals**: The heartbeat interval could be adjusted based on network conditions.
3. **Batched Heartbeats**: For systems with many followers, heartbeats could be batched to reduce network overhead.
4. **Heartbeat Optimization**: Heartbeats could be piggybacked on other messages to reduce network traffic.
