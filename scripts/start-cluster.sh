#!/bin/bash

set -e

# === Configuration ===
JAR_PATH="chapter1-data-replication/build/libs/chapter1-data-replication-all.jar"
LEADER_PORT=20000
FOLLOWER_PORTS=(20001 20002)
LOGS=("leader.log" "follower1.log" "follower2.log")

# === Cleanup Logs ===
echo "Cleaning up old log files..."
for LOG in "${LOGS[@]}"; do
  if [ -f "$LOG" ]; then
    rm -f "$LOG"
    echo "Deleted $LOG"
  fi
done

# === Build ===
echo "Cleaning and building project..."
./gradlew chapter1-data-replication:clean
./gradlew chapter1-data-replication:build -x test

# === Start Leader ===
echo "Starting leader on port $LEADER_PORT..."
nohup java -jar $JAR_PATH --role=leader --host=localhost --port=$LEADER_PORT > leader.log 2>&1 &

# === Start Followers ===
for i in "${!FOLLOWER_PORTS[@]}"; do
  PORT=${FOLLOWER_PORTS[$i]}
  LOG=${LOGS[$i+1]}  # +1 to skip leader.log
  echo "Starting follower $((i+1)) on port $PORT..."
  nohup java -jar $JAR_PATH \
    --role=follower \
    --host=localhost \
    --port=$PORT \
    --leader-host=localhost \
    --leader-port=$LEADER_PORT > "$LOG" 2>&1 &
done

echo "Cluster started. Logs: ${LOGS[*]}"
