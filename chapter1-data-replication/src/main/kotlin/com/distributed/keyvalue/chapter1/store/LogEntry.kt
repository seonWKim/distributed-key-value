package com.distributed.keyvalue.chapter1.store

/**
 * Represents an entry in the log.
 * Log entries are the basic unit of replication in the system.
 */
interface LogEntry {
    /**
     * Unique identifier for the log entry.
     */
    val id: Long

    /**
     * The term/generation when this entry was created.
     * Used to maintain consistency in leader changes.
     */
    val term: Long

    /**
     * The actual command/data to be replicated.
     */
    val data: ByteArray

    /**
     * Optional metadata for the log entry.
     */
    val metadata: Map<String, String>
}