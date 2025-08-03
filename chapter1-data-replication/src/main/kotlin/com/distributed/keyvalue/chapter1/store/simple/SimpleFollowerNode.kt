package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestCommand
import com.distributed.keyvalue.chapter1.response.Response
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.store.*
import java.util.concurrent.CompletableFuture

/**
 * Simple implementation of the FollowerNode interface.
 * This is a basic implementation for demonstration purposes.
 */
class SimpleFollowerNode(
    override val id: String,
    override val wal: WriteAheadLog,
    private val keyValueStore: KeyValueStore,
    private val electionTimeoutMs: Long = 1000
) : FollowerNode {

    override var currentTerm: Long = 0
        private set
    
    override val state: NodeState = NodeState.FOLLOWER
    
    override var leader: LeaderNode? = null
    
    override var lastHeartbeatTime: Long = System.currentTimeMillis()
    
    private var commitIndex: Long = 0
    private var running: Boolean = false
    
    override fun start() {
        if (!running) {
            running = true

            // TODO: In a real implementation, we would start a timer to check for election timeout and transition to CANDIDATE state if no heartbeat is received within the timeout
        }
    }
    
    override fun stop() {
        if (running) {
            running = false
        }
    }
    
    override fun process(request: Request): CompletableFuture<Response> {
        val future = CompletableFuture<Response>()

        try {
            // Parse request to SimpleRequestCommand
            val command = SimpleRequestCommand.from(request.command)

            // Followers should redirect write requests to the leader
            val currentLeader = leader
            if (currentLeader != null) {
                return currentLeader.process(request)
            }
            
            // If there's no leader, return an error
            val response = SimpleResponse(
                requestId = request.id,
                result = ByteArray(0),
                success = false,
                errorMessage = "No leader available",
                metadata = emptyMap()
            )
            future.complete(response)
        } catch (e: Exception) {
            // Handle parsing errors
            val response = SimpleResponse(
                requestId = request.id,
                result = ByteArray(0),
                success = false,
                errorMessage = "Error parsing command: ${e.message}",
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
