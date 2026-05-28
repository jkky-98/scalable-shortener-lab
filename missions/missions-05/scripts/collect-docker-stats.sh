#!/usr/bin/env bash
set -euo pipefail

DURATION_SECONDS="${1:-60}"
INTERVAL_SECONDS="${2:-3}"
OUTPUT_FILE="${3:-missions/missions-05/results/docker-stats.csv}"
SUMMARY_FILE="${4:-${OUTPUT_FILE%.csv}.summary.txt}"

CONTAINERS=(
  "shortener-nginx-mission-05"
  "shortener-app1-mission-05"
  "shortener-app2-mission-05"
  "shortener-app3-mission-05"
  "shortener-db-mission-05"
)

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

awk -F, '
  NR == 1 { next }
  {
    cpu = $3
    mem = $5
    gsub(/%/, "", cpu)
    gsub(/%/, "", mem)
    sum_cpu[$2] += cpu
    count[$2] += 1
    if (cpu > max_cpu[$2]) max_cpu[$2] = cpu
    if (mem > max_mem[$2]) max_mem[$2] = mem
  }
  END {
    print "container,cpu_avg_percent,cpu_max_percent,mem_max_percent,samples"
    for (container in count) {
      printf "%s,%.2f,%.2f,%.2f,%d\n", container, sum_cpu[container] / count[container], max_cpu[container], max_mem[container], count[container]
    }
  }
' "$OUTPUT_FILE" | tee "$SUMMARY_FILE"

echo "Docker stats summary saved to $SUMMARY_FILE"
