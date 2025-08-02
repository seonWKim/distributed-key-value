package com.distributed.keyvalue.chapter1.clock

/**
 * Interface for a Generational Clock.
 * A Generational Clock is a resettable clock used to distinguish between leadership epochs.
 * It's incremented when a new leader is elected.
 */
interface GenerationalClock : LogicalClock {
    /**
     * Resets the clock to a new generation.
     * This is typically done when a new leader is elected.
     *
     * @param newGeneration The new generation value
     * @return The new time after resetting
     */
    fun reset(newGeneration: Long): Long

    /**
     * Gets the current generation.
     *
     * @return The current generation
     */
    fun getGeneration(): Long
}