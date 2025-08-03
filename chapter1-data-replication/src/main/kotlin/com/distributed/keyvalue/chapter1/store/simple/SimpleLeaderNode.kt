package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.response.Response
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.store.FollowerNode
import com.distributed.keyvalue.chapter1.store.KeyValueStore
import com.distributed.keyvalue.chapter1.store.LeaderNode
import com.distributed.keyvalue.chapter1.store.LogEntry
import com.distributed.keyvalue.chapter1.store.NodeState
import com.distributed.keyvalue.chapter1.store.WriteAheadLog
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Simple implementation of the LeaderNode interface.
 * This is a basic implementation for demonstration purposes.
 */
class SimpleLeaderNode(
    override val id: String,
    override val wal: WriteAheadLog,
    override val followers: List<FollowerNode>,
    private val keyValueStore: KeyValueStore,
    private val heartbeatIntervalMs: Long = 100
) : LeaderNode {
    
    override var currentTerm: Long = 0
        private set
    
    override val state: NodeState = NodeState.LEADER
    
    override val lowWatermark: Long
        get() = calculateLowWatermark()
    
    override val highWatermark: Long
        get() = calculateHighWatermark()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var running: Boolean = false
    
    /**
     * Calculates the low watermark position in the log.
     * This is the position that has been replicated to all followers.
     */
    private fun calculateLowWatermark(): Long {
        if (followers.isEmpty()) {
            return wal.getLastPosition()
        }
        
        // Find the minimum log position across all followers
        return followers.minOfOrNull { follower ->
            // For simplicity, we assume each follower has a lastReplicatedIndex property
            // In a real implementation, we would track this information
            0L // Placeholder, would be follower.lastReplicatedIndex in a real implementation
        } ?: 0L
    }
    
    /**
     * Calculates the high watermark position in the log.
     * This is the position that has been replicated to a quorum of nodes.
     */
    private fun calculateHighWatermark(): Long {
        if (followers.isEmpty()) {
            return wal.getLastPosition()
        }
        
        // Get all log positions, including this leader
        val positions = mutableListOf<Long>()
        positions.add(wal.getLastPosition())
        
        // For simplicity, we assume each follower has a lastReplicatedIndex property
        // In a real implementation, we would track this information
        followers.forEach { follower ->
            positions.add(0L) // Placeholder, would be follower.lastReplicatedIndex in a real implementation
        }
        
        // Sort positions and get the position at the quorum index
        positions.sort()
        val quorumIndex = positions.size / 2
        return positions[quorumIndex]
    }
    
    override fun start() {
        if (!running) {
            running = true
            // Schedule heartbeat task
            scheduler.scheduleAtFixedRate(
                { sendHeartbeats() },
                0,
                heartbeatIntervalMs,
                TimeUnit.MILLISECONDS
            )
        }
    }
    
    override fun stop() {
        if (running) {
            running = false
            scheduler.shutdown()
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow()
                }
            } catch (e: InterruptedException) {
                scheduler.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }
    
    override fun process(request: Request): CompletableFuture<Response> {
        val future = CompletableFuture<Response>()
        
        try {
            // Create a log entry from the request
            val logEntry: LogEntry = SimpleLogEntry(
                id = wal.getLastPosition() + 1,
                term = currentTerm,
                data = request.command,
                metadata = request.metadata
            )

            // Append to local log
            val position = wal.append(logEntry)
            
            // Replicate to followers
            replicateLog(position).thenRun {
                // Create a success response
                val response = SimpleResponse(
                    requestId = request.id,
                    result = ByteArray(0), // Placeholder, would be actual result in a real implementation
                    success = true,
                    errorMessage = null,
                    metadata = mapOf("position" to position.toString())
                )
                future.complete(response)
            }.exceptionally { e ->
                // Create an error response
                val response = SimpleResponse(
                    requestId = request.id,
                    result = ByteArray(0),
                    success = false,
                    errorMessage = e.message,
                    metadata = emptyMap()
                )
                future.complete(response)
                null
            }
        } catch (e: Exception) {
            // Create an error response
            val response = SimpleResponse(
                requestId = request.id,
                result = ByteArray(0),
                success = false,
                errorMessage = e.message,
                metadata = emptyMap()
            )
            future.complete(response)
        }
        
        return future
    }
    
    override fun sendHeartbeats() {
        followers.forEach { follower ->
            try {
                follower.processHeartbeat(currentTerm, highWatermark)
            } catch (e: Exception) {
                // Log error, but continue with other followers
                println("Error sending heartbeat to follower ${follower.id}: ${e.message}")
            }
        }
    }
    
    override fun replicateLog(fromPosition: Long): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        
        if (followers.isEmpty()) {
            future.complete(null)
            return future
        }
        
        val entries = wal.read(fromPosition)
        if (entries.isEmpty()) {
            future.complete(null)
            return future
        }
        
        val replicationFutures = followers.map { follower ->
            val followerFuture = CompletableFuture<Boolean>()
            
            try {
                // For simplicity, we assume the previous log entry is at fromPosition - 1
                val prevLogIndex = fromPosition - 1
                val prevLogTerm = if (prevLogIndex >= 0) {
                    val prevEntries = wal.read(prevLogIndex, 1)
                    if (prevEntries.isNotEmpty()) prevEntries[0].term else 0
                } else {
                    0
                }
                
                val success = follower.appendEntries(
                    term = currentTerm,
                    prevLogIndex = prevLogIndex,
                    prevLogTerm = prevLogTerm,
                    entries = entries,
                    leaderCommit = highWatermark
                )
                
                followerFuture.complete(success)
            } catch (e: Exception) {
                // Log error, but continue with other followers
                println("Error replicating log to follower ${follower.id}: ${e.message}")
                followerFuture.complete(false)
            }
            
            followerFuture
        }
        
        // Wait for a quorum of followers to replicate
        CompletableFuture.allOf(*replicationFutures.toTypedArray()).thenRun {
            val successCount = replicationFutures.count { it.get() }
            if (successCount >= followers.size / 2) {
                future.complete(null)
            } else {
                future.completeExceptionally(Exception("Failed to replicate to a quorum of followers"))
            }
        }
        
        return future
    }
}
