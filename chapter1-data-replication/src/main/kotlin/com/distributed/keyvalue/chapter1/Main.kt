package com.distributed.keyvalue.chapter1

import com.distributed.keyvalue.chapter1.store.KeyValueStore
import com.distributed.keyvalue.chapter1.store.LogEntry
import com.distributed.keyvalue.chapter1.store.WriteAheadLog

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

/**
 * Simple in-memory implementation of the WriteAheadLog interface.
 * This is just for demonstration purposes and doesn't provide durability.
 */
class SimpleInMemoryWAL : WriteAheadLog {
    private val entries = mutableListOf<LogEntry>()

    override fun append(entry: LogEntry): Long {
        entries.add(entry)
        return entries.size - 1L
    }

    override fun read(fromPosition: Long, maxEntries: Int): List<LogEntry> {
        return entries.subList(
            fromPosition.toInt(),
            minOf(fromPosition.toInt() + maxEntries, entries.size)
        )
    }

    override fun getLastPosition(): Long {
        return if (entries.isEmpty()) -1 else entries.size - 1L
    }

    override fun truncate(upToPosition: Long) {
        if (upToPosition >= 0 && upToPosition < entries.size) {
            val newEntries = entries.subList(upToPosition.toInt() + 1, entries.size)
            entries.clear()
            entries.addAll(newEntries)
        }
    }

    override fun sync() {
        // No-op for in-memory implementation
    }

    override fun close() {
        entries.clear()
    }
}

/**
 * Simple implementation of the LogEntry interface.
 */
data class SimpleLogEntry(
    override val id: Long,
    override val term: Long,
    override val data: ByteArray,
    override val metadata: Map<String, String>
) : LogEntry {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleLogEntry

        if (id != other.id) return false
        if (term != other.term) return false
        if (!data.contentEquals(other.data)) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + term.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Simple in-memory implementation of the KeyValueStore interface.
 * This is just for demonstration purposes.
 */
class SimpleInMemoryKeyValueStore : KeyValueStore {
    private val store = mutableMapOf<ByteArrayKey, ByteArray>()
    private val versions = mutableMapOf<ByteArrayKey, Map<String, Long>>()

    override fun get(key: ByteArray): ByteArray? {
        return store[ByteArrayKey(key)]
    }

    override fun put(key: ByteArray, value: ByteArray, version: Map<String, Long>?): ByteArray? {
        val byteArrayKey = ByteArrayKey(key)
        val previousValue = store[byteArrayKey]
        store[byteArrayKey] = value
        if (version != null) {
            versions[byteArrayKey] = version
        }
        return previousValue
    }

    override fun delete(key: ByteArray): ByteArray? {
        val byteArrayKey = ByteArrayKey(key)
        val previousValue = store[byteArrayKey]
        store.remove(byteArrayKey)
        versions.remove(byteArrayKey)
        return previousValue
    }

    override fun keys(): List<ByteArray> {
        return store.keys.map { it.bytes }
    }

    override fun getVersion(key: ByteArray): Map<String, Long>? {
        return versions[ByteArrayKey(key)]
    }

    /**
     * Wrapper class for ByteArray to use as a key in a map.
     * ByteArray doesn't override equals() and hashCode(), so we need to wrap it.
     */
    private data class ByteArrayKey(val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ByteArrayKey

            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }
}
