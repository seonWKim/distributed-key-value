#!/bin/bash

./gradlew chapter1-data-replication:clean
./gradlew chapter1-data-replication:build
java -jar chapter1-data-replication/build/libs/chapter1-data-replication-all.jar --role=follower --host=localhost --port=8082 --leader-host=localhost --leader-port=8080
