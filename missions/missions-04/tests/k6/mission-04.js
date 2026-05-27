import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost/api';
const TARGET_VUS = Number(__ENV.TARGET_VUS || 400);

const data = new SharedArray('valid keys', function () {
  return JSON.parse(open('./keys.json'));
});

export const options = {
  stages: [
    { duration: '10s', target: 50 },
    { duration: '30s', target: TARGET_VUS },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const randomKey = data[Math.floor(Math.random() * data.length)];

  const res = http.get(`${BASE_URL}/${randomKey}`, {
    redirects: 0,
  });

  check(res, {
    'status is 302': (r) => r.status === 302,
  });

  sleep(0.01);
}
