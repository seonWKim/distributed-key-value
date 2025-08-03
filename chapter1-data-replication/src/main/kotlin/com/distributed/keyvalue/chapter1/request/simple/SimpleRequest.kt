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

/**
 * First Byte -> type (0=GET, 1=PUT, 2=DELETE)
 * Remaining Bytes: UTF-8 strings, delimited by ":"
 * e.g. PUT key:value  => [1, ..., UTF-8("key:value")]
 */
sealed interface SimpleRequestCommand {
    companion object {
        fun from(command: ByteArray): SimpleRequestCommand {
            require(command.isNotEmpty()) { "Command must not be empty" }

            return when (command[0].toInt()) {
                0 -> {
                    val key = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    SimpleRequestGetCommand(key)
                }
                1 -> {
                    val payload = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    val (key, value) = payload.split(":", limit = 2)
                    SimpleRequestPutCommand(key, value.toByteArray(Charsets.UTF_8))
                }
                2 -> {
                    val key = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    SimpleRequestDeleteCommand(key)
                }
                else -> throw IllegalArgumentException("Unknown command type: ${command[0]}")
            }
        }
    }
}

data class SimpleRequestGetCommand(
    val key: String
) : SimpleRequestCommand

data class SimpleRequestPutCommand(
    val key: String,
    val value: ByteArray
) : SimpleRequestCommand {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleRequestPutCommand

        if (key != other.key) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

data class SimpleRequestDeleteCommand(
    val key: String
) : SimpleRequestCommand
