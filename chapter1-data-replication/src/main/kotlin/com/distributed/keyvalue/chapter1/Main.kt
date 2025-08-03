package com.distributed.keyvalue.chapter1

import mu.KotlinLogging

/**
 * Main entry point for the distributed key-value store example.
 * This application can be run as either a leader or a follower node.
 *
 * Command-line arguments:
 * --role=<leader|follower>: The role of this node (required)
 * --host=<hostname>: The hostname or IP address this node will listen on (required)
 * --port=<port>: The port this node will listen on (required)
 * --leader-host=<hostname>: The hostname or IP address of the leader node (required for follower role)
 * --leader-port=<port>: The port of the leader node (required for follower role)
 *
 * This server receives binary requests using a 4-byte length prefix and responds with
 * UTF-8 encoded result strings, also prefixed by length.
 */

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) {
    log.info("Chapter 1: Data Replication")
    
    val initializer = NodeInitializer()
    initializer.startServer(args)
}
