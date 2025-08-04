# Migration Guide: Implementing Leader Election

This document outlines the steps to migrate the distributed key-value store from a system with predefined leader and follower nodes to a system where all nodes start with the same type and elect leaders dynamically.

## Current Architecture

In the current implementation:

1. Nodes have fixed roles (leader or follower) specified at startup via command-line arguments.
2. The leader node accepts write requests and replicates them to followers.
3. Follower nodes forward client requests to the leader and replicate log entries from the leader.
4. There is no mechanism for a node to change its role after initialization.
5. If the leader fails, the system becomes unavailable until a new leader is manually configured.

## Target Architecture

In the new architecture:

1. All nodes start with the same type (no leader/follower distinction at startup).
2. Nodes use the Raft consensus algorithm to elect a leader dynamically.
3. Nodes can transition between states (FOLLOWER, CANDIDATE, LEADER) based on system events.
4. If the leader fails, a new leader is automatically elected from the remaining nodes.
5. The system remains available as long as a majority of nodes are functioning.

## Migration Steps

### 1. Create a Universal Node Type

Replace the separate leader and follower implementations with a single node type that can transition between states.

```kotlin
class RaftNode(
    override val id: String,
    override val wal: WriteAheadLog,
    private val keyValueStore: KeyValueStore,
    private val clusterNodes: List<NodeInfo>,
    private val electionTimeoutMs: Long = 1000,
    private val heartbeatIntervalMs: Long = 100
) : Node {
    // Node state starts as FOLLOWER
    override var state: NodeState = NodeState.FOLLOWER
        private set
    
    // Current term, initialized to 0
    override var currentTerm: Long = 0
        private set
    
    // The node voted for in the current term, if any
    private var votedFor: String? = null
    
    // The current leader, if known
    private var currentLeader: String? = null
    
    // Last time a heartbeat was received from the leader
    private var lastHeartbeatTime: Long = System.currentTimeMillis()
    
    // Election timer
    private val electionTimer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    // Heartbeat timer (used when in LEADER state)
    private val heartbeatTimer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    // Node proxies for communication with other nodes
    private val nodeProxies: MutableMap<String, NodeProxy> = mutableMapOf()
    
    // Implementation of Node methods and Raft-specific methods...
}
```

### 2. Implement State Transitions

Add methods to handle state transitions between FOLLOWER, CANDIDATE, and LEADER states.

```kotlin
private fun transitionToFollower(term: Long) {
    currentTerm = term
    state = NodeState.FOLLOWER
    votedFor = null
    currentLeader = null
    
    // Stop sending heartbeats if we were a leader
    stopHeartbeatTimer()
    
    // Start election timer
    resetElectionTimer()
}

private fun transitionToCandidate() {
    state = NodeState.CANDIDATE
    currentTerm++
    votedFor = id  // Vote for self
    currentLeader = null
    
    // Request votes from all other nodes
    requestVotes()
}

private fun transitionToLeader() {
    state = NodeState.LEADER
    currentLeader = id
    
    // Initialize leader state
    // nextIndex and matchIndex for each node
    
    // Stop election timer
    stopElectionTimer()
    
    // Start sending heartbeats
    startHeartbeatTimer()
}
```

### 3. Implement Election Timers

Add election timeout and heartbeat mechanisms.

```kotlin
private fun resetElectionTimer() {
    electionTimer.shutdownNow()
    val newTimer = Executors.newSingleThreadScheduledExecutor()
    electionTimer = newTimer
    
    // Random election timeout between electionTimeoutMs and 2*electionTimeoutMs
    val timeout = electionTimeoutMs + Random().nextLong(electionTimeoutMs)
    
    newTimer.schedule({
        if (state != NodeState.LEADER) {
            // If we haven't received a heartbeat within the timeout, start an election
            transitionToCandidate()
        }
    }, timeout, TimeUnit.MILLISECONDS)
}

private fun startHeartbeatTimer() {
    heartbeatTimer.shutdownNow()
    val newTimer = Executors.newSingleThreadScheduledExecutor()
    heartbeatTimer = newTimer
    
    newTimer.scheduleAtFixedRate({
        if (state == NodeState.LEADER) {
            sendHeartbeats()
        }
    }, 0, heartbeatIntervalMs, TimeUnit.MILLISECONDS)
}
```

