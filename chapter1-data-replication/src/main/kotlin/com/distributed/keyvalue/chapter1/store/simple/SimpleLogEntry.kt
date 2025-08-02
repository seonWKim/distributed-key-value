package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.store.LogEntry

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