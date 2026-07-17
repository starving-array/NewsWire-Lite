import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '1m', target: 10 },
    { duration: '2m', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '2m', target: 100 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.01'],
  },
};

export default function () {
  const headline = `Load test article ${uuidv4().slice(0, 8)}`;
  const payload = JSON.stringify({
    headline: headline,
    summary: 'Performance test article for load testing scenario validation',
    body: 'Detailed body content '.repeat(50),
    source: 'LoadTest',
    publicationTimestamp: new Date().toISOString(),
    category: 'MARKET_MOVEMENTS',
    tags: ['loadtest', 'perf'],
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  let res = http.post(`${BASE_URL}/api/v1/articles`, payload, params);
  check(res, { 'create status 201': (r) => r.status === 201 });
  errorRate.add(res.status !== 201);

  if (res.status === 201) {
    const articleId = res.json().id;

    res = http.get(`${BASE_URL}/api/v1/articles/${articleId}`);
    check(res, { 'get status 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);

    res = http.get(`${BASE_URL}/api/v1/articles?size=10&sort=id.publicationTimestamp,desc`);
    check(res, { 'list status 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);

    res = http.get(`${BASE_URL}/api/v1/articles/search?q=performance+load+test&limit=10`);
    check(res, { 'search status 200': (r) => r.status === 200 });

    res = http.del(`${BASE_URL}/api/v1/articles/${articleId}`);
    check(res, { 'delete status 204': (r) => r.status === 204 });
    errorRate.add(res.status !== 204);
  }

  sleep(1);
}