### 4. Implement Vote Requests

Add methods to request votes and respond to vote requests.

```kotlin
private fun requestVotes() {
    val lastLogIndex = wal.getLastPosition()
    val lastLogTerm = if (lastLogIndex >= 0) {
        val entries = wal.read(lastLogIndex, 1)
        if (entries.isNotEmpty()) entries[0].term else 0
    } else 0
    
    var votesReceived = 1  // Vote for self
    
    for (nodeInfo in clusterNodes) {
        if (nodeInfo.id == id) continue  // Skip self
        
        val proxy = getOrCreateNodeProxy(nodeInfo)
        proxy.requestVote(currentTerm, id, lastLogIndex, lastLogTerm)
            .thenAccept { granted ->
                if (granted && state == NodeState.CANDIDATE && currentTerm == term) {
                    votesReceived++
                    
                    // If we have a majority of votes, become leader
                    if (votesReceived > clusterNodes.size / 2) {
                        transitionToLeader()
                    }
                }
            }
    }
}

fun handleRequestVote(
    term: Long,
    candidateId: String,
    lastLogIndex: Long,
    lastLogTerm: Long
): Boolean {
    // If the term is greater than our current term, update our term and become a follower
    if (term > currentTerm) {
        transitionToFollower(term)
    }
    
    // If we've already voted for someone else in this term, reject the vote
    if (votedFor != null && votedFor != candidateId) {
        return false
    }
    
    // Check if the candidate's log is at least as up-to-date as ours
    val ourLastLogIndex = wal.getLastPosition()
    val ourLastLogTerm = if (ourLastLogIndex >= 0) {
        val entries = wal.read(ourLastLogIndex, 1)
        if (entries.isNotEmpty()) entries[0].term else 0
    } else 0
    
    if (lastLogTerm > ourLastLogTerm ||
        (lastLogTerm == ourLastLogTerm && lastLogIndex >= ourLastLogIndex)) {
        votedFor = candidateId
        resetElectionTimer()
        return true
    }
    
    return false
}
```

### 5. Update Node Initialization

Update the NodeInitializer to initialize nodes with the same type and provide cluster configuration.

```kotlin
fun createNode(arguments: Map<String, String>): Node {
    val host = arguments["host"]!!
    val port = arguments["port"]!!.toInt()
    val wal = SimpleInMemoryWAL()
    
    // Parse cluster nodes from arguments
    val clusterNodesStr = arguments["cluster-nodes"] ?: ""
    val clusterNodes = clusterNodesStr.split(",")
        .filter { it.isNotEmpty() }
        .map { nodeStr ->
            val parts = nodeStr.split(":")
            NodeInfo(parts[0], parts[1].toInt())
        }
    
    // Create a RaftNode with the cluster configuration
    return RaftNode(
        id = "$host:$port",
        wal = wal,
        keyValueStore = SimpleInMemoryKeyValueStore(),
        clusterNodes = clusterNodes,
        electionTimeoutMs = arguments["election-timeout-ms"]?.toLong() ?: 1000,
        heartbeatIntervalMs = arguments["heartbeat-interval-ms"]?.toLong() ?: 100
    )
}
```

### 6. Update Communication Mechanism

Extend the NodeProxy to support vote requests and responses.

