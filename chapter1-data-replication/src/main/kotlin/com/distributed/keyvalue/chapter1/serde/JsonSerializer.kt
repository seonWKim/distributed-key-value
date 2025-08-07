package com.distributed.keyvalue.chapter1.serde

import com.distributed.keyvalue.chapter1.response.Response
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.store.LogEntry
import com.distributed.keyvalue.chapter1.store.simple.SimpleLogEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.reflect.KClass

/**
 * Implementation of the Serializer interface using kotlinx.serialization's JSON format.
 * This class provides methods to serialize and deserialize objects using JSON.
 * 
 * Note: Classes that need to be serialized/deserialized must be annotated with @Serializable
 */
class JsonSerializer : Serializer {
    /**
     * Serializes an object to a ByteArray using JSON.
     * Note: This method uses reflection and may not work with all types.
     * For best results, use the inline reified extension function in the companion object.
     *
     * @param obj The object to serialize
     * @return The serialized object as a ByteArray
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> serialize(obj: T): ByteArray {
        // For simplicity, we'll convert the object to a string representation
        // This is a fallback for when we can't use the reified type parameter
        return obj.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Deserializes a ByteArray to an object of type T using Java Class.
     * Note: This method has limited functionality and is primarily for interface compatibility.
     * For best results, use the inline reified extension function in the companion object.
     *
     * @param bytes The ByteArray to deserialize
     * @param clazz The class of the object to deserialize to
     * @return The deserialized object
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        // This is a simplified implementation that may not work for all types
        // It's provided for interface compatibility
        val jsonString = String(bytes, Charsets.UTF_8)
        
        // For primitive types and strings, try to convert directly
        return when {
            clazz == String::class.java -> jsonString as T
            clazz == Int::class.java -> jsonString.toInt() as T
            clazz == Long::class.java -> jsonString.toLong() as T
            clazz == Boolean::class.java -> jsonString.toBoolean() as T
            clazz == Double::class.java -> jsonString.toDouble() as T
            clazz == Float::class.java -> jsonString.toFloat() as T
            else -> throw IllegalArgumentException("Unsupported class: ${clazz.name}. Use the reified extension function instead.")
        }
    }

    /**
     * Deserializes a ByteArray to an object of type T using Kotlin KClass.
     * Note: This method has limited functionality and is primarily for interface compatibility.
     * For best results, use the inline reified extension function in the companion object.
     *
     * @param bytes The ByteArray to deserialize
     * @param kClass The Kotlin class of the object to deserialize to
     * @return The deserialized object
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> deserialize(bytes: ByteArray, kClass: KClass<T>): T {
        // This is a simplified implementation that may not work for all types
        // It's provided for interface compatibility
        val jsonString = String(bytes, Charsets.UTF_8)
        
        // For primitive types and strings, try to convert directly
        return when (kClass) {
            String::class -> jsonString as T
            Int::class -> jsonString.toInt() as T
            Long::class -> jsonString.toLong() as T
            Boolean::class -> jsonString.toBoolean() as T
            Double::class -> jsonString.toDouble() as T
            Float::class -> jsonString.toFloat() as T
            else -> throw IllegalArgumentException("Unsupported class: ${kClass.qualifiedName}. Use the reified extension function instead.")
        }
    }
    
    /**
     * Provides a companion object with extension functions for inline reified type parameters.
     */
    companion object {
        val module = SerializersModule {
            polymorphic(Response::class) {
                subclass(SimpleResponse::class)
            }
            polymorphic(LogEntry::class) {
                subclass(SimpleLogEntry::class)
            }
        }

        /**
         * Serializes an object to a ByteArray using JSON with a reified type parameter.
         *
         * @param obj The object to serialize
         * @return The serialized object as a ByteArray
         */
        inline fun <reified T> serialize(obj: T): ByteArray {
            val json = Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
                serializersModule = module
            }
            return json.encodeToString(obj).toByteArray(Charsets.UTF_8)
        }
        
        /**
         * Deserializes a ByteArray to an object of type T using a reified type parameter.
         *
         * @param bytes The ByteArray to deserialize
         * @return The deserialized object
         */
        inline fun <reified T> deserialize(bytes: ByteArray): T {
            val json = Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
                serializersModule = module
            }
            val jsonString = String(bytes, Charsets.UTF_8)
            return json.decodeFromString<T>(jsonString)
        }
    }
}
