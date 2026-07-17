// Chaos test: simulate OpenSearch outage and verify graceful degradation
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const ARTICLE_SVC = __ENV.ARTICLE_SVC || 'http://localhost:8080';
const SEARCH_SVC = __ENV.SEARCH_SVC || 'http://localhost:8081';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    errors: ['rate<0.1'],
  },
};

export default function () {
  // 1. Create article via article service (direct path, no Kafka)
  const createPayload = JSON.stringify({
    headline: `Chaos test article ${__VU}-${__ITER}`,
    summary: 'Testing graceful degradation during OpenSearch outage',
    body: 'Chaos engineering validation for FNMP resilience patterns',
    source: 'ChaosTest',
    publicationTimestamp: new Date().toISOString(),
    category: 'GENERAL',
  });

  let res = http.post(`${ARTICLE_SVC}/api/v1/articles`, createPayload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { type: 'create' },
  });
  check(res, { 'create 201': (r) => r.status === 201 });
  errorRate.add(res.status !== 201);

  if (res.status === 201) {
    const id = res.json().id;

    // 2. Get by ID (cached path)
    res = http.get(`${ARTICLE_SVC}/api/v1/articles/${id}`, {
      tags: { type: 'getById' },
    });
    check(res, { 'getById 200': (r) => r.status === 200 });

    // 3. Search via search service (OpenSearch - should fall back or return empty)
    res = http.get(`${SEARCH_SVC}/api/v1/articles/search?q=chaos+test&limit=10`, {
      tags: { type: 'search' },
    });
    // During OpenSearch outage, this returns 200 with empty array (graceful degradation)
    check(res, { 'search graceful': (r) => r.status === 200 });
  }

  // 4. List via article service (PG-backed)
  res = http.get(`${ARTICLE_SVC}/api/v1/articles?size=5&sort=id.publicationTimestamp,desc`, {
    tags: { type: 'list' },
  });
  check(res, { 'list 200': (r) => r.status === 200 });

  sleep(1);
}