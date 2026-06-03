import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost/api';
const TARGET_VUS = Number(__ENV.TARGET_VUS || 400);
const HOLD_DURATION = __ENV.HOLD_DURATION || '90s';

const cacheHits = new Rate('shortener_cache_hit');
const cacheMisses = new Rate('shortener_cache_miss');
const cacheErrors = new Rate('shortener_cache_error');

const keys = new SharedArray('valid keys', function () {
  return JSON.parse(open('./keys.json'));
});

export const options = {
  stages: [
    { duration: '10s', target: TARGET_VUS },
    { duration: HOLD_DURATION, target: TARGET_VUS },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const key = keys[Math.floor(Math.random() * keys.length)];

  const res = http.get(`${BASE_URL}/${key}`, {
    redirects: 0,
  });

  check(res, {
    'status is 302': (r) => r.status === 302,
  });

  const cacheStatus = res.headers['X-Shortener-Cache'] || res.headers['x-shortener-cache'] || '';
  cacheHits.add(cacheStatus === 'HIT');
  cacheMisses.add(cacheStatus === 'MISS');
  cacheErrors.add(cacheStatus === 'ERROR');

  sleep(0.01);
}
