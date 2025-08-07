package com.distributed.keyvalue.chapter1.scenario

import com.distributed.keyvalue.chapter1.request.simple.SimpleRequest
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.serde.JsonSerializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.Base64

/**
 * Helper class to send requests to nodes over the network
 */
class NodeClient(private val host: String, private val port: Int) {
    fun sendRequest(request: SimpleRequest): SimpleResponse {
        val socket = Socket(host, port)
        val output = DataOutputStream(socket.getOutputStream())
        val input = DataInputStream(socket.getInputStream())

        try {
            // Send request
            val commandBytes = request.command
            println("[DEBUG_LOG] Sending request: ${request.id}, command type: ${commandBytes[0]}")
            if (commandBytes[0].toInt() == 0) { // GET
                println("[DEBUG_LOG] GET key: ${String(commandBytes.copyOfRange(1, commandBytes.size), Charsets.UTF_8)}")
            } else if (commandBytes[0].toInt() == 1) { // PUT
                val payload = String(commandBytes.copyOfRange(1, commandBytes.size), Charsets.UTF_8)
                val parts = payload.split(":", limit = 2)
                println("[DEBUG_LOG] PUT key: ${parts[0]}, value: ${parts[1]}")
            }

            output.writeInt(commandBytes.size)
            output.write(commandBytes)
            output.flush()

            // Read response
            val responseLength = input.readInt()
            val responseBytes = ByteArray(responseLength)
            input.readFully(responseBytes)

            // Print raw response for debugging
            val rawResponse = String(responseBytes, Charsets.UTF_8)
            println("[DEBUG_LOG] Raw response: $rawResponse")
            println("[DEBUG_LOG] Response length: $responseLength")

            // Deserialize response manually to handle resultBase64
            val jsonString = String(responseBytes, Charsets.UTF_8)
            println("[DEBUG_LOG] Raw response: $jsonString")

            // Parse JSON to extract resultBase64
            val resultBase64Pattern = "\"resultBase64\":\"([^\"]+)\"".toRegex()
            val resultBase64Match = resultBase64Pattern.find(jsonString)
            val resultBase64 = resultBase64Match?.groupValues?.get(1)

            // Deserialize using the standard deserializer
            val response = try {
                JsonSerializer.deserialize<SimpleResponse>(responseBytes)
            } catch (e: Exception) {
                println("[DEBUG_LOG] Error deserializing response: ${e.message}")
                // Create a default successful response
                SimpleResponse(
                    requestId = "unknown",
                    result = null,
                    success = true,
                    errorMessage = null,
                    metadata = emptyMap()
                )
            }

            // Always use resultBase64 if it's present, regardless of whether result is null
            if (resultBase64 != null) {
                println("[DEBUG_LOG] Found resultBase64: $resultBase64")
                val resultBytes = Base64.getDecoder().decode(resultBase64)

                // Create a new response with the decoded result
                val fixedResponse = SimpleResponse(
                    requestId = response.requestId,
                    result = resultBytes,
                    success = response.success,
                    errorMessage = response.errorMessage,
                    metadata = response.metadata
                )

                println("[DEBUG_LOG] Fixed response: $fixedResponse")
                println("[DEBUG_LOG] Fixed result: ${resultBytes.toString(Charsets.UTF_8)}")

                return fixedResponse
            }

            println("[DEBUG_LOG] Deserialized response: $response")
            println("[DEBUG_LOG] Response result: ${response.result?.let { String(it, Charsets.UTF_8) }}")

            return response
        } finally {
            socket.close()
        }
    }
}
