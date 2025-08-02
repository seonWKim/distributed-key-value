package com.distributed.keyvalue.chapter1.store

/**
 * Interface for a key-value store.
 * The core data structure for storing and retrieving key-value pairs.
 */
interface KeyValueStore {
    /**
     * Gets the value for a key.
     *
     * @param key The key to get
     * @return The value, or null if the key doesn't exist
     */
    fun get(key: ByteArray): ByteArray?

    /**
     * Puts a key-value pair.
     *
     * @param key The key to put
     * @param value The value to put
     * @param version Optional version for conflict resolution
     * @return The previous value, or null if the key didn't exist
     */
    fun put(key: ByteArray, value: ByteArray, version: Map<String, Long>? = null): ByteArray?

    /**
     * Deletes a key.
     *
     * @param key The key to delete
     * @return The previous value, or null if the key didn't exist
     */
    fun delete(key: ByteArray): ByteArray?

    /**
     * Gets all keys in the store.
     *
     * @return A list of all keys
     */
    fun keys(): List<ByteArray>

    /**
     * Gets the version of a key.
     *
     * @param key The key to get the version for
     * @return The version, or null if the key doesn't exist
     */
    fun getVersion(key: ByteArray): Map<String, Long>?
}