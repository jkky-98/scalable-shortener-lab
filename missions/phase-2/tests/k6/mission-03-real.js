import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const TARGET_VUS = Number(__ENV.TARGET_VUS || 200);

// 1. 키 파일 로드 (같은 폴더에 있는 keys.json)
const data = new SharedArray('valid keys', function () {
  return JSON.parse(open('./keys.json'));
});

export const options = {
  // 부하 시나리오:
  // 10초 동안 50명까지 증가 -> 30초 동안 200명 유지 -> 10초 동안 감소
  stages: [
    { duration: '10s', target: 50 },
    { duration: '30s', target: TARGET_VUS }, // ★ 여기가 피크 타임
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% 요청이 500ms 안에 끝나야 함 (기준 완화)
  },
};

export default function () {
  // 2. 랜덤 키 뽑기 (1000개 중 하나)
  const randomKey = data[Math.floor(Math.random() * data.length)];

  // 3. GET 요청 (리다이렉트)
  // redirects: 0 으로 설정하여 302 응답 자체를 측정 (구글로 이동 안 함)
  const res = http.get(`${BASE_URL}/${randomKey}`, {
    redirects: 0,
  });

  // 4. 검증: 302 Found가 떠야 성공
  check(res, {
    'status is 302': (r) => r.status === 302,
  });

  // 0.01초 휴식 (너무 빠르면 맥북 네트워크가 먼저 막힘)
  sleep(0.01);
}
