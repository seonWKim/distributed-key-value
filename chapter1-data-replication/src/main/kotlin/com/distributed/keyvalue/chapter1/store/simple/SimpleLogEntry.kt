package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestCommandType
import com.distributed.keyvalue.chapter1.store.LogEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Simple implementation of the LogEntry interface.
 */
@Serializable
data class SimpleLogEntry(
    override val id: Long,
    override val term: Long,
    @Transient
    override val data: ByteArray = ByteArray(1).apply { this[0] = SimpleRequestCommandType.NONE.value },
    override val metadata: Map<String, String>
) : LogEntry {
    // This property is used for serialization/deserialization
    @Serializable
    val dataBase64: String = data.let {
        if (it.isEmpty()) {
            // Use SimpleRequestCommandType.NONE for empty arrays
            SimpleRequestCommandType.NONE.name
        } else if (it.size == 1 && it[0] == SimpleRequestCommandType.NONE.value) {
            // If it's just the NONE command type, use the name directly
            SimpleRequestCommandType.NONE.name
        } else {
            java.util.Base64.getEncoder().encodeToString(it)
        }
    }

    /**
     * Constructor that takes a [dataBase64] string and converts it to a ByteArray
     */
    constructor(
        id: Long,
        dataBase64: String?,
        term: Long,
        metadata: Map<String, String>
    ) : this(
        id = id,
        data = when {
            dataBase64 == null || dataBase64.isEmpty() -> {
                // Create a ByteArray with just the NONE command type
                ByteArray(1).apply { this[0] = SimpleRequestCommandType.NONE.value }
            }
            dataBase64 == SimpleRequestCommandType.NONE.name -> {
                // Create a ByteArray with just the NONE command type
                ByteArray(1).apply { this[0] = SimpleRequestCommandType.NONE.value }
            }
            else -> java.util.Base64.getDecoder().decode(dataBase64)
        },
        term = term,
        metadata = metadata
    )

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
