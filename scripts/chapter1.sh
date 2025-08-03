#!/bin/bash

./gradlew chapter1-data-replication:clean
./gradlew chapter1-data-replication:build
java -jar chapter1-data-replication/build/libs/chapter1-data-replication-all.jar --role=leader --host=localhost --port=8080
