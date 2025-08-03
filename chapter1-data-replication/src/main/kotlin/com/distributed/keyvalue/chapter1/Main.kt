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
 * Main entry point for the distributed key-value store example.
 * This application can be run as either a leader or a follower node.
 *
 * Command-line arguments:
 * --role=<leader|follower>: The role of this node (required)
 * --host=<hostname>: The hostname or IP address this node will listen on (required)
 * --port=<port>: The port this node will listen on (required)
 * --leader-host=<hostname>: The hostname or IP address of the leader node (required for follower role)
 * --leader-port=<port>: The port of the leader node (required for follower role)
 *
 * This server receives binary requests using a 4-byte length prefix and responds with
 * UTF-8 encoded result strings, also prefixed by length.
 */

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) {
    log.info("Chapter 1: Data Replication")

    val arguments = parseArguments(args)

    if (!validateArguments(arguments)) {
        printUsage()
        return
    }

    val node = createNode(arguments)
    node.start()

    val serverPort = arguments["port"]!!.toInt()
    val serverSocket = ServerSocket(serverPort)
    val threadPool = Executors.newCachedThreadPool()

    log.info("Node started at port $serverPort. Waiting for binary requests...")

    // Graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down node...")
        try {
            serverSocket.close()
        } catch (e: Exception) {
            log.warn("Error closing server socket: ${e.message}")
        }
        threadPool.shutdownNow()
        node.stop()
    })

    while (!serverSocket.isClosed) {
        try {
            val clientSocket = serverSocket.accept()
            log.info("Client connected: ${clientSocket.inetAddress.hostAddress}")

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
                                log.info("Client closed connection")
                                break
                            }

                            if (length <= 0 || length > 10_000) {
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

                            // Use the new serialization component to serialize the response
                            val responseBytes = JsonSerializer.serialize(response)
                            output.writeInt(responseBytes.size)
                            output.write(responseBytes)
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
            if (!serverSocket.isClosed) {
                log.error("Failed to accept connection: ${e.message}")
            }
        }
    }

    log.info("Exiting server...")
    node.stop()
    System.exit(0)
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
                followers = emptyList(),
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
