package com.distributed.keyvalue.client

import mu.KotlinLogging
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.*

private val log = KotlinLogging.logger {}

/**
 * Client for the distributed key-value store.
 * This client can connect to either a leader or follower node and send commands.
 *
 * Command-line arguments:
 * --host=<hostname>: The hostname or IP address of the node to connect to (default: localhost)
 * --port=<port>: The port of the node to connect to (default: 8080)
 *
 * Commands:
 * GET <key>: Retrieves the value for the specified key
 * PUT <key>:<value>: Stores the key-value pair
 * DELETE <key>: Removes the key-value pair
 * EXIT: Exits the client
 */
fun main(args: Array<String>) {
    log.info("Distributed Key-Value Store Client")

    val arguments = parseArguments(args)
    val host = arguments["host"] ?: "localhost"
    val port = arguments["port"]?.toIntOrNull() ?: 8080

    log.info("Connecting to node at $host:$port")

    try {
        Socket(host, port).use { socket ->
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            log.info("Connected to node at $host:$port")
            log.info("Enter commands (GET <key>, PUT <key>:<value>, DELETE <key>, EXIT)")

            val scanner = Scanner(System.`in`)
            while (true) {
                print("> ")
                val line = scanner.nextLine().trim()

                if (line.equals("EXIT", ignoreCase = true)) {
                    log.info("Exiting client...")
                    break
                }

                try {
                    val commandBytes = parseCommand(line)
                    if (commandBytes != null) {
                        // Send the command to the node
                        output.writeInt(commandBytes.size)
                        output.write(commandBytes)
                        output.flush()

                        // Read the response
                        val responseLength = input.readInt()
                        val responseBytes = ByteArray(responseLength)
                        input.readFully(responseBytes)
                        val responseString = String(responseBytes, Charsets.UTF_8)

                        println(responseString)
                    }
                } catch (e: Exception) {
                    log.error("Error processing command: ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        log.error("Error connecting to node: ${e.message}")
    }
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
 * Parses a command string into a binary command.
 * Returns null if the command is invalid.
 */
fun parseCommand(command: String): ByteArray? {
    val parts = command.split(" ", limit = 2)
    if (parts.size < 2) {
        log.warn("Invalid command format: $command")
        return null
    }

    val commandType = parts[0].uppercase()
    val payload = parts[1]

    return when (commandType) {
        "GET" -> {
            val keyBytes = payload.toByteArray(Charsets.UTF_8)
            val result = ByteArray(1 + keyBytes.size)
            result[0] = 0 // GET command type
            System.arraycopy(keyBytes, 0, result, 1, keyBytes.size)
            result
        }
        "PUT" -> {
            if (!payload.contains(":")) {
                log.warn("Invalid PUT command format. Expected: PUT key:value")
                return null
            }
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            val result = ByteArray(1 + payloadBytes.size)
            result[0] = 1 // PUT command type
            System.arraycopy(payloadBytes, 0, result, 1, payloadBytes.size)
            result
        }
        "DELETE" -> {
            val keyBytes = payload.toByteArray(Charsets.UTF_8)
            val result = ByteArray(1 + keyBytes.size)
            result[0] = 2 // DELETE command type
            System.arraycopy(keyBytes, 0, result, 1, keyBytes.size)
            result
        }
        else -> {
            log.warn("Unknown command type: $commandType")
            null
        }
    }
}
