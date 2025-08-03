package com.distributed.keyvalue.chapter3.clock

/**
 * Interface for a Vector Clock.
 * A Vector Clock is a logical clock that captures causality between events in a distributed system.
 * It maintains a vector of counters, one for each node in the system.
 */
interface VectorClock {
    /**
     * Gets the current vector of timestamps.
     *
     * @return A map of node IDs to their respective timestamps
     */
    fun getVector(): Map<String, Long>

    /**
     * Increments the clock for the local node.
     *
     * @param nodeId The ID of the local node
     * @return The updated vector
     */
    fun increment(nodeId: String): Map<String, Long>

    /**
     * Merges this vector clock with another vector clock.
     * The result is a vector where each entry is the maximum of the corresponding entries in the two vectors.
     *
     * @param other The other vector clock to merge with
     * @return The merged vector
     */
    fun merge(other: Map<String, Long>): Map<String, Long>

    /**
     * Compares this vector clock with another vector clock.
     *
     * @param other The other vector clock to compare with
     * @return -1 if this < other, 0 if this and other are concurrent, 1 if this > other
     */
    fun compareTo(other: Map<String, Long>): Int
}
