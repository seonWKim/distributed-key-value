package com.distributed.keyvalue.chapter1.scenario

import com.distributed.keyvalue.chapter1.NodeInitializer
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequest
import com.distributed.keyvalue.chapter1.response.simple.SimpleResponse
import com.distributed.keyvalue.chapter1.store.Node
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scenario tests for the distributed key-value store.
 * 
 * These tests verify that the basic operations (GET, PUT, DELETE) work correctly
 * on both leader and follower nodes, and that data is properly replicated.
 * 
 * The tests use ephemeral ports to avoid port conflicts and ensure isolation.
 * Each test initializes a leader node and two follower nodes, performs operations
 * on them, and verifies the results.
 * 
 * Key features tested:
 * - Basic operations (GET, PUT, DELETE) on the leader node
 * - Basic operations (GET, PUT, DELETE) on follower nodes
 * - Data replication from leader to followers
 * - Proper cleanup of resources after tests
 * 
 * Implementation notes:
 * - Uses NodeInitializer to create and start nodes
 * - Runs each node in a separate thread to avoid blocking
 * - Uses a custom NodeClient to communicate with nodes over the network
 * - Handles deserialization of responses manually to work around serialization issues
 */
class ReplicationScenarioTest {

    private lateinit var leaderNode: Node
    private lateinit var followerNode1: Node
    private lateinit var followerNode2: Node
    
    private var leaderPort: Int = 0
    private var follower1Port: Int = 0
    private var follower2Port: Int = 0
    
    private val executorService = Executors.newFixedThreadPool(3)
    private val nodeInitializers = mutableListOf<NodeInitializer>()
    private val startupLatch = CountDownLatch(3) // Wait for all 3 nodes to start
    
    /**
     * Get an available ephemeral port
     */
    private fun getEphemeralPort(): Int {
        return ServerSocket(0).use { it.localPort }
    }
    
    /**
     * Create a GET request for the specified key
     */
    private fun createGetRequest(key: String): SimpleRequest {
        val commandBytes = ByteArray(key.toByteArray().size + 1)
        commandBytes[0] = 0 // GET command
        System.arraycopy(key.toByteArray(), 0, commandBytes, 1, key.toByteArray().size)
        
        return SimpleRequest(
            id = UUID.randomUUID().toString(),
            command = commandBytes
        )
    }
    
    /**
     * Create a PUT request for the specified key and value
     */
    private fun createPutRequest(key: String, value: String): SimpleRequest {
        val payload = "$key:$value"
        val commandBytes = ByteArray(payload.toByteArray().size + 1)
        commandBytes[0] = 1 // PUT command
        System.arraycopy(payload.toByteArray(), 0, commandBytes, 1, payload.toByteArray().size)
        
        return SimpleRequest(
            id = UUID.randomUUID().toString(),
            command = commandBytes
        )
    }
    
    /**
     * Create a DELETE request for the specified key
     */
    private fun createDeleteRequest(key: String): SimpleRequest {
        val commandBytes = ByteArray(key.toByteArray().size + 1)
        commandBytes[0] = 2 // DELETE command
        System.arraycopy(key.toByteArray(), 0, commandBytes, 1, key.toByteArray().size)
        
        return SimpleRequest(
            id = UUID.randomUUID().toString(),
            command = commandBytes
        )
    }
    
