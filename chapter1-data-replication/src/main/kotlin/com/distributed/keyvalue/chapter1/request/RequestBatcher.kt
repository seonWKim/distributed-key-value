package com.distributed.keyvalue.chapter1.request

import com.distributed.keyvalue.chapter1.interfaces.response.Response
import java.util.concurrent.CompletableFuture

/**
 * Interface for a request batcher.
 * A request batcher groups multiple requests into batches for more efficient processing.
 */
interface RequestBatcher {
    /**
     * The maximum batch size.
     */
    val maxBatchSize: Int

    /**
     * The maximum wait time for a batch to be formed.
     */
    val maxBatchWaitTimeMs: Long

    /**
     * Adds a request to the current batch.
     *
     * @param request The request to add
     * @return A future that completes with the response
     */
    fun add(request: Request): CompletableFuture<Response>

    /**
     * Forces the current batch to be processed, even if it's not full.
     *
     * @return A future that completes when the batch is processed
     */
    fun flush(): CompletableFuture<Void>
}