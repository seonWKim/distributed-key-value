package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.request.simple.*
import com.distributed.keyvalue.chapter1.response.Response
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.serde.JsonSerializer
import com.distributed.keyvalue.chapter1.store.*
import mu.KotlinLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Simple implementation of the FollowerNode interface.
 * This is a basic implementation for demonstration purposes.
 */
class SimpleFollowerNode(
    override val id: String,
    override val wal: WriteAheadLog,
    private val keyValueStore: KeyValueStore,
    private val leaderHost: String? = null,
    private val leaderPort: Int? = null,
    private val electionTimeoutMs: Long = 1000
) : FollowerNode {

    private val log = KotlinLogging.logger { }

    override var currentTerm: Long = 0
        private set
    
    // Use a mutable state variable
    private var nodeState: NodeState = NodeState.FOLLOWER
    override val state: NodeState
        get() = nodeState
    
    // Reference to the leader proxy for forwarding requests
    override var leader: NodeProxy? = null
    
    override var lastHeartbeatTime: Long = System.currentTimeMillis()
    
    private var commitIndex: Long = 0
    private var running: Boolean = false
    
    // Timer for checking election timeout
    private val electionTimer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    override fun start() {
        if (!running) {
            running = true

            // Connect to the leader if host and port are provided
            if (leaderHost != null && leaderPort != null) {
                log.info("Connecting to leader at $leaderHost:$leaderPort")
                // Create a NodeProxy to the leader
                val proxy: NodeProxy = SimpleNodeProxy(
                    host = leaderHost,
                    port = leaderPort
                )
                proxy.start()
                leader = proxy
                log.info("Connected to leader at $leaderHost:$leaderPort")
            } else {
                log.warn("No leader host/port provided, will not connect to leader")
            }

            // Start a timer to check for election timeout
            electionTimer.scheduleAtFixedRate({
                checkElectionTimeout()
            }, electionTimeoutMs / 2, electionTimeoutMs / 2, TimeUnit.MILLISECONDS)
            
            log.info("Started election timeout timer with interval ${electionTimeoutMs / 2}ms")
        }
    }
    
    /**
     * Checks if the time since the last heartbeat exceeds the election timeout.
     * If it does, transitions to CANDIDATE state.
     */
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
                
                // In a real implementation, we would start an election here
                // For now, we'll just log the transition
                log.info("Node $id is now a candidate for term ${currentTerm + 1}")
                
                // Increment term
                currentTerm++
                
                // Reset last heartbeat time to avoid immediate re-election
                lastHeartbeatTime = currentTime
            }
        }
    }
    
    override fun stop() {
        if (running) {
            running = false
            
            // Stop the leader proxy if it exists
            leader?.let {
                log.info("Stopping connection to leader")
                it.stop()
                leader = null
            }
            
            // Stop the election timer
            try {
                log.info("Stopping election timer")
                electionTimer.shutdown()
                if (!electionTimer.awaitTermination(1, TimeUnit.SECONDS)) {
                    electionTimer.shutdownNow()
                }
            } catch (e: InterruptedException) {
                electionTimer.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }
    
    override fun process(request: Request): CompletableFuture<Response> {
        val future = CompletableFuture<Response>()

        try {
            // Parse request to SimpleRequestCommand
            when (val command = SimpleRequestCommand.from(request.command)) {
                // Handle heartbeat requests from the leader
                is SimpleRequestHeartbeatCommand -> {
                    log.info { "[SimpleFollowerNode] Handling heartbeat command: $command" }
                    val success = processHeartbeat(command.term, command.leaderCommit)
                    val response = SimpleResponse(
                        requestId = request.id,
                        result = null,
                        success = success,
                        errorMessage = if (success) null else "Heartbeat rejected",
                        metadata = mapOf("term" to currentTerm.toString())
                    )
                    future.complete(response)
                }
                
                // Handle appendEntries requests from the leader
                is SimpleRequestAppendEntriesCommand -> {
                    // Deserialize entries from JSON
                    val entriesJsonBytes = command.entriesJson.toByteArray(Charsets.UTF_8)
                    val entries = JsonSerializer.deserialize<List<SimpleLogEntry>>(entriesJsonBytes)
                    log.info { "[SimpleFollowerNode] Handling request append entries command: $entries"}
                    val success = appendEntries(
                        term = command.term,
                        prevLogIndex = command.prevLogIndex,
                        prevLogTerm = command.prevLogTerm,
                        entries = entries,
                        leaderCommit = command.leaderCommit
                    )
                    
                    val response = SimpleResponse(
                        requestId = request.id,
                        result = null,
                        success = success,
                        errorMessage = if (success) null else "AppendEntries rejected",
                        metadata = mapOf("term" to currentTerm.toString())
                    )
                    future.complete(response)
                }
                
                // For all other requests, forward to the leader if available
                else -> {
                    // TODO: handle GET request(quorum)
                    val proxy = leader
                    if (proxy != null) {
                        log.info { "[SimpleFollowerNode] Redirect request to leader node"}
                        return proxy.process(request)
                    }
                    
                    // If there's no leader proxy, return an error
                    val response = SimpleResponse(
                        requestId = request.id,
                        result = null,
                        success = false,
                        errorMessage = "No leader available",
                        metadata = emptyMap()
                    )
                    future.complete(response)
                }
            }
        } catch (e: Exception) {
            // Handle parsing errors
            val response = SimpleResponse(
                requestId = request.id,
                result = null,
                success = false,
                errorMessage = "Error processing request: ${e.message}",
                metadata = emptyMap()
            )
            future.complete(response)
        }
        
        return future
    }
    
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
            // In a real implementation, we would apply committed entries to the state machine
        }
        
        return term >= currentTerm
    }
    
    override fun appendEntries(
        term: Long,
        prevLogIndex: Long,
        prevLogTerm: Long,
        entries: List<LogEntry>,
        leaderCommit: Long
    ): Boolean {
        // Update last heartbeat time
        lastHeartbeatTime = System.currentTimeMillis()
        
        // If the term is less than our current term, reject the request
        if (term < currentTerm) {
            return false
        }
        
        // If the term is greater than our current term, update our term
        if (term > currentTerm) {
            currentTerm = term
        }
        
        // Check if we have the previous log entry
        if (prevLogIndex >= 0) {
            val prevEntries = wal.read(prevLogIndex, 1)
            if (prevEntries.isEmpty() || prevEntries[0].term != prevLogTerm) {
                return false
            }
        }
        
        // Append new entries
        for (entry in entries) {
            // Check if we already have an entry at this index
            val existingEntries = wal.read(entry.id, 1)
            if (existingEntries.isNotEmpty()) {
                // If the terms don't match, delete this and all following entries
                if (existingEntries[0].term != entry.term) {
                    // In a real implementation, we would delete all entries from this index onwards
                    // For simplicity, we'll just append the new entry, which will overwrite the existing one
                }
            }
            
            // Append the entry
            wal.append(entry)
        }
        
        // Update commit index
        if (leaderCommit > commitIndex) {
            commitIndex = minOf(leaderCommit, wal.getLastPosition())
            // In a real implementation, we would apply committed entries to the state machine
        }
        
        return true
    }
}
