package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.response.Response
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import mu.KotlinLogging
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A proxy that forwards requests to a remote leader node.
 * This allows follower nodes to connect to a leader node over the network.
 */
class SimpleLeaderNodeProxy(
    private val leaderHost: String,
    private val leaderPort: Int
) {
    private val log = KotlinLogging.logger { }
    
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var running: Boolean = false
    private val reconnectExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    /**
     * Starts the proxy by connecting to the leader node.
     */
    fun start() {
        if (!running) {
            running = true
            connectToLeader()
            
            // Schedule periodic reconnection attempts if the connection is lost
            reconnectExecutor.scheduleAtFixedRate({
                if (running && (socket == null || socket?.isClosed == true)) {
                    log.info("Attempting to reconnect to leader at $leaderHost:$leaderPort")
                    connectToLeader()
                }
            }, 5, 5, TimeUnit.SECONDS)
        }
    }
    
    /**
     * Stops the proxy by closing the connection to the leader node.
     */
    fun stop() {
        if (running) {
            running = false
            reconnectExecutor.shutdown()
            closeConnection()
        }
    }
    
    /**
     * Processes a request by forwarding it to the leader node.
     */
    fun process(request: Request): CompletableFuture<Response> {
        val future = CompletableFuture<Response>()
        
        try {
            // Ensure we have a connection to the leader
            if (socket == null || socket?.isClosed == true) {
                connectToLeader()
                
                if (socket == null || socket?.isClosed == true) {
                    // If we still can't connect, return an error
                    val response = SimpleResponse(
                        requestId = request.id,
                        result = null,
                        success = false,
                        errorMessage = "Cannot connect to leader node",
                        metadata = emptyMap()
                    )
                    future.complete(response)
                    return future
                }
            }
            
            // Forward the request to the leader
            val out = output!!
            val commandBytes = request.command
            out.writeInt(commandBytes.size)
            out.write(commandBytes)
            out.flush()
            
            // Read the response
            val inp = input!!
            val responseLength = inp.readInt()
            val responseBytes = ByteArray(responseLength)
            inp.readFully(responseBytes)
            val responseString = String(responseBytes, Charsets.UTF_8)
            
            // Parse the response
            val success = responseString.startsWith("Success")
            val result = if (success) {
                responseString.substring("Success: ".length).toByteArray(Charsets.UTF_8)
            } else {
                null
            }
            val errorMessage = if (!success) {
                responseString.substring("Error: ".length)
            } else {
                null
            }
            
            val response = SimpleResponse(
                requestId = request.id,
                result = result,
                success = success,
                errorMessage = errorMessage,
                metadata = emptyMap()
            )
            
            future.complete(response)
        } catch (e: Exception) {
            log.error("Error forwarding request to leader: ${e.message}", e)
            
            // Close the connection so we'll reconnect on the next request
            closeConnection()
            
            val response = SimpleResponse(
                requestId = request.id,
                result = null,
                success = false,
                errorMessage = "Error forwarding request to leader: ${e.message}",
                metadata = emptyMap()
            )
            future.complete(response)
        }
        
        return future
    }
    
    /**
     * Connects to the leader node.
     */
    private fun connectToLeader() {
        try {
            closeConnection() // Close any existing connection
            
            log.info("Connecting to leader at $leaderHost:$leaderPort")
            val newSocket = Socket(leaderHost, leaderPort)
            socket = newSocket
            input = DataInputStream(newSocket.getInputStream())
            output = DataOutputStream(newSocket.getOutputStream())
            log.info("Connected to leader at $leaderHost:$leaderPort")
        } catch (e: Exception) {
            log.error("Failed to connect to leader at $leaderHost:$leaderPort: ${e.message}")
            closeConnection()
        }
    }
    
    /**
     * Closes the connection to the leader node.
     */
    private fun closeConnection() {
        try {
            input?.close()
            output?.close()
            socket?.close()
        } catch (e: Exception) {
            log.warn("Error closing connection to leader: ${e.message}")
        } finally {
            input = null
            output = null
            socket = null
        }
    }
}
