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
 * First Byte -> type (0=GET, 1=PUT, 2=DELETE, 3=HEARTBEAT, 4=APPEND_ENTRIES)
 * Remaining Bytes: UTF-8 strings, delimited by ":"
 * e.g. PUT key:value  => [1, ..., UTF-8("key:value")]
 * e.g. HEARTBEAT term:leaderCommit => [3, ..., UTF-8("term:leaderCommit")]
 * e.g. APPEND_ENTRIES term:prevLogIndex:prevLogTerm:leaderCommit:entries => [4, ..., UTF-8("term:prevLogIndex:prevLogTerm:leaderCommit:entries")]
 */
sealed interface SimpleRequestCommand {
    companion object {
        fun from(command: ByteArray): SimpleRequestCommand {
            require(command.isNotEmpty()) { "Command must not be empty" }

            return when (command[0].toInt()) {
                0 -> {
                    val key = command.copyOfRange(1, command.size)
                    SimpleRequestGetCommand(key)
                }

                1 -> {
                    val payload = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    val (key, value) = payload.split(":", limit = 2)
                    SimpleRequestPutCommand(key.toByteArray(Charsets.UTF_8), value.toByteArray(Charsets.UTF_8))
                }

                2 -> {
                    val key = command.copyOfRange(1, command.size)
                    SimpleRequestDeleteCommand(key)
                }

                3 -> {
                    val payload = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    val parts = payload.split(":", limit = 2)
                    val term = parts[0].toLong()
                    val leaderCommit = parts[1].toLong()
                    SimpleRequestHeartbeatCommand(term, leaderCommit)
                }

                4 -> {
                    val payload = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    val parts = payload.split(":", limit = 5)
                    val term = parts[0].toLong()
                    val prevLogIndex = parts[1].toLong()
                    val prevLogTerm = parts[2].toLong()
                    val leaderCommit = parts[3].toLong()
                    val entriesJson = parts[4]
                    // For simplicity, we'll pass the entries as a JSON string
                    // In a real implementation, we would deserialize this to a List<LogEntry>
                    SimpleRequestAppendEntriesCommand(term, prevLogIndex, prevLogTerm, entriesJson, leaderCommit)
                }

                else -> throw IllegalArgumentException("Unknown command type: ${command[0]}")
            }
        }
    }
}

data class SimpleRequestGetCommand(
    val key: ByteArray
) : SimpleRequestCommand {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleRequestGetCommand

        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        return key.contentHashCode()
    }

    override fun toString(): String {
        return "SimpleRequestGetCommand(key=${key.toString(Charsets.UTF_8)})"
    }
}

data class SimpleRequestPutCommand(
    val key: ByteArray,
    val value: ByteArray
) : SimpleRequestCommand {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleRequestPutCommand

        if (!key.contentEquals(other.key)) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "SimpleRequestPutCommand(key=${key.toString(Charsets.UTF_8)}, value=${value.toString(Charsets.UTF_8)})"
    }
}

data class SimpleRequestDeleteCommand(
    val key: ByteArray
) : SimpleRequestCommand {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleRequestDeleteCommand

        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        return key.contentHashCode()
    }

    override fun toString(): String {
        return "SimpleRequestDeleteCommand(key=${key.toString(Charsets.UTF_8)})"
    }
}

/**
 * Command for sending heartbeats from leader to followers.
 */
data class SimpleRequestHeartbeatCommand(
    val term: Long,
    val leaderCommit: Long
) : SimpleRequestCommand {
    override fun toString(): String {
        return "SimpleRequestHeartbeatCommand(term=$term, leaderCommit=$leaderCommit)"
    }
}

/**
 * Command for replicating log entries from leader to followers.
 */
data class SimpleRequestAppendEntriesCommand(
    val term: Long,
    val prevLogIndex: Long,
    val prevLogTerm: Long,
    val entriesJson: String,
    val leaderCommit: Long
) : SimpleRequestCommand {
    override fun toString(): String {
        return "SimpleRequestAppendEntriesCommand(term=$term, prevLogIndex=$prevLogIndex, prevLogTerm=$prevLogTerm, entriesJson=$entriesJson, leaderCommit=$leaderCommit)"
    }
}