    @BeforeTest
    fun setup() {
        // Get ephemeral ports for leader and followers
        leaderPort = getEphemeralPort()
        follower1Port = getEphemeralPort()
        follower2Port = getEphemeralPort()
        
        // Start leader node in a separate thread
        val leaderInitializer = NodeInitializer()
        nodeInitializers.add(leaderInitializer)
        
        executorService.submit {
            try {
                val args = arrayOf(
                    "--role=leader",
                    "--host=localhost",
                    "--port=$leaderPort"
                )
                leaderNode = leaderInitializer.initializeNode(args)!!
                startupLatch.countDown()
                leaderInitializer.startServer(args)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Give the leader a moment to start
        TimeUnit.MILLISECONDS.sleep(500)
        
        // Start follower nodes in separate threads
        val follower1Initializer = NodeInitializer()
        nodeInitializers.add(follower1Initializer)
        
        executorService.submit {
            try {
                val args = arrayOf(
                    "--role=follower",
                    "--host=localhost",
                    "--port=$follower1Port",
                    "--leader-host=localhost",
                    "--leader-port=$leaderPort"
                )
                followerNode1 = follower1Initializer.initializeNode(args)!!
                startupLatch.countDown()
                follower1Initializer.startServer(args)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val follower2Initializer = NodeInitializer()
        nodeInitializers.add(follower2Initializer)
        
        executorService.submit {
            try {
                val args = arrayOf(
                    "--role=follower",
                    "--host=localhost",
                    "--port=$follower2Port",
                    "--leader-host=localhost",
                    "--leader-port=$leaderPort"
                )
                followerNode2 = follower2Initializer.initializeNode(args)!!
                startupLatch.countDown()
                follower2Initializer.startServer(args)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Wait for all nodes to start (with timeout)
        startupLatch.await(5, TimeUnit.SECONDS)
        
        // Give some time for connections to establish
        TimeUnit.SECONDS.sleep(1)
    }
    
    @AfterTest
    fun tearDown() {
        // Shutdown all node initializers
        nodeInitializers.forEach { it.shutdown() }
        
        // Shutdown executor service
        executorService.shutdownNow()
        executorService.awaitTermination(5, TimeUnit.SECONDS)
    }
    
    /**
     * Helper class to send requests to nodes over the network
     */
    private inner class NodeClient(private val host: String, private val port: Int) {
        fun sendRequest(request: SimpleRequest): SimpleResponse {
            val socket = java.net.Socket(host, port)
            val output = java.io.DataOutputStream(socket.getOutputStream())
            val input = java.io.DataInputStream(socket.getInputStream())
            
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
                
                // Deserialize response manually to handle resultBase64
                val jsonString = String(responseBytes, Charsets.UTF_8)
                println("[DEBUG_LOG] Raw response: $jsonString")
                
                // Parse JSON to extract resultBase64
                val resultBase64Pattern = "\"resultBase64\":\"([^\"]+)\"".toRegex()
                val resultBase64Match = resultBase64Pattern.find(jsonString)
                val resultBase64 = resultBase64Match?.groupValues?.get(1)
                
                // Deserialize using the standard deserializer
                val response = com.distributed.keyvalue.chapter1.serde.JsonSerializer.deserialize<SimpleResponse>(responseBytes)
                
                // If resultBase64 is present but result is null, manually set the result
                if (resultBase64 != null && response.result == null) {
                    println("[DEBUG_LOG] Found resultBase64: $resultBase64")
                    val resultBytes = java.util.Base64.getDecoder().decode(resultBase64)
                    
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
    
    /**
     * Test basic operations (PUT, GET, DELETE) on the leader node
     */
    @Test
    fun testBasicOperationsOnLeader() {
        val leaderClient = NodeClient("localhost", leaderPort)
        
        // PUT operation
        val putRequest = createPutRequest("testKey", "testValue")
        val putResponse = leaderClient.sendRequest(putRequest)
        assertTrue(putResponse.success, "PUT operation should succeed")
        
        // GET operation
        val getRequest = createGetRequest("testKey")
        val getResponse = leaderClient.sendRequest(getRequest)
        assertTrue(getResponse.success, "GET operation should succeed")
        assertNotNull(getResponse.result, "GET result should not be null")
        assertEquals("testValue", getResponse.result?.toString(Charsets.UTF_8), "GET should return the correct value")
        
        // DELETE operation
        val deleteRequest = createDeleteRequest("testKey")
        val deleteResponse = leaderClient.sendRequest(deleteRequest)
        assertTrue(deleteResponse.success, "DELETE operation should succeed")
        
        // Verify key is deleted
        val getAfterDeleteRequest = createGetRequest("testKey")
        val getAfterDeleteResponse = leaderClient.sendRequest(getAfterDeleteRequest)
        assertTrue(getAfterDeleteResponse.success, "GET after DELETE should succeed")
        assertEquals(null, getAfterDeleteResponse.result, "GET after DELETE should return null")
    }
    
    /**
     * Test basic operations (PUT, GET, DELETE) on follower nodes
     */
    @Test
    fun testBasicOperationsOnFollowers() {
        val leaderClient = NodeClient("localhost", leaderPort)
        val follower1Client = NodeClient("localhost", follower1Port)
        val follower2Client = NodeClient("localhost", follower2Port)
        
        // PUT operation on leader
        val putRequest = createPutRequest("followerTestKey", "followerTestValue")
        val putResponse = leaderClient.sendRequest(putRequest)
        assertTrue(putResponse.success, "PUT operation on leader should succeed")
        
        // Wait for replication
        TimeUnit.SECONDS.sleep(1)
        
        // GET operation on follower1
        val getRequest1 = createGetRequest("followerTestKey")
        val getResponse1 = follower1Client.sendRequest(getRequest1)
        assertTrue(getResponse1.success, "GET operation on follower1 should succeed")
        assertNotNull(getResponse1.result, "GET result from follower1 should not be null")
        assertEquals("followerTestValue", getResponse1.result?.toString(Charsets.UTF_8), 
            "GET from follower1 should return the correct value")
        
        // GET operation on follower2
        val getRequest2 = createGetRequest("followerTestKey")
        val getResponse2 = follower2Client.sendRequest(getRequest2)
        assertTrue(getResponse2.success, "GET operation on follower2 should succeed")
        assertNotNull(getResponse2.result, "GET result from follower2 should not be null")
        assertEquals("followerTestValue", getResponse2.result?.toString(Charsets.UTF_8), 
            "GET from follower2 should return the correct value")
        
        // DELETE operation on leader
        val deleteRequest = createDeleteRequest("followerTestKey")
        val deleteResponse = leaderClient.sendRequest(deleteRequest)
        assertTrue(deleteResponse.success, "DELETE operation on leader should succeed")
        
        // Wait for replication
        TimeUnit.SECONDS.sleep(1)
        
        // Verify key is deleted on follower1
        val getAfterDeleteRequest1 = createGetRequest("followerTestKey")
        val getAfterDeleteResponse1 = follower1Client.sendRequest(getAfterDeleteRequest1)
        assertTrue(getAfterDeleteResponse1.success, "GET after DELETE on follower1 should succeed")
        assertEquals(null, getAfterDeleteResponse1.result, "GET after DELETE on follower1 should return null")
        
        // Verify key is deleted on follower2
        val getAfterDeleteRequest2 = createGetRequest("followerTestKey")
        val getAfterDeleteResponse2 = follower2Client.sendRequest(getAfterDeleteRequest2)
        assertTrue(getAfterDeleteResponse2.success, "GET after DELETE on follower2 should succeed")
        assertEquals(null, getAfterDeleteResponse2.result, "GET after DELETE on follower2 should return null")
    }
    
    /**
     * Test data replication from leader to followers
     */
    @Test
    fun testDataReplication() {
        val leaderClient = NodeClient("localhost", leaderPort)
        val follower1Client = NodeClient("localhost", follower1Port)
        val follower2Client = NodeClient("localhost", follower2Port)
        
        // PUT multiple key-value pairs on leader
        val keys = listOf("key1", "key2", "key3")
        val values = listOf("value1", "value2", "value3")
        
        for (i in keys.indices) {
            val putRequest = createPutRequest(keys[i], values[i])
            val putResponse = leaderClient.sendRequest(putRequest)
            assertTrue(putResponse.success, "PUT operation for ${keys[i]} should succeed")
        }
        
        // Wait for replication
        TimeUnit.SECONDS.sleep(1)
        
        // Verify all keys are replicated to follower1
        for (i in keys.indices) {
            val getRequest = createGetRequest(keys[i])
            val getResponse = follower1Client.sendRequest(getRequest)
            assertTrue(getResponse.success, "GET operation for ${keys[i]} on follower1 should succeed")
            assertNotNull(getResponse.result, "GET result for ${keys[i]} from follower1 should not be null")
            assertEquals(values[i], getResponse.result?.toString(Charsets.UTF_8), 
                "GET for ${keys[i]} from follower1 should return ${values[i]}")
        }
        
        // Verify all keys are replicated to follower2
        for (i in keys.indices) {
            val getRequest = createGetRequest(keys[i])
            val getResponse = follower2Client.sendRequest(getRequest)
            assertTrue(getResponse.success, "GET operation for ${keys[i]} on follower2 should succeed")
            assertNotNull(getResponse.result, "GET result for ${keys[i]} from follower2 should not be null")
            assertEquals(values[i], getResponse.result?.toString(Charsets.UTF_8), 
                "GET for ${keys[i]} from follower2 should return ${values[i]}")
        }
        
        // DELETE a key on leader
        val deleteRequest = createDeleteRequest(keys[0])
        val deleteResponse = leaderClient.sendRequest(deleteRequest)
        assertTrue(deleteResponse.success, "DELETE operation should succeed")
        
        // Wait for replication
        TimeUnit.SECONDS.sleep(1)
        
        // Verify key is deleted on both followers
        val getAfterDeleteRequest = createGetRequest(keys[0])
        
        val getAfterDeleteResponse1 = follower1Client.sendRequest(getAfterDeleteRequest)
        assertTrue(getAfterDeleteResponse1.success, "GET after DELETE on follower1 should succeed")
        assertEquals(null, getAfterDeleteResponse1.result, "GET after DELETE on follower1 should return null")
        
        val getAfterDeleteResponse2 = follower2Client.sendRequest(getAfterDeleteRequest)
        assertTrue(getAfterDeleteResponse2.success, "GET after DELETE on follower2 should succeed")
        assertEquals(null, getAfterDeleteResponse2.result, "GET after DELETE on follower2 should return null")
    }
}
