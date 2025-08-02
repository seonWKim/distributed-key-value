package com.distributed.keyvalue.chapter1.request

import com.distributed.keyvalue.chapter1.interfaces.response.Response
import java.util.concurrent.CompletableFuture

/**
 * Interface for a request handler.
 * Processes client requests and returns responses.
 */
interface RequestHandler {
    /**
     * Handles a request and returns a response.
     *
     * @param request The request to handle
     * @return A future that completes with the response
     */
    fun handle(request: Request): CompletableFuture<Response>
}