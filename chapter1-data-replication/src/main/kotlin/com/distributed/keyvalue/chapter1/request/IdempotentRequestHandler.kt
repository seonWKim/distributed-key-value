package com.distributed.keyvalue.chapter1.request

import com.distributed.keyvalue.chapter1.response.Response

/**
 * Request handling interfaces for the distributed key-value store.
 * These interfaces define the components needed for efficient and reliable
 * request processing in a distributed system.
 */
/**
 * Interface for an idempotent request handler.
 * Ensures that requests are processed exactly once, even if they are retried.
 */
interface IdempotentRequestHandler : RequestHandler {
    /**
     * Checks if a request has already been processed.
     *
     * @param requestId The ID of the request to check
     * @return True if the request has been processed, false otherwise
     */
    fun hasProcessed(requestId: String): Boolean

    /**
     * Gets the response for a previously processed request.
     *
     * @param requestId The ID of the request
     * @return The response, or null if the request hasn't been processed
     */
    fun getResponse(requestId: String): Response?

    /**
     * Cleans up old request/response pairs to free up memory.
     *
     * @param olderThan Only clean up requests older than this timestamp
     * @return The number of entries cleaned up
     */
    fun cleanup(olderThan: Long): Int
}