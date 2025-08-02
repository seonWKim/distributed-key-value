package com.distributed.keyvalue.chapter1.request

/**
 * Interface for a request.
 * Represents a client request to the distributed system.
 */
interface Request {
    /**
     * Unique identifier for the request.
     */
    val id: String

    /**
     * The command/operation to be executed.
     */
    val command: ByteArray

    /**
     * Timestamp when the request was created.
     */
    val timestamp: Long

    /**
     * Optional metadata for the request.
     */
    val metadata: Map<String, String>
}