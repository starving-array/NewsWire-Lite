import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 100 },
    { duration: '1m', target: 200 },
    { duration: '2m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    errors: ['rate<0.001'],
  },
};

export default function () {
  http.batch([
    ['GET', `${BASE_URL}/api/v1/articles?size=20&sort=id.publicationTimestamp,desc`, null, {
      tags: { type: 'list' },
    }],
    ['GET', `${BASE_URL}/api/v1/articles/search?q=market+news&limit=10`, null, {
      tags: { type: 'search' },
    }],
  ]);

  const res = http.get(`${BASE_URL}/api/v1/articles?size=5`);
  if (res.status === 200) {
    const body = res.json();
    if (body.content && body.content.length > 0) {
      const id = body.content[0].id;
      http.get(`${BASE_URL}/api/v1/articles/${id}`, { tags: { type: 'getById' } });
    }
  }

  check(res, {
    'read status 200': (r) => r.status === 200,
  });
  errorRate.add(res.status !== 200);

  sleep(0.1);
}