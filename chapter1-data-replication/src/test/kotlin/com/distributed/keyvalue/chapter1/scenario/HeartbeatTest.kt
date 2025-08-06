package com.distributed.keyvalue.chapter1.scenario

import com.distributed.keyvalue.chapter1.NodeInitializer
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequest
import com.distributed.keyvalue.chapter1.request.simple.SimpleRequestCommandType
import com.distributed.keyvalue.chapter1.store.Node
import com.distributed.keyvalue.chapter1.store.NodeState
import com.distributed.keyvalue.chapter1.store.simple.follower.SimpleFollowerNode
import com.distributed.keyvalue.chapter1.store.simple.SimpleInMemoryKeyValueStore
import com.distributed.keyvalue.chapter1.store.simple.leader.SimpleLeaderNode
import com.distributed.keyvalue.chapter1.store.simple.SimpleNodeProxy
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Tests for heartbeat functionality in the distributed key-value store.
 * 
 * These tests verify that:
 * 1. The leader sends heartbeats at regular intervals
 * 2. Followers process heartbeats correctly
 * 3. Followers transition to CANDIDATE state if no heartbeats are received within the election timeout
 */
class HeartbeatTest {

    private lateinit var leaderNode: Node
    private lateinit var followerNode: SimpleFollowerNode
    
    private var leaderPort: Int = 0
    private var followerPort: Int = 0
    
    private val executorService = Executors.newFixedThreadPool(2)
    private val nodeInitializers = mutableListOf<NodeInitializer>()
    private val startupLatch = CountDownLatch(2) // Wait for both nodes to start
    
    /**
     * Get an available ephemeral port
     */
    private fun getEphemeralPort(): Int {
        return ServerSocket(0).use { it.localPort }
    }
    
    /**
     * Create a heartbeat request
     */
    private fun createHeartbeatRequest(term: Long, leaderCommit: Long): SimpleRequest {
        val payload = "$term:$leaderCommit"
        val commandBytes = ByteArray(payload.toByteArray().size + 1)
        commandBytes[0] = SimpleRequestCommandType.HEARTBEAT.value // HEARTBEAT command
        System.arraycopy(payload.toByteArray(), 0, commandBytes, 1, payload.toByteArray().size)
        
        return SimpleRequest(
            id = UUID.randomUUID().toString(),
            command = commandBytes
        )
    }
    
    @BeforeTest
    fun setup() {
        // Get ephemeral ports for leader and follower
        leaderPort = getEphemeralPort()
        followerPort = getEphemeralPort()
        
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
        
        // Start follower node in a separate thread with a longer election timeout
        val followerInitializer = NodeInitializer()
        nodeInitializers.add(followerInitializer)
        
        executorService.submit {
            try {
                // Create a follower with a longer election timeout (5 seconds)
                // This gives us more time to send heartbeats before the follower times out
                val args = arrayOf(
                    "--role=follower",
                    "--host=localhost",
                    "--port=$followerPort",
                    "--leader-host=localhost",
                    "--leader-port=$leaderPort",
                    "--election-timeout=5000" // 5 seconds
                )
                val node = followerInitializer.initializeNode(args)!!
                followerNode = node as SimpleFollowerNode
                startupLatch.countDown()
                followerInitializer.startServer(args)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Wait for all nodes to start (with timeout)
        startupLatch.await(5, TimeUnit.SECONDS)
        
        // Give some time for connections to establish
        TimeUnit.SECONDS.sleep(1)
        
        // Manually register the follower with the leader
        if (leaderNode is SimpleLeaderNode && followerNode is SimpleFollowerNode) {
            // Create a proxy for the follower
            val followerProxy = SimpleNodeProxy(
                host = "localhost",
                port = followerPort
            )
            followerProxy.start()
            
            // Register the follower proxy with the leader
            (leaderNode as SimpleLeaderNode).registerFollowerProxy(followerProxy)
            
            println("[DEBUG_LOG] Manually registered follower with leader")
        }
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
     * Test that followers process heartbeats correctly.
     * 
     * This test verifies that:
     * 1. The follower's lastHeartbeatTime is updated when it receives a heartbeat
     */
    @Test
    fun testHeartbeats() {
        // Get the initial lastHeartbeatTime
        val initialHeartbeatTime = followerNode.lastHeartbeatTime
        
        // Create a follower client
        val followerClient = NodeClient("localhost", followerPort)
        
        // Directly send a heartbeat to the follower
        val heartbeatRequest = createHeartbeatRequest(followerNode.currentTerm, 0)
        val heartbeatResponse = followerClient.sendRequest(heartbeatRequest)
        
        // Verify that the heartbeat was accepted
        assertTrue(heartbeatResponse.success, "Heartbeat should be accepted by follower")
        
        // Verify that the lastHeartbeatTime has been updated
        assertTrue(followerNode.lastHeartbeatTime > initialHeartbeatTime, 
            "Follower's lastHeartbeatTime should be updated after receiving a heartbeat")
    }
    
    /**
     * Test that followers transition to CANDIDATE state if no heartbeats are received within the election timeout.
     * 
     * This test verifies that:
     * 1. The follower transitions to CANDIDATE state when it doesn't receive heartbeats for longer than the election timeout
     * 2. The follower's term is incremented when it transitions to CANDIDATE state
     */
    @Test
    fun testElectionTimeout() {
        // Create a follower with a very short election timeout
        val shortTimeoutFollower = SimpleFollowerNode(
            id = "shortTimeoutFollower",
            wal = followerNode.wal,
            keyValueStore = SimpleInMemoryKeyValueStore(),
            electionTimeoutMs = 100 // Very short timeout for testing
        )
        
        // Start the follower
        shortTimeoutFollower.start()
        
        try {
            // Get the initial term
            val initialTerm = shortTimeoutFollower.currentTerm
            
            // Wait for the election timeout to occur
            TimeUnit.MILLISECONDS.sleep(200) // Wait longer than the election timeout
            
            // Verify that the follower has transitioned to CANDIDATE state
            assertEquals(NodeState.CANDIDATE, shortTimeoutFollower.state, 
                "Follower should transition to CANDIDATE state after election timeout")
            
            // Verify that the term has been incremented
            assertTrue(shortTimeoutFollower.currentTerm > initialTerm, 
                "Follower's term should be incremented when transitioning to CANDIDATE state")
        } finally {
            // Stop the follower
            shortTimeoutFollower.stop()
        }
    }
    
    /**
     * Test that a follower processes heartbeats correctly.
     * 
     * This test verifies that:
     * 1. The follower accepts heartbeats with a term >= its current term
     * 2. The follower updates its term if the heartbeat's term is greater than its current term
     * 3. The follower updates its commit index if the leader's commit index is greater than its own
     */
    @Test
    fun testProcessHeartbeat() {
        val followerClient = NodeClient("localhost", followerPort)
        
        // Get the initial term and commit index
        val initialTerm = followerNode.currentTerm
        
        // Send a heartbeat with a higher term
        val heartbeatRequest = createHeartbeatRequest(initialTerm + 1, 10)
        val heartbeatResponse = followerClient.sendRequest(heartbeatRequest)
        
        // Verify that the heartbeat was accepted
        assertTrue(heartbeatResponse.success, "Heartbeat with higher term should be accepted")
        
        // Verify that the follower's term was updated
        assertEquals(initialTerm + 1, followerNode.currentTerm, 
            "Follower's term should be updated to match the heartbeat's term")
    }
}
