package com.distributed.keyvalue.chapter1

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequest
import com.distributed.keyvalue.chapter1.serde.JsonSerializer
import com.distributed.keyvalue.chapter1.store.Node
import com.distributed.keyvalue.chapter1.store.simple.SimpleFollowerNode
import com.distributed.keyvalue.chapter1.store.simple.SimpleInMemoryKeyValueStore
import com.distributed.keyvalue.chapter1.store.simple.SimpleInMemoryWAL
import com.distributed.keyvalue.chapter1.store.simple.SimpleLeaderNode
import mu.KotlinLogging
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * NodeInitializer is responsible for initializing and running a distributed key-value store node.
 * It handles argument parsing, node creation, server setup, and client request processing.
 */
class NodeInitializer {
    private val log = KotlinLogging.logger {}
    private var serverSocket: ServerSocket? = null
    private var threadPool = Executors.newCachedThreadPool()
    private var node: Node? = null
    
    /**
     * Initializes and starts a node based on command-line arguments.
     * Returns the created node instance.
     */
    fun initializeNode(args: Array<String>): Node? {
        log.info("Initializing node...")
        
        val arguments = parseArguments(args)
        
        if (!validateArguments(arguments)) {
            printUsage()
            return null
        }
        
        val node = createNode(arguments)
        this.node = node
        node.start()
        
        return node
    }
    
    /**
     * Starts the server to handle client requests.
     * This method blocks until the server is shut down.
     */
    fun startServer(args: Array<String>) {
        val arguments = parseArguments(args)
        
        if (!validateArguments(arguments)) {
            printUsage()
            return
        }
        
        val node = this.node ?: initializeNode(args) ?: return
        val serverPort = arguments["port"]!!.toInt()
        
        serverSocket = ServerSocket(serverPort)
        
        log.info("Node started at port $serverPort. Waiting for binary requests...")
        
        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })
        
        val serverSocket = this.serverSocket ?: return
        
        while (!serverSocket.isClosed) {
            try {
                val clientSocket = serverSocket.accept()
                log.info("Client connected: ${clientSocket.inetAddress.hostAddress}:${clientSocket.port}")
                
                clientSocket.soTimeout = 15_000 // optional read timeout
                
                threadPool.submit {
                    clientSocket.use { socket ->
                        val input = DataInputStream(socket.getInputStream())
                        val output = DataOutputStream(socket.getOutputStream())
                        
                        try {
                            while (true) {
                                val length = try {
                                    input.readInt()
                                } catch (e: EOFException) {
                                    log.info("Error reading request length: $e")
                                    break
                                }
                                
                                // Handle keep-alive messages (0 length)
                                if (length == 0) {
                                    log.debug("Received keep-alive ping from client")
                                    continue
                                }
                                
                                if (length < 0 || length > 10_000) {
                                    log.warn("Invalid request length: $length")
                                    break
                                }
                                
                                val commandBytes = ByteArray(length)
                                input.readFully(commandBytes)
                                
                                val request: Request = SimpleRequest(
                                    id = UUID.randomUUID().toString(),
                                    command = commandBytes
                                )
                                
                                val responseFuture = node.process(request)
                                val response = responseFuture.get(5, TimeUnit.SECONDS)
                                
                                val responseBytes = JsonSerializer.serialize(response)
                                output.writeInt(responseBytes.size)
                                output.write(responseBytes)
                                output.flush()
                            }
                        } catch (e: SocketTimeoutException) {
                            log.warn("Socket timeout from ${socket.inetAddress.hostAddress}")
                        } catch (e: IOException) {
                            log.warn("I/O error: ${e.message}")
                        } catch (e: Exception) {
                            log.error("Unexpected error: ${e.message}", e)
                        }
                        
                        log.info("Client disconnected: ${socket.inetAddress.hostAddress}")
                    }
                }
            } catch (e: IOException) {
                if (serverSocket.isClosed) {
                    break
                }
                log.error("Failed to accept connection: ${e.message}")
            }
        }
        
        log.info("Exiting server...")
        shutdown()
    }
    
    /**
     * Shuts down the server and node.
     */
    fun shutdown() {
        log.info("Shutting down node...")
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            log.warn("Error closing server socket: ${e.message}")
        }
        threadPool.shutdownNow()
        node?.stop()
    }
    
    /**
     * Parses command-line arguments into a key-value map.
     */
    fun parseArguments(args: Array<String>): Map<String, String> {
        val arguments = mutableMapOf<String, String>()
        for (arg in args) {
            if (arg.startsWith("--")) {
                val parts = arg.substring(2).split("=", limit = 2)
                if (parts.size == 2) {
                    arguments[parts[0]] = parts[1]
                }
            }
        }
        return arguments
    }
    
    /**
     * Validates required arguments for leader or follower roles.
     */
    fun validateArguments(arguments: Map<String, String>): Boolean {
        if (!arguments.containsKey("role") || !arguments.containsKey("host") || !arguments.containsKey("port")) {
            return false
        }
        val role = arguments["role"]
        if (role == "follower" && (!arguments.containsKey("leader-host") || !arguments.containsKey("leader-port"))) {
            return false
        }
        return true
    }
    
    /**
     * Prints usage information for the command-line interface.
     */
    fun printUsage() {
        log.info("Usage: java -jar chapter1-data-replication.jar [options]")
        log.info("Options:")
        log.info("  --role=<leader|follower>     The role of this node (required)")
        log.info("  --host=<hostname>            The hostname or IP address this node will listen on (required)")
        log.info("  --port=<port>                The port this node will listen on (required)")
        log.info("  --leader-host=<hostname>     The hostname of the leader node (for followers)")
        log.info("  --leader-port=<port>         The port of the leader node (for followers)")
    }
    
    /**
     * Creates a node based on the specified role and arguments.
     */
    fun createNode(arguments: Map<String, String>): Node {
        val role = arguments["role"]!!
        val host = arguments["host"]!!
        val port = arguments["port"]!!.toInt()
        val wal = SimpleInMemoryWAL()
        
        return when (role) {
            "leader" -> {
                log.info("Creating leader node at $host:$port")
                SimpleLeaderNode(
                    id = "$host:$port",
                    wal = wal,
                    followerProxies = emptyList(),
                    keyValueStore = SimpleInMemoryKeyValueStore()
                )
            }
            
            "follower" -> {
                val leaderHost = arguments["leader-host"]!!
                val leaderPort = arguments["leader-port"]!!.toInt()
                log.info("Creating follower node at $host:$port connecting to leader at $leaderHost:$leaderPort")
                
                val followerNode = SimpleFollowerNode(
                    id = "$host:$port",
                    keyValueStore = SimpleInMemoryKeyValueStore(),
                    wal = wal,
                    leaderHost = leaderHost,
                    leaderPort = leaderPort
                )
                
                log.info("Follower node will connect to leader at $leaderHost:$leaderPort when started")
                
                followerNode
            }
            
            else -> throw IllegalArgumentException("Invalid role: $role")
        }
    }
}
