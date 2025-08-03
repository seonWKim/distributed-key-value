package com.distributed.keyvalue.chapter3.clock

/**
 * Interface for a Version Vector.
 * A Version Vector is a specialized Vector Clock used for conflict detection and resolution.
 * It tracks the version of data items across nodes.
 */
interface VersionVector : VectorClock {
    /**
     * Checks if this version vector is concurrent with another version vector.
     * Two version vectors are concurrent if neither one happened before the other.
     *
     * @param other The other version vector to check against
     * @return True if the version vectors are concurrent, false otherwise
     */
    fun isConcurrentWith(other: Map<String, Long>): Boolean

    /**
     * Checks if this version vector dominates another version vector.
     * A version vector dominates another if it has seen all the events the other has seen, plus some more.
     *
     * @param other The other version vector to check against
     * @return True if this version vector dominates the other, false otherwise
     */
    fun dominates(other: Map<String, Long>): Boolean
}
