package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.response.Response
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.serde.JsonSerializer
import com.distributed.keyvalue.chapter1.store.NodeProxy
import mu.KotlinLogging
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A simple implementation of NodeProxy that forwards requests to a single node (leader or follower).
 * Designed to work with any node, not just the leader.
 */
class SimpleNodeProxy(
    private val host: String,
    private val port: Int
) : NodeProxy {
    private val log = KotlinLogging.logger { }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var running: Boolean = false
    private val reconnectExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun start() {
        if (!running) {
            running = true
            connectToNode()

            reconnectExecutor.scheduleAtFixedRate({
                if (running && (socket == null || socket?.isClosed == true)) {
                    log.info("Attempting to reconnect to node at $host:$port")
                    connectToNode()
                }
            }, 5, 5, TimeUnit.SECONDS)
        }
    }

    override fun stop() {
        if (running) {
            running = false
            reconnectExecutor.shutdown()
            closeConnection()
        }
    }

    override fun process(request: Request): CompletableFuture<Response> {
        val future = CompletableFuture<Response>()

        try {
            if (socket == null || socket?.isClosed == true) {
                connectToNode()

                if (socket == null || socket?.isClosed == true) {
                    future.complete(
                        SimpleResponse(
                            requestId = request.id,
                            result = null,
                            success = false,
                            errorMessage = "Cannot connect to node",
                            metadata = emptyMap()
                        )
                    )
                    return future
                }
            }

            val out = output!!
            val commandBytes = request.command
            out.writeInt(commandBytes.size)
            out.write(commandBytes)
            out.flush()

            val inp = input!!
            val responseLength = inp.readInt()
            val responseBytes = ByteArray(responseLength)
            inp.readFully(responseBytes)

            try {
                val response = JsonSerializer.deserialize<SimpleResponse>(responseBytes)
                log.info { "[SimpleNodeProxy] deserialized response: $response" }
                future.complete(response)
            } catch (e: Exception) {
                future.complete(
                    SimpleResponse(
                        requestId = request.id,
                        result = null,
                        success = false,
                        errorMessage = "Failed to deserialize response: ${e.message}",
                        metadata = emptyMap()
                    )
                )
            }
        } catch (e: Exception) {
            log.error("Error forwarding request to node: ${e.message}", e)
            closeConnection()
            future.complete(
                SimpleResponse(
                    requestId = request.id,
                    result = null,
                    success = false,
                    errorMessage = "Error forwarding request: ${e.message}",
                    metadata = emptyMap()
                )
            )
        }

        return future
    }

    private fun connectToNode() {
        try {
            closeConnection()
            log.info("Connecting to node at $host:$port")
            val newSocket = Socket(host, port)
            socket = newSocket
            input = DataInputStream(newSocket.getInputStream())
            output = DataOutputStream(newSocket.getOutputStream())
            log.info("Connected to node at $host:$port")
        } catch (e: Exception) {
            log.error("Failed to connect to node at $host:$port: ${e.message}")
            closeConnection()
        }
    }

    private fun closeConnection() {
        try {
            input?.close()
            output?.close()
            socket?.close()
        } catch (e: Exception) {
            log.warn("Error closing connection: ${e.message}")
        } finally {
            input = null
            output = null
            socket = null
        }
    }
}
