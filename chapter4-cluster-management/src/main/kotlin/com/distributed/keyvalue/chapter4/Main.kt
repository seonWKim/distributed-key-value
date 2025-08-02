package com.distributed.keyvalue.chapter4

import com.distributed.keyvalue.chapter1.WriteAheadLog
import com.distributed.keyvalue.chapter2.KeyRangePartitioner
import com.distributed.keyvalue.chapter3.LamportClock

fun main() {
    println("Chapter 4: Cluster Management")
    
    // Using components from previous chapters
    val wal = WriteAheadLog()
    val partitioner = KeyRangePartitioner(wal)
    val clock = LamportClock()
    
    val partition = partitioner.assignToPartition("node-1")
    val timestamp = clock.tick()
    
    println("Node assigned to partition $partition with timestamp $timestamp")
    
    // Example implementation will go here
}

// Example class for Lease pattern
class LeaseManager(private val clock: LamportClock) {
    private val leases = mutableMapOf<String, Lease>()
    
    fun grantLease(resourceId: String, nodeId: String, durationMs: Long): Lease {
        val timestamp = clock.tick()
        val expiresAt = System.currentTimeMillis() + durationMs
        val lease = Lease(resourceId, nodeId, timestamp, expiresAt)
        leases[resourceId] = lease
        return lease
    }
    
    fun isLeaseValid(resourceId: String, nodeId: String): Boolean {
        val lease = leases[resourceId] ?: return false
        return lease.nodeId == nodeId && System.currentTimeMillis() < lease.expiresAt
    }
}

data class Lease(
    val resourceId: String,
    val nodeId: String,
    val timestamp: Int,
    val expiresAt: Long
)