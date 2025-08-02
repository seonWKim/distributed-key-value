package com.distributed.keyvalue.chapter1.consensus

import com.distributed.keyvalue.chapter1.store.LogEntry

/**
 * Interface for a RAFT consensus module.
 * RAFT is a consensus algorithm that is designed to be easy to understand.
 * It's equivalent to Paxos in fault-tolerance and performance.
 */
interface RaftConsensusModule : ConsensusModule {
    /**
     * The ID of the node that this RAFT instance voted for in the current term.
     */
    val votedFor: String?

    /**
     * For leaders: the index of the next log entry to send to each follower.
     * Reinitialized after election.
     */
    val nextIndex: Map<String, Long>

    /**
     * For leaders: the index of the highest log entry known to be replicated on each follower.
     * Reinitialized after election.
     */
    val matchIndex: Map<String, Long>

    /**
     * Processes a RequestVote RPC from a candidate.
     *
     * @param term The candidate's term
     * @param candidateId The candidate requesting the vote
     * @param lastLogIndex The index of the candidate's last log entry
     * @param lastLogTerm The term of the candidate's last log entry
     * @return A pair of (term, voteGranted)
     */
    fun requestVote(
        term: Long,
        candidateId: String,
        lastLogIndex: Long,
        lastLogTerm: Long
    ): Pair<Long, Boolean>

    /**
     * Processes an AppendEntries RPC from the leader.
     *
     * @param term The leader's term
     * @param leaderId The leader's ID
     * @param prevLogIndex The index of the log entry immediately preceding new ones
     * @param prevLogTerm The term of the prevLogIndex entry
     * @param entries The log entries to store
     * @param leaderCommit The leader's commit index
     * @return A pair of (term, success)
     */
    fun appendEntries(
        term: Long,
        leaderId: String,
        prevLogIndex: Long,
        prevLogTerm: Long,
        entries: List<LogEntry>,
        leaderCommit: Long
    ): Pair<Long, Boolean>
}