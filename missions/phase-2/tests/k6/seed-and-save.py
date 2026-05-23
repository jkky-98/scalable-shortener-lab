import urllib.request
import urllib.error
import json
import os
import random
import string

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080/api")
COUNT = 1000  # 생성할 데이터 개수
OUTPUT_FILE = "keys.json"

saved_keys = []

print(f"🚀 {COUNT}개의 데이터를 심고 키를 저장합니다... (No 'requests' lib needed)")

for i in range(COUNT):
    # 1. 랜덤 긴 URL 생성
    random_str = ''.join(random.choices(string.ascii_lowercase, k=10))
    long_url = f"https://www.google.com/search?q={random_str}"

    # 데이터 준비
    payload = json.dumps({"url": long_url}).encode('utf-8')
    headers = {
        "Content-Type": "application/json",
        "User-Agent": "Python-Seed-Script"
    }

    # 2. POST 요청 (urllib 사용)
    try:
        req = urllib.request.Request(f"{BASE_URL}/shorten", data=payload, headers=headers, method='POST')

        with urllib.request.urlopen(req, timeout=2) as response:
            if response.status == 200:
                response_body = response.read().decode('utf-8')
                data = json.loads(response_body)

                key = data.get("key")
                saved_keys.append(key)

                # 100개마다 진행상황 출력
                if (i + 1) % 100 == 0:
                    print(f"   [Process] {i + 1}/{COUNT} 완료 (Last Key: {key})")

    except urllib.error.URLError as e:
        print(f"❌ Request Failed: {e}")
    except Exception as e:
        print(f"❌ Error: {e}")

# 3. 파일로 저장
with open(OUTPUT_FILE, "w") as f:
    json.dump(saved_keys, f)

print(f"\n✅ 완료! 총 {len(saved_keys)}개의 키가 '{OUTPUT_FILE}'에 저장되었습니다.")
