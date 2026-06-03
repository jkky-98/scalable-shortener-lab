import csv
import json
import math
import sys
from collections import defaultdict
from datetime import datetime

if len(sys.argv) != 3:
    print("Usage: python3 analyze-k6-json.py <k6-json-lines> <output-csv>")
    sys.exit(1)

input_file = sys.argv[1]
output_file = sys.argv[2]

durations_by_second = defaultdict(list)
hits_by_second = defaultdict(int)
misses_by_second = defaultdict(int)
errors_by_second = defaultdict(int)
requests_by_second = defaultdict(int)


def parse_second(value):
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return int(datetime.fromisoformat(value).timestamp())


def percentile(values, pct):
    if not values:
        return ""
    values = sorted(values)
    rank = (len(values) - 1) * pct
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return values[int(rank)]
    return values[lower] + (values[upper] - values[lower]) * (rank - lower)


with open(input_file) as source:
    for line in source:
        if not line.strip():
            continue
        event = json.loads(line)
        if event.get("type") != "Point":
            continue

        metric = event.get("metric")
        data = event.get("data", {})
        second = parse_second(data["time"])
        value = data.get("value", 0)

        if metric == "http_req_duration":
            durations_by_second[second].append(float(value))
        elif metric == "http_reqs":
            requests_by_second[second] += int(value)
        elif metric == "shortener_cache_hit" and value == 1:
            hits_by_second[second] += 1
        elif metric == "shortener_cache_miss" and value == 1:
            misses_by_second[second] += 1
        elif metric == "shortener_cache_error" and value == 1:
            errors_by_second[second] += 1

seconds = sorted(
    set(durations_by_second)
    | set(hits_by_second)
    | set(misses_by_second)
    | set(errors_by_second)
    | set(requests_by_second)
)

with open(output_file, "w", newline="") as target:
    writer = csv.writer(target)
    writer.writerow([
        "timestamp",
        "requests",
        "cache_hits",
        "cache_misses",
        "cache_errors",
        "duration_avg_ms",
        "duration_p95_ms",
        "duration_max_ms",
    ])

    for second in seconds:
        durations = durations_by_second[second]
        avg = sum(durations) / len(durations) if durations else ""
        p95 = percentile(durations, 0.95)
        max_value = max(durations) if durations else ""

        writer.writerow([
            datetime.fromtimestamp(second).isoformat(),
            requests_by_second[second],
            hits_by_second[second],
            misses_by_second[second],
            errors_by_second[second],
            f"{avg:.3f}" if avg != "" else "",
            f"{p95:.3f}" if p95 != "" else "",
            f"{max_value:.3f}" if max_value != "" else "",
        ])

print(f"Wrote {output_file}")
