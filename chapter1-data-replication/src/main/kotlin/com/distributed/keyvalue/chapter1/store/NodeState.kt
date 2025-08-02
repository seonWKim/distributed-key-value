package com.distributed.keyvalue.chapter1.store

/**
 * Represents the state of a node in the cluster.
 */
enum class NodeState {
    FOLLOWER,
    CANDIDATE,
    LEADER
}