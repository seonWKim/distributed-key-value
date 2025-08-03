package com.distributed.keyvalue.chapter1.serde

/**
 * Interface for serializing and deserializing objects.
 * This provides a common contract for different serialization implementations.
 */
interface Serializer {
    /**
     * Serializes an object to a ByteArray.
     *
     * @param obj The object to serialize
     * @return The serialized object as a ByteArray
     */
    fun <T> serialize(obj: T): ByteArray

    /**
     * Deserializes a ByteArray to an object of type T.
     *
     * @param bytes The ByteArray to deserialize
     * @param clazz The class of the object to deserialize to
     * @return The deserialized object
     */
    fun <T> deserialize(bytes: ByteArray, clazz: Class<T>): T

    /**
     * Deserializes a ByteArray to an object of type T using a type parameter.
     * This is a convenience method for implementations to provide type-safe deserialization.
     *
     * @param bytes The ByteArray to deserialize
     * @return The deserialized object
     */
    fun <T : Any> deserialize(bytes: ByteArray, kClass: kotlin.reflect.KClass<T>): T
}
