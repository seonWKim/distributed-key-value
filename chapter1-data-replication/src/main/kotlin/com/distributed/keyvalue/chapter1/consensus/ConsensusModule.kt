package com.distributed.keyvalue.chapter1.consensus

import java.util.concurrent.CompletableFuture

/**
 * Interface for a consensus module.
 * The consensus module is responsible for ensuring that all nodes agree on the
 * same sequence of commands.
 */
interface ConsensusModule {
    /**
     * The current term/generation of the consensus module.
     */
    val currentTerm: Long

    /**
     * The ID of the node that this consensus module believes is the leader.
     */
    val leaderId: String?

    /**
     * The index of the highest log entry known to be committed.
     */
    val commitIndex: Long

    /**
     * The index of the highest log entry applied to the state machine.
     */
    val lastApplied: Long

    /**
     * Starts the consensus module.
     */
    fun start()

    /**
     * Stops the consensus module.
     */
    fun stop()

    /**
     * Submits a command to the consensus module.
     *
     * @param command The command to submit
     * @return A future that completes when the command is committed
     */
    fun submitCommand(command: ByteArray): CompletableFuture<ByteArray>
}