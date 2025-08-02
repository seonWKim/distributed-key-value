package com.distributed.keyvalue.chapter1.store

/**
 * Write-Ahead Log (WAL) interface.
 * WAL is a persistent, append-only log that captures every write operation
 * before applying it to the in-memory state.
 */
interface WriteAheadLog {
    /**
     * Appends a new entry to the log.
     *
     * @param entry The log entry to append
     * @return The position of the appended entry
     */
    fun append(entry: LogEntry): Long

    /**
     * Reads entries from the log starting from a specific position.
     *
     * @param fromPosition The position to start reading from
     * @param maxEntries Maximum number of entries to read
     * @return List of log entries
     */
    fun read(fromPosition: Long, maxEntries: Int = Int.MAX_VALUE): List<LogEntry>

    /**
     * Gets the last appended position in the log.
     *
     * @return The position of the last entry
     */
    fun getLastPosition(): Long

    /**
     * Truncates the log up to a specific position.
     * Used for log compaction and cleanup.
     *
     * @param upToPosition Position up to which the log should be truncated
     */
    fun truncate(upToPosition: Long)

    /**
     * Ensures all entries are persisted to stable storage.
     */
    fun sync()

    /**
     * Closes the log and releases any resources.
     */
    fun close()
}