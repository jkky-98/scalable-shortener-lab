#!/usr/bin/env bash
set -euo pipefail

DURATION_SECONDS="${1:-60}"
INTERVAL_SECONDS="${2:-1}"
OUTPUT_FILE="${3:-missions/missions-04/results/docker-stats.csv}"

CONTAINERS=(
  "${APP_CONTAINER:-shortener-app-mission-04}"
  "${DB_CONTAINER:-shortener-db-mission-04}"
  "${NGINX_CONTAINER:-shortener-nginx-mission-04}"
)

if [ -n "${EXTRA_CONTAINERS:-}" ]; then
  read -r -a EXTRA_CONTAINER_NAMES <<< "$EXTRA_CONTAINERS"
  CONTAINERS+=("${EXTRA_CONTAINER_NAMES[@]}")
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"

echo "timestamp,container,cpu_percent,mem_usage,mem_percent,net_io,block_io,pids" > "$OUTPUT_FILE"

END_AT=$((SECONDS + DURATION_SECONDS))

while [ "$SECONDS" -lt "$END_AT" ]; do
  TIMESTAMP="$(date -Iseconds)"

  docker stats --no-stream \
    --format "${TIMESTAMP},{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}},{{.NetIO}},{{.BlockIO}},{{.PIDs}}" \
    "${CONTAINERS[@]}" >> "$OUTPUT_FILE"

  sleep "$INTERVAL_SECONDS"
done

echo "Docker stats saved to $OUTPUT_FILE"
