package com.distributed.keyvalue.chapter1.store

import com.distributed.keyvalue.chapter1.request.Request
import com.distributed.keyvalue.chapter1.response.Response
import java.util.concurrent.CompletableFuture

/**
 * Interface for a node in the distributed system.
 * A node can be either a leader or a follower.
 */
interface Node {
    /**
     * Unique identifier for the node.
     */
    val id: String

    /**
     * Current state of the node.
     */
    val state: NodeState

    /**
     * The current term/generation of the node.
     */
    val currentTerm: Long

    /**
     * The write-ahead log used by this node.
     */
    val log: WriteAheadLog

    /**
     * Starts the node.
     */
    fun start()

    /**
     * Stops the node.
     */
    fun stop()

    /**
     * Processes a command from a client.
     *
     * @param request The request to process
     * @return A future that completes when the command is processed
     */
    fun process(request: Request): CompletableFuture<Response>
}