```kotlin
class NodeProxy(
    private val host: String,
    private val port: Int
) {
    // Existing methods...
    
    fun requestVote(
        term: Long,
        candidateId: String,
        lastLogIndex: Long,
        lastLogTerm: Long
    ): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        
        try {
            // Create a vote request and send it to the node
            val request = VoteRequest(term, candidateId, lastLogIndex, lastLogTerm)
            val requestBytes = JsonSerializer.serialize(request)
            
            // Send the request and receive the response
            // ...
            
            // Parse the response and complete the future
            // ...
        } catch (e: Exception) {
            future.complete(false)
        }
        
        return future
    }
}
```

### 7. Update Server to Handle Vote Requests

Update the server to handle vote requests and other Raft-specific messages.

```kotlin
fun startServer(args: Array<String>) {
    // Existing code...
    
    threadPool.submit {
        while (!serverSocket.isClosed) {
            try {
                val socket = serverSocket.accept()
                threadPool.submit {
                    try {
                        val input = DataInputStream(socket.getInputStream())
                        val output = DataOutputStream(socket.getOutputStream())
                        
                        val messageType = input.readInt()
                        
                        when (messageType) {
                            MessageType.CLIENT_REQUEST -> {
                                // Handle client request
                                // ...
                            }
                            MessageType.VOTE_REQUEST -> {
                                // Handle vote request
                                val requestLength = input.readInt()
                                val requestBytes = ByteArray(requestLength)
                                input.readFully(requestBytes)
                                
                                val request = JsonSerializer.deserialize<VoteRequest>(requestBytes)
                                val granted = node.handleRequestVote(
                                    request.term,
                                    request.candidateId,
                                    request.lastLogIndex,
                                    request.lastLogTerm
                                )
                                
                                val response = VoteResponse(node.currentTerm, granted)
                                val responseBytes = JsonSerializer.serialize(response)
                                
                                output.writeInt(responseBytes.size)
                                output.write(responseBytes)
                                output.flush()
                            }
                            // Handle other message types...
                        }
                    } catch (e: Exception) {
                        // Handle errors
                    } finally {
                        socket.close()
                    }
                }
            } catch (e: Exception) {
                // Handle errors
            }
        }
    }
}
```

### 8. Update Client to Work with Leader Election

Update the client to handle leader changes and retry requests if needed.

```kotlin
fun sendRequest(request: Request): Response {
    var attempts = 0
    var lastError: Exception? = null
    
    while (attempts < maxAttempts) {
        try {
            val response = currentLeaderProxy.process(request).get()
            
            // If the response indicates the node is not the leader, update the leader and retry
            if (!response.success && response.errorMessage?.contains("not leader") == true) {
                val newLeaderId = response.metadata["leaderId"]
                if (newLeaderId != null) {
                    updateLeader(newLeaderId)
                    attempts++
                    continue
                }
            }
            
            return response
        } catch (e: Exception) {
            lastError = e
            attempts++
            
            // Try the next node in the cluster
            rotateLeader()
        }
    }
    
    throw lastError ?: RuntimeException("Failed to send request after $maxAttempts attempts")
}
```

## Backward Compatibility

To maintain backward compatibility during the migration:

1. The RaftNode implementation can accept a `forcedRole` parameter that, if set, will make the node start in the specified role and skip the election process.
2. The existing command-line arguments for leader and follower roles can be mapped to this parameter.
3. Over time, as all nodes are migrated to the new implementation, the forced role parameter can be phased out.

## Testing the Migration

1. Start by testing the leader election in a controlled environment with a small cluster.
2. Verify that nodes can elect a leader and maintain consensus.
3. Test leader failover by stopping the current leader and verifying that a new leader is elected.
4. Test network partitions by simulating network failures between nodes.
5. Gradually migrate production nodes to the new implementation, starting with non-critical nodes.

## Conclusion

This migration will enhance the system's reliability and availability by implementing automatic leader election. The Raft consensus algorithm provides a solid foundation for distributed consensus and will enable the system to handle node failures gracefully.

By following these steps, you can migrate from a system with predefined leader and follower nodes to a more robust system with dynamic leader election.
