package com.distributed.keyvalue.chapter3.clock

/**
 * Interface for a Lamport Clock.
 * A Lamport Clock is a simple logical clock that establishes a partial ordering of events.
 * It ensures that if event A happens before event B, then the timestamp of A is less than the timestamp of B.
 */
interface LamportClock : LogicalClock {
    /**
     * Updates the clock based on a received timestamp.
     * The clock is set to max(local, received) + 1.
     *
     * @param receivedTime The timestamp received from another node
     * @return The new time after updating
     */
    fun update(receivedTime: Long): Long
}
