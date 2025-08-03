package com.distributed.keyvalue.chapter1.response.simple

import com.distributed.keyvalue.chapter1.response.Response
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Simple implementation of the Response interface.
 * This is a basic implementation for demonstration purposes.
 * 
 * This class is annotated with @Serializable to enable serialization/deserialization
 * using kotlinx.serialization.
 */
@Serializable
data class SimpleResponse(
    override val requestId: String,
    @Transient
    override val result: ByteArray? = null,
    override val success: Boolean,
    override val errorMessage: String?,
    override val metadata: Map<String, String> = emptyMap()
) : Response {
    // We need to store the result as a string for serialization
    // since ByteArray is not directly serializable
    @Serializable
    private val resultBase64: String? = result?.let { 
        java.util.Base64.getEncoder().encodeToString(it) 
    }
    
    /**
     * Constructor that takes a resultBase64 string and converts it to a ByteArray
     */
    constructor(
        requestId: String,
        resultBase64: String?,
        success: Boolean,
        errorMessage: String?,
        metadata: Map<String, String> = emptyMap()
    ) : this(
        requestId = requestId,
        result = resultBase64?.let { 
            java.util.Base64.getDecoder().decode(it) 
        },
        success = success,
        errorMessage = errorMessage,
        metadata = metadata
    )
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
