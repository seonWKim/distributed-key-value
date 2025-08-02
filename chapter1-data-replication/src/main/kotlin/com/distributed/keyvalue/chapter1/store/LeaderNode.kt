package com.distributed.keyvalue.chapter1.store

import java.util.concurrent.CompletableFuture

/**
 * Interface for a leader node.
 * The leader is responsible for accepting writes and replicating them to followers.
 */
interface LeaderNode : Node {
    /**
     * The list of follower nodes.
     */
    val followers: List<FollowerNode>

    /**
     * The low watermark position in the log.
     * Entries before this position have been replicated to all followers.
     */
    val lowWatermark: Long

    /**
     * The high watermark position in the log.
     * Entries before this position have been replicated to a quorum of nodes.
     */
    val highWatermark: Long

    /**
     * Sends heartbeats to all followers.
     */
    fun sendHeartbeats()

    /**
     * Replicates log entries to followers.
     *
     * @param fromPosition The position to start replication from
     * @return A future that completes when replication is done
     */
    fun replicateLog(fromPosition: Long): CompletableFuture<Void>
}