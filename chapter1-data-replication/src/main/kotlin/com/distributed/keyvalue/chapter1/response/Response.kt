package com.distributed.keyvalue.chapter1.response

/**
 * Interface for a response.
 * Represents the system's response to a client request.
 */
interface Response {
    /**
     * The ID of the request this response is for.
     */
    val requestId: String

    /**
     * The result of the operation.
     */
    val result: ByteArray

    /**
     * Whether the operation was successful.
     */
    val success: Boolean

    /**
     * Error message if the operation failed.
     */
    val errorMessage: String?

    /**
     * Optional metadata for the response.
     */
    val metadata: Map<String, String>
}