package com.distributed.keyvalue.chapter1

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequest
import com.distributed.keyvalue.chapter1.store.FollowerNode
import com.distributed.keyvalue.chapter1.store.LeaderNode
import com.distributed.keyvalue.chapter1.store.Node
import com.distributed.keyvalue.chapter1.store.simple.SimpleFollowerNode
import com.distributed.keyvalue.chapter1.store.simple.SimpleInMemoryKeyValueStore
import com.distributed.keyvalue.chapter1.store.simple.SimpleInMemoryWAL
import com.distributed.keyvalue.chapter1.store.simple.SimpleLeaderNode
import com.distributed.keyvalue.chapter1.store.simple.SimpleLogEntry
import mu.KotlinLogging
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
 */

private val log = KotlinLogging.logger { }

fun main(args: Array<String>) {
    log.info("Chapter 1: Data Replication")

    // Parse command-line arguments
    val arguments = parseArguments(args)

    // Validate arguments
    if (!validateArguments(arguments)) {
        printUsage()
        return
    }

    // Create node based on role
    val node = createNode(arguments)

    // Start the node
    node.start()

    // Set up a shutdown hook to stop the node gracefully
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down node...")
        node.stop()
    })

    // Keep the application running
    log.info("Node started. Press Ctrl+C to exit.")

    // Simple interactive console for demonstration
    val scanner = Scanner(System.`in`)
    val executor = Executors.newSingleThreadExecutor()

    while (true) {
        try {
            print("> ")
            val input = scanner.nextLine()

            if (input.equals("exit", ignoreCase = true)) {
                break
            }

            // Parse the input command
            val parts = input.trim().split(" ", limit = 2)
            if (parts.isEmpty()) {
                log.info("Error: Empty command")
                continue
            }
            
            // Convert the string command to a byte array format that SimpleRequestCommand can parse
            val commandBytes = when (parts[0].uppercase()) {
                "GET" -> {
                    if (parts.size < 2) {
                        log.info("Error: GET command requires a key")
                        continue
                    }
                    byteArrayOf(0) + parts[1].toByteArray()
                }
                "PUT" -> {
                    if (parts.size < 2) {
                        log.info("Error: PUT command requires key:value")
                        continue
                    }
                    val keyValue = parts[1]
                    if (!keyValue.contains(":")) {
                        log.info("Error: PUT command format should be 'PUT key:value'")
                        continue
                    }
                    byteArrayOf(1) + keyValue.toByteArray()
                }
                "DELETE" -> {
                    if (parts.size < 2) {
                        log.info("Error: DELETE command requires a key")
                        continue
                    }
                    byteArrayOf(2) + parts[1].toByteArray()
                }
                else -> {
                    log.info("Error: Unknown command '${parts[0]}'. Valid commands are GET, PUT, DELETE")
                    continue
                }
            }
            
            // Create a request from the parsed command
            val request: Request = SimpleRequest(
                id = UUID.randomUUID().toString(),
                command = commandBytes
            )

            // Process the request
            val responseFuture = node.process(request)
            val response = responseFuture.get(5, TimeUnit.SECONDS)

            // Print the response
            if (response.success) {
                log.info("Success: ${response.result?.toString(Charsets.UTF_8) ?: "null"}")
            } else {
                log.info("Error: ${response.errorMessage}")
            }
        } catch (e: Exception) {
            log.info("Error: ${e.message}")
        }
    }

    // Shutdown the application
    log.info("Exiting...")
    node.stop()
    System.exit(0)

    // Wait for the executor to finish
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
}

/**
 * Parses command-line arguments into a map.
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
 * Validates the command-line arguments.
 */
fun validateArguments(arguments: Map<String, String>): Boolean {
    // Check required arguments for all roles
    if (!arguments.containsKey("role") || !arguments.containsKey("host") || !arguments.containsKey("port")) {
        return false
    }

    // Check role-specific arguments
    val role = arguments["role"]
    if (role == "follower" && (!arguments.containsKey("leader-host") || !arguments.containsKey("leader-port"))) {
        return false
    }

    return true
}

/**
 * Prints usage information.
 */
fun printUsage() {
    log.info("Usage: java -jar chapter1-data-replication.jar [options]")
    log.info("Options:")
    log.info("  --role=<leader|follower>     The role of this node (required)")
    log.info("  --host=<hostname>            The hostname or IP address this node will listen on (required)")
    log.info("  --port=<port>                The port this node will listen on (required)")
    log.info("  --leader-host=<hostname>     The hostname or IP address of the leader node (required for follower role)")
    log.info("  --leader-port=<port>         The port of the leader node (required for follower role)")
}

/**
 * Creates a node based on the specified role.
 */
fun createNode(arguments: Map<String, String>): Node {
    val role = arguments["role"]!!
    val host = arguments["host"]!!
    val port = arguments["port"]!!.toInt()

    // Create a WAL for the node
    val wal = SimpleInMemoryWAL()

    // Create a node based on the role
    return when (role) {
        "leader" -> {
            log.info("Creating leader node at $host:$port")
            // In a real implementation, we would set up a server to listen for connections
            // For simplicity, we'll just create a leader node with no followers
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

            // Create a follower node
            // In a real implementation, we would establish a connection to the leader
            // For this simplified example, we'll just create a follower node and store the leader's address
            val followerNode = SimpleFollowerNode(
                id = "$host:$port",
                keyValueStore = SimpleInMemoryKeyValueStore(),
                wal = wal
            )

            // Store the leader's address in metadata for future use
            // This demonstrates how we could extend the implementation in the future
            val leaderAddress = "$leaderHost:$leaderPort"
            log.info("Stored leader address: $leaderAddress for future connection")

            followerNode
        }

        else -> {
            throw IllegalArgumentException("Invalid role: $role")
        }
    }
}
