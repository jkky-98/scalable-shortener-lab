import json
import os
import random
import string
import urllib.error
import urllib.request

BASE_URL = os.getenv("BASE_URL", "http://localhost/api")
COUNT = int(os.getenv("SEED_COUNT", "1000"))
OUTPUT_FILE = "keys.json"

saved_keys = []

print(f"Seed {COUNT} short URLs into {BASE_URL}")

for i in range(COUNT):
    random_str = ''.join(random.choices(string.ascii_lowercase, k=10))
    long_url = f"https://www.google.com/search?q={random_str}"

    payload = json.dumps({"url": long_url}).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "User-Agent": "Python-Seed-Script",
    }

    try:
        req = urllib.request.Request(f"{BASE_URL}/shorten", data=payload, headers=headers, method="POST")

        with urllib.request.urlopen(req, timeout=2) as response:
            if response.status == 200:
                response_body = response.read().decode("utf-8")
                data = json.loads(response_body)

                key = data.get("key")
                saved_keys.append(key)

                if (i + 1) % 100 == 0:
                    print(f"   [Process] {i + 1}/{COUNT} done (Last Key: {key})")

    except urllib.error.URLError as e:
        print(f"Request failed: {e}")
    except Exception as e:
        print(f"Error: {e}")

with open(OUTPUT_FILE, "w") as f:
    json.dump(saved_keys, f)

print(f"Saved {len(saved_keys)} keys to {OUTPUT_FILE}")
