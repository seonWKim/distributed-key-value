package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.request.simple.SimpleLeaderRequestCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequest
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestAppendEntriesCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestCommandType
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestDeleteCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestGetCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestHeartbeatCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestPutCommand
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestRegisterFollower
import com.distributed.keyvalue.chapter1.response.Response
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.serde.JsonSerializer
import com.distributed.keyvalue.chapter1.store.KeyValueStore
import com.distributed.keyvalue.chapter1.store.LeaderNode
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
 * Simple implementation of the LeaderNode interface.
 * This is a basic implementation for demonstration purposes.
 */
class SimpleLeaderNode(
    override val id: String,
    override val wal: WriteAheadLog,
    followerProxies: List<NodeProxy> = emptyList(),
    private val keyValueStore: KeyValueStore,
    private val heartbeatIntervalMs: Long = 5000
) : LeaderNode {

    // Use a mutable list for follower proxies
    private val _followerProxies = followerProxies.toMutableList()
    override val followerProxies: List<NodeProxy>
        get() = _followerProxies.toList() // Return an immutable copy

    private val log = KotlinLogging.logger { }

    override var currentTerm: Long = 0
        private set

    override val state: NodeState = NodeState.LEADER

    override val lowWatermark: Long
        get() = calculateLowWatermark()

    override val highWatermark: Long
        get() = calculateHighWatermark()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var running: Boolean = false

    // Track the last replicated index for each follower proxy
    private val followerReplicationIndices = mutableMapOf<NodeProxy, Long>()

    /**
     * Calculates the low watermark position in the log.
     * This is the position that has been replicated to all followers.
     */
    private fun calculateLowWatermark(): Long {
        if (followerProxies.isEmpty()) {
            return wal.getLastPosition()
        }

        // Find the minimum log position across all followers
        return followerReplicationIndices.values.minOrNull() ?: 0L
    }

    /**
     * Calculates the high watermark position in the log.
     * This is the position that has been replicated to a quorum of nodes.
     */
    private fun calculateHighWatermark(): Long {
        if (followerProxies.isEmpty()) {
            return wal.getLastPosition()
        }

        // Get all log positions, including this leader
        val positions = mutableListOf<Long>()
        positions.add(wal.getLastPosition())
        positions.addAll(followerReplicationIndices.values)

        // Sort positions and get the position at the quorum index
        positions.sort()
        val quorumIndex = positions.size / 2
        return positions[quorumIndex]
    }

    override fun start() {
        if (!running) {
            running = true

            // Initialize replication indices
            followerProxies.forEach { proxy ->
                followerReplicationIndices[proxy] = 0L
            }

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

    /**
     * Registers a new follower proxy with this leader.
     * This allows followers to connect to the leader after the leader has started.
     *
     * @param proxy The proxy to the follower node
     */
    override fun registerFollowerProxy(proxy: NodeProxy) {
        log.info("Registering new follower proxy")

        // Add the proxy to the list if it's not already there
        if (!_followerProxies.contains(proxy)) {
            _followerProxies.add(proxy)
            followerReplicationIndices[proxy] = 0L
            log.info("Follower proxy registered, total followers: ${_followerProxies.size}")
        } else {
            log.info("Follower proxy already registered")
        }
    }

    override fun process(request: Request): CompletableFuture<Response> {
        val future = CompletableFuture<Response>()

        try {
            // Parse request to SimpleLeaderRequestCommand
            val command = SimpleLeaderRequestCommand.from(request.command)
            var result: ByteArray? = null

            when (command) {
                is SimpleRequestGetCommand -> {
                    result = keyValueStore.get(command.key)
                    val response = SimpleResponse(
                        requestId = request.id,
                        result = result,
                        success = true,
                        errorMessage = null,
                        metadata = emptyMap()
                    )
                    future.complete(response)
                    log.info {
                        "[SimpleLeaderNode] Handle GET Request(key = ${command.key.toString(Charsets.UTF_8)}, value = ${
                            result?.toString(Charsets.UTF_8)
                        })"
                    }
                    return future
                }

                is SimpleRequestPutCommand -> {
                    result = keyValueStore.put(
                        key = command.key,
                        value = command.value,
                        version = emptyMap() // TODO
                    )
                    log.info {
                        "[SimpleLeaderNode] Handle PUT Request(key = ${command.key.toString(Charsets.UTF_8)}, value = ${
                            command.value.toString(Charsets.UTF_8)
                        })"
                    }
                }

                is SimpleRequestDeleteCommand -> {
                    result = keyValueStore.delete(command.key)
                    log.info { "[SimpleLeaderNode] Handle DELETE Request(key = ${command.key.toString(Charsets.UTF_8)})" }
                }

                is SimpleRequestRegisterFollower -> {
                    log.info { "[SimpleLeaderNode] Handling register follower command: $command" }

                    // Create a new proxy for the follower if it doesn't exist
                    // We can't use the sender_proxy metadata because the proxy is not yet registered
                    // Instead, we'll use the connection information from the request
                    val connectionInfo = request.metadata["connection_info"]
                    if (connectionInfo != null) {
                        val parts = connectionInfo.split(":")
                        if (parts.size == 2) {
                            val host = parts[0]
                            val port = parts[1].toInt()

                            // Create a new proxy for the follower
                            val newProxy = SimpleNodeProxy(host, port)
                            newProxy.start()

                            // Register the proxy
                            registerFollowerProxy(newProxy)

                            val response = SimpleResponse(
                                requestId = request.id,
                                result = null,
                                success = true,
                                errorMessage = null,
                                metadata = emptyMap()
                            )
                            future.complete(response)
                        } else {
                            val response = SimpleResponse(
                                requestId = request.id,
                                result = null,
                                success = false,
                                errorMessage = "Invalid connection_info format",
                                metadata = emptyMap()
                            )
                            future.complete(response)
                        }
                    } else {
                        // For backward compatibility, try to use the follower ID directly
                        try {
                            // Use the follower ID as the connection info
                            val followerId = command.followerId
                            if (followerId.contains(":")) {
                                val parts = followerId.split(":")
                                val host = parts[0]
                                val port = parts[1].toInt()

                                // Create a new proxy for the follower
                                val newProxy = SimpleNodeProxy(host, port)
                                newProxy.start()

                                // Register the proxy
                                registerFollowerProxy(newProxy)

                                val response = SimpleResponse(
                                    requestId = request.id,
                                    result = null,
                                    success = true,
                                    errorMessage = null,
                                    metadata = emptyMap()
                                )
                                future.complete(response)
                            } else {
                                val response = SimpleResponse(
                                    requestId = request.id,
                                    result = null,
                                    success = false,
                                    errorMessage = "Invalid follower ID format",
                                    metadata = emptyMap()
                                )
                                future.complete(response)
                            }
                        } catch (e: Exception) {
                            val response = SimpleResponse(
                                requestId = request.id,
                                result = null,
                                success = false,
                                errorMessage = "Error creating proxy: ${e.message}",
                                metadata = emptyMap()
                            )
                            future.complete(response)
                        }
                    }
                    return future
                }
            }

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
                    result = result,
                    success = true,
                    errorMessage = null,
                    metadata = mapOf("position" to position.toString())
                )
                future.complete(response)
            }.exceptionally { e ->
                val response = SimpleResponse(
                    requestId = request.id,
                    result = null,
                    success = false,
                    errorMessage = e.message,
                    metadata = emptyMap()
                )
                future.complete(response)
                null
            }
        } catch (e: Exception) {
            val response = SimpleResponse(
                requestId = request.id,
                result = null,
                success = false,
                errorMessage = e.message,
                metadata = emptyMap()
            )
            future.complete(response)
        }

        return future
    }

    override fun sendHeartbeats() {
        followerProxies.forEach { followerProxy ->
            try {
                // Create a heartbeat request
                val heartbeatCommand = SimpleRequestHeartbeatCommand(
                    term = currentTerm,
                    highWaterMark = highWatermark
                )

                val payload = "${heartbeatCommand.term}:${heartbeatCommand.highWaterMark}"
                val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                val commandBytes = ByteArray(1 + payloadBytes.size)
                commandBytes[0] = SimpleRequestCommandType.HEARTBEAT.value
                System.arraycopy(payloadBytes, 0, commandBytes, 1, payloadBytes.size)

                // Create request
                val request = SimpleRequest(
                    id = UUID.randomUUID().toString(),
                    command = commandBytes,
                    timestamp = System.currentTimeMillis(),
                    metadata = emptyMap()
                )
                log.info { "[SimpleLeaderNode] Sending heartbeats: $request" }

                // Send request to follower
                followerProxy.process(request)
                    .exceptionally { e ->
                        log.error("Failed to send heartbeat: ${e.message}", e)
                        null
                    }
            } catch (e: Exception) {
                // Log error, but continue with other followers
                log.error("Error sending heartbeat to follower: ${e.message}")
            }
        }
    }

    override fun replicateLog(fromPosition: Long): CompletableFuture<Void> {
        // TODO
        return CompletableFuture.completedFuture(null)
    }
}
