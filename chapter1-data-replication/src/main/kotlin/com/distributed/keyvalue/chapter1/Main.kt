package com.distributed.keyvalue.chapter1

import com.distributed.keyvalue.chapter1.store.simple.SimpleInMemoryKeyValueStore
import com.distributed.keyvalue.chapter1.store.simple.SimpleInMemoryWAL
import com.distributed.keyvalue.chapter1.store.simple.SimpleLogEntry

/**
 * Main entry point for the distributed key-value store example.
 * This demonstrates how to use the interfaces defined in the project.
 */
fun main() {
    println("Chapter 1: Data Replication")
    println("This project provides interfaces for implementing a distributed key-value store.")
    println("The interfaces are organized into the following categories:")
    println("1. Core interfaces (Node, WAL, etc.)")
    println("2. Clock interfaces (Lamport, Generational, Vector clocks)")
    println("3. Consensus interfaces (RAFT)")
    println("4. Request handling interfaces (Pipeline, Batching, Idempotency)")
    println("\nTo implement a distributed key-value store, you need to:")
    println("1. Implement the interfaces according to your requirements")
    println("2. Create a cluster of nodes")
    println("3. Set up replication between nodes")
    println("4. Handle client requests")

    // Example usage of the interfaces
    val exampleUsage = ExampleUsage()
    exampleUsage.demonstrateBasicUsage()
}

/**
 * Example class that demonstrates how to use the interfaces.
 */
class ExampleUsage {
    /**
     * Demonstrates basic usage of the interfaces.
     */
    fun demonstrateBasicUsage() {
        println("\n=== Example Usage ===")

        // Create a simple in-memory WAL
        val wal = SimpleInMemoryWAL()

        // Create a log entry
        val entry = SimpleLogEntry(1, 1, "SET key1 value1".toByteArray(), emptyMap())

        // Append the entry to the WAL
        val position = wal.append(entry)
        println("Appended entry at position: $position")

        // Read entries from the WAL
        val entries = wal.read(0)
        println("Read ${entries.size} entries from the WAL")

        // Create a simple key-value store
        val kvStore = SimpleInMemoryKeyValueStore()

        // Put a key-value pair
        val previousValue = kvStore.put("key1".toByteArray(), "value1".toByteArray())
        println("Previous value: ${previousValue?.let { String(it) }}")

        // Get a value
        val value = kvStore.get("key1".toByteArray())
        println("Value for key1: ${value?.let { String(it) }}")

        println("\nThis is just a simple example. In a real implementation, you would:")
        println("1. Implement all the interfaces")
        println("2. Set up a cluster of nodes")
        println("3. Implement leader election")
        println("4. Set up replication between nodes")
        println("5. Handle client requests")
        println("6. Implement consensus")
        println("7. Handle failures and recovery")
    }
}

