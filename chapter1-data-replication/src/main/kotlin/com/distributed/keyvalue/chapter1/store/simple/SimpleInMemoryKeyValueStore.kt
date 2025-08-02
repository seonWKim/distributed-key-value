package com.distributed.keyvalue.chapter1.store.simple

import com.distributed.keyvalue.chapter1.store.KeyValueStore

/**
 * Simple in-memory implementation of the KeyValueStore interface.
 * This is just for demonstration purposes.
 */
class SimpleInMemoryKeyValueStore : KeyValueStore {
    private val store = mutableMapOf<ByteArrayKey, ByteArray>()
    private val versions = mutableMapOf<ByteArrayKey, Map<String, Long>>()

    override fun get(key: ByteArray): ByteArray? {
        return store[ByteArrayKey(key)]
    }

    override fun put(key: ByteArray, value: ByteArray, version: Map<String, Long>?): ByteArray? {
        val byteArrayKey = ByteArrayKey(key)
        val previousValue = store[byteArrayKey]
        store[byteArrayKey] = value
        if (version != null) {
            versions[byteArrayKey] = version
        }
        return previousValue
    }

    override fun delete(key: ByteArray): ByteArray? {
        val byteArrayKey = ByteArrayKey(key)
        val previousValue = store[byteArrayKey]
        store.remove(byteArrayKey)
        versions.remove(byteArrayKey)
        return previousValue
    }

    override fun keys(): List<ByteArray> {
        return store.keys.map { it.bytes }
    }

    override fun getVersion(key: ByteArray): Map<String, Long>? {
        return versions[ByteArrayKey(key)]
    }

    /**
     * Wrapper class for ByteArray to use as a key in a map.
     * ByteArray doesn't override equals() and hashCode(), so we need to wrap it.
     */
    private data class ByteArrayKey(val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ByteArrayKey

            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }
}