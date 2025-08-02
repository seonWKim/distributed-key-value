package com.distributed.keyvalue.chapter1.store

/**
 * Core interfaces for the distributed key-value store.
 * These interfaces define the fundamental components needed for data replication
 * in a distributed system.
 */
/**
 * Interface for a follower node.
 * Followers replicate logs passively from the leader.
 */
interface FollowerNode : Node {
    /**
     * The leader node this follower is following.
     */
    val leader: LeaderNode?

    /**
     * The last time a heartbeat was received from the leader.
     */
    val lastHeartbeatTime: Long

    /**
     * Processes a heartbeat from the leader.
     *
     * @param term The current term of the leader
     * @param leaderCommit The commit position of the leader
     * @return True if the heartbeat was accepted, false otherwise
     */
    fun processHeartbeat(term: Long, leaderCommit: Long): Boolean

    /**
     * Processes append entries request from the leader.
     *
     * @param term The current term of the leader
     * @param prevLogIndex The index of the log entry immediately preceding new ones
     * @param prevLogTerm The term of the prevLogIndex entry
     * @param entries The log entries to store
     * @param leaderCommit The leader's commit index
     * @return True if the entries were appended, false otherwise
     */
    fun appendEntries(
        term: Long,
        prevLogIndex: Long,
        prevLogTerm: Long,
        entries: List<LogEntry>,
        leaderCommit: Long
    ): Boolean
}