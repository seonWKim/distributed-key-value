package com.distributed.keyvalue.chapter1.store

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.response.Response
import java.util.concurrent.CompletableFuture

/**
 * Interface for a proxy that forwards requests to a node (leader or follower).
 * This interface abstracts the network communication with nodes.
 */
interface NodeProxy {
    /**
     * Starts the proxy and establishes a connection to the node.
     */
    fun start() {
        TODO("Not yet implemented")
    }

    /**
     * Stops the proxy and closes the connection to the node.
     */
    fun stop() {
        TODO("Not yet implemented")
    }

    /**
     * Processes a request by forwarding it to the node.
     *
     * @param request The request to process
     * @return A future that completes when the response is received
     */
    fun process(request: Request): CompletableFuture<Response> {
        TODO("Not yet implemented")
    }
}
