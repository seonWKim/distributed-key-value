package com.distributed.keyvalue.chapter1.response.simple

import com.distributed.keyvalue.chapter1.response.Response

/**
 * Simple implementation of the Response interface.
 * This is a basic implementation for demonstration purposes.
 */
data class SimpleResponse(
    override val requestId: String,
    override val result: ByteArray?,
    override val success: Boolean,
    override val errorMessage: String?,
    override val metadata: Map<String, String> = emptyMap()
) : Response {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleResponse

        if (requestId != other.requestId) return false
        if (!result.contentEquals(other.result)) return false
        if (success != other.success) return false
        if (errorMessage != other.errorMessage) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = requestId.hashCode()
        result1 = 31 * result1 + result.contentHashCode()
        result1 = 31 * result1 + success.hashCode()
        result1 = 31 * result1 + (errorMessage?.hashCode() ?: 0)
        result1 = 31 * result1 + metadata.hashCode()
        return result1
    }
}
