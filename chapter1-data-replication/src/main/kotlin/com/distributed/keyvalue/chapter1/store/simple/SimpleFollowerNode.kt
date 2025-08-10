package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.request.simple.SimpleFollowerRequestCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequest
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestAppendEntriesCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestCommandType
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestGetCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestHeartbeatCommand
import com.distributed.keyvalue.chapter1.response.Response
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.serde.JsonSerializer
import com.distributed.keyvalue.chapter1.store.FollowerNode
import com.distributed.keyvalue.chapter1.store.KeyValueStore
import com.distributed.keyvalue.chapter1.store.LogEntry
import com.distributed.keyvalue.chapter1.store.NodeProxy
import com.distributed.keyvalue.chapter1.store.NodeState
import com.distributed.keyvalue.chapter1.store.WriteAheadLog
import mu.KotlinLogging
import java.util.UUID
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
    private val electionTimeoutMs: Long = 10000
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

                // Send register message to leader
                sendRegisterMessage(proxy)
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

    /**
     * Sends a register message to the leader to register this follower.
     *
     * @param leaderProxy The proxy to the leader node
     */
    private fun sendRegisterMessage(leaderProxy: NodeProxy) {
        try {
            log.info("Sending register message to leader")

            // Create register command with this follower's ID
            val commandBytes = ByteArray(1 + id.length)
            commandBytes[0] = SimpleRequestCommandType.REGISTER_FOLLOWER.value
            System.arraycopy(id.toByteArray(Charsets.UTF_8), 0, commandBytes, 1, id.length)

            // Create request with connection_info metadata
            val request = SimpleRequest(
                id = UUID.randomUUID().toString(),
                command = commandBytes,
                metadata = mapOf("connection_info" to id)
            )

            // Send request to leader
            leaderProxy.process(request).thenAccept { response ->
                if (response.success) {
                    log.info("Successfully registered with leader")
                } else {
                    log.error("Failed to register with leader: ${response.errorMessage}")
                }
            }.exceptionally { e ->
                log.error("Error registering with leader", e)
                null
            }
        } catch (e: Exception) {
            log.error("Error creating register message", e)
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
            try {
                when (val command = SimpleFollowerRequestCommand.from(request.command)) {
                    // Handle heartbeat requests from the leader
                    is SimpleRequestHeartbeatCommand -> {
                        log.info { "[SimpleFollowerNode] Handling heartbeat command: $command" }
                        val success = processHeartbeat(command.term, command.highWaterMark)
                        val response = SimpleResponse(
                            requestId = request.id,
                            result = null,
                            success = success,
                            errorMessage = if (success) null else "Heartbeat rejected",
                            metadata = mapOf("term" to currentTerm.toString())
                        )
                        future.complete(response)
                        return future
                    }

                    is SimpleRequestAppendEntriesCommand -> {
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
                        return future
                    }

                    // TODO: should read from at least 2 nodes
                    is SimpleRequestGetCommand -> {
                        val result = keyValueStore.get(command.key)
                        val response = SimpleResponse(
                            requestId = request.id,
                            result = result,
                            success = true,
                            errorMessage = null,
                            metadata = emptyMap()
                        )
                        future.complete(response)
                        log.info { "[SimpleFollowerNode] Handle GET Request(key = ${command.key.toString(Charsets.UTF_8)}, value = ${result?.toString(Charsets.UTF_8)})"
                        }
                        return future
                    }

                    else -> {
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
            } catch (e: IllegalArgumentException) {
                log.info { "[SimpleFollowerNode] Error processing request: $request, e: $e" }
                val response = SimpleResponse(
                    requestId = request.id,
                    result = null,
                    success = false,
                    errorMessage = "Error occurred: $e",
                    metadata = emptyMap()
                )
                future.complete(response)
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

    override fun processHeartbeat(term: Long, highWatermark: Long): Boolean {
        // Update last heartbeat time
        lastHeartbeatTime = System.currentTimeMillis()

        // If the term is greater than our current term, update our term
        if (term > currentTerm) {
            currentTerm = term
        }

        // If the leader's commit index is greater than ours, update our commit index
        if (highWatermark > commitIndex) {
            val oldCommitIndex = commitIndex
            // TODO: should we request entries to the leader?
            commitIndex = minOf(highWatermark, wal.getLastPosition())
            
            // Apply committed entries to the key-value store
            if (commitIndex > oldCommitIndex) {
                applyCommittedEntries(oldCommitIndex + 1, commitIndex)
            }
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
        TODO()
    }

    /**
     * Apply committed entries to the key-value store.
     * This is the core of WAL replication - entries are first written to the WAL,
     * then applied to the state machine (key-value store) once committed.
     *
     * @param fromIndex The index to start applying from (inclusive)
     * @param toIndex The index to apply to (inclusive)
     */
    private fun applyCommittedEntries(fromIndex: Long, toIndex: Long) {
      // TODO()
    }
}
