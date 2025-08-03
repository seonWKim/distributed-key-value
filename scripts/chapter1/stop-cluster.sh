#!/bin/bash

set -e

# === Ports used by the cluster ===
PORTS=(20000 20001 20002)

echo "Stopping cluster nodes on ports: ${PORTS[*]}..."

for PORT in "${PORTS[@]}"; do
  PID=$(lsof -ti tcp:$PORT)
  if [ -n "$PID" ]; then
    echo "Killing process on port $PORT (PID=$PID)"
    kill -9 "$PID"
  else
    echo "No process found on port $PORT"
  fi
done

echo "Cluster shutdown complete."
