package com.distributed.keyvalue.chapter1.request.simple

import com.distributed.keyvalue.chapter1.request.Request

/**
 * Simple implementation of the Request interface.
 * This is a basic implementation for demonstration purposes.
 */
data class SimpleRequest(
    override val id: String,
    override val command: ByteArray,
    override val timestamp: Long = System.currentTimeMillis(),
    override val metadata: Map<String, String> = emptyMap()
) : Request {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleRequest

        if (id != other.id) return false
        if (!command.contentEquals(other.command)) return false
        if (timestamp != other.timestamp) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + command.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}
