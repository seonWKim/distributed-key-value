package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.store.LogEntry
import com.distributed.keyvalue.chapter1.store.WriteAheadLog

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
        val endPosition = minOf(fromPosition + maxEntries.toLong(), entries.size.toLong())
        return entries.subList(
            fromPosition.toInt(),
            endPosition.toInt()
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
