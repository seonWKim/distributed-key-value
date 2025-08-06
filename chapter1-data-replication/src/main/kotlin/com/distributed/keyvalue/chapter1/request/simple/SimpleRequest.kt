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

    override fun toString(): String {
        return "SimpleRequest(id='$id', command=${command[0]}, timestamp=$timestamp, metadata=$metadata)"
    }
}

/**
 * Binary protocol format for request and response messages over TCP.
 *
 * ## Structure:
 * - **First Byte**: Message type indicator
 *   - `0` = GET
 *   - `1` = PUT
 *   - `2` = DELETE
 *   - `3` = HEARTBEAT
 *   - `4` = APPEND_ENTRIES
 *
 * - **Remaining Bytes**: UTF-8 encoded string payload, delimited by colons (`:`).
 *   - Fields depend on the message type.
 *   - All fields must be UTF-8 strings separated by colons.
 *
 * ## Message Format Examples:
 *
 * - **PUT key:value**
 *   - Byte sequence: `[1, ..., UTF-8("key:value")]`
 *   - `key` and `value` are strings to insert into the key-value store.
 *
 * - **GET key**
 *   - Byte sequence: `[0, ..., UTF-8("key")]`
 *   - Used to retrieve the value associated with a key.
 *
 * - **DELETE key**
 *   - Byte sequence: `[2, ..., UTF-8("key")]`
 *   - Requests deletion of the given key.
 *
 * - **HEARTBEAT term:leaderCommit**
 *   - Byte sequence: `[3, ..., UTF-8("term:leaderCommit")]`
 *   - Used by the leader to signal liveness and commit index.
 *   - `term`: Current term number.
 *   - `leaderCommit`: Index of highest log entry known to be committed.
 *
 * - **APPEND_ENTRIES term:prevLogIndex:prevLogTerm:leaderCommit:entries**
 *   - Byte sequence: `[4, ..., UTF-8("term:prevLogIndex:prevLogTerm:leaderCommit:entries")]`
 *   - Sent by the leader to replicate log entries.
 *   - `term`: Current term.
 *   - `prevLogIndex`: Index of log entry immediately preceding new ones.
 *   - `prevLogTerm`: Term of the previous log entry.
 *   - `leaderCommit`: Leader’s commit index.
 *   - `entries`: Concatenated string or serialized representation of log entries.
 */
/**
 * Marker interface for all request commands.
 * This interface is kept for backward compatibility.
 * Use SimpleLeaderRequestCommand or SimpleFollowerRequestCommand instead.
 */
interface SimpleRequestCommand

/**
 * Enum representing the different types of commands that can be sent in a SimpleRequest.
 * Each enum value has a corresponding byte value that is used in the binary protocol.
 *
 * - GET: Used to retrieve a value by key
 * - PUT: Used to store a key-value pair
 * - DELETE: Used to remove a key-value pair
 * - HEARTBEAT: Used by the leader to signal liveness to followers
 * - APPEND_ENTRIES: Used by the leader to replicate log entries to followers
 */
enum class SimpleRequestCommandType(val value: Byte) {
    GET(0),
    PUT(1),
    DELETE(2),
    HEARTBEAT(3),
    APPEND_ENTRIES(4),
    REGISTER_FOLLOWER(5);
    
    companion object {
        /**
         * Returns the command type corresponding to the given byte value.
         *
         * @param value The byte value to look up
         * @return The corresponding SimpleRequestCommandType
         * @throws IllegalArgumentException if the byte value doesn't match any command type
         */
        fun fromByte(value: Byte): SimpleRequestCommandType {
            return values().find { it.value == value }
                ?: throw IllegalArgumentException("Unknown command type: $value")
        }
    }
}

data class SimpleRequestGetCommand(
    val key: ByteArray
) : SimpleLeaderRequestCommand, SimpleFollowerRequestCommand {
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
) : SimpleLeaderRequestCommand, SimpleFollowerRequestCommand {
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
) : SimpleLeaderRequestCommand, SimpleFollowerRequestCommand {
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
) : SimpleFollowerRequestCommand {
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
) : SimpleFollowerRequestCommand {
    override fun toString(): String {
        return "SimpleRequestAppendEntriesCommand(term=$term, prevLogIndex=$prevLogIndex, prevLogTerm=$prevLogTerm, entriesJson=$entriesJson, leaderCommit=$leaderCommit)"
    }
}

/**
 * Command for registering a follower with the leader.
 */
data class SimpleRequestRegisterFollower(
    val followerId: String
) : SimpleFollowerRequestCommand {
    override fun toString(): String {
        return "SimpleRequestRegisterFollower(followerId=$followerId)"
    }
}

/**
 * Commands that are processed by the leader node.
 */
sealed interface SimpleLeaderRequestCommand : SimpleRequestCommand {
    companion object {
        fun from(command: ByteArray): SimpleLeaderRequestCommand {
            require(command.isNotEmpty()) { "Command must not be empty" }

            // Convert the first byte to a command type
            return when (val commandType = SimpleRequestCommandType.fromByte(command[0])) {
                SimpleRequestCommandType.GET -> {
                    val key = command.copyOfRange(1, command.size)
                    SimpleRequestGetCommand(key)
                }

                SimpleRequestCommandType.PUT -> {
                    val payload = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    val (key, value) = payload.split(":", limit = 2)
                    SimpleRequestPutCommand(key.toByteArray(Charsets.UTF_8), value.toByteArray(Charsets.UTF_8))
                }

                SimpleRequestCommandType.DELETE -> {
                    val key = command.copyOfRange(1, command.size)
                    SimpleRequestDeleteCommand(key)
                }

                else -> throw IllegalArgumentException("Invalid command type for leader: $commandType")
            }
        }
    }
}

/**
 * Commands that are processed by the follower node.
 */
sealed interface SimpleFollowerRequestCommand : SimpleRequestCommand {
    companion object {
        fun from(command: ByteArray): SimpleFollowerRequestCommand {
            require(command.isNotEmpty()) { "Command must not be empty" }

            // Convert the first byte to a command type
            return when (val commandType = SimpleRequestCommandType.fromByte(command[0])) {
                SimpleRequestCommandType.HEARTBEAT -> {
                    val payload = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    val parts = payload.split(":", limit = 2)
                    val term = parts[0].toLong()
                    val leaderCommit = parts[1].toLong()
                    SimpleRequestHeartbeatCommand(term, leaderCommit)
                }

                SimpleRequestCommandType.APPEND_ENTRIES -> {
                    val payload = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    val parts = payload.split(":", limit = 5)
                    val term = parts[0].toLong()
                    val prevLogIndex = parts[1].toLong()
                    val prevLogTerm = parts[2].toLong()
                    val leaderCommit = parts[3].toLong()
                    val entriesJson = parts[4]
                    SimpleRequestAppendEntriesCommand(term, prevLogIndex, prevLogTerm, entriesJson, leaderCommit)
                }
                
                SimpleRequestCommandType.REGISTER_FOLLOWER -> {
                    val followerId = command.copyOfRange(1, command.size).toString(Charsets.UTF_8)
                    SimpleRequestRegisterFollower(followerId)
                }

                else -> throw IllegalArgumentException("Invalid command type for follower: $commandType")
            }
        }
    }
}
