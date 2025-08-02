package com.distributed.keyvalue.chapter1.request

import com.distributed.keyvalue.chapter1.interfaces.response.Response
import java.util.concurrent.CompletableFuture

/**
 * Interface for a request pipeline.
 * A request pipeline processes multiple requests concurrently and in order.
 */
interface RequestPipeline {
    /**
     * The maximum number of in-flight requests.
     */
    val maxInFlightRequests: Int

    /**
     * The current number of in-flight requests.
     */
    val inFlightRequestCount: Int

    /**
     * Submits a request to the pipeline.
     *
     * @param request The request to submit
     * @return A future that completes with the response
     */
    fun submit(request: Request): CompletableFuture<Response>

    /**
     * Flushes the pipeline, waiting for all in-flight requests to complete.
     *
     * @return A future that completes when all requests are done
     */
    fun flush(): CompletableFuture<Void>
}