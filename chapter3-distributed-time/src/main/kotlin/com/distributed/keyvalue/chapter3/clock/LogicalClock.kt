package com.distributed.keyvalue.chapter3.clock

/**
 * Interface for a logical clock.
 * Logical clocks are used to establish a partial ordering of events in a distributed system.
 */
interface LogicalClock {
    /**
     * Gets the current time/value of the clock.
     *
     * @return The current time
     */
    fun getTime(): Long

    /**
     * Increments the clock.
     *
     * @return The new time after incrementing
     */
    fun increment(): Long
}
