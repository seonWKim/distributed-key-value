package com.distributed.keyvalue.chapter1.consensus

/**
 * Consensus interfaces for the distributed key-value store.
 * These interfaces define the components needed for implementing consensus
 * algorithms like RAFT in a distributed system.
 */
/**
 * Interface for a state machine.
 * The state machine applies committed log entries to the application state.
 */
interface StateMachine {
    /**
     * Applies a command to the state machine.
     *
     * @param command The command to apply
     * @return The result of applying the command
     */
    fun apply(command: ByteArray): ByteArray

    /**
     * Takes a snapshot of the current state.
     *
     * @return A snapshot of the state machine
     */
    fun takeSnapshot(): ByteArray

    /**
     * Restores the state machine from a snapshot.
     *
     * @param snapshot The snapshot to restore from
     */
    fun restoreSnapshot(snapshot: ByteArray)
}