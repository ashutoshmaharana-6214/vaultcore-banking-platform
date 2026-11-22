// Read-only capacity baseline. Run only against systems you own or have permission to test.
import http from "k6/http";
import { check } from "k6";

const BASE_URL = (__ENV.BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const isLocal = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE_URL);
if (!isLocal && __ENV.APPROVED_STAGING_TEST !== "I_HAVE_PERMISSION") {
  throw new Error("Remote targets require APPROVED_STAGING_TEST=I_HAVE_PERMISSION");
}
const rate = Number(__ENV.RATE || "10");
const maxRate = isLocal ? 200 : 100;
if (!Number.isInteger(rate) || rate < 1 || rate > maxRate) {
  throw new Error(`RATE must be an integer from 1 to ${maxRate} requests/sec`);
}
const vus = Math.min(500, Math.max(10, Number(__ENV.VUS || "20")));
const duration = __ENV.DURATION || "30s";
if (!/^(\d+)(s|m)$/.test(duration)) {
  throw new Error("DURATION must be like 30s or 2m");
}

export const options = {
  scenarios: {
    health_read_only: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration,
      preAllocatedVUs: vus,
      maxVUs: 500
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.001"],
    "http_req_duration{endpoint:health}": ["p(95)<200"],
    checks: ["rate>0.999"],
    dropped_iterations: ["count==0"]
  }
};

export default function () {
  const res = http.get(`${BASE_URL}/actuator/health`, {
    headers: { Accept: "application/json" },
    timeout: "5s",
    tags: { endpoint: "health" }
  });
  check(res, { "health endpoint returns HTTP 200": r => r.status === 200 });
}
